## rule
- 기술 문서 (매뉴얼·spec·API 문서·디자인 문서·SRS).
- 독자: 개발자·운영자·기술 stakeholder.
- 목적별 구조:
  - **how-to**: step-by-step (순서 list)
  - **reference**: 알파벳/카테고리 정렬
  - **explanation**: 개념·원리
  - **tutorial**: 학습 (목적 → 실습 → 결과)
  (Diátaxis framework 참조)

## rule (정확성)
- 모든 명령·코드·예제 실제 동작 확인.
- 버전 명시 (`Java 21`, `Spring Boot 3.4`).
- 변경 시 문서 갱신 (코드 변경에 따라).
- screenshot은 최신 UI (구버전 X).
- 출처·근거 명시 (API docs·spec).

## rule (코드)
- 코드 블록은 mono font + syntax highlight.
- 코드 예제는 최소 reproducible (불필요 import·setup 생략).
- output·결과도 같이 (입력 + 기대 출력).
- inline code는 backtick 또는 Word `코드` 스타일.

## rule (다이어그램)
- architecture·flow·sequence diagram (`content/figure.md`).
- 도구: draw.io, Lucid, Mermaid, PlantUML.
- 코드와 같이 commit (text 기반 다이어그램은 git diff 가능).

## rule (예제·non-example)
- ✅ 좋은 예 + ❌ 나쁜 예 대비 (학습 효과).
- 안티패턴 명시.
- edge case·error 처리 포함.

## rule (terminology)
- 용어 glossary 별도 섹션.
- 같은 개념 = 같은 용어 (synonym 금지).
- 도메인별 용어집 link.

## forbidden
- 검증 없는 명령·코드 (오류 risk)
- 버전 누락 (호환 추적 불가)
- 옛 screenshot (사용자 혼란)
- 코드 예제 partial (실행 안 됨)
- inline code 일반 폰트
- 다이어그램 없이 architecture 설명만 (시각 부족)
- ❌ 예 없이 권장만 (대비 부족)
- 용어 synonym (Floor / Level / Story 혼용)
- TODO 누적 (해결 또는 issue)
- 사용자 시나리오 없이 reference 만 (실용성 ↓)
- 한 문서가 how-to + reference + explanation 다 (분리)
