package com.majd.reachflow.repository;

import com.majd.reachflow.entity.ClientCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClientCategoryRepository extends JpaRepository<ClientCategory, Long> {
    @Query("SELECT cc FROM ClientCategory cc WHERE cc.client.id = :clientId ORDER BY cc.id ASC")
    List<ClientCategory> findByClientId(@Param("clientId") Long clientId);

    @Query("SELECT DISTINCT cc.category.id FROM ClientCategory cc WHERE cc.client.id = :clientId AND cc.category.active = true")
    List<Long> findCategoryIdsByClientId(@Param("clientId") Long clientId);

    boolean existsByClientIdAndCategoryId(Long clientId, Long categoryId);

    boolean existsByCategoryId(Long categoryId);
}
