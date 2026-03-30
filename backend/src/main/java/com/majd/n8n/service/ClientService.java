package com.majd.n8n.service;

import com.majd.n8n.dto.ClientDTO;
import com.majd.n8n.entity.Client;
import com.majd.n8n.mapper.ClientMapper;
import com.majd.n8n.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final ClientMapper clientMapper;

    @Transactional(readOnly = true)
    public List<ClientDTO> getAllClients() {
        return clientRepository.findAll().stream()
                .map(clientMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ClientDTO getClientById(Long id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Client not found with id: " + id));
        return clientMapper.toDTO(client);
    }

    @Transactional
    public ClientDTO createClient(ClientDTO clientDTO) {
        Client client = clientMapper.toEntity(clientDTO);
        return clientMapper.toDTO(clientRepository.save(client));
    }

    @Transactional
    public ClientDTO updateClient(Long id, ClientDTO clientDTO) {
        Client existingClient = clientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Client not found with id: " + id));
        
        existingClient.setName(clientDTO.getName());
        existingClient.setEmail(clientDTO.getEmail());
        existingClient.setAppPassword(clientDTO.getAppPassword());
        existingClient.setPhone(clientDTO.getPhone());
        
        return clientMapper.toDTO(clientRepository.save(existingClient));
    }

    @Transactional
    public void updateClientDocument(Long id, MultipartFile file) throws IOException {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Client not found with id: " + id));
        client.setDocument(file.getBytes());
        clientRepository.save(client);
    }
}
