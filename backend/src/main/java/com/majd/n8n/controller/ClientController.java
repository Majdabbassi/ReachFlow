package com.majd.n8n.controller;

import com.majd.n8n.archive.service.ArchiveService;
import com.majd.n8n.dto.ClientDTO;
import com.majd.n8n.dto.GmailScanResultDTO;
import com.majd.n8n.entity.ClientCategoryDocument;
import com.majd.n8n.service.ClientService;
import com.majd.n8n.service.GmailScannerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;
    private final ArchiveService archiveService;
    private final GmailScannerService gmailScannerService;

    @GetMapping
    public ResponseEntity<List<ClientDTO>> getAllClients() {
        return ResponseEntity.ok(clientService.getAllClients());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientDTO> getClientById(@PathVariable Long id) {
        return ResponseEntity.ok(clientService.getClientById(id));
    }

    @GetMapping("/{id}/categories/{categoryId}/document")
    public ResponseEntity<byte[]> downloadClientCategoryDocument(@PathVariable Long id, @PathVariable Long categoryId) {
        ClientCategoryDocument document = clientService.getClientCategoryDocument(id, categoryId);

        String fileName = document.getDocumentName() == null || document.getDocumentName().isBlank()
                ? "client-document"
                : document.getDocumentName();

        MediaType mediaType;
        try {
            mediaType = document.getDocumentContentType() == null || document.getDocumentContentType().isBlank()
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(document.getDocumentContentType());
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(mediaType)
                .body(document.getDocument());
    }

    @PostMapping
    public ResponseEntity<ClientDTO> createClient(@Valid @RequestBody ClientDTO clientDTO) {
        return ResponseEntity.ok(clientService.createClient(clientDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClientDTO> updateClient(@PathVariable Long id, @Valid @RequestBody ClientDTO clientDTO) {
        return ResponseEntity.ok(clientService.updateClient(id, clientDTO));
    }

    @PutMapping("/{id}/categories/{categoryId}/document")
    public ResponseEntity<Void> updateClientCategoryDocument(@PathVariable Long id, @PathVariable Long categoryId, @RequestParam("file") MultipartFile file) throws IOException {
        clientService.updateClientCategoryDocument(id, categoryId, file);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/scan-sent")
    public ResponseEntity<GmailScanResultDTO> scanSentEmails(@PathVariable Long id) {
        return ResponseEntity.ok(gmailScannerService.scanAndMarkSentEmails(id));
    }

    @DeleteMapping("/{id}/archive")
    public ResponseEntity<Void> archiveClient(@PathVariable Long id) {
        archiveService.archiveClient(id);
        return ResponseEntity.noContent().build();
    }
}
