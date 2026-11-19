package com.fileManager.document.presentation;

import com.fileManager.document.application.StageService;
import com.fileManager.global.error.ApiException;
import com.fileManager.file.presentation.MultipartUploads;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 단계 전이. 본문은 Map으로 받고, 문자열이어야 하는 값이 문자열이 아니면 400(요청 형식)이다. */
@RestController
@RequestMapping("/api/documents/{id}")
public class StageController {
    private final StageService service;

    StageController(StageService service) {
        this.service = service;
    }

    @PostMapping("/work-done")
    DocResponse workDone(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        // 작업완료는 입력이 없다. 본문(없음, {}, 그 밖의 내용)은 읽지 않는다.
        return service.workDone(id, DocResponse::from);
    }

    @PostMapping("/review-start")
    DocResponse reviewStart(@PathVariable String id, @RequestBody Map<String, Object> body) {
        if (!(body.get("reviewers") instanceof List<?> raw)) throw ApiException.format();
        List<String> reviewers = raw.stream().map(StageController::asText).toList();
        return service.reviewStart(id, reviewers, DocResponse::from);
    }

    /** 승인은 JSON. */
    @PostMapping(path = "/review-decision", consumes = MediaType.APPLICATION_JSON_VALUE)
    DocResponse reviewDecision(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return service.reviewDecision(id, text(body, "decision"), null, null, DocResponse::from);
    }

    /** 거절은 multipart(decision, reviewer, file=검토본 한 개). */
    @PostMapping(path = "/review-decision", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    DocResponse reviewDecisionForm(@PathVariable String id, @RequestParam(required = false) String decision,
                                   @RequestParam(required = false) String reviewer,
                                   @RequestParam(name = "file", required = false) List<MultipartFile> file) {
        if (decision == null) throw ApiException.format();
        return service.reviewDecision(id, decision, reviewer, MultipartUploads.of(file), DocResponse::from);
    }

    @PostMapping("/send")
    DocResponse send(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return service.send(id, text(body, "sentAt"), text(body, "sentTo"), text(body, "sentMemo"), DocResponse::from);
    }

    @PostMapping("/cancel")
    DocResponse cancel(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return service.cancel(id, text(body, "step"), DocResponse::from);
    }

    private static String text(Map<String, Object> body, String key) {
        return asText(body.get(key));
    }

    private static String asText(Object value) {
        if (value instanceof String s) return s;
        throw ApiException.format();
    }
}
