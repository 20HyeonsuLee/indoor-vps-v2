# CLAUDE.md Conformance — 5 Cycle Plan

task_id: 0002
branch: task/0001-spring-migration
base_ref: main
target_violations: CRITICAL 2 + MAJOR 13 = 15
strategy: D1~D6 결정 → cycle별 응집된 변경 → cycle 단위 build/test green 유지

---

## D1~D6 결정 (cycle 진입 전 고정)

| ID | 결정 | 근거 | 트레이드오프 |
|----|------|------|-------------|
| D1 | **C 정공법 + B 보조**. application은 자체 Command/Query/Result record를 정의. Controller가 web.dto ↔ application record 매핑. 외부 IO(ScanArchiveStorage, StreamingScanStorage, RtabmapGraphReader, RtabmapReprocessService)는 domain.port 인터페이스 + infrastructure adapter. | CLAUDE.md `application.naming = "Command/Query/Result record"`, `domain.contains = "외부 시스템 port"`. A안(DTO→application)은 HTTP 모델 누출로 즉시 위반. | record 신설 비용 + Controller 매핑 보일러플레이트. RtabmapGraphReader 등은 port 추출 시 IO 시그니처 안정성 필요 |
| D2 | **sub-UseCase 분할 (Cycle 4·5 분산)**. ScanApplicationService → StartStreamingScanUseCase / PushStreamingFramesUseCase / FinalizeStreamingScanUseCase / UploadScanChunkUseCase / MergeScansUseCase / ProcessFloorUseCase / Query 묶음(QueryScanChunksUseCase). NavigationApplicationService → PlanRouteUseCase / GetGraphUseCase / GetGraphMetadataUseCase. | CLAUDE.md `ddd = "UseCase 1개 = 클래스 1개"`. 보조 service 분할은 instance var 3개 초과 등 다른 위반을 다시 만든다. | rename 범위 큼 → cycle 4·5 분리. 한 cycle에 ApplicationService 1개씩 |
| D3 | **D2와 동일 cycle에 처리**. *ApplicationService 분할 = *UseCase 신설 = naming도 자동 정렬. 기존 *ApplicationService는 facade로 잠깐 남겨 호출자 점진 마이그레이션 X — 한 cycle 안에서 호출자(Controller)도 함께 갱신. | 부분 rename은 facade가 살아남아 후속 cycle에 잔존 위반. D1의 Controller 매핑 작업과 한 cycle에 묶어 처리 효율. | cycle 4·5의 변경 폭이 가장 큼 → cycle 1~3에서 base infra 안정화 후 진입 |
| D4 | **권장: 옵션 B (CLAUDE.md 갱신 + ADR 추가)**. pyproject.toml/uv가 modern Python 표준이고 이미 uv.lock 채택. CLAUDE.md `python_integration.entry_convention`을 `python/<pipeline>/main.py 또는 pyproject.toml entry-point`로 완화. ADR-009 신설. 그러나 사용자 결정 필요 — 옵션 A(main.py + requirements.txt)도 cycle 3에서 30분 안에 구현 가능. | 옵션 A: 기존 규약 유지·tooling 단순. 옵션 B: 현 코드 그대로·생태계 표준. **Cycle 3에서 사용자 핑퐁 후 둘 중 선택**. blocker 없음 — 어느 쪽이든 cycle 3 안에 처리 가능 | A는 uv 자산 폐기, B는 CLAUDE.md SSOT 변경 |
| D5 | **IndoorVpsV2ApplicationTests 삭제 + OpenApiContractSmokeTest Karate 이관**. Karate runner 자체가 SpringBoot context를 띄우므로 context loading은 Karate가 이미 검증. OpenAPI 계약은 `karate/contract/openapi.feature`로 이관 (springdoc `/v3/api-docs` GET 후 JSON 검증). | CLAUDE.md `test/java.forbidden = "@SpringBootTest 통합테스트 신규 작성 금지 (Karate가 대체)"` 와 정합. | OpenAPI smoke를 feature로 옮기면 schema diff assertion이 karate match로 표현됨 — JSON path 작성 비용 |
| D6 | **NavigationGraphService 인스턴스 메서드로 이동**. 1.2 m/s walking speed는 NavigationGraph(또는 Route) 도메인 규칙. NavigationGeometry는 순수 수학(distance, dot product)만 static 유지하고 walking-time 계산은 NavigationGraphService.estimateWalkingSeconds(distance) 인스턴스 메서드로 이동. 추가로 `domain/navigation/WalkingSpeed` VO 신설 검토 (속도 상수 캡슐화). | static biz logic 금지는 CLAUDE.md forbidden. 순수 기하 함수는 utility로 허용(Math.hypot과 동급). | NavigationGraphService 인스턴스 var 1개 증가 (현재 한도 내) |

---

## Cycle 1 — 인프라 정합 (compose base + 폴더 rename + Filter 이동)

- **처리**: 위반 3, 4, 5
  - HttpRequestLoggingFilter: `infrastructure/logging/` → `config/web/`
  - `src/test/resources/karate/acceptance/` → `karate/mapping/`
  - `docker-compose.yml` base 생성 + local/prod overrides에서 중복 12개 env 제거
- **접근**:
  1. compose base 추출: 공통 env(POSTGRES_DB, JVM_OPTS, INDOOR_*)를 docker-compose.yml로. local/prod는 차분만(host port, JVM heap, profile).
  2. Filter `git mv` + 패키지 선언 갱신 + Bean scan 경로 확인 (현재 IndoorVpsV2Application의 @SpringBootApplication scan 범위 안).
  3. Karate runner 클래스의 @KarateOptions classpath 갱신: `karate/acceptance` → `karate/mapping`. classpath 참조 grep 모두 교체.
- **회귀 위험**:
  - compose: docker-compose 합성 우선순위(base + override) — local/prod에서 base의 env가 override 안 되면 기존 동작 깨짐. ENV 충돌 시 override 미적용 silent fail.
  - Filter 이동: Spring component scan 범위 외로 나가면 logging 미동작.
  - Karate: classpath 잘못 잡으면 0개 feature run으로 silently pass.
- **검증**:
  - `./gradlew build -x test` (compile)
  - `./gradlew karateTest` (feature 실행 수 ≥ 이전과 동일 — count로 확인)
  - `docker compose -f docker-compose.yml -f docker-compose.local.yml config` 로 머지 결과 dump → 모든 env 키 존재 검증
  - `grep -rn "karate/acceptance" src/` empty 확인
  - 수동 요청 한 번 + 로그에 `HttpRequestLoggingFilter` 출력 확인

---

## Cycle 2 — null 제거 + 도메인 위치 정정 (위반 6, 7, 8)

- **처리**: 위반 6 (return null 5건), 7 (infra @Transactional), 8 (NavigationGeometry static biz logic)
  - NavigationResponseMapper:97, ScanApplicationService:360/372/398, PoiRouteTargetResolver:21, BuildJobRunner:117, JpaLocalizationMapProvider:83 → Optional<T> 또는 Null Object
  - JpaLocalizationMapProvider:17 — @Transactional 제거. Repository는 트랜잭션 없이도 호출 가능. 트랜잭션이 필요하면 호출자(UseCase)에 이동
  - NavigationGeometry walking-speed 메서드 → NavigationGraphService 인스턴스 메서드 + (선택) `domain/navigation/WalkingSpeed` VO
- **접근**:
  1. 시그니처 변경 → call-site 컴파일러 가이드로 수정. Optional 반환은 caller에서 `.orElseThrow` 또는 `.map`. Null Object 적용 대상은 PoiRouteTargetResolver(빈 Resolver 객체).
  2. JpaLocalizationMapProvider의 @Transactional이 보호하던 read 패스 — 호출자가 이미 트랜잭션 안인지 stack 추적. SlamLocalizationService가 호출자 → SlamLocalizationService에 `@Transactional(readOnly = true)` 가 이미 있는지 확인하고 없으면 추가.
  3. NavigationGeometry: 함수 1개씩 이동. NavigationApplicationService 호출 site도 함께 갱신.
- **회귀 위험**:
  - Optional 변환 누락 → NPE가 NoSuchElementException로 형만 바뀜. 모든 caller 패치 누락 시 컴파일 에러로 잡힘(안전).
  - @Transactional 제거 시 lazy loading proxy 사용처가 LazyInitializationException. 현 JpaLocalizationMapProvider가 lazy collection 접근하는지 확인 필요.
  - NavigationGeometry 이동: static import 잔존 시 컴파일 에러로 잡힘.
- **검증**:
  - `./gradlew build` (단위 + 컴파일)
  - `./gradlew karateTest`
  - `grep -rn "return null" src/main/java/kr/ac/koreatech/indoor/vps/` empty
  - `grep -rn "@Transactional" src/main/java/kr/ac/koreatech/indoor/vps/contexts/*/infrastructure/` empty
  - `grep -rn "public static.*walk\|public static.*estimate" src/main/java/...domain/navigation/NavigationGeometry.java` empty

---

## Cycle 3 — Python entry convention + @SpringBootTest 제거 (위반 2, 9, D4 확정)

- **처리**: 위반 2, 9 + D4 사용자 핑퐁
  - **D4 핑퐁 필요** (옵션 A vs B). plan 단계에서 권장 B, cycle 3 진입 시 사용자 confirm 한 줄로 결정.
  - IndoorVpsV2ApplicationTests 삭제
  - OpenApiContractSmokeTest → `karate/contract/openapi.feature` 이관 후 IT 삭제
- **접근 (옵션 B 가정)**:
  1. ADR-009 신설: "Python entry는 pyproject.toml + uv 또는 main.py 둘 다 허용". CLAUDE.md `python_integration.entry_convention` 갱신. _project_struct의 `<pipeline-name>.contains`도 `pyproject.toml | requirements.txt`, `entry script` 로 완화.
  2. python/legacy_backend/README.md에 invocation 예시(`uv run python -m legacy_backend`) 명시.
  3. PythonBridgeClient의 ProcessBuilder 실행 경로 확인 — 이미 uv 기반이면 변경 없음, 아니면 `uv run` prefix로 정렬.
  4. OpenAPI smoke를 Karate feature로 이관. existing JSON contract assertion을 `match response ==` 형식으로 변환.
  5. IndoorVpsV2ApplicationTests 삭제.
- **접근 (옵션 A 채택 시 분기)**:
  1. `python/legacy_backend/main.py` 생성 — entry는 기존 `legacy_backend` package의 main 함수 import + 실행 1줄.
  2. `uv export --format requirements-txt > python/legacy_backend/requirements.txt`.
  3. PythonBridgeClient ProcessBuilder를 `python main.py` 호출로 정렬.
- **회귀 위험**:
  - 옵션 B: ADR 추가가 SSOT 변경이므로 후속 cycle의 `_project_struct` grep 검증 기준이 바뀜.
  - 옵션 A: bridge_entry.py 잔존 시 두 진입점 존재 → 어느 쪽이 호출되는지 모호. 옵션 A 선택 시 bridge_entry.py 삭제 필수.
  - OpenAPI Karate 이관: springdoc이 활성화돼 있는지(`/v3/api-docs` accessible) 확인. 비활성이면 build.gradle.kts에 의존성 확인.
  - IndoorVpsV2ApplicationTests 삭제 후 CI에 context load 검증이 0건이면 silent. Karate runner가 띄우므로 OK.
- **검증**:
  - `./gradlew build` + `./gradlew karateTest`
  - 옵션 A: `python python/legacy_backend/main.py < sample.json` 수동 한 번
  - 옵션 B: `uv run python -c "from legacy_backend import main; main()"` 수동 한 번 + ADR-009 docs/adr/ 추가
  - `grep -rn "@SpringBootTest" src/test/` empty
  - karate feature count = 이전 + 1 (openapi.feature)

---

## Cycle 4 — ScanApplicationService 분할 + D1 적용 (위반 1 일부, 10, 11, 12, 13)

- **처리**:
  - ScanApplicationService(420줄, public 메서드 10개) → 7개 UseCase로 분할
    - StartStreamingScanUseCase, PushStreamingFramesUseCase, FinalizeStreamingScanUseCase, UploadScanChunkUseCase, ListScanChunksUseCase(+ deleteScanChunk), MergeScansUseCase(+ mergeStatus), ProcessFloorUseCase(+ processStatus)
  - StreamingScanStorageService(671줄) → port 추출 `domain/scan/port/StreamingScanStorage` + adapter 그대로. 본체는 method 추출로 200줄 이하로 줄임 (file rotation, frame stats, manifest IO 각각 helper class)
  - ScanArchiveStorageService(324줄) → port 추출 + adapter. 본체 method 추출로 200줄 이하
  - RtabmapReprocessService(489줄) → port `domain/build/port/RtabmapReprocessor` + adapter. 큰 함수 분리
  - FixtureCaptureService(239줄) → 단순 method 추출 (관심사 분리 — 캡처 / 직렬화 / 검증)
  - ScanController → 새 7개 UseCase에 dispatch. application Command/Query record 신설, web.dto ↔ command 매핑은 Controller 책임
- **접근**:
  1. 도메인 port 먼저 정의 (StreamingScanStorage, ScanArchiveStorage, RtabmapReprocessor). 기존 service는 implements 키워드만 추가.
  2. ScanApplicationService를 UseCase 7개로 분할. 공통 의존성(floorService, repositories)은 각 UseCase 생성자에 주입. 헬퍼 메서드(publicScanFileName, publicBuildState)는 `domain/scan/ScanPresentation` 같은 도메인 헬퍼 또는 각 UseCase private.
  3. ScanController는 7개 UseCase 직접 호출. web.dto → application Command record 매핑은 controller 내부 static 변환 메서드.
  4. application.scan 패키지에 Command/Query/Result record 7쌍 생성.
  5. 큰 infra service 본체 분할: StreamingScanStorageService → StreamingScanStorageAdapter(port impl, 100줄 이내) + StreamingFrameWriter + ScanManifestStore + FrameStatsCalculator. 각각 200줄 이내.
- **회귀 위험**:
  - 가장 큰 변경 cycle. 트랜잭션 경계 이동(@Transactional이 UseCase 메서드에 옮겨가야 함). 한 시퀀스를 여러 UseCase로 쪼개면 트랜잭션이 분리됨 → ScanController에서 여러 UseCase 호출 시 일관성 깨질 위험. 시퀀스 의존 호출은 한 UseCase에 묶기 필수.
  - port 추출 시 StoredScanArchive, StartedStreamingScan 같은 inner record가 infra → domain으로 이동. 이 record의 필드가 infra 타입(Path 등) 노출하면 도메인 오염. 필요 시 record 변환.
  - StreamingScanStorageService 분할 중 file lock / concurrent write 보장 잃을 위험. method 추출만 하고 동기화 블록 범위 유지.
  - 7 UseCase 클래스 추가 → `application.scan` 폴더 file_lines 합계는 늘지만 파일당 200 이내 보장.
- **검증**:
  - `./gradlew build` + `./gradlew karateTest` (mapping/scan 시나리오 전체)
  - file_lines 한도: `wc -l src/main/java/.../scan/*.java src/main/java/.../storage/*.java src/main/java/.../rtabmap/*.java` 모두 ≤ 200
  - `grep -rn "import kr\.ac\.koreatech\..*\.infrastructure\." src/main/java/.../application/scan/` empty
  - `grep -rn "import kr\.ac\.koreatech\..*\.infrastructure\." src/main/java/.../application/build/BuildJobRunner.java` empty (RtabmapReprocessor port 사용으로 전환됨)
  - public 메서드 수: 각 UseCase 클래스 ≤ 5

---

## Cycle 5 — Navigation/Poi/Floor/Building/Passage 분할 + 잔여 위반 정리 (위반 1 나머지, 14, 15)

- **처리**:
  - NavigationApplicationService(234줄, instance 9개) → PlanRouteUseCase / GetGraphUseCase / GetGraphMetadataUseCase (대표 3개). 의존성은 UseCase별로 필요한 것만.
  - NavigationResponseMapper → application의 Result record + Controller 매핑으로 흡수 (별 mapper 클래스 제거)
  - BuildJobRunner(206줄) → 단순 method 추출 + RtabmapReprocessor port 사용 (cycle 4에서 port는 이미 만듦)
  - IndoorProperties(212줄) → 중첩 record 그룹별로 별 파일 분리 (`IndoorProperties` root + `BridgeProperties`, `StorageProperties`, `LocalizationProperties` 등 nested record를 별 file로 옮김)
  - PoiApplicationService / FloorApplicationService / BuildingApplicationService / PassageApplicationService → UseCase 분할 (각 service의 public 메서드 수가 5 이하면 rename만, 초과하면 분할). D1 적용: web.dto import 제거 + application Command/Query/Result record 신설 + Controller 매핑
- **접근**:
  1. cycle 4 패턴 그대로 반복. 각 context별 application 폴더에 sub-folder(`application/navigation/route/`, `application/navigation/graph/`) 또는 flat UseCase.
  2. NavigationResponseMapper는 Controller로 흡수 — application의 Result record가 도메인 객체 그대로 노출하고, Controller가 HTTP response 변환.
  3. IndoorProperties 분할: 각 nested @ConfigurationProperties record를 같은 `config/properties/` 폴더로 분리. @EnableConfigurationProperties 등록 갱신.
  4. BuildJobRunner: cycle 4의 RtabmapReprocessor port로 import 교체 + return null 제거(cycle 2와 별개로 잔존 시).
- **회귀 위험**:
  - IndoorProperties 분할: @ConfigurationProperties prefix 그대로 유지해야 application.yml 키 매핑이 동일. prefix 누락 시 silent null binding.
  - NavigationApplicationService → 3 UseCase: 라우팅 계산이 graph 조회 + path 계산을 한 트랜잭션에 묶었던 경우 분리되면 read consistency 미세 깨질 수 있음. 동일 호출 chain 안이면 PlanRouteUseCase 한 클래스에 graph fetch + route calc 함께 보유.
  - Controller가 매핑 책임을 받으면 Controller 라인 수 증가 → file_lines 모니터 필수.
- **검증**:
  - `./gradlew build` + `./gradlew karateTest` (전체 시나리오)
  - file_lines 한도 grep: `find src/main/java -name '*.java' -exec wc -l {} + | awk '$1 > 200'` empty
  - `grep -rn "import kr\.ac\.koreatech\..*\.infrastructure\." src/main/java/kr/ac/koreatech/indoor/vps/contexts/mapping/application/` empty (D1 완전 해소)
  - `grep -rn "instance.*[4-9]" + 수동 ls`: 각 클래스 인스턴스 var ≤ 3 확인
  - `grep -rn "return null" src/main/java/` empty (cycle 2와 함께 최종 0)
  - Karate count = cycle 1 baseline + 1 (openapi)

---

## 위반 ↔ Cycle 매트릭스

| 위반 | 카테고리 | Cycle |
|------|---------|-------|
| 1 (app→infra 9파일) | CRITICAL | 4 (Scan/Build), 5 (Navigation/Poi/Floor/Building/Passage) |
| 2 (python entry) | CRITICAL | 3 |
| 3 (logging filter 위치) | MAJOR | 1 |
| 4 (karate 폴더명) | MAJOR | 1 |
| 5 (compose base 부재) | MAJOR | 1 |
| 6 (return null) | MAJOR | 2 (+ cycle 5 잔여 확인) |
| 7 (infra @Transactional) | MAJOR | 2 |
| 8 (NavigationGeometry static) | MAJOR | 2 |
| 9 (@SpringBootTest 2개) | MAJOR | 3 |
| 10 StreamingScanStorageService 671 | MAJOR | 4 |
| 11 RtabmapReprocessService 489 | MAJOR | 4 |
| 12 ScanApplicationService 420 + 메서드 10 | MAJOR | 4 |
| 13 ScanArchiveStorageService 324 | MAJOR | 4 |
| 14 FixtureCaptureService 239 / NavigationApplicationService 234 / IndoorProperties 212 / BuildJobRunner 206 | MAJOR | 4 (Fixture), 5 (Navigation/IndoorProperties/BuildJobRunner) |
| 15 (인스턴스 var 9 — Navigation) | MAJOR | 5 (분할 부산물) |

15건 전부 5 cycle 안에 분배 완료. **blocker 없음**. 단 D4는 cycle 3 진입 시 사용자 한 줄 confirm 필요(옵션 A vs B 선택). 옵션 미지정 시 plan 권장(B) 적용.

---

## Cycle 진입 전 핑퐁 1회 필요 항목

1. **D4 옵션 A vs B 선택** (cycle 3 entry). 미선택 시 B 진행.
2. (선택) cycle 4·5의 sub-UseCase 명명 패턴 — `application/scan/StartStreamingScanUseCase.java` flat vs `application/scan/streaming/StartStreamingScanUseCase.java` nested. 권장: flat (UseCase 7개 정도면 직접 files 15 한도 내).
