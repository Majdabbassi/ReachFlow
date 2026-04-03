package com.majd.n8n.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailAuditItemDTO {
    private Long id;
    private Long leadId;
    private String institutionName;
    private String email;
    private boolean primary;
    private String issueType;
    private long duplicateCount;
}
