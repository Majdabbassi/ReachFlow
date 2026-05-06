package com.majd.n8n.controller;

import com.majd.n8n.dto.GenerateSearchCombinationsRequestDTO;
import com.majd.n8n.dto.GenerateSearchCombinationsResponseDTO;
import com.majd.n8n.dto.LaunchSearchCombinationRequestDTO;
import com.majd.n8n.dto.PlaceCountryDTO;
import com.majd.n8n.dto.SearchCombinationDTO;
import com.majd.n8n.entity.enums.SearchCombinationStatus;
import com.majd.n8n.service.SearchCombinationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search-combinations")
@RequiredArgsConstructor
public class SearchCombinationController {

    private final SearchCombinationService searchCombinationService;

    @PostMapping("/seed-germany")
    public ResponseEntity<Void> seedGermany() {
        searchCombinationService.seedGermanyHierarchy();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/places/tree")
    public ResponseEntity<List<PlaceCountryDTO>> getPlaceTree(@RequestParam(defaultValue = "DE") String countryCode) {
        return ResponseEntity.ok(searchCombinationService.getPlaceTree(countryCode));
    }

    @PostMapping("/generate")
    public ResponseEntity<GenerateSearchCombinationsResponseDTO> generate(@RequestBody GenerateSearchCombinationsRequestDTO request) {
        return ResponseEntity.ok(searchCombinationService.generate(request));
    }

    @GetMapping
    public ResponseEntity<Page<SearchCombinationDTO>> list(
            @RequestParam(required = false) SearchCombinationStatus status,
            @RequestParam(required = false) Long categoryId,
            Pageable pageable
    ) {
        return ResponseEntity.ok(searchCombinationService.list(status, categoryId, pageable));
    }

    @PostMapping("/{id}/launch")
    public ResponseEntity<SearchCombinationDTO> launch(
            @PathVariable Long id,
            @RequestBody LaunchSearchCombinationRequestDTO request
    ) {
        return ResponseEntity.ok(searchCombinationService.launch(id, request));
    }
}
