package com.majd.n8n.archive.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArchivedClientDTO {
    private Long id;
    private String name;
    private String email;
    private String appPassword;
    private String phone;
    private String documentName;
    private String documentContentType;
    private LocalDateTime createdAt;
    private LocalDateTime archivedAt;
    private Long originalId;
}
