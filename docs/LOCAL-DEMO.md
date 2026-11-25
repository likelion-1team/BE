# 로컬 백엔드 합성 시연

운영 배포용이 아닌 명시적 `demo` 프로필입니다. 실제 Spring API·인증·파싱·저장·집계를 사용하며 외부 Gemini 호출만 고정 합성 응답으로 대체합니다. 모델 추정 결과는 UNKNOWN이고, 성과 확인값은 시드 스크립트가 별도로 기록합니다.

## 실행

Java 17, Node 24, MySQL 8.4가 필요합니다. MySQL은 운영 DB와 분리해 127.0.0.1:3307의 `toki_demo` 데이터베이스를 사용합니다. Docker가 있으면 `DEMO_DB_PASSWORD`를 로컬 전용 값으로 설정한 뒤 `docker compose -f compose.demo.yaml up -d`로 준비합니다. 기존 docker-compose.yml의 서비스·볼륨·3306 포트는 사용하지 않습니다.

1. `DEMO_DB_PASSWORD`를 DB 암호로 지정합니다.
2. `./gradlew bootRun --args='--spring.profiles.active=demo'` (Windows: gradlew.bat).
3. 다른 터미널에서 `node scripts/seed-local-demo.mjs`.
4. FE에서 `API_PROXY_TARGET=http://127.0.0.1:8081`, `VITE_API_BASE_URL=/api/v1`, `VITE_AUTH_TRANSPORT=auto`, `VITE_EMAIL_CHECK_METHOD=GET`을 설정하고 `npm run dev -- --port 5175 --strictPort`.
5. http://127.0.0.1:5175/login 에서 로그인합니다. `/demo`는 FE 전용 시뮬레이션이므로 이 백엔드 검증에서는 일반 로그인 화면을 사용합니다.

로컬 계정은 `demo.leader@local.test`(팀장), `demo.member1@local.test`~`demo.member5@local.test`(팀원), 공통 비밀번호 `DemoOnly1234!`입니다. 운영 서버용 계정이 아닙니다. 다시 시드하면 같은 계정에 새 합성 팀을 만듭니다. 기존 팀을 지우지 않습니다.

## 운영 분리

- 프로필이 없으면 기존 Gemini 클라이언트를 그대로 사용합니다.
- demo 클라이언트는 서버 바인딩 127.0.0.1과 지정된 로컬 DB URL이 아니면 시작을 거절합니다.
- 시드 스크립트는 URL을 외부에서 받지 않으며 127.0.0.1:8081에만 연결합니다. `X-Toki-Demo: local-synthetic` 헤더가 없으면 데이터 생성 전에 중단합니다.
- 입력 메시지가 `[DEMO]`로 시작하지 않으면 합성 분석을 거절합니다. 실제 대화를 이 환경에 넣지 마세요.
- JWT 키는 로컬 시연 전용 공개값입니다. 이 프로필을 운영에 사용하면 안 됩니다.
- 서비스는 로컬에서만 열립니다. 시드 결과의 식별자 요약은 `demo-fixtures/local-demo-result.json`에 기록하며 Git에서 제외합니다. 토큰·운영 자격 증명은 기록하지 않습니다.

## 검증 데이터와 기대값

기여자 6명, 각 파일 5행(동일 ID·내용 1개 중복), 각 4건 분석 → 총 24개 분석 항목입니다. 완료 주간은 2026-11-16이며 대상 기간은 11월입니다. 총 18건 성공·6건 실패로 성공률 75%, 오류 수정 재작업 6건으로 재작업률 25%입니다. 토큰 수는 실제 토크나이저 결과이므로 FE 전용 목업의 2,000토큰과 다릅니다.

시드 스크립트는 업로드 후보 중복 제거, 분석 항목 4건, 같은 파일 재업로드의 SAME_CONTENT 선택 차단, 다른 내용을 가진 동일 ID 파일 거절, 결과·재작업 확인, 공유 미리보기·승인·가이드 지정·팀원 열람과 집계 24건·75%·25%를 검사합니다.

`demo-fixtures/conversations-duplicate.json`은 같은 대화가 반복된 입력, `conversations-conflict.json`은 같은 ID에 내용이 다른 입력입니다. 전자는 한 번만 후보로 반환하고 후자는 400 `CONFLICTING_CONVERSATION_ID`로 거절합니다. JSON·ZIP에 같은 정책을 적용합니다. 기간 밖 첫 항목과 기간 안 두 번째 항목이 같은 ID인 경우에도 임의로 섞어 분석하지 않습니다.

이것은 실제 외부 AI 품질·운영 DB 이관·운영 배포 검증을 대신하지 않습니다. BE 규칙에 따라 기능 브랜치를 `develop` 대상으로 리뷰합니다.
