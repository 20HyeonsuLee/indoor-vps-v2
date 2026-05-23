## rule
- 마이그레이션 위치: `src/main/resources/db/migration/`.
- 파일명: `V<seq>__<snake_case_description>.sql` (4자리 zero-pad는 정렬 안전. 예: `V0042__add_polygon_to_floor.sql`).
- 카테고리별 명명:
  - DDL: `V<seq>__create_<table>.sql`, `V<seq>__add_<col>_to_<table>.sql`, `V<seq>__drop_<obj>.sql`
  - 백필: `V<seq>__backfill_<table>_<col>.sql`
  - 제약 강제: `V<seq>__enforce_not_null_<table>_<col>.sql`
- **1 PR = 1 migration 최대** (CLAUDE.md forbidden). DDL/백필/제약 강제는 각기 다른 PR.
- forward-only **Expand-Contract** 두 단계 배포 패턴:
  1. Expand: 새 컬럼/테이블 추가 (NULLable). 기존 코드 동작 유지
  2. 신규 코드 배포: 새 컬럼/테이블 사용
  3. Contract: 미사용 컬럼/테이블 제거. NOT NULL 강제
- **이미 배포된 migration 수정 금지** (CLAUDE.md forbidden). 새 migration으로 보정.
- baseline 정책: 기존 DB에 도입 시 `spring.flyway.baseline-on-migrate: true` + `baseline-version: 0`. 새 prod에선 X.
- repair는 **위험**. unused → applied 강제 변경 외엔 사용 X.
- destructive DDL (drop column/table, NOT NULL 강제)은 **데이터 백업 또는 readable copy 확인 후**. 별도 PR.
- migration SQL은 transactional. 단일 SQL 안 BEGIN/COMMIT 명시 X (Flyway가 wrapping).
- 백필 SQL이 오래 걸리면(>30초) chunked update + 별 PR.
- index 추가는 `CREATE INDEX CONCURRENTLY` (Postgres) 권장. 단 transactional 외에서 실행 — Flyway에 별도 처리 필요.
- 모든 migration은 idempotent 검토. `IF NOT EXISTS` 적극 사용.

## forbidden
- 이미 배포된 migration 수정 (checksum 깨짐 + 환경 간 drift)
- 1 PR에 migration 2개 이상
- DDL + 백필 같은 파일에 섞기
- DDL + 신규 코드 같은 PR (Expand 단계 누락)
- destructive DDL (drop, NOT NULL 강제)을 backfill PR과 합치기
- `repair` 일상 사용
- transactional 외 명령(`CREATE INDEX CONCURRENTLY`)을 일반 migration에 섞기 (분리 필요)
- migration SQL에 비밀값 hardcode
- 환경별 다른 migration 적용 (모든 env 동일 sequence)
- baseline-version을 prod에서 임의 변경
- migration 안 비즈니스 데이터 생성 (seed는 별도 절차 또는 `R__seed.sql` repeatable)
- 같은 sequence 번호 중복 (충돌 시 PR 통합 시 마지막 사람이 bump)
