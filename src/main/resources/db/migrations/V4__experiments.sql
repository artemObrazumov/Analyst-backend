CREATE TABLE experiments
(
    id          UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    project_id  UUID        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    created_by  UUID        NOT NULL REFERENCES users (id),
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    status      VARCHAR(20) NOT NULL     DEFAULT 'draft',
    result      TEXT,
    created_at  TIMESTAMPTZ NOT NULL     DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL     DEFAULT NOW()
);

CREATE TABLE experiment_events
(
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    experiment_id UUID NOT NULL REFERENCES experiments (id) ON DELETE CASCADE,
    event_type    TEXT NOT NULL,
    note          TEXT
);

CREATE TABLE experiment_groups
(
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    experiment_id  UUID         NOT NULL REFERENCES experiments (id) ON DELETE CASCADE,
    property_key   TEXT         NOT NULL,
    property_value TEXT         NOT NULL,
    label          VARCHAR(255) NOT NULL
);
