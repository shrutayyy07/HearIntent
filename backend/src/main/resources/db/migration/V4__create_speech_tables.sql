CREATE TABLE speech_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    source_type VARCHAR(20) NOT NULL DEFAULT 'LIVE', -- LIVE | FILE_UPLOAD
    original_filename VARCHAR(512),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | COMPLETED | ERROR
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at TIMESTAMPTZ,
    duration_sec DOUBLE PRECISION
);

CREATE INDEX idx_speech_sessions_user_id ON speech_sessions (user_id);

CREATE TABLE transcripts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES speech_sessions (id) ON DELETE CASCADE,
    text TEXT NOT NULL,
    confidence DOUBLE PRECISION NOT NULL DEFAULT 0,
    start_time_sec DOUBLE PRECISION,
    end_time_sec DOUBLE PRECISION,
    language VARCHAR(10),
    is_final BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_transcripts_session_id ON transcripts (session_id);

CREATE TABLE intents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES speech_sessions (id) ON DELETE CASCADE,
    transcript_id UUID REFERENCES transcripts (id) ON DELETE SET NULL,
    intent VARCHAR(100) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL DEFAULT 0,
    entities_json TEXT NOT NULL DEFAULT '{}',
    raw_text TEXT,
    rule_action VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_intents_session_id ON intents (session_id);
CREATE INDEX idx_intents_intent ON intents (intent);
