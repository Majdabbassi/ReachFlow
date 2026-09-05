package com.majd.reachflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SelectiveSendRequestDTO {
    private List<Long> sendIds;
    private String subject;
    private String body;
    private Integer delaySeconds;
    private Boolean htmlBody;
}
