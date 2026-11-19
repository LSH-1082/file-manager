package com.fileManager.document.presentation;

import com.fileManager.document.application.DocumentService;
import com.fileManager.file.presentation.MultipartUploads;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentService service;

    DocumentController(DocumentService service) {
        this.service = service;
    }

    @GetMapping
    List<DocResponse> list() {
        return service.list(DocResponse::from);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DocResponse create(@RequestParam(name = "file", required = false) List<MultipartFile> file,
                       @RequestParam(required = false) String categoryId,
                       @RequestParam(required = false) String versionName,
                       @RequestParam(required = false) String worker) {
        return service.create(MultipartUploads.of(file), categoryId, versionName, worker, DocResponse::from);
    }

    @PostMapping("/{id}/category")
    DocResponse assign(@PathVariable String id, @RequestBody AssignRequest req) {
        AssignRequest.NewCategory nc = req.newCategory();
        return service.assignCategory(id, req.categoryId(), nc == null ? null : nc.name(), nc != null, DocResponse::from);
    }

    @PostMapping("/{id}/revisions")
    DocResponse uploadRevision(@PathVariable String id,
                               @RequestParam(name = "file", required = false) List<MultipartFile> file,
                               @RequestParam(name = "revision", required = false) List<MultipartFile> revision,
                               @RequestParam(required = false) String worker) {
        return service.uploadRevision(id, MultipartUploads.of(file), MultipartUploads.of(revision), worker,
                DocResponse::from);
    }

    @DeleteMapping("/{id}/revisions/{fileId}")
    DocResponse deleteRevision(@PathVariable String id, @PathVariable String fileId) {
        return service.deleteRevision(id, fileId, DocResponse::from);
    }

    @PatchMapping("/{id}/versions/{versionId}")
    DocResponse rename(@PathVariable String id, @PathVariable String versionId, @RequestBody NameRequest req) {
        return service.renameVersion(id, versionId, req.name(), DocResponse::from);
    }

    @PostMapping("/{id}/versions")
    DocResponse startVersion(@PathVariable String id, @RequestBody StartVersionRequest req) {
        return service.startVersion(id, req.versionName(), req.worker(), DocResponse::from);
    }

    /** 200 Doc, 버전이 하나뿐이라 문서가 지워지면 204. */
    @DeleteMapping("/{id}/versions/{versionId}")
    ResponseEntity<DocResponse> deleteVersion(@PathVariable String id, @PathVariable String versionId) {
        DocResponse doc = service.deleteVersion(id, versionId, DocResponse::from);
        return doc == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(doc);
    }
}
