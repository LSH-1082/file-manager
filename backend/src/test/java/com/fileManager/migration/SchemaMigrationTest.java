package com.fileManager.migration;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.*;

/** 이미 V2까지 적용된 DB에 V3를 적용해도 기존 발송 문서 행이 그대로인지 확인한다(서버를 띄우지 않고 별도 인메모리 H2를 쓴다). */
class SchemaMigrationTest {
    private final DriverManagerDataSource db = new DriverManagerDataSource(
            "jdbc:h2:mem:migration-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    private final JdbcClient jdbc = JdbcClient.create(db);

    @AfterEach
    void shutdown() {
        jdbc.sql("SHUTDOWN").update();
    }

    private void migrateTo(String version) {
        Flyway.configure().dataSource(db).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(version)).load().migrate();
    }

    private long tables(String name) {
        return jdbc.sql("SELECT count(*) FROM information_schema.tables WHERE lower(table_name) = :t")
                .param("t", name).query(Long.class).single();
    }

    private List<Map<String, Object>> scheduleRows() {
        return jdbc.sql("""
                SELECT id, document_id, start_date, planned_send_date, memo, created_at, updated_at
                FROM schedules ORDER BY id""").query().listOfRows();
    }

    @Test
    void v3KeepsExistingSchedulesWithoutGroup() {
        migrateTo("2");
        assertEquals(0, tables("schedule_groups"));
        assertEquals(0, tables("schedule_settings"));

        String doc = UUID.randomUUID().toString();
        jdbc.sql("""
                INSERT INTO documents (id, category_id, title, dir_name, created_at, updated_at)
                VALUES (:d, NULL, '기존 문서', 'old', TIMESTAMP '2026-11-01 00:00:00', TIMESTAMP '2026-11-01 00:00:00')""")
                .param("d", doc).update();
        for (String memo : new String[] {"1차 발송", null}) {
            jdbc.sql("""
                    INSERT INTO schedules (id, document_id, start_date, planned_send_date, memo, created_at, updated_at)
                    VALUES (:i, :d, DATE '2026-11-01', DATE '2026-11-20', :m,
                            TIMESTAMP '2026-11-01 01:02:03.456789', TIMESTAMP '2026-11-02 04:05:06.789012')""")
                    .param("i", UUID.randomUUID().toString()).param("d", doc).param("m", memo).update();
        }
        List<Map<String, Object>> before = scheduleRows();
        assertEquals(2, before.size());

        migrateTo("3");

        assertEquals(before, scheduleRows());
        assertEquals(2L, jdbc.sql("SELECT count(*) FROM schedules WHERE group_id IS NULL").query(Long.class).single());
        assertEquals(1, tables("schedule_groups"));
        assertEquals(0L, jdbc.sql("SELECT count(*) FROM schedule_groups").query(Long.class).single());
        List<Map<String, Object>> settings =
                jdbc.sql("SELECT id, layout, next_color_no FROM schedule_settings").query().listOfRows();
        assertEquals(1, settings.size());
        assertEquals(1, settings.get(0).get("id"));
        assertEquals("group", settings.get(0).get("layout"));
        assertEquals(0, settings.get(0).get("next_color_no"));
    }

    @Test
    void v3ForeignKeyEmptiesGroupIdWhenGroupIsDeleted() {
        migrateTo("3");
        String doc = UUID.randomUUID().toString();
        String group = UUID.randomUUID().toString();
        String schedule = UUID.randomUUID().toString();
        jdbc.sql("""
                INSERT INTO documents (id, category_id, title, dir_name, created_at, updated_at)
                VALUES (:d, NULL, '문서', 'doc', TIMESTAMP '2026-11-01 00:00:00', TIMESTAMP '2026-11-01 00:00:00')""")
                .param("d", doc).update();
        jdbc.sql("""
                INSERT INTO schedule_groups (id, name, start_date, end_date, color_no, created_at, updated_at)
                VALUES (:g, '일정', DATE '2026-11-01', DATE '2026-11-30', 0,
                        TIMESTAMP '2026-11-01 00:00:00', TIMESTAMP '2026-11-01 00:00:00')""")
                .param("g", group).update();
        jdbc.sql("""
                INSERT INTO schedules (id, document_id, start_date, planned_send_date, memo, created_at, updated_at, group_id)
                VALUES (:i, :d, DATE '2026-11-01', DATE '2026-11-20', NULL,
                        TIMESTAMP '2026-11-01 00:00:00', TIMESTAMP '2026-11-01 00:00:00', :g)""")
                .param("i", schedule).param("d", doc).param("g", group).update();

        jdbc.sql("DELETE FROM schedule_groups WHERE id = :g").param("g", group).update();

        assertEquals(1L, jdbc.sql("SELECT count(*) FROM schedules WHERE id = :i AND group_id IS NULL")
                .param("i", schedule).query(Long.class).single());
    }
}
