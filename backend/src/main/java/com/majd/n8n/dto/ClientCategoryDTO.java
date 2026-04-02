package com.majd.n8n.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientCategoryDTO {
    private Long categoryId;
    private String categoryName;
    private String color;
    private boolean hasDocument;
}