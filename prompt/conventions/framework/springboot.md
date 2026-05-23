## rule
- DI는 **생성자 주입만** 사용한다. `@Autowired` 필드 주입·setter 주입 금지. 생성자 1개면 `@Autowired` 생략 가능.
- 생성자 주입은 `final` 필드 + `@RequiredArgsConstructor` 또는 명시 생성자.
- Bean 등록 우선순위:
  - **`@Component`/`@Service`/`@Repository`/`@Controller`**: 우리 코드 (의미 단위 어노테이션)
  - **`@Configuration` + `@Bean`**: 외부 라이브러리 객체 등록 (예: RestClient, ObjectMapper)
- `@Configuration` 클래스는 `app/config/` 또는 관심사별 sub-folder. 도메인 로직 금지.
- 외부 설정값 주입은 `@ConfigurationProperties(prefix = "app.<group>")` 사용. `@Value` 산발 사용 금지.
- profile 활성은 `SPRING_PROFILES_ACTIVE` 환경변수. 코드에 `@Profile("local")` 사용은 최소화 (config 분리 우선).
- Actuator endpoint: `/actuator/health`(healthcheck), `/actuator/info`(version). 그 외는 internal 또는 인증.
- lifecycle 콜백은 `@PostConstruct`/`@PreDestroy` 또는 `ApplicationRunner`. static initializer 금지.
- `@Transactional`은 UseCase 메서드에만 (architecture/application/service.md 참조).
- Global exception 처리는 `@RestControllerAdvice` (architecture/ui/exception-handler.md 참조).
- Spring Bean 이름은 클래스 명을 lowercase 시작 (`CreateBuildingUseCase` → `createBuildingUseCase`). 명시 이름 부여는 충돌 시에만.

## forbidden
- `@Autowired` 필드 주입 / setter 주입
- `new` 키워드로 Bean 직접 생성 (테스트 외)
- `@Value` 산발 사용 (`@ConfigurationProperties`로)
- 도메인 로직을 `@Configuration` 클래스에 두기
- 도메인 객체에 `@Component` 부착 (도메인은 Spring lifecycle 외)
- static 필드/메서드로 Spring Bean 상태 보유
- `@Profile` 분기로 비즈니스 로직 갈래 (config 분리 우선)
- Actuator endpoint를 인증 없이 외부 노출 (health/info 외)
- `@SpringBootTest` 신규 통합 테스트 (Karate가 대체 — CLAUDE.md `_project_meta`)
- 순환 의존성 (`@Lazy`로 우회 X — 의존 그래프 재설계)
- ApplicationContext를 코드에서 직접 lookup (ServiceLocator 안티패턴)
