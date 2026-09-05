package com.majd.reachflow.service;

import com.majd.reachflow.dto.CampaignStartRequestDTO;
import com.majd.reachflow.entity.Campaign;
import com.majd.reachflow.entity.CampaignStartRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignSchedulerService {

    private final CampaignService campaignService;

    @Scheduled(fixedDelay = 60000)
    public void runScheduledCampaigns() {
        List<Campaign> dueCampaigns = campaignService.getSchedulableCampaigns(LocalDateTime.now());
        for (Campaign campaign : dueCampaigns) {
            if (campaign.getStartRequest() == null) {
                log.warn("Skipping scheduled campaign {} due to missing start request", campaign.getId());
                continue;
            }

            try {
                campaignService.startCampaign(campaign.getId(), toStartRequestDTO(campaign.getStartRequest()));
            } catch (Exception ex) {
                log.error("Failed to start scheduled campaign {}", campaign.getId(), ex);
            }
        }
    }

    private CampaignStartRequestDTO toStartRequestDTO(CampaignStartRequest request) {
        return CampaignStartRequestDTO.builder()
                .subject(request.getSubject())
                .body(request.getBody())
                .delaySeconds(request.getDelaySeconds())
                .htmlBody(request.getHtmlBody())
                .build();
    }
}
