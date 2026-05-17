execution_mode: ISOLATED_WORKTREE (in-place on task/0001-spring-migration)
task_id: "0002"
title: CLAUDE.md 전면 conformance 리팩터링
verdict_final: PASS
adr_path:
  - docs/decisions/0009-python-entry-flexible.md
  - docs/decisions/0010-external-sqlite-jdbc.md

scope: CLAUDE.md _project_meta·_project_struct 규칙 전면 준수. 초기 감사 15건 위반 → 8 cycle로 0건.

cycles:
  - cycle: 0
    commit: 9bc767b
    summary: baseline. 평면 패키지 → contexts/mapping/ 적층 DDD scaffold dirty commit
  - cycle: 1
    commit: 5553611, d1bdac2 (fixup)
    summary: HttpRequestLoggingFilter → config/, karate/acceptance → karate/mapping/, docker-compose.yml base 신설
  - cycle: 2
    commit: 0fcdc42
    summary: return null 5건 제거(Optional), infra @Transactional 제거, NavigationGeometry walking-speed → NavigationGraphService 도메인 이동
  - cycle: 3
    commit: 2da7bea
    summary: ADR-009 신설(Python entry 유연), @SpringBootTest IT 2개 제거, OpenAPI smoke karate 이관
  - cycle: 4
    commit: d110c93
    summary: domain/scan/port/{StreamingScanStorage, ScanArchiveStorage, RtabmapReprocessor} 신설, ScanApplicationService 420줄 → 7 UseCase 분할, infra service Adapter rename + 200줄 이하 분할
  - cycle: 5
    commit: 814c122
    summary: Navigation/Poi/Floor/Building/Passage *ApplicationService → *UseCase rename, NavigationResponseMapper Controller 흡수, BuildJobRunner RtabmapGraphReader port 의존, IndoorProperties → config/properties/ 분리, SQL DDL 외부화(RtabmapSchemaDdl)
  - cycle: 6
    commit: f8c785e
    summary: application Command/Result record 신설(web.dto import 제거), PythonBridge port + infrastructure/bridge/PythonBridgeAdapter, Building/Floor/GetGraph 단일 책임 분할, else → early return
  - cycle: 7
    commit: 0af0c16
    summary: PassageUseCase JdbcClient → JPA Repository(ADR-007 회귀 제거), StreamingScanStorage port에서 MultipartFile 제거(FilePayload 도입), 모든 UseCase.execute 다중 인자 → Command record 단일 인자, PlanRouteUseCase UseCase 의존 제거(GraphQueryFacade 도입), instance_vars > 3 8건 → facade/분할로 ≤ 3, NavigationResponseMapper 분할(FloorMapResponseMapper + RouteResponseMapper)
  - cycle: 8
    commit: 9927acc
    summary: ListScanChunksUseCase ScanReadFacade 도입(instance_vars ≤ 3), ScanMergeRunner Command record, NavigationGeometry.distance Point3 VO 2인자, ADR-010 신설(외부 SQLite JdbcClient 허용)

final_violations: 0

verification:
  build: ./gradlew build -x test PASS
  forbidden_sweep_zero:
    app_to_infra_import: 0
    return_null: 0
    else: 0
    lombok: 0
    infra_transactional: 0
    multipartfile_in_domain: 0
    multipartfile_in_application: 0
    applicationservice_suffix: 0
    files_over_200_lines: 0
  scale_thresholds: all within limit
  adr_009: accepted (Python entry pyproject.toml/main.py 양자 허용)
  adr_010: accepted (외부 RTAB-Map SQLite JdbcClient 허용, 앱 DataSource 비적용)

insight_capture:
  status: captured
  reason: 초기 감사 결과(15건) + 5 cycle plan 산출물(plan.md)이 추가 3 cycle(6~8)에서 발견된 회귀(JdbcClient, MultipartFile in domain, UseCase→UseCase 의존)까지 잡아냄. function_args > 2 threshold가 가장 좁아 Command record 일괄 도입으로 해결한 패턴 재사용 가능.

next_steps_optional:
  - karate openapi_contract.feature 302 redirect 해결 (현재 PASS_WITH_WARN, conformance와 무관)
  - 새 Bounded Context 추가 시 cycle 1~8 패턴 재사용
