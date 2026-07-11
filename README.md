## HearIntent

A full-stack real-time speech intelligence platform. Users speak into their browser mic (or
upload a video/audio file), and the system transcribes it, translates it to English if needed,
and runs a generative model over each segment to extract the speaker's **intent** and
**emotion**, then applies a rule engine to turn certain intents into action codes.

---

### What it does

- Users sign in (email + password, or phone OTP) and open a live dashboard.
- Speaking into the mic streams raw audio to the backend over a WebSocket, which forwards it
  to a Python worker over gRPC for transcription + intent extraction, streamed back in
  real time.
- Uploading a video/audio file runs the same pipeline in batch via a unary gRPC call, decoding
  any container with ffmpeg first.
- A priority-ordered rule engine maps intents + extracted entities to action codes (e.g.
  `schedule` + `meeting` + a time entity → `CREATE_CALENDAR_EVENT`).
- Every transcript, intent, and system event is persisted, so past sessions can be reloaded.

---

### Tech Stack

| Layer | Technology |
|---|---|
| Frontend | Next.js 14 (App Router), TypeScript, Tailwind, Zustand, Radix UI |
| Backend | Spring Boot 3, Java 21, WebFlux (reactive), R2DBC, Flyway |
| Auth & Database | PostgreSQL 16, JWT (access + refresh), BCrypt |
| AI Worker | Python, gRPC, faster-whisper, ffmpeg, librosa |
| Speech-to-Text | faster-whisper (`small`, CPU/int8 by default) |
| Translation | deep-translator (Google Translate, non-EN → EN) |
| Intent/Emotion | transformers text-generation pipeline (chat-style instruct model) |
| Service Contract | Protocol Buffers / gRPC (`speech.proto`) |

---

### Architecture

```
Browser (mic / file upload)
       |
       |  WebSocket (binary PCM + JSON events)   REST (auth, sessions, upload)
       v
Next.js frontend  ------>  Spring Boot backend
                                                                    |
                                                                    |  gRPC (bidi stream +
                                                                    |   unary batch RPC)
                                                                    v
                                                              Python AI worker
                                                                    |
                                                                    |- faster-whisper  → STT
                                                                    |- deep-translator → translation
                                                                    |- transformers    → intent + emotion
                                                                    v
                                                              back to backend
                                                                    |
                                                                    v
                                                              PostgreSQL
                                                     (users, otps, sessions, speech_sessions,
                                                              transcripts, intents, logs)
```

---

###  Project Structure

```
HearIntent/
├── frontend/
│   ├── app/
│   │   ├── page.tsx            (redirects → /login or /dashboard)
│   │   ├── login/
│   │   ├── register/
│   │   ├── verify/
│   │   ├── forgot-password/
│   │   └── dashboard/
│   ├── components/
│   │   ├── auth/
│   │   └── dashboard/
│   │       ├── AudioCaptureCard.tsx
│   │       ├── LiveTranscriptionCard.tsx
│   │       ├── IntentAnalysisCard.tsx
│   │       ├── FileUploadCard.tsx
│   │       ├── SystemStatusCard.tsx
│   │       ├── ActivityLogsCard.tsx
│   │       └── UserProfileCard.tsx
│   ├── hooks/
│   │   ├── useSpeechSocket.ts
│   │   └── useRequireAuth.ts
│   ├── lib/
│   │   ├── api-client.ts
│   │   └── auth-store.ts
│   └── .env.example
├── backend/
│   └── src/main/java/com/hearintent/backend/
│       ├── auth/                (AuthController, AuthService, OtpService, MailService)
│       ├── websocket/           (SpeechWebSocketHandler, SpeechSessionOrchestrator)
│       ├── grpc/                (SpeechGrpcClient)
│       ├── upload/              (FileUploadController, FileUploadService)
│       ├── ruleengine/          (IntentRuleEngine, IntentRule)
│       ├── session/, transcript/, intent/, log/
│       ├── security/            (JwtTokenProvider, JwtAuthenticationWebFilter)
│       └── resources/db/migration/  (Flyway SQL, V1–V7)
├── ai-worker/
│   └── app/
│       ├── main.py
│       ├── grpc_servicer.py
│       ├── stt_engine.py        (faster-whisper wrapper)
│       ├── intent_engine.py     (transformers text-generation pipeline)
│       ├── session_buffer.py
│       ├── audio_utils.py       (ffmpeg / librosa)
│       └── session_report.py
├── proto/
│   └── speech.proto
├── docker-compose.yml
├── Makefile
└── list_users.js
```

---

### Getting Started (Local)

#### Prerequisites
- Docker + Docker Compose (recommended), **or**
- Node.js 18+, Java 21, Maven, Python 3.11+, and a local PostgreSQL 16 instance
- ffmpeg on `PATH` (required by the AI worker for file uploads)

#### 1. Clone the repo
```bash
git clone https://github.com/shrutayyy07/HearIntent.git
cd HearIntent
```

#### 2. Set up environment variables
```bash
cp .env.example .env
```

Key variables to review: `JWT_SECRET`, `OTP_MOCK_MODE` (leave `true` locally — OTPs print to
the backend logs instead of sending real email), `WHISPER_MODEL_SIZE`, `INTENT_MODEL_NAME`.

#### 3. Run everything with Docker
```bash
docker-compose up --build
```

This starts Postgres, the AI worker (downloads faster-whisper + intent model weights from
Hugging Face on first boot), the Spring Boot backend, and the Next.js frontend.

App runs at `http://localhost:3000`. Backend runs at `http://localhost:8080`.

#### 4. Or run services individually
```bash
# Postgres
docker run -d --name hearintent-postgres -e POSTGRES_DB=hearintent \
  -e POSTGRES_USER=hearintent -e POSTGRES_PASSWORD=hearintent \
  -p 5432:5432 postgres:16-alpine

make dev-ai-worker      # pip install + python -m app.main
make dev-backend        # mvn spring-boot:run
make dev-frontend       # npm install --legacy-peer-deps + npm run dev
```

---

### API Endpoints
| Method | Endpoint |
|---|---|
| POST | `/api/v1/auth/register` |
| POST | `/api/v1/auth/login/email` |
| POST | `/api/v1/auth/login/phone/request` |
| POST | `/api/v1/auth/login/phone/verify` |
| POST | `/api/v1/auth/password/forgot/request` |
| POST | `/api/v1/auth/password/forgot/check` |
| POST | `/api/v1/auth/password/forgot/verify` |
| POST | `/api/v1/auth/refresh` |
| POST | `/api/v1/auth/logout` |
| GET | `/api/v1/users/me` |
| GET | `/api/v1/health/status` |
| GET | `/api/v1/health/liveness` |
| POST | `/api/v1/media/upload` |
| GET | `/api/v1/sessions` |
| GET | `/api/v1/sessions/{id}` |
| GET | `/api/v1/sessions/{id}/transcripts` |
| GET | `/api/v1/sessions/{id}/intents` |
| GET | `/api/v1/logs` |
| WS | `/ws/speech?token=` |

---

### Environment Variables Reference

#### Backend
| Variable | Description |
|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | Postgres connection |
| `JWT_SECRET` | Signing key for access/refresh tokens (min 256 bits) |
| `JWT_ACCESS_TTL_MIN` | Access token lifetime (default 60 min) |
| `JWT_REFRESH_TTL_DAYS` | Refresh token lifetime (default 7 days) |
| `OTP_MOCK_MODE` | If `true`, OTPs are logged instead of emailed |
| `OTP_MAIL_FROM` / `SMTP_HOST` / `SMTP_PORT` / `SMTP_USERNAME` / `SMTP_PASSWORD` | Outbound email for real OTPs |
| `AI_WORKER_HOST` / `AI_WORKER_PORT` | gRPC address of the Python worker |
| `FLAT_LOG_DIR` | Path for on-disk audit/error logs |
| `CORS_ALLOWED_ORIGINS` | Allowed frontend origin(s) |

#### AI Worker
| Variable | Description |
|---|---|
| `GRPC_HOST` / `GRPC_PORT` | gRPC bind address (default `0.0.0.0:50051`) |
| `WHISPER_MODEL_SIZE` | faster-whisper model size (default `small`) |
| `WHISPER_DEVICE` / `WHISPER_COMPUTE_TYPE` | `cpu`/`cuda`, `int8`/etc. |
| `INTENT_MODEL_NAME` | HF model id for intent/emotion generation |
| `INTENT_DEVICE` | `cpu` or `cuda` |
| `LOG_DIR` | Path for session Markdown reports and logs |

#### Frontend
| Variable | Description |
|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | Backend REST base URL |
| `NEXT_PUBLIC_WS_BASE_URL` | Backend WebSocket base URL |

---

### Notes from the current codebase
- `INTENT_MODEL_NAME` in `docker-compose.yml` defaults to `facebook/bart-large-mnli`, a
  classification model — but `intent_engine.py` runs a `text-generation` chat pipeline, which
  expects an instruct/chat model (e.g. the `Qwen/Qwen2.5-0.5B-Instruct` default in `config.py`).
- Entity extraction (dates, times, amounts) is currently stubbed to always return `{}`; only
  `emotion` is populated, so entity-dependent rules in the rule engine won't fire on live data.
- Registration currently auto-verifies new accounts without an OTP step.
