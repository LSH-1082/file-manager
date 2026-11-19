package com.fileManager.file.application;

import com.fileManager.file.domain.FileStorage;
import com.fileManager.file.domain.FileUpload;
import com.fileManager.file.domain.StoredFile;
import com.fileManager.file.domain.StoredFileRepository;
import com.fileManager.global.error.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** stored_files 행의 생성·삭제·조회와 키 이동. 본문은 FileStorage가 맡는다. 어떤 행이 쓰이는지는 소유 도메인이 안다. */
@Service
@Transactional
public class StoredFileService {
    private final StoredFileRepository files;
    private final FileStorage storage;
    private final List<StoredFileAlias> aliases;

    StoredFileService(StoredFileRepository files, FileStorage storage, List<StoredFileAlias> aliases) {
        this.files = files;
        this.storage = storage;
        this.aliases = aliases;
    }

    /** 모든 검사를 통과한 뒤 호출한다. 본문을 dir/{원래 파일명}으로 저장하고 행을 만든다. */
    public StoredFile insert(FileUpload file, String uploadedBy, String dir) {
        String name = baseName(file.originalName());
        String key = storage.store(file, dir, name);
        String mime = file.contentType();
        if (mime == null || mime.isBlank()) mime = "application/octet-stream";
        return files.save(new StoredFile(key, name, mime, file.size(), uploadedBy));
    }

    /** 본문을 dir/{storedName}으로 저장하고, 행의 화면 이름은 displayName, 올린 시각은 uploadedAt으로 둔다. */
    public StoredFile insert(FileUpload file, String uploadedBy, String dir, String displayName, String storedName,
            Instant uploadedAt) {
        String key = storage.store(file, dir, storedName);
        String mime = file.contentType();
        if (mime == null || mime.isBlank()) mime = "application/octet-stream";
        return files.save(new StoredFile(key, displayName, mime, file.size(), uploadedBy, uploadedAt));
    }

    /** 같은 폴더 안에서 파일 이름을 storedName으로, 화면 이름을 displayName으로 바꾼다. 달라지는 것이 없으면 아무것도 안 한다. */
    public void rename(StoredFile f, String displayName, String storedName) {
        String newKey = storage.renameFile(f.getStorageKey(), storedName);
        if (newKey.equals(f.getStorageKey()) && displayName.equals(f.getFileName())) return;
        f.rename(displayName, newKey);
    }

    /** 행을 지우고 커밋 뒤 본문도 지운다. 호출하는 쪽이 더 이상 참조하지 않는 파일만 넘긴다. */
    public void delete(Collection<StoredFile> targets) {
        if (targets.isEmpty()) return;
        List<String> keys = targets.stream().map(StoredFile::getStorageKey).toList();
        files.deleteAll(targets);
        files.flush();
        storage.deleteAfterCommit(keys);
    }

    /**
     * 폴더 from을 to로 옮겼을 때 그 아래 파일들의 키 앞부분을 바꾼다. 문자열 자르기는 자바에서 하므로
     * 보충 문자(이모지 등)가 있는 폴더 이름도 DB 문자 세기와 무관하다. 바뀐 행은 flush까지 한다.
     */
    public void moveKeys(String from, String to) {
        if (from.equals(to)) return;
        String pattern = from.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "/%";
        for (StoredFile f : files.findByKeyPattern(pattern)) {
            f.setStorageKey(to + f.getStorageKey().substring(from.length()));
        }
        files.flush();
    }

    /** 저장 루트의 절대 경로. */
    @Transactional(readOnly = true)
    public String storageRoot() {
        return storage.rootPath();
    }

    /** 다른 도메인의 별칭 id면 그 파일, 아니면 stored_files.id (docs/api 4절). 다운로드가 쓴다. */
    @Transactional(readOnly = true)
    public Optional<StoredFile> find(UUID id) {
        for (StoredFileAlias a : aliases) {
            Optional<StoredFile> f = a.resolve(id);
            if (f.isPresent()) return f;
        }
        return files.findById(id);
    }

    /** 다운로드용: 파일 본문을 연다. 본문이 없으면 NoSuchFileException. */
    public InputStream open(StoredFile f) throws IOException {
        return storage.open(f.getStorageKey());
    }

    /** 같은 이름의 파일 파트가 둘 이상이면 400, 없으면 null. */
    public static FileUpload single(List<FileUpload> parts) {
        if (parts == null || parts.isEmpty()) return null;
        if (parts.size() > 1) throw ApiException.format();
        return parts.get(0);
    }

    /** 업로드 파일 이름에서 경로 부분을 떼고 이름만 남긴다. */
    public static String baseName(String original) {
        String n = original == null ? "" : original.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1);
        return n.isEmpty() ? "file" : n;
    }

    /**
     * 업로드 파일 이름(경로를 뗀 이름)의 확장자가 extensions(점 없는 소문자) 중 하나인지. 확장자는 마지막 . 뒤를
     * 대소문자 무시로 본다. .이 없으면 확장자가 없다. 판정은 여기 한 곳에만 둔다.
     */
    public static boolean hasExtension(FileUpload file, Set<String> extensions) {
        String name = baseName(file.originalName());
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        return extensions.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
