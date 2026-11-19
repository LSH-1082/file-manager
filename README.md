<div align="center">

# File Manager

문서의 작성, 검토, 발송 과정을 버전별로 기록하는 문서 관리 웹앱

![Java](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-7-3178C6?logo=typescript&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-8-646CFF?logo=vite&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?logo=mysql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker%20Compose-ready-2496ED?logo=docker&logoColor=white)

<img src="docs/images/board.png" alt="전체 문서 현황" width="100%" />

</div>

## 소개

규정, 계약서, 보고서 같은 문서는 여러 번 고쳐서 나갑니다. 공유 폴더에 `최종.hwpx`, `최종_진짜.hwpx`, `최종_수정3.hwpx`가 쌓이면 어느 파일이 검토 중이고 어느 파일을 이미 보냈는지 헷갈립니다.

File Manager는 문서마다 버전을 만들고, 버전마다 원본, 수정본, 수정대조표를 묶어 둡니다. 각 버전은 아래 다섯 단계를 차례로 거치며, 누가 언제 작업하고 검토하고 발송했는지 기록됩니다.

```
작업중 ──▶ 검토대기 ──▶ 검토중 ──▶ 발송대기 ──▶ 발송
 (작업자)    (작업완료)    (검토 시작)    (승인)       (종료)
                              │
                              └─ 거절 → 검토본과 함께 작업중으로 되돌아감
```

## 화면

<table>
  <tr>
    <td width="50%"><img src="docs/images/category.png" alt="검토 단계" /></td>
    <td width="50%"><img src="docs/images/versions.png" alt="버전과 수정대조표" /></td>
  </tr>
  <tr>
    <td align="center">검토중인 버전. 검토자와 검토 시작 시각이 남고, 여기서 승인하거나 거절합니다.</td>
    <td align="center">v2의 원본은 v1의 제출본입니다. 수정본은 수정대조표와 함께 올려야 등록됩니다.</td>
  </tr>
</table>

## 주요 기능

- 카테고리 하나에 문서가 하나 있습니다. 사이드바에서 카테고리를 고르면 그 문서의 버전 목록이 열리고, 카테고리가 없는 문서는 `미분류`에 모입니다.
- 작업완료, 검토 시작, 승인, 거절, 발송은 각각 취소할 수 있습니다. 승인과 거절 결정은 회차별 이력으로 남습니다.
- 수정본을 올릴 때는 원본과 비교한 수정대조표도 함께 올려야 합니다. 버전 문서는 `.hwpx`와 `.pdf`만 받습니다.
- 발송 전에 원본, 발송할 파일, 작업 기록, 검토 기록을 한 화면에서 확인합니다. 승인을 받지 않은 버전은 발송할 수 없습니다.
- 전체 문서 현황판에서 단계별 탭, 이름 검색, 정렬로 문서를 찾습니다.
- 카테고리마다 빈 양식과 참고자료를 따로 보관할 수 있습니다.
- 파일은 `카테고리/버전/{제목} {버전}_{yyyyMMdd}_{작업자}.pdf` 형태로 저장됩니다. 앱을 거치지 않고 SMB 공유 폴더를 직접 열어도 파일을 찾을 수 있습니다.

## 아키텍처

```
┌───────────────┐   /api    ┌────────────────────┐   JPA    ┌──────────┐
│  React + Vite │ ────────▶ │  Spring Boot 4     │ ───────▶ │ MySQL 8  │
│  (nginx :80)  │           │  (:8080, Flyway)   │          └──────────┘
└───────────────┘           │                    │  파일   ┌──────────────┐
                            │                    │ ──────▶ │ SMB 공유(CIFS)│
                            └────────────────────┘         └──────────────┘
```

| 영역 | 기술 |
| --- | --- |
| Frontend | React 19, TypeScript, Vite, oxlint. UI 라이브러리 없이 CSS만 사용 |
| Backend | Spring Boot 4 (Web MVC, Data JPA), Flyway, Java 17 |
| Database | 운영은 MySQL 8, 개발과 테스트는 H2 MySQL 모드 |
| Storage | 로컬 디렉터리, 또는 Docker CIFS 볼륨으로 마운트한 SMB 공유 |
| Infra | Docker Compose (MySQL, backend, nginx frontend, 백업 작업) |

백엔드는 도메인(`category`, `document`, `file`, `resource`)마다 `presentation → application → domain ← infrastructure` 레이어로 나뉩니다. 패키지 의존 방향과 JDBC 직접 사용 금지는 `ArchitectureTest`가 검사합니다.

```
backend/src/main/java/com/fileManager
├── category/   카테고리 CRUD, 이름 변경·삭제 이벤트
├── document/   문서·버전·단계 전이(StageService)
├── file/       저장 경로 규칙, 로컬/UNC 파일 저장
├── resource/   분류별 양식·참고자료
└── global/     config · error · util
```

## 실행 방법

### Docker Compose

```bash
cp .env.example .env          # SMB 사용자·비밀번호 등을 채운다
docker compose up --build
# → http://localhost:3000
```

MySQL 백업은 필요할 때 따로 실행합니다.

```bash
docker compose run --rm mysql-backup   # fileManager_<UTC>.sql.gz 를 SMB 공유에 저장
```

### 로컬 개발 (MySQL 없이)

```bash
# 1) 백엔드: H2 파일 DB를 쓰는 dev 프로필, :8080
cd backend
./gradlew bootRun --args='--spring.profiles.active=dev'

# 2) 프런트엔드: /api 요청을 :8080으로 프록시, :5173
cd frontend
npm ci
npm run dev
```

### 테스트

```bash
cd backend && ./gradlew test   # API 통합 테스트, 스키마, 아키텍처 규칙
cd frontend && npm run lint && npm run build
```

## 환경 변수

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `FILEMANAGER_DB_USERNAME` / `_PASSWORD` | `fileManager` | DB 계정. MySQL과 백엔드가 같은 값을 씀 |
| `MYSQL_ROOT_PASSWORD` | - | MySQL root 비밀번호. 백업에도 사용 |
| `FRONTEND_PORT` | `3000` | 호스트에 노출할 포트 |
| `FILEMANAGER_SMB_HOST` / `_SHARE` | - | 파일 본문을 저장할 SMB 공유 |
| `FILEMANAGER_SMB_USERNAME` / `_PASSWORD` | 필수 | SMB 계정 |
| `FILEMANAGER_SMB_VERS` | `2.1` | SMB 프로토콜 버전 |
| `FILEMANAGER_MYSQL_BACKUP_KEEP_DAYS` | `0` | 이 일수보다 오래된 백업을 삭제. 0이면 지우지 않음 |

전체 목록과 설명은 [`.env.example`](.env.example)에 있습니다.

## 데이터 모델

```
categories 1 ── 0..1 documents 1 ── * document_versions 1 ── * version_files ── stored_files
                                                       ├── * version_reviewers
                                                       └── * version_review_rounds ── review_round_reviewers
categories 1 ── * category_resources ── stored_files
```

DDL은 [`docs/db/schema.sql`](docs/db/schema.sql), 마이그레이션은 [`backend/src/main/resources/db/migration`](backend/src/main/resources/db/migration)에 있습니다.
