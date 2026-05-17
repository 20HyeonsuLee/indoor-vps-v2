```yml
_project_meta:
  stack:
    - java
    - spring-boot
    - jpa-hibernate
    - postgres
    - flyway
    - gradle-kotlin-dsl
    - docker
    - docker-compose
    - junit5
    - assertj
    - karate
    - slf4j
    - logback
    - python
    - pip
    - rtab-map
    - superpoint
    - lightglue
  environment: [local, prod]
  ci-cd: [github, github-actions, github-secrets]
  ci-cd: [github, github-actions, github-secrets]
  conventions: [conventional-commits, github-flow]
  config: [yml]

  forbidden:
    - "Lombok @Setter/@Getter/@Data 금지 (Tell Don't Ask 위배)"
    - "null 반환/전달 금지 (Optional 또는 Null Object)"
    - "else 금지 (Early Return / 가드 클로즈)"
    - "Controller에서 Repository 직접 호출 금지 (반드시 UseCase 경유)"
    - "다른 Bounded Context의 domain/infrastructure 직접 import 금지 (application service만 허용)"
    - "Aggregate 경계 넘는 트랜잭션 금지 (application service에서 조율)"
    - "Domain Event 사용 금지 (application service에서 직접 조율로 통일)"
    - "비즈니스 로직을 utility static 메서드로 노출 금지"
    - "이미 배포된 Flyway 마이그레이션 수정 금지"
    - "1 PR에 2개 이상 마이그레이션 금지 (DDL과 백필도 분리)"
    - "Python 스크립트 안에 비즈니스 로직 금지 (입출력 JSON 처리만)"
    - "config 중복 금지 — 환경 파일에 기본 파일과 같은 값 적기 금지"

  validation:
    install:
      - "./gradlew dependencies"
      - "pip install -r python/<pipeline>/requirements.txt"
    build: ["./gradlew build -x test"]
    test: ["./gradlew test"]
    e2e: ["./gradlew karateTest"]
    lint: ["./gradlew spotlessCheck"]
    format: ["./gradlew spotlessApply"]
    migrate: ["./gradlew flywayMigrate"]
    local-up: ["docker compose up -d"]

  config_strategy:
    principle: "기본 파일에 모든 기본값. 환경 파일은 차분만."
    duplication_forbidden: true
    layers:
      spring:
        base: src/main/resources/application.yml
        overrides:
          - src/main/resources/application-local.yml
          - src/main/resources/application-prod.yml
      docker:
        base: docker-compose.yml
        overrides:
          - docker-compose.prod.yml

  coding_principles:
    ddd:
      - "패키지 by Bounded Context (각 context에 application/domain/infrastructure 적층)"
      - "Aggregate 경계 = 트랜잭션 일관성 경계 (한 번의 내부 트랜잭션으로 완전 일관)"
      - "Aggregate 간 참조는 ID로만 (객체 참조 금지)"
      - "Domain Event 사용 안 함 — application service에서 직접 조율"
      - "UseCase 1개 = 클래스 1개 (CreateOrderUseCase, CancelOrderUseCase 형태)"
      - "Application Service가 트랜잭션 시작점 (@Transactional은 UseCase 메서드)"
      - "Repository 인터페이스는 domain에 Spring Data JPA interface로 직접 선언 (별도 구현체 없음)"
      - "Aggregate Root = JPA Entity 그 자체 (별도 매퍼/JpaEntity 분리 없음 — 보일러플레이트 절감 트레이드오프)"
      - "Bounded Context 간 통신 = 다른 context의 application service 직접 호출만 허용"
      - "Bounded Context를 함부로 자르지 않음 — 응집도 우선, 새 기능은 기존 context 안으로"
    oo_calisthenics:
      - "메서드당 들여쓰기 1단계"
      - "else 금지 (가드 클로즈로 early return)"
      - "원시값과 문자열 포장 (VO)"
      - "일급 컬렉션 사용"
      - "한 줄에 점 하나 (Law of Demeter)"
      - "축약 금지"
      - "엔티티 작게 유지"
      - "인스턴스 변수 3개 이하"
      - "getter/setter 금지"
      - "선언형 프로그래밍 우선"
    tell_dont_ask:
      - "객체에 묻지 말고 시키기 — getter로 값 꺼내 조건 분기 금지"
      - "비즈니스 로직은 데이터를 가진 객체 안으로"
    early_return:
      - "가드 클로즈로 비정상 경로 먼저 차단"
      - "else 사용 금지"
    null_object:
      - "null 반환 금지 — Optional 또는 Null Object 패턴"
      - "null 파라미터 금지 — @NonNull로 명시 또는 Optional"
    bdd:
      - "외부 동작은 Karate feature로 먼저 작성 후 구현"
      - "작성 순서: Karate feature → 도메인 → UseCase → Repository → Controller (안에서 바깥)"
    test_strategy:
      - "단위 테스트는 JUnit5 + AssertJ, 모킹 최소화 (외부 경계만 mock)"
      - "통합 테스트는 Karate가 겸함 (별도 @SpringBootTest IT 만들지 않음)"

  scale_thresholds:
    file_lines: 200
    function_lines: 20
    function_args: 2
    class_public_methods: 5
    class_instance_vars: 3
    folder_direct_files: 15
    folder_depth: 4
    dockerfile_stages: 5
    compose_services: 8
    workflow_yaml_lines: 200
    migration_per_pr: 1

  python_integration:
    invocation: "ProcessBuilder + stdin/stdout (JSON 문자열)"
    entry_convention: "python/<pipeline>/main.py 가 유일한 진입점"
    input_format: "stdin으로 JSON 1개"
    output_format: "stdout으로 결과 JSON 1개"
    error_protocol:
      - "정상: exit code = 0, stdout = 결과 JSON"
      - "실패: exit code != 0, stderr = JSON {error_code, message}"
    port_location: "사용하는 context의 domain.port (예: VisionProcessor)"
    adapter_location: "같은 context의 infrastructure.vision.*Adapter (ProcessBuilder 호출)"
    runtime: "단일 Docker 이미지 안에 Python + JRE + jar 동거 (multi-stage 빌드)"

  decisions:
    - id: ADR-001
      title: "DDD by Bounded Context (적층 구조, Hexagonal/Clean 아님)"
      rationale: "context 단위로 application/domain/infrastructure를 적층. Port&Adapter 명시적 분리 비용 회피하면서 도메인 격리는 유지."
    - id: ADR-002
      title: "Domain Event 사용 안 함"
      rationale: "현 규모에서 이벤트 디스패치/순서 보장 비용이 application service 직접 조율보다 큼. 필요해지면 transactional outbox로 도입."
    - id: ADR-003
      title: "Bounded Context 간 application service 직접 호출 허용"
      rationale: "ACL/port 분리 비용 회피. 단 domain/infrastructure import는 금지해 결합도 상한."
    - id: ADR-004
      title: "Karate가 통합테스트 겸함, 모킹 최소"
      rationale: "실제 환경에서 명세를 검증. @SpringBootTest IT 중복 방지로 테스트 비용 절감."
    - id: ADR-005
      title: "Python 통합 = ProcessBuilder + JSON stdin/stdout, 단일 Docker 이미지"
      rationale: "별도 서비스 분리 비용 회피. SLAM/CV 파이프라인은 동기 요청-응답 성격이라 IPC 오버헤드 허용 가능."
    - id: ADR-006
      title: "1 PR = 1 마이그레이션, DDL과 백필 분리"
      rationale: "롤백 단위 명확. 두 단계 배포(역호환 코드 → 마이그레이션 → 신규 코드)로 무중단 보장."
    - id: ADR-007
      title: "JPA만 사용 (QueryDSL/JdbcTemplate 미도입)"
      rationale: "현 도메인 쿼리 복잡도가 JpaRepository로 충분. 도입 임계 도달 시 ADR로 별도 결정."
    - id: ADR-008
      title: "Domain에 Spring/JPA 의존 허용 — Pure POJO 격리 포기"
      rationale: "Repository는 Spring Data JPA interface를 domain에 직접 선언, Aggregate Root에 @Entity 직접 부착. 매퍼/구현체 보일러플레이트 제거. 트레이드오프: domain 단위 테스트가 JPA 기동 필요할 수 있음. 현 규모에서 보일러플레이트 비용이 격리 이득보다 크다고 판단."

_anchors:
  base_context_meta: &base_context_meta
    intent: "한 Bounded Context의 모든 계층(application/domain/infrastructure)을 한 폴더에 모음"
    contains: [application, domain, infrastructure]
    forbidden:
      - "다른 context의 domain/infrastructure 직접 import 금지 (application service만 허용)"
      - "context 루트에 클래스 직접 두기 금지 (반드시 하위 application/domain/infrastructure)"
    naming: ["context 폴더명은 lowercase 단수형 (order, mapping, localization)"]
    split_signals:
      - "한 context의 UseCase 수 > 15"
      - "한 context의 Aggregate 수 > 5"
      - "context 폴더 전체 파일 수 > 80"
    growth_response: "context 내부 sub-domain 폴더로 분리 검토. 단 별도 context 신설은 마지막 수단 — 응집도 우선."

_project_struct:
  .github:
    _meta_local:
      intent: "GitHub 표준 폴더 — CI/CD, 템플릿, 코드오너"
      forbidden: ["여기 안에 빌드 산출물 두기 금지"]
      naming: [kebab-case]
      split_signals: ["folder_direct_files > 10"]
      growth_response: "workflows/와 ISSUE_TEMPLATE/로 폴더 분리"
    workflows:
      _meta_local:
        intent: "CI/CD 파이프라인 정의 — 빌드/테스트/배포"
        contains: ["ci.yml", "deploy.yml", "release.yml", "reusable workflows"]
        forbidden:
          - "워크플로 안에 비즈니스 로직 금지"
          - "시크릿 평문 금지 (github-secrets만 사용)"
          - "여러 워크플로에 중복된 job 정의 금지 (reusable workflow로 추출)"
        naming:
          - "kebab-case"
          - "트리거별 파일 분리 (push, pr, release)"
        split_signals:
          - "workflow_yaml_lines > 200"
          - "job 수 > 8"
        growth_response: "reusable workflow + composite action으로 추출"
        exemplar: .github/workflows/ci.yml

  docker:
    _meta_local:
      intent: "컨테이너 이미지 정의 — Spring + Python 단일 이미지 multi-stage 빌드"
      contains: ["Dockerfile"]
      forbidden:
        - "환경별 값 직접 명시 금지 (compose override 사용)"
        - "single-stage 빌드 금지 (Python deps와 JVM 빌드 분리)"
      naming: ["Dockerfile (기본)", "Dockerfile.<purpose> (보조 이미지 있을 때만)"]
      split_signals:
        - "dockerfile_stages > 5"
      growth_response: "stage 분리 + 공통 base 이미지 추출"
      exemplar: docker/Dockerfile

  python:
    _meta_inherited:
      forbidden:
        - "Python 안에 비즈니스 로직 금지 (입출력 JSON 처리와 모델 추론만)"
        - "Spring 코드와 직접 통신 금지 (반드시 stdin/stdout JSON)"
    _meta_local:
      intent: "Spring에서 ProcessBuilder로 호출하는 CV/SLAM 파이프라인 모음"
      contains: ["파이프라인별 sub-folder"]
      forbidden: ["python 루트에 .py 파일 직접 두기 금지"]
      naming: ["snake_case 폴더명", "파이프라인 1개 = 폴더 1개"]
      split_signals: ["파이프라인 수 > 10"]
      growth_response: "용도별 그룹 폴더로 묶기 (예: python/vision/, python/slam/)"
    <pipeline-name>:
      _meta_local:
        intent: "한 파이프라인의 entry script + 의존성 + 보조 모듈"
        contains: ["main.py", "requirements.txt", "보조 .py 모듈"]
        forbidden:
          - "main.py 외 진입점 금지"
          - "다른 파이프라인 폴더 import 금지"
        naming:
          - "entry는 main.py 고정"
          - "보조 모듈은 snake_case"
        split_signals:
          - "file_lines > 200"
          - "보조 모듈 수 > 10"
        growth_response: "공통 모듈은 별도 라이브러리화 검토 (python/<pipeline>/lib/)"
        exemplar: python/rtab_map/main.py

  scripts:
    _meta_local:
      intent: "로컬/CI 보조 스크립트 — 빌드, 시드, 배포 보조"
      contains: ["bash 스크립트"]
      forbidden:
        - "프로덕션 런타임 의존 스크립트 금지"
        - "스크립트 안 비즈니스 로직 금지"
      naming:
        - "kebab-case.sh"
        - "동사로 시작 (build, seed, deploy)"
      split_signals:
        - "스크립트 라인 > 100"
        - "옵션 5개 초과"
      growth_response: "별도 CLI 도구(Gradle task)로 승격"

  src:
    main:
      _meta_local:
        intent: "프로덕션 코드 + 리소스 루트"
        contains: [java, resources]
        forbidden: ["test code 금지"]
        naming: ["package lowercase"]
        split_signals: ["folder_depth > 4"]
        growth_response: "Gradle subproject로 모듈 분리 검토"
      java:
        app:
          _meta_local:
            intent: "Application 진입점과 전역 부트스트랩"
            contains: ["SpringBootApplication class", "GlobalExceptionHandler"]
            forbidden:
              - "비즈니스 로직 금지"
              - "feature/context 코드 금지"
            depends_on: [config]
            naming: ["PascalCase", "suffix Application"]
            split_signals: ["folder_direct_files > 3"]
            growth_response: "ExceptionHandler를 advice 폴더로 분리"
        config:
          _meta_local:
            intent: "Spring Bean / Filter / Interceptor 설정"
            contains: ["@Configuration class", "Filter", "Interceptor"]
            forbidden:
              - "도메인 로직 금지"
              - "context별 Bean 한 파일에 모으기 금지"
            naming: ["PascalCase", "suffix Config"]
            split_signals: ["folder_direct_files > 10"]
            growth_response: "관심사별 폴더로 분리 (security, web, persistence)"
        shared:
          _meta_local:
            intent: "여러 context가 공통 사용하는 무상태 유틸·타입·Null Object"
            contains: ["공통 util", "공통 exception base", "Null Object 구현체"]
            forbidden:
              - "특정 context import 금지"
              - "비즈니스 규칙 금지"
            naming: ["PascalCase", "util은 명사형"]
            split_signals: ["folder_direct_files > 15"]
            growth_response: "도메인 색이 생긴 코드는 해당 context로 회수"
        contexts:
          _meta_inherited:
            forbidden:
              - "다른 context의 domain/infrastructure 직접 import 금지 (application service만 허용)"
              - "Aggregate 경계 넘는 트랜잭션 금지"
          _meta_local:
            intent: "Bounded Context 모음 — 각 context는 application/domain/infrastructure 적층"
            contains: ["context 단위 폴더"]
            forbidden: ["contexts 루트에 클래스 직접 두기 금지"]
            naming: ["lowercase 단수형 (order, mapping, localization)"]
            split_signals: ["context 수 > 10"]
            growth_response: "도메인 그룹화 검토. 새 context 신설은 마지막 수단 — 응집도 우선."
          <context-name>:
            _meta_local:
              <<: *base_context_meta
              exemplar: src/main/java/com/app/contexts/order/
            application:
              _meta_local:
                intent: "UseCase 계층 — 트랜잭션 경계, 도메인 객체 조율"
                contains:
                  - "UseCase 클래스 (1 UseCase = 1 클래스)"
                  - "Command/Query/Result record"
                  - "Port out 인터페이스 (외부 시스템용)"
                forbidden:
                  - "비즈니스 규칙 직접 구현 금지 (도메인 객체에 위임)"
                  - "Controller 직접 의존 금지"
                  - "infrastructure 직접 import 금지 (port 통해서만)"
                depends_on: [domain, shared]
                naming:
                  - "UseCase suffix"
                  - "@Transactional은 UseCase 메서드에"
                  - "입력은 Command record, 조회는 Query record, 결과는 Result record"
                split_signals:
                  - "UseCase 클래스 수 > 15"
                  - "한 UseCase가 다른 UseCase 호출 (UseCase 간 의존)"
                growth_response: "UseCase 묶음을 sub-folder로 분리 (예: application/order/, application/payment/)"
                exemplar: src/main/java/com/app/contexts/order/application/CreateOrderUseCase.java
            domain:
              _meta_local:
                intent: "도메인 모델 — 비즈니스 규칙의 본체 (Spring/JPA 의존 허용, ADR-008)"
                contains:
                  - "Aggregate Root / Entity / Value Object (JPA @Entity 직접 부착)"
                  - "Repository 인터페이스 (Spring Data JpaRepository 직접 extends, 구현체 없음)"
                  - "외부 시스템 port (예: VisionProcessor)"
                  - "도메인 예외 (extends DomainException)"
                forbidden:
                  - "Lombok @Getter/@Setter/@Data 금지"
                  - "null 반환/전달 금지 (Optional 또는 Null Object)"
                  - "Aggregate 간 객체 참조 금지 (ID로만)"
                  - "Aggregate 경계 넘는 트랜잭션 금지"
                  - "비즈니스 규칙을 application으로 누출 금지 (Tell Don't Ask)"
                depends_on: [shared]
                naming:
                  - "PascalCase"
                  - "Aggregate Root는 도메인 명사 그대로 (Order, Building) — 별도 JpaEntity 분리 없음"
                  - "VO는 측정 단위 또는 의미 단위 명사 (Money, Email)"
                  - "Port 인터페이스는 동작 명사 (VisionProcessor)"
                  - "Repository는 <Aggregate>Repository (extends JpaRepository)"
                  - "예외는 suffix Exception"
                split_signals:
                  - "Aggregate 수 > 5"
                  - "class_instance_vars > 3"
                  - "class_public_methods > 5"
                growth_response: "Aggregate 단위 sub-folder로 분리 (domain/order/, domain/lineitem/)"
                exemplar: src/main/java/com/app/contexts/order/domain/Order.java
            infrastructure:
              _meta_local:
                intent: "외부 시스템 어댑터 — Web 어댑터, ProcessBuilder, 외부 API 클라이언트 (Repository 구현은 domain에 직접)"
                contains:
                  - "Controller + Request/Response DTO (web 어댑터)"
                  - "ProcessBuilder vision adapter (Python 호출)"
                  - "외부 API 클라이언트 (필요시)"
                forbidden:
                  - "domain port 없이 외부 호출 금지 (ProcessBuilder는 반드시 port 통해)"
                  - "비즈니스 로직 금지 (변환·매핑만)"
                  - "Controller에서 Repository 직접 호출 금지 (UseCase 경유)"
                  - "Repository 구현체 작성 금지 (Spring Data JPA가 domain interface 기반 자동 생성)"
                depends_on: [domain, application]
                naming:
                  - "Adapter suffix (외부 시스템 어댑터)"
                  - "Controller suffix (Web 어댑터)"
                  - "Client suffix (외부 API 클라이언트)"
                split_signals:
                  - "adapter 수 > 10"
                  - "folder_direct_files > 15"
                growth_response: "외부 시스템별 sub-folder 분리 (infrastructure/web/, infrastructure/vision/, infrastructure/external/)"
                exemplar: src/main/java/com/app/contexts/order/infrastructure/web/OrderController.java
      resources:
        _meta_local:
          intent: "런타임 리소스 — 설정 파일, 마이그레이션"
          contains: ["application*.yml", "db/migration", "karate test 리소스는 src/test/resources"]
          forbidden: ["코드 파일 (.java) 금지"]
          naming: ["snake_case 또는 kebab-case"]
          split_signals: ["folder_direct_files > 15"]
          growth_response: "하위 카테고리 폴더 분리"
        db:
          _meta_local:
            intent: "Flyway 마이그레이션 루트 (다른 DB 도구 도입 시에도 여기로 모음)"
            contains: [migration]
            forbidden: ["수동 SQL 실행 스크립트 금지 — 항상 Flyway 통해"]
            naming: ["하위에 migration 폴더만 둠 (현재 Flyway 단일)"]
            split_signals: ["다른 DB 도구 추가 (Liquibase 등)"]
            growth_response: "도구별 sub-folder 분리 (db/flyway, db/seed 등)"
          migration:
            _meta_local:
              intent: "Flyway 버전 마이그레이션 파일"
              contains: ["V<seq>__<description>.sql"]
              forbidden:
                - "이미 배포된 마이그레이션 수정 금지"
                - "1 PR에 2개 이상 마이그레이션 금지"
                - "DDL과 백필을 한 파일에 섞기 금지"
              naming:
                - "V<seq>__<snake_case>.sql"
                - "DDL은 V<seq>__add_<col>_to_<table>.sql / V<seq>__create_<table>.sql"
                - "백필은 V<seq>__backfill_<table>_<col>.sql"
                - "NOT NULL 강제는 별도 파일 V<seq>__enforce_not_null_<table>_<col>.sql"
              split_signals: ["migration_per_pr 위반"]
              growth_response: "PR 분리 (DDL PR / 백필 PR / 제약 PR 각각)"
    test:
      _meta_inherited:
        naming: ["테스트 클래스는 suffix Test"]
      _meta_local:
        intent: "테스트 코드 루트 — 단위 테스트(JUnit5) + Karate feature"
        contains: [java, resources]
        forbidden: ["프로덕션 코드 금지"]
        split_signals: ["folder_depth > 5"]
        growth_response: "테스트 종류별 sourceSet 분리 검토"
      java:
        _meta_local:
          intent: "단위 테스트 + Karate runner"
          contains:
            - "JUnit5 + AssertJ 단위 테스트 (production 코드 미러 패키지)"
            - "Karate runner 클래스 (@KarateTest)"
          forbidden:
            - "Mockito 광범위 사용 금지 (외부 경계만)"
            - "@SpringBootTest 통합테스트 신규 작성 금지 (Karate가 대체)"
          naming:
            - "단위 테스트는 <TargetClass>Test"
            - "Karate runner는 <Context>KarateTest"
          split_signals: ["folder_direct_files > 20"]
          growth_response: "프로덕션 패키지 구조 그대로 미러"
      resources:
        _meta_local:
          intent: "Karate 설정과 feature 파일"
          contains: ["karate-config.js", "karate/<context>/<usecase>.feature"]
          forbidden: ["코드 파일 금지"]
          naming: ["karate-config.js 고정", "feature 파일은 snake_case.feature"]
          split_signals: ["folder_direct_files > 15"]
          growth_response: "하위 카테고리 폴더로 분리 (fixtures, helpers 등)"
        karate:
          _meta_local:
            intent: "Karate feature 파일 — 도메인 구조 미러"
            contains: ["<context>/<usecase>.feature"]
            forbidden:
              - "외부 fixture 셋업 로직 금지 (feature 본문에서 API로 상태 세팅)"
              - "context 루트에 .feature 직접 두기 금지"
            naming:
              - "context 폴더명은 src/main 도메인 context명과 동일"
              - "feature 파일은 <usecase>.feature (UseCase명 lowercase snake)"
            split_signals: ["한 context feature 수 > 20"]
            growth_response: "context 안에서 sub-usecase 폴더로 분리"
            exemplar: src/test/resources/karate/order/create_order.feature
```
