package com.majd.reachflow.archive.dto;

import com.majd.reachflow.entity.enums.CampaignStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArchivedCampaignDTO {
    private Long id;
    private String name;
    private CampaignStatus status;
    private LocalDateTime createdAt;
    private Long archivedClientId;
    private Long originalId;
    private CampaignStats stats;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CampaignStats {
        private Long total;
        private Long sent;
        private Long pending;
        private Long failed;
    }
}
