package com.majd.n8n.controller;

import com.majd.n8n.dto.LeadDTO;
import com.majd.n8n.service.LeadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;

    @GetMapping
    public ResponseEntity<Page<LeadDTO>> getAllLeads(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String source,
            Pageable pageable) {
        return ResponseEntity.ok(leadService.getAllLeads(city, source, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LeadDTO> getLeadById(@PathVariable Long id) {
        return ResponseEntity.ok(leadService.getLeadById(id));
    }

    @PostMapping
    public ResponseEntity<LeadDTO> createLead(@Valid @RequestBody LeadDTO leadDTO) {
        return ResponseEntity.ok(leadService.createLead(leadDTO));
    }

    @PostMapping("/bulk")
    @Transactional
    public ResponseEntity<List<LeadDTO>> bulkImportLeads(@RequestBody List<LeadDTO> leadDTOs) {
        List<LeadDTO> savedLeads = leadDTOs.stream()
                .map(leadService::createOrSkipLead)
                .collect(Collectors.toList());
        return ResponseEntity.ok(savedLeads);
    }

    @PutMapping("/{id}")
    public ResponseEntity<LeadDTO> updateLead(@PathVariable Long id, @Valid @RequestBody LeadDTO leadDTO) {
        return ResponseEntity.ok(leadService.updateLead(id, leadDTO));
    }
}
