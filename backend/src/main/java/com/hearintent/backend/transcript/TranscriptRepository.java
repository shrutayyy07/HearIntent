package com.hearintent.backend.transcript;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface TranscriptRepository extends ReactiveCrudRepository<Transcript, UUID> {
    Flux<Transcript> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}
