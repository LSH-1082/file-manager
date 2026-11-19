package com.fileManager.document.domain;

import com.fileManager.category.domain.Category;
import com.fileManager.global.util.IsoTimes;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 문서. 카테고리 하나 = 문서 하나(category_id UNIQUE). 카테고리가 없으면 미분류.
 * dirName은 버전 폴더들이 들어 있는 폴더의 저장소 키다. 분류된 동안은 카테고리 폴더 이름과 같고,
 * 카테고리를 지워 미분류가 되면 _미분류/{제목}으로 바뀐다(폴더를 옮기는 일은 document 서비스·리스너가 한다).
 * 버전 파일의 키는 {dirName}/{버전 dirName}/{파일 이름}이다.
 */
@Entity
@Table(name = "documents")
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", unique = true)
    private Category category;

    @Column(nullable = false)
    private String title;

    @Column(name = "dir_name", nullable = false)
    private String dirName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("number")
    private List<DocumentVersion> versions = new ArrayList<>();

    protected Document() {}

    public Document(Category category) {
        this.category = category;
        this.title = category.getName();
        this.dirName = category.getDirName();
        this.createdAt = IsoTimes.now();
        this.updatedAt = this.createdAt;
    }

    public DocumentVersion addVersion(int number, String name, String label, String dirName, String worker) {
        DocumentVersion v = new DocumentVersion(this, number, name, label, dirName, worker);
        versions.add(v);
        return v;
    }

    public void removeVersion(DocumentVersion v) { versions.remove(v); }

    /** 버전이 없는 문서는 없다(생성 때 v1을 같이 만든다). */
    public DocumentVersion latest() { return versions.get(versions.size() - 1); }

    public void touch() { this.updatedAt = IsoTimes.now(); }

    /** 분류 지정: 제목이 카테고리 이름이 되고, 문서 폴더가 카테고리 폴더가 된다(폴더 이동은 서비스가 먼저 한다). */
    public void classify(Category category) {
        this.category = category;
        this.title = category.getName();
        this.dirName = category.getDirName();
        touch();
    }

    /** 카테고리 수정: 제목과 문서 폴더를 카테고리를 따라간다. */
    public void followCategory(String title, String dirName) {
        this.title = title;
        this.dirName = dirName;
        touch();
    }

    /** 카테고리 삭제: 미분류가 된다. 제목은 그대로이고, 문서 폴더는 서비스가 옮긴 {_미분류/…} 키다. */
    public void unclassify(String dirName) {
        this.category = null;
        this.dirName = dirName;
    }

    public UUID getId() { return id; }
    public Category getCategory() { return category; }
    public String getTitle() { return title; }
    public String getDirName() { return dirName; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<DocumentVersion> getVersions() { return versions; }
}
