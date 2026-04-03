package com.majd.n8n.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignStartRequest {

    @Column(name = "start_subject", length = 500)
    private String subject;

    @Column(name = "start_body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "start_delay_seconds")
    private Integer delaySeconds;

    @Column(name = "start_html_body")
    private Boolean htmlBody;
}
