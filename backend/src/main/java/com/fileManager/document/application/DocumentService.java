package com.fileManager.document.application;

import com.fileManager.category.application.CategoryService;
import com.fileManager.category.domain.Category;
import com.fileManager.category.domain.CategoryRepository;
import com.fileManager.document.domain.Document;
import com.fileManager.document.domain.DocumentRepository;
import com.fileManager.document.domain.DocumentVersion;
import com.fileManager.document.domain.DocumentVersionRepository;
import com.fileManager.document.domain.VersionFile;
import com.fileManager.document.domain.VersionFileRepository;
import com.fileManager.file.application.StoredFileService;
import com.fileManager.file.domain.FileStorage;
import com.fileManager.file.domain.FileUpload;
import com.fileManager.file.domain.StorageLayout;
import com.fileManager.file.domain.StorageLayout.VersionFileKind;
import com.fileManager.file.domain.StoredFile;
import com.fileManager.global.error.ApiException;
import com.fileManager.global.util.Ids;
import com.fileManager.global.util.IsoTimes;
import com.fileManager.global.util.Texts;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.fileManager.document.application.VersionRules.DOC_NOT_FOUND;
import static com.fileManager.document.application.VersionRules.VERSION_NOT_FOUND;

/**
 * 문서·버전·파일. 응답이 지연 연관(버전, 파일, 카테고리)을 읽으므로 응답을 만드는 메서드는 view 함수를
 * 트랜잭션 안에서 적용한다. 저장소 폴더는 {문서 폴더}/{버전 폴더}/ 이고 원본은 그 안에 둔다. 수정본 n번과 그
 * 수정대조표는 {버전 폴더}/edit{n}/ 안에 둔다. 버전 파일의 저장 이름은 '{제목} {버전}[ 수정대조표]_{yyyyMMdd}_{작업자}.{확장자}'(거절 검토본은
 * '{제목} {버전} 검토본_{yyyyMMdd}_{검토자}.{확장자}'), 표시 이름은 일자·작업자(검토자)를 뺀 것이다(규칙은 StorageLayout.versionFileName, 이름 추종은 VersionFileNames).
 * 잠금 순서는 categories -> documents다.
 */
@Service
@Transactional
public class DocumentService {
    private static final String CATEGORY_NOT_FOUND = "카테고리를 찾을 수 없습니다.";
    private static final String CATEGORY_HAS_DOC = "이미 문서가 있는 카테고리입니다.";
    private static final String PAIR = "수정본은 수정대조표와 함께 올려야 등록됩니다.";

    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final VersionFileRepository versionFiles;
    private final CategoryRepository categoryRepository;
    private final CategoryService categories;
    private final StoredFileService files;
    private final FileStorage storage;
    private final VersionFileNames names;
    private final ReviewCopies reviewCopies;

    DocumentService(DocumentRepository documents, DocumentVersionRepository versions,
                    VersionFileRepository versionFiles, CategoryRepository categoryRepository,
                    CategoryService categories, StoredFileService files, FileStorage storage,
                    VersionFileNames names, ReviewCopies reviewCopies) {
        this.documents = documents;
        this.versions = versions;
        this.versionFiles = versionFiles;
        this.categoryRepository = categoryRepository;
        this.categories = categories;
        this.files = files;
        this.storage = storage;
        this.names = names;
        this.reviewCopies = reviewCopies;
    }

    // 응답 조립이 여러 SELECT로 나뉘므로 documents 행을 공유 잠금으로 읽어, 진행 중인 변경(거절 취소의 검토본 삭제, 수정본 삭제)이
    // 커밋된 뒤의 상태를 읽는다. 잠금 읽기는 MySQL의 READ ONLY 트랜잭션에서 거절되므로 readOnly를 쓰지 않는다
    @Transactional
    public <R> List<R> list(Function<? super Document, R> view) {
        return documents.findAllLockedForList().stream().<R>map(view).toList();
    }

    /** 문서를 잠그고 돌려준다. id가 UUID가 아니거나 없으면 404. 단계 서비스도 쓴다. */
    public Document lockDoc(String id) {
        return documents.findLockedById(Ids.parse(id, DOC_NOT_FOUND))
                .orElseThrow(() -> ApiException.notFound(DOC_NOT_FOUND));
    }

    public <R> R create(List<FileUpload> parts, String categoryId, String versionName, String worker,
                        Function<? super Document, R> view) {
        FileUpload file = StoredFileService.single(parts);
        if (file == null || categoryId == null || worker == null) throw ApiException.format();
        String label = VersionRules.versionLabel(1, versionName);
        String by = VersionRules.worker(worker);
        VersionRules.requireDocFile(file);
        Category category = categories.findLocked(Ids.parse(categoryId, CATEGORY_NOT_FOUND));
        if (documents.existsByCategoryId(category.getId())) throw ApiException.conflict(CATEGORY_HAS_DOC);
        Document doc = new Document(category);
        String dir = storage.createDir(doc.getDirName(), label, StorageLayout.CATEGORY_RESERVED);
        Instant now = IsoTimes.now();
        var n = StorageLayout.versionFileName(doc.getTitle(), label, VersionFileKind.FILE,
                StorageLayout.extension(StoredFileService.baseName(file.originalName())), now, by);
        StoredFile original = files.insert(file, by, StorageLayout.join(doc.getDirName(), dir), n.display(), n.stored(), now);
        doc.addVersion(1, storedName(1, label), label, dir, by).addFile(0, original, null);
        documents.save(doc);
        return done(doc, view);
    }

    public <R> R assignCategory(String docId, String categoryId, String newName, boolean hasNew,
                                Function<? super Document, R> view) {
        if ((categoryId != null) == hasNew) throw ApiException.format();
        if (hasNew && newName == null) throw ApiException.format();
        UUID categoryUuid = Ids.parseOrNull(categoryId);
        // 카테고리를 먼저 잠근다(categories -> documents). 없다는 응답은 문서 검사 뒤에 한다
        Optional<Category> existing = categoryUuid == null ? Optional.empty()
                : categoryRepository.findLockedById(categoryUuid);
        Document doc = lockDoc(docId);
        if (doc.getCategory() != null) throw ApiException.conflict("이미 분류가 있는 문서는 옮길 수 없습니다.");
        Category category;
        if (!hasNew) {
            category = existing.orElseThrow(() -> ApiException.notFound(CATEGORY_NOT_FOUND));
            if (documents.existsByCategoryId(category.getId())) throw ApiException.conflict(CATEGORY_HAS_DOC);
        } else {
            String name = Texts.trim(newName);
            if (name.isEmpty()) throw ApiException.badRequest("이름을 입력하세요.");
            if (categoryRepository.existsByName(name)) throw ApiException.conflict("같은 이름의 카테고리가 있습니다.");
            category = categories.createForAssign(name);
        }
        moveVersionDirs(doc, category.getDirName());
        String oldDir = doc.getDirName();
        doc.classify(category);
        names.renameAll(doc);
        if (!oldDir.equals(doc.getDirName())) storage.pruneDirAfterCommit(oldDir);
        return done(doc, view);
    }

    public <R> R uploadRevision(String docId, List<FileUpload> fileParts, List<FileUpload> revisionParts,
                                String worker, Function<? super Document, R> view) {
        FileUpload file = StoredFileService.single(fileParts);
        FileUpload revision = StoredFileService.single(revisionParts);
        if (worker == null) throw ApiException.format();
        String by = VersionRules.worker(worker);
        Document doc = lockDoc(docId);
        DocumentVersion latest = doc.latest();
        VersionRules.requireEditable(latest);
        if (file == null || revision == null) throw ApiException.badRequest(PAIR);
        VersionRules.requireDocFile(file);
        VersionRules.requireDocFile(revision);
        int n = latest.current().getSeq() + 1;
        String versionKey = StorageLayout.join(doc.getDirName(), latest.getDirName());
        String editKey = StorageLayout.join(versionKey, storage.freeName(versionKey, StorageLayout.editDir(n), Set.of()));
        Instant now = IsoTimes.now();
        var bodyName = StorageLayout.versionFileName(doc.getTitle(), latest.getLabel(), VersionFileKind.FILE,
                StorageLayout.extension(StoredFileService.baseName(file.originalName())), now, by);
        var diffName = StorageLayout.versionFileName(doc.getTitle(), latest.getLabel(), VersionFileKind.REVISION_SHEET,
                StorageLayout.extension(StoredFileService.baseName(revision.originalName())), now, by);
        StoredFile body = files.insert(file, by, editKey, bodyName.display(), bodyName.stored(), now);
        StoredFile diff = files.insert(revision, by, editKey, diffName.display(), diffName.stored(), now);
        latest.addFile(n, body, diff);
        doc.touch();
        return done(doc, view);
    }

    public <R> R deleteRevision(String docId, String fileId, Function<? super Document, R> view) {
        Document doc = lockDoc(docId);
        DocumentVersion latest = doc.latest();
        VersionRules.requireEditable(latest);
        UUID target = Ids.parseOrNull(fileId);
        if (latest.original().fileId().equals(target)) throw ApiException.conflict("원본은 삭제할 수 없습니다.");
        VersionFile current = latest.current();
        if (latest.getFiles().size() < 2 || !current.fileId().equals(target)) {
            throw ApiException.conflict("가장 최신 수정본만 삭제할 수 있습니다.");
        }
        latest.removeFile(current);
        List<StoredFile> copies = latest.detachReviewCopiesOf(current.fileId());
        doc.touch();
        String versionKey = StorageLayout.join(doc.getDirName(), latest.getDirName());
        String fileKey = current.getFile().getStorageKey();
        String editKey = fileKey.substring(0, fileKey.lastIndexOf('/'));
        releaseUnreferenced(List.of(current.getFile(), current.getRevisionFile()));
        reviewCopies.delete(copies);
        // 파일 삭제가 먼저 등록되므로 커밋 뒤 파일이 지워진 다음 빈 edit 폴더가 지워진다
        if (!editKey.equals(versionKey)) storage.pruneDirAfterCommit(editKey);
        return done(doc, view);
    }

    public <R> R renameVersion(String docId, String versionId, String name, Function<? super Document, R> view) {
        if (name == null) throw ApiException.format();
        Document doc = lockDoc(docId);
        DocumentVersion version = version(doc, versionId);
        if (version.sent()) throw ApiException.conflict("발송된 버전의 이름은 바꿀 수 없습니다.");
        String label = VersionRules.versionLabel(version.getNumber(), name);
        requireUniqueLabel(doc, version, label);
        String oldDir = version.getDirName();
        String newDir = storage.renameDir(doc.getDirName(), oldDir, label, StorageLayout.CATEGORY_RESERVED);
        version.rename(storedName(version.getNumber(), label), label);
        if (!newDir.equals(oldDir)) {
            version.moveDir(newDir);
            documents.flush();
            files.moveKeys(StorageLayout.join(doc.getDirName(), oldDir), StorageLayout.join(doc.getDirName(), newDir));
        }
        names.renameOwned(doc, version);
        doc.touch();
        return done(doc, view);
    }

    public <R> R startVersion(String docId, String versionName, String worker, Function<? super Document, R> view) {
        if (versionName == null || worker == null) throw ApiException.format();
        String by = VersionRules.worker(worker);
        Document doc = lockDoc(docId);
        DocumentVersion latest = doc.latest();
        if (!latest.sent()) throw ApiException.conflict("발송 전인 버전이 있습니다. 발송 후에 새 버전을 시작할 수 있습니다.");
        int number = latest.getNumber() + 1;
        String label = VersionRules.versionLabel(number, versionName);
        requireUniqueLabel(doc, null, label);
        String dir = storage.createDir(doc.getDirName(), label, StorageLayout.CATEGORY_RESERVED);
        // 원본은 직전 제출본의 stored_files 행을 함께 쓴다(복사하지 않는다)
        doc.addVersion(number, storedName(number, label), label, dir, by).addFile(0, latest.current().getFile(), null);
        doc.touch();
        return done(doc, view);
    }

    /** 문서가 지워지면(유일한 v1을 지움) view를 부르지 않고 null을 돌려준다. */
    public <R> R deleteVersion(String docId, String versionId, Function<? super Document, R> view) {
        Document doc = lockDoc(docId);
        DocumentVersion version = version(doc, versionId);
        if (version.sent()) throw ApiException.conflict("발송된 버전은 삭제할 수 없습니다.");
        if (!doc.latest().getId().equals(version.getId())) throw ApiException.conflict("최신 버전만 삭제할 수 있습니다.");
        List<StoredFile> copies = version.reviewCopies();
        Set<StoredFile> candidates = new LinkedHashSet<>();
        for (VersionFile f : version.getFiles()) {
            candidates.add(f.getFile());
            if (f.getRevisionFile() != null) candidates.add(f.getRevisionFile());
        }
        boolean removeDoc = version.getNumber() == 1 && doc.getVersions().size() == 1;
        boolean unclassified = doc.getCategory() == null;
        String versionKey = StorageLayout.join(doc.getDirName(), version.getDirName());
        String docKey = doc.getDirName();
        if (removeDoc) {
            documents.delete(doc);
        } else {
            doc.removeVersion(version);
            doc.touch();
        }
        releaseUnreferenced(candidates);
        reviewCopies.delete(copies);
        // 미분류 문서는 _미분류/{문서 폴더}까지 정리한다. 분류된 문서의 카테고리 폴더는 남긴다
        storage.deleteDirAfterCommit(removeDoc && unclassified ? docKey : versionKey);
        return removeDoc ? null : done(doc, view);
    }

    // ---- 내부 ----

    /** 새로 만든 엔티티의 id가 채워지도록 flush한 뒤 응답을 만든다. DB 제약 위반도 여기서 드러난다. */
    private <R> R done(Document doc, Function<? super Document, R> view) {
        documents.flush();
        return view.apply(doc);
    }

    private DocumentVersion version(Document doc, String versionId) {
        UUID id = Ids.parseOrNull(versionId);
        return doc.getVersions().stream().filter(v -> v.getId().equals(id)).findFirst()
                .orElseThrow(() -> ApiException.notFound(VERSION_NOT_FOUND));
    }

    /** 같은 문서의 다른 버전이 같은 화면 이름을 쓰는지. self는 이름을 바꾸는 버전(새 버전이면 null). */
    private static void requireUniqueLabel(Document doc, DocumentVersion self, String label) {
        boolean taken = doc.getVersions().stream().anyMatch(v -> v != self && v.getLabel().equals(label));
        if (taken) throw ApiException.conflict("이 문서에 같은 이름의 버전이 있습니다.");
    }

    /** 이름이 v{번호}와 같으면 이름 없음(null)으로 저장한다. 응답에서 name 키가 빠진다. */
    private static String storedName(int number, String label) {
        return label.equals("v" + number) ? null : label;
    }

    /** 분류 지정: 버전 폴더를 카테고리 폴더 아래로 옮기고 파일 키를 따라 바꾼다. */
    private void moveVersionDirs(Document doc, String categoryDir) {
        String docDir = doc.getDirName();
        for (DocumentVersion v : doc.getVersions()) {
            String name = storage.freeName(categoryDir, v.getDirName(), StorageLayout.CATEGORY_RESERVED);
            String from = StorageLayout.join(docDir, v.getDirName());
            String to = StorageLayout.join(categoryDir, name);
            storage.moveDir(from, to);
            files.moveKeys(from, to);
            v.moveDir(name);
        }
    }

    /**
     * 버전 파일 행이 지워진 뒤, 더 이상 아무 버전도 쓰지 않는 파일만 지운다. 새 버전의 원본처럼 다른 버전이 함께 쓰는
     * 파일은 남긴다. 결정 기록이 가리키던 파일이 지워지면 그 참조를 비운다.
     */
    private void releaseUnreferenced(java.util.Collection<StoredFile> candidates) {
        documents.flush();
        List<StoredFile> orphans = new ArrayList<>();
        for (StoredFile f : candidates) {
            if (f != null && versionFiles.countReferences(f.getId()) == 0) orphans.add(f);
        }
        if (orphans.isEmpty()) return;
        List<UUID> ids = orphans.stream().map(StoredFile::getId).toList();
        for (DocumentVersion v : versions.findByReviewFiles(ids)) ids.forEach(v::forgetReviewFile);
        files.delete(orphans);
    }
}
