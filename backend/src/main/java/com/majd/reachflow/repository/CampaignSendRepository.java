package com.majd.reachflow.repository;

import com.majd.reachflow.entity.CampaignSend;
import com.majd.reachflow.entity.enums.CampaignSendStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface CampaignSendRepository extends JpaRepository<CampaignSend, Long> {

    Optional<CampaignSend> findByTrackingToken(String trackingToken);

    @Query("SELECT cs.leadEmail.id FROM CampaignSend cs WHERE cs.campaign.id = :campaignId")
    Set<Long> findLeadEmailIdsByCampaignId(@Param("campaignId") Long campaignId);

    @Query("SELECT cs.campaign.id FROM CampaignSend cs WHERE cs.leadEmail.lead.id = :leadId")
    Set<Long> findCampaignIdsByLeadEmailLeadId(@Param("leadId") Long leadId);

    @Query("SELECT cs.campaign.id FROM CampaignSend cs WHERE cs.leadEmail.id = :leadEmailId")
    Set<Long> findCampaignIdsByLeadEmailId(@Param("leadEmailId") Long leadEmailId);

    List<CampaignSend> findByCampaignIdAndStatus(Long campaignId, CampaignSendStatus status);

    @Query("SELECT cs FROM CampaignSend cs WHERE cs.campaign.client.id = :clientId AND cs.status = :status")
    List<CampaignSend> findByClientIdAndStatus(@Param("clientId") Long clientId, @Param("status") CampaignSendStatus status);

    List<CampaignSend> findByCampaignIdAndStatusIn(Long campaignId, List<CampaignSendStatus> statuses);

    Page<CampaignSend> findByCampaignIdAndStatus(Long campaignId, CampaignSendStatus status, Pageable pageable);

    Page<CampaignSend> findByCampaignId(Long campaignId, Pageable pageable);

    List<CampaignSend> findByCampaignId(Long campaignId);

    List<CampaignSend> findByCampaignIdIn(List<Long> campaignIds);

    List<CampaignSend> findByCampaignIdAndIdIn(Long campaignId, List<Long> sendIds);

    List<CampaignSend> findByLeadEmailId(Long leadEmailId);

    @Modifying
    @Query("DELETE FROM CampaignSend cs WHERE cs.leadEmail.lead.id = :leadId")
    int deleteByLeadEmailLeadId(@Param("leadId") Long leadId);

    @Modifying
    @Query("DELETE FROM CampaignSend cs WHERE cs.leadEmail.id IN :leadEmailIds")
    int deleteByLeadEmailIdIn(@Param("leadEmailIds") List<Long> leadEmailIds);

    @Modifying
    @Query("DELETE FROM CampaignSend cs WHERE cs.campaign.id IN :campaignIds")
    int bulkDeleteByCampaignIdIn(@Param("campaignIds") List<Long> campaignIds);

    @Query("SELECT COUNT(cs) FROM CampaignSend cs WHERE cs.campaign.id = :campaignId")
    long countByCampaignId(@Param("campaignId") Long campaignId);

    @Query("SELECT COUNT(cs) FROM CampaignSend cs WHERE cs.campaign.id = :campaignId AND cs.status = :status")
    long countByCampaignIdAndStatus(@Param("campaignId") Long campaignId, @Param("status") CampaignSendStatus status);
}
