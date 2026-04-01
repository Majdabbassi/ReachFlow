package com.majd.n8n.service;

import com.majd.n8n.dto.LeadDTO;
import com.majd.n8n.entity.Lead;
import com.majd.n8n.mapper.LeadMapper;
import com.majd.n8n.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository leadRepository;
    private final LeadMapper leadMapper;

    @Transactional(readOnly = true)
    public Page<LeadDTO> getAllLeads(String city, String source, Pageable pageable) {
        Page<Lead> leads;
        if (city != null && source != null) {
            leads = leadRepository.findByCityContainingIgnoreCaseAndSourceContainingIgnoreCase(city, source, pageable);
        } else if (city != null) {
            leads = leadRepository.findByCityContainingIgnoreCase(city, pageable);
        } else if (source != null) {
            leads = leadRepository.findBySourceContainingIgnoreCase(source, pageable);
        } else {
            leads = leadRepository.findAll(pageable);
        }
        return leads.map(leadMapper::toDTO);
    }

    @Transactional
    public LeadDTO createLead(LeadDTO leadDTO) {
        Lead lead = leadMapper.toEntity(leadDTO);
        return leadMapper.toDTO(leadRepository.save(lead));
    }

    @Transactional
    public LeadDTO createOrSkipLead(LeadDTO leadDTO) {
        return leadRepository.findByEmail(leadDTO.getEmail())
                .map(existing -> {
                    existing.setInstitutionName(leadDTO.getInstitutionName());
                    existing.setCity(leadDTO.getCity());
                    existing.setPhone(leadDTO.getPhone());
                    existing.setAddress(leadDTO.getAddress());
                    existing.setLatitude(leadDTO.getLatitude());
                    existing.setLongitude(leadDTO.getLongitude());
                    existing.setWebsite(leadDTO.getWebsite());
                    existing.setSource(leadDTO.getSource());
                    return leadMapper.toDTO(leadRepository.save(existing));
                })
                .orElseGet(() -> createLead(leadDTO));
    }

    @Transactional
    public LeadDTO updateLead(Long id, LeadDTO leadDTO) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lead not found with id: " + id));
        
        lead.setEmail(leadDTO.getEmail());
        lead.setInstitutionName(leadDTO.getInstitutionName());
        lead.setCity(leadDTO.getCity());
        lead.setPhone(leadDTO.getPhone());
        lead.setAddress(leadDTO.getAddress());
        lead.setLatitude(leadDTO.getLatitude());
        lead.setLongitude(leadDTO.getLongitude());
        lead.setWebsite(leadDTO.getWebsite());
        lead.setSource(leadDTO.getSource());

        return leadMapper.toDTO(leadRepository.save(lead));
    }

    @Transactional(readOnly = true)
    public LeadDTO getLeadById(Long id) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lead not found with id: " + id));
        return leadMapper.toDTO(lead);
    }

    @Transactional(readOnly = true)
    public String getAllEmailsAsTextFile() {
        List<String> emails = leadRepository.findAllEmails();
        return String.join(System.lineSeparator(), emails);
    }
}
