package com.fileManager.file.domain;

import com.fileManager.global.util.IsoTimes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 업로드된 파일 한 건. 본문은 파일 저장소의 storageKey(루트 기준 상대 경로)에 있다. */
@Entity
@Table(name = "stored_files")
public class StoredFile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "storage_key", nullable = false, unique = true, length = 700)
    private String storageKey;

    /** 화면 이름. 버전 파일은 '{제목} {버전}[ 수정대조표].{확장자}', 양식·참고자료는 올린 이름. */
    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "uploaded_by", length = 5)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected StoredFile() {}

    public StoredFile(String storageKey, String fileName, String mimeType, long sizeBytes, String uploadedBy) {
        this(storageKey, fileName, mimeType, sizeBytes, uploadedBy, IsoTimes.now());
    }

    public StoredFile(String storageKey, String fileName, String mimeType, long sizeBytes, String uploadedBy,
            Instant uploadedAt) {
        this.storageKey = storageKey;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }

    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }

    /** 화면 이름과 저장 키를 함께 바꾼다(버전 파일 이름이 제목·라벨을 따라갈 때). */
    public void rename(String fileName, String storageKey) {
        this.fileName = fileName;
        this.storageKey = storageKey;
    }

    public UUID getId() { return id; }
    public String getStorageKey() { return storageKey; }
    public String getFileName() { return fileName; }
    public String getMimeType() { return mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getUploadedBy() { return uploadedBy; }
    public Instant getUploadedAt() { return uploadedAt; }

    /** 다운로드 이름: 저장 이름(storageKey의 마지막 조각). */
    public String downloadName() { return storageKey.substring(storageKey.lastIndexOf('/') + 1); }
}
