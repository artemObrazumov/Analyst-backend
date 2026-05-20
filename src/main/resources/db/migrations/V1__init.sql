-- TimescaleDB extension
DO $$
BEGIN
    BEGIN
        CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;
    EXCEPTION WHEN OTHERS THEN
        RAISE WARNING 'TimescaleDB not available, running as plain PostgreSQL: %', SQLERRM;
    END;
END $$;

-- Admin users
CREATE TABLE IF NOT EXISTS users (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    email         TEXT        NOT NULL UNIQUE,
    name          TEXT        NOT NULL,
    password_hash TEXT        NOT NULL,
    role          TEXT        NOT NULL DEFAULT 'admin',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Registered apps (each has an API key for the SDK)
CREATE TABLE IF NOT EXISTS apps (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name       TEXT        NOT NULL,
    api_key    TEXT        NOT NULL UNIQUE,
    owner_id   UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    is_active  BOOLEAN     NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Events (main time-series table)
CREATE TABLE IF NOT EXISTS events (
    id          BIGSERIAL,
    event_id    UUID        NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    app_id      UUID        NOT NULL REFERENCES apps(id),
    session_id  UUID,
    device_id   TEXT        NOT NULL,
    user_id     TEXT,

    event_type  TEXT        NOT NULL,

    platform    TEXT,
    app_version TEXT,
    os_version  TEXT,
    country     CHAR(2),

    properties  JSONB       NOT NULL DEFAULT '{}',

    PRIMARY KEY (id, occurred_at)
);

-- Indexes
CREATE UNIQUE INDEX IF NOT EXISTS events_event_id_idx    ON events(event_id);
CREATE INDEX        IF NOT EXISTS events_app_type_time   ON events(app_id, event_type, occurred_at DESC);
CREATE INDEX        IF NOT EXISTS events_properties_gin  ON events USING GIN (properties);

-- Convert to hypertable and add compression (only when TimescaleDB is present)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        PERFORM create_hypertable('events', 'occurred_at', if_not_exists => true);
        PERFORM add_compression_policy('events', INTERVAL '7 days', if_not_exists => true);
    END IF;
END $$;
