package com.fileManager.document;

import com.fileManager.ApiTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.*;

class DocumentApiTest extends ApiTestSupport {
    private static final String FORMAT = "요청 형식이 올바르지 않습니다.";
    private static final String SENT_EDIT = "발송된 버전은 수정할 수 없습니다. 새 버전을 시작하세요.";
    private static final String DOC_404 = "문서를 찾을 수 없습니다.";
    private static final String VER_404 = "버전을 찾을 수 없습니다.";
    private static final String WORKER_EMPTY = "작업자 이름을 적어야 합니다.";
    private static final String WORKER_LONG = "작업자 이름은 5자까지 쓸 수 있습니다.";
    private static final String VERSION_LONG = "버전 이름은 5자 이내로 입력하세요.";
    private static final String HAS_DOC = "이미 문서가 있는 카테고리입니다.";
    private static final String DUP = "이 문서에 같은 이름의 버전이 있습니다.";
    private static final String UUID0 = "00000000-0000-4000-8000-000000000000";

    private long storedFiles() {
        return jdbc.sql("SELECT count(*) FROM stored_files").query(Long.class).single();
    }

    private JsonNode docs() {
        Response r = get("/api/documents");
        assertEquals(200, r.status(), r.text());
        return r.json();
    }

    private JsonNode versions(Response r) {
        assertEquals(200, r.status(), r.text());
        return r.json().get("versions");
    }

    private String docUrl(JsonNode doc, String tail) { return "/api/documents/" + doc.get("id").asString() + tail; }

    private String docId(JsonNode doc) { return doc.get("id").asString(); }

    private String vid(JsonNode doc) { return latestVersionId(doc); }

    private static JsonNode lastVersion(JsonNode doc) {
        JsonNode vs = doc.get("versions");
        return vs.get(vs.size() - 1);
    }

    private JsonNode reload(JsonNode doc) {
        for (JsonNode d : docs()) if (d.get("id").asString().equals(docId(doc))) return d;
        throw new AssertionError("문서 없음");
    }

    private Response startVersion(JsonNode doc, String name, String worker) {
        return post(docUrl(doc, "/versions"), Map.of("versionName", name, "worker", worker));
    }

    // ---- 8.8 createDoc ----

    @Test
    void createDocReturnsDocWithoutNulls() {
        // given
        String cat = category("계약서");

        // when
        Response r = uploadDoc(cat, "  김철수  ");

        // then
        assertEquals(201, r.status(), r.text());
        JsonNode d = r.json();
        assertEquals("계약서", d.get("title").asString());
        assertEquals(cat, d.get("categoryId").asString());
        assertEquals(1, d.get("versions").size());
        JsonNode v = d.get("versions").get(0);
        assertEquals(1, v.get("number").asInt());
        assertEquals("working", v.get("status").asString());
        assertFalse(v.has("name"));
        assertEquals("김철수", v.get("worker").asString(), "v1 worker는 원본 올린 사람");
        assertEquals("김철수", v.get("files").get(0).get("uploadedBy").asString());
        assertEquals("계약서 v1.pdf", v.get("files").get(0).get("fileName").asString());
        assertFalse(v.get("files").get(0).has("revision"));
        assertFalse(r.text().contains(":null"), r.text());
        assertTrue(r.text().matches(".*\"createdAt\":\"\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\.\\d{3}Z\".*"));
        assertEquals(1, docs().size());
    }

    @Test
    void createDocKeepsVersionNameOnlyWhenNotDefault() {
        // given
        String cat = category("a");

        // when: 버전 이름 '초안'으로 만든다
        Response r = multipart(HttpMethod.POST, "/api/documents",
                Map.of("categoryId", cat, "worker", "김", "versionName", "초안"), Map.of("file", Part.of("a.pdf", "x")));

        // then: 이름이 남는다
        assertEquals("초안", r.json().get("versions").get(0).get("name").asString());

        // when: 다른 카테고리에 기본 이름 ' v1 '으로 만든다
        String cat2 = category("b");
        Response r2 = multipart(HttpMethod.POST, "/api/documents",
                Map.of("categoryId", cat2, "worker", "김", "versionName", " v1 "), Map.of("file", Part.of("a.pdf", "x")));

        // then: 이름이 없다
        assertFalse(r2.json().get("versions").get(0).has("name"));
    }

    @Test
    void createDocRejections() throws Exception {
        // given
        String cat = category("c");
        Map<String, Part> f = Map.of("file", Part.of("a.pdf", "x"));
        String url = "/api/documents";

        // when & then: 잘못된 요청은 거절되고 파일이 남지 않는다
        assertError(multipart(HttpMethod.POST, url, Map.of("categoryId", cat, "worker", "", "versionName", "여섯글자이상"), f),
                400, "INVALID_INPUT", VERSION_LONG);
        assertError(multipart(HttpMethod.POST, url, Map.of("categoryId", cat, "worker", "  "), f),
                400, "INVALID_INPUT", WORKER_EMPTY);
        assertError(multipart(HttpMethod.POST, url, Map.of("categoryId", cat, "worker", "여섯글자이상"), f),
                400, "INVALID_INPUT", WORKER_LONG);
        assertError(multipart(HttpMethod.POST, url, Map.of("categoryId", UUID0, "worker", "김"), f),
                404, "NOT_FOUND", "카테고리를 찾을 수 없습니다.");
        assertError(multipart(HttpMethod.POST, url, Map.of("categoryId", "abc", "worker", "김"), f),
                404, "NOT_FOUND", "카테고리를 찾을 수 없습니다.");
        assertError(multipart(HttpMethod.POST, url, Map.of("worker", "김"), f), 400, "INVALID_INPUT", FORMAT);
        assertError(multipart(HttpMethod.POST, url, Map.of("categoryId", cat), f), 400, "INVALID_INPUT", FORMAT);
        assertError(multipartMulti(HttpMethod.POST, url, Map.of("categoryId", cat, "worker", "김"),
                Map.of("file", List.of(Part.of("a.pdf", "1"), Part.of("b.pdf", "2")))), 400, "INVALID_INPUT", FORMAT);
        assertEquals(0, storedFiles());
        assertEquals(0, storedBodies());

        // when & then: 첫 문서는 만들어지고 같은 카테고리의 두 번째 문서는 충돌이다
        assertEquals(201, uploadDoc(cat, "김").status());
        assertError(uploadDoc(cat, "김"), 409, "CONFLICT", HAS_DOC);
        assertEquals(1, storedFiles());
        assertEquals(1, storedBodies());
    }

    private static final String DOC_FILE_TYPE = ".hwpx 또는 .pdf 파일만 올릴 수 있습니다.";

    private Response createWith(String cat, String worker, String fileName) {
        return multipart(HttpMethod.POST, "/api/documents", Map.of("categoryId", cat, "worker", worker),
                Map.of("file", Part.of(fileName, "x")));
    }

    private Response revisionWith(String docId, String worker, String fileName, String revisionName) {
        return multipart(HttpMethod.POST, "/api/documents/" + docId + "/revisions", Map.of("worker", worker),
                Map.of("file", Part.of(fileName, "x"), "revision", Part.of(revisionName, "y")));
    }

    @Test
    void createDocAcceptsOnlyHwpxAndPdf() {
        // given
        int n = 0;

        // when & then: .pdf·.hwpx는 대소문자와 관계없이 만들어진다
        for (String name : List.of("a.pdf", "a.PDF", "a.hwpx", "a.HwPx")) {
            String catName = "ok" + n++;
            String cat = category(catName);
            Response r = createWith(cat, "김", name);
            assertEquals(201, r.status(), r.text());
            assertEquals(catName + " v1" + name.substring(1), r.json().get("versions").get(0).get("files").get(0).get("fileName").asString());
        }

        // when & then: 그 밖의 확장자는 거절되고 문서·파일·폴더가 남지 않는다
        long rows = storedFiles();
        long bodies = storedBodies();
        int docCount = docs().size();
        for (String name : List.of("a.txt", "a.exe", "a.EXE", "a.docx", "a", "a.", "dir/a.exe")) {
            String catName = "no" + n++;
            String cat = category(catName);
            assertError(createWith(cat, "김", name), 400, "INVALID_INPUT", DOC_FILE_TYPE);
            assertEquals(docCount, docs().size(), name);
            assertEquals(rows, storedFiles(), name);
            assertEquals(bodies, storedBodies(), name);
            assertFalse(java.nio.file.Files.exists(storageRoot().resolve(catName).resolve("v1")), name);
        }
    }

    @Test
    void createDocExtensionCheckOrder() {
        // given
        String cat = category("c");

        // when & then: 작업자 검사는 확장자보다 먼저고, 확장자 검사는 카테고리보다 먼저다
        assertError(createWith(cat, "", "a.exe"), 400, "INVALID_INPUT", WORKER_EMPTY);
        assertError(createWith(cat, "여섯글자이상", "a.exe"), 400, "INVALID_INPUT", WORKER_LONG);
        assertError(createWith(UUID0, "김", "a.exe"), 400, "INVALID_INPUT", DOC_FILE_TYPE);

        // when & then: 문서가 있는 카테고리에서도 확장자 검사가 먼저다
        assertEquals(201, uploadDoc(cat, "김").status());
        assertError(createWith(cat, "김", "a.exe"), 400, "INVALID_INPUT", DOC_FILE_TYPE);
        assertEquals(1, storedFiles());
        assertEquals(1, storedBodies());
    }

    @Test
    void uploadRevisionAcceptsOnlyHwpxAndPdf() {
        // given
        JsonNode d = newDoc("c");
        String id = docId(d);

        // when: 대소문자가 섞인 .hwpx·.pdf 쌍을 올린다
        Response ok = revisionWith(id, "김", "a.HWPX", "b.Pdf");

        // then: 쌍이 등록된다
        assertEquals(200, ok.status(), ok.text());
        assertEquals(2, ok.json().get("versions").get(0).get("files").size());

        // when & then: 둘 중 하나라도 다른 확장자면 거절되고 파일이 늘지 않는다
        long rows = storedFiles();
        long bodies = storedBodies();
        String[][] bad = {{"a.pdf", "b.txt"}, {"a.exe", "b.pdf"}, {"a.pdf", "b.EXE"}, {"a", "b.pdf"}, {"a.docx", "b.hwpx"}};
        for (String[] p : bad) {
            assertError(revisionWith(id, "김", p[0], p[1]), 400, "INVALID_INPUT", DOC_FILE_TYPE);
            assertEquals(rows, storedFiles());
            assertEquals(bodies, storedBodies());
        }
        assertEquals(2, docs().get(0).get("versions").get(0).get("files").size());
    }

    @Test
    void uploadRevisionExtensionCheckOrder() {
        // given
        JsonNode d = newDoc("c");
        String id = docId(d);
        String url = "/api/documents/" + id + "/revisions";

        // when & then: 쌍·작업자·문서 검사가 확장자보다 먼저다
        assertError(multipart(HttpMethod.POST, url, Map.of("worker", "김"), Map.of("file", Part.of("a.exe", "x"))),
                400, "INVALID_INPUT", "수정본은 수정대조표와 함께 올려야 등록됩니다.");
        assertError(revisionWith(id, "", "a.exe", "b.exe"), 400, "INVALID_INPUT", WORKER_EMPTY);
        assertError(revisionWith(UUID0, "김", "a.exe", "b.exe"), 404, "NOT_FOUND", DOC_404);

        // when & then: 검토대기로 바꾸면 단계 검사가 확장자보다 먼저다
        fixture(vid(d), "queued");
        assertError(revisionWith(id, "김", "a.exe", "b.exe"), 409, "CONFLICT",
                "검토대기 단계에서는 파일을 바꿀 수 없습니다. 작업완료를 취소해 작업중으로 돌아가야 합니다.");
    }

    @Test
    void createDocTooLargeIs413() {
        // given
        String cat = category("c");

        // when
        Response r = multipart(HttpMethod.POST, "/api/documents", Map.of("categoryId", cat, "worker", "김"),
                Map.of("file", new Part("big.pdf", new byte[2048], "application/octet-stream")));

        // then
        assertError(r, 413, "PAYLOAD_TOO_LARGE", "파일이 너무 큽니다. 100MB까지 올릴 수 있습니다.");
        assertEquals(0, docs().size());
    }

    @Test
    void concurrentCreateDocOnSameCategoryAllowsOne() throws Exception {
        // given
        String cat = category("c");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> fs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Callable<Integer> job = () -> {
                go.await();
                return uploadDoc(cat, "김").status();
            };
            fs.add(pool.submit(job));
        }

        // when
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : fs) statuses.add(f.get());
        pool.shutdown();

        // then
        assertEquals(List.of(201, 409), statuses.stream().sorted().toList());
        assertEquals(1, docs().size());
        assertEquals(1, storedFiles());
    }

    // ---- 8.7 listDocs ----

    @Test
    void listDocsOrderedByCreation() {
        // given
        JsonNode a = newDoc("첫째");
        JsonNode b = newDoc("둘째");

        // when
        JsonNode list = docs();

        // then
        assertEquals(2, list.size());
        assertEquals(docId(a), list.get(0).get("id").asString());
        assertEquals(docId(b), list.get(1).get("id").asString());
    }

    @Test
    void listDocsIsEmptyArrayWithoutDocs() {
        // when & then
        assertEquals("[]", get("/api/documents").text());
    }

    @Test
    void listDocsShowsReviewRecordsWithoutNulls() {
        // given
        JsonNode d = newDoc("c");
        fixture(vid(d), "rejected");

        // when
        Response r = get("/api/documents");

        // then
        assertFalse(r.text().contains(":null"), r.text());
        JsonNode v = r.json().get(0).get("versions").get(0);
        assertFalse(v.get("workDone").has("worker"));
        assertTrue(v.get("workDone").has("completedAt"));
        assertEquals("이영희", v.get("reviewStart").get("reviewers").get(0).asString());
        assertEquals("reject", v.get("review").get("result").asString());
        assertEquals("이영희", v.get("review").get("reviewers").get(0).asString());
        assertFalse(v.get("review").has("fileId"));
        assertFalse(v.has("sentAt"));
        assertTrue(v.get("reviews").isArray());
        assertEquals(0, v.get("reviews").size());
    }

    // ---- 8.5 assignCategory ----

    /** 카테고리를 지워 미분류가 된 문서. */
    private JsonNode unclassified(String name) {
        JsonNode d = newDoc(name);
        assertEquals(204, delete("/api/categories/" + d.get("categoryId").asString()).status());
        return reload(d);
    }

    @Test
    void deletingCategoryMakesDocUnclassifiedAndKeepsTitle() {
        // when
        JsonNode d = unclassified("계약서");

        // then
        assertTrue(d.has("categoryId"));
        assertTrue(d.get("categoryId").isNull());
        assertEquals("계약서", d.get("title").asString());
        assertEquals(1, d.get("versions").size());
        assertEquals(1, get("/api/documents").text().split(":null", -1).length - 1);
    }

    @Test
    void renamingCategorySyncsDocTitle() {
        // given
        JsonNode d = newDoc("옛이름");

        // when & then
        assertEquals(200, put("/api/categories/" + d.get("categoryId").asString(),
                Map.of("name", "새이름")).status());
        assertEquals("새이름", reload(d).get("title").asString());
    }

    @Test
    void assignExistingCategory() {
        // given
        JsonNode d = unclassified("옛");
        String cat = category("대상");

        // when
        Response r = post(docUrl(d, "/category"), Map.of("categoryId", cat));

        // then
        assertEquals(200, r.status(), r.text());
        assertEquals(cat, r.json().get("categoryId").asString());
        assertEquals("대상", r.json().get("title").asString());
        assertEquals(1, r.json().get("versions").size());
    }

    @Test
    void assignNewCategory() {
        // given
        JsonNode d = unclassified("옛");

        // when
        Response r = post(docUrl(d, "/category"), Map.of("newCategory", Map.of("name", " 신규 ")));

        // then
        assertEquals(200, r.status(), r.text());
        assertEquals("신규", r.json().get("title").asString());
        Response cats = get("/api/categories");
        assertEquals("신규", cats.json().get(0).get("name").asString());
        assertEquals(r.json().get("categoryId").asString(), cats.json().get(0).get("id").asString());
    }

    @Test
    void assignNewCategoryIgnoresUnknownColorKey() {
        // given
        JsonNode d = unclassified("옛");

        // when
        Response r = post(docUrl(d, "/category"), Map.of("newCategory", Map.of("name", "n", "color", "#1")));

        // then
        assertEquals(200, r.status(), r.text());
        assertEquals("n", r.json().get("title").asString());
    }

    @Test
    void assignRejections() {
        // given
        JsonNode d = unclassified("옛");
        String occupied = category("찬곳");

        // when & then: '찬곳'에 문서를 만든다
        assertEquals(201, uploadDoc(occupied, "김").status());

        // when & then: 없는 문서·카테고리, 찬 카테고리, 잘못된 새 카테고리·형식은 거절한다
        String free = category("빈곳");
        category("같은이름");

        assertError(post("/api/documents/" + UUID0 + "/category", Map.of("categoryId", free)), 404, "NOT_FOUND", DOC_404);
        assertError(post("/api/documents/abc/category", Map.of("categoryId", free)), 404, "NOT_FOUND", DOC_404);
        assertError(post(docUrl(d, "/category"), Map.of("categoryId", UUID0)), 404, "NOT_FOUND", "카테고리를 찾을 수 없습니다.");
        assertError(post(docUrl(d, "/category"), Map.of("categoryId", "abc")), 404, "NOT_FOUND", "카테고리를 찾을 수 없습니다.");
        assertError(post(docUrl(d, "/category"), Map.of("categoryId", occupied)), 409, "CONFLICT", HAS_DOC);
        assertError(post(docUrl(d, "/category"), Map.of("newCategory", Map.of("name", "  "))),
                400, "INVALID_INPUT", "이름을 입력하세요.");
        assertError(post(docUrl(d, "/category"), Map.of("newCategory", Map.of("name", "같은이름"))),
                409, "CONFLICT", "같은 이름의 카테고리가 있습니다.");
        assertError(post(docUrl(d, "/category"), Map.of("categoryId", free, "newCategory", Map.of("name", "x"))),
                400, "INVALID_INPUT", FORMAT);
        assertError(post(docUrl(d, "/category"), Map.of()), 400, "INVALID_INPUT", FORMAT);
        assertError(post(docUrl(d, "/category"), "{broken"), 400, "INVALID_INPUT", FORMAT);

        // when & then: 한 번 분류한 뒤에는 옮길 수 없다
        assertEquals(200, post(docUrl(d, "/category"), Map.of("categoryId", free)).status());
        assertError(post(docUrl(d, "/category"), Map.of("categoryId", category("또"))),
                409, "CONFLICT", "이미 분류가 있는 문서는 옮길 수 없습니다.");
        assertEquals(0, jdbc.sql("SELECT count(*) FROM categories WHERE name = 'x'").query(Long.class).single());
    }

    // ---- 8.9 uploadWorkingFile ----

    @Test
    void uploadRevisionAddsPairWithUploader() {
        // given
        JsonNode d = newDoc("c");

        // when: 수정본 쌍을 올린다
        Response r = addRevision(docId(d), " 박민수 ");

        // then: 올린 사람과 파일 이름이 남는다
        assertEquals(200, r.status(), r.text());
        JsonNode files = r.json().get("versions").get(0).get("files");
        assertEquals(2, files.size());
        assertEquals("c v1.hwpx", files.get(1).get("fileName").asString());
        String day = java.time.format.DateTimeFormatter.BASIC_ISO_DATE.format(java.time.LocalDate.ofInstant(
                java.time.Instant.parse(files.get(1).get("uploadedAt").asString()), java.time.ZoneId.systemDefault()));
        assertEquals("c v1_" + day + "_박민수.hwpx", files.get(1).get("downloadName").asString());
        assertEquals("박민수", files.get(1).get("uploadedBy").asString());
        assertEquals("c v1 수정대조표.pdf", files.get(1).get("revision").get("fileName").asString());
        assertEquals("박민수", files.get(1).get("revision").get("uploadedBy").asString());
        assertFalse(files.get(1).get("revision").has("revision"));

        // when & then: 한 쌍 더 올리면 파일이 셋이다
        assertEquals(200, addRevision(docId(d), "김").status());
        assertEquals(3, reload(d).get("versions").get(0).get("files").size());
    }

    @Test
    void uploadRevisionRejections() throws Exception {
        // given
        JsonNode d = newDoc("c");
        String id = docId(d);

        // when & then: 잘못된 작업자·문서·파일 구성은 거절되고 파일이 늘지 않는다
        assertError(addRevision(id, ""), 400, "INVALID_INPUT", WORKER_EMPTY);
        assertError(addRevision(id, "여섯글자이상"), 400, "INVALID_INPUT", WORKER_LONG);
        assertError(addRevision(UUID0, "김"), 404, "NOT_FOUND", DOC_404);
        assertError(addRevision("abc", "김"), 404, "NOT_FOUND", DOC_404);
        assertError(multipart(HttpMethod.POST, "/api/documents/" + id + "/revisions", Map.of("worker", "김"),
                Map.of("file", Part.of("a.pdf", "x"))), 400, "INVALID_INPUT", "수정본은 수정대조표와 함께 올려야 등록됩니다.");
        assertError(multipart(HttpMethod.POST, "/api/documents/" + id + "/revisions", Map.of("worker", "김"),
                Map.of("revision", Part.of("a.pdf", "x"))), 400, "INVALID_INPUT", "수정본은 수정대조표와 함께 올려야 등록됩니다.");
        assertError(multipartMulti(HttpMethod.POST, "/api/documents/" + id + "/revisions", Map.of("worker", "김"),
                Map.of("file", List.of(Part.of("a.pdf", "1"), Part.of("b.pdf", "2")), "revision", List.of(Part.of("c.pdf", "3")))),
                400, "INVALID_INPUT", FORMAT);
        assertEquals(1, storedFiles());
        assertEquals(1, storedBodies());

        // when & then: 검토대기
        String lock = " 단계에서는 파일을 바꿀 수 없습니다. 작업완료를 취소해 작업중으로 돌아가야 합니다.";
        fixture(vid(d), "queued");
        assertError(addRevision(id, "김"), 409, "CONFLICT", "검토대기" + lock);

        // when & then: 검토중
        jdbc.sql("UPDATE document_versions SET review_started_at = CURRENT_TIMESTAMP(6) WHERE id = :v").param("v", vid(d)).update();
        assertError(addRevision(id, "김"), 409, "CONFLICT", "검토중" + lock);

        // when & then: 발송대기
        jdbc.sql("UPDATE document_versions SET review_result = 'accept', reviewed_at = CURRENT_TIMESTAMP(6) WHERE id = :v").param("v", vid(d)).update();
        assertError(addRevision(id, "김"), 409, "CONFLICT", "발송대기" + lock);

        // when & then: 발송됨
        jdbc.sql("UPDATE document_versions SET sent_at = CURRENT_TIMESTAMP(6), sent_to = '', sent_memo = '' WHERE id = :v").param("v", vid(d)).update();
        assertError(addRevision(id, "김"), 409, "CONFLICT", SENT_EDIT);
        assertEquals(1, storedFiles());
        assertEquals(1, storedBodies());
    }

    @Test
    void uploadRevisionAllowedAfterRejection() {
        // given
        JsonNode d = newDoc("c");
        fixture(vid(d), "rejected");

        // when & then
        assertEquals(200, addRevision(docId(d), "김").status());
    }

    // ---- 8.10 deleteLatestRevision ----

    @Test
    void deleteLatestRevisionRemovesPairAndBodies() throws Exception {
        // given
        JsonNode d = newDoc("c");

        // when: 수정본 쌍을 두 번 올린다
        addRevision(docId(d), "김");
        JsonNode two = addRevision(docId(d), "김").json();

        // then: 원본과 두 쌍이 저장된다
        JsonNode files = two.get("versions").get(0).get("files");
        String rev1 = files.get(1).get("id").asString();
        String rev2 = files.get(2).get("id").asString();
        String rev2Diff = files.get(2).get("revision").get("id").asString();
        assertEquals(5, storedFiles());

        // when: 최신 수정본을 지운다
        Response r = delete(docUrl(d, "/revisions/" + rev2));

        // then: 그 쌍과 본문만 지워진다
        assertEquals(200, r.status(), r.text());
        assertEquals(2, r.json().get("versions").get(0).get("files").size());
        assertEquals(3, storedFiles());
        assertEquals(3, storedBodies());
        assertEquals(404, get("/api/files/" + rev2).status());
        assertEquals(404, get("/api/files/" + rev2Diff).status());
        assertEquals(200, get("/api/files/" + rev1).status());
    }

    @Test
    void deleteLatestRevisionRejections() {
        // given
        JsonNode d = newDoc("c");
        String orig = d.get("versions").get(0).get("files").get(0).get("id").asString();
        String id = docId(d);
        String latestOnly = "가장 최신 수정본만 삭제할 수 있습니다.";

        // when & then: 없는 문서, 원본, 없는 수정본은 지울 수 없다
        assertError(delete("/api/documents/" + UUID0 + "/revisions/" + orig), 404, "NOT_FOUND", DOC_404);
        assertError(delete("/api/documents/abc/revisions/" + orig), 404, "NOT_FOUND", DOC_404);
        assertError(delete(docUrl(d, "/revisions/" + orig)), 409, "CONFLICT", "원본은 삭제할 수 없습니다.");
        assertError(delete(docUrl(d, "/revisions/" + UUID0)), 409, "CONFLICT", latestOnly);
        assertError(delete(docUrl(d, "/revisions/abc")), 409, "CONFLICT", latestOnly);

        // when & then: 수정본 쌍을 두 번 올리면 최신이 아닌 수정본과 원본은 지울 수 없다
        JsonNode withTwo = addRevision(id, "김").json();
        String first = withTwo.get("versions").get(0).get("files").get(1).get("id").asString();
        JsonNode withThree = addRevision(id, "김").json();
        assertError(delete(docUrl(d, "/revisions/" + first)), 409, "CONFLICT", latestOnly);
        assertError(delete(docUrl(d, "/revisions/" + orig)), 409, "CONFLICT", "원본은 삭제할 수 없습니다.");
        String last = withThree.get("versions").get(0).get("files").get(2).get("id").asString();

        // when & then: 검토대기
        fixture(vid(d), "queued");
        assertError(delete(docUrl(d, "/revisions/" + last)), 409, "CONFLICT",
                "검토대기 단계에서는 파일을 바꿀 수 없습니다. 작업완료를 취소해 작업중으로 돌아가야 합니다.");

        // when & then: 발송됨
        jdbc.sql("UPDATE document_versions SET review_started_at = CURRENT_TIMESTAMP(6), review_result = 'accept', reviewed_at = CURRENT_TIMESTAMP(6), sent_at = CURRENT_TIMESTAMP(6), sent_to = '', sent_memo = '' WHERE id = :v")
                .param("v", vid(d)).update();
        assertError(delete(docUrl(d, "/revisions/" + last)), 409, "CONFLICT", SENT_EDIT);
        assertEquals(5, storedFiles());
    }

    // ---- 8.16 renameVersion ----

    @Test
    void renameVersionStoresNameOrDropsDefault() {
        // given
        JsonNode d = newDoc("c");
        String v = vid(d);
        String url = docUrl(d, "/versions/" + v);

        // when & then: 이름은 다듬어 저장하고 기본 이름 'v1'은 지운다
        assertEquals("최종", versions(patch(url, Map.of("name", " 최종 "))).get(0).get("name").asString());
        assertFalse(versions(patch(url, Map.of("name", "v1"))).get(0).has("name"));

        // when & then: 빈 이름도 지운다
        patch(url, Map.of("name", "최종"));
        assertFalse(versions(patch(url, Map.of("name", ""))).get(0).has("name"));

        // when & then: 5자까지 받는다
        assertEquals("abcde", versions(patch(url, Map.of("name", "abcde"))).get(0).get("name").asString());
    }

    @Test
    void renameVersionRejections() {
        // given
        JsonNode d = newDoc("c");
        JsonNode other = newDoc("o");
        String v = vid(d);
        String url = docUrl(d, "/versions/" + v);

        // when & then: 없는 문서·버전, 긴 이름, 형식 오류는 거절한다
        assertError(patch("/api/documents/" + UUID0 + "/versions/" + v, Map.of("name", "a")), 404, "NOT_FOUND", DOC_404);
        assertError(patch(docUrl(d, "/versions/" + UUID0), Map.of("name", "a")), 404, "NOT_FOUND", VER_404);
        assertError(patch(docUrl(d, "/versions/abc"), Map.of("name", "a")), 404, "NOT_FOUND", VER_404);
        assertError(patch(docUrl(d, "/versions/" + vid(other)), Map.of("name", "a")), 404, "NOT_FOUND", VER_404);
        assertError(patch(url, Map.of("name", "여섯글자이상")), 400, "INVALID_INPUT", VERSION_LONG);
        assertError(patch(url, Map.of()), 400, "INVALID_INPUT", FORMAT);

        // when & then: 발송된 버전의 이름은 바꿀 수 없다
        fixture(v, "sent");
        assertError(patch(url, Map.of("name", "a")), 409, "CONFLICT", "발송된 버전의 이름은 바꿀 수 없습니다.");

        // when & then: 새 버전 이름을 기존 버전 이름으로 바꿀 수 없다
        assertEquals(200, startVersion(d, "둘째", "김").status());
        JsonNode v2 = lastVersion(reload(d));
        assertError(patch(docUrl(d, "/versions/" + v2.get("id").asString()), Map.of("name", "v1")),
                409, "CONFLICT", DUP);
    }

    // ---- 8.17 startVersion ----

    @Test
    void startVersionTakesLastSubmittedFileAsOriginal() {
        // given
        JsonNode d = newDoc("c");
        JsonNode withRev = addRevision(docId(d), "김").json();
        String submitted = withRev.get("versions").get(0).get("files").get(1).get("id").asString();
        fixture(vid(d), "sent");

        // when
        Response r = startVersion(d, "", " 박민수 ");

        // then
        assertEquals(200, r.status(), r.text());
        JsonNode v2 = r.json().get("versions").get(1);
        assertEquals(2, v2.get("number").asInt());
        assertFalse(v2.has("name"));
        assertEquals("박민수", v2.get("worker").asString());
        assertEquals("working", v2.get("status").asString());
        assertEquals(1, v2.get("files").size());
        assertEquals(submitted, v2.get("files").get(0).get("id").asString());
        assertFalse(v2.get("files").get(0).has("revision"));
        assertEquals("sent", r.json().get("versions").get(0).get("status").asString());
        assertFalse(r.text().contains(":null"));
    }

    @Test
    void startVersionRejections() {
        // given
        JsonNode d = newDoc("c");

        // when & then: 작업자·문서·형식 오류와 발송 전 버전이 있으면 거절한다
        assertError(startVersion(d, "", ""), 400, "INVALID_INPUT", WORKER_EMPTY);
        assertError(startVersion(d, "", "여섯글자이상"), 400, "INVALID_INPUT", WORKER_LONG);
        assertError(post("/api/documents/" + UUID0 + "/versions", Map.of("versionName", "", "worker", "김")),
                404, "NOT_FOUND", DOC_404);
        assertError(post(docUrl(d, "/versions"), Map.of("worker", "김")), 400, "INVALID_INPUT", FORMAT);
        assertError(startVersion(d, "", "김"), 409, "CONFLICT", "발송 전인 버전이 있습니다. 발송 후에 새 버전을 시작할 수 있습니다.");

        // when & then: 발송 뒤에도 긴 이름과 같은 이름은 거절하고 버전이 늘지 않는다
        fixture(vid(d), "sent");
        assertError(startVersion(d, "여섯글자이상", "김"), 400, "INVALID_INPUT", VERSION_LONG);
        assertError(startVersion(d, "v1", "김"), 409, "CONFLICT", DUP);
        assertEquals(1, reload(d).get("versions").size());
    }

    @Test
    void startVersionRejectsNameEqualToExistingCustomName() {
        // given
        JsonNode d = newDoc("c");
        patch(docUrl(d, "/versions/" + vid(d)), Map.of("name", "최종"));
        fixture(vid(d), "sent");

        // when & then: 바꾼 이름과 같으면 거절한다
        assertError(startVersion(d, "최종", "김"), 409, "CONFLICT", DUP);

        // when & then: 다른 이름 'v2'는 받는다
        assertEquals(200, startVersion(d, "v2", "김").status());
    }

    // ---- 8.18 deleteVersion ----

    @Test
    void deleteVersionKeepsSharedOriginal() throws Exception {
        // given
        JsonNode d = newDoc("c");

        // when: v1에 수정본을 올려 발송하고 v2를 시작해 수정본을 올린다
        String shared = addRevision(docId(d), "김").json().get("versions").get(0).get("files").get(1).get("id").asString();
        fixture(vid(d), "sent");
        startVersion(d, "", "김");
        JsonNode v2 = addRevision(docId(d), "김").json();

        // then: 파일이 다섯 개다
        String v2Rev = v2.get("versions").get(1).get("files").get(1).get("id").asString();
        assertEquals(5, storedFiles());

        // when: v2를 지운다
        Response r = delete(docUrl(d, "/versions/" + vid(v2)));

        // then: v1과 공유하는 원본은 남고 v2 수정본만 지워진다
        assertEquals(200, r.status(), r.text());
        assertEquals(1, r.json().get("versions").size());
        assertEquals(3, storedFiles());
        assertEquals(3, storedBodies());
        assertEquals(200, get("/api/files/" + shared).status());
        assertEquals(404, get("/api/files/" + v2Rev).status());
        assertEquals(1, reload(d).get("versions").size());
    }

    @Test
    void deletingOnlyVersionRemovesDocAndFreesCategory() throws Exception {
        // given
        JsonNode d = newDoc("c");

        // when: 하나뿐인 버전을 지운다
        Response r = delete(docUrl(d, "/versions/" + vid(d)));

        // then: 문서와 파일이 모두 지워진다
        assertEquals(204, r.status());
        assertEquals(0, r.bytes().length);
        assertEquals(0, docs().size());
        assertEquals(0, storedFiles());
        assertEquals(0, storedBodies());

        // when & then: 그 카테고리에 다시 문서를 만들 수 있다
        assertEquals(201, uploadDoc(d.get("categoryId").asString(), "김").status());
    }

    @Test
    void deleteVersionRejections() {
        // given
        JsonNode d = newDoc("c");
        JsonNode other = newDoc("o");
        String v = vid(d);

        // when & then: 없는 문서·버전과 다른 문서의 버전은 찾을 수 없다
        assertError(delete("/api/documents/" + UUID0 + "/versions/" + v), 404, "NOT_FOUND", DOC_404);
        assertError(delete(docUrl(d, "/versions/" + UUID0)), 404, "NOT_FOUND", VER_404);
        assertError(delete(docUrl(d, "/versions/abc")), 404, "NOT_FOUND", VER_404);
        assertError(delete(docUrl(d, "/versions/" + vid(other))), 404, "NOT_FOUND", VER_404);

        // when & then: 발송된 버전은 지울 수 없다
        fixture(v, "sent");
        assertError(delete(docUrl(d, "/versions/" + v)), 409, "CONFLICT", "발송된 버전은 삭제할 수 없습니다.");
        assertEquals(1, reload(d).get("versions").size());
    }

    @Test
    void deleteOlderVersionIsRejectedAsNotLatest() {
        // given
        JsonNode d = newDoc("c");
        String v1 = vid(d);
        // v1은 발송 전으로 두고 더 새로운 발송 버전 v2를 SQL로 만든다(정상 흐름에서는 나올 수 없는 상태)
        jdbc.sql("""
                INSERT INTO document_versions (id, document_id, number, label, dir_name, created_at, work_done_at,
                  review_started_at, review_result, reviewed_at, sent_at, sent_to, sent_memo)
                VALUES (:id, :d, 2, 'v2', 'v2', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6),
                  'accept', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), '', '')""")
                .param("id", newId()).param("d", docId(d)).update();

        // when & then
        // v2는 발송됐고 v1은 발송 전이므로 v1 삭제는 '최신 아님'
        assertError(delete(docUrl(d, "/versions/" + v1)), 409, "CONFLICT", "최신 버전만 삭제할 수 있습니다.");
    }

    // ---- 파일 없이 문서 만들기(D 결정 2026-11-24) ----

    private static final String ORIGINAL_ONLY = "원본 파일 하나만 올리세요.";
    private static final String ORIGINAL_REQUIRED = "원본 파일을 올리세요.";

    private JsonNode emptyDoc(String catName) {
        Response r = multipart(HttpMethod.POST, "/api/documents", Map.of("categoryId", category(catName), "worker", "김철수"), Map.of());
        assertEquals(201, r.status(), r.text());
        return r.json();
    }

    private Response revisions(JsonNode doc, Map<String, Part> parts) {
        return multipart(HttpMethod.POST, docUrl(doc, "/revisions"), Map.of("worker", "이영희"), parts);
    }

    @Test
    void createDocWithoutFileMakesEmptyV1() {
        // when
        JsonNode d = emptyDoc("빈문서");

        // then
        JsonNode v = d.get("versions").get(0);
        assertEquals(1, v.get("number").asInt());
        assertEquals("김철수", v.get("worker").asString());
        assertEquals(0, v.get("files").size());
        assertEquals(0, storedFiles());
        assertTrue(java.nio.file.Files.isDirectory(storageRoot().resolve("빈문서").resolve("v1")));
        assertEquals(0, reload(d).get("versions").get(0).get("files").size());
    }

    @Test
    void uploadOriginalToEmptyVersion() {
        // given
        JsonNode d = emptyDoc("원본");

        // when & then: 원본 하나가 아니거나 형식이 다르면 거절되고 파일이 남지 않는다
        assertError(revisions(d, Map.of("file", Part.of("a.pdf", "x"), "revision", Part.of("b.pdf", "y"))),
                400, "INVALID_INPUT", ORIGINAL_ONLY);
        assertError(revisions(d, Map.of("revision", Part.of("b.pdf", "y"))), 400, "INVALID_INPUT", ORIGINAL_ONLY);
        assertError(revisions(d, Map.of()), 400, "INVALID_INPUT", ORIGINAL_REQUIRED);
        assertError(revisions(d, Map.of("file", Part.of("a.txt", "x"))), 400, "INVALID_INPUT", DOC_FILE_TYPE);
        assertEquals(0, storedFiles());
        assertEquals(0, storedBodies());

        // when: 원본 하나를 올린다
        Response ok = revisions(d, Map.of("file", Part.of("a.hwpx", "x")));

        // then: 대조표 없이 원본으로 저장된다
        assertEquals(200, ok.status(), ok.text());
        JsonNode files = lastVersion(ok.json()).get("files");
        assertEquals(1, files.size());
        assertEquals("원본 v1.hwpx", files.get(0).get("fileName").asString());
        assertEquals("이영희", files.get(0).get("uploadedBy").asString());
        assertFalse(files.get(0).has("revision"));
        assertTrue(java.nio.file.Files.exists(storageRoot().resolve("원본").resolve("v1")
                .resolve(files.get(0).get("downloadName").asString())));

        // when & then: 원본이 생긴 뒤에는 지금의 수정본+대조표 쌍 규칙이다
        assertError(revisions(d, Map.of("file", Part.of("a.pdf", "x"))), 400, "INVALID_INPUT",
                "수정본은 수정대조표와 함께 올려야 등록됩니다.");

        // when: 수정본 쌍을 올린다
        Response pair = addRevision(docId(d), "박");

        // then: 쌍이 edit1 폴더에 등록된다
        assertEquals(200, pair.status(), pair.text());
        JsonNode fs = lastVersion(pair.json()).get("files");
        assertEquals(2, fs.size());
        assertEquals("원본 v1 수정대조표.pdf", fs.get(1).get("revision").get("fileName").asString());
        assertTrue(java.nio.file.Files.isDirectory(storageRoot().resolve("원본").resolve("v1").resolve("edit1")));
    }

    @Test
    void emptyDocListDeleteRevisionRenameCategoryAndDelete() {
        // given
        JsonNode d = emptyDoc("목록");
        String cat = d.get("categoryId").asString();

        // when & then: 목록에 파일 없는 버전으로 보인다
        assertEquals(0, docs().get(0).get("versions").get(0).get("files").size());

        // when & then: 지울 수정본이 없다
        assertError(delete(docUrl(d, "/revisions/" + UUID0)), 409, "CONFLICT", "가장 최신 수정본만 삭제할 수 있습니다.");

        // when: 카테고리 이름을 바꾼다
        Response renamed = put("/api/categories/" + cat, Map.of("name", "새목록"));

        // then: 제목과 폴더가 따라 바뀐다
        assertEquals(200, renamed.status(), renamed.text());
        assertEquals("새목록", reload(d).get("title").asString());
        assertTrue(java.nio.file.Files.isDirectory(storageRoot().resolve("새목록").resolve("v1")));

        // when: 하나뿐인 버전을 지운다
        Response gone = delete(docUrl(d, "/versions/" + vid(d)));

        // then: 문서가 없어진다
        assertEquals(204, gone.status(), gone.text());
        assertEquals(0, docs().size());
    }
}
