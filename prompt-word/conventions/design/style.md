## rule
- Word **style sheet** (`홈 > 스타일`)로 모든 서식 통일. 직접 서식 X.
- 기본 style:
  - **표준 (Normal)**: 본문
  - **제목 1·2·3...**: heading 계층
  - **목록 단락**: list
  - **인용**: block quote
  - **캡션**: 그림·표
  - **각주 텍스트**: 각주
  - **본문 들여쓰기**: 첫 줄 들여쓰기 본문
- 사용자 정의 style 추가 시 명명 규칙 (`팀명_역할_사이즈` 등).
- 한 문서 = 한 style 세트 (다른 문서 style 충돌 방지).
- style 수정은 `홈 > 스타일 > 수정`. 직접 단락 서식 X.
- style 기반으로 목차 자동 생성.
- 다른 문서 가져올 때 style 충돌 주의 (style cleanup 권장).

## rule (template = style + master)
- 사내 standard `.dotx` template 사용.
- 새 문서는 template에서 시작 (`파일 > 새로 만들기 > 개인` 또는 `사용자 지정`).
- template은 brand·typography·layout·기본 style 포함.
- template 변경은 SSOT (사내 공유). 개별 수정 X.

## rule (theme)
- Word `디자인 > 테마` (색·font·effect 묶음).
- brand theme 1개 등록 후 모든 문서 적용.
- theme 변경 한 번에 문서 전체 갱신.

## forbidden
- 직접 서식 (스타일 우회)
- 스타일 무질서 (한 문서 5+ 사용자 정의 style)
- 같은 의미에 다른 style 사용
- 스타일 이름이 모호 (`스타일1`, `내 스타일`)
- 다른 문서 style 무검토 import
- 직접 서식으로 heading 흉내
- template 수정을 SSOT 동기화 없이
- theme을 매 단락 변경 (문서 전체 일관)
- list 들여쓰기를 스페이스로 (style의 들여쓰기 활용)
- 빈 단락으로 spacing (style spacing 활용)
