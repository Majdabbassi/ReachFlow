package com.majd.reachflow.dto;

import com.majd.reachflow.entity.enums.SearchCombinationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SearchCombinationDTO {
    private Long id;
    private Long keywordId;
    private String keywordNameEn;
    private String keywordNameDe;
    private Long categoryId;
    private String categoryName;
    private Long cityId;
    private String cityName;
    private Long districtId;
    private String districtName;
    private String placeDisplayName;
    private SearchCombinationStatus status;
    private Integer maxResults;
    private LocalDateTime launchedAt;
    private LocalDateTime failedAt;
    private String failureReason;
    private LocalDateTime createdAt;
}
