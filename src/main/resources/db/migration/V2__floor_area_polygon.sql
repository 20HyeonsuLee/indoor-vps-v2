CREATE TABLE IF NOT EXISTS floor_area_polygon (
    area_id             UUID        PRIMARY KEY,
    scan_id             UUID        NOT NULL REFERENCES scan_ingest(scan_id) ON DELETE CASCADE,
    build_job_id        UUID        NOT NULL REFERENCES build_job(build_job_id) ON DELETE CASCADE,
    floor_id            UUID        REFERENCES building_floor(floor_id) ON DELETE CASCADE,
    mark_session_id     TEXT        NOT NULL,
    polygon             geometry(PolygonZ, 0),
    source_mark_ids     JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_floor_area_polygon_scan_id
    ON floor_area_polygon(scan_id);

CREATE INDEX IF NOT EXISTS idx_floor_area_polygon_build_job_id
    ON floor_area_polygon(build_job_id);
