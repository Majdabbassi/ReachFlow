package com.majd.reachflow.dto;

import lombok.Data;
import java.util.List;

@Data
public class CollectRequestDTO {
    private List<String> cities;
    private List<String> keywords;
    private int maxResults;
    private String webhookUrl;
    /** Categories of the keywords searched; every lead found is filed under them. */
    private List<Long> categoryIds;
}
