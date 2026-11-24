package com.fileManager;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchemaTest extends ApiTestSupport {
    /** docs/db/schema.sql은 Flyway V1__schema.sql, V2__schedules.sql, V3__schedule_groups.sql, V4__category_hidden.sql을 차례로 구분자 없이 이어 붙인 합본과 같아야 한다. */
    @Test
    void flywayV1ToV4EqualsDesignSchema() throws Exception {
        String migrations = Files.readString(Path.of("src/main/resources/db/migration/V1__schema.sql"))
                + Files.readString(Path.of("src/main/resources/db/migration/V2__schedules.sql"))
                + Files.readString(Path.of("src/main/resources/db/migration/V3__schedule_groups.sql"))
                + Files.readString(Path.of("src/main/resources/db/migration/V4__category_hidden.sql"));
        assertEquals(Files.readString(Path.of("../docs/db/schema.sql")), migrations);
    }

    @Test
    void flywayCreatesTwelveTables() {
        Integer n = jdbc.sql("""
                SELECT count(*) FROM information_schema.tables
                WHERE lower(table_name) IN ('categories', 'documents', 'stored_files', 'document_versions',
                                            'version_files', 'version_reviewers', 'category_resources',
                                            'version_review_rounds', 'review_round_reviewers', 'schedules',
                                            'schedule_groups', 'schedule_settings')""")
                .query(Integer.class).single();
        assertEquals(12, n);
    }
}
