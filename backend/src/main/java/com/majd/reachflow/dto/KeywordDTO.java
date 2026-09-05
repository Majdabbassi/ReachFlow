package com.majd.reachflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KeywordDTO {
    private Long id;

    @NotBlank(message = "English name is required")
    private String nameEn;

    @NotBlank(message = "German name is required")
    private String nameDe;

    private Long categoryId;
    private String categoryName;
    private boolean active;
}
