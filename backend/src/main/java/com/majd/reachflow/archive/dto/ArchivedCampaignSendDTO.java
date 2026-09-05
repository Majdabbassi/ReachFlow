package com.majd.reachflow.archive.dto;

import com.majd.reachflow.entity.enums.CampaignSendStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArchivedCampaignSendDTO {
    private String email;
    private String leadInstitutionName;
    private String leadCity;
    private CampaignSendStatus status;
    private LocalDateTime sentAt;
    private Long archivedCampaignId;
}
