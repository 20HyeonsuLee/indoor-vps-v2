# ADR-013: floor → area 명시 모델 (1급 entity)

## 상태
accepted

supersedes: 없음 (이전 plan에서 검토됐던 "floor_section auto-derived from connected component" 접근을 폐기, 본 ADR로 대체)

## 배경
현 스키마는 1 floor = 1 단위 scan 공간으로 가정한다. `floor_scan` unique `(floor_id) WHERE active=true` 가 floor 당 active scan 1개를 강제하고, 모든 navigation/graph가 floor scope.

실제 케이스: 같은 floor에 물리적으로 격리된 다중 공간이 존재한다.
- 예 1: 2층에 사무동 영역(A)과 격리된 연구실 영역(B). 둘 사이 통로 없음. 같은 floor지만 routing graph는 단절.
- 예 2: 같은 층에 보안 구역으로 격리된 별관. floor entry는 같지만 ingress 경로가 분리.

이 케이스에서 두 영역을 한 scan으로 묶으면 (a) graph가 단절돼 routing 실패, (b) localization이 잘못된 영역으로 fix될 위험, (c) scan rebuild 시 한 영역만 갱신 불가.

이전 검토안: scan rtabmap graph의 connected component를 자동 추출해 `floor_section`으로 derive. 단점은 (a) component 경계가 noise/missing edge에 민감, (b) iOS에서 사용자 의도와 다른 분할이 나옴, (c) merge/rebuild 의미가 모호.

## 결정
**`floor_area`를 1급 entity로 도입한다. 1 floor = N area (default 1). scan은 area 단위로 귀속.**

| 항목 | 내용 |
|---|---|
| 모델 | `building 1─N building_floor 1─N floor_area 1─N area_scan 1─N area_chunk` |
| floor 생성 시 | default area 1개 자동 생성 (`is_default=true`, label `"Area 1"`) |
| area 생성 | 사용자 명시. default label = `"Area {area_index+1}"` |
| unique 제약 | `(floor_id, area_index)` UNIQUE / `floor_id` WHERE `is_default=true` UNIQUE (floor 당 default 1개 보장) |
| scan 단위 | area당 1 active scan. `floor_scan` unique 제약을 `(area_id) WHERE active=true` 로 재정의 |
| routing scope | (1) area 내부 = 단일 scan graph. (2) cross-area = building-wide composite graph (per-area graph + vertical_connector_stop 기반 cross-area edge) |
| iOS 호환 | 기존 `/floors/{id}/*` endpoint는 default area로 자동 매핑. 새 `/floors/{id}/areas/{areaId}/*`는 옵션 (후속 PR에서 iOS 채택). 응답에 `areaId` extra field 추가 — iOS Decodable이 unknown key 무시함으로 backward compatible |
| vertical_connector_stop | unique `(connector_id, area_id)`로 재정의. 기존 `level_id` 컬럼은 V5에서 area_id 추가, V6에서 drop |

## 대안 비교

| 대안 | 장점 | 단점 | 채택 여부 |
|---|---|---|---|
| (a) auto-derived component from rtabmap graph | 사용자 입력 0 | edge noise에 component 분할 민감. iOS UX에서 의도와 다른 분할. merge 의미 모호 | 폐기 |
| (b) `floor_section` 추상 derived | floor 모델 변경 없음 | section vs area 의미 중복. lifecycle 불분명 | 폐기 |
| (c) **`floor_area` 1급 entity (manual first-class)** | 사용자 의도 명확. lifecycle/CRUD/마이그레이션 명확. default area로 기존 1-area floor 케이스 0-cost 호환 | floor 단위 API에 default area 매핑 layer 필요. 마이그레이션 다단계 (V3~V6) | **채택** |

## 마이그레이션 전략 (forward-only Expand-Contract, 1 PR = 1 마이그레이션)

| 단계 | 파일 | 내용 | 위험 |
|---|---|---|---|
| V3 (Expand-DDL) | `V3__add_floor_area.sql` | `floor_area` 테이블 신설. `floor_scan`/`map_node`/`map_edge`/`floor_area_polygon`에 `area_id` nullable 컬럼 추가. 인덱스 추가 | nullable이므로 기존 read/write 무영향 |
| V4 (Expand-backfill) | `V4__backfill_floor_area.sql` | 모든 floor에 default area 1개 생성. 기존 `floor_scan`/`map_node`/`map_edge`/`floor_area_polygon` row의 `area_id`를 해당 floor의 default area로 채움 | floor 다수 시 long-running. 트랜잭션 분할 검토 |
| V5 (Constraint) | `V5__enforce_area_id_not_null.sql` | `area_id` NOT NULL 강제. `floor_scan` unique `(area_id) WHERE active=true` 추가, 기존 `(floor_id) WHERE active=true` drop. `vertical_connector_stop`에 `area_id` 추가 + NOT NULL + unique `(connector_id, area_id)` | application code가 V4까지의 backfill 완료 후 V5 마이그레이션 전에 area_id 채워 쓰기 시작해야 함 (deploy 순서 = code 먼저 area_id 쓰기 → V5) |
| V6 (Contract) | `V6__drop_level_id.sql` | `vertical_connector_stop.level_id` drop. 관련 native query 제거 | code에서 level_id 참조 제거 선행 필수 |

각 단계 = 별 PR. CLAUDE.md ADR-006 (1 PR 1 마이그레이션) 준수.

## 도메인 영향 요약 (자세한 표는 plan.md)

- 새 entity: `FloorAreaEntity` (domain.entity), `FloorAreaRepository` (domain.repository)
- area_id 추가 대상 entity: `FloorScanEntity`, `MapNodeEntity`, `MapEdgeEntity`, `FloorAreaPolygonEntity`, `VerticalConnectorStopEntity` (V5에서)
- UseCase 변경: `BuildJobRunner`, `ScanMetadataIntegrator`, `BuildGraphPersister`, `UploadScanChunkUseCase`, `MergeScansUseCase`, `ListScanChunksUseCase`, `PlanRouteUseCase`, `GetGraphUseCase`
- Controller 변경: 기존 `/floors/{id}/*` 유지 (default area 자동 매핑) + 새 `/floors/{id}/areas/*` 옵션 추가
- 충돌 주의: 기존 `floor_area_polygon.area_id` (polygon PK)와 새 `floor_area.area_id` (area PK) 컬럼명 동음이의. polygon은 별 컬럼 `polygon_id`로 rename 권장하나 본 ADR 범위 밖. 본 PR에서는 polygon 테이블에 `area_id` 추가 시 새 컬럼은 `floor_area_id`로 명명해 충돌 회피

## 결과

- 같은 floor 다중 격리 영역 케이스를 1급 모델로 표현
- 기존 single-area floor는 default area 자동 매핑으로 zero-impact
- routing scope 2단(area 내부 / cross-area composite)으로 명확화
- iOS는 본 PR 후에도 정상 동작 (default area 1개 사용)
- vertical connector의 floor 식별이 stringly-typed `level_id`에서 `area_id` UUID FK로 강화
- 트레이드오프: 다단계 마이그레이션(V3~V6) deploy coordination 필요. 각 단계 사이 application code가 양쪽 스키마와 호환되도록 작성 필요 (forward-compat read, dual-write 패턴)
