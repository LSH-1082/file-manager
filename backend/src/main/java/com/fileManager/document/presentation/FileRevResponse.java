package com.fileManager.document.presentation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fileManager.file.domain.StoredFile;
import com.fileManager.global.util.IsoTimes;

/**
 * 버전의 파일 한 건(docs/api 5.1 FileRev). fileName은 표시 이름(버전 파일은 {제목} {버전}[ 수정대조표].{확장자}),
 * downloadName은 저장 이름({표시 이름의 확장자 앞}_{yyyyMMdd}_{작업자}.{확장자}, 항상 있음). revision(수정대조표)은 수정본에만 있고 그 안에는 다시 revision이 없다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FileRevResponse(String id, String fileName, String downloadName, long fileSize, String mimeType, String uploadedAt,
                              String uploadedBy, FileRevResponse revision) {
    static FileRevResponse of(StoredFile f, FileRevResponse revision) {
        return new FileRevResponse(f.getId().toString(), f.getFileName(), f.downloadName(), f.getSizeBytes(), f.getMimeType(),
                IsoTimes.format(f.getUploadedAt()), f.getUploadedBy(), revision);
    }
}
