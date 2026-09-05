package com.majd.reachflow.repository;

import com.majd.reachflow.entity.PlaceCity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlaceCityRepository extends JpaRepository<PlaceCity, Long> {
    List<PlaceCity> findByStateIdOrderByNameAsc(Long stateId);
    Optional<PlaceCity> findByStateIdAndNameIgnoreCase(Long stateId, String name);
}
