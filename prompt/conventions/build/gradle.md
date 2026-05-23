## rule
- DSL은 **Kotlin (`build.gradle.kts`)** 한 가지만. Groovy `.gradle` 금지.
- `build.gradle.kts` 구조 순서:
  1. plugins
  2. group/version/java target
  3. repositories
  4. dependencies (version catalog 참조)
  5. tasks 설정
  6. 커스텀 task 정의
- 멀티 module 도입 시 `settings.gradle.kts`에서 module 선언. 현재 단일 module.
- Java toolchain 명시:
  ```kotlin
  java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
  ```
- gradle wrapper(`gradlew`/`gradlew.bat`/`gradle/wrapper/`)는 commit 필수. 모든 환경에서 같은 gradle 버전.
- gradle build cache 활성: `org.gradle.caching=true` (gradle.properties).
- parallel 활성: `org.gradle.parallel=true`.
- configuration cache 활성 검토: `org.gradle.configuration-cache=true` (Spring 플러그인 호환 확인 후).
- 커스텀 task는 `group` + `description` 명시 (`./gradlew tasks`에 노출).
- task 명명은 동사+명사 (`migrateDb`, `runKarate`). 표준 task(`build`, `test`, `clean`)는 재정의 X.
- `bootRun` 시 active profile은 `-Dspring.profiles.active=local` 또는 환경변수.
- spotless (포맷) / flyway (migration) / spring-boot 플러그인은 version catalog로 관리.
- test report는 `build/reports/`에 둠. CI에서 artifact upload.
- `--no-daemon` 은 CI에서만. 로컬은 daemon 활성.

## forbidden
- Groovy DSL (`build.gradle`) 신규 도입
- gradle wrapper 누락 (환경 간 버전 drift)
- `repositories { mavenLocal() }` 의존 (재현성 깨짐)
- 표준 task(`build`, `test`) override
- `allprojects { ... }` / `subprojects { ... }` 남용 (멀티 module 도입 시 ADR)
- 직접 의존성 version hardcode (version catalog 사용 — dependencies.md)
- `tasks.withType<Test> { ... }` 안 무관한 책임 묶기
- `--offline` / `--refresh-dependencies` 일상 사용 (의존성 lock과 충돌)
- `gradle.properties`에 비밀값
- 인증된 private repo 사용 시 credentials를 build script에 hardcode
- system-wide gradle 사용 (wrapper 필수)
