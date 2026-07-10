# API Reference

## REST endpoints (Spring Boot backend, base `http://localhost:8080`)

### Auth

| Method | Path                     | Auth | Body                              | Notes                          |
|--------|--------------------------|------|------------------------------------|---------------------------------|
| POST   | `/api/v1/auth/otp/request` | No | `{ "email": string }`              | Rate-limited 5/hour per email   |
| POST   | `/api/v1/auth/otp/resend`  | No | `{ "email": string }`              | 60s cooldown                    |
| POST   | `/api/v1/auth/otp/verify`  | No | `{ "email": string, "code": string }` | Returns JWT pair + user profile |
| POST   | `/api/v1/auth/refresh`     | No | `{ "refreshToken": string }`       | Rotates the refresh token       |
| POST   | `/api/v1/auth/logout`      | Yes | (none)                            | Revokes the active session      |

`otp/verify` response:

```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "expiresInSeconds": 3600,
  "userId": "b3b3...",
  "email": "user@example.com",
  "displayName": "user"
}
```

### Users

| Method | Path                 | Auth | Notes               |
|--------|----------------------|------|---------------------|
| GET    | `/api/v1/users/me`   | Yes  | Current user profile |

### Sessions / transcripts / intents

| Method | Path                                     | Auth | Notes                          |
|--------|-------------------------------------------|------|---------------------------------|
| GET    | `/api/v1/sessions`                        | Yes  | List the current user's sessions |
| GET    | `/api/v1/sessions/{id}`                   | Yes  | Single session metadata          |
| GET    | `/api/v1/sessions/{id}/transcripts`       | Yes  | All transcripts for a session    |
| GET    | `/api/v1/sessions/{id}/intents`           | Yes  | All intents for a session        |

### Media upload

| Method | Path                    | Auth | Body                          |
|--------|-------------------------|------|--------------------------------|
| POST   | `/api/v1/media/upload`  | Optional | `multipart/form-data`, field `file` |

Response:

```json
{
  "sessionId": "b3b3...",
  "success": true,
  "errorMessage": null,
  "segments": [{ "text": "schedule a meeting tomorrow at 3pm", "confidence": 0.95, "startTimeSec": 0.0, "endTimeSec": 2.4 }],
  "intents": [{ "intent": "schedule_meeting", "confidence": 0.95, "entities": { "date": "tomorrow", "time": "3pm" }, "ruleAction": "CREATE_CALENDAR_EVENT" }],
  "fullTranscript": "schedule a meeting tomorrow at 3pm",
  "durationSec": 2.4
}
```

### Health / logs

| Method | Path                   | Auth | Notes                                   |
|--------|-------------------------|------|-------------------------------------------|
| GET    | `/api/v1/health/status` | No   | Aggregate status incl. AI worker health   |
| GET    | `/api/v1/health/liveness` | No | Simple liveness probe                     |
| GET    | `/api/v1/logs?limit=50` | Yes  | Recent activity logs for the current user |

## WebSocket: `/ws/speech`

Connect with `?token=<accessToken>` query param (optional but recommended;
falls back to an anonymous session id if omitted or invalid).

**Client -> Server**

- One JSON text frame at session start:
  `{"type":"START_SESSION","sampleRate":16000}`
- Binary frames: raw 16-bit signed little-endian PCM, mono, matching the
  `sampleRate` declared above.
- Closing the socket signals end-of-session; the backend sends a final
  `is_final=true` chunk to the AI worker automatically.

**Server -> Client** (JSON text frames)

```json
{"type":"TRANSCRIPTION","payload":{"text":"...","confidence":0.97,"role":"user","timestamp":"..."},"timestampMs":...}
{"type":"INTENT","payload":{"intent":"...","confidence":0.9,"entities":{...},"ruleAction":"..."},"timestampMs":...}
{"type":"ERROR","payload":{"code":"...","message":"..."},"timestampMs":...}
{"type":"SESSION_CLOSED","payload":{"sessionId":"..."},"timestampMs":...}
```

## gRPC: `SpeechIntelligenceService` (`proto/speech.proto`)

| RPC                | Type                | Used by                                  |
|---------------------|---------------------|--------------------------------------------|
| `StreamAudio`        | bidirectional stream | Backend -> AI worker, live mic sessions    |
| `ProcessAudioFile`    | unary               | Backend -> AI worker, file upload sessions |
| `HealthCheck`         | unary               | Backend's `/api/v1/health/status`          |

See `proto/speech.proto` for the full message definitions
(`AudioChunk`, `SpeechEvent`, `TranscriptionResult`, `IntentResult`,
`AudioFileRequest`, `AudioFileResponse`, `HealthCheckRequest/Response`).
