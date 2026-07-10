package com.hearintent.backend.auth;

import com.hearintent.backend.auth.dto.*;
import com.hearintent.backend.config.HearIntentProperties;
import com.hearintent.backend.exception.RateLimitExceededException;
import com.hearintent.backend.ratelimit.RateLimiter;
import com.hearintent.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RateLimiter rateLimiter;
    private final HearIntentProperties properties;

    public AuthController(AuthService authService, RateLimiter rateLimiter,
                           HearIntentProperties properties) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<MessageResponseDto>> register(@Valid @RequestBody RegisterRequestDto dto) {
        return authService.register(dto)
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED)
                        .body(MessageResponseDto.ok("Registration successful.")));
    }

    @PostMapping("/login/email")
    public Mono<ResponseEntity<AuthResponseDto>> loginWithEmail(@Valid @RequestBody EmailLoginRequestDto dto,
                                                                  ServerWebExchange exchange) {
        String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");
        String ipAddress = resolveClientIp(exchange);
        return authService.loginWithEmail(dto.email(), dto.password(), userAgent, ipAddress)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/login/phone/request")
    public Mono<ResponseEntity<MessageResponseDto>> requestPhoneOtp(@Valid @RequestBody PhoneOtpRequestDto dto,
                                                                      ServerWebExchange exchange) {
        String rateLimitKey = "otp-request:phone:" + dto.phoneNumber();
        boolean allowed = rateLimiter.tryAcquire(
                rateLimitKey, properties.rateLimit().otpRequestsPerHour(), 3600
        );
        if (!allowed) {
            return Mono.error(new RateLimitExceededException(
                    "Too many OTP requests for this phone number. Please try again later."
            ));
        }
        return authService.requestPhoneOtp(dto.phoneNumber())
                .thenReturn(ResponseEntity.ok(MessageResponseDto.ok(
                        "A verification code has been sent to " + dto.phoneNumber()
                )));
    }

    @PostMapping("/login/phone/verify")
    public Mono<ResponseEntity<AuthResponseDto>> verifyPhoneOtp(@Valid @RequestBody PhoneOtpVerifyDto dto,
                                                                  ServerWebExchange exchange) {
        String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");
        String ipAddress = resolveClientIp(exchange);
        return authService.verifyPhoneOtpAndLogin(dto.phoneNumber(), dto.code(), userAgent, ipAddress)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/password/forgot/request")
    public Mono<ResponseEntity<MessageResponseDto>> requestPasswordReset(@Valid @RequestBody ForgotPasswordRequestDto dto) {
        return authService.requestPasswordReset(dto.email())
                .thenReturn(ResponseEntity.ok(MessageResponseDto.ok(
                        "If the email is registered, a password reset code has been sent."
                )));
    }

    @PostMapping("/password/forgot/check")
    public Mono<ResponseEntity<MessageResponseDto>> checkPasswordReset(@Valid @RequestBody OtpVerifyDto dto) {
        return authService.checkPasswordResetOtp(dto.email(), dto.code())
                .thenReturn(ResponseEntity.ok(MessageResponseDto.ok(
                        "Verification code is valid."
                )));
    }

    @PostMapping("/password/forgot/verify")
    public Mono<ResponseEntity<MessageResponseDto>> verifyPasswordReset(@Valid @RequestBody ForgotPasswordVerifyDto dto) {
        return authService.verifyPasswordReset(dto.email(), dto.otp(), dto.newPassword())
                .thenReturn(ResponseEntity.ok(MessageResponseDto.ok(
                        "Password has been reset successfully."
                )));
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<AuthResponseDto>> refresh(@Valid @RequestBody RefreshTokenRequestDto dto,
                                                           ServerWebExchange exchange) {
        String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");
        String ipAddress = resolveClientIp(exchange);
        return authService.refreshTokens(dto.refreshToken(), userAgent, ipAddress)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<MessageResponseDto>> logout() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> (AuthenticatedUser) ctx.getAuthentication().getPrincipal())
                .flatMap(user -> authService.logout(user.userId()))
                .thenReturn(ResponseEntity.ok(MessageResponseDto.ok("Logged out successfully.")));
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }
}
