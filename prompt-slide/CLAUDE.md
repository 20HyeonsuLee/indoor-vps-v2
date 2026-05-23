# AI Agent 인덱스 루트 (Slide)

목적: **MS PowerPoint 슬라이드 deck 작성**을 위한 SSOT.

- **`index.yml`**: 작업 영역별 `_conventions`/`_workflows` 매핑.
- **`CLAUDE.md`** (이 파일): 에이전트 유형별 진입 룰 + 동적 로드 매트릭스.

로드 순서: 사용자 요청 → `prompt-slide/CLAUDE.md` → `prompt-slide/index.yml` → 작업 영역별 `_conventions`/`_workflows` 동적.

---

## 에이전트 유형

| 유형 | 단계 | 책임 | 산출물 | 핵심 참조 |
|---|---|---|---|---|
| plan | 1 | 목적·청중·메시지·storyboard | plan 1page | `structure/*`, `content/message`, `workflows/plan` |
| draft | 2 | 슬라이드 작성 | `.pptx` draft | `structure/slide`, `content/*`, `design/*`, `office/powerpoint`, `workflows/draft` |
| review | 3 | 6 axis 검토 | review 코멘트 | `quality/review`, `workflows/review` |
| finalize | 4 | 최종화·export·공유 | final `.pptx`+PDF | `office/export`, `workflows/finalize` |
| rehearse | 5 | dry-run·timing·Q&A | timing 보고 | `workflows/rehearse` |

**6 axis review**: message · flow · 시각 · 디자인 · a11y · 품질

---

## 동적 로드 매트릭스 (작업 영역 → 로드)

| 작업 영역 | 로드 |
|---|---|
| 전체 deck 설계 | `structure/deck`, `structure/flow`, `workflows/plan` |
| 단일 슬라이드 작성 | `structure/slide`, `content/message`, `content/headline`, `content/bullet`, `design/*` |
| 차트·표·데이터 슬라이드 | `content/data`, `design/color`, `design/typography` |
| 시각 자산 (이미지·아이콘·스크린샷) | `content/visual`, `design/color` |
| 디자인 시스템·master | `design/template`, `design/typography`, `design/color`, `design/layout`, `branding/brand` |
| 발표 노트·script | `writing/writing` |
| PowerPoint 기능 (애니메이션·transition·master) | `office/powerpoint` |
| 출력 (pptx·PDF·인쇄) | `office/export` |
| 접근성 점검 | `accessibility/a11y` |
| 브랜드 일관성 | `branding/brand` |
| 검토 | `quality/review`, `workflows/review` |
| **모든 작업 공통** | `writing/writing`, `accessibility/a11y`, `branding/brand` |

---

## 작업 종류 → workflow

**모든 작업의 entry는 `workflows/task-flow`**.

| 작업 | 분기 workflow |
|---|---|
| 새 deck 작성 | `plan` → `draft` → `review` → `finalize` → `rehearse` |
| 기존 deck 수정 | `draft` (부분) → `review` → `finalize` |
| 검토만 | `review` |
| 발표 준비 | `rehearse` |
| 디자인·template 신설 | `design/template` 참조 + 디자이너 합의 |

## deck 종류별 흐름

| 종류 | 특이사항 |
|---|---|
| executive briefing | 5초 rule 엄격 · pyramid top-down · backup 풍부 |
| technical deep-dive | bottom-up 가능 · 차트·diagram 중심 · appendix |
| sales pitch | Problem→Solution→Proof→Ask · CTA 명확 |
| training/tutorial | step-by-step · 핸드아웃 · 연습 |
| conference talk | story arc · signature slide · timing 엄격 |
| status report | 진행·issue·next · 간결 |

---

## 항상 적용

- **목적·청중·메시지** 확정 없이 작성 시작 X (`workflows/plan` 강제)
- 1 슬라이드 = 1 message (`structure/slide`, `content/message`)
- headline = 결론 (`content/headline`)
- master·template 사용 (직접 디자인 X)
- 6x6 rule (`content/bullet`)
- a11y (`accessibility/a11y`)
- brand 일관 (`branding/brand`)
- 한국어/영어 톤 일관 (`writing/writing`)
- review 6 axis (`quality/review`)
- 폰트 embed + PDF 함께 export

---

## 인덱스 갱신 책임

- 새 카테고리·workflow 추가 시 `index.yml` 갱신
- 새 동적 로드 패턴이면 본 파일 매트릭스 갱신

---

## 호출 흐름

```
요청 → plan(목적·청중·big idea·storyboard)
  → draft(master + slide 작성)
  → review(self → peer → stakeholder)
  → finalize(export + 공유 + backup)
  → rehearse(timing + Q&A)
  → 발표
```
