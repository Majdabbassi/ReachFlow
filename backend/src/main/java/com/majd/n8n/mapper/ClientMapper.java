package com.majd.n8n.mapper;

import com.majd.n8n.dto.ClientDTO;
import com.majd.n8n.entity.Client;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ClientMapper {
    @Mapping(target = "categories", ignore = true)
    ClientDTO toDTO(Client client);

    @Mapping(target = "campaigns", ignore = true)
    @Mapping(target = "clientCategories", ignore = true)
    @Mapping(target = "clientCategoryDocuments", ignore = true)
    Client toEntity(ClientDTO clientDTO);
}
