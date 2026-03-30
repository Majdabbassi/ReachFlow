package com.majd.n8n.dto;

import com.majd.n8n.entity.enums.CampaignStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignDTO {
    private Long id;

    @NotBlank(message = "Campaign name is required")
    private String name;

    private CampaignStatus status;

    @NotNull(message = "Client ID is required")
    private Long clientId;

    private LocalDateTime createdAt;
}
