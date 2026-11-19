package com.fileManager.resource.presentation;

import com.fileManager.file.presentation.MultipartUploads;
import com.fileManager.resource.application.ResourceService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ResourceController {
    private final ResourceService service;

    ResourceController(ResourceService service) {
        this.service = service;
    }

    @GetMapping("/api/resources")
    List<ResourceResponse> list() {
        return service.list(ResourceResponse::from);
    }

    @PostMapping("/api/categories/{categoryId}/resources")
    @ResponseStatus(HttpStatus.CREATED)
    ResourceResponse create(@PathVariable String categoryId,
                            @RequestParam(name = "file", required = false) List<MultipartFile> file,
                            @RequestParam(required = false) String kind,
                            @RequestParam(required = false) String title,
                            @RequestParam(required = false) String note) {
        return service.create(categoryId, MultipartUploads.of(file), kind, title, note, ResourceResponse::from);
    }

    @PutMapping("/api/resources/{id}")
    ResourceResponse update(@PathVariable String id,
                            @RequestParam(name = "file", required = false) List<MultipartFile> file,
                            @RequestParam(required = false) String kind,
                            @RequestParam(required = false) String title,
                            @RequestParam(required = false) String note) {
        return service.update(id, MultipartUploads.of(file), kind, title, note, ResourceResponse::from);
    }

    @DeleteMapping("/api/resources/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id) {
        service.delete(id);
    }
}
