# indoor-vps-v2

Java + Spring Boot migration target for the indoor VPS/pathfinding backend.

The legacy Python backend is kept as a backup artifact under `backups/`.
Java owns the HTTP API and persistence surface; Python is reserved for ML,
SLAM, RTAB-Map, and geometry-heavy jobs that do not have a practical JVM
replacement.

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

## Architecture

The Java migration uses a DDD-style package boundary:

- `api`: Spring MVC controllers, request/response DTOs, OpenAPI tags
- `application`: use-case services grouped by domain workflow
- `domain`: domain enums, value objects, and pure domain services
- `infrastructure`: JPA entities/repositories, RTAB-Map readers, filesystem storage, localization adapters

## Harness

Execution notes are tracked in `docs/tasks/0001-spring-migration/execution_report.md`.
