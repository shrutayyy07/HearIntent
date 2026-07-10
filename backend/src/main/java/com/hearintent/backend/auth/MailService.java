package com.hearintent.backend.auth;

import com.hearintent.backend.config.HearIntentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final HearIntentProperties properties;

    public MailService(JavaMailSender mailSender, HearIntentProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public Mono<Void> sendOtpEmail(String toEmail, String code) {
        return Mono.<Void>fromRunnable(() -> {
            if (properties.otp().mockMode()) {
                log.info("[MOCK EMAIL] OTP for {} is {}", toEmail, code);
                return;
            }
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(properties.otp().mailFrom());
            message.setTo(toEmail);
            message.setSubject("Your HearIntent verification code");
            message.setText(
                    "Your HearIntent verification code is: " + code + "\n\n" +
                            "This code expires in " + properties.otp().ttlMinutes() + " minutes.\n" +
                            "If you did not request this code, you can safely ignore this email."
            );
            mailSender.send(message);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
