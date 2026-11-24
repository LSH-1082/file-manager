# 백엔드

문서 관리 웹앱의 REST 서버다. Java 17 + Spring Boot 4.1.2 + Gradle 래퍼 + Spring Data JPA, DB는 MySQL 8이고 스키마는 Flyway가 만든다. 파일 본문은 DB가 아니라 저장 루트 아래 폴더에 둔다. MySQL이 없으면 `dev` 프로필(H2 파일 DB, MySQL 모드)로 돌린다.

## 실행

```
./gradlew bootRun                                         # MySQL로 실행. http://localhost:8080/api
./gradlew bootRun --args='--spring.profiles.active=dev'   # MySQL 없이 H2 파일 DB로 실행
./gradlew test                                            # 통합 테스트(H2 MySQL 모드)
./gradlew build                                           # 빌드 + 테스트
```

- `JAVA_HOME`은 JDK 17 이상이어야 Gradle 래퍼가 뜬다. 툴체인 JDK 17은 Gradle이 받는다.
- 실행 인자(`--이름=값`)가 `application.yml`보다 우선한다. 예: `--server.port=8081`.
- CORS 허용 출처는 `fileManager.cors.allowed-origins`(기본 `http://localhost:5173`, `http://localhost:4173`)다. 프런트는 보통 프록시로 부르므로 `VITE_API_BASE`에 절대 주소를 줄 때만 필요하다.

## DB

MySQL 8.0.16 이상. DB `fileManager`는 처음 연결할 때 만들어진다(`createDatabaseIfNotExist=true`).

| 설정 키 | 환경변수 | 기본값 |
|---|---|---|
| `spring.datasource.url` | `FILEMANAGER_DB_URL` | `jdbc:mysql://localhost:3306/fileManager?createDatabaseIfNotExist=true&connectionTimeZone=UTC&characterEncoding=UTF-8` |
| `spring.datasource.username` | `FILEMANAGER_DB_USERNAME` | `fileManager` |
| `spring.datasource.password` | `FILEMANAGER_DB_PASSWORD` | `fileManager1!` |

- `dev` 프로필은 `./data/db/fileManager`(실행 디렉터리 기준) H2 파일을 쓴다. 다른 위치는 `--spring.datasource.url=jdbc:h2:file:<경로>;MODE=MySQL;DATABASE_TO_LOWER=TRUE`로 준다.
- Hibernate는 스키마를 바꾸지 않는다(`ddl-auto: none`). 시각은 UTC로 저장한다.

## 스키마 변경

- 마이그레이션은 `src/main/resources/db/migration/V{n}__*.sql`이다. 이미 있는 파일은 고치지 않고 새 번호 파일을 더한다.
- `docs/db/schema.sql`은 V1부터 마지막 번호까지를 차례로 이어 붙인 사본이다. 새 파일을 더하면 같은 내용을 `schema.sql` 끝에도 붙인다. `SchemaTest`가 일치를 확인한다.

## 저장 루트

- 설정 `fileManager.storage-root`(기본 `./data/storage`), 환경변수 `FILEMANAGER_STORAGE_ROOT`. UNC 경로(SMB 공유)도 된다: `--fileManager.storage-root=//192.168.0.8/Media/repository`.
- 다른 DB를 쓰는 두 서버가 같은 저장 루트를 함께 쓰지 않는다.

```
{루트}/{카테고리}/{버전}/{저장 이름}           원본, 원본을 거절한 검토본
{루트}/{카테고리}/{버전}/edit{n}/{저장 이름}   수정본 n번과 수정대조표, 그 수정본을 거절한 검토본
{루트}/{카테고리}/_자료/{파일 이름}            양식·참고자료
{루트}/_미분류/{문서 제목}/{버전}/…            카테고리가 없는 문서
저장 이름 = {카테고리} {버전}[ 수정대조표|검토본]_{yyyyMMdd}_{작업자|검토자}.{확장자}
```

- 이름 규칙은 `file/domain/StorageLayout`, 금지 문자 처리는 `file/infrastructure/PathNames`에 있다. 같은 이름이 있으면 ` (2)`가 붙는다.
- 카테고리·버전 이름을 바꾸면 폴더와 파일 이름이 따라 바뀐다. 폴더 삭제는 커밋 뒤에 하고, 롤백되면 이동·이름 변경을 역순으로 되돌린다.

## 초기화

서버를 끄고 DB를 비우고(MySQL `DROP DATABASE`, `dev`는 `data/db` 삭제) 저장 루트를 지운다. 하나만 지우면 DB와 파일이 어긋난다. 저장 루트가 SMB 공유면 공유 파일은 사람이 직접 정리한다.

## 도커 이미지

```
docker build -t filemanager-backend .
docker run -d -p 8080:8080 -e FILEMANAGER_DB_URL='jdbc:mysql://<호스트>:3306/fileManager?createDatabaseIfNotExist=true&connectionTimeZone=UTC&characterEncoding=UTF-8' -e FILEMANAGER_DB_PASSWORD=<비밀번호> -v fm-storage:/app/data/storage filemanager-backend
```

- JDK 17로 `bootJar`를 만들고 JRE 17로 실행하는 멀티스테이지다. 이미지 빌드는 테스트를 돌리지 않는다. 이미지는 MySQL 전용이다.
- 저장 루트는 `/app/data/storage`(`VOLUME`), 사용자는 uid 10001이다.
- 루트의 `docker-compose.yml`은 MySQL·백엔드·프런트를 함께 띄우고 저장 루트에 SMB 공유를 CIFS 볼륨으로 붙인다.

## 패키지 (`com.fileManager`)

도메인마다 `presentation → application → domain ← infrastructure` 네 계층이고, 공용 코드는 `global`(`config`, `error`, `util`)에 있다.

| 도메인 | 하는 일 |
|---|---|
| `file` | 파일 행과 본문 저장소, 다운로드 `GET /api/files/{id}` |
| `category` | 카테고리 API(숨김 포함), 카테고리 폴더, 이름 변경·삭제 이벤트 |
| `resource` | 카테고리 양식·참고자료 |
| `document` | 문서·버전·파일, 단계 전이(작업완료·검토·발송)와 취소 |
| `schedule` | 발송 문서(`Schedule`), 일정(`ScheduleGroup`), 보기 설정 |

- 도메인 의존: `file ← category ← resource, document`, `document ← schedule`. 아래 도메인은 위를 모르고, 위로 알릴 때는 이벤트(`CategoryRenamedEvent`, `CategoryDeletingEvent`)를 쓴다.
- `domain` 계층은 Spring·Hibernate·`java.nio.file`을 쓰지 않는다(엔티티의 `jakarta.persistence`만 허용). `ArchitectureTest`가 확인한다.

## 테스트

`./gradlew test`는 MySQL 없이 H2 인메모리(MySQL 모드)로 돈다. 통합 테스트는 `ApiTestSupport`를 상속해 HTTP로 부른다. `file/LocalFileStorageUncTest`는 Windows에서 환경변수 `FILEMANAGER_UNC_TEST_ROOT`를 줄 때만 돈다(`./gradlew cleanTest test --tests 'com.fileManager.file.LocalFileStorageUncTest'`).
