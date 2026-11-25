package com.fileManager.document;

import com.fileManager.ApiTestSupport;
import com.fileManager.document.application.DocumentService;
import com.fileManager.document.presentation.DocResponse;
import com.fileManager.document.presentation.DocVersionResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 목록(GET /api/documents)을 조립하는 도중 다른 트랜잭션이 검토본·수정본을 지우고 커밋해도 목록이 깨지지 않는지 확인한다.
 * 서비스의 view 함수 안(회차·파일 목록을 읽은 뒤, 검토본 StoredFile을 읽기 전)에서 HTTP 변경을 보내 경쟁을 결정적으로 만든다.
 * 목록이 documents 행을 공유 잠금으로 읽으므로 변경은 목록 트랜잭션이 끝난 뒤에 커밋된다.
 */
class DocumentListConsistencyTest extends ApiTestSupport {
    @Autowired DocumentService documentService;

    private String docId(JsonNode doc) { return doc.get("id").asString(); }

    /** 검토중까지 진행해 거절한 문서(검토본 1개). withRevision이면 수정본 1과 대조표를 올린 뒤 거절한다. */
    private JsonNode rejectedDoc(String category, boolean withRevision) {
        JsonNode doc = newDoc(category);
        String id = docId(doc);
        if (withRevision) assertEquals(200, addRevision(id, "김철수").status());
        assertEquals(200, post("/api/documents/" + id + "/work-done", Map.of()).status());
        assertEquals(200, post("/api/documents/" + id + "/review-start", Map.of("reviewers", List.of("박검토"))).status());
        Response r = reject(id, "박검토", Part.of("memo.pdf", "copy-body"));
        assertEquals(200, r.status(), r.text());
        return r.json();
    }

    private static JsonNode latest(JsonNode doc) {
        JsonNode vs = doc.get("versions");
        return vs.get(vs.size() - 1);
    }

    /** 목록을 부르되, 첫 문서 처리에서 회차·파일 목록만 읽은 뒤 mutation(HTTP 변경)을 끝까지 기다리고 응답을 만든다. */
    private List<DocResponse> listDuringMutation(Supplier<Response> mutation, Response[] mutationResult) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        boolean[] first = {true};
        @SuppressWarnings("unchecked") Future<Response>[] pending = new Future[1];
        try {
            List<DocResponse> listed = documentService.list(d -> {
                if (first[0]) {
                    first[0] = false;
                    d.getVersions().forEach(v -> {
                        v.getRounds().size();
                        v.getFiles().size();
                    });
                    Future<Response> f = pool.submit(mutation::get);
                    waitUntilBlockedOrDone(f);
                    pending[0] = f;
                }
                return DocResponse.from(d);
            });
            // list 트랜잭션이 끝나 공유 잠금이 풀린 뒤에야 변경이 끝난다
            mutationResult[0] = pending[0].get(10, TimeUnit.SECONDS);
            return listed;
        } finally {
            pool.shutdown();
        }
    }

    /** 변경 요청이 끝나거나 H2 세션 하나가 다른 세션의 잠금을 기다리는(BLOCKER_ID) 상태가 될 때까지 최대 5초 기다린다. */
    private void waitUntilBlockedOrDone(Future<Response> f) {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!f.isDone() && System.nanoTime() < end) {
            long blocked = jdbc.sql("SELECT count(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE BLOCKER_ID IS NOT NULL")
                    .query(Long.class).single();
            if (blocked > 0) return;
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    private static DocVersionResponse latestVersion(DocResponse d) {
        return d.versions().get(d.versions().size() - 1);
    }

    @Test
    @Timeout(60)
    void concurrentCancelRejectDoesNotBreakList() throws Exception {
        // given
        JsonNode doc = rejectedDoc("재무", false);
        String id = docId(doc);
        Response[] res = new Response[1];

        // when: 목록을 조립하는 도중 거절을 취소한다
        List<DocResponse> listed = listDuringMutation(
                () -> post("/api/documents/" + id + "/cancel", Map.of("step", "reject")), res);

        // then: 목록은 취소 전 검토본까지 온전하고, 취소는 그 뒤 커밋된다
        assertEquals(1, listed.size());
        assertEquals(1, latestVersion(listed.get(0)).reviews().size());
        assertNotNull(latestVersion(listed.get(0)).reviews().get(0).reviewFile());
        assertEquals(200, res[0].status(), res[0].text());
        Response after = get("/api/documents");
        assertEquals(200, after.status(), after.text());
        assertEquals(0, latest(after.json().get(0)).get("reviews").size());
    }

    @Test
    @Timeout(60)
    void concurrentDeleteRevisionWithReviewCopyDoesNotBreakList() throws Exception {
        // given
        JsonNode doc = rejectedDoc("재무", true);
        String id = docId(doc);
        String revisionId = latest(doc).get("files").get(1).get("id").asString();
        Response[] res = new Response[1];

        // when: 목록을 조립하는 도중 검토본이 붙은 수정본을 지운다
        List<DocResponse> listed = listDuringMutation(
                () -> delete("/api/documents/" + id + "/revisions/" + revisionId), res);

        // then: 목록은 삭제 전 상태로 온전하고, 삭제는 그 뒤 커밋된다
        assertEquals(1, listed.size());
        DocVersionResponse v = latestVersion(listed.get(0));
        assertTrue(v.files().stream().anyMatch(f -> f.id().equals(revisionId)));
        assertEquals(1, v.reviews().size());
        assertEquals(200, res[0].status(), res[0].text());
        Response after = get("/api/documents");
        assertEquals(200, after.status(), after.text());
        JsonNode last = latest(after.json().get(0));
        assertEquals(1, last.get("files").size());
        assertEquals(1, last.get("reviews").size());
        assertFalse(last.get("reviews").get(0).has("reviewFile"));
        assertEquals(revisionId, last.get("reviews").get(0).get("fileId").asString());
    }
}
