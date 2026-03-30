package com.majd.n8n.mapper;

import com.majd.n8n.dto.ClientDTO;
import com.majd.n8n.entity.Client;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface ClientMapper {
    ClientMapper INSTANCE = Mappers.getMapper(ClientMapper.class);

    ClientDTO toDTO(Client client);

    @Mapping(target = "campaigns", ignore = true)
    @Mapping(target = "document", ignore = true)
    Client toEntity(ClientDTO clientDTO);
}
