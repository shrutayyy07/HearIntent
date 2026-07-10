import threading
import time

import numpy as np

from app.config import config


class SessionBuffer:
    """Accumulates incoming float32 PCM audio for a single live session and
    decides when enough audio has built up to run a transcription pass.
    Whisper performs best on utterance-level chunks rather than tiny
    fragments, so we buffer until either a minimum duration has been reached
    or the session is marked final."""

    def __init__(self, session_id: str) -> None:
        self.session_id = session_id
        self._chunks: list[np.ndarray] = []
        self._total_samples = 0
        self._lock = threading.Lock()
        self._last_flush_ts = time.time()
        self.last_sequence_number = -1

    def append(self, audio: np.ndarray) -> None:
        with self._lock:
            self._chunks.append(audio)
            self._total_samples += audio.size
            max_samples = int(
                config.session_buffer_max_sec * config.target_sample_rate
            )
            if self._total_samples > max_samples:
                overflow = self._total_samples - max_samples
                self._trim_front(overflow)

    def _trim_front(self, samples_to_drop: int) -> None:
        dropped = 0
        while dropped < samples_to_drop and self._chunks:
            head = self._chunks[0]
            remaining = samples_to_drop - dropped
            if head.size <= remaining:
                dropped += head.size
                self._chunks.pop(0)
            else:
                self._chunks[0] = head[remaining:]
                dropped += remaining
        self._total_samples -= dropped

    def duration_sec(self) -> float:
        return self._total_samples / float(config.target_sample_rate)

    def should_flush(self) -> bool:
        return self.duration_sec() >= config.min_chunk_duration_sec

    def snapshot(self) -> np.ndarray:
        with self._lock:
            if not self._chunks:
                return np.zeros(0, dtype=np.float32)
            return np.concatenate(self._chunks)

    def clear(self) -> None:
        with self._lock:
            self._chunks = []
            self._total_samples = 0
            self._last_flush_ts = time.time()


class SessionManager:
    def __init__(self) -> None:
        self._sessions: dict[str, SessionBuffer] = {}
        self._lock = threading.Lock()

    def get_or_create(self, session_id: str) -> SessionBuffer:
        with self._lock:
            if session_id not in self._sessions:
                self._sessions[session_id] = SessionBuffer(session_id)
            return self._sessions[session_id]

    def remove(self, session_id: str) -> None:
        with self._lock:
            self._sessions.pop(session_id, None)

    def active_count(self) -> int:
        with self._lock:
            return len(self._sessions)


session_manager = SessionManager()
