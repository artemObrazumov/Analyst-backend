CREATE TABLE funnels
(
    id          UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    project_id  UUID        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    created_by  UUID        NOT NULL REFERENCES users (id),
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE funnel_steps
(
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    funnel_id   UUID         NOT NULL REFERENCES funnels (id) ON DELETE CASCADE,
    event_type  TEXT         NOT NULL,
    label       VARCHAR(255) NOT NULL,
    step_order  INT          NOT NULL,
    UNIQUE (funnel_id, step_order)
);
