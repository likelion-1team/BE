# BE

## OpenAI 토큰 실측 비교

백엔드 실행 환경의 `OPENAI_API_KEY`에 OpenAI 프로젝트 키를 설정하면 팀 작업 공간의 **비용 · ROI 시나리오 → 실제 토큰 비교**를 사용할 수 있습니다. 로컬에서는 BE 디렉터리의 Git에서 제외된 `.env`에 `OPENAI_API_KEY=...`를 넣고 BE 디렉터리에서 실행합니다. 운영에서는 서버의 비밀 환경 변수를 사용합니다. `OPENAI_COMPARISON_MODEL`의 기본값은 `gpt-4.1-mini-2025-04-14`입니다.

비교 실행 시 공통 업무를 `input`, 각 프롬프트를 `instructions`로 보내 OpenAI Responses API를 두 번 호출합니다. 두 호출은 같은 모델, 응답당 최대 1,024토큰, `store: false`를 사용합니다. 입력과 응답 본문은 TOKI DB에 저장하지 않습니다.

- `GET /api/v1/teams/{teamId}/prompt-comparison`은 설정 여부와 모델명을 반환합니다. 팀 멤버만 접근할 수 있습니다.
- `POST /api/v1/teams/{teamId}/prompt-comparison`은 `task`, `baselinePrompt`, `improvedPrompt`를 받습니다. 공통 업무는 6,000자, 각 프롬프트는 4,000자까지 입력할 수 있습니다. 한 분당 계정별 3회, 서버 인스턴스별 12회까지 비교를 시작할 수 있습니다.
- `baseline`과 `improved`에 실행 상태, 응답 본문, `usage`, OpenAI `x-request-id`와 응답 ID를 각각 반환합니다. 한쪽 응답이 실패해도 다른 쪽 성공 결과는 유지하며, 사용량이 없으면 `null`을 반환합니다.

호출별 확인은 반환된 `usage`와 요청 ID를 사용합니다. 프로젝트 단위 확인은 [OpenAI Usage](https://platform.openai.com/usage)에서 해당 UTC 기간, 모델, API 키로 범위를 좁혀 대조합니다.

## GitHub 규칙

### 1. 브랜치 전략

| 브랜치 | 설명 |
| --- | --- |
| `main` | 배포용 브랜치. 직접 머지 및 push 금지 (머지 제한 설정됨) |
| `develop` | 개발 통합 브랜치. 모든 기능 브랜치는 `develop`으로 PR을 보낸다 |
| `기능/BE-#이슈번호/상세 기능` | `develop`에서 분기해 작업하는 기능 브랜치 |

- 작업은 항상 `develop`에서 브랜치를 따서 시작한다.
- 작업이 끝나면 `develop`을 대상으로 PR을 생성한다.

---

### 2. 브랜치 네이밍 규칙

```
기능/BE-#이슈번호/상세기능
```

- `기능`: 작업 종류 (예: `feat`, `fix`, `refactor` 등)
- `BE-#이슈번호`: 연결된 이슈 번호
- `상세기능`: 작업 내용을 나타내는 짧은 영문 설명 (kebab-case)

**예시**

```
feat/BE-#2/swagger-setting
```

---

### 3. PR 네이밍 규칙

```
[BE-#이슈번호] PR 제목
```

- PR 본문은 레포지토리의 **PR 템플릿**에 맞춰 작성한다.

**예시**

```
[BE-#2] Swagger 설정 추가
```

---

### 4. 머지 규칙

- 머지 방식은 **Squash and Merge**를 선택한다.
- Squash and Merge 방식 사용 시, 여러 번 커밋한 내용도 하나의 커밋으로 develop에 머지되기 때문에 develop 브랜치를 깔끔하게 관리하기 좋다.
- `main`으로의 머지는 막혀 있으며, 모든 PR의 대상 브랜치는 `develop`이다.
