package com.fileManager.file;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fileManager.file.application.StoredFileService;
import com.fileManager.file.domain.FileUpload;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StoredFileExtensionTest {
    private static final Set<String> DOC = Set.of("hwpx", "pdf");
    private static final Set<String> EXE = Set.of("exe");

    private static boolean has(String name, Set<String> exts) {
        return StoredFileService.hasExtension(new FileUpload(name, "application/octet-stream", 0, null), exts);
    }

    @Test
    void matchesCaseInsensitivelyAfterStrippingPath() {
        // when & then
        for (String n : new String[] {"a.pdf", "a.PDF", "a.HwPx", "dir/a.pdf", "dir\\a.pdf", "x.exe\\a.pdf"}) {
            assertEquals(true, has(n, DOC), n);
        }
    }

    @Test
    void rejectsOtherOrMissingExtensions() {
        // when & then: 다른 확장자나 확장자 없는 이름은 false
        for (String n : new String[] {"a", "a.", "a.txt", "a.pdf.exe", "a.exe/"}) {
            assertEquals(false, has(n, DOC), n);
        }

        // when & then: null 이름도 false
        assertEquals(false, has(null, DOC));
    }

    @Test
    void blockedSet() {
        // when & then: 막을 확장자는 대소문자·경로와 관계없이 true
        for (String n : new String[] {"a.exe", "A.EXE", "..\\a.Exe"}) assertEquals(true, has(n, EXE), n);

        // when & then: 마지막 확장자가 다르면 false
        for (String n : new String[] {"a.exe.txt", "a"}) assertEquals(false, has(n, EXE), n);
    }
}
