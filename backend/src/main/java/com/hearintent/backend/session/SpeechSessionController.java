package com.hearintent.backend.session;

import com.hearintent.backend.intent.IntentRecordRepository;
import com.hearintent.backend.security.AuthenticatedUser;
import com.hearintent.backend.transcript.TranscriptRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
public class SpeechSessionController {

    private final SpeechSessionRepository sessionRepository;
    private final TranscriptRepository transcriptRepository;
    private final IntentRecordRepository intentRecordRepository;

    public SpeechSessionController(SpeechSessionRepository sessionRepository,
                                    TranscriptRepository transcriptRepository,
                                    IntentRecordRepository intentRecordRepository) {
        this.sessionRepository = sessionRepository;
        this.transcriptRepository = transcriptRepository;
        this.intentRecordRepository = intentRecordRepository;
    }

    @GetMapping
    public Flux<SpeechSession> listMySessions() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> (AuthenticatedUser) ctx.getAuthentication().getPrincipal())
                .flatMapMany(user -> sessionRepository.findByUserIdOrderByStartedAtDesc(user.userId()));
    }

    @GetMapping("/{sessionId}/transcripts")
    public Flux<com.hearintent.backend.transcript.Transcript> sessionTranscripts(@PathVariable UUID sessionId) {
        return transcriptRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @GetMapping("/{sessionId}/intents")
    public Flux<com.hearintent.backend.intent.IntentRecord> sessionIntents(@PathVariable UUID sessionId) {
        return intentRecordRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @GetMapping("/{sessionId}")
    public Mono<ResponseEntity<SpeechSession>> getSession(@PathVariable UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
