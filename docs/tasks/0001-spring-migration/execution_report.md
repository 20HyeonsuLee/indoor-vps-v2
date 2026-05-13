# 0001 Spring Migration Execution Report

```yaml
execution_mode: ISOLATED_BRANCH
degraded_reason: target repo was empty, so git was initialized in-place and the target root is used directly
task_id: "0001"
cycle_number: 1
commit_policy: cycle
main_worktree_path: /Users/leehyeonsu/home/koreatech/graduate_project/indoor-vps-v2
worktree_path: /Users/leehyeonsu/home/koreatech/graduate_project/indoor-vps-v2
branch_name: task/0001-spring-migration
```

## Request

Migrate the Python FastAPI backend to Java + Spring Boot under
`/Users/leehyeonsu/home/koreatech/graduate_project/indoor-vps-v2`.
Keep Python only where required for library compatibility, invoked as scripts
or worker subprocesses. Back up the existing Python code first.

## Status

- [x] Initialize target git repository
- [x] Back up existing Python backend
- [x] Check JVM/Python library compatibility
- [x] Scaffold Spring Boot backend
- [x] Port API contracts
- [x] Add Python bridge for unavoidable Python workloads
- [x] Verify build and OpenAPI surface
- [x] Replace contract scaffold with Spring Data JPA persistence
- [x] Split JPA facade into SRP application services
- [x] Add typed Python bridge command contracts
- [x] Replace hand-rolled route search with JGraphT
- [x] Preserve typed Python bridge errors in Java envelope
- [x] Add focused navigation graph unit coverage
- [x] Replace raw scan-file storage with ZIP archive validation/extraction
- [x] Keep localization DB lookup in Java/JPA and pass floor-map files to Python
- [x] Preserve existing scan archive data when forced replacement upload is invalid
- [x] Resolve omitted scan IDs from scan ZIP root UUID
- [x] Wire merge-scan command to legacy RTAB-Map Python boundary
- [x] Add Java-owned build worker lifecycle with JPA persistence
- [x] Read RTAB-Map `Node`/`Link` SQLite artifacts through library adapter, then persist graph through JPA

## Cycle 1 Result

```yaml
cycles_run: 1
verdict: PASS
agent_trace:
  - phase: plan
    agent: plan-architect
    summary: "Use Java for HTTP/API/DB/job lifecycle and keep Python behind subprocess bridge for SLAM/ML/build workloads."
  - phase: audit
    agent: explorer
    summary: "Confirmed cleaned API surface and Python-only boundaries: SLAM localize, RTAB-Map reprocess, build/map worker."
  - phase: build
    agent: main-session
    summary: "Spring Boot scaffold, API controllers, DTOs, OpenAPI aliases, Flyway baseline, and Python bridge stub."
verification:
  - "./mvnw test -q"
  - "SERVER_PORT=18081 FLYWAY_ENABLED=false ./mvnw spring-boot:run -q"
  - "curl -fsS http://127.0.0.1:18081/openapi.json"
  - "curl -fsSI http://127.0.0.1:18081/v3/api-docs"
  - "curl -fsSI http://127.0.0.1:18081/swagger-ui/index.html"
warnings:
  - "Cycle 1 API services are in-memory contract scaffolds; database-backed repositories are next."
  - "Python bridge only has a health stub; localize/build command parity is next."
post_eval_fixes:
  - "Wrapped Spring MVC binding/type mismatch errors in the client validation envelope."
  - "Added invalid UUID contract test for `/api/v1/buildings/not-a-uuid`."
```

## Artifacts

- `backups/indoor-pathfinding-backend-python-20260513-215226.tar.gz`
- `docs/tasks/0001-spring-migration/python-openapi-current.json`
- `docs/tasks/0001-spring-migration/migration_plan.md`

## Cycle 2 Result

```yaml
cycles_run: 2
verdict: PASS
agent_trace:
  - phase: plan
    agent: plan-architect
    summary: "Replace in-memory service with PostgreSQL/PostGIS-backed behavior and keep Python for SLAM/RTAB-Map/ML compute."
  - phase: build
    agent: main-session
    summary: "Switched controllers to VpsService, added Spring Data JPA entities/repositories, Hibernate Spatial mapping, JPA-backed API service, and explicit persistence mode selection."
  - phase: eval
    agent: eval-implementation
    summary: "PASS_WITH_WARN before baseline fix; warning was missing poi_canonical display_point/display metadata in Flyway baseline."
verification:
  - "./mvnw test -q"
  - "./mvnw -q -DskipTests package"
  - "SERVER_PORT=18084 FLYWAY_ENABLED=false ./mvnw spring-boot:run -q"
  - "live smoke: create building/floor, upload scan, enqueue process, read process status, read floor map, list POIs, delete temp data"
  - "database smoke: scan_ingest count=1 and build_job count=1 for uploaded scan before cleanup"
  - "fresh Flyway smoke: temp PostgreSQL database, FLYWAY_ENABLED=true, Spring Data JPA startup, upload/process/map API smoke, then DROP DATABASE"
notes:
  - "Direct JdbcTemplate service attempt was removed after user feedback; persistence now uses Spring Data JPA and Hibernate Spatial/JTS."
  - "Test runtime sets indoor.persistence=memory; runtime default is indoor.persistence=jpa."
  - "Eval warning fixed by aligning poi_canonical baseline with Python schema display_point/display_area_id/source_mark_ids/cluster_method/created_at columns."
```

## Cycle 3 Result

```yaml
cycles_run: 3
verdict: PASS
agent_trace:
  - phase: plan
    agent: plan-architect
    summary: "Keep JpaVpsService as a thin facade, split JPA responsibilities by domain, and formalize Python bridge command contracts."
  - phase: build
    agent: main-session
    summary: "Reduced JpaVpsService from 941 lines to a delegating facade, added building/scan/navigation/POI JPA services, typed bridge request/response records, bridge command enum, Python dispatch validation, and bridge contract tests."
  - phase: eval
    agent: eval-implementation
    summary: "PASS. API surface unchanged, direct JDBC absent, facade delegates only, bridge contracts validated."
verification:
  - "./mvnw test -q"
  - "./mvnw -q -DskipTests package"
  - "SERVER_PORT=18086 FLYWAY_ENABLED=false ./mvnw spring-boot:run -q"
  - "live smoke: create building/floor, upload scan, merge active chunk, enqueue process, read process status, read floor map, search POIs, delete temp data"
notes:
  - "JpaVpsService is now 161 lines; domain service sizes: Building 197, Scan 308, Navigation 451, POI 83."
  - "Python bridge commands localize/merge_scan/build_floor_map validate typed payloads and return explicit BRIDGE_COMMAND_NOT_IMPLEMENTED until Python-only compute is wired."
  - "Next candidate: split NavigationJpaService route algorithm/helper and preserve typed bridge error codes through the Java envelope."
```

## Cycle 4 Result

```yaml
cycles_run: 4
verdict: PASS
agent_trace:
  - phase: build
    agent: main-session
    summary: "Added JGraphT and moved route graph search into NavigationGraphService; PythonBridgeClient now preserves typed BRIDGE_* stderr error codes in ClientApiException envelopes."
  - phase: eval
    agent: eval-implementation
    summary: "PASS. JGraphT usage verified, hand-rolled Dijkstra removed, bridge typed error preservation verified."
verification:
  - "./mvnw test -q"
  - "./mvnw -q -DskipTests package"
  - "SERVER_PORT=18087 FLYWAY_ENABLED=false ./mvnw spring-boot:run -q"
  - "live smoke: create building/floor, upload scan, merge active chunk, enqueue process, read process status, read floor map, search POIs, delete temp data"
notes:
  - "NavigationJpaService reduced from 451 lines to 357 lines; NavigationGraphService owns graph construction and JGraphT Dijkstra routing."
  - "PythonBridgeContractTest now verifies BRIDGE_COMMAND_NOT_IMPLEMENTED is preserved by Java instead of being collapsed into PYTHON_BRIDGE_FAILED."
  - "Next candidate: add focused NavigationGraphService unit tests for shortest path, reverse traversal, unreachable route, missing node, duplicate edge."
```

## Cycle 5 Result

```yaml
cycles_run: 5
verdict: PASS
agent_trace:
  - phase: build
    agent: main-session
    summary: "Added focused NavigationGraphService unit tests and entity factory methods for graph fixtures."
  - phase: eval
    agent: eval-implementation
    summary: "PASS. Tests cover shortest path, reverse traversal, unreachable route, missing node, duplicate edge, and 3D nearest-node behavior."
verification:
  - "./mvnw test -q"
  - "./mvnw -q -DskipTests package"
  - "./mvnw -q -Dtest=NavigationGraphServiceTest test"
notes:
  - "MapNodeEntity.create and MapEdgeEntity.create set required entity fields without opening broad setters."
```

## Cycle 6 Result

```yaml
cycles_run: 6
verdict: PASS
agent_trace:
  - phase: build
    agent: main-session
    summary: "Added Apache Commons Compress ZIP validation/extraction, JPA active floor-map handoff for localization, and a real localize bridge adapter that imports the legacy Python ML engine only when configured."
verification:
  - "./mvnw test -q"
  - "./mvnw -q -DskipTests package"
  - "python3 -m py_compile scripts/python_bridge/bridge_entry.py"
  - "git diff --check"
  - "SERVER_PORT=18088 FLYWAY_ENABLED=false PYTHON_BRIDGE_ENABLED=false ./mvnw spring-boot:run -q"
  - "live smoke: create building/floor, upload valid scan ZIP, verify var/storage/scans/{scanId}/rtabmap.db, read merge status, delete temp data"
notes:
  - "commons-compress 1.28.0 is pinned because Spring Boot 4.0.6 does not manage this dependency."
  - "Scan upload now requires a valid ZIP with root {scanId}/ plus rtabmap.db and scan_metadata.db."
  - "Java/JPA resolves active floor maps; Python receives image paths and floor-map DB paths, avoiding a second SQLAlchemy DB connection for localization."
  - "Next candidate: wire merge_scan/build_floor_map bridge commands to RTAB-Map reprocess and the legacy build worker boundary."
post_eval_fixes:
  - "force=true archive replacement now validates and extracts into a temp directory before replacing the existing scan root."
  - "scan_id can be omitted; the JPA upload path resolves it from the ZIP root UUID."
  - "PYTHON_BACKEND_SRC now defaults to blank and is only forwarded to the bridge when explicitly configured."
```

## Cycle 7 Result

```yaml
cycles_run: 7
verdict: PASS
agent_trace:
  - phase: build
    agent: main-session
    summary: "Fixed scan archive replacement semantics, restored optional scan_id behavior by deriving it from the ZIP root UUID, and wired merge_scan to the legacy RTAB-Map subprocess boundary."
  - phase: eval
    agent: eval-implementation
    summary: "PASS. No blocking findings; remaining warning is lack of a real RTAB-Map binary success-path smoke on this machine."
verification:
  - "python3 -m py_compile scripts/python_bridge/bridge_entry.py"
  - "./mvnw -q -Dtest=ScanArchiveStorageServiceTest,PythonBridgeContractTest test"
  - "./mvnw test -q"
  - "./mvnw -q -DskipTests package"
  - "git diff --check"
  - "SERVER_PORT=18089 FLYWAY_ENABLED=false PYTHON_BRIDGE_ENABLED=false ./mvnw spring-boot:run -q"
  - "live smoke: upload scan ZIP without scan_id, verify response scanId is derived from ZIP root UUID"
notes:
  - "Invalid force replacement now leaves the previously extracted scan directory untouched."
  - "Multi-scan merge still delegates only the RTAB-Map-compatible compute boundary to Python; Java/JPA owns API state."
```

## Cycle 8 Result

```yaml
cycles_run: 8
verdict: PASS
agent_trace:
  - phase: build
    agent: main-session
    summary: "Added a scheduled Java build worker that claims pending build jobs, reads RTAB-Map Node/Link graph artifacts, and persists map_nodes/map_edges/build status through Spring Data JPA."
verification:
  - "./mvnw -q -Dtest=RtabmapGraphReaderTest test"
  - "./mvnw test -q"
  - "./mvnw -q -DskipTests package"
  - "git diff --check"
  - "SERVER_PORT=18093 DATABASE_URL=jdbc:postgresql://127.0.0.1:55432/indoor_v2_smoke_1778686988 FLYWAY_ENABLED=true PYTHON_BRIDGE_ENABLED=false BUILD_WORKER_POLL_INTERVAL_MS=500 ./mvnw spring-boot:run -q"
  - "live smoke on fresh Flyway DB: upload scan ZIP with SQLite rtabmap.db Node/Link tables, enqueue /process, poll SUCCEEDED, verify /path returns nodes=2 edges=1"
notes:
  - "Application persistence is Spring Data JPA/Hibernate Spatial; no application database JdbcTemplate/JdbcClient repository path is used."
  - "RTAB-Map graph ingestion uses xerial SQLiteDataSource plus Spring JdbcClient only as a file-format adapter for rtabmap.db; resulting graph rows are persisted via JPA repositories."
  - ".gitignore was tightened from build/ to /build/ so source package src/.../application/build is tracked."
  - "A shared-DB smoke was discarded because the existing Docker worker consumed the pending build job first; the accepted evidence uses an isolated temporary database."
```
