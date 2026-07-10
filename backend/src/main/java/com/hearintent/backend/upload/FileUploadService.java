package com.hearintent.backend.upload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.hearintent.backend.grpc.SpeechGrpcClient;
import com.hearintent.backend.grpc.SpeechProto;
import com.hearintent.backend.intent.IntentRecord;
import com.hearintent.backend.intent.IntentRecordRepository;
import com.hearintent.backend.ruleengine.IntentRuleEngine;
import com.hearintent.backend.ruleengine.RuleContext;
import com.hearintent.backend.ruleengine.RuleEvaluationResult;
import com.hearintent.backend.session.SpeechSession;
import com.hearintent.backend.session.SpeechSessionRepository;
import com.hearintent.backend.transcript.Transcript;
import com.hearintent.backend.transcript.TranscriptRepository;
import com.hearintent.backend.upload.dto.FileProcessingResultDto;
import com.hearintent.backend.upload.dto.IntentResultDto;
import com.hearintent.backend.upload.dto.TranscriptSegmentDto;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FileUploadService {

    private final SpeechGrpcClient grpcClient;
    private final SpeechSessionRepository sessionRepository;
    private final TranscriptRepository transcriptRepository;
    private final IntentRecordRepository intentRecordRepository;
    private final IntentRuleEngine ruleEngine;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FileUploadService(SpeechGrpcClient grpcClient,
                              SpeechSessionRepository sessionRepository,
                              TranscriptRepository transcriptRepository,
                              IntentRecordRepository intentRecordRepository,
                              IntentRuleEngine ruleEngine) {
        this.grpcClient = grpcClient;
        this.sessionRepository = sessionRepository;
        this.transcriptRepository = transcriptRepository;
        this.intentRecordRepository = intentRecordRepository;
        this.ruleEngine = ruleEngine;
    }

    public Mono<FileProcessingResultDto> processUploadedMedia(UUID userId, FilePart filePart) {
        String filename = filePart.filename();
        String contentType = filePart.headers().getContentType() != null
                ? filePart.headers().getContentType().toString()
                : "application/octet-stream";

        return sessionRepository.save(SpeechSession.startFileUpload(userId, filename))
                .flatMap(speechSession -> readAllBytes(filePart)
                        .flatMap(mediaBytes -> grpcClient.processAudioFile(
                                SpeechProto.AudioFileRequest.newBuilder()
                                        .setSessionId(speechSession.getId().toString())
                                        .setPcmData(ByteString.copyFrom(mediaBytes))
                                        .setSampleRate(0) // 0 signals "raw media, let worker invoke ffmpeg via content type"
                                        .setOriginalFilename(filename)
                                        .setContentType(contentType)
                                        .build()
                        ))
                        .flatMap(response -> persistAndBuildResult(speechSession.getId(), response))
                        .flatMap(result -> markCompleted(speechSession.getId(), result))
                );
    }

    private Mono<byte[]> readAllBytes(FilePart filePart) {
        return Mono.fromCallable(ByteArrayOutputStream::new)
                .flatMap(buffer -> filePart.content()
                        .doOnNext(dataBuffer -> {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            org.springframework.core.io.buffer.DataBufferUtils.release(dataBuffer);
                            buffer.writeBytes(bytes);
                        })
                        .then(Mono.fromCallable(buffer::toByteArray)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @SuppressWarnings("unchecked")
    private Mono<FileProcessingResultDto> persistAndBuildResult(UUID dbSessionId, SpeechProto.AudioFileResponse response) {
        if (!response.getSuccess()) {
            return Mono.just(new FileProcessingResultDto(
                    dbSessionId.toString(), false, response.getErrorMessage(), List.of(), List.of(), "", 0.0
            ));
        }

        List<Mono<TranscriptSegmentDto>> transcriptMonos = new ArrayList<>();
        for (SpeechProto.TranscriptionResult seg : response.getSegmentsList()) {
            Transcript transcript = Transcript.create(
                    dbSessionId, seg.getText(), seg.getConfidence(),
                    seg.getStartTimeSec(), seg.getEndTimeSec(),
                    seg.getLanguage().isEmpty() ? "en" : seg.getLanguage(), true
            );
            transcriptMonos.add(transcriptRepository.save(transcript)
                    .map(saved -> new TranscriptSegmentDto(
                            saved.getText(), seg.getTranslatedText(), saved.getConfidence(),
                            saved.getStartTimeSec(), saved.getEndTimeSec()
                    )));
        }

        List<Mono<IntentResultDto>> intentMonos = new ArrayList<>();
        for (SpeechProto.IntentResult intentProto : response.getIntentsList()) {
            Map<String, Object> entities;
            try {
                entities = objectMapper.readValue(intentProto.getEntitiesJson(), Map.class);
            } catch (Exception e) {
                entities = new HashMap<>();
            }
            RuleContext ctx = new RuleContext(intentProto.getIntent(), intentProto.getConfidence(), entities, intentProto.getRawText());
            RuleEvaluationResult ruleResult = ruleEngine.evaluate(ctx);

            IntentRecord record = IntentRecord.create(
                    dbSessionId, null, intentProto.getIntent(), intentProto.getConfidence(),
                    intentProto.getEntitiesJson(), intentProto.getRawText(), ruleResult.actionCode()
            );

            Map<String, Object> finalEntities = entities;
            intentMonos.add(intentRecordRepository.save(record)
                    .map(saved -> new IntentResultDto(
                            saved.getIntent(), saved.getConfidence(), finalEntities, ruleResult.actionCode()
                    )));
        }

        return Mono.zip(
                Flux.concat(transcriptMonos).collectList(),
                Flux.concat(intentMonos).collectList()
        ).map(tuple -> new FileProcessingResultDto(
                dbSessionId.toString(),
                true,
                null,
                tuple.getT1(),
                tuple.getT2(),
                response.getFullTranscript(),
                response.getDurationSec()
        ));
    }

    private Mono<FileProcessingResultDto> markCompleted(UUID dbSessionId, FileProcessingResultDto result) {
        return sessionRepository.findById(dbSessionId)
                .flatMap(session -> {
                    session.setStatus(result.success() ? "COMPLETED" : "ERROR");
                    session.setEndedAt(java.time.Instant.now());
                    session.setDurationSec(result.durationSec());
                    return sessionRepository.save(session);
                })
                .thenReturn(result);
    }
}
