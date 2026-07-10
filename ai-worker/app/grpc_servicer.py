import time

import grpc
import numpy as np

from app.audio_utils import pcm16_bytes_to_float32, resample_audio
from app.config import config
from app.generated import speech_pb2, speech_pb2_grpc
from app.intent_engine import intent_engine
from app.logger import get_logger, log_with_fields
from app.session_buffer import session_manager
from app.session_report import (
    append_session_event,
    finalize_session_report,
    init_session_report,
)
from app.stt_engine import stt_engine

logger = get_logger("grpc_servicer")

_START_TIME = time.time()


class SpeechIntelligenceServicer(speech_pb2_grpc.SpeechIntelligenceServiceServicer):
    def StreamAudio(self, request_iterator, context):
        session_id = None
        try:
            for chunk in request_iterator:
                session_id = chunk.session_id
                buffer = session_manager.get_or_create(session_id)

                if buffer.last_sequence_number == -1:
                    init_session_report(session_id)
                    log_with_fields(
                        logger, 20, "Session started", session_id=session_id
                    )

                buffer.last_sequence_number = chunk.sequence_number

                audio_f32 = pcm16_bytes_to_float32(chunk.pcm_data)
                if chunk.sample_rate and chunk.sample_rate != config.target_sample_rate:
                    audio_f32 = resample_audio(audio_f32, chunk.sample_rate)
                buffer.append(audio_f32)

                if buffer.should_flush() or chunk.is_final:
                    snapshot = buffer.snapshot()
                    segments = stt_engine.transcribe(snapshot)

                    for seg in segments:
                        if not seg.text:
                            continue
                        append_session_event(
                            session_id, "TRANSCRIPTION", seg.text
                        )
                        yield speech_pb2.SpeechEvent(
                            session_id=session_id,
                            type=speech_pb2.SpeechEvent.FINAL_TRANSCRIPTION,
                            transcription=speech_pb2.TranscriptionResult(
                                text=seg.text,
                                confidence=seg.confidence,
                                start_time_sec=seg.start_time_sec,
                                end_time_sec=seg.end_time_sec,
                                language=seg.language,
                                is_final=True,
                                translated_text=seg.translated_text,
                            ),
                        )

                        intent_text = seg.translated_text if seg.translated_text else seg.text
                        intent_result = intent_engine.extract(intent_text)
                        emotion = intent_result.entities.get("emotion")
                        log_text = f"{intent_result.intent} ({intent_result.confidence})"
                        if emotion:
                            log_text += f" [Emotion: {emotion}]"
                        
                        append_session_event(
                            session_id,
                            "INTENT",
                            log_text,
                        )
                        yield speech_pb2.SpeechEvent(
                            session_id=session_id,
                            type=speech_pb2.SpeechEvent.INTENT_EXTRACTED,
                            intent=speech_pb2.IntentResult(
                                intent=intent_result.intent,
                                confidence=intent_result.confidence,
                                entities_json=intent_result.to_entities_json(),
                                raw_text=intent_result.raw_text,
                                extracted_at_ms=intent_result.extracted_at_ms,
                            ),
                        )

                    buffer.clear()

                if chunk.is_final:
                    yield speech_pb2.SpeechEvent(
                        session_id=session_id,
                        type=speech_pb2.SpeechEvent.SESSION_CLOSED,
                    )
                    finalize_session_report(
                        session_id, "Session closed normally by client."
                    )
                    session_manager.remove(session_id)

        except Exception as exc:  # noqa: BLE001
            log_with_fields(
                logger, 40, "StreamAudio error", error=str(exc), session_id=session_id
            )
            yield speech_pb2.SpeechEvent(
                session_id=session_id or "unknown",
                type=speech_pb2.SpeechEvent.ERROR,
                error=speech_pb2.ErrorDetail(code="WORKER_ERROR", message=str(exc)),
            )

    def ProcessAudioFile(self, request, context):
        session_id = request.session_id
        try:
            init_session_report(session_id)
            log_with_fields(
                logger,
                20,
                "Processing uploaded file",
                session_id=session_id,
                filename=request.original_filename,
                request_pcm_size=len(request.pcm_data)
            )

            if request.sample_rate == 0:
                log_with_fields(logger, 20, "Starting ffmpeg decode", session_id=session_id)
                from app.audio_utils import decode_media_to_pcm16
                pcm_bytes, sr = decode_media_to_pcm16(bytes(request.pcm_data))
                log_with_fields(logger, 20, "ffmpeg decode complete", session_id=session_id, bytes_len=len(pcm_bytes))
                audio_f32 = pcm16_bytes_to_float32(pcm_bytes)
            else:
                log_with_fields(logger, 20, "Using raw pcm data", session_id=session_id)
                audio_f32 = pcm16_bytes_to_float32(request.pcm_data)
                if request.sample_rate != config.target_sample_rate:
                    audio_f32 = resample_audio(audio_f32, request.sample_rate)

            log_with_fields(logger, 20, "Starting transcription", session_id=session_id, audio_size=audio_f32.size)
            segments = stt_engine.transcribe(audio_f32)
            log_with_fields(logger, 20, "Transcription generator created", session_id=session_id)

            full_text_parts = []
            proto_segments = []
            proto_intents = []

            for seg in segments:
                log_with_fields(logger, 20, "Transcription segment received", session_id=session_id)
                if not context.is_active():
                    log_with_fields(logger, 30, "Client disconnected, aborting ProcessAudioFile", session_id=session_id)
                    return speech_pb2.AudioFileResponse(session_id=session_id, success=False, error_message="Client disconnected")

                if not seg.text:
                    continue
                full_text_parts.append(seg.text)
                append_session_event(session_id, "TRANSCRIPTION", seg.text)
                proto_segments.append(
                    speech_pb2.TranscriptionResult(
                        text=seg.text,
                        confidence=seg.confidence,
                        start_time_sec=seg.start_time_sec,
                        end_time_sec=seg.end_time_sec,
                        language=seg.language,
                        is_final=True,
                        translated_text=seg.translated_text,
                    )
                )

                intent_text = seg.translated_text if seg.translated_text else seg.text
                intent_result = intent_engine.extract(intent_text)
                emotion = intent_result.entities.get("emotion")
                log_text = f"{intent_result.intent} ({intent_result.confidence})"
                if emotion:
                    log_text += f" [Emotion: {emotion}]"

                append_session_event(
                    session_id,
                    "INTENT",
                    log_text,
                )
                proto_intents.append(
                    speech_pb2.IntentResult(
                        intent=intent_result.intent,
                        confidence=intent_result.confidence,
                        entities_json=intent_result.to_entities_json(),
                        raw_text=intent_result.raw_text,
                        extracted_at_ms=intent_result.extracted_at_ms,
                    )
                )

            full_transcript = " ".join(full_text_parts)
            duration_sec = audio_f32.size / float(config.target_sample_rate)

            finalize_session_report(
                session_id,
                f"Processed uploaded file '{request.original_filename}', "
                f"duration {duration_sec:.1f}s, {len(proto_segments)} segments.",
            )

            return speech_pb2.AudioFileResponse(
                session_id=session_id,
                segments=proto_segments,
                full_transcript=full_transcript,
                intents=proto_intents,
                duration_sec=duration_sec,
                success=True,
            )
        except Exception as exc:  # noqa: BLE001
            log_with_fields(
                logger, 40, "ProcessAudioFile error", error=str(exc), session_id=session_id
            )
            return speech_pb2.AudioFileResponse(
                session_id=session_id,
                success=False,
                error_message=str(exc),
            )

    def HealthCheck(self, request, context):
        return speech_pb2.HealthCheckResponse(
            healthy=True,
            worker_version=config.worker_version,
            whisper_model_loaded=stt_engine.is_loaded,
            intent_model_loaded=intent_engine.is_loaded,
            uptime_sec=time.time() - _START_TIME,
        )
