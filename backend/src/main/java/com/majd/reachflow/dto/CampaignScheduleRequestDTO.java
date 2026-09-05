package com.majd.reachflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignScheduleRequestDTO {
    private LocalDateTime scheduledAt;
    private String subject;
    private String body;
    private Integer delaySeconds;
    private Boolean htmlBody;
}
