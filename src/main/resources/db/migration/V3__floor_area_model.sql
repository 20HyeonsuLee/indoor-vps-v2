-- V3: floor_area 1급 엔티티 도입
-- 기존 데이터 없다고 가정 → backfill 없음, 모두 NOT NULL로 시작

-- 1. floor_area 테이블 신설
CREATE TABLE IF NOT EXISTS floor_area (
    area_id      UUID        PRIMARY KEY,
    floor_id     UUID        NOT NULL REFERENCES building_floor(floor_id) ON DELETE CASCADE,
    area_index   INTEGER     NOT NULL,
    label        TEXT        NOT NULL,
    is_default   BOOLEAN     NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_floor_area_index UNIQUE (floor_id, area_index)
);

-- default area는 floor당 1개만 허용 (partial unique index)
CREATE UNIQUE INDEX IF NOT EXISTS uq_floor_area_one_default
    ON floor_area(floor_id) WHERE is_default = true;

-- 2. floor_scan: floor_id → area_id로 교체
ALTER TABLE floor_scan
    ADD COLUMN area_id UUID REFERENCES floor_area(area_id) ON DELETE CASCADE;

-- active unique index를 area 단위로 재정의
DROP INDEX IF EXISTS uq_floor_scan_one_active;
CREATE UNIQUE INDEX IF NOT EXISTS uq_floor_scan_one_active
    ON floor_scan(area_id) WHERE active = true;

-- floor_id는 area.getFloor()로 derive 가능하므로 nullable로 유지 (기존 FK 보존)
-- NOT NULL 강제는 데이터가 채워진 다음 단계에서 추가 (현재 기존 데이터 없음)
ALTER TABLE floor_scan
    ALTER COLUMN area_id SET NOT NULL;

-- 3. map_node: area_id 추가
ALTER TABLE map_node
    ADD COLUMN area_id UUID REFERENCES floor_area(area_id) ON DELETE CASCADE;
ALTER TABLE map_node
    ALTER COLUMN area_id SET NOT NULL;

-- 4. map_edge: area_id 추가
ALTER TABLE map_edge
    ADD COLUMN area_id UUID REFERENCES floor_area(area_id) ON DELETE CASCADE;
ALTER TABLE map_edge
    ALTER COLUMN area_id SET NOT NULL;

-- 5. scan_ingest: area_id 추가
ALTER TABLE scan_ingest
    ADD COLUMN area_id UUID REFERENCES floor_area(area_id) ON DELETE CASCADE;
ALTER TABLE scan_ingest
    ALTER COLUMN area_id SET NOT NULL;

-- 6. build_job: area_id 추가
ALTER TABLE build_job
    ADD COLUMN area_id UUID REFERENCES floor_area(area_id) ON DELETE CASCADE;
ALTER TABLE build_job
    ALTER COLUMN area_id SET NOT NULL;

-- 7. vertical_connector_stop: level_id DROP + area_id ADD
ALTER TABLE vertical_connector_stop
    DROP COLUMN level_id;
ALTER TABLE vertical_connector_stop
    ADD COLUMN area_id UUID REFERENCES floor_area(area_id) ON DELETE CASCADE;
ALTER TABLE vertical_connector_stop
    ALTER COLUMN area_id SET NOT NULL;

-- unique 재정의: (connector_id, area_id)
ALTER TABLE vertical_connector_stop
    DROP CONSTRAINT IF EXISTS uq_vertical_connector_stop;
ALTER TABLE vertical_connector_stop
    ADD CONSTRAINT uq_vertical_connector_stop UNIQUE (connector_id, area_id);

-- 8. poi_canonical: level_id DROP + area_id ADD
ALTER TABLE poi_canonical
    DROP COLUMN level_id;
ALTER TABLE poi_canonical
    ADD COLUMN area_id UUID REFERENCES floor_area(area_id) ON DELETE SET NULL;

-- 9. floor_area_polygon: floor_area 참조 컬럼 추가 (area_id PK와 이름 충돌 → floor_area_id)
ALTER TABLE floor_area_polygon
    ADD COLUMN floor_area_id UUID REFERENCES floor_area(area_id) ON DELETE CASCADE;
