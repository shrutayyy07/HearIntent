package com.hearintent.backend.upload.dto;

import java.util.List;

public record FileProcessingResultDto(
        String sessionId,
        boolean success,
        String errorMessage,
        List<TranscriptSegmentDto> segments,
        List<IntentResultDto> intents,
        String fullTranscript,
        double durationSec
) {
}
