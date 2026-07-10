package com.hearintent.backend.log;

import com.hearintent.backend.logging.FlatFileLogService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

@Service
public class LogService {

    private final LogRepository logRepository;
    private final FlatFileLogService flatFileLogService;

    public LogService(LogRepository logRepository, FlatFileLogService flatFileLogService) {
        this.logRepository = logRepository;
        this.flatFileLogService = flatFileLogService;
    }

    public Mono<LogEntry> log(UUID userId, UUID sessionId, String level, String category,
                               String message, String metadataJson) {
        LogEntry entry = LogEntry.create(userId, sessionId, level, category, message, metadataJson);

        return logRepository.save(entry)
                .doOnSuccess(saved -> Mono.fromRunnable(() ->
                                flatFileLogService.appendAuditLine(category, message))
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe());
    }

    public Mono<LogEntry> logError(UUID userId, UUID sessionId, String message, Throwable throwable) {
        LogEntry entry = LogEntry.create(userId, sessionId, "ERROR", "SYSTEM", message, "{}");
        return logRepository.save(entry)
                .doOnSuccess(saved -> Mono.fromRunnable(() ->
                                flatFileLogService.appendErrorLine(message, throwable))
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe());
    }

    public Flux<LogEntry> recent(UUID userId, int limit) {
        return logRepository.findRecent(userId, limit);
    }
}
