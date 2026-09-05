package com.majd.reachflow.repository;

import com.majd.reachflow.entity.LeadCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface LeadCategoryRepository extends JpaRepository<LeadCategory, Long> {
    @Query("SELECT lc FROM LeadCategory lc WHERE lc.lead.id = :leadId ORDER BY lc.id ASC")
    List<LeadCategory> findByLeadId(@Param("leadId") Long leadId);

    @Query("SELECT lc FROM LeadCategory lc WHERE lc.lead.id IN :leadIds ORDER BY lc.id ASC")
    List<LeadCategory> findByLeadIdIn(@Param("leadIds") List<Long> leadIds);

    boolean existsByLeadIdAndCategoryId(Long leadId, Long categoryId);

    @Query("SELECT DISTINCT lc.lead.id FROM LeadCategory lc WHERE lc.category.id IN :categoryIds AND lc.category.active = true")
    Set<Long> findLeadIdsByCategoryIdIn(@Param("categoryIds") List<Long> categoryIds);

    boolean existsByCategoryId(Long categoryId);
}
