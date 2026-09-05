package com.majd.reachflow.mapper;

import com.majd.reachflow.dto.LeadDTO;
import com.majd.reachflow.entity.Lead;
import com.majd.reachflow.entity.LeadEmail;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface LeadMapper {
    @Mapping(target = "primaryEmail", source = "email")
    @Mapping(target = "emails", expression = "java(toEmailList(lead))")
    @Mapping(target = "categoryIds", ignore = true)
    @Mapping(target = "categoryNames", ignore = true)
    LeadDTO toDTO(Lead lead);

    @Mapping(target = "leadEmails", ignore = true)
    @Mapping(target = "leadCategories", ignore = true)
    @Mapping(target = "email", ignore = true)
    Lead toEntity(LeadDTO leadDTO);

    default List<String> toEmailList(Lead lead) {
        if (lead == null || lead.getLeadEmails() == null) {
            return List.of();
        }

        return lead.getLeadEmails().stream()
                .sorted(
                        Comparator.comparing(LeadEmail::isPrimary).reversed()
                                .thenComparing(LeadEmail::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(LeadEmail::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                )
                .map(LeadEmail::getEmail)
                .collect(Collectors.toList());
    }
}
