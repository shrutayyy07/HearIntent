import signal
import sys
import threading
from concurrent import futures

import grpc

from app.config import config
from app.generated import speech_pb2_grpc
from app.grpc_servicer import SpeechIntelligenceServicer
from app.intent_engine import intent_engine
from app.logger import get_logger, log_with_fields
from app.stt_engine import stt_engine

logger = get_logger("main")


def _preload_models() -> None:
    try:
        stt_engine.load()
    except Exception as exc:  # noqa: BLE001
        log_with_fields(logger, 40, "STT model preload failed", error=str(exc))
    try:
        intent_engine.load()
    except Exception as exc:  # noqa: BLE001
        log_with_fields(logger, 40, "Intent model preload failed", error=str(exc))


def serve() -> None:
    server = grpc.server(
        futures.ThreadPoolExecutor(max_workers=config.max_workers),
        options=[
            ("grpc.max_send_message_length", 200 * 1024 * 1024),
            ("grpc.max_receive_message_length", 200 * 1024 * 1024),
        ],
    )
    speech_pb2_grpc.add_SpeechIntelligenceServiceServicer_to_server(
        SpeechIntelligenceServicer(), server
    )

    bind_address = f"{config.grpc_host}:{config.grpc_port}"
    server.add_insecure_port(bind_address)
    server.start()

    log_with_fields(
        logger, 20, "gRPC server started", address=bind_address
    )

    preload_thread = threading.Thread(target=_preload_models, daemon=True)
    preload_thread.start()

    def _graceful_shutdown(signum, frame):  # noqa: ANN001
        log_with_fields(logger, 20, "Shutting down gRPC server", signal=signum)
        server.stop(grace=5)
        sys.exit(0)

    signal.signal(signal.SIGTERM, _graceful_shutdown)
    signal.signal(signal.SIGINT, _graceful_shutdown)

    server.wait_for_termination()


if __name__ == "__main__":
    serve()
