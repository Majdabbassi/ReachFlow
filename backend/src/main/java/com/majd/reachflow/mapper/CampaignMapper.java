package com.majd.reachflow.mapper;

import com.majd.reachflow.dto.CampaignDTO;
import com.majd.reachflow.entity.Campaign;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CampaignMapper {
    @Mapping(source = "client.id", target = "clientId")
    CampaignDTO toDTO(Campaign campaign);

    @Mapping(target = "client", ignore = true)
    @Mapping(target = "campaignSends", ignore = true)
    @Mapping(target = "startRequest", ignore = true)
    Campaign toEntity(CampaignDTO campaignDTO);
}
