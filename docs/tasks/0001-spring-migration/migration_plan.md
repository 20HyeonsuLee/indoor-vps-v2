# Spring Boot Migration Plan

## Decision

Java owns HTTP API, validation, persistence, storage bookkeeping, and job
lifecycle. Python remains only behind a subprocess bridge for SLAM/ML,
RTAB-Map merge/reprocess, and heavy map-generation pipelines.

## API Surface

Source of truth for cycle 1 is `python-openapi-current.json`. The Java server
must expose the cleaned surface only:

- `/api/v1/buildings`
- `/api/v1/buildings/{buildingId}`
- `/api/v1/buildings/{buildingId}/status`
- `/api/v1/buildings/{buildingId}/floors`
- `/api/v1/floors/{floorId}`
- `/api/v1/floors/{floorId}/path`
- `/api/v1/floors/{floorId}/map`
- `/api/v1/floors/{floorId}/scans/chunks`
- `/api/v1/floors/{floorId}/scans/chunks/{chunkId}`
- `/api/v1/floors/{floorId}/scans/merge`
- `/api/v1/floors/{floorId}/scans/merge/status`
- `/api/v1/floors/{floorId}/process`
- `/api/v1/floors/{floorId}/process/status`
- `/api/v1/floors/{floorId}/route`
- `/api/v1/buildings/{buildingId}/pathfinding`
- `/api/v1/buildings/{buildingId}/pois`
- `/api/v1/buildings/{buildingId}/pois/search`
- `/api/slam/v3/localize`

Removed legacy APIs must not be restored.

## Compatibility Matrix

| Area | Cycle-1 choice | Reason |
|---|---|---|
| HTTP, validation, multipart | Spring WebMVC | Java API ownership |
| OpenAPI/Swagger | springdoc 3.x | Spring Boot 4 compatible docs surface |
| PostgreSQL/PostGIS | Flyway + JDBC/JPA-ready schema | Java owns DB lifecycle |
| Geometry read/write | JTS/PostGIS SQL | Shapely replacement for API read surfaces |
| Routing | Java-native later | NetworkX can be replaced |
| ZIP ingest/sidecar JSON | Java-native later | Jackson/Java IO sufficient |
| RTAB-Map CLI | subprocess | Binary dependency, not Python language dependency |
| SuperPoint/LightGlue | Python bridge | PyTorch/LightGlue JVM parity is not economical |
| ONNX/SegFormer/depth/video pipeline | Python bridge first | Pre/post-processing parity is high risk |

## Cycle 1 Acceptance

- `mvn test` passes without Python bridge enabled.
- Current cleaned path/method set matches `python-openapi-current.json`.
- Validation/client errors use `{code,message,detail}` envelope.
- `/openapi.json`, `/v3/api-docs`, `/docs`, `/swagger-ui/index.html` are reachable.
- Backup archive exists under `backups/`.

## Version Verification

- Spring Initializr metadata returned `4.0.6.RELEASE`, but Maven Central
  resolves `spring-boot-starter-parent` as `4.0.6`.
- The project uses `4.0.6` because it is the buildable parent artifact.
