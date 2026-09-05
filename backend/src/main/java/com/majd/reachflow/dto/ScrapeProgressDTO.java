package com.majd.reachflow.dto;

import com.majd.reachflow.entity.enums.ScrapeStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ScrapeProgressDTO {
    private String jobId;
    private ScrapeStatus status;
    private String message;
    private Integer leadsFound;
    private Integer leadsImported;
    private String errorMessage;
    private String keywords;
    private String cities;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
