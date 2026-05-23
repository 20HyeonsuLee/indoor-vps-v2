## 목적
Word 문서 작업 entry. 요청 → plan → draft → review → finalize lifecycle.

## 5 단계
1. **요청 접수**
   - 요청자·목적·독자·범위·종류·deadline·output 형식 확인
2. **plan** (`workflows/plan`)
   - 목적·독자·범위·문서 종류 결정
   - 구조(heading 계층) 초안
3. **draft** (`workflows/draft`)
   - template에서 시작
   - 본문 작성 (단락·list·표·그림)
   - 참조·인용 관리
4. **review** (`workflows/review`)
   - self-review (24h 후)
   - peer review 1+
   - stakeholder review (필요 시)
   - proofreader (외부 출판)
5. **finalize** (`workflows/finalize`)
   - 변경 추적 적용·댓글 정리
   - field update
   - 메타·hidden 검사
   - export (docx + PDF)
   - 공유·archive

## 분기 (문서 종류별)
| 종류 | 특이사항 |
|---|---|
| memo | 1~2 page · 빠른 draft → 짧은 review |
| 보고서 (executive) | 요약 우선 · 5~10 page + 부록 |
| 보고서 (technical) | 깊은 detail · 다이어그램 · 15~50 page |
| 제안서 | Problem→Solution→Cost→Team · CTA |
| 매뉴얼 | step-by-step · troubleshooting · index |
| 학술 논문 | IMRaD · 인용·재현성 · IRB |
| 계약서 | 법무 review 필수 |
| 공문 | 형식 엄격 · 발신처·수신처 |

## 항상 적용
- 목적·독자·범위 확정 없이 작성 시작 X
- template 사용 (직접 서식 X)
- Word style 통일
- 변경 추적·댓글로 review
- field update 전 final
- a11y (`accessibility/a11y`)
- brand 일관 (`branding/brand`)
- 용어·표기 consistency (`quality/consistency`)
- 6 axis review (`quality/review`)

## 금지
- plan 없이 draft
- review 0회 외부 공유
- final 직전 큰 변경
- 변경 추적·댓글 잔존 공유
- sensitive 정보 마스킹 누락
- single backup
