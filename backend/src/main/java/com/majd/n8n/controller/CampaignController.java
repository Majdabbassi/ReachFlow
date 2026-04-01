package com.majd.n8n.controller;

import com.majd.n8n.dto.CampaignDTO;
import com.majd.n8n.dto.CampaignSendDTO;
import com.majd.n8n.dto.CampaignStatsDTO;
import com.majd.n8n.dto.CampaignStartRequestDTO;
import com.majd.n8n.service.CampaignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService campaignService;

    @GetMapping
    public ResponseEntity<List<CampaignDTO>> getAllCampaigns() {
        return ResponseEntity.ok(campaignService.getAllCampaigns());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CampaignDTO> getCampaignById(@PathVariable Long id) {
        return ResponseEntity.ok(campaignService.getCampaignById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CampaignDTO> updateCampaign(@PathVariable Long id, @Valid @RequestBody CampaignDTO campaignDTO) {
        return ResponseEntity.ok(campaignService.updateCampaign(id, campaignDTO));
    }

    @PostMapping("/{id}/generate")
    public ResponseEntity<Void> generateCampaignSends(@PathVariable Long id) {
        campaignService.generateCampaignSends(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<Void> startCampaign(@PathVariable Long id, @RequestBody CampaignStartRequestDTO request) {
        campaignService.startCampaign(id, request);
        return ResponseEntity.accepted().build(); // 202 - returns immediately
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<CampaignStatsDTO> getCampaignStats(@PathVariable Long id) {
        return ResponseEntity.ok(campaignService.getCampaignStats(id));
    }

    @GetMapping("/{id}/sends")
    public ResponseEntity<Page<CampaignSendDTO>> getCampaignSends(
            @PathVariable Long id,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return ResponseEntity.ok(campaignService.getCampaignSends(id, status, pageable));
    }
}
