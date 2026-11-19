package com.fileManager.category.domain;

import com.fileManager.global.util.IsoTimes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 카테고리. 이름은 UNIQUE가 아니다(프런트와 같음). dirName은 저장소 루트 바로 아래의 카테고리 폴더 이름이다. */
@Entity
@Table(name = "categories")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "dir_name", nullable = false)
    private String dirName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Category() {}

    public Category(String name, String dirName) {
        this.name = name;
        this.dirName = dirName;
        this.createdAt = IsoTimes.now();
    }

    public void update(String name) {
        this.name = name;
    }

    public void moveDir(String dirName) { this.dirName = dirName; }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDirName() { return dirName; }
    public Instant getCreatedAt() { return createdAt; }
}
