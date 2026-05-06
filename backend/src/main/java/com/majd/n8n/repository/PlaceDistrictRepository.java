package com.majd.n8n.repository;

import com.majd.n8n.entity.PlaceDistrict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlaceDistrictRepository extends JpaRepository<PlaceDistrict, Long> {
    List<PlaceDistrict> findByCityIdOrderByNameAsc(Long cityId);
    List<PlaceDistrict> findByCityIdIn(List<Long> cityIds);
    Optional<PlaceDistrict> findByCityIdAndNameIgnoreCase(Long cityId, String name);
}
