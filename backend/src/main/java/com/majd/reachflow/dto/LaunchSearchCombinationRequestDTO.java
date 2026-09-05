package com.majd.reachflow.dto;

import com.majd.reachflow.entity.enums.SearchCombinationStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LaunchSearchCombinationRequestDTO {
    private SearchCombinationStatus status;
    private String failureReason;
    private Integer maxResults;
}
