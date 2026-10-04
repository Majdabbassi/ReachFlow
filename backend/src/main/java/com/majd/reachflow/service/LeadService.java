package com.majd.reachflow.service;

import com.majd.reachflow.dto.*;
import com.majd.reachflow.entity.*;
import com.majd.reachflow.exception.BusinessException;
import com.majd.reachflow.mapper.LeadMapper;
import com.majd.reachflow.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeadService {

    private final LeadRepository leadRepository;
    private final LeadEmailRepository leadEmailRepository;
    private final LeadCategoryRepository leadCategoryRepository;
    private final CategoryRepository categoryRepository;
    private final CampaignSendRepository campaignSendRepository;
    private final LeadMapper leadMapper;
    private final CampaignExecutionService campaignExecutionService;
    private final ScrapeProgressTracker scrapeProgressTracker;
    private final GeocodingService geocodingService;

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
            throw new BusinessException("At least one email is required", HttpStatus.BAD_REQUEST);
        }

        lead.setEmail(normalizedEmails.get(0));
        Lead savedLead = leadRepository.save(lead);

        linkLeadCategories(savedLead, leadDTO.getCategoryIds(), false);
        mergeEmailsIntoLead(savedLead, normalizedEmails, false);
        campaignExecutionService.syncLeadAcrossAllCampaigns(savedLead);

        Lead reloadedLead = leadRepository.findById(savedLead.getId())
            .orElseThrow(() -> new BusinessException("Lead not found with id: " + savedLead.getId(), HttpStatus.NOT_FOUND));
        return toLeadDTO(reloadedLead);
    }

    @Transactional
    public LeadDTO createOrSkipLead(LeadDTO leadDTO) {
        return findExistingLead(leadDTO)
                .map(existing -> {
                    applyLeadFields(existing, leadDTO);
                    Lead savedLead = leadRepository.save(existing);

                    linkLeadCategories(savedLead, leadDTO.getCategoryIds(), false);
                        mergeEmailsIntoLead(savedLead, normalizeIncomingEmails(leadDTO), true);
                    campaignExecutionService.syncLeadAcrossAllCampaigns(savedLead);

                    Lead reloadedLead = leadRepository.findById(savedLead.getId())
                            .orElseThrow(() -> new BusinessException("Lead not found with id: " + savedLead.getId(), HttpStatus.NOT_FOUND));
                    return toLeadDTO(reloadedLead);
                })
                .orElseGet(() -> createLead(leadDTO));
    }

    @Transactional
    public LeadDTO updateLead(Long id, LeadDTO leadDTO) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Lead not found with id: " + id, HttpStatus.NOT_FOUND));

        applyLeadFields(lead, leadDTO);
        Lead savedLead = leadRepository.save(lead);

        linkLeadCategories(savedLead, leadDTO.getCategoryIds(), true);
        mergeEmailsIntoLead(savedLead, normalizeIncomingEmails(leadDTO), true);
        campaignExecutionService.syncLeadAcrossAllCampaigns(savedLead);

        Lead reloadedLead = leadRepository.findById(savedLead.getId())
                .orElseThrow(() -> new BusinessException("Lead not found with id: " + savedLead.getId(), HttpStatus.NOT_FOUND));
        return toLeadDTO(reloadedLead);
    }

    @Transactional
    public void deleteLead(Long id) {
        if (!leadRepository.existsById(id)) {
            throw new BusinessException("Lead not found with id: " + id, HttpStatus.NOT_FOUND);
        }

        List<Long> leadEmailIds = leadEmailRepository.findByLeadId(id).stream()
                .map(LeadEmail::getId)
                .toList();

        if (!leadEmailIds.isEmpty()) {
            campaignSendRepository.deleteByLeadEmailIdIn(leadEmailIds);
            leadEmailRepository.deleteAllByIdInBatch(leadEmailIds);
        }

        List<LeadCategory> leadCategories = leadCategoryRepository.findByLeadId(id);
        if (!leadCategories.isEmpty()) {
            leadCategoryRepository.deleteAllInBatch(leadCategories);
        }

        leadRepository.deleteById(id);
    }

    @Transactional
    public int bulkDeleteLeads(List<Long> leadIds) {
        if (leadIds == null || leadIds.isEmpty()) {
            return 0;
        }

        List<Long> uniqueIds = leadIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        int deleted = 0;
        for (Long id : uniqueIds) {
            if (leadRepository.existsById(id)) {
                deleteLead(id);
                deleted++;
            }
        }
        return deleted;
    }

    @Transactional(readOnly = true)
    public LeadDTO getLeadById(Long id) {
        Lead lead = leadRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Lead not found with id: " + id, HttpStatus.NOT_FOUND));
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

    @Transactional(readOnly = true)
    public String getAllLeadsAsCsv() {
        List<Lead> leads = leadRepository.findAll();
        String header = "id,institutionName,city,phone,address,website,email,allEmails,categories,source,createdAt";
        if (leads.isEmpty()) {
            return header;
        }

        List<Long> leadIds = leads.stream().map(Lead::getId).toList();
        Map<Long, List<LeadEmail>> emailsByLeadId = leadEmailRepository.findByLeadIdIn(leadIds).stream()
                .collect(Collectors.groupingBy(leadEmail -> leadEmail.getLead().getId()));
        Map<Long, List<String>> categoryNamesByLeadId = leadCategoryRepository.findByLeadIdIn(leadIds).stream()
                .filter(leadCategory -> leadCategory.getCategory() != null && leadCategory.getCategory().isActive())
                .collect(Collectors.groupingBy(
                        leadCategory -> leadCategory.getLead().getId(),
                        Collectors.mapping(leadCategory -> leadCategory.getCategory().getName(), Collectors.toList())
                ));

        List<String> lines = new ArrayList<>();
        lines.add(header);

        for (Lead lead : leads) {
            List<String> allEmails = emailsByLeadId.getOrDefault(lead.getId(), List.of()).stream()
                    .map(LeadEmail::getEmail)
                    .filter(this::hasText)
                    .map(value -> value.trim().toLowerCase())
                    .distinct()
                    .toList();
            List<String> categories = categoryNamesByLeadId.getOrDefault(lead.getId(), List.of()).stream()
                    .filter(this::hasText)
                    .distinct()
                    .toList();

            lines.add(String.join(",",
                    String.valueOf(lead.getId()),
                    csvCell(lead.getInstitutionName()),
                    csvCell(lead.getCity()),
                    csvCell(lead.getPhone()),
                    csvCell(lead.getAddress()),
                    csvCell(lead.getWebsite()),
                    csvCell(lead.getEmail()),
                    csvCell(String.join("|", allEmails)),
                    csvCell(String.join("|", categories)),
                    csvCell(lead.getSource()),
                    csvCell(lead.getCreatedAt() == null ? "" : lead.getCreatedAt().toString())
            ));
        }

        return String.join(System.lineSeparator(), lines);
    }

    @Transactional
    public BulkImportResultDTO importFromCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("CSV file is empty", HttpStatus.BAD_REQUEST);
        }

        int imported = 0;
        int skipped = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (!hasText(headerLine)) {
                throw new BusinessException("CSV header row is missing", HttpStatus.BAD_REQUEST);
            }

            Map<String, Integer> headerIndex = buildHeaderIndex(parseCsvLine(headerLine));
            List<String> requiredHeaders = List.of("institutionname", "city", "phone", "address", "website", "email", "categories");
            for (String required : requiredHeaders) {
                if (!headerIndex.containsKey(required)) {
                    throw new BusinessException("Missing required CSV header: " + required, HttpStatus.BAD_REQUEST);
                }
            }

            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (!hasText(line)) {
                    continue;
                }

                try {
                    List<String> values = parseCsvLine(line);
                    String emailCell = getCsvValue(values, headerIndex, "email");
                    List<String> emails = splitPipeValues(emailCell);
                    if (emails.isEmpty()) {
                        throw new BusinessException("email column is required", HttpStatus.BAD_REQUEST);
                    }

                    LeadDTO leadDTO = LeadDTO.builder()
                            .institutionName(getCsvValue(values, headerIndex, "institutionname"))
                            .city(getCsvValue(values, headerIndex, "city"))
                            .phone(getCsvValue(values, headerIndex, "phone"))
                            .address(getCsvValue(values, headerIndex, "address"))
                            .website(getCsvValue(values, headerIndex, "website"))
                            .email(emails.get(0))
                            .primaryEmail(emails.get(0))
                            .emails(emails)
                            .source("CSV Import")
                            .build();

                    List<String> categoryNames = splitPipeValues(getCsvValue(values, headerIndex, "categories"));
                    List<Long> categoryIds = new ArrayList<>();
                    List<String> resolvedCategoryNames = new ArrayList<>();
                    for (String categoryName : categoryNames) {
                        Optional<Category> category = categoryRepository.findByNameIgnoreCaseAndActiveTrue(categoryName);
                        if (category.isEmpty()) {
                            continue;
                        }
                        categoryIds.add(category.get().getId());
                        resolvedCategoryNames.add(category.get().getName());
                    }
                    leadDTO.setCategoryIds(categoryIds);
                    leadDTO.setCategoryNames(resolvedCategoryNames);

                    boolean existedBefore = findExistingLead(leadDTO).isPresent();
                    createOrSkipLead(leadDTO);
                    if (existedBefore) {
                        skipped++;
                    } else {
                        imported++;
                    }
                } catch (Exception ex) {
                    failed++;
                    errors.add("Row " + rowNumber + ": " + ex.getMessage());
                }
            }
        } catch (IOException ex) {
            throw new BusinessException("Failed to read CSV file", HttpStatus.BAD_REQUEST, ex);
        }

        return BulkImportResultDTO.builder()
                .imported(imported)
                .skipped(skipped)
                .failed(failed)
                .errors(errors)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<EmailAuditItemDTO> getEmailAudit(String mode, Pageable pageable) {
        String normalizedMode = mode == null ? "invalid" : mode.trim().toLowerCase();

        if (!"invalid".equals(normalizedMode) && !"duplicate".equals(normalizedMode)) {
            throw new BusinessException("Unsupported audit mode: " + mode, HttpStatus.BAD_REQUEST);
        }

        Page<EmailAuditProjection> page = "duplicate".equals(normalizedMode)
                ? leadEmailRepository.findDuplicateEmails(pageable)
                : leadEmailRepository.findInvalidEmails(pageable);

        final String issueType = normalizedMode.toUpperCase();
        return page.map(item -> EmailAuditItemDTO.builder()
                .id(item.getId())
                .leadId(item.getLeadId())
                .institutionName(item.getInstitutionName())
                .email(item.getEmail())
                .primary(Boolean.TRUE.equals(item.getIsPrimary()))
                .issueType(issueType)
                .duplicateCount(item.getDuplicateCount() == null ? 0L : item.getDuplicateCount())
                .build());
    }

    @Transactional
    public DeleteLeadEmailsResponseDTO deleteLeadEmails(List<Long> emailIds) {
        if (emailIds == null || emailIds.isEmpty()) {
            return DeleteLeadEmailsResponseDTO.builder()
                    .deletedCount(0)
                    .skippedCount(0)
                    .skippedEmailIds(List.of())
                    .build();
        }

        List<Long> uniqueIds = emailIds.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (uniqueIds.isEmpty()) {
            return DeleteLeadEmailsResponseDTO.builder()
                    .deletedCount(0)
                    .skippedCount(0)
                    .skippedEmailIds(List.of())
                    .build();
        }

        List<LeadEmail> selectedEmails = leadEmailRepository.findAllById(uniqueIds);
        if (selectedEmails.isEmpty()) {
            return DeleteLeadEmailsResponseDTO.builder()
                    .deletedCount(0)
                    .skippedCount(0)
                    .skippedEmailIds(List.of())
                    .build();
        }

        Map<Long, List<LeadEmail>> byLeadId = selectedEmails.stream()
                .filter(item -> item.getLead() != null && item.getLead().getId() != null)
                .collect(Collectors.groupingBy(item -> item.getLead().getId()));

        List<Long> deletableIds = new ArrayList<>();
        Set<Long> forceDeleteLeadIds = new LinkedHashSet<>();

        for (Map.Entry<Long, List<LeadEmail>> entry : byLeadId.entrySet()) {
            Long leadId = entry.getKey();
            int selectedForLead = entry.getValue().size();
            long totalForLead = leadEmailRepository.countByLeadId(leadId);

            if (totalForLead - selectedForLead <= 0) {
            forceDeleteLeadIds.add(leadId);
                continue;
            }

            deletableIds.addAll(entry.getValue().stream().map(LeadEmail::getId).toList());
        }

        int deletedViaForcedLeadDelete = 0;
        for (Long leadId : forceDeleteLeadIds) {
            deletedViaForcedLeadDelete += (int) leadEmailRepository.countByLeadId(leadId);
            deleteLead(leadId);
        }

        if (!deletableIds.isEmpty()) {
            campaignSendRepository.deleteByLeadEmailIdIn(deletableIds);
            leadEmailRepository.deleteAllByIdInBatch(deletableIds);
        }

        Set<Long> affectedLeadIds = byLeadId.keySet().stream()
            .filter(leadId -> !forceDeleteLeadIds.contains(leadId))
            .collect(Collectors.toSet());
        refreshLeadPrimaryEmail(affectedLeadIds);

        return DeleteLeadEmailsResponseDTO.builder()
            .deletedCount(deletableIds.size() + deletedViaForcedLeadDelete)
            .skippedCount(0)
            .skippedEmailIds(List.of())
                .build();
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
            throw new BusinessException("At least one email is required", HttpStatus.BAD_REQUEST);
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
        lead.setWebsite(leadDTO.getWebsite());
        lead.setSource(leadDTO.getSource());
        if (leadDTO.getLatitude() != null) {
            lead.setLatitude(leadDTO.getLatitude());
        }
        if (leadDTO.getLongitude() != null) {
            lead.setLongitude(leadDTO.getLongitude());
        }
        geocodeIfMissing(lead);
    }

    private void geocodeIfMissing(Lead lead) {
        if (lead.getLatitude() != null && lead.getLongitude() != null) {
            return;
        }
        try {
            double[] coords = geocodingService.geocode(lead.getCity(), lead.getAddress());
            if (coords != null) {
                lead.setLatitude(coords[0]);
                lead.setLongitude(coords[1]);
            }
        } catch (Exception e) {
            log.debug("Geocoding skipped for lead {}: {}", lead.getId(), e.getMessage());
        }
    }

    @Transactional
    public int geocodeMissingLeads() {
        List<Lead> leads = leadRepository.findAll();
        log.info("Geocoding backfill: found {} leads total", leads.size());
        int geocoded = 0;
        int attempted = 0;
        for (Lead lead : leads) {
            if (lead.getLatitude() != null && lead.getLongitude() != null) {
                continue;
            }
            if (hasText(lead.getCity()) || hasText(lead.getAddress())) {
                attempted++;
                try {
                    double[] coords = geocodingService.geocode(lead.getCity(), lead.getAddress());
                    if (coords != null) {
                        lead.setLatitude(coords[0]);
                        lead.setLongitude(coords[1]);
                        leadRepository.save(lead);
                        geocoded++;
                    } else {
                        log.info("Geocoding returned no coords for lead {} '{}' '{}'", lead.getId(), lead.getCity(), lead.getAddress());
                    }
                } catch (Exception e) {
                    log.warn("Geocoding failed for lead {}: {}", lead.getId(), e.getMessage());
                }
            }
        }
        log.info("Geocoding backfill done: attempted={}, geocoded={}", attempted, geocoded);
        return geocoded;
    }

    private void linkLeadCategories(Lead lead, List<Long> categoryIds, boolean replaceMissing) {
        if (lead == null || lead.getId() == null || categoryIds == null) {
            return;
        }

        Set<Long> uniqueCategoryIds = new LinkedHashSet<>(categoryIds);
        List<LeadCategory> existingCategories = leadCategoryRepository.findByLeadId(lead.getId());

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

        if (!replaceMissing) {
            return;
        }

        for (LeadCategory existingCategory : existingCategories) {
            if (existingCategory.getCategory() == null || uniqueCategoryIds.contains(existingCategory.getCategory().getId())) {
                continue;
            }
            leadCategoryRepository.delete(existingCategory);
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

    private Map<String, Integer> buildHeaderIndex(List<String> headers) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String normalized = headers.get(i) == null ? "" : headers.get(i).trim().toLowerCase();
            if (!normalized.isEmpty()) {
                index.put(normalized, i);
            }
        }
        return index;
    }

    private String getCsvValue(List<String> values, Map<String, Integer> headerIndex, String key) {
        Integer index = headerIndex.get(key);
        if (index == null || index < 0 || index >= values.size()) {
            return "";
        }
        String value = values.get(index);
        return value == null ? "" : value.trim();
    }

    private List<String> splitPipeValues(String value) {
        if (!hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split("\\|"))
                .map(String::trim)
                .filter(this::hasText)
                .map(v -> v.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                    continue;
                }
                inQuotes = !inQuotes;
                continue;
            }

            if (ch == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
                continue;
            }

            current.append(ch);
        }

        values.add(current.toString());
        return values;
    }

    private String csvCell(String value) {
        String safe = value == null ? "" : value;
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    private void refreshLeadPrimaryEmail(Set<Long> leadIds) {
        if (leadIds == null || leadIds.isEmpty()) {
            return;
        }

        for (Long leadId : leadIds) {
            if (leadId == null) {
                continue;
            }

            Lead lead = leadRepository.findById(leadId).orElse(null);
            if (lead == null) {
                continue;
            }

            List<LeadEmail> emails = leadEmailRepository.findByLeadId(leadId);
            if (emails.isEmpty()) {
                continue;
            }

            LeadEmail primary = emails.stream().filter(LeadEmail::isPrimary).findFirst().orElse(null);
            if (primary == null) {
                primary = emails.get(0);
                for (LeadEmail email : emails) {
                    email.setPrimary(email.getId().equals(primary.getId()));
                }
                leadEmailRepository.saveAll(emails);
            }

            lead.setEmail(primary.getEmail());
            leadRepository.save(lead);
        }
    }

    public String collectLeadsAsync(CollectRequestDTO request) {
        String jobId = scrapeProgressTracker.newJobId();
        String keywordsCsv = request.getKeywords() != null ? String.join(", ", request.getKeywords()) : "";
        String citiesCsv = request.getCities() != null ? String.join(", ", request.getCities()) : "";
        
        scrapeProgressTracker.registerJob(jobId, keywordsCsv, citiesCsv);
        // Through the proxy: a plain this.call would skip @Async and block this request until the
        // whole scrape is done, so the UI would never get a job id to poll.
        self.collectLeadsAsyncInternal(request, jobId);
        
        return jobId;
    }

    @org.springframework.beans.factory.annotation.Value("${demo.webhook-url:}")
    private String demoWebhookUrl;

    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private LeadService self;

    @Async
    @SuppressWarnings("unchecked")
    public void collectLeadsAsyncInternal(CollectRequestDTO request, String jobId) {
        log.info("Starting async lead collection job {} for keywords: {} in cities: {}", jobId, request.getKeywords(), request.getCities());

        int maxResults = request.getMaxResults() > 0 ? request.getMaxResults() : 50;
        scrapeProgressTracker.setRunning(jobId, maxResults);

        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(10000);
            factory.setReadTimeout(600000); // 10 minutes
            RestTemplate restTemplate = new RestTemplate(factory);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> payload = Map.of(
                "cities", request.getCities(),
                "keywords", request.getKeywords(),
                "maxResults", request.getMaxResults()
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            // Demo mode swaps the user's n8n webhook for the bundled mock scraper.
            String webhookUrl = (demoWebhookUrl != null && !demoWebhookUrl.isBlank())
                    ? demoWebhookUrl : request.getWebhookUrl();

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                webhookUrl,
                HttpMethod.POST,
                entity,
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );

            int importedCount = 0;
            int rawResultsCount = 0;
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody().get("results");
                if (results != null) {
                    rawResultsCount = results.size();
                    log.info("Scraper returned {} results. Importing...", rawResultsCount);
                    scrapeProgressTracker.setScraperReturned(jobId, rawResultsCount);

                    int i = 0;
                    for (Map<String, Object> item : results) {
                        try {
                            LeadDTO leadDTO = LeadDTO.builder()
                                .institutionName((String) item.get("title"))
                                .city((String) item.get("city"))
                                .phone((String) item.get("phone"))
                                .address((String) item.get("address"))
                                .website((String) item.get("website"))
                                .email((String) item.get("email"))
                                .primaryEmail((String) item.get("email"))
                                .emails((List<String>) item.get("allEmails"))
                                .source("Scraper Automation")
                                // Without a category a lead can never match a client, so never be emailed.
                                .categoryIds(request.getCategoryIds())
                                .build();

                            if (leadDTO.getLatitude() == null && leadDTO.getLongitude() == null
                                    && hasText(leadDTO.getCity())) {
                                try {
                                    double[] coords = geocodingService.geocode(leadDTO.getCity(), leadDTO.getAddress());
                                    if (coords != null) {
                                        leadDTO.setLatitude(coords[0]);
                                        leadDTO.setLongitude(coords[1]);
                                    }
                                } catch (Exception ge) {
                                    log.debug("Scraper geocoding skipped for {}: {}", leadDTO.getCity(), ge.getMessage());
                                }
                            }

                            // Map categories if needed, for now we leave them empty or use a default
                            // Via the proxy: this runs on an async thread with no session, so the call
                            // needs its own @Transactional (one transaction per lead).
                            self.createOrSkipLead(leadDTO);
                            importedCount++;
                            i++;
                            if (i % 5 == 0 || i == rawResultsCount) {
                                scrapeProgressTracker.setProgress(jobId, i, rawResultsCount);
                            }
                        } catch (Exception e) {
                            log.error("Failed to import lead: {}", item.get("title"), e);
                        }
                    }
                    log.info("Finished importing leads from scraper. Imported {}.", importedCount);
                }
            }
            scrapeProgressTracker.setCompleted(jobId, importedCount);
        } catch (Exception e) {
            log.error("Error during async lead collection for job {}", jobId, e);
            scrapeProgressTracker.setFailed(jobId, e.getMessage() == null ? "Unknown error" : e.getMessage());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public ScrapeProgressDTO getScrapeProgress(String jobId) {
        return scrapeProgressTracker.get(jobId);
    }
}
