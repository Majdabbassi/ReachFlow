package com.majd.n8n.controller;

import com.majd.n8n.dto.LeadDTO;
import com.majd.n8n.dto.BulkImportResultDTO;
import com.majd.n8n.dto.BulkLeadImportResponseDTO;
import com.majd.n8n.dto.CollectRequestDTO;
import com.majd.n8n.dto.DeleteLeadEmailsRequestDTO;
import com.majd.n8n.dto.DeleteLeadEmailsResponseDTO;
import com.majd.n8n.dto.EmailAuditItemDTO;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.Map;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import com.majd.n8n.service.LeadService;
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
    public ResponseEntity<Map<String, Object>> collectLeads(@RequestBody CollectRequestDTO request) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(600000); // 10 minutes
        RestTemplate restTemplate = new RestTemplate(factory);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> payload = Map.of(
            "cities", request.getCities(),
            "keywords", request.getKeywords(),
            "maxResults", request.getMaxResults()
        );
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        ResponseEntity<Map<String, Object>> n8nResponse = restTemplate.exchange(
            request.getWebhookUrl(),
            HttpMethod.POST,
            entity,
            new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
        );
        return ResponseEntity.ok(n8nResponse.getBody());
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
