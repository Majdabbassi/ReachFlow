package com.majd.reachflow.config;

import com.majd.reachflow.entity.Category;
import com.majd.reachflow.entity.Keyword;
import com.majd.reachflow.repository.CategoryRepository;
import com.majd.reachflow.repository.KeywordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Seeds a minimal set of base categories/keywords on startup so the app is usable
 * immediately after a fresh `docker-compose down -v`, without requiring manual
 * category creation through the UI first.
 *
 * Idempotent by design (find-or-create per category/keyword), mirroring the existing
 * SearchCombinationService#seedGermanyHierarchy pattern for places — safe to run on
 * every startup, never creates duplicates, and never touches leads or clients.
 */
@Component
@org.springframework.core.annotation.Order(1) // before DemoDataSeeder (2), which needs these categories
@RequiredArgsConstructor
@Slf4j
public class CategorySeeder implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final KeywordRepository keywordRepository;

    // categoryName -> { englishKeyword -> germanKeyword }
    private static Map<String, LinkedHashMap<String, String>> baseSeed() {
        Map<String, LinkedHashMap<String, String>> seed = new LinkedHashMap<>();

        LinkedHashMap<String, String> ausbildung = new LinkedHashMap<>();
        ausbildung.put("apprenticeship", "ausbildung");
        ausbildung.put("training company", "ausbildungsbetrieb");
        ausbildung.put("dual studies", "duales studium");
        seed.put("Ausbildung", ausbildung);

        LinkedHashMap<String, String> itServices = new LinkedHashMap<>();
        itServices.put("software company", "softwareunternehmen");
        itServices.put("IT service provider", "IT-dienstleister");
        seed.put("IT Services", itServices);

        LinkedHashMap<String, String> hospitality = new LinkedHashMap<>();
        hospitality.put("restaurant", "restaurant");
        hospitality.put("hotel", "hotel");
        seed.put("Hospitality", hospitality);

        return seed;
    }

    @Override
    @Transactional
    public void run(String... args) {
        int categoriesCreated = 0;
        int keywordsCreated = 0;

        for (Map.Entry<String, LinkedHashMap<String, String>> entry : baseSeed().entrySet()) {
            String categoryName = entry.getKey();

            Category category = categoryRepository.findByNameIgnoreCaseAndActiveTrue(categoryName)
                    .orElse(null);

            if (category == null) {
                category = categoryRepository.save(Category.builder()
                        .name(categoryName)
                        .active(true)
                        .build());
                categoriesCreated++;
            }

            for (Map.Entry<String, String> kw : entry.getValue().entrySet()) {
                String nameEn = kw.getKey();
                String nameDe = kw.getValue();

                boolean exists = keywordRepository.existsByCategoryIdAndNameDeIgnoreCase(category.getId(), nameDe);
                if (!exists) {
                    keywordRepository.save(Keyword.builder()
                            .category(category)
                            .nameEn(nameEn)
                            .nameDe(nameDe)
                            .active(true)
                            .build());
                    keywordsCreated++;
                }
            }
        }

        if (categoriesCreated > 0 || keywordsCreated > 0) {
            log.info("CategorySeeder: created {} categories and {} keywords (base reference data).",
                    categoriesCreated, keywordsCreated);
        } else {
            log.debug("CategorySeeder: base categories/keywords already present, nothing to do.");
        }
    }
}
