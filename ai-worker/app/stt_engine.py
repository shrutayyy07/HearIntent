import threading
import time
from dataclasses import dataclass

import numpy as np
from faster_whisper import WhisperModel

from app.config import config
from app.logger import get_logger, log_with_fields

logger = get_logger("stt_engine")


@dataclass
class TranscriptionSegment:
    text: str
    confidence: float
    start_time_sec: float
    end_time_sec: float
    language: str
    is_final: bool
    translated_text: str = ""


class SpeechToTextEngine:
    """Thin wrapper around faster-whisper providing both batch and
    streaming-style (chunked) transcription. Model load is lazy and
    thread-safe so the gRPC server can boot instantly and start serving
    health checks while the model loads in the background.
    """

    def __init__(self) -> None:
        self._model: WhisperModel | None = None
        self._lock = threading.Lock()
        self._load_error: str | None = None

    def load(self) -> None:
        with self._lock:
            if self._model is not None:
                return
            log_with_fields(
                logger,
                20,
                "Loading faster-whisper model",
                model_size=config.whisper_model_size,
                device=config.whisper_device,
                compute_type=config.whisper_compute_type,
            )
            started = time.time()
            try:
                self._model = WhisperModel(
                    config.whisper_model_size,
                    device=config.whisper_device,
                    compute_type=config.whisper_compute_type,
                )
            except Exception as exc:  # noqa: BLE001
                self._load_error = str(exc)
                log_with_fields(
                    logger, 40, "Failed to load whisper model", error=str(exc)
                )
                raise
            log_with_fields(
                logger,
                20,
                "Whisper model loaded",
                elapsed_sec=round(time.time() - started, 2),
            )

    @property
    def is_loaded(self) -> bool:
        return self._model is not None

    def transcribe(
        self, audio: np.ndarray, sample_rate: int = config.target_sample_rate
    ) -> list[TranscriptionSegment]:
        """Run a full transcription pass over a float32 mono audio array.
        Used both for the live-session rolling buffer and for batch file
        uploads."""
        if self._model is None:
            self.load()
        assert self._model is not None

        if audio.size == 0:
            return []

        segments, info = self._model.transcribe(
            audio,
            language=config.whisper_language if config.whisper_language != "auto" else None,
            beam_size=config.whisper_beam_size,
            vad_filter=True,
            vad_parameters={"min_silence_duration_ms": 500},
        )

        results: list[TranscriptionSegment] = []
        for seg in segments:
            avg_logprob = getattr(seg, "avg_logprob", -1.0)
            confidence = max(0.0, min(1.0, 1.0 + (avg_logprob / 5.0)))
            
            original_text = seg.text.strip()
            translated_text = ""
            
            # If not english, translate to english for intent analysis and UI display
            if info.language and info.language != "en":
                try:
                    from deep_translator import GoogleTranslator
                    translator = GoogleTranslator(source='auto', target='en')
                    translated_text = translator.translate(original_text)
                except Exception as e:
                    logger.warning(f"Translation failed: {e}")
            
            results.append(
                TranscriptionSegment(
                    text=original_text,
                    confidence=round(confidence, 4),
                    start_time_sec=seg.start,
                    end_time_sec=seg.end,
                    language=info.language,
                    is_final=True,
                    translated_text=translated_text,
                )
            )
        return results


stt_engine = SpeechToTextEngine()
