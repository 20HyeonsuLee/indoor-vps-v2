# ADR-011: scan_metadata.db를 빌드 그래프에 통합

## Status
Accepted

## Context
빌드 파이프라인은 rtabmap_reprocessed.db의 Node/Link 테이블만 읽어 map_node(전부 corridor)와 map_edge를 생성했다.
scan_metadata.db에 branch_mark(corridor/corner), poi_mark, interfloor_mark가 채워지지만 무시되어 poi_canonical, vertical_connector, junction 노드가 0건이었다.

## Decision
- `ScanMetadataReader` port (domain/scan/port)와 `ScanMetadataReaderAdapter` (infrastructure/storage) 신설.
  - scan_metadata.db가 없으면 `Optional.empty()` 반환 — 하위 호환 보장.
- ARKit(Y-up, -Z-forward) → RTABMap(Z-up, X-forward) 좌표 변환을 `ArKitToRtabmap` 순수 함수(application/scan)로 캡슐화.
  - `rt_x = -arkit_z`, `rt_y = -arkit_x`, `rt_z = arkit_y`
- `ScanMetadataIntegrator` (application/build)가 통합 로직 소유.
  - `branch_mark.node_type == 'corner'` → 가장 가까운 rtabmap node를 `NodeType.junction`으로 업데이트 (≤ 0.5m)
  - `poi_mark` → `map_node(poi)` + `poi_canonical` + `poi_spur edge` 생성
  - `interfloor_mark` → `map_node(poi)` + `vertical_connector` upsert + `vertical_connector_stop` upsert + `poi_spur edge`
- `BuildGraphPersister.persistSuccess` 시그니처에 `Optional<ScanMetadata>` 추가.
- `BuildJobRunner`에서 `rtabmap.db` 형제 경로 `scan_metadata.db`를 읽어 전달.
- `scan_ingest.device_info`에 `device_model`, `scan_started_at`을 merge — scan_session 정보 추적.

## Consequences
- 기존 scan(scan_metadata.db 없음) 재빌드 시 동작 동일 — `Optional.empty()` 분기.
- 새 scan 빌드 시 corner 노드는 `junction`으로 타입 갱신, poi_canonical/vertical_connector_stop 자동 생성.
- `ScanMetadataIntegrator`는 application layer — domain/infrastructure 의존 없음, `@Transactional` 경계는 `BuildGraphPersister`가 소유.
- 좌표 변환 검증: `ArKitToRtabmap` 단위 테스트 4건, `ScanMetadataReaderAdapter` 단위 테스트 3건.
