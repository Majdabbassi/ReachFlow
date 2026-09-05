package com.majd.reachflow.repository;

import com.majd.reachflow.entity.PlaceCountry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlaceCountryRepository extends JpaRepository<PlaceCountry, Long> {
    Optional<PlaceCountry> findByCodeIgnoreCase(String code);
}
