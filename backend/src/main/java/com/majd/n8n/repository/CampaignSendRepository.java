package com.majd.n8n.repository;

import com.majd.n8n.entity.CampaignSend;
import com.majd.n8n.entity.enums.CampaignSendStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface CampaignSendRepository extends JpaRepository<CampaignSend, Long> {

    @Query("SELECT cs.lead.id FROM CampaignSend cs WHERE cs.campaign.client.id = :clientId")
    Set<Long> findLeadIdsAlreadySentByClient(@Param("clientId") Long clientId);

    List<CampaignSend> findByCampaignIdAndStatus(Long campaignId, CampaignSendStatus status);

    Page<CampaignSend> findByCampaignIdAndStatus(Long campaignId, CampaignSendStatus status, Pageable pageable);

    @Query("SELECT COUNT(cs) FROM CampaignSend cs WHERE cs.campaign.id = :campaignId")
    long countByCampaignId(@Param("campaignId") Long campaignId);

    @Query("SELECT COUNT(cs) FROM CampaignSend cs WHERE cs.campaign.id = :campaignId AND cs.status = :status")
    long countByCampaignIdAndStatus(@Param("campaignId") Long campaignId, @Param("status") CampaignSendStatus status);
}
