package com.majd.reachflow.controller;

import com.majd.reachflow.dto.GenerateSearchCombinationsRequestDTO;
import com.majd.reachflow.dto.GenerateSearchCombinationsResponseDTO;
import com.majd.reachflow.dto.LaunchSearchCombinationRequestDTO;
import com.majd.reachflow.dto.PlaceCountryDTO;
import com.majd.reachflow.dto.SearchCombinationDTO;
import com.majd.reachflow.entity.enums.SearchCombinationStatus;
import com.majd.reachflow.service.SearchCombinationService;
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
