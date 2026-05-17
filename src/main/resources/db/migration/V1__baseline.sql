CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS vector;

DO $$ BEGIN
    CREATE TYPE build_state AS ENUM ('not_started', 'pending', 'running', 'succeeded', 'failed', 'cancelled');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE node_type AS ENUM ('junction', 'endpoint', 'corridor', 'poi', 'poi_attach');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE edge_type AS ENUM ('rtabmap_link', 'poi_spur');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE build_step AS ENUM ('rtabmap_graph', 'persist', 'done');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE build_failure_reason AS ENUM ('rtabmap_data_not_ready', 'internal');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

CREATE TABLE IF NOT EXISTS building (
    building_id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    status TEXT NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS building_floor (
    floor_id UUID PRIMARY KEY,
    building_id UUID NOT NULL REFERENCES building(building_id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    level INTEGER NOT NULL,
    height DOUBLE PRECISION,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_building_floor_level UNIQUE(building_id, level)
);

CREATE TABLE IF NOT EXISTS scan_ingest (
    scan_id UUID PRIMARY KEY,
    payload_sha256 TEXT NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    replaced_at TIMESTAMPTZ,
    storage_path TEXT NOT NULL,
    device_info JSONB,
    build_state build_state NOT NULL DEFAULT 'not_started',
    build_job_id UUID
);

CREATE TABLE IF NOT EXISTS floor_scan (
    floor_scan_id UUID PRIMARY KEY,
    floor_id UUID NOT NULL REFERENCES building_floor(floor_id) ON DELETE CASCADE,
    scan_id UUID NOT NULL REFERENCES scan_ingest(scan_id) ON DELETE CASCADE,
    file_name TEXT,
    file_size BIGINT,
    status TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT false,
    upload_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_floor_scan_scan UNIQUE(floor_id, scan_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_floor_scan_one_active ON floor_scan(floor_id) WHERE active = true;

CREATE TABLE IF NOT EXISTS build_job (
    build_job_id UUID PRIMARY KEY,
    scan_id UUID NOT NULL REFERENCES scan_ingest(scan_id) ON DELETE CASCADE,
    state build_state NOT NULL DEFAULT 'pending',
    current_step build_step,
    progress DOUBLE PRECISION,
    enqueued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    locked_at TIMESTAMPTZ,
    worker_id TEXT,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    failure_reason build_failure_reason,
    failure_detail TEXT,
    counts JSONB
);

CREATE TABLE IF NOT EXISTS map_node (
    node_id UUID PRIMARY KEY,
    scan_id UUID NOT NULL REFERENCES scan_ingest(scan_id) ON DELETE CASCADE,
    build_job_id UUID NOT NULL REFERENCES build_job(build_job_id) ON DELETE CASCADE,
    node_type node_type NOT NULL,
    geom geometry(PointZ, 0) NOT NULL,
    label TEXT,
    source_ref JSONB,
    is_stale BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE IF NOT EXISTS map_edge (
    edge_id UUID PRIMARY KEY,
    scan_id UUID NOT NULL REFERENCES scan_ingest(scan_id) ON DELETE CASCADE,
    build_job_id UUID NOT NULL REFERENCES build_job(build_job_id) ON DELETE CASCADE,
    from_node_id UUID NOT NULL REFERENCES map_node(node_id) ON DELETE CASCADE,
    to_node_id UUID NOT NULL REFERENCES map_node(node_id) ON DELETE CASCADE,
    edge_type edge_type NOT NULL,
    geom geometry(LineStringZ, 0) NOT NULL,
    length_m DOUBLE PRECISION NOT NULL,
    is_stale BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE IF NOT EXISTS poi_canonical (
    canonical_id UUID PRIMARY KEY,
    label TEXT,
    scan_id UUID REFERENCES scan_ingest(scan_id) ON DELETE CASCADE,
    building_id UUID REFERENCES building(building_id) ON DELETE CASCADE,
    floor_id UUID REFERENCES building_floor(floor_id) ON DELETE SET NULL,
    level_id TEXT NOT NULL DEFAULT 'level-0',
    category TEXT NOT NULL DEFAULT 'unknown',
    name TEXT,
    world_pose geometry(PointZ, 0),
    route_node_id UUID REFERENCES map_node(node_id) ON DELETE SET NULL,
    display_point geometry(PointZ, 0),
    display_area_id UUID,
    source_mark_ids JSONB,
    needs_review BOOLEAN NOT NULL DEFAULT false,
    cluster_method TEXT,
    llm_confidence DOUBLE PRECISION,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS vertical_connector (
    connector_id UUID PRIMARY KEY,
    building_id UUID REFERENCES building(building_id) ON DELETE CASCADE,
    connector_type TEXT NOT NULL,
    connector_key TEXT NOT NULL,
    name TEXT,
    is_mock BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_vertical_connector UNIQUE(building_id, connector_type, connector_key)
);

CREATE TABLE IF NOT EXISTS vertical_connector_stop (
    connector_stop_id UUID PRIMARY KEY,
    connector_id UUID NOT NULL REFERENCES vertical_connector(connector_id) ON DELETE CASCADE,
    level_id TEXT NOT NULL,
    poi_canonical_id UUID REFERENCES poi_canonical(canonical_id) ON DELETE CASCADE,
    route_node_id UUID REFERENCES map_node(node_id) ON DELETE SET NULL,
    CONSTRAINT uq_vertical_connector_stop UNIQUE(connector_id, level_id)
);
