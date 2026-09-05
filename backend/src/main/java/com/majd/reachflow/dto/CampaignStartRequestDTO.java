package com.majd.reachflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignStartRequestDTO {
    private String webhookUrl;
    private String subject;
    private String body;
    private Integer delaySeconds;
    private Boolean htmlBody;
}
