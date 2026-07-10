package com.hearintent.backend.auth;

import com.hearintent.backend.auth.dto.AuthResponseDto;
import com.hearintent.backend.auth.dto.RegisterRequestDto;
import com.hearintent.backend.config.HearIntentProperties;
import com.hearintent.backend.exception.UnauthorizedException;
import com.hearintent.backend.security.JwtTokenProvider;
import com.hearintent.backend.security.TokenHasher;
import com.hearintent.backend.session.Session;
import com.hearintent.backend.session.SessionRepository;
import com.hearintent.backend.user.User;
import com.hearintent.backend.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private final OtpService otpService;
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final HearIntentProperties properties;
    private final PasswordEncoder passwordEncoder;

    public AuthService(OtpService otpService, UserRepository userRepository,
                        SessionRepository sessionRepository, JwtTokenProvider jwtTokenProvider,
                        HearIntentProperties properties, PasswordEncoder passwordEncoder) {
        this.otpService = otpService;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
    }

    public Mono<Void> register(RegisterRequestDto dto) {
        return userRepository.existsByEmail(dto.email().toLowerCase())
                .flatMap(existsByEmail -> {
                    if (existsByEmail) return Mono.error(new IllegalArgumentException("Email is already registered."));
                    return userRepository.existsByPhoneNumber(dto.phoneNumber());
                })
                .flatMap(existsByPhone -> {
                    if (existsByPhone) return Mono.error(new IllegalArgumentException("Phone number is already registered."));
                    User user = User.newUser(dto.email().toLowerCase());
                    user.setDisplayName(dto.name());
                    user.setPhoneNumber(dto.phoneNumber());
                    user.setPasswordHash(passwordEncoder.encode(dto.password()));
                    user.setVerified(true); // Auto-verify for now
                    return userRepository.save(user);
                }).then();
    }

    public Mono<AuthResponseDto> loginWithEmail(String email, String password, String userAgent, String ipAddress) {
        return userRepository.findByEmail(email.toLowerCase())
                .switchIfEmpty(Mono.error(new UnauthorizedException("Email is not registered.")))
                .flatMap(user -> {
                    if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
                        return Mono.error(new UnauthorizedException("Invalid password."));
                    }
                    user.setLastLoginAt(Instant.now());
                    return userRepository.save(user);
                })
                .flatMap(user -> issueTokens(user, userAgent, ipAddress));
    }

    public Mono<Void> requestPhoneOtp(String phoneNumber) {
        return userRepository.existsByPhoneNumber(phoneNumber)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new UnauthorizedException("Phone number is not registered."));
                    }
                    return otpService.requestOtp(phoneNumber); // Repurposing OtpService to use phoneNumber as identifier
                });
    }

    public Mono<AuthResponseDto> verifyPhoneOtpAndLogin(String phoneNumber, String code,
                                                     String userAgent, String ipAddress) {
        return otpService.verifyOtp(phoneNumber, code)
                .flatMap(verified -> userRepository.findByPhoneNumber(phoneNumber)
                        .switchIfEmpty(Mono.error(new UnauthorizedException("Phone number not registered.")))
                )
                .flatMap(user -> {
                    user.setLastLoginAt(Instant.now());
                    return userRepository.save(user);
                })
                .flatMap(user -> issueTokens(user, userAgent, ipAddress));
    }

    public Mono<Void> requestPasswordReset(String email) {
        return userRepository.existsByEmail(email.toLowerCase())
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new UnauthorizedException("Email is not registered."));
                    }
                    return otpService.requestOtp(email.toLowerCase());
                });
    }

    public Mono<Void> checkPasswordResetOtp(String email, String otp) {
        return otpService.checkOtp(email.toLowerCase(), otp).then();
    }

    public Mono<Void> verifyPasswordReset(String email, String otp, String newPassword) {
        return otpService.verifyOtp(email.toLowerCase(), otp)
                .flatMap(verified -> userRepository.findByEmail(email.toLowerCase())
                        .switchIfEmpty(Mono.error(new UnauthorizedException("Email is not registered.")))
                )
                .flatMap(user -> {
                    user.setPasswordHash(passwordEncoder.encode(newPassword));
                    return userRepository.save(user);
                }).then();
    }

    public Mono<AuthResponseDto> refreshTokens(String refreshToken, String userAgent, String ipAddress) {
        if (!jwtTokenProvider.isValid(refreshToken)) {
            return Mono.error(new UnauthorizedException("Invalid or expired refresh token."));
        }
        if (!"refresh".equals(jwtTokenProvider.extractTokenType(refreshToken))) {
            return Mono.error(new UnauthorizedException("Provided token is not a refresh token."));
        }

        String tokenHash = TokenHasher.sha256(refreshToken);

        return sessionRepository.findByRefreshTokenHash(tokenHash)
                .switchIfEmpty(Mono.error(new UnauthorizedException("Session not found or has been revoked.")))
                .flatMap(session -> {
                    if (session.isRevoked() || session.getExpiresAt().isBefore(Instant.now())) {
                        return Mono.error(new UnauthorizedException("Session has expired or been revoked."));
                    }
                    UUID userId = jwtTokenProvider.extractUserId(refreshToken);
                    return userRepository.findById(userId)
                            .switchIfEmpty(Mono.error(new UnauthorizedException("User not found.")))
                            .flatMap(user -> {
                                session.setRevoked(true);
                                return sessionRepository.save(session).thenReturn(user);
                            });
                })
                .flatMap(user -> issueTokens(user, userAgent, ipAddress));
    }

    public Mono<Void> logout(UUID userId) {
        return sessionRepository.findByUserIdAndIsRevokedFalse(userId)
                .flatMap(session -> {
                    session.setRevoked(true);
                    return sessionRepository.save(session);
                })
                .then();
    }

    private Mono<AuthResponseDto> issueTokens(User user, String userAgent, String ipAddress) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
        UUID sessionId = UUID.randomUUID();
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), sessionId);

        Instant expiresAt = Instant.now().plusSeconds(properties.jwt().refreshTokenTtlDays() * 86400);
        Session session = Session.create(
                user.getId(),
                TokenHasher.sha256(refreshToken),
                userAgent,
                ipAddress,
                expiresAt
        );

        return sessionRepository.save(session)
                .thenReturn(new AuthResponseDto(
                        accessToken,
                        refreshToken,
                        properties.jwt().accessTokenTtlMinutes() * 60,
                        user.getId(),
                        user.getEmail(),
                        user.getDisplayName()
                ));
    }
}
