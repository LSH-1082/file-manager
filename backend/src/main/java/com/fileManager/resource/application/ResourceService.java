package com.fileManager.resource.application;

import com.fileManager.category.application.CategoryService;
import com.fileManager.category.domain.Category;
import com.fileManager.file.application.StoredFileService;
import com.fileManager.file.domain.FileUpload;
import com.fileManager.file.domain.StorageLayout;
import com.fileManager.file.domain.StoredFile;
import com.fileManager.global.error.ApiException;
import com.fileManager.global.util.Ids;
import com.fileManager.resource.domain.CategoryResource;
import com.fileManager.resource.domain.CategoryResourceRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카테고리 양식·참고자료. 지연 연관(category, file)을 읽는 응답은 view 함수를 트랜잭션 안에서 호출해 만든다.
 */
@Service
@Transactional
public class ResourceService {
    private static final String CATEGORY_NOT_FOUND = "카테고리를 찾을 수 없습니다.";
    private static final String NOT_FOUND = "자료를 찾을 수 없습니다.";
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of("exe");
    private static final String EXE_BLOCKED = ".exe 파일은 올릴 수 없습니다.";
    private static final Set<String> KINDS = Set.of("template", "reference", "etc");

    private final CategoryResourceRepository resources;
    private final CategoryService categories;
    private final StoredFileService files;

    ResourceService(CategoryResourceRepository resources, CategoryService categories, StoredFileService files) {
        this.resources = resources;
        this.categories = categories;
        this.files = files;
    }

    @Transactional(readOnly = true)
    public <R> List<R> list(Function<? super CategoryResource, R> view) {
        return resources.findAllByOrderByCreatedAtAscIdAsc().stream().<R>map(view).toList();
    }

    public <R> R create(String categoryId, List<FileUpload> parts, String kind, String title, String note,
                        Function<? super CategoryResource, R> view) {
        FileUpload file = StoredFileService.single(parts);
        if (file == null || kind == null || title == null) throw ApiException.format();
        Category category = categories.findLocked(Ids.parse(categoryId, CATEGORY_NOT_FOUND));
        if (!KINDS.contains(kind)) throw ApiException.format();
        if (StoredFileService.hasExtension(file, BLOCKED_EXTENSIONS)) throw ApiException.badRequest(EXE_BLOCKED);
        StoredFile stored = files.insert(file, null, dir(category));
        CategoryResource r = resources.save(new CategoryResource(category, kind, title, note == null ? "" : note, stored));
        return view.apply(r);
    }

    public <R> R update(String id, List<FileUpload> parts, String kind, String title, String note,
                        Function<? super CategoryResource, R> view) {
        FileUpload file = StoredFileService.single(parts);
        if (kind == null || title == null) throw ApiException.format();
        UUID uuid = Ids.parse(id, NOT_FOUND);
        CategoryResource r = resources.findLockedById(uuid).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        if (!KINDS.contains(kind)) throw ApiException.format();
        if (file != null && StoredFileService.hasExtension(file, BLOCKED_EXTENSIONS)) {
            throw ApiException.badRequest(EXE_BLOCKED);
        }
        StoredFile old = r.getFile();
        StoredFile next = file == null ? old : files.insert(file, null, dir(r.getCategory()));
        r.update(kind, title, note == null ? "" : note, next);
        if (file != null) {
            resources.flush();
            files.delete(List.of(old));
        }
        return view.apply(r);
    }

    /** 없는 id도 UUID가 아닌 값도 조용히 끝난다(204). */
    public void delete(String id) {
        UUID uuid = Ids.parseOrNull(id);
        if (uuid == null) return;
        CategoryResource r = resources.findLockedById(uuid).orElse(null);
        if (r == null) return;
        StoredFile file = r.getFile();
        resources.delete(r);
        resources.flush();
        files.delete(List.of(file));
    }

    private static String dir(Category category) {
        return StorageLayout.join(category.getDirName(), StorageLayout.RESOURCE_DIR);
    }
}
