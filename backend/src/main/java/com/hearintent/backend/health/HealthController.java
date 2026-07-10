package com.hearintent.backend.health;

import com.hearintent.backend.grpc.SpeechGrpcClient;
import com.hearintent.backend.grpc.SpeechProto;
import com.hearintent.backend.websocket.dto.SystemStatusPayload;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final SpeechGrpcClient grpcClient;

    public HealthController(SpeechGrpcClient grpcClient) {
        this.grpcClient = grpcClient;
    }

    @GetMapping("/status")
    public Mono<ResponseEntity<Map<String, Object>>> status() {
        return grpcClient.healthCheck()
                .map(worker -> ResponseEntity.ok(Map.of(
                        "frontend", "online",
                        "backend", "online",
                        "pythonWorker", worker.getHealthy() ? "online" : "offline",
                        "websocket", "online",
                        "whisperModelLoaded", worker.getWhisperModelLoaded(),
                        "intentModelLoaded", worker.getIntentModelLoaded(),
                        "workerVersion", worker.getWorkerVersion(),
                        "workerUptimeSec", worker.getUptimeSec()
                )));
    }

    @GetMapping("/liveness")
    public Mono<ResponseEntity<Map<String, String>>> liveness() {
        return Mono.just(ResponseEntity.ok(Map.of("status", "UP")));
    }
}
