## rule
- registry는 GitHub Container Registry (`ghcr.io/<owner>/<repo>`).
- 이미지 태깅 3 종:
  - **immutable**: `<sha>` (커밋 SHA) — CI가 빌드마다 push. 진짜 식별자.
  - **mutable env pointer**: `:dev` / `:prod` — 환경별 현재 운영 이미지를 가리킴.
  - **release**: `:vX.Y.Z` — SemVer 태그 push 시 생성.
- promotion은 **digest 기반**. dev 검증 digest를 prod로 재태그(이미지 재빌드 X).
- production에서는 mutable tag(`:prod`, `:latest`) 직접 사용 X. 항상 immutable digest(`@sha256:...`) 참조.
- 이미지 크기 임계 < 1.5GB 권장 (Python + JRE 동거 고려). 초과 시 base image 또는 stage 검토.
- multi-arch는 현재 `linux/amd64`만. arm64 추가는 별도 ADR.
- 이미지에 secret bake 금지 (config/env.md 참조).
- 이미지 OCI label 명시: `org.opencontainers.image.source`, `org.opencontainers.image.revision`, `org.opencontainers.image.created`.
- registry 인증은 `GITHUB_TOKEN` 또는 PAT. 평문 hardcode 금지.
- 이미지 보관 정책: `:<sha>` 90일, `:vX.Y.Z` 영구, `:dev`/`:prod`는 mutable.

## forbidden
- `:latest` 태그 prod 사용 (digest 또는 SemVer만)
- 같은 mutable tag로 prod와 dev 동시 가리킴 (혼동)
- 이미지 재빌드로 promote (dev digest와 다른 binary가 prod에 가는 risk)
- registry credential을 이미지 안에 bake
- multi-arch 미지원 (`linux/arm64` 누락 — 명시적 결정 전엔 amd64만)
- OCI label 누락 (source/revision 추적 불가)
- 이미지 보존 무한 (디스크 낭비)
- 비공식 registry 혼용 (ghcr 단일)
- digest 변조 (registry 측 mutation 감지 정책 없으면 신뢰 X)
