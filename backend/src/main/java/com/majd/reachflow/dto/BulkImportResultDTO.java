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
public class BulkImportResultDTO {
    private int imported;
    private int skipped;
    private int failed;
    private List<String> errors;
}
