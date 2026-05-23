## rule (필수 기능)
- **스타일** (`홈 > 스타일`): 모든 서식 (design/style.md).
- **참조 > 목차**: 자동 목차.
- **참조 > 캡션**: 그림·표 자동 번호.
- **참조 > 상호 참조**: 섹션·그림·표 동적 link.
- **참조 > 인용 및 참고문헌**: 인용 관리.
- **참조 > 각주/미주**: 자동 번호.
- **레이아웃 > 구역 나누기**: 섹션 break.
- **검토 > 변경 내용 추적**: 협업 review.
- **검토 > 새 메모**: 댓글.
- **검토 > 접근성 검사**: a11y 자동 점검.
- **삽입 > 표 / 그림 / 차트**: 콘텐츠 삽입.

## rule (협업 — 변경 내용 추적)
- review는 `검토 > 변경 내용 추적` 활성 후 수정.
- 직접 본문 수정 X — 추적 표시로 보임.
- 댓글로 의견 (코드 review 같은 prefix: `nit:`/`q:`/`suggest:`/`blocker:`).
- author는 검토 후 `변경 내용 적용/거부`.
- 최종 release 시 추적 변경 다 적용 + 댓글 정리.

## rule (협업 — 공동 편집)
- OneDrive/SharePoint에 저장 후 share.
- Microsoft 365 web 또는 desktop으로 동시 편집.
- 큰 변경은 분리 branch (별 파일) 또는 시간 분리.
- 자동 저장 활성 (cloud 필수).

## rule (필드·자동 갱신)
- 목차·페이지번호·cross-ref는 **field**로 자동.
- final 직전 `Ctrl+A` → `F9`로 모든 field update.
- field 수동 입력 X.

## rule (매크로·VBA)
- 매크로는 반복 작업 자동화 (수십+ 번 반복 시).
- 보안: 외부 .docx 매크로 비활성 (default).
- 매크로 포함 문서는 `.docm` (`.docx`가 아님).
- 매크로 사용 사전 합의 + 명문화.

## rule (보안)
- 비밀번호 (`파일 > 정보 > 문서 보호`): sensitive 문서만.
- 편집 제한 (read-only): 외부 공유 시.
- inspect document (`파일 > 정보 > 문서 검사`)로 메타·hidden 정보 검사.
- track changes·comment 잔존 검사 (final 전).

## rule (단축키 / 효율)
- `Ctrl+B/I/U`: bold/italic/underline
- `Ctrl+Alt+1/2/3`: 제목 1/2/3
- `Ctrl+Shift+L`: 글머리표
- `Ctrl+Enter`: 페이지 break
- `Shift+F5`: 마지막 편집 위치
- `F7`: 맞춤법 검사
- `F9`: field update

## forbidden
- 직접 서식 (스타일 사용)
- 수동 목차·번호·캡션 (자동 사용)
- 직접 본문 수정으로 review (변경 추적 사용)
- 동시 편집 시 충돌 무시
- 매크로 보안 검사 없이 외부 .docm 열기
- 비밀번호 protected를 받는 사람에 비밀번호 안 알림
- track changes 잔존 final 공유 (수신자 혼란)
- comment 잔존 final 공유
- field update 누락 (목차·페이지번호 stale)
- 자동 저장 비활성
- 단축키 없이 매번 메뉴 (생산성 ↓)
- inspect document 누락 (sensitive 메타 leak)
