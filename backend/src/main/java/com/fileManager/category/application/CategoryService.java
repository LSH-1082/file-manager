package com.fileManager.category.application;

import com.fileManager.category.domain.Category;
import com.fileManager.category.domain.CategoryDeletingEvent;
import com.fileManager.category.domain.CategoryRenamedEvent;
import com.fileManager.category.domain.CategoryRepository;
import com.fileManager.file.application.StoredFileService;
import com.fileManager.file.domain.FileStorage;
import com.fileManager.file.domain.StorageLayout;
import com.fileManager.global.error.ApiException;
import com.fileManager.global.util.Ids;
import com.fileManager.global.util.Texts;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카테고리와 그 저장소 폴더. 이름 변경·삭제를 문서·자료 쪽에 알릴 때는 이 도메인이 그쪽을 import하지 않도록
 * 같은 트랜잭션 안의 이벤트(CategoryRenamedEvent, CategoryDeletingEvent)를 쓴다.
 */
@Service
@Transactional
public class CategoryService {
    static final String NOT_FOUND = "카테고리를 찾을 수 없습니다.";
    static final String NAME_EMPTY = "이름을 입력하세요.";

    private final CategoryRepository categories;
    private final StoredFileService files;
    private final FileStorage storage;
    private final ApplicationEventPublisher events;

    CategoryService(CategoryRepository categories, StoredFileService files, FileStorage storage,
                    ApplicationEventPublisher events) {
        this.categories = categories;
        this.files = files;
        this.storage = storage;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<Category> list() {
        return categories.findAllByOrderByCreatedAtAscIdAsc();
    }

    public Category create(String name) {
        return createEntity(name);
    }

    /** 문서 분류 지정에서 새 카테고리를 만들 때 쓴다. 같은 이름 거절 여부는 부르는 쪽이 정한다. */
    public Category createForAssign(String name) {
        return createEntity(name);
    }

    /** 카테고리를 잠그고 돌려준다. 없으면 404. */
    public Category findLocked(UUID id) {
        return categories.findLockedById(id).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }

    /** 잠그지 않고 읽는다. 없으면 404. */
    @Transactional(readOnly = true)
    public Category requireById(UUID id) {
        return categories.findById(id).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }

    public Category update(String id, String name) {
        if (name == null) throw ApiException.format();
        Category c = findLocked(Ids.parse(id, NOT_FOUND));
        String trimmed = nonEmpty(name);
        boolean renamed = !trimmed.equals(c.getName());
        String oldDir = c.getDirName();
        c.update(trimmed);
        String newDir = oldDir;
        if (renamed) {
            newDir = storage.renameDir("", oldDir, trimmed, StorageLayout.ROOT_RESERVED);
            c.moveDir(newDir);
        }
        if (!newDir.equals(oldDir)) {
            categories.flush();
            files.moveKeys(oldDir, newDir);
        }
        // 이름이 그대로여도알린다: 옛 동작대로 문서 제목·updatedAt을 항상 갱신해야 한다(step 3 리스너)
        events.publishEvent(new CategoryRenamedEvent(c.getId(), trimmed, oldDir, newDir));
        return c;
    }

    /** 숨김 여부만 바꾼다. 없으면 404. */
    public Category setHidden(String id, boolean hidden) {
        Category c = findLocked(Ids.parse(id, NOT_FOUND));
        c.setHidden(hidden);
        return c;
    }

    /** 없는 id도 UUID가 아닌 값도 조용히 끝난다(204). */
    public void delete(String id) {
        UUID uuid = Ids.parseOrNull(id);
        if (uuid == null) return;
        Category c = categories.findLockedById(uuid).orElse(null);
        if (c == null) return;
        String dir = c.getDirName();
        events.publishEvent(new CategoryDeletingEvent(uuid, dir));
        categories.delete(c);
        categories.flush();
        storage.deleteDirAfterCommit(dir);
    }

    private Category createEntity(String name) {
        if (name == null) throw ApiException.format();
        String trimmed = nonEmpty(name);
        String dir = storage.createDir("", trimmed, StorageLayout.ROOT_RESERVED);
        return categories.save(new Category(trimmed, dir));
    }

    private static String nonEmpty(String name) {
        String trimmed = Texts.trim(name);
        if (trimmed.isEmpty()) throw ApiException.badRequest(NAME_EMPTY);
        return trimmed;
    }
}
