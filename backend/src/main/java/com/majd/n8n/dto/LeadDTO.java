package com.majd.n8n.dto;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadDTO {
    private Long id;

    @Email(message = "Invalid email format")
    private String email;

    @Email(message = "Invalid email format")
    private String primaryEmail;

    private List<String> emails;

    private String institutionName;
    private String city;
    private String phone;
    private String address;
    private Double latitude;
    private Double longitude;
    private String website;
    private String source;
    private LocalDateTime createdAt;
}
