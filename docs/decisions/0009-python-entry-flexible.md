# ADR-009: Python entry는 pyproject.toml + uv 또는 main.py 둘 다 허용

- Status: accepted
- Date: 2026-05-17
- Task: 0002-claudemd-conformance / Cycle 3

## Context

기존 CLAUDE.md는 `python/<pipeline>/main.py`를 유일한 Python 진입점으로 강제했다.
그러나 `python/legacy_backend`는 이미 `pyproject.toml` + `uv.lock` 기반으로 구성돼 있다.

- `uv` / `pyproject.toml` 은 2024년 이후 Python 생태계 표준으로 자리잡음.
- `uv run --project python/<pipeline> python -m <module>` 또는 `uv run <script>` 형식이 `python main.py`보다 의존성 격리가 명확.
- ProcessBuilder는 호출 명령어 문자열만 받으므로 진입점 형식에 무관하게 동작.

## Decision

`python/<pipeline>/` 내 진입점 형식을 두 가지 모두 허용한다.

| 형식 | 호출 예시 |
|---|---|
| `main.py` (레거시 호환) | `python python/<pipeline>/main.py` |
| `pyproject.toml` + entry-point | `uv run --project python/<pipeline> python -m <module>` |

어느 형식이든 ProcessBuilder adapter에서 호출 명령어를 결정한다.
두 형식을 한 파이프라인에서 혼용하는 것은 금지한다.

## Alternatives Considered

- **main.py 강제 유지**: `legacy_backend` pyproject.toml을 폐기하고 flat main.py로 재작성해야 함. 기존 uv.lock 자산 폐기 비용 과다.
- **uv 전용 강제**: 기존 main.py 파이프라인을 마이그레이션해야 함. 전환 비용 대비 이득 없음.

## Consequences

- `_project_meta.validation.install`이 `uv sync --project python/<pipeline>`으로 대체됨.
- `_project_struct.python.<pipeline-name>.forbidden`에서 "main.py 외 진입점 금지" 규칙 제거.
- Python adapter(infrastructure layer)가 호출 명령어를 config 또는 생성자 주입으로 결정해야 함 — 하드코딩 금지.
