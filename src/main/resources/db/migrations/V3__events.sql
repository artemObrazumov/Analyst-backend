DROP TABLE IF EXISTS events CASCADE;
DROP TABLE IF EXISTS apps CASCADE;

CREATE TABLE events (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    project_id  UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    session_id  UUID,
    device_id   TEXT,
    user_id     TEXT,
    event_type  TEXT        NOT NULL,
    platform    TEXT,
    app_version TEXT,
    os_version  TEXT,
    country     CHAR(2),
    properties  JSONB       NOT NULL DEFAULT '{}',
    PRIMARY KEY (id, occurred_at)
);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        PERFORM create_hypertable('events', 'occurred_at', if_not_exists => true);
        ALTER TABLE events SET (
            timescaledb.compress,
            timescaledb.compress_segmentby = 'project_id',
            timescaledb.compress_orderby = 'occurred_at DESC'
        );
        PERFORM add_compression_policy('events', INTERVAL '7 days', if_not_exists => true);
    END IF;
END $$;

CREATE INDEX events_project_time ON events(project_id, occurred_at DESC);
CREATE INDEX events_project_type ON events(project_id, event_type, occurred_at DESC);
