package com.majd.reachflow.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PlaceStateDTO {
    private Long id;
    private String name;
    private List<PlaceCityDTO> cities;
}
