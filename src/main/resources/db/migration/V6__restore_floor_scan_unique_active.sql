-- V4 에서 drop 했던 active 단일성 제약 복원. area 당 한 번에 하나의 active 스캔만
-- 허용 — 새 업로드/머지 시 코드(ScanPersistence.deactivateForArea)가 기존 active를
-- 풀어주고 DB-level uniqueness 가 race 시 정합성 가드.
CREATE UNIQUE INDEX IF NOT EXISTS uq_floor_scan_one_active
    ON floor_scan(area_id) WHERE active = true;
