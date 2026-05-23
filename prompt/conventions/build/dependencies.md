## rule
- 의존성 version은 **Version Catalog** (`gradle/libs.versions.toml`)에서 SSOT 관리.
- `build.gradle.kts`에서는 `libs.<alias>` 또는 `libs.bundles.<name>`만 참조. 직접 version string 사용 금지.
- Spring 관련은 **BOM(platform)** 사용:
  ```kotlin
  implementation(platform(libs.spring.boot.bom))
  implementation("org.springframework.boot:spring-boot-starter-web")  // version은 BOM
  ```
- Version Catalog 구조:
  - `[versions]`: 공유 version (`kotlin = "1.9.22"`, `springBoot = "3.4.0"`)
  - `[libraries]`: 단일 의존성 (`spring-boot-starter-web = { module = "...", version.ref = "springBoot" }`)
  - `[bundles]`: 그룹 (`spring-boot = ["spring-boot-starter-web", "spring-boot-starter-actuator"]`)
  - `[plugins]`: gradle plugin (`spring-boot = { id = "org.springframework.boot", version.ref = "springBoot" }`)
- dependency configuration 의미:
  - `implementation`: 런타임 + 컴파일, 다른 모듈에 전파 안 함 (기본)
  - `api`: 컴파일 + 전파 (lib 모듈에서만)
  - `runtimeOnly`: 런타임만 (JDBC driver 등)
  - `compileOnly`: 컴파일만 (Lombok annotation processor 등)
  - `testImplementation`: 테스트 코드 한정
- `compileOnly` + `annotationProcessor` 짝 — Lombok 같은 코드 생성 도구.
- 동일 lib의 여러 version 의존 시 `resolutionStrategy { force(...) }` 명시. 묵시적 resolve 의존 X.
- **dependency locking** 활성화: `dependencyLocking { lockAllConfigurations() }` + `gradle.lockfile` commit. major bump 시 `./gradlew dependencies --write-locks`.
- 외부 lib 추가 시 **PR description에 이유·라이선스·대안 검토** 명시.
- 사용 안 하는 의존성은 `gradle dependencyUpdates` / `dependencyAnalysis` plugin으로 주기 점검.
- 보안 스캔: `dependency-check` 또는 GitHub Dependabot 알림 활성.
- snapshot version(`-SNAPSHOT`) 의존성 금지 (prod 빌드 재현성 깨짐).
- private repo는 `repositories { maven { url = uri("...") credentials { ... } } }`. credentials는 환경변수.

## forbidden
- version string을 `build.gradle.kts`에 직접 하드코딩 (Version Catalog 사용)
- BOM 없이 Spring 개별 lib version 지정
- `dependency-locking` 미활성으로 의존성 drift 허용
- `snapshot`/`-SNAPSHOT` version
- `force` resolution 없는 묵시적 conflict resolve 의존
- `compile` configuration (deprecated — `implementation` 사용)
- 외부 lib 추가를 PR description 없이 (이유·라이선스 누락)
- `mavenLocal()` 의존
- private credentials를 `build.gradle.kts` 또는 `gradle.properties`에 평문
- 사용 안 하는 의존성 누적 (정기 정리)
- 동일 lib을 multi-version 의존성 트리에 silent 허용
- 라이선스 검토 없이 GPL/AGPL 의존성 추가
