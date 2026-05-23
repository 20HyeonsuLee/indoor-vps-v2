## rule
- 폼은 **React Hook Form + Zod**.
- 폼 schema는 Zod로 정의. `zodResolver`로 RHF에 연결.
- 같은 schema에서 type 추출: `type FormValues = z.infer<typeof schema>`.
- 폼 컴포넌트는 `useForm<FormValues>({ resolver: zodResolver(schema) })`.
- 입력 등록은 `register` 또는 `Controller`(외부 lib UI). 명시.
- submit handler는 `handleSubmit(onSubmit)`. onSubmit은 server action 또는 mutation 호출.
- error 표시는 `formState.errors.<field>`. 필드 옆 inline 메시지.
- 서버 검증 실패는 `setError`로 RHF state에 반영.
- 1 form = 1 책임. 여러 무관한 입력 한 form X.
- defaultValues 명시. uncontrolled로 시작.
- 폼 reset은 mutation 성공 후 `reset(values?)`.

## forbidden
- raw `useState`로 폼 입력 관리 (RHF 사용)
- HTML5 `required`/`pattern` 의존 (Zod schema로 검증)
- schema 없이 ad-hoc validation 함수 (Zod 통일)
- `handleSubmit` 없이 form `onSubmit` 직접
- 서버 검증 실패를 store에 따로 보관 (`setError` 사용)
- error 메시지 hardcode (i18n 키 또는 schema message)
- 1 form에 여러 무관한 도메인 묶기
- defaultValues 없이 controlled 전환 후 warning 무시
- submit 중 button 활성 (`formState.isSubmitting` 활용)
- Zod schema 재사용 안 하고 컴포넌트마다 중복 정의
