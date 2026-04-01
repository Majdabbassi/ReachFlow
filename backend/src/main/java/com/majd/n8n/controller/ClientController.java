package com.majd.n8n.controller;

import com.majd.n8n.dto.ClientDTO;
import com.majd.n8n.entity.Client;
import com.majd.n8n.service.ClientService;
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

    @GetMapping
    public ResponseEntity<List<ClientDTO>> getAllClients() {
        return ResponseEntity.ok(clientService.getAllClients());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientDTO> getClientById(@PathVariable Long id) {
        return ResponseEntity.ok(clientService.getClientById(id));
    }

    @GetMapping("/{id}/document")
    public ResponseEntity<byte[]> downloadClientDocument(@PathVariable Long id) {
        Client client = clientService.getClientDocument(id);

        String fileName = client.getDocumentName() == null || client.getDocumentName().isBlank()
                ? "client-document"
                : client.getDocumentName();

        MediaType mediaType;
        try {
            mediaType = client.getDocumentContentType() == null || client.getDocumentContentType().isBlank()
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(client.getDocumentContentType());
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(mediaType)
                .body(client.getDocument());
    }

    @PostMapping
    public ResponseEntity<ClientDTO> createClient(@Valid @RequestBody ClientDTO clientDTO) {
        return ResponseEntity.ok(clientService.createClient(clientDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClientDTO> updateClient(@PathVariable Long id, @Valid @RequestBody ClientDTO clientDTO) {
        return ResponseEntity.ok(clientService.updateClient(id, clientDTO));
    }

    @PutMapping("/{id}/document")
    public ResponseEntity<Void> updateClientDocument(@PathVariable Long id, @RequestParam("file") MultipartFile file) throws IOException {
        clientService.updateClientDocument(id, file);
        return ResponseEntity.noContent().build();
    }
}
