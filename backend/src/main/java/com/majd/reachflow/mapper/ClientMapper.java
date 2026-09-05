package com.majd.reachflow.mapper;

import com.majd.reachflow.dto.ClientDTO;
import com.majd.reachflow.entity.Client;
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
