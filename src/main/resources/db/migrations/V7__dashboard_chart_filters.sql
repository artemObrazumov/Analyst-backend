ALTER TABLE dashboard_charts
    ADD COLUMN filters JSONB NOT NULL DEFAULT '{}';
