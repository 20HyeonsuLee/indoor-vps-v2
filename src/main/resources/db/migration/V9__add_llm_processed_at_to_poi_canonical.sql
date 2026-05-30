-- POI Labeler 가 마지막으로 처리한 시각.
-- NULL 이면 미처리 — 라벨러가 우선 대상으로 삼는다.
-- cluster_method 컬럼은 처리 버전 마커로 별도 유지 (예: 'llm_v1' → SKILL 업그레이드 시 'llm_v2').
ALTER TABLE poi_canonical
    ADD COLUMN llm_processed_at TIMESTAMP WITH TIME ZONE;

COMMENT ON COLUMN poi_canonical.llm_processed_at IS
    'POI Labeler 가 마지막으로 AI 분류를 시도한 시각. NULL 이면 미처리.';
