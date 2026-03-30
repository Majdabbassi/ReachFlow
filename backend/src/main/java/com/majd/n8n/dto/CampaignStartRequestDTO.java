package com.majd.n8n.dto;

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
}
