package com.hearintent.backend.intent;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface IntentRecordRepository extends ReactiveCrudRepository<IntentRecord, UUID> {
    Flux<IntentRecord> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}
