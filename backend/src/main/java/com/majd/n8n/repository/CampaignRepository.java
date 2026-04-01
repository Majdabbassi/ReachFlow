package com.majd.n8n.repository;

import com.majd.n8n.entity.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, Long> {
	Optional<Campaign> findByClientId(Long clientId);

	boolean existsByClientId(Long clientId);
}
