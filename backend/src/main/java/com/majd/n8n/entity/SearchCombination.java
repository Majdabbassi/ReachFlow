package com.majd.n8n.entity;

import com.majd.n8n.entity.enums.SearchCombinationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "search_combinations",
        uniqueConstraints = @UniqueConstraint(name = "uk_search_combination_keyword_place", columnNames = {"keyword_id", "place_key"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchCombination {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "keyword_id", nullable = false)
    private Keyword keyword;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id", nullable = false)
    private PlaceCity city;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id")
    private PlaceDistrict district;

    @Column(name = "place_key", nullable = false, length = 128)
    private String placeKey;

    @Column(name = "place_display_name", nullable = false)
    private String placeDisplayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private SearchCombinationStatus status = SearchCombinationStatus.PENDING;

    private Integer maxResults;

    private LocalDateTime launchedAt;

    private LocalDateTime failedAt;

    @Column(length = 1000)
    private String failureReason;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
