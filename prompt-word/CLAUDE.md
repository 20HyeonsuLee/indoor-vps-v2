# AI Agent 인덱스 루트 (Word 문서)

목적: **MS Word 문서 작성** SSOT. (보고서·제안서·매뉴얼·메모·학술 논문·계약서·공문)

- **`index.yml`**: 작업 영역·문서 종류별 `_conventions`/`_workflows` 매핑.
- **`CLAUDE.md`** (이 파일): 에이전트 유형별 진입 룰 + 동적 로드 매트릭스.

로드 순서: 사용자 요청 → `prompt-word/CLAUDE.md` → `prompt-word/index.yml` → 작업 영역별 `_conventions`/`_workflows` 동적.

---

## 에이전트 유형

| 유형 | 단계 | 책임 | 산출물 | 핵심 참조 |
|---|---|---|---|---|
| plan | 1 | 목적·독자·범위·구조 outline | plan 1page | `structure/*`, `workflows/plan` |
| draft | 2 | 본문 작성 | `.docx` draft | `content/*`, `design/*`, `office/word`, `writing/*`, `workflows/draft` |
| review | 3 | 6 axis 검토 | 변경 추적·댓글 | `quality/review`, `quality/consistency`, `workflows/review` |
| finalize | 4 | 최종화·export·공유 | final `.docx` + PDF | `office/export`, `workflows/finalize` |

**6 axis review**: 목적 · 구조 · 콘텐츠 · 언어 · a11y · 품질

---

## 동적 로드 매트릭스 (작업 영역 → 로드)

| 작업 영역 | 로드 |
|---|---|
| 전체 문서 구조 설계 | `structure/document`, `structure/heading`, `workflows/plan` |
| 본문 단락 작성 | `content/paragraph`, `writing/writing` |
| list 작성 | `content/list` |
| 표 작성 | `content/table` |
| 그림·차트·다이어그램 | `content/figure` |
| 인용·각주·참고문헌 | `structure/reference`, `content/quotation` |
| heading·목차 | `structure/heading`, `office/word` |
| 디자인 (typography·style·layout) | `design/*` |
| Word 기능 (변경 추적·field·자동화) | `office/word` |
| template 사용·수정 | `office/template`, `branding/brand` |
| 출력 (docx·PDF·인쇄) | `office/export` |
| 접근성 점검 | `accessibility/a11y` |
| 브랜드 일관성 | `branding/brand` |
| 기술 문서 | `writing/technical` |
| 학술 문서 | `writing/academic`, `structure/reference` |
| 검토 | `quality/review`, `quality/consistency`, `workflows/review` |
| **모든 작업 공통** | `writing/writing`, `accessibility/a11y`, `branding/brand`, `quality/consistency` |

---

## 작업 종류 → workflow

**모든 작업의 entry는 `workflows/task-flow`**.

| 작업 | 흐름 |
|---|---|
| 새 문서 작성 | `plan` → `draft` → `review` → `finalize` |
| 기존 문서 수정 | `draft` (부분) → `review` → `finalize` |
| 검토만 | `review` |
| 출력·배포 | `finalize` |

## 문서 종류별 표준

| 종류 | 추가 참조 |
|---|---|
| memo | `structure/document` (memo 섹션). 1~2 page |
| 보고서 (executive) | `structure/document`. 요약 우선 + 5~10 page + 부록 |
| 보고서 (technical) | `writing/technical`. 다이어그램·코드·15~50 page |
| 제안서 | `structure/document`. P→S→Cost→Team→CTA |
| 매뉴얼 | `writing/technical`. step-by-step + troubleshooting + index |
| 학술 논문 | `writing/academic`, `structure/reference`. IMRaD + 인용 + 재현성 |
| 계약서 | 법무 review 의무. `structure/document` |
| 공문 | 형식 엄격. 사내 standard 따름 |

---

## 항상 적용

- **목적·독자·범위** 확정 없이 작성 시작 X (`workflows/plan` 강제)
- template + Word style 사용 (직접 서식 X)
- heading 자동 (제목 1·2·3...)
- 목차·캡션·번호·cross-ref는 Word 자동 기능 (수동 X)
- 변경 추적·댓글로 review (직접 본문 수정 X)
- field update 전 final (`F9`)
- 메타·hidden 정보 검사 (`파일 > 정보 > 문서 검사`)
- a11y (`accessibility/a11y`)
- brand 일관 (`branding/brand`)
- 용어·표기 consistency (`quality/consistency`)
- 6 axis review (`quality/review`)
- 폰트 embed + PDF 같이 export

---

## 인덱스 갱신 책임

- 새 카테고리·workflow 추가 시 `index.yml` 갱신
- 새 동적 로드 패턴이면 본 파일 매트릭스 갱신

---

## 호출 흐름

```
요청 → plan(목적·독자·범위·구조)
  → draft(template + heading + 본문 + 인용)
  → review(self → peer → stakeholder → proofreader)
  → finalize(변경 추적 적용 + field update + 메타 검사 + export + archive)
```
