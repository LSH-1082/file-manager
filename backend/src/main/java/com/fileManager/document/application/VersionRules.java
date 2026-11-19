package com.fileManager.document.application;

import com.fileManager.document.domain.DocumentVersion;
import com.fileManager.file.application.StoredFileService;
import com.fileManager.file.domain.FileUpload;
import com.fileManager.global.error.ApiException;
import com.fileManager.global.util.Texts;
import java.util.Map;
import java.util.Set;

/** 버전·단계 규칙의 공용 문구와 검사. 문구는 docs/api 2.3과 8절이 정본이다. */
final class VersionRules {
    static final Map<String, String> STAGE_LABEL = Map.of(
            "working", "작업중", "queued", "검토대기", "reviewing", "검토중", "ready", "발송대기", "sent", "발송");
    static final Map<String, String> CANCEL_PHRASE = Map.of(
            "workDone", "작업완료를 취소", "reviewStart", "검토를 취소", "approve", "승인을 취소",
            "reject", "거절을 취소", "send", "발송을 취소");
    static final String DOC_NOT_FOUND = "문서를 찾을 수 없습니다.";
    static final String VERSION_NOT_FOUND = "버전을 찾을 수 없습니다.";
    static final String SENT_EDIT = "발송된 버전은 수정할 수 없습니다. 새 버전을 시작하세요.";
    static final int NAME_MAX = 5;
    static final Set<String> DOC_FILE_EXTENSIONS = Set.of("hwpx", "pdf");
    static final String DOC_FILE_TYPE = ".hwpx 또는 .pdf 파일만 올릴 수 있습니다.";

    static final String REVIEW_COPY_REQUIRED = "거절하려면 검토본 파일을 올려야 합니다.";
    static final String REVIEWER_NOT_LISTED = "검토자는 검토 시작 때 적은 검토자 중에서 골라야 합니다.";

    private VersionRules() {}

    /** "이미 {단계} 단계입니다." */
    static ApiException already(String stage) {
        return ApiException.conflict("이미 " + STAGE_LABEL.get(stage) + " 단계입니다.");
    }

    /** 작업자 이름: 앞뒤 공백을 지우고 비어 있으면, 5자를 넘으면 400. */
    static String worker(String raw) {
        String w = Texts.trim(raw);
        if (w.isEmpty()) throw ApiException.badRequest("작업자 이름을 적어야 합니다.");
        if (w.length() > NAME_MAX) throw ApiException.badRequest("작업자 이름은 5자까지 쓸 수 있습니다.");
        return w;
    }

    /** 버전 화면 이름: 비면 v{번호}. 5자를 넘으면 400. */
    static String versionLabel(int number, String raw) {
        String name = Texts.trim(raw == null ? "" : raw);
        String label = name.isEmpty() ? "v" + number : name;
        if (label.length() > NAME_MAX) throw ApiException.badRequest("버전 이름은 5자 이내로 입력하세요.");
        return label;
    }

    /** 버전 문서·수정대조표는 .hwpx, .pdf만 받는다(docs/api 3절). */
    static void requireDocFile(FileUpload f) {
        if (!StoredFileService.hasExtension(f, DOC_FILE_EXTENSIONS)) throw ApiException.badRequest(DOC_FILE_TYPE);
    }

    /** 파일을 바꿀 수 있는 버전은 작업중뿐이다(작업완료 뒤 잠금). */
    static void requireEditable(DocumentVersion latest) {
        if (latest.sent()) throw ApiException.conflict(SENT_EDIT);
        String stage = latest.stage();
        if (!"working".equals(stage)) {
            throw ApiException.conflict(STAGE_LABEL.get(stage)
                    + " 단계에서는 파일을 바꿀 수 없습니다. 작업완료를 취소해 작업중으로 돌아가야 합니다.");
        }
    }
}
