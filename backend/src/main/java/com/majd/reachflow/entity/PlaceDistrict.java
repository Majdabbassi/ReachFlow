package com.majd.reachflow.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "place_districts", uniqueConstraints = @UniqueConstraint(name = "uk_place_district_city_name", columnNames = {"city_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceDistrict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id", nullable = false)
    private PlaceCity city;
}
