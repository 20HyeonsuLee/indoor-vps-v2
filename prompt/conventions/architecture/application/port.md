## rule
- application/port는 **순수 인프라 외부 시스템**에 대한 outbound 인터페이스를 둔다. (메일·SMS·파일 저장소·시스템 시계 등)
- 도메인 종속 외부 시스템(`VisionProcessor`, `MapStorage` 같이 도메인 어휘로 말하는 port)은 `domain/<sub>/port/`에 둔다. application/port에 두지 않는다.
- 인터페이스만 선언한다. 구현은 `infrastructure/<system>/*Adapter`.
- 명명은 동작/역할 명사. (`MailSender`, `FileStorage`, `Clock`, `IdGenerator`)
- UseCase에서 생성자 주입으로 사용한다.
- port 메서드는 도메인 친화 타입(VO·도메인 record)으로 입출력한다. 외부 SDK 타입 노출 금지.
- 한 port = 한 책임. 메서드 늘어나면 port 분리.

## forbidden
- 도메인 종속 port를 application/port에 두기 (domain/port로)
- 구현 클래스 작성 (infrastructure 어댑터가 구현)
- 외부 SDK·라이브러리 타입을 시그니처에 노출
- 비즈니스 규칙 내장 (port는 호출 계약만)
- 한 port에 무관한 책임 묶기 (`UtilPort` 류 금지)
- application의 다른 모듈(UseCase·다른 port) 참조
- 다른 Bounded Context의 application/domain import
- null 반환 (Optional)

## 분기 기준 (domain/port vs application/port)
- "이 port가 사용하는 어휘가 도메인 어휘인가?" → 예 = domain/port, 아니오 = application/port
- 예 (domain/port): `VisionProcessor.extractFeatures(ScanFrame)` — ScanFrame은 도메인 개념
- 예 (application/port): `MailSender.send(EmailAddress, MailBody)` — 도메인과 무관한 일반 인프라
