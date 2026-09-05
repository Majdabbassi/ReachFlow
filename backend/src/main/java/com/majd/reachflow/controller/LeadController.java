package com.majd.reachflow.controller;

import com.majd.reachflow.dto.LeadDTO;
import com.majd.reachflow.dto.BulkImportResultDTO;
import com.majd.reachflow.dto.BulkLeadImportResponseDTO;
import com.majd.reachflow.dto.CollectRequestDTO;
import com.majd.reachflow.dto.DeleteLeadEmailsRequestDTO;
import com.majd.reachflow.dto.DeleteLeadEmailsResponseDTO;
import com.majd.reachflow.dto.EmailAuditItemDTO;
import com.majd.reachflow.dto.ScrapeProgressDTO;
import org.springframework.http.*;
import java.util.Map;
import com.majd.reachflow.service.LeadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.http.HttpHeaders;
import java.util.List;
import java.util.ArrayList;


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

    @GetMapping("/emails/download")
    public ResponseEntity<String> downloadAllEmails() {
        String body = leadService.getAllEmailsAsTextFile();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=all-emails.txt")
                .contentType(MediaType.TEXT_PLAIN)
                .body(body);
    }

    @GetMapping("/export/csv")
    public ResponseEntity<String> exportAllLeadsCsv() {
        String body = leadService.getAllLeadsAsCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=leads.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(body);
    }

    @PostMapping(value = "/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BulkImportResultDTO> importLeadsCsv(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(leadService.importFromCsv(file));
    }

    @GetMapping("/emails/audit")
    public ResponseEntity<Page<EmailAuditItemDTO>> getEmailAudit(
            @RequestParam(defaultValue = "invalid") String mode,
            Pageable pageable) {
        return ResponseEntity.ok(leadService.getEmailAudit(mode, pageable));
    }

    @DeleteMapping("/emails")
    public ResponseEntity<DeleteLeadEmailsResponseDTO> deleteLeadEmails(@RequestBody DeleteLeadEmailsRequestDTO request) {
        return ResponseEntity.ok(leadService.deleteLeadEmails(request.getEmailIds()));
    }

    @PostMapping
    public ResponseEntity<LeadDTO> createLead(@Valid @RequestBody LeadDTO leadDTO) {
        return ResponseEntity.ok(leadService.createLead(leadDTO));
    }

    @PostMapping("/bulk")
    public ResponseEntity<BulkLeadImportResponseDTO> bulkImportLeads(@RequestBody List<LeadDTO> leadDTOs) {
        List<LeadDTO> saved = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (LeadDTO leadDTO : leadDTOs) {
            try {
                saved.add(leadService.createOrSkipLead(leadDTO));
            } catch (Exception ex) {
                String email = leadDTO.getEmail() != null ? leadDTO.getEmail() : leadDTO.getPrimaryEmail();
                String prefix = email == null || email.isBlank() ? "Lead" : "Lead " + email;
                errors.add(prefix + ": " + ex.getMessage());
            }
        }

        return ResponseEntity.ok(BulkLeadImportResponseDTO.builder()
                .saved(saved)
                .errors(errors)
                .build());
    }

    @PostMapping("/collect")
    public ResponseEntity<Map<String, String>> collectLeads(@RequestBody CollectRequestDTO request) {
        String jobId = leadService.collectLeadsAsync(request);
        return ResponseEntity.ok(Map.of("jobId", jobId, "status", "Scraping process started. Results will appear in the leads list soon."));
    }

    @GetMapping("/scrape-status/{jobId}")
    public ResponseEntity<ScrapeProgressDTO> getScrapeStatus(@PathVariable String jobId) {
        ScrapeProgressDTO progress = leadService.getScrapeProgress(jobId);
        if (progress == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(progress);
    }

    @PutMapping("/{id}")
    public ResponseEntity<LeadDTO> updateLead(@PathVariable Long id, @Valid @RequestBody LeadDTO leadDTO) {
        return ResponseEntity.ok(leadService.updateLead(id, leadDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLead(@PathVariable Long id) {
        leadService.deleteLead(id);
        return ResponseEntity.noContent().build();
    }


}
