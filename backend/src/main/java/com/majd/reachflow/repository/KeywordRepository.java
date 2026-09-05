package com.majd.reachflow.repository;

import com.majd.reachflow.entity.Keyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KeywordRepository extends JpaRepository<Keyword, Long> {
    List<Keyword> findByCategoryIdAndActiveTrue(Long categoryId);

    List<Keyword> findAllByActiveTrue();

    boolean existsByCategoryIdAndNameDeIgnoreCase(Long categoryId, String nameDe);
}
