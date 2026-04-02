package com.majd.n8n.service;

import com.majd.n8n.dto.CategoryDTO;
import com.majd.n8n.dto.CategoryWithKeywordsDTO;
import com.majd.n8n.dto.KeywordDTO;
import com.majd.n8n.entity.Category;
import com.majd.n8n.entity.Keyword;
import com.majd.n8n.repository.CategoryRepository;
import com.majd.n8n.repository.ClientCategoryRepository;
import com.majd.n8n.repository.KeywordRepository;
import com.majd.n8n.repository.LeadCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final KeywordRepository keywordRepository;
    private final LeadCategoryRepository leadCategoryRepository;
    private final ClientCategoryRepository clientCategoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryWithKeywordsDTO> getAllCategories() {
        return categoryRepository.findByActiveTrue().stream()
                .map(this::toCategoryWithKeywordsDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CategoryWithKeywordsDTO getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));
        return toCategoryWithKeywordsDTO(category);
    }

    @Transactional
    public CategoryDTO createCategory(CategoryDTO categoryDTO) {
        String name = normalizeName(categoryDTO.getName());
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new RuntimeException("Category name already exists");
        }

        Category category = Category.builder()
                .name(name)
                .color(categoryDTO.getColor())
                .active(true)
                .build();
        return toCategoryDTO(categoryRepository.save(category));
    }

    @Transactional
    public CategoryDTO updateCategory(Long id, CategoryDTO categoryDTO) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));

        String name = normalizeName(categoryDTO.getName());
        if (!category.getName().equalsIgnoreCase(name) && categoryRepository.existsByNameIgnoreCase(name)) {
            throw new RuntimeException("Category name already exists");
        }

        category.setName(name);
        category.setColor(categoryDTO.getColor());
        return toCategoryDTO(categoryRepository.save(category));
    }

    @Transactional
    public void deactivateCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));

        if (leadCategoryRepository.existsByCategoryId(id) || clientCategoryRepository.existsByCategoryId(id)) {
            throw new RuntimeException("Category is in use and cannot be deactivated");
        }

        category.setActive(false);
        List<Keyword> keywords = keywordRepository.findByCategoryIdAndActiveTrue(id);
        keywords.forEach(keyword -> keyword.setActive(false));
        keywordRepository.saveAll(keywords);
        categoryRepository.save(category);
    }

    @Transactional
    public KeywordDTO addKeyword(Long categoryId, KeywordDTO keywordDTO) {
        Category category = categoryRepository.findByIdAndActiveTrue(categoryId)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + categoryId));

        String nameEn = normalizeName(keywordDTO.getNameEn());
        String nameDe = normalizeName(keywordDTO.getNameDe());
        if (keywordRepository.existsByCategoryIdAndNameDeIgnoreCase(categoryId, nameDe)) {
            throw new RuntimeException("Keyword already exists in this category");
        }

        Keyword keyword = Keyword.builder()
            .nameEn(nameEn)
            .nameDe(nameDe)
                .category(category)
                .active(true)
                .build();
        return toKeywordDTO(keywordRepository.save(keyword));
    }

    @Transactional
    public KeywordDTO updateKeyword(Long keywordId, KeywordDTO keywordDTO) {
        Keyword keyword = keywordRepository.findById(keywordId)
                .orElseThrow(() -> new RuntimeException("Keyword not found with id: " + keywordId));

        String nameEn = normalizeName(keywordDTO.getNameEn());
        String nameDe = normalizeName(keywordDTO.getNameDe());
        Long categoryId = keyword.getCategory().getId();
        String currentNameDe = keyword.getNameDe() != null ? keyword.getNameDe() : keyword.getNameEn();
        if (!currentNameDe.equalsIgnoreCase(nameDe) && keywordRepository.existsByCategoryIdAndNameDeIgnoreCase(categoryId, nameDe)) {
            throw new RuntimeException("Keyword already exists in this category");
        }

        keyword.setNameEn(nameEn);
        keyword.setNameDe(nameDe);
        return toKeywordDTO(keywordRepository.save(keyword));
    }

    @Transactional
    public void deactivateKeyword(Long keywordId) {
        Keyword keyword = keywordRepository.findById(keywordId)
                .orElseThrow(() -> new RuntimeException("Keyword not found with id: " + keywordId));
        keyword.setActive(false);
        keywordRepository.save(keyword);
    }

    @Transactional(readOnly = true)
    public List<KeywordDTO> getAllKeywordsFlat() {
        return keywordRepository.findAllByActiveTrue().stream()
                .map(this::toKeywordDTO)
                .collect(Collectors.toList());
    }

    private CategoryDTO toCategoryDTO(Category category) {
        long activeKeywordCount = keywordRepository.findByCategoryIdAndActiveTrue(category.getId()).size();
        return CategoryDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .color(category.getColor())
                .active(category.isActive())
                .keywordCount((int) activeKeywordCount)
                .createdAt(category.getCreatedAt())
                .build();
    }

    private CategoryWithKeywordsDTO toCategoryWithKeywordsDTO(Category category) {
        List<KeywordDTO> keywords = keywordRepository.findByCategoryIdAndActiveTrue(category.getId()).stream()
                .map(this::toKeywordDTO)
                .collect(Collectors.toList());

        return CategoryWithKeywordsDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .color(category.getColor())
                .active(category.isActive())
                .keywords(keywords)
                .build();
    }

    private KeywordDTO toKeywordDTO(Keyword keyword) {
        return KeywordDTO.builder()
                .id(keyword.getId())
                .nameEn(keyword.getNameEn())
                .nameDe(keyword.getNameDe() != null ? keyword.getNameDe() : keyword.getNameEn())
                .categoryId(keyword.getCategory() != null ? keyword.getCategory().getId() : null)
                .categoryName(keyword.getCategory() != null ? keyword.getCategory().getName() : null)
                .active(keyword.isActive())
                .build();
    }

    private String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new RuntimeException("Name is required");
        }
        return name.trim();
    }
}