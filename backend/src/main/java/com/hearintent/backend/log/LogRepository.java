package com.hearintent.backend.log;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface LogRepository extends ReactiveCrudRepository<LogEntry, UUID> {

    @Query("""
        SELECT * FROM logs
        WHERE (:userId IS NULL OR user_id = :userId)
        ORDER BY created_at DESC
        LIMIT :limit
        """)
    Flux<LogEntry> findRecent(UUID userId, int limit);

    Flux<LogEntry> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}
