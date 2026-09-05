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
public class CategoryWithKeywordsDTO {
    private Long id;
    private String name;
    private String color;
    private boolean active;
    private List<KeywordDTO> keywords;
}
