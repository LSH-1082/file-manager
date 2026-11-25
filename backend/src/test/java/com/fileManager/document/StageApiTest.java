package com.fileManager.document;

import com.fileManager.ApiTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.*;

class StageApiTest extends ApiTestSupport {
    private static final String FORMAT = "요청 형식이 올바르지 않습니다.";
    private static final String SENT_EDIT = "발송된 버전은 수정할 수 없습니다. 새 버전을 시작하세요.";
    private static final String DOC_404 = "문서를 찾을 수 없습니다.";
    private static final String UUID0 = "00000000-0000-4000-8000-000000000000";
    private static final String AT = "2026-11-03T01:02:03Z";

    private static String id(JsonNode doc) { return doc.get("id").asString(); }

    private static JsonNode last(JsonNode doc) {
        JsonNode vs = doc.get("versions");
        return vs.get(vs.size() - 1);
    }

    private String url(JsonNode doc, String tail) { return "/api/documents/" + id(doc) + tail; }

    private JsonNode ok(Response r) {
        assertEquals(200, r.status(), r.text());
        return r.json();
    }

    private JsonNode workDone(JsonNode doc) { return ok(post(url(doc, "/work-done"), Map.of())); }

    private JsonNode start(JsonNode doc) { return ok(post(url(doc, "/review-start"), Map.of("reviewers", List.of("이영희", "박민수")))); }

    private JsonNode decide(JsonNode doc, String d) {
        if ("reject".equals(d)) return ok(reject(id(doc), "이영희", Part.of("검토.pdf", "copy")));
        return ok(post(url(doc, "/review-decision"), Map.of("decision", d)));
    }

    private Response send(JsonNode doc, String to) {
        return post(url(doc, "/send"), Map.of("sentAt", AT, "sentTo", to, "sentMemo", " 메모 "));
    }

    private Response cancel(JsonNode doc, String step) { return post(url(doc, "/cancel"), Map.of("step", step)); }

    private void conflict(Response r, String message) { assertError(r, 409, "CONFLICT", message); }

    /** 최신 버전의 단계(working, queued, reviewing, ready, sent). 저장된 기록을 GET 응답에서 읽어 계산한다. */
    private String stage(JsonNode doc) {
        for (JsonNode d : get("/api/documents").json()) {
            if (!id(d).equals(id(doc))) continue;
            JsonNode v = last(d);
            if (v.has("sentAt")) return "sent";
            if (v.has("review")) return v.get("review").get("result").asString().equals("accept") ? "ready" : "working";
            if (v.has("reviewStart")) return "reviewing";
            return v.has("workDone") ? "queued" : "working";
        }
        throw new AssertionError("문서가 없다");
    }

    // ---- 전체 흐름 ----

    @Test
    void fullFlowThroughApiOnly() {
        // given
        JsonNode doc = newDoc("계약서");

        // when: 작업완료한다
        doc = workDone(doc);

        // then: 완료 시각만 남고 작업자는 남지 않는다
        assertTrue(last(doc).get("workDone").has("completedAt"));
        assertFalse(last(doc).get("workDone").has("worker"));

        // when: 검토를 시작한다
        doc = start(doc);

        // then: 검토자 두 명이 남는다
        assertEquals(2, last(doc).get("reviewStart").get("reviewers").size());

        // when: 거절한다
        doc = decide(doc, "reject");

        // then: 거절 결과가 남고 작업중으로 돌아간다
        assertEquals("reject", last(doc).get("review").get("result").asString());
        assertEquals("working", stage(doc));

        // when: 거절을 취소한다
        doc = ok(cancel(doc, "reject"));

        // then: 검토중으로 돌아가고 판정이 지워진다
        assertEquals("reviewing", stage(doc));
        assertFalse(last(doc).has("review"));

        // when: 승인한다
        doc = decide(doc, "approve");

        // then: 발송대기다
        assertEquals("ready", stage(doc));

        // when: 발송한다
        doc = ok(send(doc, "기관"));

        // then: 발송 시각과 다듬은 메모가 남는다
        assertEquals("sent", last(doc).get("status").asString());
        assertEquals("2026-11-03T01:02:03.000Z", last(doc).get("sentAt").asString());
        assertEquals("메모", last(doc).get("sentMemo").asString());

        // when: 발송을 취소한다
        doc = ok(cancel(doc, "send"));

        // then: 발송대기로 돌아가고 발송 시각이 지워진다
        assertEquals("working", last(doc).get("status").asString());
        assertEquals("ready", stage(doc));
        assertFalse(last(doc).has("sentAt"));

        // when: 다시 발송하고 새 버전을 시작한다
        doc = ok(send(doc, "기관"));
        doc = ok(post(url(doc, "/versions"), Map.of("versionName", "", "worker", "김철수")));

        // then: v2는 직전 제출본 하나를 원본으로 가진다
        assertEquals(2, doc.get("versions").size());
        assertEquals(1, last(doc).get("files").size());

        // when & then: v2에 수정본을 올린다
        assertEquals(200, addRevision(id(doc), "김철수").status());

        // when: 작업완료하고 검토를 시작해 거절한다
        doc = reload(doc);
        doc = workDone(doc);
        doc = start(doc);
        doc = decide(doc, "reject");

        // then: 작업중으로 돌아가고 rejected 상태 값은 쓰지 않는다
        assertEquals("working", stage(doc));
        assertFalse(doc.toString().contains("rejected"));

        // when: 거절을 취소하고 다시 거절한 뒤 수정본을 올린다
        doc = ok(cancel(doc, "reject"));
        doc = decide(doc, "reject");
        // 거절 뒤 수정본이 바뀌면 거절 취소 불가
        Response revised = addRevision(id(doc), "김철수");

        // then: 수정본이 올라간다
        assertEquals(200, revised.status());

        // when & then: 거절 취소는 막힌다
        conflict(cancel(doc, "reject"), "거절 뒤 수정본이나 수정대조표가 바뀌어 거절 취소를 할 수 없습니다.");

        // when: 작업완료한다
        doc = workDone(reload(doc));

        // then: 검토 기록이 지워지고 검토대기다
        assertFalse(last(doc).has("review"));
        assertFalse(last(doc).has("reviewStart"));
        assertEquals("queued", stage(doc));
    }

    private JsonNode reload(JsonNode doc) {
        for (JsonNode d : get("/api/documents").json()) if (id(d).equals(id(doc))) return d;
        throw new AssertionError("문서 없음");
    }

    // ---- 8.11 ----

    @Test
    void workDoneRules() {
        // given
        JsonNode doc = newDoc("A");

        // when & then: 없는 문서는 404
        assertError(post("/api/documents/" + UUID0 + "/work-done", Map.of()), 404, "NOT_FOUND", DOC_404);
        assertError(post("/api/documents/x/work-done", Map.of()), 404, "NOT_FOUND", DOC_404);

        // when: 본문 없이 작업완료한다
        // 본문 없음, {}, 작업자가 들어 있어도(무시) 모두 200
        JsonNode done = ok(call(HttpMethod.POST, url(doc, "/work-done"), null, null, Map.of()));

        // then: 검토대기이고 작업자는 남지 않는다
        assertEquals("queued", stage(done));
        assertFalse(last(done).get("workDone").has("worker"));

        // when & then: 이미 검토대기면 409
        conflict(post(url(doc, "/work-done"), Map.of()), "이미 검토대기 단계입니다.");

        // when: 작업완료를 취소하고 {}로 다시 작업완료한다
        doc = ok(cancel(doc, "workDone"));
        JsonNode done2 = ok(post(url(doc, "/work-done"), Map.of()));

        // then: 검토대기다
        assertEquals("queued", stage(done2));

        // when: 작업완료를 취소하고 작업자를 넣어 다시 작업완료한다
        doc = ok(cancel(doc, "workDone"));
        JsonNode done3 = ok(post(url(doc, "/work-done"), Map.of("worker", "여섯글자이름")));

        // then: 작업자는 무시된다
        assertEquals("queued", stage(done3));
        assertFalse(last(done3).get("workDone").has("worker"));

        // when: 수정본 없는 v2를 작업완료한다
        // v2는 수정본이 있어야 함
        JsonNode d2 = sentDoc("B");
        d2 = ok(post(url(d2, "/versions"), Map.of("versionName", "", "worker", "a")));
        Response noRevision = post(url(d2, "/work-done"), Map.of());

        // then: 409
        conflict(noRevision, "수정본을 올려야 작업완료할 수 있습니다.");

        // when: 발송된 버전을 작업완료한다
        // 발송된 버전
        JsonNode d3 = sentDoc("C");
        Response sentWorkDone = post(url(d3, "/work-done"), Map.of());

        // then: 409
        conflict(sentWorkDone, SENT_EDIT);
    }

    private JsonNode sentDoc(String name) {
        JsonNode d = workDone(newDoc(name));
        d = start(d);
        d = decide(d, "approve");
        return ok(send(d, "기관"));
    }

    // ---- 8.12 ----

    @Test
    void reviewStartRules() {
        // given
        JsonNode doc = newDoc("A");
        String u = url(doc, "/review-start");

        // when & then: 잘못된 검토자 입력은 400
        assertError(post(u, Map.of("reviewers", List.of(" ", ""))), 400, "INVALID_INPUT", "검토자 이름을 한 명 이상 적어야 합니다.");
        assertError(post(u, Map.of("reviewers", List.of())), 400, "INVALID_INPUT", "검토자 이름을 한 명 이상 적어야 합니다.");
        assertError(post(u, Map.of("reviewers", List.of("여섯글자이름"))), 400, "INVALID_INPUT", "검토자 이름은 5자까지 쓸 수 있습니다.");
        assertError(post(u, Map.of("reviewers", "x")), 400, "INVALID_INPUT", FORMAT);
        assertError(post(u, Map.of("reviewers", List.of(1))), 400, "INVALID_INPUT", FORMAT);
        assertError(post(u, Map.of()), 400, "INVALID_INPUT", FORMAT);

        // when & then: 없는 문서는 404
        assertError(post("/api/documents/" + UUID0 + "/review-start", Map.of("reviewers", List.of("a"))), 404, "NOT_FOUND", DOC_404);

        // when & then: 작업완료 전이면 409
        conflict(post(u, Map.of("reviewers", List.of("a"))), "작업완료를 먼저 해야 검토를 시작할 수 있습니다.");

        // when: 작업완료한 뒤 검토를 시작한다
        workDone(doc);
        JsonNode r = ok(post(u, Map.of("reviewers", List.of(" 이영희 ", "", "박민수", "최오자이름"))));

        // then: 이름의 앞뒤 공백을 지우고 빈 이름은 뺀다
        assertEquals(List.of("이영희", "박민수", "최오자이름"),
                r.get("versions").get(0).get("reviewStart").get("reviewers").valueStream().map(JsonNode::asString).toList());

        // when & then: 이미 검토중이면 409
        conflict(post(u, Map.of("reviewers", List.of("a"))), "이미 검토중 단계입니다.");

        // when: 발송된 버전의 검토를 시작한다
        JsonNode sent = sentDoc("B");
        Response sentStart = post(url(sent, "/review-start"), Map.of("reviewers", List.of("a")));

        // then: 409
        conflict(sentStart, SENT_EDIT);
    }

    // ---- 8.13 ----

    @Test
    void reviewDecisionRules() {
        // given
        JsonNode doc = newDoc("A");
        String u = url(doc, "/review-decision");

        // when & then: 잘못된 판정 입력은 400
        assertError(post(u, Map.of("decision", "maybe")), 400, "INVALID_INPUT", "승인 또는 거절만 할 수 있습니다.");
        assertError(post(u, Map.of()), 400, "INVALID_INPUT", FORMAT);
        assertError(post(u, Map.of("decision", 1)), 400, "INVALID_INPUT", FORMAT);

        // when & then: 없는 문서는 404
        assertError(post("/api/documents/" + UUID0 + "/review-decision", Map.of("decision", "approve")), 404, "NOT_FOUND", DOC_404);

        // when & then: 작업중이면 409
        String need = "검토를 시작해야 승인하거나 거절할 수 있습니다.";
        conflict(post(u, Map.of("decision", "approve")), need);

        // when: 작업완료만 한 뒤 거절한다
        workDone(doc);
        Response rejectQueued = post(u, Map.of("decision", "reject"));

        // then: 409
        conflict(rejectQueued, need);

        // when: 검토를 시작해 승인한다
        start(doc);
        JsonNode ok = decide(doc, "approve");

        // then: 승인 판정이 검토자·원본과 함께 남는다
        assertEquals("accept", last(ok).get("review").get("result").asString());
        assertEquals(2, last(ok).get("review").get("reviewers").size());
        assertEquals(last(ok).get("files").get(0).get("id").asString(), last(ok).get("review").get("fileId").asString());
        assertFalse(last(ok).get("review").has("revisionId"));

        // when & then: 이미 발송대기면 409
        conflict(post(u, Map.of("decision", "approve")), "이미 발송대기 단계입니다.");

        // when: 발송된 버전을 승인한다
        JsonNode sent = sentDoc("B");
        Response sentDecision = post(url(sent, "/review-decision"), Map.of("decision", "approve"));

        // then: 409
        conflict(sentDecision, SENT_EDIT);
    }

    // ---- 8.14 ----

    @Test
    void sendRules() {
        // given
        JsonNode doc = newDoc("A");
        String u = url(doc, "/send");

        // when & then: 잘못된 발송 입력은 400
        assertError(post(u, Map.of("sentAt", AT, "sentTo", "x".repeat(21), "sentMemo", "")), 400, "INVALID_INPUT", "발송처는 20자까지 입력할 수 있습니다.");
        assertError(post(u, Map.of("sentAt", AT, "sentTo", "x")), 400, "INVALID_INPUT", FORMAT);
        assertError(post(u, Map.of("sentAt", "어제", "sentTo", "x", "sentMemo", "")), 400, "INVALID_INPUT", FORMAT);

        // when & then: 없는 문서는 404
        assertError(post("/api/documents/" + UUID0 + "/send", Map.of("sentAt", AT, "sentTo", "x", "sentMemo", "")), 404, "NOT_FOUND", DOC_404);

        // when & then: 작업중이면 409
        conflict(send(doc, "x"), "작업완료와 검토 승인을 거쳐야 발송할 수 있습니다.");

        // when: 작업완료만 한 뒤 발송한다
        workDone(doc);
        Response sendQueued = send(doc, "x");

        // then: 409
        conflict(sendQueued, "검토를 시작하고 승인을 받아야 발송할 수 있습니다.");

        // when: 검토를 시작한 뒤 발송한다
        start(doc);
        Response sendReviewing = send(doc, "x");

        // then: 409
        conflict(sendReviewing, "검토중입니다. 승인을 받아야 발송할 수 있습니다.");

        // when: 거절한 뒤 발송한다
        decide(doc, "reject");
        Response sendRejected = send(doc, "x");

        // then: 409
        conflict(sendRejected, "검토에서 거절되었습니다. 승인을 받아야 발송할 수 있습니다.");

        // when: 거절을 취소하고 승인한 뒤 앞뒤 공백이 있는 발송처로 발송한다
        ok(cancel(doc, "reject"));
        decide(doc, "approve");
        JsonNode sent = ok(send(doc, "  " + "y".repeat(20) + "  "));

        // then: 공백을 지운 20자 발송처가 남는다
        assertEquals("y".repeat(20), last(sent).get("sentTo").asString());

        // when & then: 다시 발송하면 409
        conflict(send(doc, "x"), SENT_EDIT);

        // when: 빈 발송처로 발송한다
        // 빈 발송처 허용
        JsonNode empty = sentDocTo("E", "");

        // then: 빈 발송처가 남는다
        assertEquals("", last(empty).get("sentTo").asString());

        // when & then: 발송된 버전의 삭제·이름 변경은 409
        // 발송된 버전은 삭제·이름 변경 불가
        conflict(delete(url(sent, "/versions/" + last(sent).get("id").asString())), "발송된 버전은 삭제할 수 없습니다.");
        conflict(patch(url(sent, "/versions/" + last(sent).get("id").asString()), Map.of("name", "z")), "발송된 버전의 이름은 바꿀 수 없습니다.");
    }

    private JsonNode sentDocTo(String name, String to) {
        JsonNode d = decide(start(workDone(newDoc(name))), "approve");
        return ok(send(d, to));
    }

    // ---- 8.15 ----

    @Test
    void cancelEachStep() {
        // given
        JsonNode doc = newDoc("A");
        doc = workDone(doc);

        // when: 작업완료를 취소한다
        doc = ok(cancel(doc, "workDone"));

        // then: 작업중으로 돌아간다
        assertEquals("working", stage(doc));
        assertFalse(last(doc).has("workDone"));

        // when: 다시 작업완료하고 검토를 시작한 뒤 검토를 취소한다
        doc = start(workDone(doc));
        doc = ok(cancel(doc, "reviewStart"));

        // then: 검토대기로 돌아가고 검토자 행도 지워진다
        assertEquals("queued", stage(doc));
        assertFalse(last(doc).has("reviewStart"));
        assertEquals(0, jdbc.sql("SELECT count(*) FROM version_reviewers").query(Long.class).single());

        // when: 검토를 시작해 승인한 뒤 승인을 취소한다
        doc = decide(start(doc), "approve");
        doc = ok(cancel(doc, "approve"));

        // then: 검토중으로 돌아가고 검토 시작은 남는다
        assertEquals("reviewing", stage(doc));
        assertFalse(last(doc).has("review"));
        assertTrue(last(doc).has("reviewStart"));

        // when: 승인하고 발송한 뒤 발송을 취소한다
        doc = decide(doc, "approve");
        doc = ok(send(doc, "기관"));
        doc = ok(cancel(doc, "send"));

        // then: 발송대기로 돌아간다
        assertEquals("ready", stage(doc));
    }

    @Test
    void cancelRejections() {
        // given
        JsonNode doc = newDoc("A");

        // when & then: 잘못된 단계 입력은 400
        assertError(cancel(doc, "x"), 400, "INVALID_INPUT", FORMAT);
        assertError(post(url(doc, "/cancel"), Map.of()), 400, "INVALID_INPUT", FORMAT);

        // when & then: 없는 문서는 404
        assertError(post("/api/documents/" + UUID0 + "/cancel", Map.of("step", "send")), 404, "NOT_FOUND", DOC_404);

        // when & then: 작업중에서 할 수 없는 취소는 409
        conflict(cancel(doc, "workDone"), "지금 단계(작업중)에서는 작업완료를 취소할 수 없습니다.");
        conflict(cancel(doc, "reject"), "지금 단계(작업중)에서는 거절을 취소할 수 없습니다.");

        // when: 작업완료한 뒤 승인을 취소한다
        workDone(doc);
        Response approveQueued = cancel(doc, "approve");

        // then: 409
        conflict(approveQueued, "지금 단계(검토대기)에서는 승인을 취소할 수 없습니다.");

        // when & then: 검토대기에서 검토 취소는 409
        conflict(cancel(doc, "reviewStart"), "지금 단계(검토대기)에서는 검토를 취소할 수 없습니다.");

        // when: 검토를 시작한 뒤 발송을 취소한다
        start(doc);
        Response sendReviewing = cancel(doc, "send");

        // then: 409
        conflict(sendReviewing, "지금 단계(검토중)에서는 발송을 취소할 수 없습니다.");

        // when: 거절한 뒤 승인을 취소한다
        decide(doc, "reject");
        Response approveRejected = cancel(doc, "approve");

        // then: 409
        conflict(approveRejected, "지금 단계(작업중, 거절됨)에서는 승인을 취소할 수 없습니다.");

        // when: 거절을 취소하고 승인한 뒤 작업완료를 취소한다
        ok(cancel(doc, "reject"));
        decide(doc, "approve");
        Response workDoneReady = cancel(doc, "workDone");

        // then: 409
        conflict(workDoneReady, "지금 단계(발송대기)에서는 작업완료를 취소할 수 없습니다.");

        // when: 발송한 뒤 승인을 취소한다
        JsonNode sent = ok(send(doc, "기관"));
        Response approveSent = cancel(sent, "approve");

        // then: 409
        conflict(approveSent, "지금 단계(발송)에서는 승인을 취소할 수 없습니다.");
    }

    @Test
    void cancelSendOnlyLatestAndLockAfterWorkDone() {
        // given
        JsonNode doc = sentDoc("A");
        doc = ok(post(url(doc, "/versions"), Map.of("versionName", "", "worker", "a")));

        // when & then: v1의 발송 취소는 409
        // 최신 버전(v2)은 발송 전이므로 v1의 발송 취소는 최신 기준으로 판정된다
        conflict(cancel(doc, "send"), "지금 단계(작업중)에서는 발송을 취소할 수 없습니다.");

        // when: 수정본을 올려 작업완료한 뒤 수정본을 더 올린다
        addRevision(id(doc), "a");
        doc = workDone(reload(doc));
        // 작업완료 뒤 잠금
        Response addLocked = addRevision(id(doc), "a");

        // then: 409
        conflict(addLocked, "검토대기 단계에서는 파일을 바꿀 수 없습니다. 작업완료를 취소해 작업중으로 돌아가야 합니다.");

        // when & then: 수정본 삭제도 409
        String fileId = last(doc).get("files").get(1).get("id").asString();
        conflict(delete(url(doc, "/revisions/" + fileId)), "검토대기 단계에서는 파일을 바꿀 수 없습니다. 작업완료를 취소해 작업중으로 돌아가야 합니다.");
    }

    // ---- 검토 이력·거절 검토본 ----

    private static final String COPY_NEEDED = "거절하려면 검토본 파일을 올려야 합니다.";
    private static final String REVIEWER_PICK = "검토자는 검토 시작 때 적은 검토자 중에서 골라야 합니다.";

    private String decisionUrl(JsonNode doc) { return url(doc, "/review-decision"); }

    private JsonNode reviewing(String category, boolean withRevision) { return reviewing(category, withRevision, "박검토", "이영희"); }

    private JsonNode reviewing(String category, boolean withRevision, String... names) {
        JsonNode doc = newDoc(category);
        if (withRevision) assertEquals(200, addRevision(id(doc), "김철수").status());
        doc = workDone(reload(doc));
        return ok(post(url(doc, "/review-start"), Map.of("reviewers", List.of(names))));
    }

    private static String day(JsonNode fileRev) {
        return DateTimeFormatter.BASIC_ISO_DATE.format(
                LocalDate.ofInstant(Instant.parse(fileRev.get("uploadedAt").asString()), ZoneId.systemDefault()));
    }

    private Path diskFile(String... parts) {
        Path p = storageRoot();
        for (String s : parts) p = p.resolve(s);
        return p;
    }

    private JsonNode rejectOk(JsonNode doc, String reviewer, String fileName) {
        return ok(reject(id(doc), reviewer, Part.of(fileName, "copy-body")));
    }

    private long rounds() { return jdbc.sql("SELECT count(*) FROM version_review_rounds").query(Long.class).single(); }

    @Test
    void rejectRequiresReviewCopy() {
        // given
        JsonNode doc = reviewing("재무", false);
        long rows = storedFileRows();
        long bodies = storedBodies();
        String u = decisionUrl(doc);

        // when & then: 검토본·검토자가 잘못된 거절은 400
        assertError(post(u, Map.of("decision", "reject")), 400, "INVALID_INPUT", COPY_NEEDED);
        assertError(multipart(HttpMethod.POST, u, Map.of("decision", "reject", "reviewer", "박검토"), Map.of()),
                400, "INVALID_INPUT", COPY_NEEDED);
        assertError(multipartMulti(HttpMethod.POST, u, Map.of("decision", "reject", "reviewer", "박검토"),
                Map.of("file", List.of(Part.of("a.pdf", "a"), Part.of("b.pdf", "b")))), 400, "INVALID_INPUT", FORMAT);
        assertError(reject(id(doc), "박검토", Part.of("memo.docx", "x")), 400, "INVALID_INPUT",
                ".hwpx 또는 .pdf 파일만 올릴 수 있습니다.");
        for (String bad : List.of("최검토", "")) {
            assertError(reject(id(doc), bad, Part.of("memo.pdf", "x")), 400, "INVALID_INPUT", REVIEWER_PICK);
        }
        assertError(multipart(HttpMethod.POST, u, Map.of("decision", "reject"), Map.of("file", Part.of("memo.pdf", "x"))),
                400, "INVALID_INPUT", REVIEWER_PICK);

        // then: 실패한 거절은 상태·파일·회차를 남기지 않는다
        assertEquals("reviewing", stage(doc));
        assertEquals(rows, storedFileRows());
        assertEquals(bodies, storedBodies());
        assertEquals(0, rounds());

        // when: 앞뒤 공백이 있는 검토자 이름으로 거절한다
        // 앞뒤 공백은 지운 뒤 비교한다
        JsonNode done = ok(reject(id(doc), " 박검토 ", Part.of("memo.pdf", "x")));

        // then: 공백을 지운 이름으로 거절된다
        assertEquals("working", stage(done));
        assertEquals("박검토", last(done).get("reviews").get(0).get("reviewers").get(0).asString());

        // when: 검토대기 문서를 검토본 없이 거절한다
        // 검토 전 문서는 상태 검사(409)가 먼저
        JsonNode queued = workDone(newDoc("B"));
        Response rejectQueued = post(decisionUrl(queued), Map.of("decision", "reject"));

        // then: 400이 아니라 409
        conflict(rejectQueued, "검토를 시작해야 승인하거나 거절할 수 있습니다.");

        // when: 파일 없는 multipart로 승인한다
        // 승인은 multipart(file 없음)로도 된다
        JsonNode other = reviewing("C", false);
        JsonNode approved = ok(multipart(HttpMethod.POST, decisionUrl(other), Map.of("decision", "approve"), Map.of()));

        // then: 발송대기다
        assertEquals("ready", stage(approved));
    }

    @Test
    void rejectStoresReviewCopyUnderTargetRevision() {
        // given
        JsonNode doc = reviewing("재무", true, "박검토");

        // when
        JsonNode done = rejectOk(doc, "박검토", "memo.pdf");

        // then
        assertEquals("working", stage(done));
        assertEquals("reject", last(done).get("review").get("result").asString());
        JsonNode reviews = last(done).get("reviews");
        assertEquals(1, reviews.size());
        JsonNode r = reviews.get(reviews.size() - 1);
        assertEquals(1, r.get("round").asInt());
        assertEquals("reject", r.get("result").asString());
        assertEquals(1, r.get("reviewers").size());
        assertEquals("박검토", r.get("reviewers").get(0).asString());
        assertTrue(r.has("reviewedAt"));
        JsonNode rev = last(done).get("files").get(1);
        assertEquals(rev.get("id").asString(), r.get("fileId").asString());
        assertEquals(rev.get("revision").get("id").asString(), r.get("revisionId").asString());
        JsonNode rf = r.get("reviewFile");
        assertEquals("재무 v1 검토본.pdf", rf.get("fileName").asString());
        String stored = "재무 v1 검토본_" + day(rf) + "_박검토.pdf";
        assertEquals(stored, rf.get("downloadName").asString());
        assertEquals("박검토", rf.get("uploadedBy").asString());
        assertTrue(Files.exists(diskFile("재무", "v1", "edit1", stored)));
        Response dl = get("/api/files/" + rf.get("id").asString());
        assertEquals(200, dl.status());
        assertEquals("copy-body", dl.text());
    }

    @Test
    void rejectWithoutRevisionStoresCopyInVersionFolder() {
        // given
        JsonNode doc = reviewing("재무", false);

        // when
        JsonNode done = rejectOk(doc, "박검토", "memo.pdf");

        // then
        JsonNode r = last(done).get("reviews").get(0);
        assertEquals(last(done).get("files").get(0).get("id").asString(), r.get("fileId").asString());
        assertFalse(r.has("revisionId"));
        JsonNode rf = r.get("reviewFile");
        assertEquals("재무 v1 검토본_" + day(rf) + "_박검토.pdf", rf.get("downloadName").asString());
        assertTrue(Files.exists(diskFile("재무", "v1", rf.get("downloadName").asString())));
    }

    @Test
    void historySurvivesWorkDoneAndAccumulates() {
        // given
        JsonNode doc = rejectOk(reviewing("재무", false), "박검토", "memo.pdf");

        // when & then: 거절 뒤 수정본을 올린다
        assertEquals(200, addRevision(id(doc), "김철수").status());

        // when: 작업완료한다
        doc = reload(doc);
        doc = workDone(doc);

        // then: 거절 회차는 남고 현재 판정은 지워진다
        assertEquals(1, last(doc).get("reviews").size());
        assertFalse(last(doc).has("review"));

        // when: 다시 검토를 시작해 승인한다
        doc = start(doc);
        doc = decide(doc, "approve");

        // then: 회차가 거절·승인 순으로 쌓인다
        JsonNode reviews = last(doc).get("reviews");
        assertEquals(2, reviews.size());
        assertEquals(1, reviews.get(0).get("round").asInt());
        assertEquals("reject", reviews.get(0).get("result").asString());
        assertTrue(reviews.get(0).has("reviewFile"));
        assertEquals(2, reviews.get(1).get("round").asInt());
        assertEquals("accept", reviews.get(1).get("result").asString());
        assertFalse(reviews.get(1).has("reviewFile"));
        assertEquals("accept", last(doc).get("review").get("result").asString());
    }

    @Test
    void cancelRejectRemovesRoundAndCopy() {
        // given
        JsonNode doc = reviewing("재무", false);
        long rows = storedFileRows();
        long bodies = storedBodies();

        // when: 거절한다
        JsonNode done = rejectOk(doc, "박검토", "memo.pdf");

        // then: 검토본 파일과 파일 행이 생긴다
        JsonNode rf = last(done).get("reviews").get(0).get("reviewFile");
        Path copy = diskFile("재무", "v1", rf.get("downloadName").asString());
        assertTrue(Files.exists(copy));
        assertEquals(rows + 1, storedFileRows());

        // when: 거절을 취소한다
        JsonNode back = ok(cancel(done, "reject"));

        // then: 회차·검토본 파일·행이 모두 지워진다
        assertEquals("reviewing", stage(back));
        assertEquals(0, last(back).get("reviews").size());
        assertEquals(rows, storedFileRows());
        assertEquals(bodies, storedBodies());
        assertFalse(Files.exists(copy));
        assertEquals(0, rounds());

        // when: 다시 거절한다
        JsonNode again = rejectOk(back, "이영희", "memo.hwpx");

        // then: 회차 번호는 다시 1이다
        assertEquals(1, last(again).get("reviews").get(0).get("round").asInt());
    }

    @Test
    void cancelApproveRemovesRound() {
        // given
        JsonNode doc = rejectOk(reviewing("재무", false), "박검토", "memo.pdf");

        // when & then: 거절 뒤 수정본을 올린다
        assertEquals(200, addRevision(id(doc), "김철수").status());

        // when: 작업완료하고 검토를 시작해 승인한다
        doc = reload(doc);
        doc = start(workDone(doc));
        doc = decide(doc, "approve");

        // then: 회차가 두 개다
        assertEquals(2, last(doc).get("reviews").size());

        // when: 승인을 취소한다
        doc = ok(cancel(doc, "approve"));

        // then: 승인 회차만 지워지고 거절 회차는 남는다
        assertEquals("reviewing", stage(doc));
        JsonNode reviews = last(doc).get("reviews");
        assertEquals(1, reviews.size());
        assertEquals("reject", reviews.get(0).get("result").asString());
        assertEquals(1, reviews.get(0).get("round").asInt());
    }

    private static final String NEEDS_REV = "거절된 뒤 수정본을 올려야 작업완료할 수 있습니다.";

    @Test
    void rejectedV1BlocksWorkDoneUntilRevision() {
        // given
        JsonNode doc = rejectOk(reviewing("재무", false), "박검토", "memo.pdf");

        // when & then: 수정본 없이 작업완료하면 409
        conflict(post(url(doc, "/work-done"), Map.of()), NEEDS_REV);

        // when & then: 수정본을 올린다
        assertEquals(200, addRevision(id(doc), "김철수").status());

        // when: 작업완료한다
        JsonNode done = workDone(reload(doc));

        // then: 거절 판정이 지워지고 검토대기다
        assertFalse(last(done).has("review"));
        assertEquals("queued", stage(done));
    }

    @Test
    void deletingRevisionAfterRejectionBlocksAgain() {
        // given
        JsonNode doc = rejectOk(reviewing("재무", false), "박검토", "memo.pdf");
        JsonNode added = ok(addRevision(id(doc), "김철수"));
        String revId = last(added).get("files").get(1).get("id").asString();

        // when: 거절 뒤 올린 수정본을 지우고 작업완료한다
        ok(delete(url(doc, "/revisions/" + revId)));
        Response afterDelete = post(url(doc, "/work-done"), Map.of());

        // then: 다시 막힌다
        conflict(afterDelete, NEEDS_REV);
    }

    @Test
    void rejectedWithRevisionBeforeRejection() {
        // given
        JsonNode doc = rejectOk(reviewing("재무", true), "박검토", "memo.pdf");

        // when & then: 거절 전에 올린 수정본은 인정하지 않는다
        conflict(post(url(doc, "/work-done"), Map.of()), NEEDS_REV);
    }

    @Test
    void rejectedV2NeedsRevisionAndKeepsCheckOrder() {
        // given
        JsonNode doc = sentDoc("재무");
        doc = ok(post(url(doc, "/versions"), Map.of("versionName", "", "worker", "김철수")));

        // when & then: v2에 수정본을 올린다
        assertEquals(200, addRevision(id(doc), "김철수").status());

        // when: 작업완료하고 검토를 시작해 거절한 뒤 작업완료한다
        doc = start(workDone(reload(doc)));
        doc = decide(doc, "reject");
        Response beforeRevision = post(url(doc, "/work-done"), Map.of());

        // then: 거절 뒤 수정본이 필요하다
        conflict(beforeRevision, NEEDS_REV);

        // when & then: 수정본을 올린다
        assertEquals(200, addRevision(id(doc), "김철수").status());

        // when: 작업완료한다
        JsonNode queued = workDone(reload(doc));

        // then: 검토대기다
        assertEquals("queued", stage(queued));

        // when: 다른 문서 v2에서 수정본 1개로 거절한 뒤 그 수정본을 지우고 작업완료한다
        // 수정본 1개 상태에서 거절한 뒤 그 수정본을 지우면 앞선 검사(수정본 없음)가 먼저 나온다
        JsonNode d2 = sentDoc("법무");
        d2 = ok(post(url(d2, "/versions"), Map.of("versionName", "", "worker", "김철수")));
        JsonNode with = ok(addRevision(id(d2), "김철수"));
        String onlyId = last(with).get("files").get(1).get("id").asString();
        d2 = decide(start(workDone(reload(d2))), "reject");
        ok(delete(url(d2, "/revisions/" + onlyId)));
        Response afterDelete = post(url(d2, "/work-done"), Map.of());

        // then: 수정본 없음 검사가 먼저 나온다
        conflict(afterDelete, "수정본을 올려야 작업완료할 수 있습니다.");
    }

    @Test
    void cancelRejectRemovesRestriction() {
        // given
        JsonNode doc = rejectOk(reviewing("재무", false), "박검토", "memo.pdf");

        // when: 거절·검토·작업완료를 차례로 취소한 뒤 다시 작업완료한다
        doc = ok(cancel(doc, "reject"));
        doc = ok(cancel(doc, "reviewStart"));
        doc = ok(cancel(doc, "workDone"));
        JsonNode done = workDone(doc);

        // then: 수정본 없이도 검토대기가 된다
        assertEquals("queued", stage(done));
    }

    @Test
    void workDoneNeedsOriginalWhenVersionHasNoFiles() {
        // when: 파일 없이 문서를 만든다
        Response r = multipart(HttpMethod.POST, "/api/documents", Map.of("categoryId", category("빈"), "worker", "김"), Map.of());

        // then: 201
        assertEquals(201, r.status(), r.text());
        JsonNode doc = r.json();

        // when & then: 원본 없이 작업완료하면 409
        conflict(post(url(doc, "/work-done"), Map.of()), "원본을 올려야 작업완료할 수 있습니다.");

        // then: 작업중 그대로다
        assertEquals("working", stage(reload(doc)));

        // when: 원본을 올리고 작업완료한다
        ok(multipart(HttpMethod.POST, url(doc, "/revisions"), Map.of("worker", "김"), Map.of("file", Part.of("a.pdf", "x"))));
        JsonNode done = workDone(doc);

        // then: 검토대기다
        assertEquals("queued", stage(done));
    }
}
