-- 한 area에 active scan이 둘 이상 공존하도록 unique 제약 해제.
-- 건물 좌표 drift가 없는 전제에서 새 청크 업로드/머지 결과가 기존 active 스캔을
-- 비활성화하지 않고 함께 그래프에 합쳐지도록.
DROP INDEX IF EXISTS uq_floor_scan_one_active;
