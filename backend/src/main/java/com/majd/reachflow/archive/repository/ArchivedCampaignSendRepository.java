package com.majd.reachflow.archive.repository;

import com.majd.reachflow.archive.entity.ArchivedCampaignSend;
import com.majd.reachflow.entity.enums.CampaignSendStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArchivedCampaignSendRepository extends JpaRepository<ArchivedCampaignSend, Long> {
    Page<ArchivedCampaignSend> findByArchivedCampaignId(Long archivedCampaignId, Pageable pageable);
    Page<ArchivedCampaignSend> findByArchivedCampaignIdAndStatus(Long archivedCampaignId, CampaignSendStatus status, Pageable pageable);
    List<ArchivedCampaignSend> findByArchivedCampaignId(Long archivedCampaignId);
    long countByArchivedCampaignId(Long archivedCampaignId);
    long countByArchivedCampaignIdAndStatus(Long archivedCampaignId, CampaignSendStatus status);
}
