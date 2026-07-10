package com.hearintent.backend.auth;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface OtpRepository extends ReactiveCrudRepository<Otp, UUID> {

    @Query("""
        SELECT * FROM otps
        WHERE email = :email AND is_used = false
        ORDER BY created_at DESC
        LIMIT 1
        """)
    Mono<Otp> findLatestActiveByEmail(String email);

    @Query("""
        SELECT COUNT(*) FROM otps
        WHERE email = :email AND created_at > now() - INTERVAL '1 hour'
        """)
    Mono<Long> countRecentRequestsByEmail(String email);
}
