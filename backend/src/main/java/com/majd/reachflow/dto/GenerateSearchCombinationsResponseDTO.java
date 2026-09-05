package com.majd.reachflow.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GenerateSearchCombinationsResponseDTO {
    private int created;
    private int existing;
}
