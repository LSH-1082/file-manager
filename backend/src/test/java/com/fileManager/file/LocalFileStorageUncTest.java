package com.fileManager.file;

import com.fileManager.file.domain.FileUpload;
import com.fileManager.file.domain.StorageLayout;
import com.fileManager.file.infrastructure.LocalFileStorage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UNC 공유(FILEMANAGER_UNC_TEST_ROOT) 아래 고유 임시 폴더를 저장 루트로 써서 LocalFileStorage를 확인한다(Windows, 환경변수가
 * 있을 때만 실행).
 */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfEnvironmentVariable(named = "FILEMANAGER_UNC_TEST_ROOT", matches = ".+")
class LocalFileStorageUncTest {
    Path root;
    LocalFileStorage storage;

    @BeforeEach
    void setUp() throws IOException {
        Path share = Path.of(System.getenv("FILEMANAGER_UNC_TEST_ROOT"));
        Assumptions.assumeTrue(share.toString().startsWith("\\\\"), "UNC 경로가 아니다");
        Assumptions.assumeTrue(Files.isDirectory(share), "공유에 접근할 수 없다");
        root = share.resolve(".fm-unc-check-" + UUID.randomUUID());
        storage = new LocalFileStorage(root.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        if (root == null) return;
        if (Files.exists(root)) {
            try (Stream<Path> s = Files.walk(root)) {
                for (Path p : s.sorted(Comparator.reverseOrder()).toList()) {
                    if (!p.startsWith(root)) continue;
                    Files.deleteIfExists(p);
                }
            }
        }
        assertFalse(Files.exists(root));
    }

    private static FileUpload upload(String name, byte[] bytes) {
        return new FileUpload(name, "text/plain", bytes.length, () -> new ByteArrayInputStream(bytes));
    }

    @Test
    void storesReadsRenamesAndDeletesOnUncRoot() throws IOException {
        // when & then: 저장 루트는 UNC 경로다
        assertTrue(storage.rootPath().startsWith("\\\\"));

        // when: 폴더를 만든다
        String cat = storage.createDir("", "계약 문서", Set.of());

        // then: 공유에 폴더가 생긴다
        assertEquals("계약 문서", cat);
        assertTrue(Files.isDirectory(storage.root().resolve(cat)));

        // when: 원본을 저장한다
        byte[] bytes = "UNC 본문".getBytes(StandardCharsets.UTF_8);
        String dir = StorageLayout.join(cat, "v1");
        String key = storage.store(upload("원본.txt", bytes), dir, "원본.txt");

        // then: 키와 본문이 맞다
        assertEquals("계약 문서/v1/원본.txt", key);
        try (var in = storage.open(key)) {
            assertArrayEquals(bytes, in.readAllBytes());
        }

        // when & then: 같은 이름을 다시 저장하면 (2)가 붙는다
        assertEquals("계약 문서/v1/원본 (2).txt", storage.store(upload("원본.txt", bytes), dir, "원본.txt"));

        // when: 수정본을 edit1 폴더에 저장한다
        String revName = StorageLayout.versionFileName("계약 문서", "v1", StorageLayout.VersionFileKind.FILE, ".txt",
                java.time.Instant.parse("2026-11-03T03:00:00Z"), "홍길동").stored();
        String revKey = storage.store(upload(revName, bytes), StorageLayout.join(dir, StorageLayout.editDir(1)), revName);

        // then
        assertEquals("계약 문서/v1/edit1/" + revName, revKey);

        // when & then: 폴더와 파일 이름을 바꾼다
        assertEquals("계약", storage.renameDir("", cat, "계약", Set.of()));
        assertEquals("계약/v1/원본 (3).txt", storage.renameFile("계약/v1/원본 (2).txt", "원본 (3).txt"));
        assertEquals("계약/v1/원본 (2).txt", storage.renameFile("계약/v1/원본 (3).txt", "원본 (2).txt"));

        // then: 바뀐 폴더에서 원본을 그대로 읽는다
        try (var in = storage.open("계약/v1/원본.txt")) {
            assertArrayEquals(bytes, in.readAllBytes());
        }

        // when & then: 루트 밖 경로는 거부한다
        assertThrows(IllegalArgumentException.class, () -> storage.resolve("../밖"));

        // when: 폴더를 지운다
        storage.deleteDirAfterCommit("계약");

        // then
        assertFalse(Files.exists(storage.root().resolve("계약")));
    }

    @Test
    void caseOnlyRenameKeepsContentOnUncRoot() throws IOException {
        // given
        String name = storage.createDir("", "Contract", Set.of());
        byte[] bytes = "case".getBytes(StandardCharsets.UTF_8);
        storage.store(upload("a.txt", bytes), name, "a.txt");

        // when
        String renamed = storage.renameDir("", name, "contract", Set.of());

        // then
        assertEquals("contract", renamed);
        try (Stream<Path> s = Files.list(storage.root())) {
            assertEquals(List.of("contract"), s.map(p -> p.getFileName().toString()).toList());
        }
        try (var in = storage.open("contract/a.txt")) {
            assertArrayEquals(bytes, in.readAllBytes());
        }
    }
}
