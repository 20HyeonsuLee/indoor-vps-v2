-- 새 건물 생성 시 기본 status 를 ACTIVE 로. DRAFT 기본은 단일 운영자 환경에서
-- "보이지 않음" 상태를 만들기만 해 불편 — 매번 수동 ACTIVE 전환 필요.
ALTER TABLE building ALTER COLUMN status SET DEFAULT 'ACTIVE';
