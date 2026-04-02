package com.majd.n8n.service;

import com.majd.n8n.dto.ClientCategoryDTO;
import com.majd.n8n.dto.ClientDTO;
import com.majd.n8n.entity.Campaign;
import com.majd.n8n.entity.Category;
import com.majd.n8n.entity.Client;
import com.majd.n8n.entity.ClientCategory;
import com.majd.n8n.entity.ClientCategoryDocument;
import com.majd.n8n.entity.enums.CampaignStatus;
import com.majd.n8n.mapper.ClientMapper;
import com.majd.n8n.repository.CampaignRepository;
import com.majd.n8n.repository.CategoryRepository;
import com.majd.n8n.repository.ClientCategoryDocumentRepository;
import com.majd.n8n.repository.ClientCategoryRepository;
import com.majd.n8n.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final CampaignRepository campaignRepository;
    private final ClientMapper clientMapper;
    private final CategoryRepository categoryRepository;
    private final ClientCategoryRepository clientCategoryRepository;
    private final ClientCategoryDocumentRepository clientCategoryDocumentRepository;

    @Transactional(readOnly = true)
    public List<ClientDTO> getAllClients() {
        return clientRepository.findAll().stream()
                .map(this::toClientDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ClientDTO getClientById(Long id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Client not found with id: " + id));
        return toClientDTO(client);
    }

    @Transactional(readOnly = true)
    public ClientCategoryDocument getClientCategoryDocument(Long clientId, Long categoryId) {
        ClientCategoryDocument document = clientCategoryDocumentRepository.findByClientIdAndCategoryId(clientId, categoryId)
                .orElseThrow(() -> new RuntimeException("No document found for client id: " + clientId + " and category id: " + categoryId));

        if (document.getDocument() == null || document.getDocument().length == 0) {
            throw new RuntimeException("No document found for client id: " + clientId + " and category id: " + categoryId);
        }

        return document;
    }

    @Transactional
    public ClientDTO createClient(ClientDTO clientDTO) {
        Client client = clientMapper.toEntity(clientDTO);
        Client savedClient = clientRepository.save(client);

        syncClientCategories(savedClient, clientDTO.getCategories(), false);

        Campaign defaultCampaign = Campaign.builder()
                .name(savedClient.getName() + " Campaign")
                .status(CampaignStatus.DRAFT)
                .client(savedClient)
                .build();
        campaignRepository.save(defaultCampaign);

        return toClientDTO(clientRepository.findById(savedClient.getId())
                .orElseThrow(() -> new RuntimeException("Client not found with id: " + savedClient.getId())));
    }

    @Transactional
    public ClientDTO updateClient(Long id, ClientDTO clientDTO) {
        Client existingClient = clientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Client not found with id: " + id));

        existingClient.setName(clientDTO.getName());
        existingClient.setEmail(clientDTO.getEmail());
        existingClient.setAppPassword(clientDTO.getAppPassword());
        existingClient.setPhone(clientDTO.getPhone());

        Client savedClient = clientRepository.save(existingClient);
        syncClientCategories(savedClient, clientDTO.getCategories(), true);

        return toClientDTO(clientRepository.findById(savedClient.getId())
                .orElseThrow(() -> new RuntimeException("Client not found with id: " + savedClient.getId())));
    }

    @Transactional
    public void updateClientCategoryDocument(Long clientId, Long categoryId, MultipartFile file) throws IOException {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found with id: " + clientId));
        Category category = categoryRepository.findByIdAndActiveTrue(categoryId)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + categoryId));

        if (!clientCategoryRepository.existsByClientIdAndCategoryId(clientId, categoryId)) {
            clientCategoryRepository.save(ClientCategory.builder()
                    .client(client)
                    .category(category)
                    .build());
        }

        ClientCategoryDocument document = clientCategoryDocumentRepository.findByClientIdAndCategoryId(clientId, categoryId)
                .orElseGet(() -> ClientCategoryDocument.builder()
                        .client(client)
                        .category(category)
                        .build());
        document.setClient(client);
        document.setCategory(category);
        document.setDocument(file.getBytes());
        document.setDocumentName(file.getOriginalFilename());
        document.setDocumentContentType(file.getContentType());
        clientCategoryDocumentRepository.save(document);
    }

    @Transactional(readOnly = true)
    public void validateClientReadyToSend(Long clientId) {
        List<ClientCategory> categories = clientCategoryRepository.findByClientId(clientId);
        List<String> missingCategories = categories.stream()
                .filter(entry -> entry.getCategory() != null && entry.getCategory().isActive())
                .filter(entry -> !clientCategoryDocumentRepository.existsByClientIdAndCategoryId(clientId, entry.getCategory().getId()))
                .map(entry -> entry.getCategory().getName())
                .collect(Collectors.toList());

        if (!missingCategories.isEmpty()) {
            throw new RuntimeException("Client is missing documents for categories: " + String.join(", ", missingCategories));
        }
    }

    private void syncClientCategories(Client client, List<ClientCategoryDTO> categoryDTOs, boolean replaceMissing) {
        if (client == null || client.getId() == null || categoryDTOs == null) {
            return;
        }

        Set<Long> desiredCategoryIds = categoryDTOs.stream()
                .map(ClientCategoryDTO::getCategoryId)
                .filter(categoryId -> categoryId != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<ClientCategory> existingCategories = clientCategoryRepository.findByClientId(client.getId());
        Map<Long, ClientCategory> existingByCategoryId = existingCategories.stream()
                .filter(entry -> entry.getCategory() != null)
                .collect(Collectors.toMap(entry -> entry.getCategory().getId(), entry -> entry));

        for (Long categoryId : desiredCategoryIds) {
            if (existingByCategoryId.containsKey(categoryId)) {
                continue;
            }

            categoryRepository.findByIdAndActiveTrue(categoryId).ifPresent(category ->
                    clientCategoryRepository.save(ClientCategory.builder()
                            .client(client)
                            .category(category)
                            .build())
            );
        }

        if (!replaceMissing) {
            return;
        }

        for (ClientCategory existingCategory : existingCategories) {
            Category category = existingCategory.getCategory();
            if (category == null || desiredCategoryIds.contains(category.getId())) {
                continue;
            }

            clientCategoryDocumentRepository.findByClientIdAndCategoryId(client.getId(), category.getId())
                    .ifPresent(clientCategoryDocumentRepository::delete);
            clientCategoryRepository.delete(existingCategory);
        }
    }

    private ClientDTO toClientDTO(Client client) {
        ClientDTO dto = clientMapper.toDTO(client);
        if (client == null || client.getId() == null) {
            dto.setCategories(List.of());
            return dto;
        }

        List<ClientCategoryDTO> categories = clientCategoryRepository.findByClientId(client.getId()).stream()
                .filter(entry -> entry.getCategory() != null)
                .map(entry -> ClientCategoryDTO.builder()
                        .categoryId(entry.getCategory().getId())
                        .categoryName(entry.getCategory().getName())
                        .color(entry.getCategory().getColor())
                        .hasDocument(clientCategoryDocumentRepository.existsByClientIdAndCategoryId(client.getId(), entry.getCategory().getId()))
                        .build())
                .collect(Collectors.toList());
        dto.setCategories(categories);
        return dto;
    }
}
