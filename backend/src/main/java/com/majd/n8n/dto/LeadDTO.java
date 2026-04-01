package com.majd.n8n.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadDTO {
    private Long id;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    private String allEmails;

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
