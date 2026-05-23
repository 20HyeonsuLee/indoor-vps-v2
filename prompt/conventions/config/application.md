## rule
- Spring 설정은 yml 한 종류만 사용.
- 기본값은 `src/main/resources/application.yml`에 모두 둔다.
- 환경 오버라이드는 `application-<profile>.yml` (`application-local.yml`, `application-prod.yml`).
- 환경 파일에는 **차분만 적는다**. 기본 파일과 같은 값 적기 금지 (drift 차단).
- profile은 `local`, `prod` 2개 운영.
- 활성 profile은 `SPRING_PROFILES_ACTIVE` 환경변수로 결정. 코드/yml에 하드코딩 X.
- 비밀값(DB password, API key 등)은 yml에 평문 X. 환경변수 placeholder (`${DB_PASSWORD}`)로만 참조.
- 같은 키 중복 정의 시 base보다 override가 이김. 의도 외 override 검출은 PR 리뷰 항목.
- application.yml 키 구조는 도메인/관심사별 그룹화 (`spring.datasource`, `app.vision`, `app.security`).
- 새 키 추가 시 base에 default 또는 placeholder 명시 후 override.

## forbidden
- 환경 파일에 base와 같은 값 적기 (config 중복 금지 — CLAUDE.md forbidden)
- yml에 평문 비밀값
- profile-specific 키를 base에 두기 (반대로 base 공통 키를 override에 흩뿌리기도 금지)
- `application.properties` 사용 (yml 단일화)
- yml 안 SpEL 남발 (간단 조건만)
- profile 활성화를 코드 안 `@Profile` 외에 분기 (config 외에 분기 들어가면 추적 어려움)
- prod 비밀값을 local yml에 적기
