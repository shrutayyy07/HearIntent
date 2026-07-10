import os
from dataclasses import dataclass


@dataclass(frozen=True)
class WorkerConfig:
    grpc_host: str = os.getenv("GRPC_HOST", "0.0.0.0")
    grpc_port: int = int(os.getenv("GRPC_PORT", "50051"))
    max_workers: int = int(os.getenv("GRPC_MAX_WORKERS", "10"))

    whisper_model_size: str = os.getenv("WHISPER_MODEL_SIZE", "small")
    whisper_device: str = os.getenv("WHISPER_DEVICE", "cpu")
    whisper_compute_type: str = os.getenv("WHISPER_COMPUTE_TYPE", "int8")
    whisper_language: str = os.getenv("WHISPER_LANGUAGE", "en")
    whisper_beam_size: int = int(os.getenv("WHISPER_BEAM_SIZE", "5"))

    intent_model_name: str = os.getenv(
        "INTENT_MODEL_NAME", "Qwen/Qwen2.5-0.5B-Instruct"
    )
    intent_device: str = os.getenv("INTENT_DEVICE", "cpu")
    intent_confidence_threshold: float = float(
        os.getenv("INTENT_CONFIDENCE_THRESHOLD", "0.35")
    )

    target_sample_rate: int = int(os.getenv("TARGET_SAMPLE_RATE", "16000"))
    min_chunk_duration_sec: float = float(os.getenv("MIN_CHUNK_DURATION_SEC", "1.0"))
    session_buffer_max_sec: float = float(os.getenv("SESSION_BUFFER_MAX_SEC", "30.0"))

    log_dir: str = os.getenv("LOG_DIR", "/var/log/hearintent")
    worker_version: str = os.getenv("WORKER_VERSION", "1.0.0")


config = WorkerConfig()
