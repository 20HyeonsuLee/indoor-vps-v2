## rule (PDF)
- 외부 공유는 PDF default.
- 옵션:
  - 옵션 > **문서 속성** 포함 (검색)
  - **북마크 생성** (목차 → PDF 북마크)
  - **태그된 PDF** (a11y — 스크린리더 reading order)
  - 색공간: RGB (스크린) / CMYK (인쇄)
  - 압축: 표준 (작은 파일) vs 인쇄 (고품질)
- 비밀번호·인쇄 차단·복사 차단: sensitive 문서.
- 메타데이터 검토 (제목·작성자·키워드). sensitive 정보 없는지.

## rule (docx 공유)
- 편집 필요한 경우만 docx.
- 받는 사람의 Word 버전 호환 확인 (이전 .doc은 비추).
- 폰트 embed (`파일 > 옵션 > 저장 > 파일에 글꼴 포함`).
- 큰 이미지는 압축 (`그림 형식 > 그림 압축`).
- macro 포함 (`.docm`)은 보안 합의 후.

## rule (인쇄)
- 인쇄 미리보기 확인 (페이지 break·여백·색).
- 제본 여백 추가 (`레이아웃 > 여백 > 사용자 지정`).
- 흑백 인쇄 시 색 의존 콘텐츠 점검 (chart·강조).
- 양면 인쇄 시 머리말·꼬리말 짝·홀 다름 가능.
- 양수: A4 (한국) / Letter (북미).

## rule (배포 전 검토)
- 폰트 깨짐 (다른 PC)
- 이미지 해상도
- 페이지 번호·목차 일치
- 머리말·꼬리말 정확
- 변경 추적 모두 적용/거부
- 댓글 정리
- field update (`F9` 모두)
- 메타·hidden 정보 검사 (`파일 > 정보 > 문서 검사`)
- sensitive 정보 마스킹

## rule (파일명·archive)
- 파일명: `<topic>_<doc-type>_<YYYY-MM-DD>_v<NN>.docx`
- version은 `_v01`, `_v02` 또는 cloud history.
- archive: 사내 공유 폴더 + cloud + source + PDF.
- 최종은 `_final` 또는 SemVer (`_v1.0`).

## forbidden
- 추적 변경·댓글 잔존 외부 공유
- field 미갱신 (목차·페이지번호 stale)
- 폰트 embed 누락
- 메타·hidden 정보 검사 누락
- sensitive 정보 노출
- 비밀번호 미고지
- 인쇄 미리보기 없이 인쇄
- 같은 final이 여러 버전 (`_final`, `_final2`, `_final_real`)
- archive 누락
- 단일 backup (cloud만 또는 USB만)
- 단축형 `.doc` 저장 (호환 손실)
- macro 포함을 (.docm) 공유 받는 사람에 미고지
