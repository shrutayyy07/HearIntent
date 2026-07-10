package com.hearintent.backend.session;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SessionRepository extends ReactiveCrudRepository<Session, UUID> {
    Mono<Session> findByRefreshTokenHash(String refreshTokenHash);
    Flux<Session> findByUserIdAndIsRevokedFalse(UUID userId);
}
