package com.majd.n8n.archive.repository;

import com.majd.n8n.archive.entity.ArchivedCampaign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArchivedCampaignRepository extends JpaRepository<ArchivedCampaign, Long> {
    Page<ArchivedCampaign> findByArchivedClientId(Long archivedClientId, Pageable pageable);
    List<ArchivedCampaign> findByArchivedClientId(Long archivedClientId);
    List<ArchivedCampaign> findByArchivedClientIdAndOriginalIdIn(Long archivedClientId, List<Long> originalIds);
}
