package com.fileManager.document.domain;

import com.fileManager.file.domain.StoredFile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

/** 버전의 파일. seq 0 = 원본(대조표 없음), seq 1.. = 수정본 n(수정대조표 필수). */
@Entity
@Table(name = "version_files",
        uniqueConstraints = @UniqueConstraint(name = "uq_vfiles_seq", columnNames = {"version_id", "seq"}))
public class VersionFile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private DocumentVersion version;

    @Column(nullable = false)
    private int seq;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false)
    private StoredFile file;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revision_file_id", unique = true)
    private StoredFile revisionFile;

    protected VersionFile() {}

    VersionFile(DocumentVersion version, int seq, StoredFile file, StoredFile revisionFile) {
        if ((seq == 0) != (revisionFile == null)) throw new IllegalArgumentException("원본만 대조표가 없다");
        this.version = version;
        this.seq = seq;
        this.file = file;
        this.revisionFile = revisionFile;
    }

    public int getSeq() { return seq; }
    public StoredFile getFile() { return file; }
    public StoredFile getRevisionFile() { return revisionFile; }
    public UUID fileId() { return file.getId(); }
    public UUID revisionId() { return revisionFile == null ? null : revisionFile.getId(); }
}
