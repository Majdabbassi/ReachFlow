package com.majd.reachflow.service;

import com.majd.reachflow.repository.CampaignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncEmailSender {

    private final CampaignRepository campaignRepository;
    private final MailService mailService;

    // Not @Transactional: MailService commits each email in its own transaction.
    @Async
    public void sendSelectedEmails(Long campaignId, String subject, String body, boolean htmlBody, List<Long> sendIds, int delaySeconds) {
        if (!campaignRepository.existsById(campaignId)) {
            log.warn("Campaign {} no longer exists while sending selected emails", campaignId);
            return;
        }
        if (sendIds == null || sendIds.isEmpty()) {
            log.warn("No selectable sends found for campaign {} during selected send", campaignId);
            return;
        }

        mailService.sendEmails(campaignId, sendIds, subject, body, htmlBody, delaySeconds);
    }
}
