package com.majd.n8n.repository;

import com.majd.n8n.entity.LeadEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeadEmailRepository extends JpaRepository<LeadEmail, Long> {
    List<LeadEmail> findByLeadId(Long leadId);

    List<LeadEmail> findByLeadIdIn(List<Long> leadIds);

    Optional<LeadEmail> findByLeadIdAndIsPrimaryTrue(Long leadId);
    boolean existsByLeadIdAndEmail(Long leadId, String email);
    Optional<LeadEmail> findByEmail(String email);
}
