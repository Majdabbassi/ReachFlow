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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository leadRepository;
    private final LeadMapper leadMapper;
    private final CampaignService campaignService;

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
        EmailMerge mergedEmails = mergeEmails(null, leadDTO);
        lead.setEmail(mergedEmails.primaryEmail);
        lead.setAllEmails(mergedEmails.allEmailsAsText);
        Lead savedLead = leadRepository.save(lead);
        campaignService.syncLeadAcrossAllCampaigns(savedLead);
        return leadMapper.toDTO(savedLead);
    }

    @Transactional
    public LeadDTO createOrSkipLead(LeadDTO leadDTO) {
        return findExistingLead(leadDTO)
                .map(existing -> {
                    EmailMerge mergedEmails = mergeEmails(existing, leadDTO);
                    existing.setEmail(mergedEmails.primaryEmail);
                    existing.setAllEmails(mergedEmails.allEmailsAsText);
                    existing.setInstitutionName(leadDTO.getInstitutionName());
                    existing.setCity(leadDTO.getCity());
                    existing.setPhone(leadDTO.getPhone());
                    existing.setAddress(leadDTO.getAddress());
                    existing.setLatitude(leadDTO.getLatitude());
                    existing.setLongitude(leadDTO.getLongitude());
                    existing.setWebsite(leadDTO.getWebsite());
                    existing.setSource(leadDTO.getSource());
                    Lead savedLead = leadRepository.save(existing);
                    campaignService.syncLeadAcrossAllCampaigns(savedLead);
                    return leadMapper.toDTO(savedLead);
                })
                .orElseGet(() -> createLead(leadDTO));
    }

    @Transactional
    public LeadDTO updateLead(Long id, LeadDTO leadDTO) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lead not found with id: " + id));

        EmailMerge mergedEmails = mergeEmails(lead, leadDTO);
        
        lead.setEmail(mergedEmails.primaryEmail);
        lead.setAllEmails(mergedEmails.allEmailsAsText);
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
        Set<String> emails = new TreeSet<>();
        for (Lead lead : leadRepository.findAll()) {
            addEmail(emails, lead.getEmail());
            for (String email : splitEmails(lead.getAllEmails())) {
                addEmail(emails, email);
            }
        }
        return String.join(System.lineSeparator(), emails);
    }

    private Optional<Lead> findExistingLead(LeadDTO leadDTO) {
        if (hasText(leadDTO.getWebsite())) {
            Optional<Lead> byWebsite = leadRepository.findFirstByWebsiteIgnoreCase(leadDTO.getWebsite().trim());
            if (byWebsite.isPresent()) {
                return byWebsite;
            }
        }

        if (hasText(leadDTO.getInstitutionName()) && hasText(leadDTO.getCity())) {
            Optional<Lead> byInstitution = leadRepository.findFirstByInstitutionNameIgnoreCaseAndCityIgnoreCase(
                    leadDTO.getInstitutionName().trim(),
                    leadDTO.getCity().trim()
            );
            if (byInstitution.isPresent()) {
                return byInstitution;
            }
        }

        if (hasText(leadDTO.getEmail())) {
            return leadRepository.findByEmail(leadDTO.getEmail().trim().toLowerCase());
        }

        return Optional.empty();
    }

    private EmailMerge mergeEmails(Lead existing, LeadDTO incoming) {
        LinkedHashSet<String> all = new LinkedHashSet<>();

        if (existing != null) {
            addEmail(all, existing.getEmail());
            splitEmails(existing.getAllEmails()).forEach(email -> addEmail(all, email));
        }

        addEmail(all, incoming.getEmail());
        splitEmails(incoming.getAllEmails()).forEach(email -> addEmail(all, email));

        String primary = all.stream().findFirst().orElseThrow(() -> new RuntimeException("At least one email is required"));
        String allAsText = String.join(System.lineSeparator(), all);
        return new EmailMerge(primary, allAsText);
    }

    private List<String> splitEmails(String emailsText) {
        if (!hasText(emailsText)) {
            return List.of();
        }

        String[] parts = emailsText.split("[\\n,;]");
        List<String> emails = new ArrayList<>();
        for (String part : parts) {
            if (hasText(part)) {
                emails.add(part.trim().toLowerCase());
            }
        }
        return emails;
    }

    private void addEmail(Set<String> set, String email) {
        if (hasText(email)) {
            set.add(email.trim().toLowerCase());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private record EmailMerge(String primaryEmail, String allEmailsAsText) {
    }
}
