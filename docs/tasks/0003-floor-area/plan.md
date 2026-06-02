# floor → area 1급 entity — 후속 PR 로드맵

task_id: 0003
related_adr: ADR-013 (`docs/decisions/0013-floor-area.md`)
strategy: Expand-Contract forward-only. 1 PR = 1 마이그레이션 + 동반 code change. iOS는 default area 매핑으로 모든 단계에서 정상 동작.

---

## PR 분리

| PR | 마이그레이션 | code 변경 요지 | deploy 후 시스템 상태 |
|---|---|---|---|
| PR-A | V3 (Expand-DDL) | `FloorAreaEntity` + `FloorAreaRepository` 신설. `*Entity`에 `areaId` nullable 필드 추가. read 경로는 area_id null이어도 동작 (legacy fallback) | 기존 동작 유지. 새 컬럼은 모두 null |
| PR-B | V4 (Expand-backfill) | floor 생성 UseCase에 default area 자동 생성 추가. application code가 write 시 항상 area_id 채움(dual-write). 기존 row backfill | 모든 row가 area_id를 가짐. 신규 floor는 default area 자동 생성 |
| PR-C | (마이그레이션 없음) | build pipeline·routing이 area_id 기반으로 동작. iOS 호환 layer: `/floors/{id}/*` → default area 자동 매핑. building-wide composite graph 구현. 새 `/floors/{id}/areas/*` endpoint 옵션 노출. `vertical_connector_stop` write 시 area_id 함께 채움(dual-write) | iOS는 default area 1개로 정상 동작. 다중 area는 새 endpoint로 접근 가능 |
| PR-D | V5 (Constraint) | `area_id` NOT NULL 강제 + `floor_scan` active unique를 area_id 기준으로 전환. `vertical_connector_stop` area_id NOT NULL 강제 + unique 재정의. 기존 native query (`VerticalConnectorRepository`의 level_id join)는 area_id join으로 재작성 | application은 area_id 없는 row 생성 불가. level_id는 deprecated이지만 컬럼 남아있음 |
| PR-E | V6 (Contract) | `level_id` 참조 코드 전부 제거 후 컬럼 drop. `VerticalConnectorStopEntity.levelId` 필드 제거 | level_id 완전 제거 |

각 PR 사이 deploy 순서 엄수: code 먼저 → 마이그레이션 → 검증. 특히 V5는 backfill 누락 row 있으면 NOT NULL constraint violation.

---

## 도메인 영향 표

| 영역 | 파일 | PR-A | PR-B | PR-C | PR-D | PR-E |
|---|---|---|---|---|---|---|
| Entity | `FloorAreaEntity.java` (신규) | 신설 | — | — | — | — |
| Entity | `FloorScanEntity` | `@Column area_id` nullable | — | — | NOT NULL 강제 | — |
| Entity | `MapNodeEntity` | `@Column area_id` nullable | — | — | NOT NULL 강제 | — |
| Entity | `MapEdgeEntity` | `@Column area_id` nullable | — | — | NOT NULL 강제 | — |
| Entity | `FloorAreaPolygonEntity` | `@Column floor_area_id` nullable (이름 충돌 회피) | — | — | NOT NULL 강제 | — |
| Entity | `VerticalConnectorStopEntity` | — | — | `area_id` nullable + dual-write | NOT NULL + unique `(connector_id, area_id)` | `levelId` 필드 제거 |
| Repository | `FloorAreaRepository` (신규) | 신설 (`findByFloor_FloorId`, `findDefaultByFloor_FloorId`) | — | — | — | — |
| Repository | `FloorScanRepository` | `findByArea_AreaId*` 추가, 기존 메서드 유지 | — | — | 기존 floor 기반 메서드 deprecated | — |
| Repository | `VerticalConnectorRepository` | — | — | native query (level_id join) 영역 호환 작성 | native query를 area_id join으로 교체 | — |
| UseCase | floor 생성 UseCase (mapping/application/floor) | — | default area 자동 생성 로직 | — | — | — |
| UseCase | `UploadScanChunkUseCase` | — | area_id 함께 저장 (default area) | area_id parameter 받기 | — | — |
| UseCase | `MergeScansUseCase` | — | — | area scope로 merge | — | — |
| UseCase | `ListScanChunksUseCase` | — | — | area 기준 list + floor 기준 fallback | — | — |
| UseCase | `BuildJobRunner` | — | — | area 단위 build | — | — |
| UseCase | `ScanMetadataIntegrator` | — | — | area_id를 entity 생성 시 주입. connector stop은 area_id로 lookup | — | — |
| UseCase | `BuildGraphPersister` | — | — | persist 시 area_id 함께 | — | — |
| UseCase | `PlanRouteUseCase` | — | — | same-area = per-area graph / cross-area = composite | — | — |
| UseCase | `GetGraphUseCase` | — | — | area scope graph response. floor scope = default area | — | — |
| Controller | `FloorController` (또는 동등) | — | — | 기존 `/floors/{id}/*` 유지 + default area 자동 매핑 | — | — |
| Controller | `FloorAreaController` (신규) | — | — | `/floors/{floorId}/areas`, `/floors/{floorId}/areas/{areaId}/*` | — | — |
| DTO | mapping web dto | — | — | response에 `areaId` extra field 추가 (iOS Decodable 무시) | — | — |
| Karate feature | `karate/mapping/floor_area/*.feature` (신규) | — | feature 작성 (default area auto-creation) | feature 확장 (multi-area, cross-area routing) | — | — |

---

## V3 DDL 초안 (확정 시 `src/main/resources/db/migration/V3__add_floor_area.sql`로 작성)

```sql
-- V3: floor_area 1급 entity 신설 (Expand-DDL only, backfill 없음)
-- 컬럼 추가는 모두 nullable로 시작 — 기존 read/write 무영향
-- backfill 및 NOT NULL 강제는 V4/V5에서 분리 수행

CREATE TABLE IF NOT EXISTS floor_area (
    area_id     UUID        PRIMARY KEY,
    floor_id    UUID        NOT NULL REFERENCES building_floor(floor_id) ON DELETE CASCADE,
    area_index  INTEGER     NOT NULL,
    label       TEXT        NOT NULL,
    is_default  BOOLEAN     NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_floor_area_floor_index UNIQUE(floor_id, area_index)
);

-- floor 당 default area 1개 보장
CREATE UNIQUE INDEX IF NOT EXISTS uq_floor_area_default
    ON floor_area(floor_id) WHERE is_default = true;

-- scan/graph 테이블에 area_id 추가 (nullable)
ALTER TABLE floor_scan
    ADD COLUMN IF NOT EXISTS area_id UUID
    REFERENCES floor_area(area_id) ON DELETE CASCADE;

ALTER TABLE map_node
    ADD COLUMN IF NOT EXISTS area_id UUID
    REFERENCES floor_area(area_id) ON DELETE CASCADE;

ALTER TABLE map_edge
    ADD COLUMN IF NOT EXISTS area_id UUID
    REFERENCES floor_area(area_id) ON DELETE CASCADE;

-- floor_area_polygon은 PK가 이미 area_id이므로 이름 충돌 회피용 별 컬럼명
ALTER TABLE floor_area_polygon
    ADD COLUMN IF NOT EXISTS floor_area_id UUID
    REFERENCES floor_area(area_id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS ix_floor_scan_area ON floor_scan(area_id);
CREATE INDEX IF NOT EXISTS ix_map_node_area   ON map_node(area_id);
CREATE INDEX IF NOT EXISTS ix_map_edge_area   ON map_edge(area_id);
CREATE INDEX IF NOT EXISTS ix_floor_area_polygon_floor_area ON floor_area_polygon(floor_area_id);
```

⚠️ `vertical_connector_stop`은 PR-C 코드 변경 후 PR-D V5에서 area_id 추가 + NOT NULL + unique 재정의. V3 시점에 동시 추가하면 `(connector_id, area_id)` unique를 만족 못하는 기존 row가 NOT NULL 강제 시 깨짐 → 별 PR로 분리.

---

## 이름 충돌 처리

| 충돌 | 해결 |
|---|---|
| 기존 `floor_area_polygon.area_id` (polygon PK) vs 새 `floor_area.area_id` (공간 area PK) | 본 PR 범위에서 polygon 테이블의 PK rename은 안 함 (영향 큼). polygon이 area를 참조할 때는 컬럼명을 `floor_area_id`로 명명 — 의미 명확화 + 충돌 회피. 향후 polygon PK를 `polygon_id`로 rename할지는 별 ADR. |
| 새 `FloorAreaEntity` (Java) vs 기존 `FloorAreaPolygonEntity` (Java) | 클래스명 다르므로 Java 레벨 충돌 없음. import alias 불필요. |

---

## building-wide composite graph 설계 (PR-C 상세)

| 항목 | 내용 |
|---|---|
| Value object | `BuildingRouteGraph` (domain.navigation) |
| 구성 | union(per-area-scan graph) + cross-area edges |
| cross-area edge source | `vertical_connector_stop`. 같은 connector를 공유하는 두 area의 route_node를 잇는 edge 생성 |
| edge weight | connector_type 기반 가중치 (계단 vs 엘리베이터). 기존 `VerticalPassageResult` 가중 로직 재사용 |
| 캐시 키 | `building_id` + sorted active `area_scan.scan_id` 집합 hash |
| invalidation | `build_job` succeed 시 해당 area의 building composite 무효. area의 active scan 변경 시 무효 |
| 캐시 위치 | `NavigationGraphService` 안 in-memory `ConcurrentHashMap<BuildingCacheKey, BuildingRouteGraph>`. multi-instance 시 별 ADR로 외부 캐시 검토 |
| read API | `PlanRouteUseCase`가 same-area면 per-area graph로, cross-area면 composite로 dispatch |

---

## iOS 호환 전략 (PR-C 상세)

| 경로 | 동작 |
|---|---|
| 기존 `GET /floors/{floorId}/...` | server에서 default area 자동 조회 후 area scope로 처리. response에 `areaId` extra field 포함 |
| 기존 `POST /floors/{floorId}/scans/...` upload | area_id 미지정 시 default area에 귀속 |
| 신규 `GET /floors/{floorId}/areas` | area list 반환 |
| 신규 `POST /floors/{floorId}/areas` | area 생성 (label, area_index optional) |
| 신규 `*/areas/{areaId}/...` | area scope 명시 |
| iOS Decodable | unknown key 무시 default 동작 → `areaId` extra field 안전. 단 iOS가 keyDecodingStrategy를 strict로 켜뒀다면 별도 확인 필요 (현 코드 base 점검 후 확정) |

---

## 검증 체크리스트 (각 PR 공통)

- `./gradlew build -x test` green
- `./gradlew test` green
- `./gradlew karateTest` green (PR-B 이후 default area auto-creation feature 추가)
- `./gradlew flywayMigrate` clean apply (각 PR의 V* 1개)
- 마이그레이션 rollback 시나리오 검토 (Flyway forward-only이므로 down 없음 — 실패 시 hotfix-forward)
- iOS 회귀: 기존 endpoint 모든 response shape 동일 + `areaId` extra field만 추가
