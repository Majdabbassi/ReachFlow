package com.majd.n8n.controller;

import com.majd.n8n.dto.CampaignDTO;
import com.majd.n8n.dto.CampaignStatsDTO;
import com.majd.n8n.dto.CampaignStartRequestDTO;
import com.majd.n8n.service.CampaignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    @PostMapping
    public ResponseEntity<CampaignDTO> createCampaign(@Valid @RequestBody CampaignDTO campaignDTO) {
        return ResponseEntity.ok(campaignService.createCampaign(campaignDTO));
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
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<CampaignStatsDTO> getCampaignStats(@PathVariable Long id) {
        return ResponseEntity.ok(campaignService.getCampaignStats(id));
    }
}
