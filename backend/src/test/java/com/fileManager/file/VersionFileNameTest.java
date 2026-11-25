package com.fileManager.file;

import com.fileManager.file.domain.StorageLayout;
import com.fileManager.file.domain.StorageLayout.VersionFileKind;
import com.fileManager.file.domain.StorageLayout.VersionFileName;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 스프링 없이 버전 파일 이름 규칙을 확인한다. */
class VersionFileNameTest {
    private static final Instant AT = Instant.parse("2026-11-03T03:00:00Z");

    private static String date() {
        return DateTimeFormatter.BASIC_ISO_DATE.format(AT.atZone(ZoneId.systemDefault()).toLocalDate());
    }

    @Test
    void buildsDisplayAndStoredNames() {
        // when
        VersionFileName n = StorageLayout.versionFileName("재무", "v1", VersionFileKind.FILE, ".PDF", AT, "김재무");

        // then
        assertEquals("재무 v1.PDF", n.display());
        assertEquals("재무 v1_" + date() + "_김재무.PDF", n.stored());
    }

    @Test
    void revisionSheetHasSuffix() {
        // when
        VersionFileName n = StorageLayout.versionFileName("재무", "v1", VersionFileKind.REVISION_SHEET, ".hwpx", AT, "김재무");

        // then
        assertEquals("재무 v1 수정대조표.hwpx", n.display());
        assertEquals("재무 v1 수정대조표_" + date() + "_김재무.hwpx", n.stored());
    }

    @Test
    void reviewCopyHasSuffix() {
        // when
        VersionFileName n = StorageLayout.versionFileName("재무", "v1", VersionFileKind.REVIEW_COPY, ".pdf", AT, "박검토");

        // then
        assertEquals("재무 v1 검토본.pdf", n.display());
        assertEquals("재무 v1 검토본_" + date() + "_박검토.pdf", n.stored());
    }

    @Test
    void extensionStartsAtLastDotKeepingCase() {
        // when & then
        assertEquals(".PDF", StorageLayout.extension("결산표.PDF"));
        assertEquals(".hwpx", StorageLayout.extension("a.b.hwpx"));
        assertEquals("", StorageLayout.extension(".gitignore"));
        assertEquals("", StorageLayout.extension("readme"));
    }

    @Test
    void uploadDateUsesSystemZone() {
        // when & then
        assertEquals(AT.atZone(ZoneId.systemDefault()).toLocalDate(), StorageLayout.uploadDate(AT));
    }
}
