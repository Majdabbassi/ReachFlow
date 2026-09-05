package com.majd.reachflow.repository;

import com.majd.reachflow.entity.ClientCategoryDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientCategoryDocumentRepository extends JpaRepository<ClientCategoryDocument, Long> {
    Optional<ClientCategoryDocument> findByClientIdAndCategoryId(Long clientId, Long categoryId);

    List<ClientCategoryDocument> findByClientId(Long clientId);

    boolean existsByClientIdAndCategoryId(Long clientId, Long categoryId);
}
