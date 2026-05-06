package com.majd.n8n.dto;

import com.majd.n8n.entity.enums.SearchCombinationStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LaunchSearchCombinationRequestDTO {
    private SearchCombinationStatus status;
    private String failureReason;
    private Integer maxResults;
}
