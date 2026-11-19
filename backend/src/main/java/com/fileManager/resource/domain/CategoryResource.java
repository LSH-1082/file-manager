package com.fileManager.resource.domain;

import com.fileManager.category.domain.Category;
import com.fileManager.file.domain.StoredFile;
import com.fileManager.global.util.IsoTimes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 카테고리에 딸린 양식·참고자료. 카테고리를 지우면 함께 지운다. */
@Entity
@Table(name = "category_resources")
public class CategoryResource {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 20)
    private String kind;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String note;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false, unique = true)
    private StoredFile file;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CategoryResource() {}

    public CategoryResource(Category category, String kind, String title, String note, StoredFile file) {
        this.category = category;
        this.kind = kind;
        this.title = title;
        this.note = note;
        this.file = file;
        this.createdAt = IsoTimes.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String kind, String title, String note, StoredFile file) {
        this.kind = kind;
        this.title = title;
        this.note = note;
        this.file = file;
        this.updatedAt = IsoTimes.now();
    }

    public UUID getId() { return id; }
    public Category getCategory() { return category; }
    public String getKind() { return kind; }
    public String getTitle() { return title; }
    public String getNote() { return note; }
    public StoredFile getFile() { return file; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
