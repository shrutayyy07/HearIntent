CREATE TABLE logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    session_id UUID,
    level VARCHAR(10) NOT NULL DEFAULT 'INFO', -- DEBUG | INFO | WARN | ERROR
    category VARCHAR(50) NOT NULL DEFAULT 'SYSTEM', -- AUTH | AUDIO | INTENT | SYSTEM | AUDIT
    message TEXT NOT NULL,
    metadata_json TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_logs_created_at ON logs (created_at DESC);
CREATE INDEX idx_logs_category ON logs (category);
CREATE INDEX idx_logs_user_id ON logs (user_id);
