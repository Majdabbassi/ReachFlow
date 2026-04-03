package com.majd.n8n.service;

import com.majd.n8n.dto.CampaignDTO;
import com.majd.n8n.dto.CampaignSendDTO;
import com.majd.n8n.dto.CampaignStartRequestDTO;
import com.majd.n8n.dto.CampaignStatsDTO;
import com.majd.n8n.dto.SelectiveSendRequestDTO;
import com.majd.n8n.entity.Campaign;
import com.majd.n8n.entity.CampaignSend;
import com.majd.n8n.entity.Client;
import com.majd.n8n.entity.ClientCategory;
import com.majd.n8n.entity.Lead;
import com.majd.n8n.entity.LeadEmail;
import com.majd.n8n.entity.enums.CampaignSendStatus;
import com.majd.n8n.entity.enums.CampaignStatus;
import com.majd.n8n.exception.BusinessException;
import com.majd.n8n.mapper.CampaignMapper;
import com.majd.n8n.repository.CampaignRepository;
import com.majd.n8n.repository.CampaignSendRepository;
import com.majd.n8n.repository.ClientCategoryDocumentRepository;
import com.majd.n8n.repository.ClientCategoryRepository;
import com.majd.n8n.repository.ClientRepository;
import com.majd.n8n.repository.LeadCategoryRepository;
import com.majd.n8n.repository.LeadEmailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final CampaignSendRepository campaignSendRepository;
    private final ClientRepository clientRepository;
    private final LeadEmailRepository leadEmailRepository;
    private final ClientCategoryRepository clientCategoryRepository;
    private final LeadCategoryRepository leadCategoryRepository;
    private final ClientCategoryDocumentRepository clientCategoryDocumentRepository;
    private final CampaignMapper campaignMapper;
    private final MailService mailService;

    @Transactional(readOnly = true)
    public List<CampaignDTO> getAllCampaigns() {
        return campaignRepository.findAll().stream()
                .map(campaignMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public CampaignDTO getCampaignById(Long id) {
        syncCampaignSendsWithAllLeads(id);
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Campaign not found with id: " + id, HttpStatus.NOT_FOUND));
        return campaignMapper.toDTO(campaign);
    }

    @Transactional
    public CampaignDTO updateCampaign(Long id, CampaignDTO campaignDTO) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Campaign not found with id: " + id, HttpStatus.NOT_FOUND));
        campaign.setName(campaignDTO.getName());
        if (campaignDTO.getStatus() != null) {
            campaign.setStatus(campaignDTO.getStatus());
        }
        return campaignMapper.toDTO(campaignRepository.save(campaign));
    }

    @Transactional
    public void generateCampaignSends(Long id) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Campaign not found with id: " + id, HttpStatus.NOT_FOUND));
        Set<Long> campaignLeadEmailIds = campaignSendRepository.findLeadEmailIdsByCampaignId(id);
        List<LeadEmail> eligibleLeadEmails = findEligibleLeadEmails(campaign);
        List<CampaignSend> newSends = eligibleLeadEmails.stream()
                .filter(leadEmail -> !campaignLeadEmailIds.contains(leadEmail.getId()))
                .map(leadEmail -> CampaignSend.builder()
                        .campaign(campaign)
                        .leadEmail(leadEmail)
                        .status(CampaignSendStatus.PENDING)
                        .build())
                .collect(Collectors.toList());
        campaignSendRepository.saveAll(newSends);
    }

    @Async
    public void startCampaign(Long id, CampaignStartRequestDTO request) {
        try {
            CampaignStartContext context = prepareCampaignStart(id);
            int delaySeconds = request.getDelaySeconds() == null ? 2 : Math.max(request.getDelaySeconds(), 0);
            boolean htmlBody = Boolean.TRUE.equals(request.getHtmlBody());
            mailService.sendEmails(id, context.client(), request.getSubject(), request.getBody(), htmlBody, context.retryableSends(), delaySeconds);
            checkCampaignCompletion(id);
        } catch (Exception ex) {
            log.error("startCampaign failed for campaign {}", id, ex);
            setCampaignToDraft(id);
            if (ex instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new BusinessException("Failed to start campaign", HttpStatus.INTERNAL_SERVER_ERROR, ex);
        }
    }

    @Transactional
    public void stopCampaign(Long id) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Campaign not found with id: " + id, HttpStatus.NOT_FOUND));

        if (campaign.getStatus() != CampaignStatus.RUNNING) {
            throw new BusinessException("Campaign is not running", HttpStatus.CONFLICT);
        }

        campaign.setStatus(CampaignStatus.STOP_REQUESTED);
        campaignRepository.save(campaign);
    }

    @Transactional
    public void sendSelected(Long campaignId, SelectiveSendRequestDTO request) {
        if (request.getSendIds() == null || request.getSendIds().isEmpty()) {
            throw new BusinessException("sendIds must not be empty", HttpStatus.BAD_REQUEST);
        }

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new BusinessException("Campaign not found with id: " + campaignId, HttpStatus.NOT_FOUND));

        List<CampaignSend> sends = campaignSendRepository.findByCampaignIdAndIdIn(campaignId, request.getSendIds());
        if (sends.size() != request.getSendIds().size()) {
            throw new BusinessException("Some sendIds do not belong to this campaign", HttpStatus.BAD_REQUEST);
        }

        boolean invalidStatusFound = sends.stream().anyMatch(send ->
                send.getStatus() != CampaignSendStatus.PENDING && send.getStatus() != CampaignSendStatus.FAILED
        );
        if (invalidStatusFound) {
            throw new BusinessException("Only PENDING or FAILED sends can be selected", HttpStatus.BAD_REQUEST);
        }

        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            campaign.setStatus(CampaignStatus.DRAFT);
            campaignRepository.save(campaign);
        }

        int delaySeconds = request.getDelaySeconds() == null ? 2 : Math.max(request.getDelaySeconds(), 0);
        boolean htmlBody = Boolean.TRUE.equals(request.getHtmlBody());
        mailService.sendEmails(campaignId, campaign.getClient(), request.getSubject(), request.getBody(), htmlBody, sends, delaySeconds);
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
                .orElseThrow(() -> new BusinessException("CampaignSend not found with id: " + sendId, HttpStatus.NOT_FOUND));
        try {
            campaignSend.setStatus(CampaignSendStatus.valueOf(status.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Invalid campaign send status: " + status, HttpStatus.BAD_REQUEST);
        }
        campaignSend.setSentAt(LocalDateTime.now());
        campaignSendRepository.save(campaignSend);
        reconcileCampaignStatuses();
        checkCampaignCompletion(campaignSend.getCampaign().getId());
    }

    @Transactional
    public Page<CampaignSendDTO> getCampaignSends(Long campaignId, String status, Pageable pageable) {
        if (!campaignRepository.existsById(campaignId)) {
            throw new BusinessException("Campaign not found with id: " + campaignId, HttpStatus.NOT_FOUND);
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
                throw new BusinessException("Invalid campaign send status: " + status, HttpStatus.BAD_REQUEST);
            }
            sendPage = campaignSendRepository.findByCampaignIdAndStatus(campaignId, sendStatus, pageable);
        }

        return sendPage.map(send -> CampaignSendDTO.builder()
                .id(send.getId())
                .campaignId(send.getCampaign().getId())
                .leadEmailId(send.getLeadEmail().getId())
                .email(send.getLeadEmail().getEmail())
                .leadId(send.getLeadEmail().getLead().getId())
                .leadInstitutionName(send.getLeadEmail().getLead().getInstitutionName())
                .leadCity(send.getLeadEmail().getLead().getCity())
                .status(send.getStatus())
                .sentAt(send.getSentAt())
                .build());
    }

    @Transactional
    public CampaignStartContext prepareCampaignStart(Long campaignId) {
        syncCampaignSendsWithAllLeads(campaignId);
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new BusinessException("Campaign not found with id: " + campaignId, HttpStatus.NOT_FOUND));

        validateClientReadyToSend(campaign.getClient().getId());

        if (campaign.getStatus() == CampaignStatus.RUNNING) {
            throw new BusinessException("Campaign is already running", HttpStatus.CONFLICT);
        }

        List<CampaignSend> retryableSends = campaignSendRepository.findByCampaignIdAndStatusIn(
                campaignId,
                Arrays.asList(CampaignSendStatus.PENDING, CampaignSendStatus.FAILED)
        );

        if (retryableSends.isEmpty()) {
            throw new BusinessException("No pending or failed emails to send", HttpStatus.BAD_REQUEST);
        }

        campaign.setStatus(CampaignStatus.RUNNING);
        campaignRepository.save(campaign);

        return new CampaignStartContext(campaign.getClient(), retryableSends);
    }

    @Transactional
    public void checkCampaignCompletion(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign == null) {
            return;
        }

        if (campaign.getStatus() == CampaignStatus.STOP_REQUESTED) {
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

    @Transactional
    public void setCampaignToDraft(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign == null) {
            return;
        }
        campaign.setStatus(CampaignStatus.DRAFT);
        campaignRepository.save(campaign);
    }

    @Transactional
    public void syncCampaignSendsWithAllLeads(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new BusinessException("Campaign not found with id: " + campaignId, HttpStatus.NOT_FOUND));

        List<LeadEmail> eligibleLeadEmails = findEligibleLeadEmails(campaign);
        Set<Long> eligibleLeadEmailIds = eligibleLeadEmails.stream()
            .map(LeadEmail::getId)
            .collect(Collectors.toSet());

        List<CampaignSend> existingSends = campaignSendRepository.findByCampaignId(campaignId);
        List<CampaignSend> staleSends = existingSends.stream()
            .filter(send -> !eligibleLeadEmailIds.contains(send.getLeadEmail().getId()))
            .collect(Collectors.toList());

        if (!staleSends.isEmpty()) {
            campaignSendRepository.deleteAll(staleSends);
        }

        Set<Long> existingLeadEmailIds = existingSends.stream()
            .filter(send -> eligibleLeadEmailIds.contains(send.getLeadEmail().getId()))
            .map(send -> send.getLeadEmail().getId())
            .collect(Collectors.toSet());

        List<CampaignSend> newSends = eligibleLeadEmails.stream()
                .filter(leadEmail -> !existingLeadEmailIds.contains(leadEmail.getId()))
                .map(leadEmail -> CampaignSend.builder()
                        .campaign(campaign)
                        .leadEmail(leadEmail)
                        .status(CampaignSendStatus.PENDING)
                        .build())
                .collect(Collectors.toList());

        if (!newSends.isEmpty()) {
            campaignSendRepository.saveAll(newSends);
        }
    }

    @Transactional
    public void ensureCampaignForEachClient() {
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

        List<LeadEmail> leadEmails = leadEmailRepository.findByLeadId(lead.getId());
        for (LeadEmail leadEmail : leadEmails) {
            syncLeadEmailAcrossAllCampaigns(leadEmail);
        }
    }

    @Transactional
    public void syncLeadEmailAcrossAllCampaigns(LeadEmail leadEmail) {
        if (leadEmail == null || leadEmail.getId() == null) {
            return;
        }

        List<CampaignSend> existingSends = campaignSendRepository.findByLeadEmailId(leadEmail.getId());
        Map<Long, CampaignSend> sendByCampaignId = new HashMap<>();
        for (CampaignSend send : existingSends) {
            if (send.getCampaign() != null && send.getCampaign().getId() != null) {
                sendByCampaignId.put(send.getCampaign().getId(), send);
            }
        }

        List<Long> leadCategoryIds = getActiveLeadCategoryIds(leadEmail.getLead().getId());
        List<Campaign> campaigns = campaignRepository.findAll();

        List<CampaignSend> missingSends = new ArrayList<>();
        List<CampaignSend> staleSends = new ArrayList<>();

        for (Campaign campaign : campaigns) {
            boolean shouldBelong = sharesCategory(
                    leadCategoryIds,
                    clientCategoryRepository.findCategoryIdsByClientId(campaign.getClient().getId())
            );
            CampaignSend existingSend = sendByCampaignId.get(campaign.getId());

            if (shouldBelong && existingSend == null) {
                missingSends.add(CampaignSend.builder()
                        .campaign(campaign)
                        .leadEmail(leadEmail)
                        .status(CampaignSendStatus.PENDING)
                        .build());
            }

            if (!shouldBelong && existingSend != null) {
                staleSends.add(existingSend);
            }
        }

        if (!staleSends.isEmpty()) {
            campaignSendRepository.deleteAll(staleSends);
        }

        if (!missingSends.isEmpty()) {
            campaignSendRepository.saveAll(missingSends);
            for (CampaignSend send : missingSends) {
                Campaign campaign = send.getCampaign();
                if (campaign.getStatus() != CampaignStatus.RUNNING && campaign.getStatus() != CampaignStatus.STOP_REQUESTED) {
                    campaign.setStatus(CampaignStatus.DRAFT);
                    campaignRepository.save(campaign);
                }
            }
        }

        Set<Long> touchedCampaignIds = new HashSet<>();
        staleSends.forEach(send -> touchedCampaignIds.add(send.getCampaign().getId()));
        missingSends.forEach(send -> touchedCampaignIds.add(send.getCampaign().getId()));

        for (Long campaignId : touchedCampaignIds) {
            Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
            if (campaign != null && campaign.getStatus() != CampaignStatus.RUNNING && campaign.getStatus() != CampaignStatus.STOP_REQUESTED) {
                checkCampaignCompletion(campaignId);
            }
        }
    }

    @Transactional
    public void syncCampaignsForClient(Long clientId) {
        List<Campaign> campaigns = campaignRepository.findAllByClientId(clientId);
        for (Campaign campaign : campaigns) {
            syncCampaignSendsWithAllLeads(campaign.getId());
        }
    }

    @Transactional
    protected void reconcileCampaignStatuses() {
        List<Campaign> campaigns = campaignRepository.findAll();
        for (Campaign campaign : campaigns) {
            if (campaign.getStatus() == CampaignStatus.RUNNING || campaign.getStatus() == CampaignStatus.STOP_REQUESTED) {
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

    private List<LeadEmail> findEligibleLeadEmails(Campaign campaign) {
        List<Long> clientCategoryIds = clientCategoryRepository.findCategoryIdsByClientId(campaign.getClient().getId());
        if (clientCategoryIds.isEmpty()) {
            return List.of();
        }

        Set<Long> eligibleLeadIds = leadCategoryRepository.findLeadIdsByCategoryIdIn(clientCategoryIds);
        if (eligibleLeadIds.isEmpty()) {
            return List.of();
        }

        return leadEmailRepository.findByLeadIdIn(new ArrayList<>(eligibleLeadIds));
    }

    private void validateClientReadyToSend(Long clientId) {
        List<ClientCategory> categories = clientCategoryRepository.findByClientId(clientId);
        List<String> missingCategories = categories.stream()
                .filter(entry -> entry.getCategory() != null && entry.getCategory().isActive())
                .filter(entry -> !clientCategoryDocumentRepository.existsByClientIdAndCategoryId(clientId, entry.getCategory().getId()))
                .map(entry -> entry.getCategory().getName())
                .collect(Collectors.toList());

        if (!missingCategories.isEmpty()) {
            throw new BusinessException("Client is missing documents for categories: " + String.join(", ", missingCategories), HttpStatus.BAD_REQUEST);
        }
    }

    private List<Long> getActiveLeadCategoryIds(Long leadId) {
        return leadCategoryRepository.findByLeadId(leadId).stream()
                .filter(leadCategory -> leadCategory.getCategory() != null && leadCategory.getCategory().isActive())
                .map(leadCategory -> leadCategory.getCategory().getId())
                .collect(Collectors.toList());
    }

    private boolean sharesCategory(List<Long> left, List<Long> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }

        Set<Long> rightSet = new HashSet<>(right);
        return left.stream().anyMatch(rightSet::contains);
    }

    public static class CampaignStartContext {
        private final Client client;
        private final List<CampaignSend> retryableSends;

        public CampaignStartContext(Client client, List<CampaignSend> retryableSends) {
            this.client = client;
            this.retryableSends = retryableSends;
        }

        public Client client() {
            return client;
        }

        public List<CampaignSend> retryableSends() {
            return retryableSends;
        }
    }
}
