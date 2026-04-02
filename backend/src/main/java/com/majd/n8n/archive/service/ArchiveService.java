package com.majd.n8n.archive.service;

import com.majd.n8n.archive.dto.ArchivedCampaignDTO;
import com.majd.n8n.archive.dto.ArchivedCampaignSendDTO;
import com.majd.n8n.archive.dto.ArchivedClientDTO;
import com.majd.n8n.archive.entity.ArchivedCampaign;
import com.majd.n8n.archive.entity.ArchivedCampaignSend;
import com.majd.n8n.archive.entity.ArchivedClient;
import com.majd.n8n.archive.repository.ArchivedCampaignRepository;
import com.majd.n8n.archive.repository.ArchivedCampaignSendRepository;
import com.majd.n8n.archive.repository.ArchivedClientRepository;
import com.majd.n8n.entity.Campaign;
import com.majd.n8n.entity.CampaignSend;
import com.majd.n8n.entity.Client;
import com.majd.n8n.entity.LeadEmail;
import com.majd.n8n.entity.enums.CampaignSendStatus;
import com.majd.n8n.repository.CampaignRepository;
import com.majd.n8n.repository.CampaignSendRepository;
import com.majd.n8n.repository.ClientRepository;
import com.majd.n8n.repository.LeadEmailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArchiveService {

    private final ClientRepository clientRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignSendRepository campaignSendRepository;
    private final LeadEmailRepository leadEmailRepository;

    private final ArchivedClientRepository archivedClientRepository;
    private final ArchivedCampaignRepository archivedCampaignRepository;
    private final ArchivedCampaignSendRepository archivedCampaignSendRepository;

    @Transactional("transactionManager")
    public void archiveClient(Long clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found with id: " + clientId));

        List<Campaign> campaigns = campaignRepository.findAllByClientId(clientId);
        List<Long> campaignIds = campaigns.stream().map(Campaign::getId).collect(Collectors.toList());
        List<CampaignSend> allSends = campaignIds.isEmpty()
            ? List.of()
            : campaignSendRepository.findByCampaignIdIn(campaignIds);
        Map<Long, List<CampaignSend>> sendsByCampaignId = allSends.stream()
            .collect(Collectors.groupingBy(send -> send.getCampaign().getId()));

        ArchivedClient archivedClient = archivedClientRepository.save(ArchivedClient.builder()
                .name(client.getName())
                .email(client.getEmail())
                .appPassword(client.getAppPassword())
                .phone(client.getPhone())
                .createdAt(client.getCreatedAt())
                .archivedAt(LocalDateTime.now())
                .originalId(client.getId())
                .build());

        for (Campaign campaign : campaigns) {
            ArchivedCampaign archivedCampaign = archivedCampaignRepository.save(ArchivedCampaign.builder()
                    .name(campaign.getName())
                    .status(campaign.getStatus())
                    .createdAt(campaign.getCreatedAt())
                    .archivedClientId(archivedClient.getId())
                    .originalId(campaign.getId())
                    .build());

            List<CampaignSend> sends = sendsByCampaignId.getOrDefault(campaign.getId(), List.of());
            for (CampaignSend send : sends) {
                LeadEmail leadEmail = send.getLeadEmail();
                if (leadEmail == null || leadEmail.getEmail() == null || leadEmail.getEmail().isBlank()) {
                    log.warn("Skipping campaign send {} during archive due to missing lead email reference", send.getId());
                    continue;
                }

                String institutionName = leadEmail.getLead() != null ? leadEmail.getLead().getInstitutionName() : null;
                String city = leadEmail.getLead() != null ? leadEmail.getLead().getCity() : null;
                archivedCampaignSendRepository.save(ArchivedCampaignSend.builder()
                        .email(leadEmail.getEmail())
                        .leadInstitutionName(institutionName)
                        .leadCity(city)
                        .status(send.getStatus())
                        .sentAt(send.getSentAt())
                        .archivedCampaignId(archivedCampaign.getId())
                        .originalId(send.getId())
                        .build());
            }
        }

        if (!campaignIds.isEmpty()) {
            campaignSendRepository.bulkDeleteByCampaignIdIn(campaignIds);
            campaignRepository.bulkDeleteByIdIn(campaignIds);
        }
        clientRepository.deleteById(clientId);
    }

    @Transactional("transactionManager")
    public void restoreClient(Long archivedClientId) {
        ArchivedClient archivedClient = archivedClientRepository.findById(archivedClientId)
                .orElseThrow(() -> new RuntimeException("Archived client not found with id: " + archivedClientId));

        List<ArchivedCampaign> archivedCampaigns = archivedCampaignRepository.findByArchivedClientId(archivedClientId);

        Client restoredClient = clientRepository.save(Client.builder()
                .name(archivedClient.getName())
                .email(archivedClient.getEmail())
                .appPassword(archivedClient.getAppPassword())
                .phone(archivedClient.getPhone())
                .build());

        for (ArchivedCampaign archivedCampaign : archivedCampaigns) {
            Campaign restoredCampaign = campaignRepository.save(Campaign.builder()
                    .name(archivedCampaign.getName())
                    .status(archivedCampaign.getStatus())
                    .client(restoredClient)
                    .build());

            List<ArchivedCampaignSend> archivedSends = archivedCampaignSendRepository
                    .findByArchivedCampaignId(archivedCampaign.getId());

            for (ArchivedCampaignSend archivedSend : archivedSends) {
                leadEmailRepository.findByEmail(archivedSend.getEmail()).ifPresent(leadEmail -> {
                    campaignSendRepository.save(CampaignSend.builder()
                            .campaign(restoredCampaign)
                            .leadEmail(leadEmail)
                            .status(archivedSend.getStatus())
                            .sentAt(archivedSend.getSentAt())
                            .build());
                });
            }
        }

        for (ArchivedCampaign archivedCampaign : archivedCampaigns) {
            List<ArchivedCampaignSend> archivedSends = archivedCampaignSendRepository
                    .findByArchivedCampaignId(archivedCampaign.getId());
            archivedCampaignSendRepository.deleteAll(archivedSends);
        }
        archivedCampaignRepository.deleteAll(archivedCampaigns);
        archivedClientRepository.delete(archivedClient);
    }

    @Transactional(readOnly = true)
    public Page<ArchivedClientDTO> getAllArchivedClients(Pageable pageable) {
        return archivedClientRepository.findAll(pageable).map(this::toArchivedClientDTO);
    }

    @Transactional(readOnly = true)
    public ArchivedClientDTO getArchivedClientById(Long id) {
        ArchivedClient archivedClient = archivedClientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Archived client not found with id: " + id));
        return toArchivedClientDTO(archivedClient);
    }

    @Transactional(readOnly = true)
    public Page<ArchivedCampaignDTO> getArchivedCampaigns(Long archivedClientId, Pageable pageable) {
        return archivedCampaignRepository.findByArchivedClientId(archivedClientId, pageable)
                .map(this::toArchivedCampaignDTO);
    }

    @Transactional(readOnly = true)
    public Page<ArchivedCampaignSendDTO> getArchivedCampaignSends(Long archivedClientId, Long archivedCampaignId, String status, Pageable pageable) {
        ArchivedCampaign archivedCampaign = archivedCampaignRepository.findById(archivedCampaignId)
                .orElseThrow(() -> new RuntimeException("Archived campaign not found with id: " + archivedCampaignId));
        if (!archivedCampaign.getArchivedClientId().equals(archivedClientId)) {
            throw new RuntimeException("Archived campaign does not belong to archived client id: " + archivedClientId);
        }

        if (status == null || status.isBlank()) {
            return archivedCampaignSendRepository.findByArchivedCampaignId(archivedCampaignId, pageable)
                    .map(this::toArchivedCampaignSendDTO);
        }

        CampaignSendStatus sendStatus;
        try {
            sendStatus = CampaignSendStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException("Invalid campaign send status: " + status);
        }

        return archivedCampaignSendRepository.findByArchivedCampaignIdAndStatus(archivedCampaignId, sendStatus, pageable)
                .map(this::toArchivedCampaignSendDTO);
    }

    private ArchivedClientDTO toArchivedClientDTO(ArchivedClient archivedClient) {
        return ArchivedClientDTO.builder()
                .id(archivedClient.getId())
                .name(archivedClient.getName())
                .email(archivedClient.getEmail())
                .appPassword(archivedClient.getAppPassword())
                .phone(archivedClient.getPhone())
                .documentName(archivedClient.getDocumentName())
                .documentContentType(archivedClient.getDocumentContentType())
                .createdAt(archivedClient.getCreatedAt())
                .archivedAt(archivedClient.getArchivedAt())
                .originalId(archivedClient.getOriginalId())
                .build();
    }

    private ArchivedCampaignDTO toArchivedCampaignDTO(ArchivedCampaign archivedCampaign) {
        long total = archivedCampaignSendRepository.countByArchivedCampaignId(archivedCampaign.getId());
        long sent = archivedCampaignSendRepository.countByArchivedCampaignIdAndStatus(archivedCampaign.getId(), CampaignSendStatus.SENT);
        long pending = archivedCampaignSendRepository.countByArchivedCampaignIdAndStatus(archivedCampaign.getId(), CampaignSendStatus.PENDING);
        long failed = archivedCampaignSendRepository.countByArchivedCampaignIdAndStatus(archivedCampaign.getId(), CampaignSendStatus.FAILED);

        return ArchivedCampaignDTO.builder()
                .id(archivedCampaign.getId())
                .name(archivedCampaign.getName())
                .status(archivedCampaign.getStatus())
                .createdAt(archivedCampaign.getCreatedAt())
                .archivedClientId(archivedCampaign.getArchivedClientId())
                .originalId(archivedCampaign.getOriginalId())
                .stats(ArchivedCampaignDTO.CampaignStats.builder()
                        .total(total)
                        .sent(sent)
                        .pending(pending)
                        .failed(failed)
                        .build())
                .build();
    }

    private ArchivedCampaignSendDTO toArchivedCampaignSendDTO(ArchivedCampaignSend archivedCampaignSend) {
        return ArchivedCampaignSendDTO.builder()
                .email(archivedCampaignSend.getEmail())
                .leadInstitutionName(archivedCampaignSend.getLeadInstitutionName())
                .leadCity(archivedCampaignSend.getLeadCity())
                .status(archivedCampaignSend.getStatus())
                .sentAt(archivedCampaignSend.getSentAt())
                .archivedCampaignId(archivedCampaignSend.getArchivedCampaignId())
                .build();
    }
}
