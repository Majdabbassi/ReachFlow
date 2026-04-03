package com.majd.n8n.repository;

import com.majd.n8n.entity.Campaign;
import com.majd.n8n.entity.enums.CampaignStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, Long> {
	Optional<Campaign> findByClientId(Long clientId);

	List<Campaign> findAllByClientId(Long clientId);

	List<Campaign> findByStatusAndScheduledAtIsNotNullAndScheduledAtLessThanEqual(CampaignStatus status, LocalDateTime scheduledAt);

	boolean existsByClientId(Long clientId);

	@Modifying
	@Query("DELETE FROM Campaign c WHERE c.id IN :campaignIds")
	int bulkDeleteByIdIn(@Param("campaignIds") List<Long> campaignIds);
}
