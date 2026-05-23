DROP TABLE IF EXISTS events CASCADE;
DROP TABLE IF EXISTS apps CASCADE;

CREATE TABLE events (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
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
    properties  JSONB       NOT NULL DEFAULT '{}'
);

CREATE INDEX events_project_time ON events(project_id, received_at DESC);
CREATE INDEX events_project_type ON events(project_id, event_type);
