## rule
- Value Object는 식별자가 없는 불변 값이다. 원시값과 문자열을 의미 단위로 포장한다.
- 형태는 `record` 우선. JPA에 부착해야 하면 `@Embeddable` class.
- 모든 필드 final. setter 없음. mutation 필요 시 새 인스턴스 반환(`with*` 메서드).
- 생성 검증은 생성자 또는 compact constructor에서 invariant 체크. 위반 시 도메인 예외 throw.
- equals/hashCode는 값 기반(record 자동, `@Embeddable`은 직접 구현).
- 명명은 의미 단위 명사. (`Money`, `Email`, `FloorId`, `Polygon`, `Coordinate`)
- 분류:
  - ID VO: Aggregate 식별자 (`BuildingId`, `FloorId`)
  - 측정 VO: 단위가 있는 값 (`Money`, `Distance`, `Area`)
  - 식별/제약 VO: 형식 제약 있는 문자열/숫자 (`Email`, `PhoneNumber`)
- VO에도 행위 부여한다. `money.add(other)`, `polygon.contains(point)` 등.

## forbidden
- mutable 필드
- setter 메서드
- null 필드 (생성자에서 차단)
- getter만 있는 단순 포장 (행위 없는 anemic VO)
- 원시 long/String을 도메인 경계에서 직접 노출 (VO로 감쌈)
- VO에 영속화 상태(JPA `@Id` 등) 부여 — 식별자 VO는 Entity의 `@EmbeddedId`로 사용
- 다른 Aggregate Root 참조
- 외부 라이브러리 타입을 VO 내부에 노출 (도메인 친화 타입만)
