package com.majd.n8n.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PlaceCityDTO {
    private Long id;
    private String name;
    private List<PlaceDistrictDTO> districts;
}
