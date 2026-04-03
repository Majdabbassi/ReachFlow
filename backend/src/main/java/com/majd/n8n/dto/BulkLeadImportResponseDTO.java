package com.majd.n8n.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkLeadImportResponseDTO {
    private List<LeadDTO> saved;
    private List<String> errors;
}
