package com.majd.reachflow.archive.controller;

import com.majd.reachflow.archive.dto.ArchivedCampaignDTO;
import com.majd.reachflow.archive.dto.ArchivedCampaignSendDTO;
import com.majd.reachflow.archive.dto.ArchivedClientDTO;
import com.majd.reachflow.archive.service.ArchiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/archive")
@RequiredArgsConstructor
public class ArchiveController {

    private final ArchiveService archiveService;

    @PostMapping("/clients/{id}/restore")
    public ResponseEntity<Void> restoreClient(@PathVariable Long id) {
        archiveService.restoreClient(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/clients")
    public ResponseEntity<Page<ArchivedClientDTO>> getArchivedClients(Pageable pageable) {
        return ResponseEntity.ok(archiveService.getAllArchivedClients(pageable));
    }

    @GetMapping("/clients/{id}")
    public ResponseEntity<ArchivedClientDTO> getArchivedClientById(@PathVariable Long id) {
        return ResponseEntity.ok(archiveService.getArchivedClientById(id));
    }

    @GetMapping("/clients/{id}/campaigns")
    public ResponseEntity<Page<ArchivedCampaignDTO>> getArchivedCampaigns(@PathVariable Long id, Pageable pageable) {
        return ResponseEntity.ok(archiveService.getArchivedCampaigns(id, pageable));
    }

    @GetMapping("/clients/{id}/campaigns/{campaignId}/sends")
    public ResponseEntity<Page<ArchivedCampaignSendDTO>> getArchivedCampaignSends(
            @PathVariable Long id,
            @PathVariable Long campaignId,
            @RequestParam(required = false) String status,
            Pageable pageable
    ) {
        return ResponseEntity.ok(archiveService.getArchivedCampaignSends(id, campaignId, status, pageable));
    }
}
