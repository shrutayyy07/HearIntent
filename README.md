# HearIntent

A production-ready real-time speech intelligence platform: live microphone transcription and intent extraction, plus batch intent detection from uploaded video/audio files.

```
Frontend (Next.js 14 + TypeScript + Tailwind)
        |  WebSocket (binary PCM frames + JSON events)
        v
Spring Boot Backend (Java 21, WebFlux, Spring Security, JWT)
        |  gRPC (bidirectional stream + unary batch RPC)
        v
Python AI Worker (Python 3.11, grpc.io)
        |
        |- faster-whisper            -> speech-to-text
        |- transformers zero-shot    -> intent classification
        |- regex entity extraction   -> dates / times / names / amounts
        |- Markdown + flat-file logs -> session reports
        v
PostgreSQL (users, otps, sessions, speech_sessions, transcripts, intents, logs)
```

## Services

| Service     | Stack                                            | Port  |
|-------------|---------------------------------------------------|-------|
| `frontend`  | Next.js 14, TypeScript, Tailwind, App Router       | 3000  |
| `backend`   | Spring Boot 3 / Java 21, WebFlux, R2DBC, Flyway    | 8080  |
| `ai-worker` | Python 3.11, gRPC, faster-whisper, transformers    | 50051 |
| `postgres`  | PostgreSQL 16                                      | 5432  |

## Quick start

```bash
git clone <this-repo>
cd hearintent
cp .env.example .env
docker-compose up --build
```

Then open `http://localhost:3000`. On first login, OTPs are printed to the
backend container logs by default (`OTP_MOCK_MODE=true`), so no SMTP server
is required for local development. To send real emails, set
`OTP_MOCK_MODE=false` and provide `SMTP_USERNAME` / `SMTP_PASSWORD` in `.env`.

> **First boot note:** the AI worker downloads the faster-whisper (`small`,
> ~500MB) and `facebook/bart-large-mnli` (~1.6GB) model weights from Hugging
> Face on first start. This requires outbound internet access from the
> `ai-worker` container and can take several minutes depending on bandwidth.
> Subsequent restarts reuse the `whisper_model_cache` volume.

## Authentication flow

1. `POST /api/v1/auth/otp/request` - request a 6-digit OTP for an email.
2. `POST /api/v1/auth/otp/verify` - verify the OTP; returns a JWT access
   token (60 min) and refresh token (7 days), creating the user on first
   login.
3. `POST /api/v1/auth/otp/resend` - resend, subject to a 60s cooldown.
4. `POST /api/v1/auth/refresh` - exchange a refresh token for a new pair.
5. `POST /api/v1/auth/logout` - revoke the current session (requires
   `Authorization: Bearer <accessToken>`).

OTP requests are rate-limited to 5/hour per email (in-memory sliding window).

## Real-time speech flow

The frontend opens `ws://<backend>/ws/speech?token=<accessToken>`, sends a
JSON control frame (`{"type":"START_SESSION","sampleRate":16000}`), then
streams raw 16-bit PCM binary frames captured from the microphone via the Web
Audio API. The backend forwards each frame over a gRPC bidirectional stream
to the Python worker, which buffers about 1s of audio, runs faster-whisper,
then runs zero-shot intent classification on each finalized transcript
segment. Results stream back over the same WebSocket as JSON events:

```json
{"type":"TRANSCRIPTION","payload":{"text":"...","confidence":0.97}}
{"type":"INTENT","payload":{"intent":"schedule_meeting","confidence":0.97,"entities":{"date":"tomorrow","time":"3 PM"},"ruleAction":"CREATE_CALENDAR_EVENT"}}
```

## Upload-based intent detection

Instead of speaking live, a user can upload a video or audio file
(`POST /api/v1/media/upload`, multipart `file` field). The backend streams
the raw bytes to the AI worker's `ProcessAudioFile` RPC, which invokes
`ffmpeg` to extract/transcode the audio track to mono 16kHz PCM regardless of
the original container/codec (mp4, mov, webm, mp3, wav, etc.), then runs the
same STT + intent pipeline as the live path. The full transcript and every
detected intent are returned in one response and persisted to the database.

## Rule engine

`IntentRuleEngine` (Java) evaluates each extracted intent against a
priority-ordered set of rules (e.g. `schedule_meeting` plus a `time` entity
maps to `CREATE_CALENDAR_EVENT`; an `amount` entity of 10,000 or more maps to
`FIRE_HIGH_PRIORITY_ALERT`). The matched action code is attached to the
intent before it is persisted and broadcast to the frontend, and new rules
can be registered via `IntentRuleEngine#registerRule` without touching the
gRPC/WebSocket plumbing.

## Logging

- **Database**: every transcript, intent, and system/audit/error event is
  written to PostgreSQL (`transcripts`, `intents`, `logs` tables).
- **Flat files**: `FlatFileLogService` (backend) and `session_report.py`
  (AI worker) append thread-safe, atomic audit/error logs and per-session
  Markdown transcripts to disk (`/var/log/hearintent/...` inside each
  container, backed by named Docker volumes).

## Project structure

```
hearintent/
|-- docker-compose.yml
|-- proto/speech.proto              (shared gRPC contract for Java + Python)
|-- frontend/                       (Next.js 14 dashboard)
|-- backend/                        (Spring Boot WebFlux backend)
|   `-- src/main/resources/db/migration/   (Flyway SQL migrations)
|-- ai-worker/                      (Python gRPC AI worker)
`-- docs/                           (architecture & API reference)
```

See `docs/ARCHITECTURE.md` for a deeper component breakdown and
`docs/API.md` for the full REST/WebSocket/gRPC reference.

## Running services individually (without Docker)

```bash
# Postgres (simplest to still run via Docker)
docker run -d --name hearintent-postgres -e POSTGRES_DB=hearintent \
  -e POSTGRES_USER=hearintent -e POSTGRES_PASSWORD=hearintent \
  -p 5432:5432 postgres:16-alpine

# AI worker
cd ai-worker
pip install -r requirements.txt --break-system-packages
python -m app.main

# Backend
cd backend
mvn spring-boot:run

# Frontend
cd frontend
npm install --legacy-peer-deps
npm run dev
```

## Known local-environment caveats

- The AI worker needs `ffmpeg` on the host/container `PATH` (already
  installed in the provided Dockerfile) for the file-upload flow.
- `faster-whisper` and the zero-shot `transformers` pipeline run on CPU by
  default (`WHISPER_DEVICE=cpu`, `INTENT_DEVICE=cpu`); set both to `cuda` if
  an NVIDIA GPU and a matching CUDA-enabled `torch`/`ctranslate2` build are
  available, for materially faster inference.
- The Maven build fetches a matching native `protoc` binary via
  `os-maven-plugin` at build time, so the first `mvn package` requires
  network access to Maven Central.
