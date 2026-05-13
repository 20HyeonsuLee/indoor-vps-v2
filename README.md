# indoor-vps-v2

Java + Spring Boot migration target for the indoor VPS/pathfinding backend.

The legacy Python backend is kept as a backup artifact under `backups/`.
Java owns the HTTP API and persistence surface; Python is reserved for ML,
SLAM, RTAB-Map, and geometry-heavy jobs that do not have a practical JVM
replacement.

## Local target

- Java: 21
- Framework: Spring Boot
- Database: PostgreSQL + PostGIS
- API docs: OpenAPI/Swagger

## Harness

Execution notes are tracked in `docs/tasks/0001-spring-migration/execution_report.md`.
