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
public class DeleteLeadEmailsResponseDTO {
    private int deletedCount;
    private int skippedCount;
    private List<Long> skippedEmailIds;
}
