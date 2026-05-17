# ADR-012: navigation graph source는 scan_metadata.db만

## 상태
accepted

## 배경
이전 구현은 rtabmap pose graph(Node/Link 테이블)를 PostgreSQL map_node/map_edge에 적재했다.
결과: map_node 37개(rtabmap corridor 35 + POI 2), map_edge 76개(dense trajectory).
navigation 용도에서 dense trajectory는 노이즈이고 relocalization에만 필요하다.

## 결정
- navigation graph의 source는 scan_metadata.db만.
- `branch_mark.corridor` → `map_node` (NodeType.corridor).
- `branch_mark.corner` → `floor_area_polygon` 테이블 (같은 mark_session_id 그룹을 PolygonZ로 보존).
- `poi_mark` → `map_node` (NodeType.poi) + `poi_canonical`.
- `interfloor_mark` → `map_node` (NodeType.poi) + `vertical_connector_stop`.
- `branch_edge.sequential` → `map_edge` (EdgeType.rtabmap_link 재사용).
- `branch_edge.cornerPolygon` → 무시 (polygon은 floor_area_polygon에서 처리).
- spur edges: poi/connector → 가장 가까운 corridor (0.5m 이내).
- rtabmap.db 파일은 relocalization용으로 그대로 보존. PostgreSQL 비적재.

## 대안
- rtabmap_link 유지: dense trajectory 오염 문제 지속.
- corner를 junction 노드로: map_node에 기하 정보만 저장, polygon 유실.
- corner를 source_ref jsonb로: map_node에 메타 포함, polygon 구조 불명확.

## 결과
- navigation 노드 ~5개 수준으로 축소 (corridor 3 + poi 1 + connector 1 예시).
- rtabmap.db 파일은 그대로 보존, relocalization에 사용.
- pose graph 시각화 불가 — 필요 시 별도 endpoint에서 rtabmap.db 직접 read.
- floor_area_polygon 테이블로 corner 기반 구역 폴리곤 보존.
