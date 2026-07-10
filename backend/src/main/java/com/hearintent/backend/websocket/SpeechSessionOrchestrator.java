package com.hearintent.backend.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hearintent.backend.grpc.SpeechGrpcClient;
import com.hearintent.backend.grpc.SpeechProto;
import com.hearintent.backend.intent.IntentRecord;
import com.hearintent.backend.intent.IntentRecordRepository;
import com.hearintent.backend.log.LogService;
import com.hearintent.backend.ruleengine.IntentRuleEngine;
import com.hearintent.backend.ruleengine.RuleContext;
import com.hearintent.backend.ruleengine.RuleEvaluationResult;
import com.hearintent.backend.session.SpeechSession;
import com.hearintent.backend.session.SpeechSessionRepository;
import com.hearintent.backend.transcript.Transcript;
import com.hearintent.backend.transcript.TranscriptRepository;
import com.hearintent.backend.websocket.dto.ErrorPayload;
import com.hearintent.backend.websocket.dto.IntentPayload;
import com.hearintent.backend.websocket.dto.TranscriptionPayload;
import com.hearintent.backend.websocket.dto.WsOutboundMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class SpeechSessionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SpeechSessionOrchestrator.class);

    private final SpeechGrpcClient grpcClient;
    private final SpeechSessionRepository sessionRepository;
    private final TranscriptRepository transcriptRepository;
    private final IntentRecordRepository intentRecordRepository;
    private final IntentRuleEngine ruleEngine;
    private final LogService logService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SpeechSessionOrchestrator(SpeechGrpcClient grpcClient,
                                      SpeechSessionRepository sessionRepository,
                                      TranscriptRepository transcriptRepository,
                                      IntentRecordRepository intentRecordRepository,
                                      IntentRuleEngine ruleEngine,
                                      LogService logService) {
        this.grpcClient = grpcClient;
        this.sessionRepository = sessionRepository;
        this.transcriptRepository = transcriptRepository;
        this.intentRecordRepository = intentRecordRepository;
        this.ruleEngine = ruleEngine;
        this.logService = logService;
    }

    /**
     * Drives a single live microphone session: persists a SpeechSession row,
     * forwards `audioFrames` to the AI worker over gRPC, persists each
     * resulting transcript/intent, evaluates the rule engine, and returns a
     * Flux of outbound WebSocket messages ready to be serialized to the
     * connected browser client.
     */
    public Flux<WsOutboundMessage> runLiveSession(UUID userId,
                                                    Flux<SpeechProto.AudioChunk> audioFrames) {
        Mono<SpeechSession> sessionMono = sessionRepository.save(SpeechSession.startLive(userId));

        return sessionMono.flatMapMany(speechSession -> {
            UUID dbSessionId = speechSession.getId();

            Flux<SpeechProto.SpeechEvent> events = grpcClient.streamAudio(audioFrames);

            return events
                    .flatMap(event -> handleEvent(dbSessionId, event))
                    .doOnError(err -> log.error("Live session error for user {}: {}", userId, err.getMessage()))
                    .onErrorResume(err -> Flux.just(WsOutboundMessage.of(
                            "ERROR", new ErrorPayload("STREAM_ERROR", err.getMessage())
                    )))
                    .doFinally(signal -> finalizeSession(dbSessionId).subscribe());
        });
    }

    private Flux<WsOutboundMessage> handleEvent(UUID dbSessionId, SpeechProto.SpeechEvent event) {
        return switch (event.getType()) {
            case FINAL_TRANSCRIPTION, PARTIAL_TRANSCRIPTION -> persistTranscript(dbSessionId, event)
                    .map(saved -> WsOutboundMessage.of("TRANSCRIPTION", new TranscriptionPayload(
                            event.getTranscription().getText(),
                            event.getTranscription().getTranslatedText(),
                            event.getTranscription().getConfidence(),
                            "user",
                            DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                    )))
                    .flux();

            case INTENT_EXTRACTED -> persistIntentAndEvaluateRule(dbSessionId, event)
                    .flux();

            case ERROR -> Flux.just(WsOutboundMessage.of(
                    "ERROR", new ErrorPayload(event.getError().getCode(), event.getError().getMessage())
            ));

            case SESSION_CLOSED -> Flux.just(WsOutboundMessage.of("SESSION_CLOSED", Map.of("sessionId", dbSessionId.toString())));

            default -> Flux.empty();
        };
    }

    private Mono<Transcript> persistTranscript(UUID dbSessionId, SpeechProto.SpeechEvent event) {
        var t = event.getTranscription();
        Transcript transcript = Transcript.create(
                dbSessionId,
                t.getText(),
                t.getConfidence(),
                t.getStartTimeSec(),
                t.getEndTimeSec(),
                t.getLanguage().isEmpty() ? "en" : t.getLanguage(),
                t.getIsFinal()
        );
        return transcriptRepository.save(transcript);
    }

    @SuppressWarnings("unchecked")
    private Mono<WsOutboundMessage> persistIntentAndEvaluateRule(UUID dbSessionId, SpeechProto.SpeechEvent event) {
        var i = event.getIntent();

        Map<String, Object> entities;
        try {
            entities = objectMapper.readValue(i.getEntitiesJson(), Map.class);
        } catch (Exception e) {
            entities = new HashMap<>();
        }

        RuleContext ruleContext = new RuleContext(i.getIntent(), i.getConfidence(), entities, i.getRawText());
        RuleEvaluationResult ruleResult = ruleEngine.evaluate(ruleContext);

        IntentRecord record = IntentRecord.create(
                dbSessionId,
                null,
                i.getIntent(),
                i.getConfidence(),
                i.getEntitiesJson(),
                i.getRawText(),
                ruleResult.actionCode()
        );

        Map<String, Object> finalEntities = entities;
        return intentRecordRepository.save(record)
                .map(saved -> WsOutboundMessage.of("INTENT", new IntentPayload(
                        i.getIntent(), i.getConfidence(), finalEntities, ruleResult.actionCode()
                )));
    }

    private Mono<Void> finalizeSession(UUID dbSessionId) {
        return sessionRepository.findById(dbSessionId)
                .flatMap(session -> {
                    session.setStatus("COMPLETED");
                    session.setEndedAt(Instant.now());
                    if (session.getStartedAt() != null) {
                        session.setDurationSec(
                                (double) (session.getEndedAt().toEpochMilli() - session.getStartedAt().toEpochMilli()) / 1000.0
                        );
                    }
                    return sessionRepository.save(session);
                })
                .then();
    }
}
