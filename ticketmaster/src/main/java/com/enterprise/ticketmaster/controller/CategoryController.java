package com.enterprise.ticketmaster.controller;

import com.enterprise.ticketmaster.model.Category;
import com.enterprise.ticketmaster.repository.CategoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;

    public CategoryController(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    // Lightweight shape for the dropdown — keeps the lazy subCategories list out of the response
    public static class CategoryResponse {
        public Long id;
        public String name;

        public CategoryResponse(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        List<CategoryResponse> categories = categoryRepository.findAll().stream()
                .map(c -> new CategoryResponse(c.getId(), c.getName()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(categories);
    }

    @PostMapping
    public ResponseEntity<?> createCategory(@RequestBody Category category) {
        if (category.getName() == null || category.getName().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Category name cannot be blank.");
        }
        if (categoryRepository.existsByName(category.getName())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("A category with that name already exists.");
        }
        category.setId(null); // never trust a client-supplied id — this is always a new row
        Category saved = categoryRepository.save(category);
        return ResponseEntity.status(HttpStatus.CREATED).body(new CategoryResponse(saved.getId(), saved.getName()));
    }
}