package com.majd.n8n.repository;

import com.majd.n8n.entity.Keyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KeywordRepository extends JpaRepository<Keyword, Long> {
    List<Keyword> findByCategoryIdAndActiveTrue(Long categoryId);

    List<Keyword> findAllByActiveTrue();

    boolean existsByCategoryIdAndNameDeIgnoreCase(Long categoryId, String nameDe);
}