package com.majd.reachflow.service;

import com.majd.reachflow.dto.GenerateSearchCombinationsRequestDTO;
import com.majd.reachflow.dto.GenerateSearchCombinationsResponseDTO;
import com.majd.reachflow.dto.LaunchSearchCombinationRequestDTO;
import com.majd.reachflow.dto.PlaceCityDTO;
import com.majd.reachflow.dto.PlaceCountryDTO;
import com.majd.reachflow.dto.PlaceDistrictDTO;
import com.majd.reachflow.dto.PlaceStateDTO;
import com.majd.reachflow.dto.SearchCombinationDTO;
import com.majd.reachflow.entity.Keyword;
import com.majd.reachflow.entity.PlaceCity;
import com.majd.reachflow.entity.PlaceCountry;
import com.majd.reachflow.entity.PlaceDistrict;
import com.majd.reachflow.entity.PlaceState;
import com.majd.reachflow.entity.SearchCombination;
import com.majd.reachflow.entity.enums.SearchCombinationStatus;
import com.majd.reachflow.exception.BusinessException;
import com.majd.reachflow.repository.KeywordRepository;
import com.majd.reachflow.repository.PlaceCityRepository;
import com.majd.reachflow.repository.PlaceCountryRepository;
import com.majd.reachflow.repository.PlaceDistrictRepository;
import com.majd.reachflow.repository.PlaceStateRepository;
import com.majd.reachflow.repository.SearchCombinationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchCombinationService {

    private static final int DEFAULT_MAX_RESULTS = 10;

    private final PlaceCountryRepository placeCountryRepository;
    private final PlaceStateRepository placeStateRepository;
    private final PlaceCityRepository placeCityRepository;
    private final PlaceDistrictRepository placeDistrictRepository;
    private final KeywordRepository keywordRepository;
    private final SearchCombinationRepository searchCombinationRepository;

    @Transactional
    public void seedGermanyHierarchy() {
        PlaceCountry germany = placeCountryRepository.findByCodeIgnoreCase("DE")
                .orElseGet(() -> placeCountryRepository.save(PlaceCountry.builder().name("Germany").code("DE").build()));

        for (Map.Entry<String, LinkedHashMap<String, List<String>>> stateEntry : germanySeed().entrySet()) {
            PlaceState state = placeStateRepository.findByCountryIdAndNameIgnoreCase(germany.getId(), stateEntry.getKey())
                    .orElseGet(() -> placeStateRepository.save(PlaceState.builder().country(germany).name(stateEntry.getKey()).build()));

            for (Map.Entry<String, List<String>> cityEntry : stateEntry.getValue().entrySet()) {
                PlaceCity city = placeCityRepository.findByStateIdAndNameIgnoreCase(state.getId(), cityEntry.getKey())
                        .orElseGet(() -> placeCityRepository.save(PlaceCity.builder().state(state).name(cityEntry.getKey()).build()));

                for (String districtName : cityEntry.getValue()) {
                    placeDistrictRepository.findByCityIdAndNameIgnoreCase(city.getId(), districtName)
                            .orElseGet(() -> placeDistrictRepository.save(PlaceDistrict.builder().city(city).name(districtName).build()));
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public List<PlaceCountryDTO> getPlaceTree(String countryCode) {
        List<PlaceCountry> countries;
        if (countryCode == null || countryCode.isBlank()) {
            countries = placeCountryRepository.findAll();
        } else {
            countries = placeCountryRepository.findByCodeIgnoreCase(countryCode.trim())
                    .map(List::of)
                    .orElse(List.of());
        }

        return countries.stream().map(country -> {
            List<PlaceStateDTO> states = placeStateRepository.findByCountryIdOrderByNameAsc(country.getId()).stream()
                    .map(state -> {
                        List<PlaceCityDTO> cities = placeCityRepository.findByStateIdOrderByNameAsc(state.getId()).stream()
                                .map(city -> PlaceCityDTO.builder()
                                        .id(city.getId())
                                        .name(city.getName())
                                        .districts(placeDistrictRepository.findByCityIdOrderByNameAsc(city.getId()).stream()
                                                .map(district -> PlaceDistrictDTO.builder()
                                                        .id(district.getId())
                                                        .name(district.getName())
                                                        .build())
                                                .toList())
                                        .build())
                                .toList();
                        return PlaceStateDTO.builder()
                                .id(state.getId())
                                .name(state.getName())
                                .cities(cities)
                                .build();
                    })
                    .toList();

            return PlaceCountryDTO.builder()
                    .id(country.getId())
                    .code(country.getCode())
                    .name(country.getName())
                    .states(states)
                    .build();
        }).toList();
    }

    @Transactional
    public GenerateSearchCombinationsResponseDTO generate(GenerateSearchCombinationsRequestDTO request) {
        if (request == null || request.getKeywordIds() == null || request.getKeywordIds().isEmpty()) {
            throw new BusinessException("keywordIds are required", HttpStatus.BAD_REQUEST);
        }

        List<Keyword> keywords = keywordRepository.findAllById(request.getKeywordIds()).stream()
                .filter(Keyword::isActive)
                .toList();
        if (keywords.isEmpty()) {
            throw new BusinessException("No active keywords found for provided ids", HttpStatus.BAD_REQUEST);
        }

        Set<Long> cityIds = new LinkedHashSet<>();
        Set<Long> districtIds = new LinkedHashSet<>();

        if (request.getStateIds() != null && !request.getStateIds().isEmpty()) {
            List<PlaceCity> citiesFromStates = request.getStateIds().stream()
                    .flatMap(stateId -> placeCityRepository.findByStateIdOrderByNameAsc(stateId).stream())
                    .toList();
            cityIds.addAll(citiesFromStates.stream().map(PlaceCity::getId).toList());
        }

        if (request.getCityIds() != null) {
            cityIds.addAll(request.getCityIds());
        }

        if (!cityIds.isEmpty()) {
            List<PlaceDistrict> districtsFromCities = placeDistrictRepository.findByCityIdIn(new ArrayList<>(cityIds));
            districtIds.addAll(districtsFromCities.stream().map(PlaceDistrict::getId).toList());
        }

        if (request.getDistrictIds() != null) {
            districtIds.addAll(request.getDistrictIds());
        }

        if (cityIds.isEmpty() && districtIds.isEmpty()) {
            throw new BusinessException("At least one stateId, cityId, or districtId is required", HttpStatus.BAD_REQUEST);
        }

        int maxResults = request.getMaxResults() == null ? DEFAULT_MAX_RESULTS : Math.max(1, request.getMaxResults());
        int created = 0;
        int existing = 0;

        Map<Long, PlaceDistrict> districtById = placeDistrictRepository.findAllById(districtIds).stream()
                .collect(Collectors.toMap(PlaceDistrict::getId, district -> district));

        for (Keyword keyword : keywords) {
            for (PlaceDistrict district : districtById.values()) {
                String placeKey = "DISTRICT:" + district.getId();
                if (searchCombinationRepository.existsByKeywordIdAndPlaceKey(keyword.getId(), placeKey)) {
                    existing++;
                    continue;
                }

                String displayName = district.getCity().getName() + " - " + district.getName();
                SearchCombination combination = SearchCombination.builder()
                        .keyword(keyword)
                        .city(district.getCity())
                        .district(district)
                        .placeKey(placeKey)
                        .placeDisplayName(displayName)
                        .status(SearchCombinationStatus.PENDING)
                        .maxResults(maxResults)
                        .build();
                searchCombinationRepository.save(combination);
                created++;
            }
        }

        return GenerateSearchCombinationsResponseDTO.builder()
                .created(created)
                .existing(existing)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<SearchCombinationDTO> list(SearchCombinationStatus status, Long categoryId, Pageable pageable) {
        return searchCombinationRepository.findForList(status, categoryId, pageable)
                .map(this::toDTO);
    }

    @Transactional
    public SearchCombinationDTO launch(Long combinationId, LaunchSearchCombinationRequestDTO request) {
        SearchCombination combination = searchCombinationRepository.findById(combinationId)
                .orElseThrow(() -> new BusinessException("Search combination not found with id: " + combinationId, HttpStatus.NOT_FOUND));

        if (request == null || request.getStatus() == null) {
            throw new BusinessException("status is required", HttpStatus.BAD_REQUEST);
        }

        int maxResults = request.getMaxResults() == null
                ? (combination.getMaxResults() == null ? DEFAULT_MAX_RESULTS : combination.getMaxResults())
                : Math.max(1, request.getMaxResults());

        combination.setStatus(request.getStatus());
        combination.setMaxResults(maxResults);

        if (request.getStatus() == SearchCombinationStatus.LAUNCHED) {
            combination.setLaunchedAt(LocalDateTime.now());
            combination.setFailedAt(null);
            combination.setFailureReason(null);
        } else if (request.getStatus() == SearchCombinationStatus.FAILED) {
            combination.setFailedAt(LocalDateTime.now());
            combination.setFailureReason(request.getFailureReason());
        }

        return toDTO(searchCombinationRepository.save(combination));
    }

    private SearchCombinationDTO toDTO(SearchCombination combination) {
        return SearchCombinationDTO.builder()
                .id(combination.getId())
                .keywordId(combination.getKeyword().getId())
                .keywordNameEn(combination.getKeyword().getNameEn())
                .keywordNameDe(combination.getKeyword().getNameDe())
                .categoryId(combination.getKeyword().getCategory().getId())
                .categoryName(combination.getKeyword().getCategory().getName())
                .cityId(combination.getCity().getId())
                .cityName(combination.getCity().getName())
                .districtId(combination.getDistrict() == null ? null : combination.getDistrict().getId())
                .districtName(combination.getDistrict() == null ? null : combination.getDistrict().getName())
                .placeDisplayName(combination.getPlaceDisplayName())
                .status(combination.getStatus())
                .maxResults(combination.getMaxResults())
                .launchedAt(combination.getLaunchedAt())
                .failedAt(combination.getFailedAt())
                .failureReason(combination.getFailureReason())
                .createdAt(combination.getCreatedAt())
                .build();
    }

    private LinkedHashMap<String, LinkedHashMap<String, List<String>>> germanySeed() {
        LinkedHashMap<String, LinkedHashMap<String, List<String>>> data = new LinkedHashMap<>();

        data.put("Bayern", orderedMap(
                entry("Muenchen", List.of("Schwabing", "Maxvorstadt", "Sendling", "Bogenhausen", "Pasing")),
                entry("Nuernberg", List.of("Nordstadt", "Suedstadt", "Gostenhof")),
                entry("Augsburg", List.of("Innenstadt", "Lechhausen", "Goeggingen"))
        ));

        data.put("Nordrhein-Westfalen", orderedMap(
                entry("Koeln", List.of("Ehrenfeld", "Nippes", "Chorweiler", "Kalk")),
                entry("Duesseldorf", List.of("Altstadt", "Bilk", "Oberkassel")),
                entry("Dortmund", List.of("Innenstadt-West", "Innenstadt-Ost", "Hoerde", "Eving")),
                entry("Essen", List.of("Ruettenscheid", "Kettwig", "Altenessen")),
                entry("Duisburg", List.of("Hamborn", "Meiderich", "Rheinhausen"))
        ));

        data.put("Baden-Wuerttemberg", orderedMap(
                entry("Stuttgart", List.of("Mitte", "Bad Cannstatt", "Vaihingen")),
                entry("Karlsruhe", List.of("Innenstadt", "Durlach")),
                entry("Mannheim", List.of("Neckarstadt", "Lindenhof"))
        ));

        data.put("Hessen", orderedMap(
                entry("Frankfurt am Main", List.of("Innenstadt", "Sachsenhausen", "Bockenheim", "Hoechst")),
                entry("Wiesbaden", List.of("Mitte", "Biebrich")),
                entry("Darmstadt", List.of("Arheilgen", "Eberstadt"))
        ));

        data.put("Niedersachsen", orderedMap(
                entry("Hannover", List.of("Mitte", "Linden", "Bothfeld")),
                entry("Braunschweig", List.of("Innenstadt", "Weststadt")),
                entry("Wolfsburg", List.of("Mitte-West", "Fallersleben"))
        ));

        data.put("Sachsen", orderedMap(
                entry("Leipzig", List.of("Zentrum", "Plagwitz", "Connewitz")),
                entry("Dresden", List.of("Altstadt", "Neustadt", "Blasewitz"))
        ));

        data.put("Berlin", orderedMap(
                entry("Berlin", List.of("Mitte", "Neukoelln", "Kreuzberg", "Charlottenburg", "Spandau"))
        ));

        data.put("Hamburg", orderedMap(
                entry("Hamburg", List.of("Altona", "Eimsbuettel", "Wandsbek", "Harburg"))
        ));

        data.put("Bremen", orderedMap(
                entry("Bremen", List.of("Mitte", "Vegesack", "Neustadt")),
                entry("Bremerhaven", List.of("Lehe", "Geestemuende"))
        ));

        return data;
    }

    @SafeVarargs
    private final LinkedHashMap<String, List<String>> orderedMap(Map.Entry<String, List<String>>... entries) {
        LinkedHashMap<String, List<String>> map = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }

    private Map.Entry<String, List<String>> entry(String key, List<String> value) {
        return Map.entry(key, value);
    }
}
