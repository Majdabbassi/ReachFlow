package com.majd.reachflow.service;

import com.majd.reachflow.entity.Campaign;
import com.majd.reachflow.entity.CampaignSend;
import com.majd.reachflow.repository.CampaignRepository;
import com.majd.reachflow.repository.CampaignSendRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncEmailSender {

    private final CampaignRepository campaignRepository;
    private final CampaignSendRepository campaignSendRepository;
    private final MailService mailService;

    @Async
    @Transactional
    public void sendSelectedEmails(Long campaignId, String subject, String body, boolean htmlBody, List<Long> sendIds, int delaySeconds) {
        Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign == null) {
            log.warn("Campaign {} no longer exists while sending selected emails", campaignId);
            return;
        }

        List<CampaignSend> sends = campaignSendRepository.findByCampaignIdAndIdIn(campaignId, sendIds);
        if (sends.isEmpty()) {
            log.warn("No selectable sends found for campaign {} during selected send", campaignId);
            return;
        }

        mailService.sendEmails(campaignId, campaign.getClient(), subject, body, htmlBody, sends, delaySeconds);
    }
}