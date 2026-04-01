package com.majd.n8n.archive.repository;

import com.majd.n8n.archive.entity.ArchivedClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ArchivedClientRepository extends JpaRepository<ArchivedClient, Long> {
    Optional<ArchivedClient> findByOriginalId(Long originalId);
}
