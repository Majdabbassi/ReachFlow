package com.majd.n8n.service;

import com.majd.n8n.dto.CampaignDTO;
import com.majd.n8n.dto.CampaignSendDTO;
import com.majd.n8n.dto.CampaignStatsDTO;
import com.majd.n8n.dto.CampaignStartRequestDTO;
import com.majd.n8n.entity.Campaign;
import com.majd.n8n.entity.CampaignSend;
import com.majd.n8n.entity.Client;
import com.majd.n8n.entity.Lead;
import com.majd.n8n.entity.enums.CampaignSendStatus;
import com.majd.n8n.entity.enums.CampaignStatus;
import com.majd.n8n.mapper.CampaignMapper;
import com.majd.n8n.repository.CampaignRepository;
import com.majd.n8n.repository.CampaignSendRepository;
import com.majd.n8n.repository.ClientRepository;
import com.majd.n8n.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;
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
    private final MailService mailService;

    @Transactional(readOnly = true)
    public List<CampaignDTO> getAllCampaigns() {
        ensureCampaignForEachClient();
        reconcileCampaignStatuses();
        return campaignRepository.findAll().stream()
                .map(campaignMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public CampaignDTO getCampaignById(Long id) {
        syncCampaignSendsWithAllLeads(id);
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Campaign not found with id: " + id));
        return campaignMapper.toDTO(campaign);
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
        Set<Long> campaignLeadIds = campaignSendRepository.findLeadIdsByCampaignId(id);
        int pageSize = 500;
        int pageNumber = 0;
        Page<Lead> leadPage;
        do {
            leadPage = leadRepository.findAll(PageRequest.of(pageNumber, pageSize));
            List<CampaignSend> newSends = leadPage.getContent().stream()
                .filter(lead -> !campaignLeadIds.contains(lead.getId()))
                    .map(lead -> CampaignSend.builder()
                            .campaign(campaign)
                            .lead(lead)
                            .status(CampaignSendStatus.PENDING)
                            .build())
                    .collect(Collectors.toList());
            campaignSendRepository.saveAll(newSends);
            newSends.forEach(send -> campaignLeadIds.add(send.getLead().getId()));
            log.info("Generated {} campaign sends for campaign {} (Page {})", newSends.size(), id, pageNumber);
            pageNumber++;
        } while (leadPage.hasNext());
    }

    @Async
    @Transactional
    public void startCampaign(Long id, CampaignStartRequestDTO request) {
        syncCampaignSendsWithAllLeads(id);
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Campaign not found with id: " + id));
        if (campaign.getStatus() == CampaignStatus.RUNNING) {
            throw new RuntimeException("Campaign is already running");
        }

        Client client = campaign.getClient();
        List<CampaignSend> retryableSends = campaignSendRepository.findByCampaignIdAndStatusIn(
                id,
                Arrays.asList(CampaignSendStatus.PENDING, CampaignSendStatus.FAILED)
        );

        if (retryableSends.isEmpty()) {
            throw new RuntimeException("No pending or failed emails to send");
        }

        campaign.setStatus(CampaignStatus.RUNNING);
        campaignRepository.save(campaign);

        int delaySeconds = request.getDelaySeconds() == null ? 2 : Math.max(request.getDelaySeconds(), 0);
        boolean htmlBody = Boolean.TRUE.equals(request.getHtmlBody());
        mailService.sendEmails(client, request.getSubject(), request.getBody(), htmlBody, retryableSends, delaySeconds);

        checkCampaignCompletion(id);
    }

    @Transactional
    public CampaignStatsDTO getCampaignStats(Long id) {
        syncCampaignSendsWithAllLeads(id);
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

    @Transactional
    public Page<CampaignSendDTO> getCampaignSends(Long campaignId, String status, Pageable pageable) {
        if (!campaignRepository.existsById(campaignId)) {
            throw new RuntimeException("Campaign not found with id: " + campaignId);
        }

        syncCampaignSendsWithAllLeads(campaignId);

        Page<CampaignSend> sendPage;
        if (status == null || status.isBlank()) {
            sendPage = campaignSendRepository.findByCampaignId(campaignId, pageable);
        } else {
            CampaignSendStatus sendStatus;
            try {
                sendStatus = CampaignSendStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new RuntimeException("Invalid campaign send status: " + status);
            }
            sendPage = campaignSendRepository.findByCampaignIdAndStatus(campaignId, sendStatus, pageable);
        }

        return sendPage.map(send -> CampaignSendDTO.builder()
                .id(send.getId())
                .campaignId(send.getCampaign().getId())
                .leadId(send.getLead().getId())
                .leadEmail(send.getLead().getEmail())
                .leadInstitutionName(send.getLead().getInstitutionName())
                .leadCity(send.getLead().getCity())
                .status(send.getStatus())
                .sentAt(send.getSentAt())
                .build());
    }

    private void checkCampaignCompletion(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign == null) {
            return;
        }

        long total = campaignSendRepository.countByCampaignId(campaignId);
        long pending = campaignSendRepository.countByCampaignIdAndStatus(campaignId, CampaignSendStatus.PENDING);
        long failed = campaignSendRepository.countByCampaignIdAndStatus(campaignId, CampaignSendStatus.FAILED);

        CampaignStatus nextStatus = (total > 0 && pending == 0 && failed == 0)
                ? CampaignStatus.COMPLETED
                : CampaignStatus.DRAFT;

        if (campaign.getStatus() != nextStatus) {
            campaign.setStatus(nextStatus);
            campaignRepository.save(campaign);
        }
    }

    private void syncCampaignSendsWithAllLeads(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("Campaign not found with id: " + campaignId));

        Set<Long> existingLeadIds = campaignSendRepository.findLeadIdsByCampaignId(campaignId);
        int pageSize = 500;
        int pageNumber = 0;
        Page<Lead> leadPage;

        do {
            leadPage = leadRepository.findAll(PageRequest.of(pageNumber, pageSize));
            List<CampaignSend> newSends = leadPage.getContent().stream()
                    .filter(lead -> !existingLeadIds.contains(lead.getId()))
                    .map(lead -> CampaignSend.builder()
                            .campaign(campaign)
                            .lead(lead)
                            .status(CampaignSendStatus.PENDING)
                            .build())
                    .collect(Collectors.toList());

            if (!newSends.isEmpty()) {
                campaignSendRepository.saveAll(newSends);
                newSends.forEach(send -> existingLeadIds.add(send.getLead().getId()));
            }
            pageNumber++;
        } while (leadPage.hasNext());
    }

    @Transactional
    protected void ensureCampaignForEachClient() {
        List<Client> clients = clientRepository.findAll();
        for (Client client : clients) {
            if (!campaignRepository.existsByClientId(client.getId())) {
                Campaign campaign = Campaign.builder()
                        .name(client.getName() + " Campaign")
                        .status(CampaignStatus.DRAFT)
                        .client(client)
                        .build();
                campaignRepository.save(campaign);
            }
        }
    }

    @Transactional
    public void syncLeadAcrossAllCampaigns(Lead lead) {
        if (lead == null || lead.getId() == null) {
            return;
        }

        ensureCampaignForEachClient();

        Set<Long> linkedCampaignIds = campaignSendRepository.findCampaignIdsByLeadId(lead.getId());
        List<CampaignSend> missingSends = campaignRepository.findAll().stream()
                .filter(campaign -> !linkedCampaignIds.contains(campaign.getId()))
                .map(campaign -> CampaignSend.builder()
                        .campaign(campaign)
                        .lead(lead)
                        .status(CampaignSendStatus.PENDING)
                        .build())
                .collect(Collectors.toList());

        if (!missingSends.isEmpty()) {
            campaignSendRepository.saveAll(missingSends);

            for (CampaignSend send : missingSends) {
                Campaign campaign = send.getCampaign();
                if (campaign.getStatus() != CampaignStatus.RUNNING) {
                    campaign.setStatus(CampaignStatus.DRAFT);
                    campaignRepository.save(campaign);
                }
            }
        }
    }

    @Transactional
    protected void reconcileCampaignStatuses() {
        List<Campaign> campaigns = campaignRepository.findAll();
        for (Campaign campaign : campaigns) {
            if (campaign.getStatus() == CampaignStatus.RUNNING) {
                continue;
            }

            long total = campaignSendRepository.countByCampaignId(campaign.getId());
            long pending = campaignSendRepository.countByCampaignIdAndStatus(campaign.getId(), CampaignSendStatus.PENDING);
            long failed = campaignSendRepository.countByCampaignIdAndStatus(campaign.getId(), CampaignSendStatus.FAILED);

            CampaignStatus targetStatus = (total > 0 && pending == 0 && failed == 0)
                    ? CampaignStatus.COMPLETED
                    : CampaignStatus.DRAFT;

            if (campaign.getStatus() != targetStatus) {
                campaign.setStatus(targetStatus);
                campaignRepository.save(campaign);
            }
        }
    }
}