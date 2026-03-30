package com.majd.n8n.service;

import com.majd.n8n.dto.CampaignDTO;
import com.majd.n8n.dto.CampaignStatsDTO;
import com.majd.n8n.dto.CampaignStartRequestDTO;
import com.majd.n8n.dto.N8nPayloadDTO;
import com.majd.n8n.entity.Campaign;
import com.majd.n8n.entity.CampaignSend;
import com.majd.n8n.entity.Client;
import com.majd.n8n.entity.Lead;
import com.majd.n8n.entity.enums.CampaignSendStatus;
import com.majd.n8n.entity.enums.CampaignStatus;
import com.majd.n8n.integration.N8nService;
import com.majd.n8n.mapper.CampaignMapper;
import com.majd.n8n.repository.CampaignRepository;
import com.majd.n8n.repository.CampaignSendRepository;
import com.majd.n8n.repository.ClientRepository;
import com.majd.n8n.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final CampaignSendRepository campaignSendRepository;
    private final ClientRepository clientRepository;
    private final LeadRepository leadRepository;
    private final CampaignMapper campaignMapper;
    private final N8nService n8nService;

    @Transactional(readOnly = true)
    public List<CampaignDTO> getAllCampaigns() {
        return campaignRepository.findAll().stream()
                .map(campaignMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CampaignDTO getCampaignById(Long id) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Campaign not found with id: " + id));
        return campaignMapper.toDTO(campaign);
    }

    @Transactional
    public CampaignDTO createCampaign(CampaignDTO campaignDTO) {
        Client client = clientRepository.findById(campaignDTO.getClientId())
                .orElseThrow(() -> new RuntimeException("Client not found with id: " + campaignDTO.getClientId()));
        
        Campaign campaign = campaignMapper.toEntity(campaignDTO);
        campaign.setClient(client);
        campaign.setStatus(CampaignStatus.DRAFT);
        
        return campaignMapper.toDTO(campaignRepository.save(campaign));
    }

    @Transactional
    public CampaignDTO updateCampaign(Long id, CampaignDTO campaignDTO) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Campaign not found with id: " + id));
        
        campaign.setName(campaignDTO.getName());
        if (campaignDTO.getStatus() != null) {
            campaign.setStatus(campaignDTO.getStatus());
        }
        
        return campaignMapper.toDTO(campaignRepository.save(campaign));
    }

    @Transactional
    public void generateCampaignSends(Long id) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Campaign not found with id: " + id));
        
        Client client = campaign.getClient();
        Set<Long> contactedLeadIds = campaignSendRepository.findLeadIdsAlreadySentByClient(client.getId());
        
        int pageSize = 500;
        int pageNumber = 0;
        Page<Lead> leadPage;
        
        do {
            leadPage = leadRepository.findAll(PageRequest.of(pageNumber, pageSize));
            List<CampaignSend> newSends = leadPage.getContent().stream()
                    .filter(lead -> !contactedLeadIds.contains(lead.getId()))
                    .map(lead -> CampaignSend.builder()
                            .campaign(campaign)
                            .lead(lead)
                            .status(CampaignSendStatus.PENDING)
                            .build())
                    .collect(Collectors.toList());
            
            campaignSendRepository.saveAll(newSends);
            log.info("Generated {} campaign sends for campaign {} (Page {})", newSends.size(), id, pageNumber);
            pageNumber++;
        } while (leadPage.hasNext());
    }

    @Transactional
    public void startCampaign(Long id, CampaignStartRequestDTO request) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Campaign not found with id: " + id));
        
        int pageSize = 50;
        int pageNumber = 0;
        Page<CampaignSend> pendingPage;
        boolean allSentSuccessfully = true;

        do {
            pendingPage = campaignSendRepository.findByCampaignIdAndStatus(id, CampaignSendStatus.PENDING, PageRequest.of(pageNumber, pageSize));
            if (pendingPage.isEmpty()) break;

            Client client = campaign.getClient();
            N8nPayloadDTO payload = N8nPayloadDTO.builder()
                    .clientEmail(client.getEmail())
                    .appPassword(client.getAppPassword())
                    .subject(request.getSubject())
                    .body(request.getBody())
                    .recipients(pendingPage.getContent().stream()
                            .map(cs -> N8nPayloadDTO.RecipientDTO.builder()
                                    .campaignSendId(cs.getId())
                                    .email(cs.getLead().getEmail())
                                    .build())
                            .collect(Collectors.toList()))
                    .build();
            
            try {
                n8nService.sendToN8n(request.getWebhookUrl(), payload);
            } catch (Exception e) {
                log.error("Failed to send batch to n8n for campaign {}: {}", id, e.getMessage());
                allSentSuccessfully = false;
                break;
            }
            pageNumber++;
        } while (pendingPage.hasNext());

        if (allSentSuccessfully) {
            campaign.setStatus(CampaignStatus.RUNNING);
            campaignRepository.save(campaign);
        } else {
            throw new RuntimeException("Failed to start campaign: n8n unreachable or returned error");
        }
    }

    @Transactional(readOnly = true)
    public CampaignStatsDTO getCampaignStats(Long id) {
        long total = campaignSendRepository.countByCampaignId(id);
        long sent = campaignSendRepository.countByCampaignIdAndStatus(id, CampaignSendStatus.SENT);
        long pending = campaignSendRepository.countByCampaignIdAndStatus(id, CampaignSendStatus.PENDING);
        long failed = campaignSendRepository.countByCampaignIdAndStatus(id, CampaignSendStatus.FAILED);
        
        return CampaignStatsDTO.builder()
                .total(total)
                .sent(sent)
                .pending(pending)
                .failed(failed)
                .build();
    }

    @Transactional
    public void updateSendStatus(Long sendId, String status) {
        CampaignSend campaignSend = campaignSendRepository.findById(sendId)
                .orElseThrow(() -> new RuntimeException("CampaignSend not found with id: " + sendId));
        
        campaignSend.setStatus(CampaignSendStatus.valueOf(status.toUpperCase()));
        campaignSend.setSentAt(LocalDateTime.now());
        campaignSendRepository.save(campaignSend);
        
        checkCampaignCompletion(campaignSend.getCampaign().getId());
    }

    private void checkCampaignCompletion(Long campaignId) {
        long pending = campaignSendRepository.countByCampaignIdAndStatus(campaignId, CampaignSendStatus.PENDING);
        if (pending == 0) {
            Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
            if (campaign != null && campaign.getStatus() == CampaignStatus.RUNNING) {
                campaign.setStatus(CampaignStatus.COMPLETED);
                campaignRepository.save(campaign);
            }
        }
    }
}
