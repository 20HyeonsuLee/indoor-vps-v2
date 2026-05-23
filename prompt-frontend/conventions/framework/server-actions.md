## rule
- Next.js 15+ **server action**으로 mutation 우선. API route는 BFF·외부 통합 한정.
- 정의: 함수 상단 `"use server"` 지시자. 또는 server component 안에서 정의 후 export.
- 위치: `src/features/<feature>/actions/<name>.ts`. 도메인별 폴더로 응집.
- 입력은 **Zod schema parse**. unvalidated input으로 작업 X.
- 인증·인가는 server action 진입부에서 확인 (`getSession` 등). client 신뢰 X.
- 반환은 type-safe — `{ ok: true, data }` 또는 `{ ok: false, error }`. exception 던지지 말고 result 패턴.
- `revalidatePath` / `revalidateTag`로 mutation 후 cache 갱신.
- form submit은 `<form action={serverAction}>` 또는 `useActionState` (React 19).
- progressive enhancement: JS 비활성에서도 동작 (form action native submit fallback).
- file upload는 `FormData` 인자. 크기·content-type 검증 + 재네이밍.
- redirect는 action 끝에서 `redirect(url)` 호출.
- rate limit · idempotency key는 민감 action에 적용.

## rule (useActionState 패턴)
```ts
'use server';
import { z } from 'zod';

const Schema = z.object({ email: z.string().email() });

export async function subscribe(prevState: State, formData: FormData) {
  const parsed = Schema.safeParse(Object.fromEntries(formData));
  if (!parsed.success) return { ok: false, error: parsed.error.flatten() };
  // ... 도메인 작업
  revalidatePath('/newsletter');
  return { ok: true };
}
```

```tsx
'use client';
const [state, formAction] = useActionState(subscribe, { ok: false });
<form action={formAction}>...</form>
```

## rule (vs API route)
| 상황 | server action | API route |
|---|---|---|
| 같은 origin에서 form submit | ✅ | △ |
| 외부 client(모바일 앱·third-party)가 호출 | ❌ | ✅ |
| webhook 수신 | ❌ | ✅ |
| 복잡 streaming response | ❌ | ✅ |
| BFF (외부 API 호출 + 변환) | △ | ✅ |
| simple CRUD from client | ✅ | △ |

## rule (security)
- action은 public endpoint와 동일 — auth/authz/input validation 모두 강제
- secret env 접근은 action 안에서만 (`NEXT_PUBLIC_*` 아님)
- CSRF: Next.js server action은 same-origin 강제 (자동 보호). but `headers()` 확인 권장
- error 메시지를 client에 raw 노출 X (사용자 친화 메시지)

## rule (TanStack Query와 통합)
- server action 결과를 query cache에 set 가능 (`queryClient.setQueryData`)
- 또는 `revalidatePath` 후 client가 query refetch
- mutation hook 패턴: `useMutation({ mutationFn: serverAction })`도 가능

## rule (optimistic UI)
- React 19 `useOptimistic` 활용
- mutation 시작 즉시 UI 업데이트 → action 실패 시 rollback

## forbidden
- input validation 누락 (Zod parse 필수)
- 인증 검증 없이 sensitive action 노출
- secret을 client component에서 action 인자로 전달 (server에서 직접 접근)
- exception throw로 client 에러 전파 (result 패턴)
- `revalidate` 누락 후 stale cache (사용자가 변경 후 새 페이지에서 옛 데이터)
- server action을 useEffect 안에서 호출 (`useMutation` 또는 form action)
- file upload 크기·content-type 검증 누락
- redirect URL을 user input에 직접 (open redirect)
- progressive enhancement 무시 (JS 의존 form만)
- rate limit 없는 민감 action (스팸 위험)
- server action을 import 후 client component에서 직접 호출하면서 type 잃기
