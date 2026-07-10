package com.hearintent.backend.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hearintent.backend.grpc.SpeechProto;
import com.hearintent.backend.security.JwtTokenProvider;
import com.hearintent.backend.websocket.dto.WsControlMessage;
import com.hearintent.backend.websocket.dto.WsOutboundMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SpeechWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SpeechWebSocketHandler.class);

    private final SpeechSessionOrchestrator orchestrator;
    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SpeechWebSocketHandler(SpeechSessionOrchestrator orchestrator, JwtTokenProvider jwtTokenProvider) {
        this.orchestrator = orchestrator;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        UUID userId = resolveUserId(session);
        String grpcSessionId = UUID.randomUUID().toString();
        AtomicLong sequence = new AtomicLong(0);
        AtomicInteger sampleRate = new AtomicInteger(16000);

        Sinks.Many<SpeechProto.AudioChunk> audioSink = Sinks.many().unicast().onBackpressureBuffer();

        Mono<Void> receiveLoop = session.receive()
                .doOnNext(message -> {
                    if (message.getType() == WebSocketMessage.Type.TEXT) {
                        handleControlMessage(message, sampleRate, audioSink, sequence, grpcSessionId);
                    } else if (message.getType() == WebSocketMessage.Type.BINARY) {
                        byte[] pcmBytes = readBytes(message);
                        SpeechProto.AudioChunk chunk = SpeechProto.AudioChunk.newBuilder()
                                .setSessionId(grpcSessionId)
                                .setPcmData(com.google.protobuf.ByteString.copyFrom(pcmBytes))
                                .setSampleRate(sampleRate.get())
                                .setSequenceNumber(sequence.incrementAndGet())
                                .setIsFinal(false)
                                .setTimestampMs(System.currentTimeMillis())
                                .build();
                        audioSink.tryEmitNext(chunk);
                    }
                })
                .doOnComplete(() -> {
                    SpeechProto.AudioChunk finalChunk = SpeechProto.AudioChunk.newBuilder()
                            .setSessionId(grpcSessionId)
                            .setSampleRate(sampleRate.get())
                            .setSequenceNumber(sequence.incrementAndGet())
                            .setIsFinal(true)
                            .setTimestampMs(System.currentTimeMillis())
                            .build();
                    audioSink.tryEmitNext(finalChunk);
                    audioSink.tryEmitComplete();
                })
                .doOnError(err -> audioSink.tryEmitError(err))
                .then();

        Flux<WebSocketMessage> outbound = orchestrator
                .runLiveSession(userId, audioSink.asFlux())
                .map(this::toJsonMessage)
                .map(session::textMessage);

        Mono<Void> sendLoop = session.send(outbound);

        return Mono.when(receiveLoop, sendLoop);
    }

    private void handleControlMessage(WebSocketMessage message, AtomicInteger sampleRate, Sinks.Many<SpeechProto.AudioChunk> audioSink, AtomicLong sequence, String grpcSessionId) {
        try {
            WsControlMessage control = objectMapper.readValue(message.getPayloadAsText(), WsControlMessage.class);
            if (control.sampleRate() != null) {
                sampleRate.set(control.sampleRate());
            }
            if ("END_SESSION".equals(control.type())) {
                SpeechProto.AudioChunk finalChunk = SpeechProto.AudioChunk.newBuilder()
                        .setSessionId(grpcSessionId)
                        .setSampleRate(sampleRate.get())
                        .setSequenceNumber(sequence.incrementAndGet())
                        .setIsFinal(true)
                        .setTimestampMs(System.currentTimeMillis())
                        .build();
                audioSink.tryEmitNext(finalChunk);
                audioSink.tryEmitComplete();
            }
            log.info("Received control message: {}", control.type());
        } catch (Exception e) {
            log.warn("Failed to parse control message: {}", e.getMessage());
        }
    }

    private byte[] readBytes(WebSocketMessage message) {
        byte[] bytes = new byte[message.getPayload().readableByteCount()];
        message.getPayload().read(bytes);
        return bytes;
    }

    private String toJsonMessage(WsOutboundMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            return "{\"type\":\"ERROR\",\"payload\":{\"message\":\"serialization failure\"}}";
        }
    }

    private UUID resolveUserId(WebSocketSession session) {
        URI uri = session.getHandshakeInfo().getUri();
        String query = uri.getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("token=")) {
                    String token = param.substring("token=".length());
                    try {
                        if (jwtTokenProvider.isValid(token)) {
                            return jwtTokenProvider.extractUserId(token);
                        }
                    } catch (Exception ignored) {
                        // fall through to anonymous session
                    }
                }
            }
        }
        return UUID.randomUUID();
    }
}
