
-- 11. 일정(기간 묶음). 발송 문서(schedules)가 선택으로 속한다. 색 번호는 등록할 때 정해지고 바뀌지 않는다
CREATE TABLE schedule_groups (
  id         CHAR(36)    NOT NULL,
  name       VARCHAR(20) NOT NULL,
  start_date DATE        NOT NULL,
  end_date   DATE        NOT NULL,
  color_no   INT         NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT ck_schedule_groups_name CHECK (char_length(name) BETWEEN 1 AND 20),
  CONSTRAINT ck_schedule_groups_period CHECK (start_date <= end_date),
  CONSTRAINT ck_schedule_groups_color CHECK (color_no BETWEEN 0 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 12. 일정 화면 설정. 항상 한 행(id 1)이다. next_color_no는 다음에 등록할 일정의 색 번호
CREATE TABLE schedule_settings (
  id            INT         NOT NULL,
  layout        VARCHAR(10) NOT NULL,
  next_color_no INT         NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT ck_schedule_settings_one CHECK (id = 1),
  CONSTRAINT ck_schedule_settings_layout CHECK (layout IN ('group', 'band')),
  CONSTRAINT ck_schedule_settings_color CHECK (next_color_no BETWEEN 0 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

INSERT INTO schedule_settings (id, layout, next_color_no) VALUES (1, 'group', 0);

-- 발송 문서(schedules)의 소속 일정. NULL = 일정과 관계없는 문서. 일정이 지워지면 NULL이 된다
ALTER TABLE schedules ADD COLUMN group_id CHAR(36) NULL;
CREATE INDEX idx_schedules_group ON schedules (group_id);
ALTER TABLE schedules ADD CONSTRAINT fk_schedules_group FOREIGN KEY (group_id) REFERENCES schedule_groups (id) ON DELETE SET NULL;
