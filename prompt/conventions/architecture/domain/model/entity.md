## rule
- Aggregate Root와 Entity는 JPA `@Entity`를 직접 부착한다. 별도 JpaEntity/Mapper 분리 없음.
- Aggregate Root는 도메인 명사 그대로 명명한다. (`Building`, `Floor`, `Scan`)
- Aggregate 간 참조는 ID(VO)로만 한다. (`FloorId floorId` — `Floor floor` 객체 참조 금지)
- 식별자는 VO 타입으로 감싼다. (`@EmbeddedId BuildingId id` — primitive long 직접 노출 X)
- 생성은 static factory(`Floor.create(...)`). public 생성자 금지. JPA용 protected/package-private 기본 생성자만 허용.
- 상태 변경은 동사 메서드로. (`floor.bindPolygon(polygon)` — `floor.setPolygon(...)` 금지)
- equals/hashCode는 identity 기반(ID 비교). JPA proxy 호환 위해 `instanceof` + getClass 주의.
- 인스턴스 변수 ≤ 3, public 메서드 ≤ 5 권장. 초과 시 VO 추출 또는 Aggregate 분리 검토.
- Aggregate 경계 = 트랜잭션 경계. 한 트랜잭션 = 한 Aggregate 변경.
- 도메인 invariant(불변식) 위반 시 도메인 예외 throw.

## forbidden
- `@Setter`/`@Getter`/`@Data` (Tell Don't Ask 위반)
- public 생성자 (static factory 사용)
- Aggregate 간 객체 참조 (ID로만)
- null 필드 (Optional 또는 Null Object)
- setter 메서드 자체
- 다른 Aggregate Root fetch 관계(`@OneToMany`로 다른 Aggregate 묶기 금지)
- 도메인 규칙 application으로 누출
- 비즈니스 로직 utility static 메서드 노출
- Domain Event 발행
- primitive long/String을 식별자로 직접 노출
