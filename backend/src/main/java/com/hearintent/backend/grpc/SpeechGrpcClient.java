package com.hearintent.backend.grpc;

import com.hearintent.backend.config.HearIntentProperties;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

@Component
public class SpeechGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(SpeechGrpcClient.class);

    private final SpeechIntelligenceServiceGrpc.SpeechIntelligenceServiceStub asyncStub;
    private final SpeechIntelligenceServiceGrpc.SpeechIntelligenceServiceBlockingStub blockingStub;
    private final HearIntentProperties properties;

    public SpeechGrpcClient(SpeechIntelligenceServiceGrpc.SpeechIntelligenceServiceStub asyncStub,
                             SpeechIntelligenceServiceGrpc.SpeechIntelligenceServiceBlockingStub blockingStub,
                             HearIntentProperties properties) {
        this.asyncStub = asyncStub;
        this.blockingStub = blockingStub;
        this.properties = properties;
    }

    /**
     * Opens a bidirectional gRPC stream to the AI worker. `outboundAudio` is a
     * Flux of AudioChunk messages produced as the browser sends PCM frames over
     * the backend's WebSocket; the returned Flux emits SpeechEvent messages
     * (partial/final transcription, intent extraction, errors) as they stream
     * back from Python.
     */
    public Flux<SpeechProto.SpeechEvent> streamAudio(Flux<SpeechProto.AudioChunk> outboundAudio) {
        return Flux.create(sink -> {
            StreamObserver<SpeechProto.AudioChunk> requestObserver = asyncStub.streamAudio(
                    new StreamObserver<SpeechProto.SpeechEvent>() {
                        @Override
                        public void onNext(SpeechProto.SpeechEvent event) {
                            sink.next(event);
                        }

                        @Override
                        public void onError(Throwable t) {
                            log.error("gRPC stream error from AI worker", t);
                            sink.error(t);
                        }

                        @Override
                        public void onCompleted() {
                            sink.complete();
                        }
                    }
            );

            outboundAudio.subscribe(
                    requestObserver::onNext,
                    requestObserver::onError,
                    requestObserver::onCompleted
            );

            sink.onDispose(() -> {
                try {
                    requestObserver.onCompleted();
                } catch (IllegalStateException ignored) {
                    // already completed/cancelled
                }
            });
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    public Mono<SpeechProto.AudioFileResponse> processAudioFile(SpeechProto.AudioFileRequest request) {
        return Mono.create(sink -> asyncStub.processAudioFile(request, new StreamObserver<>() {
            @Override
            public void onNext(SpeechProto.AudioFileResponse value) {
                sink.success(value);
            }

            @Override
            public void onError(Throwable t) {
                sink.error(t);
            }

            @Override
            public void onCompleted() {
                // no-op, result already emitted via onNext
            }
        }));
    }

    public Mono<SpeechProto.HealthCheckResponse> healthCheck() {
        return Mono.fromCallable(() -> blockingStub.healthCheck(
                        SpeechProto.HealthCheckRequest.newBuilder()
                                .setRequester("spring-boot-backend")
                                .build()
                ))
                .onErrorResume(ex -> {
                    log.warn("AI worker health check failed: {}", ex.getMessage());
                    return Mono.just(SpeechProto.HealthCheckResponse.newBuilder()
                            .setHealthy(false)
                            .build());
                });
    }
}
