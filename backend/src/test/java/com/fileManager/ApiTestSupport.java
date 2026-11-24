package com.fileManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 실제 Tomcat(RANDOM_PORT)에 HTTP로 호출하는 통합 테스트 기반. 매 테스트 전에 DB와 파일 저장소를 비운다. */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.context.annotation.Import(ApiTestSupport.Probe.class)
public abstract class ApiTestSupport {
    public record Response(int status, byte[] bytes, HttpHeaders headers) {
        public String text() { return new String(bytes, java.nio.charset.StandardCharsets.UTF_8); }
        public JsonNode json() { return MAPPER.readTree(text()); }
    }

    /** 파일 파트(이름은 Map 키). */
    public record Part(String fileName, byte[] bytes, String contentType) {
        public static Part of(String fileName, String content) {
            return new Part(fileName, content.getBytes(java.nio.charset.StandardCharsets.UTF_8), "text/plain");
        }
    }

    static final JsonMapper MAPPER = JsonMapper.builder().build();

    @LocalServerPort protected int port;
    @Autowired protected JdbcClient jdbc;
    @Value("${filemanager.storage-root}") String storageRootPath;

    private RestClient client;

    @BeforeEach
    void resetAll() throws IOException {
        // FK 순서대로 지운다(H2와 MySQL 모두에서 도는 DELETE)
        for (String t : List.of("schedules", "schedule_groups", "review_round_reviewers", "version_review_rounds", "version_reviewers", "version_files", "category_resources",
                "document_versions", "documents", "stored_files", "categories")) {
            jdbc.sql("DELETE FROM " + t).update();
        }
        // 설정 행은 V3 마이그레이션이 만든 한 행이라 지우지 않고 초기값으로 되돌린다
        jdbc.sql("UPDATE schedule_settings SET layout = 'group', next_color_no = 0").update();
        Path root = storageRoot();
        if (Files.isDirectory(root)) {
            try (Stream<Path> s = Files.list(root)) {
                for (Path p : s.toList()) deleteTree(p);
            }
        }
    }

    private static void deleteTree(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }

    // ---- 저장소 공용 헬퍼 ----

    protected Path storageRoot() { return Path.of(storageRootPath).toAbsolutePath().normalize(); }

    protected long storedFileRows() {
        return jdbc.sql("SELECT count(*) FROM stored_files").query(Long.class).single();
    }

    /** 저장 루트 아래 일반 파일 수(재귀). */
    protected long storedBodies() {
        Path root = storageRoot();
        if (!Files.isDirectory(root)) return 0;
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    protected String newId() { return java.util.UUID.randomUUID().toString(); }

    protected Response call(HttpMethod method, String path, MediaType type, Object body, Map<String, String> headers) {
        if (client == null) {
            client = RestClient.builder().baseUrl("http://localhost:" + port)
                    .defaultStatusHandler(s -> true, (req, res) -> {}).build();
        }
        RestClient.RequestBodySpec spec = client.method(method).uri(path).headers(h -> headers.forEach(h::set));
        if (type != null) spec.contentType(type);
        if (body != null) spec.body(body);
        return spec.exchange((req, res) -> new Response(res.getStatusCode().value(),
                res.getBody().readAllBytes(), res.getHeaders()));
    }

    protected Response get(String path) { return call(HttpMethod.GET, path, null, null, Map.of()); }
    protected Response delete(String path) { return call(HttpMethod.DELETE, path, null, null, Map.of()); }
    protected Response json(HttpMethod m, String path, Object body) {
        return call(m, path, MediaType.APPLICATION_JSON,
                body instanceof String s ? s : MAPPER.writeValueAsString(body), Map.of());
    }
    protected Response post(String path, Object body) { return json(HttpMethod.POST, path, body); }
    protected Response put(String path, Object body) { return json(HttpMethod.PUT, path, body); }
    protected Response patch(String path, Object body) { return json(HttpMethod.PATCH, path, body); }

    protected Response multipart(HttpMethod m, String path, Map<String, String> fields, Map<String, Part> files) {
        return multipartMulti(m, path, fields, files == null ? Map.of() : toLists(files));
    }

    /** 거절: multipart(decision=reject, reviewer, file=검토본). file이 null이면 파일 파트 없이 보낸다. */
    protected Response reject(String docId, String reviewer, Part file) {
        return multipart(HttpMethod.POST, "/api/documents/" + docId + "/review-decision",
                Map.of("decision", "reject", "reviewer", reviewer), file == null ? Map.of() : Map.of("file", file));
    }

    /** 같은 이름의 파일 파트를 여러 개 보낼 때. */
    protected Response multipartMulti(HttpMethod m, String path, Map<String, String> fields, Map<String, List<Part>> files) {
        MultiValueMap<String, Object> mv = new LinkedMultiValueMap<>();
        if (fields != null) fields.forEach(mv::add);
        files.forEach((name, parts) -> parts.forEach(p -> {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.parseMediaType(p.contentType()));
            mv.add(name, new HttpEntity<>(new ByteArrayResource(p.bytes()) {
                @Override public String getFilename() { return p.fileName(); }
            }, h));
        }));
        return call(m, path, MediaType.MULTIPART_FORM_DATA, mv, Map.of());
    }

    private static Map<String, List<Part>> toLists(Map<String, Part> files) {
        Map<String, List<Part>> r = new LinkedHashMap<>();
        files.forEach((k, v) -> r.put(k, List.of(v)));
        return r;
    }

    protected void assertError(Response r, int status, String code, String message) {
        assertEquals(status, r.status(), r.text());
        JsonNode j = r.json();
        assertEquals(code, j.get("code").asString());
        assertEquals(message, j.get("message").asString());
    }

    // ---- 문서 테스트 공용 헬퍼 ----

    protected String category(String name) {
        return post("/api/categories", Map.of("name", name)).json().get("id").asString();
    }

    protected Response uploadDoc(String categoryId, String worker) {
        return multipart(HttpMethod.POST, "/api/documents", Map.of("categoryId", categoryId, "worker", worker),
                Map.of("file", Part.of("원본.pdf", "original")));
    }

    /** 카테고리를 새로 만들고 그 문서를 올린다(응답은 201 Doc). */
    protected JsonNode newDoc(String categoryName) {
        Response r = uploadDoc(category(categoryName), "김철수");
        assertEquals(201, r.status(), r.text());
        return r.json();
    }

    protected Response addRevision(String docId, String worker) {
        return multipart(HttpMethod.POST, "/api/documents/" + docId + "/revisions", Map.of("worker", worker),
                Map.of("file", Part.of("수정본.hwpx", "revised"), "revision", Part.of("대조표.pdf", "diff")));
    }

    protected static String latestVersionId(JsonNode doc) {
        JsonNode vs = doc.get("versions");
        return vs.get(vs.size() - 1).get("id").asString();
    }

    /** 단계를 SQL로 직접 맞춘다(단계 전이 API에 기대지 않는다). stage: queued, reviewing, ready, rejected, sent. */
    protected void fixture(String versionId, String stage) {
        String now = "CURRENT_TIMESTAMP(6)";
        String set = "work_done_at = " + now;
        if (!stage.equals("queued")) set += ", review_started_at = " + now;
        if (stage.equals("ready") || stage.equals("sent")) set += ", review_result = 'accept', reviewed_at = " + now;
        if (stage.equals("rejected")) set += ", review_result = 'reject', reviewed_at = " + now;
        if (stage.equals("sent")) set += ", sent_at = " + now + ", sent_to = '기관', sent_memo = ''";
        jdbc.sql("UPDATE document_versions SET " + set + " WHERE id = :v").param("v", versionId).update();
        if (!stage.equals("queued")) {
            jdbc.sql("INSERT INTO version_reviewers (version_id, reviewer_order, name) VALUES (:v, 0, '이영희')")
                    .param("v", versionId).update();
        }
    }

    /** 415·405·파트 누락을 일으키기 위한 테스트 전용 엔드포인트. */
    @org.springframework.web.bind.annotation.RestController
    public static class Probe {
        @org.springframework.web.bind.annotation.PostMapping(path = "/api/_probe/json", consumes = "application/json")
        void json(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> b) {}

        @org.springframework.web.bind.annotation.PostMapping("/api/_probe/part")
        void part(@org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile f) {}
    }

}
