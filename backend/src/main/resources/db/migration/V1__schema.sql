-- 문서 관리 웹앱 RDB 스키마 (MySQL 8.0.16 이상, 테스트는 H2 MySQL 모드)
-- 설계 설명과 열 표는 docs/db/README.md가 정본이다. 이 파일은 같은 설계의 DDL이다.
-- id는 애플리케이션(JPA)이 UUID로 만들어 CHAR(36)에 넣는다. 단계(stage)는 열이 아니라 엔티티가 계산한다.
-- 이름 비교가 대소문자·악센트를 구분하도록 테이블마다 utf8mb4_bin 정렬을 쓴다.

-- 1. 카테고리. 이름 UNIQUE는 두지 않는다. dir_name은 저장 루트 바로 아래 카테고리 폴더 이름
CREATE TABLE categories (
  id         CHAR(36)     NOT NULL,
  name       VARCHAR(255) NOT NULL,
  dir_name   VARCHAR(255) NOT NULL,
  created_at DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT ck_categories_name CHECK (char_length(name) >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 2. 문서. 카테고리 하나 = 문서 하나(category_id UNIQUE). NULL은 여러 개 허용 = 미분류
--    dir_name은 버전 폴더들이 들어 있는 폴더(카테고리 폴더 또는 _미분류/{제목})
CREATE TABLE documents (
  id          CHAR(36)     NOT NULL,
  category_id CHAR(36)     NULL,
  title       VARCHAR(255) NOT NULL,
  dir_name    VARCHAR(255) NOT NULL,
  created_at  DATETIME(6)  NOT NULL,
  updated_at  DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uq_documents_category UNIQUE (category_id),
  CONSTRAINT fk_documents_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 3. 업로드된 파일 한 건(본문은 저장 루트 아래 파일, 여기에는 루트 기준 상대 경로 키만)
CREATE TABLE stored_files (
  id          CHAR(36)     NOT NULL,
  storage_key VARCHAR(700) NOT NULL,
  file_name   VARCHAR(500) NOT NULL,
  mime_type   VARCHAR(255) NOT NULL,
  size_bytes  BIGINT       NOT NULL,
  uploaded_by VARCHAR(5)   NULL,
  uploaded_at DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uq_stored_files_key UNIQUE (storage_key),
  CONSTRAINT ck_stored_files_size CHECK (size_bytes >= 0),
  CONSTRAINT ck_stored_files_uploaded_by CHECK (uploaded_by IS NULL OR char_length(uploaded_by) >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 4. 문서의 버전. label = 화면 이름(name, 없으면 v{번호}), dir_name = 문서 폴더 안의 버전 폴더 이름
--    review_file_id/review_revision_id는 결정 때의 현재 수정본·대조표(파일이 지워지면 서비스가 비운다)
CREATE TABLE document_versions (
  id                 CHAR(36)     NOT NULL,
  document_id        CHAR(36)     NOT NULL,
  number             INT          NOT NULL,
  name               VARCHAR(5)   NULL,
  label              VARCHAR(20)  NOT NULL,
  dir_name           VARCHAR(255) NOT NULL,
  worker             VARCHAR(5)   NULL,
  created_at         DATETIME(6)  NOT NULL,
  work_done_at       DATETIME(6)  NULL,
  review_started_at  DATETIME(6)  NULL,
  review_result      VARCHAR(10)  NULL,
  reviewed_at        DATETIME(6)  NULL,
  review_file_id     CHAR(36)     NULL,
  review_revision_id CHAR(36)     NULL,
  sent_at            DATETIME(6)  NULL,
  sent_to            VARCHAR(20)  NULL,
  sent_memo          TEXT         NULL,
  PRIMARY KEY (id),
  CONSTRAINT uq_versions_number UNIQUE (document_id, number),
  CONSTRAINT uq_versions_label UNIQUE (document_id, label),
  CONSTRAINT fk_versions_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE,
  CONSTRAINT ck_versions_number CHECK (number >= 1),
  CONSTRAINT ck_versions_review_result CHECK (review_result IS NULL OR review_result IN ('accept', 'reject')),
  CONSTRAINT ck_versions_decision_pair CHECK ((review_result IS NULL) = (reviewed_at IS NULL)),
  CONSTRAINT ck_versions_sent_to CHECK (sent_to IS NULL OR char_length(sent_to) <= 20)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE INDEX idx_versions_review_file ON document_versions (review_file_id);
CREATE INDEX idx_versions_review_revision ON document_versions (review_revision_id);

-- 5. 버전의 파일. seq 0 = 원본(대조표 없음), seq 1.. = 수정본 n(대조표 필수)
CREATE TABLE version_files (
  id               CHAR(36) NOT NULL,
  version_id       CHAR(36) NOT NULL,
  seq              INT      NOT NULL,
  file_id          CHAR(36) NOT NULL,
  revision_file_id CHAR(36) NULL,
  PRIMARY KEY (id),
  CONSTRAINT uq_vfiles_seq UNIQUE (version_id, seq),
  CONSTRAINT uq_vfiles_revision UNIQUE (revision_file_id),
  CONSTRAINT fk_vfiles_version FOREIGN KEY (version_id) REFERENCES document_versions (id) ON DELETE CASCADE,
  CONSTRAINT fk_vfiles_file FOREIGN KEY (file_id) REFERENCES stored_files (id) ON DELETE RESTRICT,
  CONSTRAINT fk_vfiles_revision FOREIGN KEY (revision_file_id) REFERENCES stored_files (id) ON DELETE RESTRICT,
  CONSTRAINT ck_vfiles_seq CHECK (seq >= 0),
  CONSTRAINT ck_vfiles_pair CHECK ((seq = 0) = (revision_file_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 6. 검토 시작 때 적은 검토자 목록(현재 결정도 이 목록을 쓴다. 회차의 검토자는 9번에 복사한다). reviewer_order는 0부터
CREATE TABLE version_reviewers (
  version_id     CHAR(36)   NOT NULL,
  reviewer_order INT        NOT NULL,
  name           VARCHAR(5) NOT NULL,
  PRIMARY KEY (version_id, reviewer_order),
  CONSTRAINT fk_reviewers_version FOREIGN KEY (version_id) REFERENCES document_versions (id) ON DELETE CASCADE,
  CONSTRAINT ck_reviewers_order CHECK (reviewer_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 7. 카테고리에 딸린 양식·참고자료. 카테고리를 지우면 함께 지워진다
CREATE TABLE category_resources (
  id          CHAR(36)     NOT NULL,
  category_id CHAR(36)     NOT NULL,
  kind        VARCHAR(20)  NOT NULL,
  title       VARCHAR(255) NOT NULL,
  note        TEXT         NOT NULL,
  file_id     CHAR(36)     NOT NULL,
  created_at  DATETIME(6)  NOT NULL,
  updated_at  DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uq_resources_file UNIQUE (file_id),
  CONSTRAINT fk_resources_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE CASCADE,
  CONSTRAINT fk_resources_file FOREIGN KEY (file_id) REFERENCES stored_files (id) ON DELETE RESTRICT,
  CONSTRAINT ck_resources_kind CHECK (kind IN ('template', 'reference', 'etc'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 8. 검토 회차(승인·거절 결정마다 한 행). round_no는 버전 안에서 1부터. 작업완료는 지우지 않고 결정 취소가 마지막 회차를 지운다
--    target_file_id/target_revision_id는 결정 당시 현재 수정본(없으면 원본)·대조표 id로 FK 없이 남긴다
--    review_file_id는 거절 때 올린 검토본. 대상 수정본을 지우면 서비스가 검토본을 지우고 NULL로 비운다
CREATE TABLE version_review_rounds (
  id                 CHAR(36)    NOT NULL,
  version_id         CHAR(36)    NOT NULL,
  round_no           INT         NOT NULL,
  result             VARCHAR(10) NOT NULL,
  reviewed_at        DATETIME(6) NOT NULL,
  target_file_id     CHAR(36)    NOT NULL,
  target_revision_id CHAR(36)    NULL,
  review_file_id     CHAR(36)    NULL,
  PRIMARY KEY (id),
  CONSTRAINT uq_rounds_no UNIQUE (version_id, round_no),
  CONSTRAINT uq_rounds_review_file UNIQUE (review_file_id),
  CONSTRAINT fk_rounds_version FOREIGN KEY (version_id) REFERENCES document_versions (id) ON DELETE CASCADE,
  CONSTRAINT fk_rounds_review_file FOREIGN KEY (review_file_id) REFERENCES stored_files (id) ON DELETE RESTRICT,
  CONSTRAINT ck_rounds_no CHECK (round_no >= 1),
  CONSTRAINT ck_rounds_result CHECK (result IN ('accept', 'reject')),
  CONSTRAINT ck_rounds_review_file CHECK (review_file_id IS NULL OR result = 'reject')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 9. 회차의 검토자(결정 당시 version_reviewers의 복사). reviewer_order는 0부터
CREATE TABLE review_round_reviewers (
  round_id       CHAR(36)   NOT NULL,
  reviewer_order INT        NOT NULL,
  name           VARCHAR(5) NOT NULL,
  PRIMARY KEY (round_id, reviewer_order),
  CONSTRAINT fk_round_reviewers_round FOREIGN KEY (round_id) REFERENCES version_review_rounds (id) ON DELETE CASCADE,
  CONSTRAINT ck_round_reviewers_order CHECK (reviewer_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
