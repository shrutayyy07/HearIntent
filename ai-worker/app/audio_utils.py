import io
import subprocess

import numpy as np

from app.config import config


def pcm16_bytes_to_float32(pcm_bytes: bytes) -> np.ndarray:
    """Convert little-endian signed 16-bit PCM bytes to a float32 numpy array
    scaled to [-1.0, 1.0], the format faster-whisper expects."""
    if not pcm_bytes:
        return np.zeros(0, dtype=np.float32)
    audio_i16 = np.frombuffer(pcm_bytes, dtype="<i2")
    audio_f32 = audio_i16.astype(np.float32) / 32768.0
    return audio_f32


def resample_audio(
    audio: np.ndarray, orig_sr: int, target_sr: int = config.target_sample_rate
) -> np.ndarray:
    """Resample a float32 mono audio array using librosa (handles 44.1kHz -> 16kHz
    and any other arbitrary input rate from a browser MediaRecorder/AudioContext)."""
    if orig_sr == target_sr or audio.size == 0:
        return audio
    import librosa

    return librosa.resample(audio, orig_sr=orig_sr, target_sr=target_sr)


def decode_media_to_pcm16(
    media_bytes: bytes, target_sr: int = config.target_sample_rate
) -> tuple[bytes, int]:
    """Decode an arbitrary audio/video file (mp4, mov, webm, mp3, wav, etc.) to
    mono 16-bit PCM at target_sr using ffmpeg. Used for the 'upload a video'
    intent-detection flow: ffmpeg extracts and transcodes the audio track
    regardless of the container/codec.

    Returns (pcm_bytes, sample_rate).
    """
    import tempfile
    import os
    with tempfile.NamedTemporaryFile(delete=False) as tmp:
        tmp.write(media_bytes)
        tmp_path = tmp.name

    try:
        process = subprocess.run(
            [
                "ffmpeg",
                "-y",
                "-hide_banner",
                "-loglevel",
                "error",
                "-i",
                tmp_path,
                "-vn",
                "-ac",
                "1",
                "-ar",
                str(target_sr),
                "-f",
                "s16le",
                "pipe:1",
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=True,
        )
        return process.stdout, target_sr
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)


def float32_to_pcm16_bytes(audio: np.ndarray) -> bytes:
    clipped = np.clip(audio, -1.0, 1.0)
    audio_i16 = (clipped * 32767.0).astype("<i2")
    return audio_i16.tobytes()
