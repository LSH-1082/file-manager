package com.fileManager;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchemaTest extends ApiTestSupport {
    @Test
    void flywayCopyEqualsDesignSchema() throws Exception {
        assertEquals(Files.readString(Path.of("../docs/db/schema.sql")),
                Files.readString(Path.of("src/main/resources/db/migration/V1__schema.sql")));
    }

    @Test
    void flywayCreatesNineTables() {
        Integer n = jdbc.sql("""
                SELECT count(*) FROM information_schema.tables
                WHERE lower(table_name) IN ('categories', 'documents', 'stored_files', 'document_versions',
                                            'version_files', 'version_reviewers', 'category_resources',
                                            'version_review_rounds', 'review_round_reviewers')""")
                .query(Integer.class).single();
        assertEquals(9, n);
    }
}
