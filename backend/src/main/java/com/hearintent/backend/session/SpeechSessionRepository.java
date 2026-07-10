package com.hearintent.backend.session;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface SpeechSessionRepository extends ReactiveCrudRepository<SpeechSession, UUID> {
    Flux<SpeechSession> findByUserIdOrderByStartedAtDesc(UUID userId);
}
