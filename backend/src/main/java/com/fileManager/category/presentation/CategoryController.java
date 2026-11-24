package com.fileManager.category.presentation;

import com.fileManager.category.application.CategoryService;
import com.fileManager.global.error.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService service;

    CategoryController(CategoryService service) { this.service = service; }

    @GetMapping
    List<CategoryResponse> list() { return service.list().stream().map(CategoryResponse::from).toList(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CategoryResponse create(@RequestBody CategoryRequest in) {
        return CategoryResponse.from(service.create(in.name()));
    }

    @PutMapping("/{id}")
    CategoryResponse update(@PathVariable String id, @RequestBody CategoryRequest in) {
        return CategoryResponse.from(service.update(id, in.name()));
    }

    /** 본문 {"hidden": 불리언}. 불리언이 아니면(문자열 "true" 포함) 형식 오류. */
    @PutMapping("/{id}/hidden")
    CategoryResponse setHidden(@PathVariable String id, @RequestBody JsonNode body) {
        JsonNode v = body == null ? null : body.get("hidden");
        if (v == null || !v.isBoolean()) throw ApiException.format();
        return CategoryResponse.from(service.setHidden(id, v.booleanValue()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id) { service.delete(id); }
}
