-- funnel_steps: label -> property_filters (ERD)
ALTER TABLE funnel_steps
    ADD COLUMN property_filters JSONB NOT NULL DEFAULT '{}';

ALTER TABLE funnel_steps
    DROP COLUMN label;

-- dashboards: ERD has only id, project_id, name, description
ALTER TABLE dashboards
    DROP COLUMN created_by;

ALTER TABLE dashboards
    DROP COLUMN created_at;

ALTER TABLE dashboards
    DROP COLUMN updated_at;

-- dashboard_charts -> dashboard_series (ERD)
ALTER TABLE dashboard_charts
    RENAME TO dashboard_series;

ALTER TABLE dashboard_series
    RENAME COLUMN title TO label;

ALTER TABLE dashboard_series
    RENAME COLUMN chart_order TO position;

ALTER TABLE dashboard_series
    ADD COLUMN period VARCHAR(20) NOT NULL DEFAULT '7d';

ALTER TABLE dashboard_series
    ADD COLUMN platform TEXT;

ALTER TABLE dashboard_series
    ADD COLUMN os_version TEXT;

ALTER TABLE dashboard_series
    ADD COLUMN app_version TEXT;

ALTER TABLE dashboard_series
    ADD COLUMN country CHAR(2);

ALTER TABLE dashboard_series
    ADD COLUMN property_filters JSONB NOT NULL DEFAULT '{}';

UPDATE dashboard_series
SET platform = filters ->> 'platform',
    os_version = filters ->> 'osVersion',
    app_version = filters ->> 'appVersion',
    country = LEFT(filters ->> 'country', 2),
    property_filters = COALESCE(filters -> 'properties', '{}'::jsonb)
                           || CASE
                                  WHEN filters ->> 'deviceId' IS NOT NULL
                                      THEN jsonb_build_object('deviceId', filters ->> 'deviceId')
                                  ELSE '{}'::jsonb END
                           || CASE
                                  WHEN filters ->> 'userId' IS NOT NULL
                                      THEN jsonb_build_object('userId', filters ->> 'userId')
                                  ELSE '{}'::jsonb END
WHERE filters IS NOT NULL
  AND filters != '{}'::jsonb;

ALTER TABLE dashboard_series
    DROP COLUMN filters;

ALTER TABLE dashboard_series
    DROP COLUMN chart_type;

ALTER TABLE dashboard_series
    DROP CONSTRAINT IF EXISTS dashboard_charts_dashboard_id_chart_order_key;

ALTER TABLE dashboard_series
    ADD CONSTRAINT dashboard_series_dashboard_id_position_key UNIQUE (dashboard_id, position);

-- events_hourly (ERD)
CREATE TABLE events_hourly
(
    bucket_time TIMESTAMPTZ NOT NULL,
    project_id  UUID        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    event_type  TEXT        NOT NULL,
    count       BIGINT      NOT NULL,
    PRIMARY KEY (bucket_time, project_id, event_type)
);

INSERT INTO events_hourly (bucket_time, project_id, event_type, count)
SELECT date_trunc('hour', occurred_at) AS bucket_time,
       project_id,
       event_type,
       COUNT(*)::bigint
FROM events
GROUP BY 1, 2, 3;

-- revoked_refresh_tokens (ERD)
CREATE TABLE revoked_refresh_tokens
(
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    revoked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_revoked_refresh_tokens_expires_at ON revoked_refresh_tokens (expires_at);
