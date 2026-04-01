package com.majd.n8n.mapper;

import com.majd.n8n.dto.ClientDTO;
import com.majd.n8n.entity.Client;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ClientMapper {
    ClientDTO toDTO(Client client);

    @Mapping(target = "campaigns", ignore = true)
    @Mapping(target = "document", ignore = true)
    @Mapping(target = "documentContentType", ignore = true)
    Client toEntity(ClientDTO clientDTO);
}
