# Test Strategy

## Decision

Acceptance E2E is the primary confidence layer for `indoor-vps-v2`.

- Acceptance scenarios are written in Korean Gherkin under `src/test/resources/features`.
- Cucumber step definitions and support utilities live under `src/test/java`.
- Feature files use product language. RTAB-Map, ZIP, node, and edge details stay in steps/support unless the scenario itself is about that contract.
- Step definitions are split by domain: building setup, scan upload, map build, map assertions, pathfinding, and error handling.
- Test utility code is under `acceptance/support`; feature and step files should not rebuild HTTP, multipart, fixture, or cleanup logic.
- Acceptance tests run a real Spring server, real HTTP, Testcontainers PostgreSQL, Flyway, JPA, and temp filesystem storage.
- Real device scan ZIPs and localization images can be captured with `FIXTURE_CAPTURE_ENABLED=true`; see `docs/testing/real-device-fixtures.md`.
- Unit tests are kept for complex pure logic only.
- CRUD services, DTOs, and controller method lists are not unit-tested.
- Tests must create their own data and avoid shared state.

## Layers

| Layer | Purpose | Examples |
|---|---|---|
| Acceptance E2E | Product-readable workflow proof | building -> floor -> scan upload -> process -> map/path/route |
| Integration | Adapter/format proof | RTAB-Map SQLite reader, archive storage, Python bridge contract |
| Unit | Pure logic proof | weighted routing, nearest-node, geometry helpers |

## E2E Runtime

- Spring Boot: `RANDOM_PORT`
- HTTP client: real HTTP, not `MockMvc`
- DB: Testcontainers PostgreSQL with PostGIS and pgvector
- Schema: Flyway enabled, `baseline-on-migrate=true`, `baseline-version=0`, Hibernate `validate`
- Storage: temp directory per test JVM
- Build worker scheduler: disabled; tests call `BuildJobRunner.runJob(buildJobId)` directly
- Python bridge: disabled by default in PR-gate E2E

## Current DB Image

Default E2E image:

```text
garapadev/postgres-postgis-pgvector:15-stable
```

This is a pinned fallback because the production DB image tag is not recorded in
this repo yet. Replace it with the production tag through:

```bash
./gradlew e2eTest -Pindoor.e2e.db.image=<prod-db-image-tag>
```

## PR Gate

```bash
./gradlew test
./gradlew e2eTest
./gradlew bootJar
```

`./gradlew test` excludes the Cucumber engine; `./gradlew e2eTest` runs Cucumber scenarios tagged `@acceptance`.

## E2E Scenarios

| Feature | Scenario |
|---|---|
| `scan_to_map_build.feature` | 스캔 파일 업로드 -> 지도 생성 -> 층 지도와 경로 조회 |
| `scan_replacement.feature` | 같은 스캔 파일은 409로 거절, `force=true`에서 교체 |
| `empty_floor_build.feature` | 활성 스캔 없는 층은 지도 생성을 시작하지 않음 |
| `floor_map_cache.feature` | ETag 재조회는 304와 빈 본문 반환 |
| `building_pathfinding.feature` | 목적지 검색 실패를 `destinationFound=false` 결과로 반환 |
| `request_error.feature` | 잘못된 식별자는 표준 오류 응답으로 반환 |

## Main Scenario Flow

1. Create building.
2. Create floor.
3. Generate an RTAB-Map SQLite DB fixture with `Node` and `Link` rows.
4. Upload ZIP containing `rtabmap.db` and `scan_metadata.db`.
5. Enqueue processing.
6. Run the claimed build job directly.
7. Assert process status, floor path, floor map, and route response.

## Cucumber Rules

- One scenario describes one externally visible workflow or failure mode.
- Background is avoided until at least two feature files repeat the same story setup inside one feature.
- Step text avoids implementation terms when a planner can describe the behavior without them.
- Step definitions may call lower-level helper methods, but scenario text should not encode HTTP shape.
- Scenario state is scenario-scoped. Static mutable state is limited to expensive infrastructure such as the Testcontainers database.

## Not In PR Gate

- Real SuperPoint/LightGlue model localization.
- CUDA profile smoke.
- Multi-scan merge requiring `rtabmap-reprocess`.

Those belong in a server/nightly gate because they depend on model files, CUDA,
and native RTAB-Map binaries.
