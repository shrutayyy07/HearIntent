package com.hearintent.backend.auth;

import com.hearintent.backend.config.HearIntentProperties;
import com.hearintent.backend.exception.InvalidOtpException;
import com.hearintent.backend.exception.RateLimitExceededException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

@Service
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private final OtpRepository otpRepository;
    private final MailService mailService;
    private final HearIntentProperties properties;

    public OtpService(OtpRepository otpRepository, MailService mailService,
                       HearIntentProperties properties) {
        this.otpRepository = otpRepository;
        this.mailService = mailService;
        this.properties = properties;
    }

    public Mono<Void> requestOtp(String email) {
        return otpRepository.findLatestActiveByEmail(email)
                .flatMap(existing -> {
                    Duration sinceLastSend = Duration.between(existing.getLastSentAt(), Instant.now());
                    if (!existing.isExpired() && sinceLastSend.getSeconds() < properties.otp().resendCooldownSeconds()) {
                        long waitSeconds = properties.otp().resendCooldownSeconds() - sinceLastSend.getSeconds();
                        return Mono.<Otp>error(new RateLimitExceededException(
                                "Please wait " + waitSeconds + " seconds before requesting another code."
                        ));
                    }
                    return generateAndPersist(email);
                })
                .switchIfEmpty(generateAndPersist(email))
                .then();
    }


    private Mono<Otp> generateAndPersist(String email) {
        String code = generateNumericCode(properties.otp().length());
        String codeHash = ENCODER.encode(code);
        Instant expiresAt = Instant.now().plusSeconds(properties.otp().ttlMinutes() * 60);

        Otp otp = Otp.create(email, codeHash, "LOGIN", properties.otp().maxAttempts(), expiresAt);

        return otpRepository.save(otp)
                .flatMap(saved -> mailService.sendOtpEmail(email, code).thenReturn(saved));
    }

    public Mono<Void> resendOtp(String email) {
        return requestOtp(email);
    }

    public Mono<Boolean> verifyOtp(String email, String code) {
        return checkOtpInternal(email, code, true);
    }

    public Mono<Boolean> checkOtp(String email, String code) {
        return checkOtpInternal(email, code, false);
    }

    private Mono<Boolean> checkOtpInternal(String email, String code, boolean consume) {
        return otpRepository.findLatestActiveByEmail(email)
                .switchIfEmpty(Mono.error(new InvalidOtpException("No active verification code found. Please request a new one.")))
                .flatMap(otp -> {
                    if (otp.isExpired()) {
                        return Mono.error(new InvalidOtpException("Verification code has expired. Please request a new one."));
                    }
                    if (!otp.hasAttemptsLeft()) {
                        return Mono.error(new InvalidOtpException("Too many incorrect attempts. Please request a new code."));
                    }
                    boolean matches = ENCODER.matches(code, otp.getCodeHash());
                    if (!matches) {
                        otp.setAttempts(otp.getAttempts() + 1);
                        return otpRepository.save(otp)
                                .then(Mono.error(new InvalidOtpException("Incorrect verification code.")));
                    }
                    if (consume) {
                        otp.setUsed(true);
                        return otpRepository.save(otp).thenReturn(true);
                    }
                    return Mono.just(true);
                });
    }

    private String generateNumericCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
