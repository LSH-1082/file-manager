package com.fileManager.file;

import com.fileManager.file.domain.FileUpload;
import com.fileManager.file.infrastructure.LocalFileStorage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 스프링 없이 LocalFileStorage의 폴더 이름 변경(대소문자만 바뀌는 경우 포함)을 확인한다. */
class LocalFileStorageTest {
    @TempDir Path tmp;
    LocalFileStorage storage;

    @BeforeEach
    void setUp() throws IOException {
        storage = new LocalFileStorage(tmp.resolve("root").toString());
    }

    private static List<String> names(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    private void folder(String name, String file) throws IOException {
        Path d = storage.root().resolve(name);
        Files.createDirectories(d);
        Files.writeString(d.resolve(file), name);
    }

    @Test
    void caseOnlyRenameKeepsExactNameAndContent() throws IOException {
        // given
        folder("Contract", "a.txt");

        // when
        String result = storage.renameDir("", "Contract", "contract", Set.of());

        // then
        assertEquals("contract", result);
        assertEquals(List.of("contract"), names(storage.root()));
        assertTrue(Files.exists(storage.root().resolve("contract").resolve("a.txt")));
    }

    @Test
    void sameNameIsReturnedAsIs() throws IOException {
        // given
        folder("Contract", "a.txt");

        // when
        String result = storage.renameDir("", "Contract", "Contract", Set.of());

        // then
        assertEquals("Contract", result);
        assertEquals(List.of("Contract"), names(storage.root()));
    }

    @Test
    void existingDifferentFolderGetsNumberSuffix() throws IOException {
        // given
        folder("one", "a.txt");
        folder("two", "b.txt");

        // when
        String result = storage.renameDir("", "one", "two", Set.of());

        // then
        assertEquals("two (2)", result);
        assertEquals(List.of("two", "two (2)"), names(storage.root()));
        assertEquals("one", Files.readString(storage.root().resolve("two (2)").resolve("a.txt")));
        assertEquals("two", Files.readString(storage.root().resolve("two").resolve("b.txt")));
    }

    @Test
    void caseSensitiveFileSystemKeepsOtherFolderWhenNamesDifferOnlyByCase() throws IOException {
        // given
        Path probe = Files.createDirectories(tmp.resolve("probe"));
        Files.createDirectory(probe.resolve("a"));
        boolean sensitive;
        try {
            Files.createDirectory(probe.resolve("A"));
            sensitive = true;
        } catch (IOException e) {
            sensitive = false;
        }
        Assumptions.assumeTrue(sensitive, "대소문자를 구분하는 파일 시스템에서만 확인한다");

        folder("Contract", "upper.txt");
        folder("contract", "lower.txt");

        // when
        String result = storage.renameDir("", "Contract", "contract", Set.of());

        // then
        assertEquals("contract (2)", result);
        assertEquals(List.of("contract", "contract (2)"), names(storage.root()));
        assertEquals("contract", Files.readString(storage.root().resolve("contract").resolve("lower.txt")));
        assertEquals("Contract", Files.readString(storage.root().resolve("contract (2)").resolve("upper.txt")));
    }

    private String file(String dir, String name, String content) throws IOException {
        Path d = storage.root().resolve(dir);
        Files.createDirectories(d);
        Files.writeString(d.resolve(name), content);
        return dir.isEmpty() ? name : dir + "/" + name;
    }

    private static FileUpload upload(String name, String content) {
        byte[] b = content.getBytes();
        return new FileUpload(name, "text/plain", b.length, () -> new ByteArrayInputStream(b));
    }

    @Test
    void renameFileChangesNameAndKeepsContent() throws IOException {
        // given
        String key = file("재무/v1", "a.pdf", "body");

        // when
        String result = storage.renameFile(key, "b.pdf");

        // then
        assertEquals("재무/v1/b.pdf", result);
        assertEquals(List.of("b.pdf"), names(storage.root().resolve("재무/v1")));
        assertEquals("body", Files.readString(storage.root().resolve(result)));
    }

    @Test
    void renameFileSameNameKeepsKey() throws IOException {
        // given
        String key = file("d", "a.pdf", "x");

        // when
        String result = storage.renameFile(key, "a.pdf");

        // then
        assertEquals(key, result);
        assertEquals(List.of("a.pdf"), names(storage.root().resolve("d")));
    }

    @Test
    void renameFileAddsNumberWhenTargetExists() throws IOException {
        // given
        file("d", "이름.pdf", "other");
        String key = file("d", "a.pdf", "mine");

        // when
        String result = storage.renameFile(key, "이름.pdf");

        // then
        assertEquals("d/이름 (2).pdf", result);
        assertEquals("other", Files.readString(storage.root().resolve("d/이름.pdf")));
        assertEquals("mine", Files.readString(storage.root().resolve(result)));
    }

    @Test
    void renameFileKeepsKeyWhenAlreadyNumberedSameName() throws IOException {
        // given
        file("d", "이름.pdf", "other");
        String key = file("d", "이름 (2).pdf", "mine");

        // when
        String result = storage.renameFile(key, "이름.pdf");

        // then
        assertEquals(key, result);
        assertEquals(List.of("이름 (2).pdf", "이름.pdf"), names(storage.root().resolve("d")));
    }

    @Test
    void renameFileCaseOnlyChange() throws IOException {
        // given
        String key = file("d", "Contract v1.pdf", "body");

        // when
        String result = storage.renameFile(key, "contract v1.pdf");

        // then
        assertEquals("d/contract v1.pdf", result);
        assertEquals(List.of("contract v1.pdf"), names(storage.root().resolve("d")));
        assertEquals("body", Files.readString(storage.root().resolve(result)));
    }

    @Test
    void renameFileReplacesForbiddenCharacters() throws IOException {
        // given
        String key = file("d", "a.pdf", "x");

        // when
        String result = storage.renameFile(key, "a/b:c? v1.pdf");

        // then
        assertEquals("d/a_b_c_ v1.pdf", result);
        assertEquals(List.of("a_b_c_ v1.pdf"), names(storage.root().resolve("d")));
    }

    @Test
    void storeNumbersSameNameTwice() throws IOException {
        // when & then: 처음 저장은 이름 그대로다
        assertEquals("d/x.pdf", storage.store(upload("x.pdf", "1"), "d", "x.pdf"));

        // when & then: 같은 이름을 다시 저장하면 (2)가 붙는다
        assertEquals("d/x (2).pdf", storage.store(upload("x.pdf", "2"), "d", "x.pdf"));
    }

    /** 폴더 이름 변경 뒤 그 안 파일 이름 변경이 함께 롤백돼도 파일을 먼저 되돌려 원래대로 돌아온다. */
    @Test
    void rollbackRunsInReverseOrder() throws IOException {
        // given
        String key = file("재무/v1", "재무 v1_x.pdf", "body");
        TransactionSynchronizationManager.initSynchronization();
        try {
            // when: 폴더와 그 안 파일의 이름을 바꾼다
            storage.renameDir("", "재무", "회계", Set.of());
            storage.renameFile("회계/v1/재무 v1_x.pdf", "회계 v1_x.pdf");

            // then: 바뀐 이름으로 있다
            assertTrue(Files.exists(storage.root().resolve("회계/v1/회계 v1_x.pdf")));

            // when: 트랜잭션 롤백을 알린다
            for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
                s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        // then: 원래 이름과 본문으로 돌아온다
        assertEquals("body", Files.readString(storage.root().resolve(key)));
        assertFalse(Files.exists(storage.root().resolve("회계")));
        assertEquals(List.of("재무"), names(storage.root()));
    }
}
