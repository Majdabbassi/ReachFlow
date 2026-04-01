package com.majd.n8n.entity;

import com.majd.n8n.entity.enums.CampaignSendStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "campaign_sends")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignSend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_email_id", nullable = false)
    private LeadEmail leadEmail;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CampaignSendStatus status = CampaignSendStatus.PENDING;

    private LocalDateTime sentAt;
}
