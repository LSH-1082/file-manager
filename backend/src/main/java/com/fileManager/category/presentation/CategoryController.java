package com.fileManager.category.presentation;

import com.fileManager.category.application.CategoryService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id) { service.delete(id); }
}
