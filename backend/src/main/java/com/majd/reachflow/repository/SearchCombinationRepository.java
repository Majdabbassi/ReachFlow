package com.majd.reachflow.repository;

import com.majd.reachflow.entity.SearchCombination;
import com.majd.reachflow.entity.enums.SearchCombinationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SearchCombinationRepository extends JpaRepository<SearchCombination, Long> {
    boolean existsByKeywordIdAndPlaceKey(Long keywordId, String placeKey);

    Optional<SearchCombination> findByKeywordIdAndPlaceKey(Long keywordId, String placeKey);

    @Query("""
            SELECT sc
            FROM SearchCombination sc
            WHERE (:status IS NULL OR sc.status = :status)
              AND (:categoryId IS NULL OR sc.keyword.category.id = :categoryId)
            """)
    Page<SearchCombination> findForList(
            @Param("status") SearchCombinationStatus status,
            @Param("categoryId") Long categoryId,
            Pageable pageable
    );
}
