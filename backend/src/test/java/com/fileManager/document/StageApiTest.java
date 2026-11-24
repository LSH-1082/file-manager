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
        JsonNode doc = newDoc("계약서");
        doc = workDone(doc);
        assertTrue(last(doc).get("workDone").has("completedAt"));
        assertFalse(last(doc).get("workDone").has("worker"));
        doc = start(doc);
        assertEquals(2, last(doc).get("reviewStart").get("reviewers").size());
        doc = decide(doc, "reject");
        assertEquals("reject", last(doc).get("review").get("result").asString());
        assertEquals("working", stage(doc));
        doc = ok(cancel(doc, "reject"));
        assertEquals("reviewing", stage(doc));
        assertFalse(last(doc).has("review"));
        doc = decide(doc, "approve");
        assertEquals("ready", stage(doc));
        doc = ok(send(doc, "기관"));
        assertEquals("sent", last(doc).get("status").asString());
        assertEquals("2026-11-03T01:02:03.000Z", last(doc).get("sentAt").asString());
        assertEquals("메모", last(doc).get("sentMemo").asString());
        doc = ok(cancel(doc, "send"));
        assertEquals("working", last(doc).get("status").asString());
        assertEquals("ready", stage(doc));
        assertFalse(last(doc).has("sentAt"));
        doc = ok(send(doc, "기관"));
        doc = ok(post(url(doc, "/versions"), Map.of("versionName", "", "worker", "김철수")));
        assertEquals(2, doc.get("versions").size());
        assertEquals(1, last(doc).get("files").size());
        assertEquals(200, addRevision(id(doc), "김철수").status());
        doc = reload(doc);
        doc = workDone(doc);
        doc = start(doc);
        doc = decide(doc, "reject");
        assertEquals("working", stage(doc));
        assertFalse(doc.toString().contains("rejected"));
        doc = ok(cancel(doc, "reject"));
        doc = decide(doc, "reject");
        // 거절 뒤 수정본이 바뀌면 거절 취소 불가
        assertEquals(200, addRevision(id(doc), "김철수").status());
        conflict(cancel(doc, "reject"), "거절 뒤 수정본이나 수정대조표가 바뀌어 거절 취소를 할 수 없습니다.");
        doc = workDone(reload(doc));
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
        JsonNode doc = newDoc("A");
        assertError(post("/api/documents/" + UUID0 + "/work-done", Map.of()), 404, "NOT_FOUND", DOC_404);
        assertError(post("/api/documents/x/work-done", Map.of()), 404, "NOT_FOUND", DOC_404);
        // 본문 없음, {}, 작업자가 들어 있어도(무시) 모두 200
        JsonNode done = ok(call(HttpMethod.POST, url(doc, "/work-done"), null, null, Map.of()));
        assertEquals("queued", stage(done));
        assertFalse(last(done).get("workDone").has("worker"));
        conflict(post(url(doc, "/work-done"), Map.of()), "이미 검토대기 단계입니다.");
        doc = ok(cancel(doc, "workDone"));
        assertEquals("queued", stage(ok(post(url(doc, "/work-done"), Map.of()))));
        doc = ok(cancel(doc, "workDone"));
        JsonNode done3 = ok(post(url(doc, "/work-done"), Map.of("worker", "여섯글자이름")));
        assertEquals("queued", stage(done3));
        assertFalse(last(done3).get("workDone").has("worker"));
        // v2는 수정본이 있어야 함
        JsonNode d2 = sentDoc("B");
        d2 = ok(post(url(d2, "/versions"), Map.of("versionName", "", "worker", "a")));
        conflict(post(url(d2, "/work-done"), Map.of()), "수정본을 올려야 작업완료할 수 있습니다.");
        // 발송된 버전
        JsonNode d3 = sentDoc("C");
        conflict(post(url(d3, "/work-done"), Map.of()), SENT_EDIT);
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
        JsonNode doc = newDoc("A");
        String u = url(doc, "/review-start");
        assertError(post(u, Map.of("reviewers", List.of(" ", ""))), 400, "INVALID_INPUT", "검토자 이름을 한 명 이상 적어야 합니다.");
        assertError(post(u, Map.of("reviewers", List.of())), 400, "INVALID_INPUT", "검토자 이름을 한 명 이상 적어야 합니다.");
        assertError(post(u, Map.of("reviewers", List.of("여섯글자이름"))), 400, "INVALID_INPUT", "검토자 이름은 5자까지 쓸 수 있습니다.");
        assertError(post(u, Map.of("reviewers", "x")), 400, "INVALID_INPUT", FORMAT);
        assertError(post(u, Map.of("reviewers", List.of(1))), 400, "INVALID_INPUT", FORMAT);
        assertError(post(u, Map.of()), 400, "INVALID_INPUT", FORMAT);
        assertError(post("/api/documents/" + UUID0 + "/review-start", Map.of("reviewers", List.of("a"))), 404, "NOT_FOUND", DOC_404);
        conflict(post(u, Map.of("reviewers", List.of("a"))), "작업완료를 먼저 해야 검토를 시작할 수 있습니다.");
        workDone(doc);
        JsonNode r = ok(post(u, Map.of("reviewers", List.of(" 이영희 ", "", "박민수", "최오자이름"))));
        assertEquals(List.of("이영희", "박민수", "최오자이름"),
                r.get("versions").get(0).get("reviewStart").get("reviewers").valueStream().map(JsonNode::asString).toList());
        conflict(post(u, Map.of("reviewers", List.of("a"))), "이미 검토중 단계입니다.");
        JsonNode sent = sentDoc("B");
        conflict(post(url(sent, "/review-start"), Map.of("reviewers", List.of("a"))), SENT_EDIT);
    }

    // ---- 8.13 ----

    @Test
    void reviewDecisionRules() {
        JsonNode doc = newDoc("A");
        String u = url(doc, "/review-decision");
        assertError(post(u, Map.of("decision", "maybe")), 400, "INVALID_INPUT", "승인 또는 거절만 할 수 있습니다.");
        assertError(post(u, Map.of()), 400, "INVALID_INPUT", FORMAT);
        assertError(post(u, Map.of("decision", 1)), 400, "INVALID_INPUT", FORMAT);
        assertError(post("/api/documents/" + UUID0 + "/review-decision", Map.of("decision", "approve")), 404, "NOT_FOUND", DOC_404);
        String need = "검토를 시작해야 승인하거나 거절할 수 있습니다.";
        conflict(post(u, Map.of("decision", "approve")), need);
        workDone(doc);
        conflict(post(u, Map.of("decision", "reject")), need);
        start(doc);
        JsonNode ok = decide(doc, "approve");
        assertEquals("accept", last(ok).get("review").get("result").asString());
        assertEquals(2, last(ok).get("review").get("reviewers").size());
        assertEquals(last(ok).get("files").get(0).get("id").asString(), last(ok).get("review").get("fileId").asString());
        assertFalse(last(ok).get("review").has("revisionId"));
        conflict(post(u, Map.of("decision", "approve")), "이미 발송대기 단계입니다.");
        JsonNode sent = sentDoc("B");
        conflict(post(url(sent, "/review-decision"), Map.of("decision", "approve")), SENT_EDIT);
    }

    // ---- 8.14 ----

    @Test
    void sendRules() {
        JsonNode doc = newDoc("A");
        String u = url(doc, "/send");
        assertError(post(u, Map.of("sentAt", AT, "sentTo", "x".repeat(21), "sentMemo", "")), 400, "INVALID_INPUT", "발송처는 20자까지 입력할 수 있습니다.");
        assertError(post(u, Map.of("sentAt", AT, "sentTo", "x")), 400, "INVALID_INPUT", FORMAT);
        assertError(post(u, Map.of("sentAt", "어제", "sentTo", "x", "sentMemo", "")), 400, "INVALID_INPUT", FORMAT);
        assertError(post("/api/documents/" + UUID0 + "/send", Map.of("sentAt", AT, "sentTo", "x", "sentMemo", "")), 404, "NOT_FOUND", DOC_404);
        conflict(send(doc, "x"), "작업완료와 검토 승인을 거쳐야 발송할 수 있습니다.");
        workDone(doc);
        conflict(send(doc, "x"), "검토를 시작하고 승인을 받아야 발송할 수 있습니다.");
        start(doc);
        conflict(send(doc, "x"), "검토중입니다. 승인을 받아야 발송할 수 있습니다.");
        decide(doc, "reject");
        conflict(send(doc, "x"), "검토에서 거절되었습니다. 승인을 받아야 발송할 수 있습니다.");
        ok(cancel(doc, "reject"));
        decide(doc, "approve");
        JsonNode sent = ok(send(doc, "  " + "y".repeat(20) + "  "));
        assertEquals("y".repeat(20), last(sent).get("sentTo").asString());
        conflict(send(doc, "x"), SENT_EDIT);
        // 빈 발송처 허용
        JsonNode empty = sentDocTo("E", "");
        assertEquals("", last(empty).get("sentTo").asString());
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
        JsonNode doc = newDoc("A");
        doc = workDone(doc);
        doc = ok(cancel(doc, "workDone"));
        assertEquals("working", stage(doc));
        assertFalse(last(doc).has("workDone"));
        doc = start(workDone(doc));
        doc = ok(cancel(doc, "reviewStart"));
        assertEquals("queued", stage(doc));
        assertFalse(last(doc).has("reviewStart"));
        assertEquals(0, jdbc.sql("SELECT count(*) FROM version_reviewers").query(Long.class).single());
        doc = decide(start(doc), "approve");
        doc = ok(cancel(doc, "approve"));
        assertEquals("reviewing", stage(doc));
        assertFalse(last(doc).has("review"));
        assertTrue(last(doc).has("reviewStart"));
        doc = decide(doc, "approve");
        doc = ok(send(doc, "기관"));
        doc = ok(cancel(doc, "send"));
        assertEquals("ready", stage(doc));
    }

    @Test
    void cancelRejections() {
        JsonNode doc = newDoc("A");
        assertError(cancel(doc, "x"), 400, "INVALID_INPUT", FORMAT);
        assertError(post(url(doc, "/cancel"), Map.of()), 400, "INVALID_INPUT", FORMAT);
        assertError(post("/api/documents/" + UUID0 + "/cancel", Map.of("step", "send")), 404, "NOT_FOUND", DOC_404);
        conflict(cancel(doc, "workDone"), "지금 단계(작업중)에서는 작업완료를 취소할 수 없습니다.");
        conflict(cancel(doc, "reject"), "지금 단계(작업중)에서는 거절을 취소할 수 없습니다.");
        workDone(doc);
        conflict(cancel(doc, "approve"), "지금 단계(검토대기)에서는 승인을 취소할 수 없습니다.");
        conflict(cancel(doc, "reviewStart"), "지금 단계(검토대기)에서는 검토를 취소할 수 없습니다.");
        start(doc);
        conflict(cancel(doc, "send"), "지금 단계(검토중)에서는 발송을 취소할 수 없습니다.");
        decide(doc, "reject");
        conflict(cancel(doc, "approve"), "지금 단계(작업중, 거절됨)에서는 승인을 취소할 수 없습니다.");
        ok(cancel(doc, "reject"));
        decide(doc, "approve");
        conflict(cancel(doc, "workDone"), "지금 단계(발송대기)에서는 작업완료를 취소할 수 없습니다.");
        JsonNode sent = ok(send(doc, "기관"));
        conflict(cancel(sent, "approve"), "지금 단계(발송)에서는 승인을 취소할 수 없습니다.");
    }

    @Test
    void cancelSendOnlyLatestAndLockAfterWorkDone() {
        JsonNode doc = sentDoc("A");
        doc = ok(post(url(doc, "/versions"), Map.of("versionName", "", "worker", "a")));
        // 최신 버전(v2)은 발송 전이므로 v1의 발송 취소는 최신 기준으로 판정된다
        conflict(cancel(doc, "send"), "지금 단계(작업중)에서는 발송을 취소할 수 없습니다.");
        addRevision(id(doc), "a");
        doc = workDone(reload(doc));
        // 작업완료 뒤 잠금
        conflict(addRevision(id(doc), "a"), "검토대기 단계에서는 파일을 바꿀 수 없습니다. 작업완료를 취소해 작업중으로 돌아가야 합니다.");
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
        JsonNode doc = reviewing("재무", false);
        long rows = storedFileRows();
        long bodies = storedBodies();
        String u = decisionUrl(doc);
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
        assertEquals("reviewing", stage(doc));
        assertEquals(rows, storedFileRows());
        assertEquals(bodies, storedBodies());
        assertEquals(0, rounds());

        // 앞뒤 공백은 지운 뒤 비교한다
        JsonNode done = ok(reject(id(doc), " 박검토 ", Part.of("memo.pdf", "x")));
        assertEquals("working", stage(done));
        assertEquals("박검토", last(done).get("reviews").get(0).get("reviewers").get(0).asString());

        // 검토 전 문서는 상태 검사(409)가 먼저
        JsonNode queued = workDone(newDoc("B"));
        conflict(post(decisionUrl(queued), Map.of("decision", "reject")), "검토를 시작해야 승인하거나 거절할 수 있습니다.");

        // 승인은 multipart(file 없음)로도 된다
        JsonNode other = reviewing("C", false);
        JsonNode approved = ok(multipart(HttpMethod.POST, decisionUrl(other), Map.of("decision", "approve"), Map.of()));
        assertEquals("ready", stage(approved));
    }

    @Test
    void rejectStoresReviewCopyUnderTargetRevision() {
        JsonNode doc = reviewing("재무", true, "박검토");
        JsonNode done = rejectOk(doc, "박검토", "memo.pdf");
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
        JsonNode doc = reviewing("재무", false);
        JsonNode done = rejectOk(doc, "박검토", "memo.pdf");
        JsonNode r = last(done).get("reviews").get(0);
        assertEquals(last(done).get("files").get(0).get("id").asString(), r.get("fileId").asString());
        assertFalse(r.has("revisionId"));
        JsonNode rf = r.get("reviewFile");
        assertEquals("재무 v1 검토본_" + day(rf) + "_박검토.pdf", rf.get("downloadName").asString());
        assertTrue(Files.exists(diskFile("재무", "v1", rf.get("downloadName").asString())));
    }

    @Test
    void historySurvivesWorkDoneAndAccumulates() {
        JsonNode doc = rejectOk(reviewing("재무", false), "박검토", "memo.pdf");
        assertEquals(200, addRevision(id(doc), "김철수").status());
        doc = reload(doc);
        doc = workDone(doc);
        assertEquals(1, last(doc).get("reviews").size());
        assertFalse(last(doc).has("review"));
        doc = start(doc);
        doc = decide(doc, "approve");
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
        JsonNode doc = reviewing("재무", false);
        long rows = storedFileRows();
        long bodies = storedBodies();
        JsonNode done = rejectOk(doc, "박검토", "memo.pdf");
        JsonNode rf = last(done).get("reviews").get(0).get("reviewFile");
        Path copy = diskFile("재무", "v1", rf.get("downloadName").asString());
        assertTrue(Files.exists(copy));
        assertEquals(rows + 1, storedFileRows());
        JsonNode back = ok(cancel(done, "reject"));
        assertEquals("reviewing", stage(back));
        assertEquals(0, last(back).get("reviews").size());
        assertEquals(rows, storedFileRows());
        assertEquals(bodies, storedBodies());
        assertFalse(Files.exists(copy));
        assertEquals(0, rounds());
        JsonNode again = rejectOk(back, "이영희", "memo.hwpx");
        assertEquals(1, last(again).get("reviews").get(0).get("round").asInt());
    }

    @Test
    void cancelApproveRemovesRound() {
        JsonNode doc = rejectOk(reviewing("재무", false), "박검토", "memo.pdf");
        assertEquals(200, addRevision(id(doc), "김철수").status());
        doc = reload(doc);
        doc = start(workDone(doc));
        doc = decide(doc, "approve");
        assertEquals(2, last(doc).get("reviews").size());
        doc = ok(cancel(doc, "approve"));
        assertEquals("reviewing", stage(doc));
        JsonNode reviews = last(doc).get("reviews");
        assertEquals(1, reviews.size());
        assertEquals("reject", reviews.get(0).get("result").asString());
        assertEquals(1, reviews.get(0).get("round").asInt());
    }

    private static final String NEEDS_REV = "거절된 뒤 수정본을 올려야 작업완료할 수 있습니다.";

    @Test
    void rejectedV1BlocksWorkDoneUntilRevision() {
        JsonNode doc = rejectOk(reviewing("재무", false), "박검토", "memo.pdf");
        conflict(post(url(doc, "/work-done"), Map.of()), NEEDS_REV);
        assertEquals(200, addRevision(id(doc), "김철수").status());
        JsonNode done = workDone(reload(doc));
        assertFalse(last(done).has("review"));
        assertEquals("queued", stage(done));
    }

    @Test
    void deletingRevisionAfterRejectionBlocksAgain() {
        JsonNode doc = rejectOk(reviewing("재무", false), "박검토", "memo.pdf");
        JsonNode added = ok(addRevision(id(doc), "김철수"));
        String revId = last(added).get("files").get(1).get("id").asString();
        ok(delete(url(doc, "/revisions/" + revId)));
        conflict(post(url(doc, "/work-done"), Map.of()), NEEDS_REV);
    }

    @Test
    void rejectedWithRevisionBeforeRejection() {
        JsonNode doc = rejectOk(reviewing("재무", true), "박검토", "memo.pdf");
        conflict(post(url(doc, "/work-done"), Map.of()), NEEDS_REV);
    }

    @Test
    void rejectedV2NeedsRevisionAndKeepsCheckOrder() {
        JsonNode doc = sentDoc("재무");
        doc = ok(post(url(doc, "/versions"), Map.of("versionName", "", "worker", "김철수")));
        assertEquals(200, addRevision(id(doc), "김철수").status());
        doc = start(workDone(reload(doc)));
        doc = decide(doc, "reject");
        conflict(post(url(doc, "/work-done"), Map.of()), NEEDS_REV);
        assertEquals(200, addRevision(id(doc), "김철수").status());
        assertEquals("queued", stage(workDone(reload(doc))));

        // 수정본 1개 상태에서 거절한 뒤 그 수정본을 지우면 앞선 검사(수정본 없음)가 먼저 나온다
        JsonNode d2 = sentDoc("법무");
        d2 = ok(post(url(d2, "/versions"), Map.of("versionName", "", "worker", "김철수")));
        JsonNode with = ok(addRevision(id(d2), "김철수"));
        String onlyId = last(with).get("files").get(1).get("id").asString();
        d2 = decide(start(workDone(reload(d2))), "reject");
        ok(delete(url(d2, "/revisions/" + onlyId)));
        conflict(post(url(d2, "/work-done"), Map.of()), "수정본을 올려야 작업완료할 수 있습니다.");
    }

    @Test
    void cancelRejectRemovesRestriction() {
        JsonNode doc = rejectOk(reviewing("재무", false), "박검토", "memo.pdf");
        doc = ok(cancel(doc, "reject"));
        doc = ok(cancel(doc, "reviewStart"));
        doc = ok(cancel(doc, "workDone"));
        assertEquals("queued", stage(workDone(doc)));
    }

    @Test
    void workDoneNeedsOriginalWhenVersionHasNoFiles() {
        Response r = multipart(HttpMethod.POST, "/api/documents", Map.of("categoryId", category("빈"), "worker", "김"), Map.of());
        assertEquals(201, r.status(), r.text());
        JsonNode doc = r.json();
        conflict(post(url(doc, "/work-done"), Map.of()), "원본을 올려야 작업완료할 수 있습니다.");
        assertEquals("working", stage(reload(doc)));
        ok(multipart(HttpMethod.POST, url(doc, "/revisions"), Map.of("worker", "김"), Map.of("file", Part.of("a.pdf", "x"))));
        assertEquals("queued", stage(workDone(doc)));
    }
}
