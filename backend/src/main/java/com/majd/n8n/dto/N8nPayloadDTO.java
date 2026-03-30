package com.majd.n8n.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class N8nPayloadDTO {
    private String clientEmail;
    private String appPassword;
    private String subject;
    private String body;
    private List<RecipientDTO> recipients;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecipientDTO {
        private Long campaignSendId;
        private String email;
    }
}
