package com.majd.n8n.controller;

import com.majd.n8n.dto.LeadDTO;
import com.majd.n8n.dto.CollectRequestDTO;
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

import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpHeaders;
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

    @GetMapping("/emails/download")
    public ResponseEntity<String> downloadAllEmails() {
        String body = leadService.getAllEmailsAsTextFile();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=all-emails.txt")
                .contentType(MediaType.TEXT_PLAIN)
                .body(body);
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


}
