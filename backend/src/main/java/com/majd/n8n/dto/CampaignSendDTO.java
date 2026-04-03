package com.majd.n8n.dto;

import com.majd.n8n.entity.enums.CampaignSendStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignSendDTO {
    private Long id;
    private Long campaignId;
    private Long leadEmailId;
    private String email;
    private Long leadId;
    private String leadInstitutionName;
    private String leadCity;
    private CampaignSendStatus status;
    private LocalDateTime sentAt;
    private LocalDateTime repliedAt;
}