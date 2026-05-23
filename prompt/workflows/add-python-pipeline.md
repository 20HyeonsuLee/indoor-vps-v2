## 목적
Spring ↔ Python ProcessBuilder JSON 통합으로 새 CV/SLAM/ML 파이프라인을 추가하는 절차. (ADR-005, ADR-009)

## 절차
1. **port 정의 (Java 쪽 — domain)**
   - 도메인 종속이면 `domain/<sub>/port/<Name>Processor.java`
   - 순수 인프라면 `application/port/<Name>Adapter.java`
   - 시그니처는 도메인 친화 타입 (VO·record). 외부 SDK 타입 노출 X
   - convention: `architecture/domain/repository.md`(domain port 부분), `architecture/application/port.md`
2. **Python pipeline 폴더 생성**
   ```
   python/<pipeline_name>/
     pyproject.toml         # uv + dependency 명시
     uv.lock
     main.py                # entry (또는 [project.scripts] entry-point)
     <module>.py            # 보조 모듈
   ```
   - convention: `python/uv.md`
3. **Python entry 작성**
   - stdin JSON 1개 read → 처리 → stdout JSON 1개 write
   - 에러는 stderr JSON + exit code (`python/protocol.md`)
   - 비즈니스 로직 X (변환·모델 추론만)
4. **JSON 스키마 명시**
   - Python: `@dataclass` 또는 `TypedDict`
   - Java: record `*Request`/`*Response`
   - 양쪽 동기화 (테스트 fixture로 검증)
5. **infrastructure adapter 작성 (Java 쪽)**
   - `infrastructure/vision/<Name>Adapter.java`
   - port 구현. ProcessBuilder + stdin/stdout JSON 호출
   - timeout 명시
   - convention: `architecture/infrastructure/external.md` (또는 infrastructure/vision.md 있다면)
6. **에러 매핑**
   - exit code 1-N → 도메인 예외로 매핑
   - stderr JSON parse → error_code별 분기
7. **Dockerfile / base image**
   - 무거운 deps(PyTorch·CV) → `Dockerfile.base` 이동 (`docker/base.md`)
   - 가벼운 deps만 메인 Dockerfile에서 pip install (cache mount)
   - convention: `docker/dockerfile.md`, `docker/clean.md`, `docker/design.md`
8. **UseCase에서 port 호출**
   - convention: `architecture/application/service.md`
9. **Karate 시나리오**
   - 외부 동작 검증. 단 PyTorch 모델 무거우면 dev profile에서 stub 또는 시나리오 tag (`@vision`) 분리
   - convention: `framework/karate.md`
10. **단위 테스트**
    - port mock으로 UseCase 테스트
    - convention: `framework/junit.md`
11. **ADR 작성 (필요 시)**
    - 새 알고리즘/모델 채택은 ADR 권장 (PoC 결과 인용 — `workflows/poc.md`)
12. **prompt/index.yml 갱신**
    - python/<pipeline>/ 경로 + _conventions 매핑
13. **observability**
    - traceId env로 전파 (`TRACE_ID`)
    - Python 로그(stderr)에 같은 traceId
    - convention: `observability/observability.md`

## 의존성 정책
- Python deps는 `pyproject.toml`만 (requirements.txt X)
- 무거운 deps는 base image로 (`docker/base.md`)
- system pip 직접 install 금지
- convention: `python/uv.md`

## 금지
- HTTP/gRPC/socket 다른 IPC 도입 (ADR 필요)
- Python 안 비즈니스 로직 (CLAUDE.md forbidden)
- stdout에 결과 외 텍스트 (`python/protocol.md`)
- entry path hardcode (Java config로 주입)
- 무한 timeout
- 외부 SDK 타입을 domain/application으로 노출
- 큰 binary를 JSON inline base64 (파일 경로 또는 named pipe)
- venv 활성화 의존 (uv run 사용)
- PyTorch/LightGlue 같은 무거운 deps를 매 빌드 pip install (base image로)
