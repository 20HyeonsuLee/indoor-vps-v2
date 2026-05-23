## rule (PowerPoint 기능 활용)
- **슬라이드 마스터** (보기 > 슬라이드 마스터): layout·색·font 표준화. design/template.md 참조.
- **테마** + **변형**: brand theme 1개 등록.
- **placeholder**: 텍스트·이미지·차트는 master placeholder에 채움. 직접 text box 추가 X.
- **개체 정렬**: 정렬·맞춤·균등 분배 도구 적극 (눈대중 X).
- **눈금자/안내선**: 정렬 일관성 위해 활성.
- **섹션**: 큰 deck은 섹션 나눠 navigation 쉽게.

## rule (애니메이션·전환)
- 애니메이션 **최소**. 의미 있는 reveal만 (단계별 설명·강조).
- 전환은 cut 또는 fade. spinning·3D·zoom X.
- 슬라이드 내 entrance는 fade·appear (subtle). 화려한 entrance X.
- 모든 슬라이드 같은 전환 (deck 일관).
- 자동 전환 시간 X (수동 advance).

## rule (포함 가능)
- chart는 **PowerPoint 차트** 직접 (편집 가능). 이미지 paste X.
- 표는 **PowerPoint 표** 또는 Excel paste-link.
- 동영상은 embed 또는 link. autoplay 신중.
- 수식은 Equation editor.
- 코드는 mono font + 색 syntax highlight (또는 carbon.now.sh 이미지).

## rule (협업)
- 변경 추적은 OneDrive/SharePoint 공유 또는 댓글.
- 댓글로 review (직접 슬라이드 수정 X — 충돌).
- version은 OneDrive 자동 또는 `_v01`, `_v02` 명명.
- 동시 편집은 Microsoft 365 web 또는 desktop.

## rule (저장·호환)
- 저장은 `.pptx` (이전 `.ppt` X).
- 폰트는 **embed** (`옵션 > 저장 > 파일에 글꼴 포함`) — 다른 PC 깨짐 방지.
- 슬라이드 비율 16:9 (디자인 > 슬라이드 크기).
- compress images (`그림 압축`) — 파일 크기 절감.
- 외부 link는 절대 경로 X (broken).

## rule (단축키 / 접근성)
- Alt text는 이미지·차트마다 (accessibility/a11y.md).
- reading order는 화면 보기에서 확인 (스크린리더).
- 슬라이드 노트로 발표 script (청중에 안 보임).
- Outline view로 deck flow 점검.

## forbidden
- master 없이 매번 직접 그림
- 화려한 entrance·exit 애니메이션
- 자동 전환 시간 설정
- 외부 link 절대 경로
- 폰트 embed 누락 (다른 PC 깨짐)
- 이미지 차트 paste (편집 불가)
- 동시 편집 충돌 무시
- 저장 시 손실 형식 (이전 `.ppt`)
- 비밀번호 protected pptx를 sharing (열기 어려움)
- 자동저장 disable
- 직접 슬라이드 수정으로 review (댓글 사용)
- 슬라이드 노트에 비밀값
