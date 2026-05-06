package com.majd.n8n.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PlaceCountryDTO {
    private Long id;
    private String code;
    private String name;
    private List<PlaceStateDTO> states;
}
