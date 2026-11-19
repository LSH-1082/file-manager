package com.fileManager.file.infrastructure;

import com.fileManager.file.domain.FileStorage;
import com.fileManager.file.domain.FileUpload;
import com.fileManager.file.domain.StorageLayout;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 로컬 디스크 파일 저장소(FileStorage 구현). 루트 아래 상대 경로(키, '/' 구분)로 다룬다. DB(stored_files.storage_key)에는 키만 둔다.
 * 쓰기·이동은 즉시 하고, 활성 트랜잭션이 롤백되면 되돌린다. 삭제는 커밋 뒤에 한다.
 * 롤백 동작은 트랜잭션마다 한 목록에 모아 등록의 역순(LIFO)으로 실행한다: 폴더 이름 변경 뒤 그 안 파일 이름 변경이
 * 함께 롤백돼도 파일을 먼저 되돌린다.
 * ponytail: 쓰기와 커밋 사이에 프로세스가 죽으면 고아 파일이 남는다. 필요해지면 키와 디스크를 대조하는 배치를 둔다.
 */
@Component
public class LocalFileStorage implements FileStorage {
    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);

    private final Path root;

    public LocalFileStorage(@Value("${filemanager.storage-root}") String root) throws IOException {
        this.root = Path.of(root).toAbsolutePath().normalize();
        Files.createDirectories(this.root);
        log.info("파일 저장소 루트: {}", this.root);
    }

    public Path root() { return root; }

    @Override
    public String rootPath() { return root.toString(); }

    /** 키를 루트 안 경로로 바꾼다. 루트 밖을 가리키면 거부한다. */
    public Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) throw new IllegalArgumentException("저장소 밖 경로: " + key);
        return p;
    }

    /** parent 아래에 이름이 겹치지 않는 폴더를 새로 만들고 그 이름을 돌려준다. 롤백되면 비어 있을 때 지운다. */
    public synchronized String createDir(String parent, String desired, Set<String> reserved) {
        Path dir = resolve(parent);
        String base = PathNames.safe(desired);
        try {
            Files.createDirectories(dir);
            for (int n = 1; ; n++) {
                String name = PathNames.candidate(base, n, false);
                if (isReserved(name, reserved)) continue;
                Path p = dir.resolve(name);
                try {
                    Files.createDirectory(p);
                } catch (FileAlreadyExistsException e) {
                    continue;
                }
                onRollback(() -> deleteIfEmpty(p));
                return name;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * parent 안의 current 폴더 이름을 desired로 바꾼다. 바뀐 이름을 돌려주며 바꿀 필요가 없으면 current 그대로.
     * 대상 이름이 이미 있으면 ' (n)'을 붙인다. 롤백되면 원래 이름으로 되돌린다.
     */
    public synchronized String renameDir(String parent, String current, String desired, Set<String> reserved) {
        String base = PathNames.safe(desired);
        if (base.equals(current)) return current;
        Path currentPath = resolve(StorageLayout.join(parent, current));
        String target = null;
        for (int n = 1; target == null; n++) {
            String name = PathNames.candidate(base, n, false);
            if (isReserved(name, reserved)) continue;
            if (name.equals(current)) {
                target = name; // 바꿀 필요가 없다
                continue;
            }
            Path p = resolve(StorageLayout.join(parent, name));
            // 없으면 일반 이동. 있어도 실제로 같은 폴더(대소문자만 다른 자기 자신)일 때만 쓰고, 아니면 다음 후보
            if (!Files.exists(p) || sameFolder(p, currentPath)) target = name;
        }
        if (target.equals(current)) return current;
        String from = StorageLayout.join(parent, current);
        String to = StorageLayout.join(parent, target);
        if (sameFolder(resolve(to), currentPath)) {
            // 대소문자만 바뀐다: 대소문자를 구분하지 않는 FS는 한 번 옮기면 아무 일도 없으므로 임시 이름을 거친다
            caseOnlyRename(from, to);
        } else {
            moveDir(from, to);
        }
        return target;
    }

    private static boolean sameFolder(Path a, Path b) {
        try {
            return Files.isSameFile(a, b);
        } catch (IOException e) {
            return false;
        }
    }

    private void caseOnlyRename(String fromKey, String toKey) {
        Path from = resolve(fromKey);
        Path to = resolve(toKey);
        Path tmp = from.resolveSibling(from.getFileName() + ".tmp-" + System.nanoTime());
        try {
            Files.move(from, tmp);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            Files.move(tmp, to);
        } catch (IOException e) {
            try {
                Files.move(tmp, from);
            } catch (IOException back) {
                log.error("폴더·파일을 원래 이름으로 되돌리지 못했습니다. 임시 폴더: {} (원래 이름 {})", tmp, from, back);
                e.addSuppressed(back);
            }
            throw new UncheckedIOException(e);
        }
        onRollback(() -> {
            try {
                Files.move(to, tmp);
                Files.move(tmp, from);
            } catch (IOException e) {
                log.error("롤백 중 폴더·파일을 되돌리지 못했습니다: {} -> {} (임시 {})", to, from, tmp, e);
            }
        });
    }

    /** parent 안에서 desired와 겹치지 않는 이름(아직 없는 폴더)을 고른다. 만들지는 않는다. */
    public synchronized String freeName(String parent, String desired, Set<String> reserved) {
        String base = PathNames.safe(desired);
        for (int n = 1; ; n++) {
            String name = PathNames.candidate(base, n, false);
            if (!isReserved(name, reserved) && !Files.exists(resolve(StorageLayout.join(parent, name)))) return name;
        }
    }

    /** from 폴더를 to로 옮긴다(to의 부모는 만든다). from이 없으면 to 폴더만 만든다. 롤백되면 되돌린다. */
    public synchronized void moveDir(String fromKey, String toKey) {
        Path from = resolve(fromKey);
        Path to = resolve(toKey);
        try {
            Files.createDirectories(to.getParent());
            if (!Files.exists(from)) {
                Files.createDirectories(to);
                return;
            }
            Files.move(from, to);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        onRollback(() -> {
            try {
                Files.move(to, from);
            } catch (IOException e) {
                log.error("롤백 중 폴더를 되돌리지 못했습니다: {} -> {}", to, from, e);
            }
        });
    }

    /** 같은 폴더 안에서 key의 파일 이름을 desiredName으로 바꾸고 새 키를 돌려준다. 충돌은 ' (n)', 롤백되면 되돌린다. */
    public synchronized String renameFile(String key, String desiredName) {
        int slash = key.lastIndexOf('/');
        String dir = slash < 0 ? "" : key.substring(0, slash);
        String current = key.substring(slash + 1);
        String base = PathNames.safe(desiredName);
        if (base.equals(current)) return key;
        Path currentPath = resolve(key);
        if (!Files.exists(currentPath)) {
            throw new UncheckedIOException(new NoSuchFileException(currentPath.toString()));
        }
        String target = null;
        for (int n = 1; target == null; n++) {
            String name = PathNames.candidate(base, n, true);
            if (name.equals(current)) return key; // 이미 ' (n)'이 붙은 같은 이름
            Path p = resolve(StorageLayout.join(dir, name));
            if (!Files.exists(p) || sameFolder(p, currentPath)) target = name;
        }
        String toKey = StorageLayout.join(dir, target);
        Path to = resolve(toKey);
        if (sameFolder(to, currentPath)) {
            caseOnlyRename(key, toKey);
        } else {
            try {
                Files.move(currentPath, to);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            onRollback(() -> {
                try {
                    Files.move(to, currentPath);
                } catch (IOException e) {
                    log.error("롤백 중 파일을 되돌리지 못했습니다: {} -> {}", to, currentPath, e);
                }
            });
        }
        return toKey;
    }

    /** dir 아래에 원래 이름으로 저장하고 키를 돌려준다. 같은 이름이 있으면 ' (n)'. 롤백되면 지운다. */
    public synchronized String store(FileUpload upload, String dir, String originalName) {
        Path d = resolve(dir);
        String base = PathNames.safe(originalName);
        try {
            Files.createDirectories(d);
            for (int n = 1; ; n++) {
                String name = PathNames.candidate(base, n, true);
                Path p = d.resolve(name);
                try (OutputStream out = Files.newOutputStream(p, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                     InputStream in = upload.content().open()) {
                    in.transferTo(out);
                } catch (FileAlreadyExistsException e) {
                    continue;
                } catch (IOException e) {
                    Files.deleteIfExists(p);
                    throw e;
                }
                String key = StorageLayout.join(dir, name);
                onRollback(() -> deleteFiles(List.of(key)));
                return key;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    /** 커밋 뒤 파일을 지운다. 트랜잭션이 없으면 즉시. */
    public void deleteAfterCommit(Collection<String> keys) {
        if (keys.isEmpty()) return;
        List<String> copy = List.copyOf(keys);
        afterCommit(() -> deleteFiles(copy));
    }

    /** 커밋 뒤 폴더를 안의 파일까지 통째로 지운다(루트 자체는 거부). 트랜잭션이 없으면 즉시. */
    public void deleteDirAfterCommit(String dirKey) {
        Path dir = resolve(dirKey);
        if (dir.equals(root)) throw new IllegalArgumentException("저장소 루트는 지울 수 없습니다");
        afterCommit(() -> deleteTree(dir));
    }

    /** 커밋 뒤 폴더를 정리한다: 빈 하위 폴더를 지우고, 파일이 하나도 없으면 폴더 자체도 지운다. */
    public void pruneDirAfterCommit(String dirKey) {
        afterCommit(() -> prune(resolve(dirKey)));
    }

    private static boolean isReserved(String name, Set<String> reserved) {
        return reserved.stream().anyMatch(r -> r.equalsIgnoreCase(name));
    }

    private void deleteFiles(Collection<String> keys) {
        for (String k : keys) {
            try {
                Files.deleteIfExists(resolve(k));
            } catch (IOException e) {
                log.warn("파일을 지우지 못했습니다(고아 파일로 남음): {}", k, e);
            }
        }
    }

    private void deleteTree(Path dir) {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        } catch (IOException | UncheckedIOException e) {
            log.warn("폴더를 지우지 못했습니다(고아 파일로 남음): {}", dir, e);
        }
    }

    private void prune(Path dir) {
        if (!Files.isDirectory(dir) || dir.equals(root)) return;
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).toList()) {
                if (Files.isDirectory(p)) deleteIfEmpty(p);
            }
        } catch (IOException | UncheckedIOException e) {
            log.warn("폴더를 정리하지 못했습니다: {}", dir, e);
        }
    }

    private static void deleteIfEmpty(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (DirectoryNotEmptyException | NoSuchFileException ignored) {
            // 파일이 남아 있으면 둔다
        } catch (IOException e) {
            log.warn("폴더를 지우지 못했습니다: {}", p, e);
        }
    }

    /** 한 트랜잭션의 롤백 동작 목록. 롤백·미완료 때 등록의 역순으로 실행하고, 하나가 실패해도 나머지를 계속한다. */
    private static final class RollbackActions implements TransactionSynchronization {
        final Deque<Runnable> actions = new ArrayDeque<>();

        @Override
        public void afterCompletion(int status) {
            if (status == STATUS_COMMITTED) return;
            Runnable r;
            while ((r = actions.pollLast()) != null) {
                try {
                    r.run();
                } catch (RuntimeException e) {
                    log.error("롤백 동작을 실행하지 못했습니다", e);
                }
            }
        }
    }

    private static void onRollback(Runnable r) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        RollbackActions found = null;
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            if (s instanceof RollbackActions a) {
                found = a;
                break;
            }
        }
        if (found == null) {
            found = new RollbackActions();
            TransactionSynchronizationManager.registerSynchronization(found);
        }
        found.actions.addLast(r);
    }

    private static void afterCommit(Runnable r) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            r.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() { r.run(); }
        });
    }
}
