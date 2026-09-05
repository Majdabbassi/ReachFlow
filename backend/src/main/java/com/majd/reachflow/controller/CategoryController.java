package com.majd.reachflow.controller;

import com.majd.reachflow.dto.CategoryDTO;
import com.majd.reachflow.dto.CategoryWithKeywordsDTO;
import com.majd.reachflow.dto.KeywordDTO;
import com.majd.reachflow.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<CategoryWithKeywordsDTO>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getAllCategories());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryWithKeywordsDTO> getCategoryById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getCategoryById(id));
    }

    @PostMapping
    public ResponseEntity<CategoryDTO> createCategory(@Valid @RequestBody CategoryDTO categoryDTO) {
        return ResponseEntity.ok(categoryService.createCategory(categoryDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryDTO> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryDTO categoryDTO) {
        return ResponseEntity.ok(categoryService.updateCategory(id, categoryDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateCategory(@PathVariable Long id) {
        categoryService.deactivateCategory(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/keywords")
    public ResponseEntity<KeywordDTO> addKeyword(@PathVariable Long id, @Valid @RequestBody KeywordDTO keywordDTO) {
        return ResponseEntity.ok(categoryService.addKeyword(id, keywordDTO));
    }

    @PutMapping("/keywords/{keywordId}")
    public ResponseEntity<KeywordDTO> updateKeyword(@PathVariable Long keywordId, @Valid @RequestBody KeywordDTO keywordDTO) {
        return ResponseEntity.ok(categoryService.updateKeyword(keywordId, keywordDTO));
    }

    @DeleteMapping("/keywords/{keywordId}")
    public ResponseEntity<Void> deactivateKeyword(@PathVariable Long keywordId) {
        categoryService.deactivateKeyword(keywordId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/keywords/all")
    public ResponseEntity<List<KeywordDTO>> getAllKeywordsFlat() {
        return ResponseEntity.ok(categoryService.getAllKeywordsFlat());
    }
}
