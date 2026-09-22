package com.majd.reachflow.service;

import com.majd.reachflow.dto.LeadDTO;
import com.majd.reachflow.entity.Lead;
import com.majd.reachflow.entity.LeadEmail;
import com.majd.reachflow.mapper.LeadMapper;
import com.majd.reachflow.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadServiceTest {

    @Mock
    private LeadRepository leadRepository;
    @Mock
    private LeadEmailRepository leadEmailRepository;
    @Mock
    private LeadCategoryRepository leadCategoryRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private CampaignSendRepository campaignSendRepository;
    @Mock
    private LeadMapper leadMapper;
    @Mock
    private CampaignExecutionService campaignExecutionService;
    @Mock
    private ScrapeProgressTracker scrapeProgressTracker;
    @Mock
    private GeocodingService geocodingService;

    private LeadService leadService;

    @BeforeEach
    void setUp() {
        leadService = new LeadService(
                leadRepository,
                leadEmailRepository,
                leadCategoryRepository,
                categoryRepository,
                campaignSendRepository,
                leadMapper,
                campaignExecutionService,
                scrapeProgressTracker,
                geocodingService
        );
    }

    private LeadDTO existingLeadDto() {
        return LeadDTO.builder()
                .website("https://example-school.de")
                .institutionName("Example School")
                .email("info@example-school.de")
                .primaryEmail("info@example-school.de")
                .build();
    }

    @Test
    void createOrSkipLead_updatesExistingLeadByWebsite_insteadOfCreatingNew() {
        Lead existing = Lead.builder()
                .id(1L)
                .email("old@example-school.de")
                .website("https://example-school.de")
                .institutionName("Old Name")
                .build();

        when(leadRepository.findFirstByWebsiteIgnoreCase("https://example-school.de"))
                .thenReturn(Optional.of(existing));
        when(leadRepository.save(any(Lead.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(leadRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(leadMapper.toDTO(any(Lead.class))).thenReturn(new LeadDTO());

        LeadEmail primaryEmail = LeadEmail.builder()
                .id(5L)
                .lead(existing)
                .email("info@example-school.de")
                .isPrimary(true)
                .build();
        when(leadEmailRepository.findByLeadId(1L)).thenReturn(List.of(primaryEmail));
        when(leadCategoryRepository.findByLeadId(1L)).thenReturn(List.of());

        LeadDTO result = leadService.createOrSkipLead(existingLeadDto());

        assertNotNull(result);
        assertEquals("Example School", existing.getInstitutionName());
        assertEquals("https://example-school.de", existing.getWebsite());
    }

    @Test
    void createOrSkipLead_doesNotCreateNewLeadWhenDuplicateFound() {
        Lead existing = Lead.builder()
                .id(1L)
                .email("info@example-school.de")
                .website("https://example-school.de")
                .institutionName("Example School")
                .build();

        when(leadRepository.findFirstByWebsiteIgnoreCase("https://example-school.de"))
                .thenReturn(Optional.of(existing));
        when(leadRepository.save(any(Lead.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(leadRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(leadMapper.toDTO(any(Lead.class))).thenReturn(new LeadDTO());

        LeadEmail primaryEmail = LeadEmail.builder()
                .id(5L)
                .lead(existing)
                .email("info@example-school.de")
                .isPrimary(true)
                .build();
        when(leadEmailRepository.findByLeadId(1L)).thenReturn(List.of(primaryEmail));
        when(leadCategoryRepository.findByLeadId(1L)).thenReturn(List.of());

        leadService.createOrSkipLead(existingLeadDto());

        verify(leadMapper, never()).toEntity(any(LeadDTO.class));
        verify(campaignExecutionService).syncLeadAcrossAllCampaigns(existing);
    }

    @Test
    void createOrSkipLead_createsNewLead_whenNoMatchFound() {
        when(leadRepository.findFirstByWebsiteIgnoreCase("https://example-school.de"))
                .thenReturn(Optional.empty());
        when(leadEmailRepository.findByEmail("info@example-school.de")).thenReturn(Optional.empty());
        when(leadMapper.toEntity(any(LeadDTO.class))).thenReturn(Lead.builder().build());
        when(leadRepository.save(any(Lead.class))).thenAnswer(invocation -> {
            Lead lead = invocation.getArgument(0);
            if (lead.getId() == null) {
                lead.setId(10L);
            }
            return lead;
        });
        when(leadRepository.findById(10L)).thenReturn(Optional.of(Lead.builder()
                .id(10L)
                .email("info@example-school.de")
                .build()));
        when(leadEmailRepository.findByLeadId(10L)).thenReturn(new ArrayList<>());
        when(leadEmailRepository.save(any(LeadEmail.class))).thenAnswer(invocation -> {
            LeadEmail email = invocation.getArgument(0);
            if (email.getId() == null) {
                email.setId(100L);
            }
            return email;
        });
        when(leadCategoryRepository.findByLeadId(10L)).thenReturn(List.of());
        when(leadMapper.toDTO(any(Lead.class))).thenReturn(new LeadDTO());

        LeadDTO result = leadService.createOrSkipLead(existingLeadDto());

        assertNotNull(result);
        verify(leadMapper).toEntity(any(LeadDTO.class));
        verify(campaignExecutionService).syncLeadAcrossAllCampaigns(any(Lead.class));

        ArgumentCaptor<Lead> savedLeadCaptor = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository, atLeastOnce()).save(savedLeadCaptor.capture());
        Lead firstSaved = savedLeadCaptor.getAllValues().get(0);
        assertEquals("info@example-school.de", firstSaved.getEmail());
        assertEquals(10L, firstSaved.getId());
    }
}