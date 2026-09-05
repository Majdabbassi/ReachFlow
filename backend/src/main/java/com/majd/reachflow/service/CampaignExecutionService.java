package com.majd.reachflow.service;

import com.majd.reachflow.dto.CampaignStartRequestDTO;
import com.majd.reachflow.dto.SelectiveSendRequestDTO;
import com.majd.reachflow.entity.*;
import com.majd.reachflow.entity.enums.CampaignSendStatus;
import com.majd.reachflow.entity.enums.CampaignStatus;
import com.majd.reachflow.exception.BusinessException;
import com.majd.reachflow.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignExecutionService {

    private final CampaignRepository campaignRepository;
    private final CampaignSendRepository campaignSendRepository;
    private final LeadEmailRepository leadEmailRepository;
    private final ClientCategoryRepository clientCategoryRepository;
    private final LeadCategoryRepository leadCategoryRepository;
    private final ClientCategoryDocumentRepository clientCategoryDocumentRepository;
    private final MailService mailService;

    @Transactional
    public void startCampaign(Long id, CampaignStartRequestDTO request) {
        if (request == null) {
            throw new BusinessException("Campaign request is required", HttpStatus.BAD_REQUEST);
        }

        CampaignStartContext context = prepareCampaignStart(id);
        int delaySeconds = request.getDelaySeconds() == null ? 2 : Math.max(request.getDelaySeconds(), 0);
        boolean htmlBody = Boolean.TRUE.equals(request.getHtmlBody());
        List<Long> sendIds = context.retryableSends().stream()
                .map(CampaignSend::getId)
                .toList();

        startCampaignAsync(id, request.getSubject(), request.getBody(), htmlBody, delaySeconds, sendIds);
    }

    @Async
    @Transactional
    public void startCampaignAsync(Long campaignId, String subject, String body, boolean htmlBody, int delaySeconds, List<Long> sendIds) {
        try {
            Campaign campaign = campaignRepository.findById(campaignId)
                    .orElseThrow(() -> new BusinessException("Campaign not found with id: " + campaignId, HttpStatus.NOT_FOUND));

            List<CampaignSend> sendsToProcess = campaignSendRepository.findByCampaignIdAndIdIn(campaignId, sendIds);
            if (sendsToProcess.isEmpty()) {
                setCampaignToDraft(campaignId);
                return;
            }

            mailService.sendEmails(campaignId, campaign.getClient(), subject, body, htmlBody, sendsToProcess, delaySeconds);
            checkCampaignCompletion(campaignId);
        } catch (Exception ex) {
            log.error("startCampaignAsync failed for campaign {}", campaignId, ex);
            setCampaignToDraft(campaignId);
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

        if (campaign.getStatus() == CampaignStatus.COMPLETED) {
            throw new BusinessException("Campaign is already completed", HttpStatus.CONFLICT);
        }

        if (campaign.getStatus() == CampaignStatus.STOP_REQUESTED) {
            return;
        }

        if (campaign.getStatus() != CampaignStatus.RUNNING && campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new BusinessException("Campaign cannot be stopped in status: " + campaign.getStatus(), HttpStatus.CONFLICT);
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

        CampaignStatus nextStatus = (total > 0 && pending > 0)
            ? CampaignStatus.RUNNING
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
    public void reconcileCampaignStatuses() {
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
        campaign.setScheduledAt(null);
        campaign.setStartRequest(null);
        campaignRepository.save(campaign);

        return new CampaignStartContext(campaign.getClient(), retryableSends);
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

    public static record CampaignStartContext(Client client, List<CampaignSend> retryableSends) {}
}
