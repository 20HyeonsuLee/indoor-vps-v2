# ADR-010: RTAB-Map SQLite 파일 읽기에 JdbcClient 직접 사용 허용

- Status: accepted
- Date: 2026-05-17
- Task: 0002-claudemd-conformance / Cycle 8

## Context

RTAB-Map이 출력하는 `rtabmap.db` (SQLite)는 앱 DataSource(PostgreSQL)와 독립된 외부 툴 산출물이다.
ADR-007은 "앱 데이터 스토어에 JPA만 사용"을 결정했으나, 외부 툴이 생성한 SQLite 파일을 JPA로
읽으려면 별도 EntityManagerFactory 설정이 필요하고, 스키마를 Flyway로 관리할 수 없다는 문제가 있다.

`RtabmapGraphReaderAdapter`는 이미 ProcessBuilder로 Python 파이프라인을 호출하거나
SQLite 파일을 직접 파싱하는 인프라 어댑터로서, 읽기 전용 조회만 수행한다.

## Decision

RTAB-Map SQLite 파일을 읽는 인프라 어댑터(`infrastructure/rtabmap/`)에 한해
`JdbcClient` (또는 `JdbcTemplate`) 직접 사용을 허용한다.

허용 조건:

| 조건 | 내용 |
|---|---|
| 위치 | `infrastructure/rtabmap/` 어댑터 내부만 |
| DataSource | 앱 DataSource(PostgreSQL) 아님. SQLite 파일용 별도 DataSource 또는 JDBC URL 직접 |
| 방향 | 읽기 전용. 쓰기 금지 |
| Port 분리 | domain port 인터페이스 통해 호출. 어댑터 내부 구현만 JDBC |
| 스키마 관리 | Flyway 대상 아님. 외부 툴 산출물이므로 앱이 DDL 소유하지 않음 |

## Alternatives Considered

- **JPA + 별도 EntityManagerFactory**: 설정 비용 과다, Flyway 관리 불가, 외부 스키마 변경에 취약.
- **Python 파이프라인으로 위임**: 이미 ProcessBuilder 통합이 있으나, 그래프 파싱 로직을 Python에 두면 Java 도메인 모델로 변환하는 어댑터 책임이 모호해짐.

## Consequences

- ADR-007 "JPA만 사용"의 범위는 **앱 DataSource(PostgreSQL) 한정**으로 명시.
- `infrastructure/rtabmap/` 외 위치에서 SQLite JDBC 사용은 여전히 금지.
- Port/Adapter 분리는 유지. domain이 JDBC에 직접 의존하는 것은 금지.
