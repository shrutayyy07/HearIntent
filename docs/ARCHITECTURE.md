# Architecture

## Component overview

### Frontend (`frontend/`)

- **Framework**: Next.js 14 App Router, TypeScript, Tailwind CSS.
- **State**: Zustand (`lib/auth-store.ts`) persists JWTs to `localStorage`
  on the client only; auth is gated client-side via `useRequireAuth`.
- **Audio capture**: `hooks/useSpeechSocket.ts` uses `getUserMedia` plus
  `AudioContext`/`ScriptProcessorNode` to capture mono audio, downsamples it
  to 16kHz 16-bit PCM in-browser, and streams raw binary frames over a native
  `WebSocket` to `/ws/speech`.
- **Dashboard layout** (`app/dashboard/page.tsx`) mirrors the supplied
  screenshot: a 3-column grid with Audio Capture, System Status, and Upload
  on the left, Live Transcription in the center, Intent Analysis, Profile,
  and Activity Logs on the right.

### Backend (`backend/`)

- **Framework**: Spring Boot 3.3 on Java 21, fully reactive (WebFlux + R2DBC,
  no blocking JDBC on the request path; Flyway uses a separate JDBC
  connection only for migrations at startup).
- **Auth** (`auth/`): OTP generation/verification (BCrypt-hashed codes),
  JWT issuance (`security/JwtTokenProvider`), refresh-token sessions hashed
  with SHA-256 for deterministic lookup (`session/`).
- **Realtime bridge** (`websocket/`): `SpeechWebSocketHandler` accepts the
  WebFlux WebSocket connection, demultiplexes JSON control frames vs. binary
  PCM frames, and feeds a `Sinks.Many` that is exposed to
  `SpeechSessionOrchestrator` as a `Flux<AudioChunk>`.
- **gRPC client** (`grpc/`): `SpeechGrpcClient` wraps the generated
  `SpeechIntelligenceServiceStub`'s `StreamObserver` callback API in a
  Reactor `Flux`/`Mono`, so the rest of the backend never touches raw gRPC
  callbacks.
- **Rule engine** (`ruleengine/`): in-memory, priority-ordered list of
  `IntentRule` predicates evaluated against a `RuleContext`. Extensible at
  runtime via `registerRule`.
- **Persistence**: Flyway-versioned PostgreSQL schema (`users`, `otps`,
  `sessions`, `speech_sessions`, `transcripts`, `intents`, `logs`).
- **Logging** (`logging/`, `log/`): dual-write to Postgres and to
  newline-delimited flat files using `java.nio.channels.FileChannel` guarded
  by a per-file `ReentrantLock`, so concurrent requests never interleave or
  tear a line.

### AI worker (`ai-worker/`)

- **gRPC server** (`app/grpc_servicer.py`): implements
  `SpeechIntelligenceService` from `proto/speech.proto` -- bidirectional
  `StreamAudio` for live sessions, unary `ProcessAudioFile` for uploads, and
  `HealthCheck`.
- **STT** (`app/stt_engine.py`): `faster-whisper` `WhisperModel`, lazily
  loaded on a background thread at startup so the gRPC server can accept
  connections (and serve health checks) immediately.
- **Intent extraction** (`app/intent_engine.py`): zero-shot NLI
  classification (`facebook/bart-large-mnli` by default) against a fixed
  candidate-label set, combined with regex-based entity extraction (date,
  time, person name, currency amount).
- **Session buffering** (`app/session_buffer.py`): per-`session_id` rolling
  audio buffer; flushes to Whisper once around 1s of audio has accumulated
  or the client marks a chunk `is_final`.
- **File decoding** (`app/audio_utils.py`): `ffmpeg` subprocess strips and
  transcodes the audio track of arbitrary uploaded media (video or audio) to
  mono 16kHz PCM16, so the same STT/intent pipeline handles both live mic
  input and uploaded files.
- **Logging** (`app/session_report.py`, `app/logger.py`): JSON-line
  structured logs to stdout and file, and a per-session Markdown timeline
  (`/var/log/hearintent/sessions/session_<id>.md`).

## End-to-end data flow (live session)

1. Browser captures mic audio, downsamples to 16kHz PCM16, opens
   `wss://.../ws/speech?token=<jwt>`.
2. `SpeechWebSocketHandler` validates the JWT (best-effort; falls back to an
   anonymous session if absent/invalid), wraps each binary frame as an
   `AudioChunk` proto message, and emits it into a `Sinks.Many`.
3. `SpeechSessionOrchestrator.runLiveSession` persists a `speech_sessions`
   row, then calls `SpeechGrpcClient.streamAudio(...)`, which opens the
   bidirectional gRPC stream to the AI worker and bridges its
   `StreamObserver` callbacks into a `Flux<SpeechEvent>`.
4. The AI worker buffers PCM per session, runs Whisper once enough audio has
   accumulated, and for every finalized transcript: emits a
   `FINAL_TRANSCRIPTION` event, then runs intent extraction and emits an
   `INTENT_EXTRACTED` event.
5. Back in the backend, each event is persisted (`transcripts`/`intents`
   tables), the intent is evaluated against `IntentRuleEngine`, and the
   result is wrapped in a `WsOutboundMessage` and written back over the same
   WebSocket as JSON.
6. The frontend renders each `TRANSCRIPTION` event into the Live
   Transcription feed and each `INTENT` event into the Intent Analysis JSON
   viewer in real time.

## End-to-end data flow (file upload)

1. Browser POSTs a `multipart/form-data` file to `/api/v1/media/upload`.
2. `FileUploadController` -> `FileUploadService` reads the full file into
   memory, persists a `speech_sessions` row (`sourceType=FILE_UPLOAD`), and
   calls the AI worker's unary `ProcessAudioFile` RPC with the raw file bytes
   and `sample_rate=0` (a sentinel meaning "this is an encoded media
   container, not raw PCM").
3. The AI worker detects `sample_rate == 0`, pipes the bytes through
   `ffmpeg` to produce mono 16kHz PCM16, runs the full Whisper transcription
   pass over the whole file, then runs intent extraction per segment.
4. The worker returns one `AudioFileResponse` containing every transcript
   segment and every detected intent.
5. The backend persists each segment/intent, evaluates the rule engine for
   each intent, and returns a single JSON response to the browser containing
   the full transcript, all segments, and all intents with their rule
   actions.
