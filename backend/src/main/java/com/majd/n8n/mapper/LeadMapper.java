package com.majd.n8n.mapper;

import com.majd.n8n.dto.LeadDTO;
import com.majd.n8n.entity.Lead;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface LeadMapper {
    LeadMapper INSTANCE = Mappers.getMapper(LeadMapper.class);

    LeadDTO toDTO(Lead lead);
    Lead toEntity(LeadDTO leadDTO);
}
