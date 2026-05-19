-- BuildJob의 마지막 step에서 rtabmap.db → cloud.ply export 결과 파일을 추적하기 위한 컬럼.
-- nullable 유지: 기존 스캔과 export 실패 케이스 모두 수용. 컨트롤러는 컬럼이 비어도 디렉터리 내
-- 표준 파일명(cloud.ply)을 우선 탐색하므로 백필 없이 무중단 배포 가능.
ALTER TABLE scan_ingest
    ADD COLUMN ply_relative_path TEXT;
