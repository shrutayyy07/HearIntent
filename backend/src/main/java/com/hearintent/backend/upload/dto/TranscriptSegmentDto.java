package com.hearintent.backend.upload.dto;

public record TranscriptSegmentDto(
        String text,
        String translatedText,
        double confidence,
        Double startTimeSec,
        Double endTimeSec
) {
}
