# indoor-vps-v2

Java + Spring Boot migration target for the indoor VPS/pathfinding backend.

The legacy Python backend is kept as a backup artifact under `backups/`.
Java owns the HTTP API and persistence surface; Python is reserved for ML,
SLAM, RTAB-Map, and geometry-heavy jobs that do not have a practical JVM
replacement.
Route graph rows are built from RTAB-Map `Node`/`Link` SQLite artifacts, not
from the legacy walkable-grid skeleton pipeline.

Bridge-owned Python runtime code lives under `python/legacy_backend/`.
The default Spring config points `indoor.python.backend-source` at
`./python/legacy_backend/src`, so `PYTHON_BACKEND_SRC` is only needed for
external override testing.

Local runs use `indoor.python.device=cpu` and hide CUDA from the Python bridge.
The `prod` Spring profile sets `indoor.python.device=cuda` unless
`PYTHON_ML_DEVICE` overrides it.

## Local target

- Java: 21
- Framework: Spring Boot
- Build: Gradle Kotlin DSL (`build.gradle.kts`)
- Database: PostgreSQL + PostGIS
- API docs: OpenAPI/Swagger

## Local run

```bash
./gradlew test
./gradlew bootRun
```

```bash
SPRING_PROFILES_ACTIVE=prod PYTHON_BRIDGE_ENABLED=true ./gradlew bootRun
```

Real phone scan/localize fixture capture:

```bash
FIXTURE_CAPTURE_ENABLED=true FIXTURE_CAPTURE_ROOT=./test-fixtures/captured ./gradlew bootRun
```

Captured raw data is written under `test-fixtures/captured/`; see
`docs/testing/real-device-fixtures.md`.

## Docker run

```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build
curl http://127.0.0.1:8080/openapi.json
```

Phone fixture capture with Docker:

```bash
FIXTURE_CAPTURE_ENABLED=true docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build
```

## Architecture

The Java migration uses the project prompt package boundary:

- `app`: Spring Boot entrypoint and global exception advice
- `config`: global Spring configuration and typed properties
- `shared`: shared exception primitives with no context dependency
- `contexts/mapping/application`: use-case services grouped by workflow
- `contexts/mapping/domain`: JPA entities/repositories, domain enums, value objects, and pure domain services
- `contexts/mapping/infrastructure`: RTAB-Map readers, filesystem storage, localization adapters, request logging
- `contexts/mapping/infrastructure/web`: Spring MVC controllers and request/response DTOs
- `python/legacy_backend`: vendored Python runtime used only by the bridge

Environment-specific runtime files are split into:

- `application-local.yml`, `docker/Dockerfile.local`, `docker-compose.local.yml`
- `application-prod.yml`, `docker/Dockerfile.prod`, `docker-compose.prod.yml`

## Harness

Execution notes are tracked in `docs/tasks/0001-spring-migration/execution_report.md`.
