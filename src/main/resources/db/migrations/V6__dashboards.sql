CREATE TABLE dashboards
(
    id          UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    project_id  UUID        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    created_by  UUID        NOT NULL REFERENCES users (id),
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE dashboard_charts
(
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dashboard_id UUID         NOT NULL REFERENCES dashboards (id) ON DELETE CASCADE,
    title        VARCHAR(255) NOT NULL,
    chart_type   VARCHAR(20)  NOT NULL DEFAULT 'line',
    event_type   TEXT         NOT NULL,
    chart_order  INT          NOT NULL,
    UNIQUE (dashboard_id, chart_order)
);
