package com.hearintent.backend.grpc;

import com.hearintent.backend.config.HearIntentProperties;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class GrpcClientConfig {

    private ManagedChannel channel;

    @Bean
    public ManagedChannel aiWorkerChannel(HearIntentProperties properties) {
        this.channel = NettyChannelBuilder
                .forAddress(properties.grpc().aiWorkerHost(), properties.grpc().aiWorkerPort())
                .usePlaintext()
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .maxInboundMessageSize(200 * 1024 * 1024)
                .build();
        return channel;
    }

    @Bean
    public SpeechIntelligenceServiceGrpc.SpeechIntelligenceServiceStub speechAsyncStub(ManagedChannel aiWorkerChannel) {
        return SpeechIntelligenceServiceGrpc.newStub(aiWorkerChannel);
    }

    @Bean
    public SpeechIntelligenceServiceGrpc.SpeechIntelligenceServiceBlockingStub speechBlockingStub(ManagedChannel aiWorkerChannel) {
        return SpeechIntelligenceServiceGrpc.newBlockingStub(aiWorkerChannel);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
        }
    }
}
