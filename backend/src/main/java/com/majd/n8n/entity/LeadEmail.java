package com.majd.n8n.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "lead_emails",
        uniqueConstraints = @UniqueConstraint(name = "uk_lead_email", columnNames = {"lead_id", "email"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id", nullable = false)
    private Lead lead;

    @Column(nullable = false)
    private String email;

    @Builder.Default
    @Column(nullable = false)
    private boolean isPrimary = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}