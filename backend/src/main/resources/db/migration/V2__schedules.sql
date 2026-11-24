
-- 10. 일정. 문서(카테고리 문서·미분류 문서)마다 여러 개. 문서가 지워지면 함께 지워진다
CREATE TABLE schedules (
  id                CHAR(36)    NOT NULL,
  document_id       CHAR(36)    NOT NULL,
  start_date        DATE        NOT NULL,
  planned_send_date DATE        NOT NULL,
  memo              VARCHAR(20) NULL,
  created_at        DATETIME(6) NOT NULL,
  updated_at        DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_schedules_document (document_id),
  CONSTRAINT fk_schedules_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE,
  CONSTRAINT ck_schedules_period CHECK (start_date <= planned_send_date),
  CONSTRAINT ck_schedules_memo CHECK (memo IS NULL OR char_length(memo) BETWEEN 1 AND 20)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
