package com.majd.n8n.mapper;

import com.majd.n8n.dto.CampaignDTO;
import com.majd.n8n.entity.Campaign;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CampaignMapper {
    @Mapping(source = "client.id", target = "clientId")
    CampaignDTO toDTO(Campaign campaign);

    @Mapping(target = "client", ignore = true)
    @Mapping(target = "campaignSends", ignore = true)
    Campaign toEntity(CampaignDTO campaignDTO);
}
