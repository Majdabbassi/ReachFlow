package com.majd.n8n.repository;

import com.majd.n8n.entity.Lead;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LeadRepository extends JpaRepository<Lead, Long> {
    Optional<Lead> findByEmail(String email);
    Optional<Lead> findFirstByWebsiteIgnoreCase(String website);
    Optional<Lead> findFirstByInstitutionNameIgnoreCaseAndCityIgnoreCase(String institutionName, String city);
    Page<Lead> findByCityContainingIgnoreCaseAndSourceContainingIgnoreCase(String city, String source, Pageable pageable);
    Page<Lead> findByCityContainingIgnoreCase(String city, Pageable pageable);
    Page<Lead> findBySourceContainingIgnoreCase(String source, Pageable pageable);
}
