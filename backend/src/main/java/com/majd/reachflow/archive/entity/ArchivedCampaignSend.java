package com.majd.reachflow.archive.entity;

import com.majd.reachflow.entity.enums.CampaignSendStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "archived_campaign_sends")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArchivedCampaignSend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    private String leadInstitutionName;

    private String leadCity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignSendStatus status;

    private LocalDateTime sentAt;

    @Column(nullable = false)
    private Long archivedCampaignId;

    @Column(nullable = false)
    private Long originalId;
}
