package com.majd.reachflow.controller;

import com.majd.reachflow.service.CampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/campaign-sends")
@RequiredArgsConstructor
public class CampaignSendController {

    private final CampaignService campaignService;

    @PostMapping("/{id}/status")
    public ResponseEntity<Void> updateSendStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (status == null) {
            return ResponseEntity.badRequest().build();
        }
        campaignService.updateSendStatus(id, status);
        return ResponseEntity.noContent().build();
    }
}
