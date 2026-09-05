package com.majd.reachflow.repository;

import com.majd.reachflow.entity.PlaceState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlaceStateRepository extends JpaRepository<PlaceState, Long> {
    List<PlaceState> findByCountryIdOrderByNameAsc(Long countryId);
    Optional<PlaceState> findByCountryIdAndNameIgnoreCase(Long countryId, String name);
}
