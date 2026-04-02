package com.majd.n8n.service;

import com.majd.n8n.dto.LeadDTO;
import com.majd.n8n.entity.Lead;
import com.majd.n8n.entity.LeadCategory;
import com.majd.n8n.entity.LeadEmail;
import com.majd.n8n.mapper.LeadMapper;
import com.majd.n8n.repository.CategoryRepository;
import com.majd.n8n.repository.LeadCategoryRepository;
import com.majd.n8n.repository.LeadEmailRepository;
import com.majd.n8n.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository leadRepository;
    private final LeadEmailRepository leadEmailRepository;
    private final LeadCategoryRepository leadCategoryRepository;
    private final CategoryRepository categoryRepository;
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
        return leads.map(this::toLeadDTO);
    }

    @Transactional
    public LeadDTO createLead(LeadDTO leadDTO) {
        Lead lead = leadMapper.toEntity(leadDTO);
        List<String> normalizedEmails = normalizeIncomingEmails(leadDTO);
        if (normalizedEmails.isEmpty()) {
            throw new RuntimeException("At least one email is required");
        }

        lead.setEmail(normalizedEmails.get(0));
        Lead savedLead = leadRepository.save(lead);

        linkLeadCategories(savedLead, leadDTO.getCategoryIds());
        mergeEmailsIntoLead(savedLead, normalizedEmails, false);
        campaignService.syncLeadAcrossAllCampaigns(savedLead);

        Lead reloadedLead = leadRepository.findById(savedLead.getId())
                .orElseThrow(() -> new RuntimeException("Lead not found with id: " + savedLead.getId()));
        return toLeadDTO(reloadedLead);
    }

    @Transactional
    public LeadDTO createOrSkipLead(LeadDTO leadDTO) {
        return findExistingLead(leadDTO)
                .map(existing -> {
                    applyLeadFields(existing, leadDTO);
                    Lead savedLead = leadRepository.save(existing);

                    linkLeadCategories(savedLead, leadDTO.getCategoryIds());
                    List<LeadEmail> newLeadEmails = mergeEmailsIntoLead(savedLead, normalizeIncomingEmails(leadDTO), true);
                    campaignService.syncLeadAcrossAllCampaigns(savedLead);
                    newLeadEmails.forEach(campaignService::syncLeadEmailAcrossAllCampaigns);

                    Lead reloadedLead = leadRepository.findById(savedLead.getId())
                            .orElseThrow(() -> new RuntimeException("Lead not found with id: " + savedLead.getId()));
                    return toLeadDTO(reloadedLead);
                })
                .orElseGet(() -> createLead(leadDTO));
    }

    @Transactional
    public LeadDTO updateLead(Long id, LeadDTO leadDTO) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lead not found with id: " + id));

        applyLeadFields(lead, leadDTO);
        Lead savedLead = leadRepository.save(lead);

        linkLeadCategories(savedLead, leadDTO.getCategoryIds());
        List<LeadEmail> newLeadEmails = mergeEmailsIntoLead(savedLead, normalizeIncomingEmails(leadDTO), true);
        campaignService.syncLeadAcrossAllCampaigns(savedLead);
        newLeadEmails.forEach(campaignService::syncLeadEmailAcrossAllCampaigns);

        Lead reloadedLead = leadRepository.findById(savedLead.getId())
                .orElseThrow(() -> new RuntimeException("Lead not found with id: " + savedLead.getId()));
        return toLeadDTO(reloadedLead);
    }

    @Transactional(readOnly = true)
    public LeadDTO getLeadById(Long id) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Lead not found with id: " + id));
        return toLeadDTO(lead);
    }

    @Transactional(readOnly = true)
    public String getAllEmailsAsTextFile() {
        return leadEmailRepository.findAll().stream()
                .map(LeadEmail::getEmail)
                .filter(this::hasText)
                .map(email -> email.trim().toLowerCase())
                .collect(Collectors.toCollection(TreeSet::new))
                .stream()
                .collect(Collectors.joining(System.lineSeparator()));
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

        for (String email : normalizeIncomingEmails(leadDTO)) {
            Optional<LeadEmail> byEmail = leadEmailRepository.findByEmail(email);
            if (byEmail.isPresent()) {
                return Optional.of(byEmail.get().getLead());
            }
        }

        return Optional.empty();
    }

    private List<LeadEmail> mergeEmailsIntoLead(Lead lead, List<String> incomingEmails, boolean keepExistingPrimary) {
        List<LeadEmail> existingEmails = leadEmailRepository.findByLeadId(lead.getId());
        Map<String, LeadEmail> byEmail = new HashMap<>();
        for (LeadEmail leadEmail : existingEmails) {
            byEmail.put(leadEmail.getEmail().trim().toLowerCase(), leadEmail);
        }

        List<LeadEmail> newLeadEmails = new ArrayList<>();
        for (String incomingEmail : incomingEmails) {
            String key = incomingEmail.trim().toLowerCase();
            if (byEmail.containsKey(key)) {
                continue;
            }
            LeadEmail leadEmail = leadEmailRepository.save(LeadEmail.builder()
                    .lead(lead)
                    .email(key)
                    .isPrimary(false)
                    .build());
            byEmail.put(key, leadEmail);
            newLeadEmails.add(leadEmail);
            existingEmails.add(leadEmail);
        }

        Optional<LeadEmail> existingPrimary = existingEmails.stream().filter(LeadEmail::isPrimary).findFirst();
        LeadEmail targetPrimary = null;

        if (keepExistingPrimary && existingPrimary.isPresent()) {
            targetPrimary = existingPrimary.get();
        } else if (!incomingEmails.isEmpty()) {
            targetPrimary = byEmail.get(incomingEmails.get(0));
        } else if (existingPrimary.isPresent()) {
            targetPrimary = existingPrimary.get();
        } else if (!existingEmails.isEmpty()) {
            targetPrimary = existingEmails.stream()
                    .sorted(Comparator.comparing(LeadEmail::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                    .findFirst()
                    .orElse(null);
        }

        if (targetPrimary == null) {
            throw new RuntimeException("At least one email is required");
        }

        for (LeadEmail leadEmail : existingEmails) {
            boolean shouldBePrimary = leadEmail.getId().equals(targetPrimary.getId());
            if (leadEmail.isPrimary() != shouldBePrimary) {
                leadEmail.setPrimary(shouldBePrimary);
                leadEmailRepository.save(leadEmail);
            }
        }

        lead.setEmail(targetPrimary.getEmail());
        leadRepository.save(lead);
        return newLeadEmails;
    }

    private List<String> normalizeIncomingEmails(LeadDTO incoming) {
        LinkedHashSet<String> emails = new LinkedHashSet<>();
        if (incoming == null) {
            return List.of();
        }

        addEmail(emails, incoming.getPrimaryEmail());
        addEmail(emails, incoming.getEmail());

        if (incoming.getEmails() != null) {
            for (String email : incoming.getEmails()) {
                addEmail(emails, email);
            }
        }

        return List.copyOf(emails);
    }

    private void applyLeadFields(Lead lead, LeadDTO leadDTO) {
        lead.setInstitutionName(leadDTO.getInstitutionName());
        lead.setCity(leadDTO.getCity());
        lead.setPhone(leadDTO.getPhone());
        lead.setAddress(leadDTO.getAddress());
        lead.setLatitude(leadDTO.getLatitude());
        lead.setLongitude(leadDTO.getLongitude());
        lead.setWebsite(leadDTO.getWebsite());
        lead.setSource(leadDTO.getSource());
    }

    private void linkLeadCategories(Lead lead, List<Long> categoryIds) {
        if (lead == null || lead.getId() == null || categoryIds == null) {
            return;
        }

        Set<Long> uniqueCategoryIds = new LinkedHashSet<>(categoryIds);
        for (Long categoryId : uniqueCategoryIds) {
            if (categoryId == null || leadCategoryRepository.existsByLeadIdAndCategoryId(lead.getId(), categoryId)) {
                continue;
            }

            categoryRepository.findByIdAndActiveTrue(categoryId).ifPresent(category ->
                    leadCategoryRepository.save(LeadCategory.builder()
                            .lead(lead)
                            .category(category)
                            .build())
            );
        }
    }

    private LeadDTO toLeadDTO(Lead lead) {
        LeadDTO dto = leadMapper.toDTO(lead);
        if (lead == null || lead.getId() == null) {
            dto.setCategoryIds(List.of());
            dto.setCategoryNames(List.of());
            return dto;
        }

        List<LeadCategory> leadCategories = leadCategoryRepository.findByLeadId(lead.getId());
        List<Long> categoryIds = leadCategories.stream()
                .filter(leadCategory -> leadCategory.getCategory() != null && leadCategory.getCategory().isActive())
                .map(leadCategory -> leadCategory.getCategory().getId())
                .collect(Collectors.toList());
        List<String> categoryNames = leadCategories.stream()
                .filter(leadCategory -> leadCategory.getCategory() != null && leadCategory.getCategory().isActive())
                .map(leadCategory -> leadCategory.getCategory().getName())
                .collect(Collectors.toList());

        dto.setCategoryIds(categoryIds);
        dto.setCategoryNames(categoryNames);
        return dto;
    }

    private void addEmail(Set<String> set, String email) {
        if (hasText(email)) {
            set.add(email.trim().toLowerCase());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
