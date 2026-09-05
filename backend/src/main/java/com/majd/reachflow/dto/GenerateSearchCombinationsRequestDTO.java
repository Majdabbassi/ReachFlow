package com.majd.reachflow.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GenerateSearchCombinationsRequestDTO {
    private List<Long> keywordIds;
    private List<Long> stateIds;
    private List<Long> cityIds;
    private List<Long> districtIds;
    private Integer maxResults;
}
