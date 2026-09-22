# Graph Report - reachflow  (2026-09-19)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 1334 nodes · 3493 edges · 67 communities (56 shown, 11 thin omitted)
- Extraction: 95% EXTRACTED · 5% INFERRED · 0% AMBIGUOUS · INFERRED: 164 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `99a54e72`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- lombok.Builder
- org.springframework.http.ResponseEntity
- CampaignDetailsComponent
- lombok.RequiredArgsConstructor
- LeadNewSearchComponent
- @angular/core
- reachflow
- org.springframework.transaction.annotation.Transactional
- Client
- CampaignSendRepository
- ArchiveDataSourceConfig.java
- DashboardHomeComponent
- BusinessException
- LeadService
- CategoriesKeywordsComponent
- Client
- LeadService
- package.json
- lead-new-search.component.ts
- LeadController
- lead-combinations.component.ts
- Lead
- LeadDatabaseComponent
- ArchiveService.java
- org.springframework.data.domain.Page
- SearchCombination
- dependencies
- models.ts
- Category
- PlaceCity
- ScrapeProgressTracker
- CampaignService
- org.springframework.data.jpa.repository.JpaRepository
- LeadEmailRepository
- CampaignStatus
- ArchivedCampaign
- SearchCombinationStatus
- SearchCombinationService.java
- EmailAuditComponent
- LeadCategoryRepository
- .findByLeadId
- devDependencies
- CategoryService
- AttributeEncryptor
- PlaceCountry
- PlaceState
- EmailTemplateService
- CampaignPerformanceComponent
- ArchivedCampaignSend
- ClientCategoryDocument
- LeadEmail
- ArchivedClient
- SearchCombinationService
- MainLayoutComponent
- SearchCombinationController
- GeocodingService
- LeadsShellComponent
- app.ts
- TemplateLoaderDialogComponent
- Application
- scripts
- prettier
- SelectiveSendDialogComponent
- CategoriesShellComponent
- SkeletonLoaderComponent
- FilterByIdPipe
- com.majd:reachflow

## God Nodes (most connected - your core abstractions)
1. `BusinessException` - 56 edges
2. `LeadNewSearchComponent` - 52 edges
3. `LeadService` - 47 edges
4. `CampaignDetailsComponent` - 39 edges
5. `@angular/core` - 38 edges
6. `CampaignExecutionService` - 33 edges
7. `CampaignSendRepository` - 32 edges
8. `LeadDTO` - 29 edges
9. `CampaignService` - 29 edges
10. `LeadService` - 27 edges

## Surprising Connections (you probably didn't know these)
- `ClientDialogResult` --references--> `Client`  [EXTRACTED]
  frontend/src/app/features/clients/client-dialog/client-dialog.component.ts → frontend/src/app/core/models/models.ts
- `ArchivedCampaignDTO` --references--> `CampaignStatus`  [EXTRACTED]
  backend/src/main/java/com/majd/reachflow/archive/dto/ArchivedCampaignDTO.java → backend/src/main/java/com/majd/reachflow/entity/enums/CampaignStatus.java
- `ArchivedCampaignSendDTO` --references--> `CampaignSendStatus`  [EXTRACTED]
  backend/src/main/java/com/majd/reachflow/archive/dto/ArchivedCampaignSendDTO.java → backend/src/main/java/com/majd/reachflow/entity/enums/CampaignSendStatus.java
- `CampaignController` --references--> `CampaignService`  [EXTRACTED]
  backend/src/main/java/com/majd/reachflow/controller/CampaignController.java → backend/src/main/java/com/majd/reachflow/service/CampaignService.java
- `BulkLeadImportResponseDTO` --references--> `LeadDTO`  [EXTRACTED]
  backend/src/main/java/com/majd/reachflow/dto/BulkLeadImportResponseDTO.java → backend/src/main/java/com/majd/reachflow/dto/LeadDTO.java

## Import Cycles
- None detected.

## Communities (67 total, 11 thin omitted)

### Community 0 - "lombok.Builder"
Cohesion: 0.08
Nodes (41): ArchivedCampaignDTO, CampaignStats, ArchivedCampaignSendDTO, CampaignController, DeleteMapping, GetMapping, PostMapping, PutMapping (+33 more)

### Community 1 - "org.springframework.http.ResponseEntity"
Cohesion: 0.07
Nodes (27): PostMapping, PostMapping, CategoryController, ClientController, DeleteMapping, GetMapping, PostMapping, PutMapping (+19 more)

### Community 2 - "CampaignDetailsComponent"
Cohesion: 0.06
Nodes (11): CampaignSend, CampaignSendStatus, BOUNCED, FAILED, PENDING, REPLIED, SENT, ArchiveComponent (+3 more)

### Community 3 - "lombok.RequiredArgsConstructor"
Cohesion: 0.09
Nodes (24): Campaign, AllArgsConstructor, Builder, Entity, Getter, NoArgsConstructor, Setter, Table (+16 more)

### Community 5 - "@angular/core"
Cohesion: 0.12
Nodes (20): routes, errorInterceptor(), loadingInterceptor(), CampaignStats, LoadingService, Injectable, AusbildungFinderComponent, Component (+12 more)

### Community 6 - "reachflow"
Cohesion: 0.05
Nodes (40): build, extract-i18n, serve, test, builder, configurations, defaultConfiguration, options (+32 more)

### Community 7 - "org.springframework.transaction.annotation.Transactional"
Cohesion: 0.12
Nodes (10): CampaignSendController, RequestMapping, RestController, CampaignExecutionService, CampaignStartContext, CampaignSend, Client, Lead (+2 more)

### Community 8 - "Client"
Cohesion: 0.11
Nodes (18): ClientDTO, Client, AllArgsConstructor, Builder, Entity, Getter, NoArgsConstructor, Setter (+10 more)

### Community 9 - "CampaignSendRepository"
Cohesion: 0.11
Nodes (17): CampaignSend, AllArgsConstructor, Builder, Entity, Getter, NoArgsConstructor, Setter, Table (+9 more)

### Community 10 - "ArchiveDataSourceConfig.java"
Cohesion: 0.15
Nodes (20): ArchiveDataSourceConfig, ArchiveJpaRepositoriesConfig, PrimaryJpaRepositoriesConfig, CorsConfig, DataSourceProperties, jakarta.persistence.EntityManagerFactory, javax.sql.DataSource, org.springframework.boot.ApplicationRunner (+12 more)

### Community 11 - "DashboardHomeComponent"
Cohesion: 0.10
Nodes (7): DashboardHomeComponent, Component, ViewChild, LeadMapComponent, Component, Input, ViewChild

### Community 12 - "BusinessException"
Cohesion: 0.15
Nodes (8): KeywordDTO, Entity, Table, Keyword, BusinessException, KeywordRepository, CategoryService, org.springframework.http.HttpStatus

### Community 13 - "LeadService"
Cohesion: 0.09
Nodes (11): BulkImportResult, BulkLeadImportResponse, DeleteLeadEmailsResponse, EmailAuditItem, GenerateSearchCombinationsRequest, GenerateSearchCombinationsResponse, LaunchSearchCombinationRequest, Lead (+3 more)

### Community 14 - "CategoriesKeywordsComponent"
Cohesion: 0.11
Nodes (6): CategoryWithKeywords, Keyword, CategoriesKeywordsComponent, Component, ClientDialogComponent, Component

### Community 15 - "Client"
Cohesion: 0.11
Nodes (8): Client, GmailScanResult, ClientService, Injectable, CampaignDialogComponent, Component, ClientListComponent, Component

### Community 16 - "LeadService"
Cohesion: 0.23
Nodes (3): LeadDTO, Lead, LeadService

### Community 17 - "package.json"
Cohesion: 0.08
Nodes (24): name, private, version, @angular/build, @angular/cdk, @angular/cli, @angular/compiler, @angular/compiler-cli (+16 more)

### Community 18 - "lead-new-search.component.ts"
Cohesion: 0.12
Nodes (11): PlaceCityTree, PlaceCountryTree, PlaceDistrictTree, PlaceStateTree, CategoriesPlacesComponent, Component, CollectorCategory, CollectorKeyword (+3 more)

### Community 19 - "LeadController"
Cohesion: 0.19
Nodes (8): DeleteMapping, GetMapping, PostMapping, PutMapping, RequestMapping, ResponseEntity, RestController, LeadController

### Community 20 - "lead-combinations.component.ts"
Cohesion: 0.15
Nodes (7): SearchCombination, SearchCombinationStatus, DEFAULT_N8N_WEBHOOK_URL, N8nSettingsService, Injectable, LeadCombinationsComponent, Component

### Community 21 - "Lead"
Cohesion: 0.13
Nodes (14): AllArgsConstructor, Builder, Entity, Getter, NoArgsConstructor, Setter, Table, Lead (+6 more)

### Community 22 - "LeadDatabaseComponent"
Cohesion: 0.12
Nodes (5): LeadDatabaseComponent, Component, ViewChild, ConfirmDialogComponent, Component

### Community 23 - "ArchiveService.java"
Cohesion: 0.19
Nodes (7): ArchiveController, GetMapping, RequestMapping, RestController, ArchivedClientDTO, ArchivedCampaignSendRepository, ArchiveService

### Community 24 - "org.springframework.data.domain.Page"
Cohesion: 0.28
Nodes (3): LeadRepository, org.springframework.data.domain.Page, org.springframework.data.domain.Pageable

### Community 25 - "SearchCombination"
Cohesion: 0.11
Nodes (16): AllArgsConstructor, Builder, Entity, Getter, NoArgsConstructor, Setter, Table, PlaceDistrict (+8 more)

### Community 26 - "dependencies"
Cohesion: 0.11
Nodes (18): dependencies, @angular/animations, @angular/cdk, @angular/common, @angular/compiler, @angular/core, @angular/forms, @angular/material (+10 more)

### Community 27 - "models.ts"
Cohesion: 0.22
Nodes (9): ArchivedCampaign, ArchivedCampaignSend, ArchivedClient, CampaignLog, ClientCategoryDocument, PageResponse, ScrapeStatus, ArchiveService (+1 more)

### Community 28 - "Category"
Cohesion: 0.21
Nodes (8): CategorySeeder, Override, Category, Entity, Table, CategoryRepository, org.springframework.boot.CommandLineRunner, org.springframework.stereotype.Component

### Community 29 - "PlaceCity"
Cohesion: 0.15
Nodes (9): AllArgsConstructor, Builder, Entity, Getter, NoArgsConstructor, Setter, Table, PlaceCity (+1 more)

### Community 30 - "ScrapeProgressTracker"
Cohesion: 0.23
Nodes (3): ScrapeProgressTracker, jakarta.annotation.PostConstruct, SuppressWarnings

### Community 31 - "CampaignService"
Cohesion: 0.16
Nodes (5): Campaign, CampaignScheduleRequest, SelectiveSendRequest, CampaignService, Injectable

### Community 32 - "org.springframework.data.jpa.repository.JpaRepository"
Cohesion: 0.30
Nodes (7): ArchivedClientRepository, ClientRepository, PlaceDistrictRepository, PlaceStateRepository, SearchCombinationRepository, org.springframework.data.jpa.repository.JpaRepository, org.springframework.stereotype.Repository

### Community 34 - "CampaignStatus"
Cohesion: 0.17
Nodes (7): CampaignStatus, COMPLETED, DRAFT, RUNNING, STOP_REQUESTED, CampaignListComponent, Component

### Community 35 - "ArchivedCampaign"
Cohesion: 0.18
Nodes (9): ArchivedCampaign, AllArgsConstructor, Builder, Entity, Getter, NoArgsConstructor, Setter, Table (+1 more)

### Community 36 - "SearchCombinationStatus"
Cohesion: 0.23
Nodes (6): LaunchSearchCombinationRequestDTO, SearchCombinationDTO, SearchCombinationStatus, FAILED, LAUNCHED, PENDING

### Community 37 - "SearchCombinationService.java"
Cohesion: 0.30
Nodes (7): GenerateSearchCombinationsRequestDTO, GenerateSearchCombinationsResponseDTO, PlaceCityDTO, PlaceCountryDTO, PlaceDistrictDTO, PlaceStateDTO, lombok.Getter

### Community 39 - "LeadCategoryRepository"
Cohesion: 0.19
Nodes (5): Entity, Table, LeadCategory, LeadCategoryRepository, Campaign

### Community 41 - "devDependencies"
Cohesion: 0.15
Nodes (13): devDependencies, @angular/build, @angular/cli, @angular/compiler-cli, jasmine-core, karma, karma-chrome-launcher, karma-coverage (+5 more)

### Community 42 - "CategoryService"
Cohesion: 0.22
Nodes (5): Category, ClientCategoryDTO, CategoryService, Injectable, ClientDialogResult

### Community 43 - "AttributeEncryptor"
Cohesion: 0.27
Nodes (6): AttributeEncryptor, Override, jakarta.persistence.AttributeConverter, jakarta.persistence.Converter, java.security.Key, javax.crypto.Cipher

### Community 44 - "PlaceCountry"
Cohesion: 0.20
Nodes (9): AllArgsConstructor, Builder, Entity, Getter, NoArgsConstructor, Setter, Table, PlaceCountry (+1 more)

### Community 45 - "PlaceState"
Cohesion: 0.17
Nodes (8): AllArgsConstructor, Builder, Entity, Getter, NoArgsConstructor, Setter, Table, PlaceState

### Community 47 - "CampaignPerformanceComponent"
Cohesion: 0.31
Nodes (4): CampaignPerformanceComponent, Component, Input, ViewChild

### Community 48 - "ArchivedCampaignSend"
Cohesion: 0.20
Nodes (8): ArchivedCampaignSend, AllArgsConstructor, Builder, Entity, Getter, NoArgsConstructor, Setter, Table

### Community 49 - "ClientCategoryDocument"
Cohesion: 0.29
Nodes (4): ClientCategoryDocument, Entity, Table, ClientCategoryDocumentRepository

### Community 50 - "LeadEmail"
Cohesion: 0.20
Nodes (8): AllArgsConstructor, Builder, Entity, Getter, NoArgsConstructor, Setter, Table, LeadEmail

### Community 51 - "ArchivedClient"
Cohesion: 0.22
Nodes (8): ArchivedClient, AllArgsConstructor, Builder, Entity, Getter, NoArgsConstructor, Setter, Table

### Community 52 - "SearchCombinationService"
Cohesion: 0.31
Nodes (3): SearchCombinationService, Entry, SafeVarargs

### Community 54 - "SearchCombinationController"
Cohesion: 0.29
Nodes (5): GetMapping, PostMapping, RequestMapping, RestController, SearchCombinationController

### Community 55 - "GeocodingService"
Cohesion: 0.39
Nodes (3): GeocodingService, com.fasterxml.jackson.databind.ObjectMapper, java.net.http.HttpClient

### Community 57 - "app.ts"
Cohesion: 0.38
Nodes (4): App, appConfig, Component, @angular/platform-browser

### Community 58 - "TemplateLoaderDialogComponent"
Cohesion: 0.33
Nodes (3): EmailTemplate, TemplateLoaderDialogComponent, Component

### Community 59 - "Application"
Cohesion: 0.53
Nodes (4): Application, org.springframework.boot.autoconfigure.SpringBootApplication, org.springframework.scheduling.annotation.EnableAsync, org.springframework.scheduling.annotation.EnableScheduling

### Community 60 - "scripts"
Cohesion: 0.33
Nodes (6): scripts, build, ng, start, test, watch

### Community 61 - "prettier"
Cohesion: 0.50
Nodes (4): prettier, overrides, printWidth, singleQuote

### Community 64 - "SkeletonLoaderComponent"
Cohesion: 0.67
Nodes (3): SkeletonLoaderComponent, Component, Input

## Knowledge Gaps
- **119 isolated node(s):** `CollectorCategory`, `CollectorKeyword`, `DebugLevel`, `DebugLog`, `SelectedPlaceItem` (+114 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 401 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **11 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `CampaignDetailsComponent` connect `CampaignDetailsComponent` to `CampaignStatus`, `@angular/core`, `Client`, `models.ts`, `CampaignService`?**
  _High betweenness centrality (0.028) - this node is a cross-community bridge._
- **Why does `LeadNewSearchComponent` connect `LeadNewSearchComponent` to `lead-new-search.component.ts`, `LeadService`?**
  _High betweenness centrality (0.021) - this node is a cross-community bridge._
- **Why does `CampaignExecutionService` connect `org.springframework.transaction.annotation.Transactional` to `LeadEmailRepository`, `lombok.RequiredArgsConstructor`, `LeadCategoryRepository`, `Client`, `CampaignSendRepository`, `LeadService`, `ClientCategoryDocument`, `Lead`?**
  _High betweenness centrality (0.021) - this node is a cross-community bridge._
- **What connects `CollectorCategory`, `CollectorKeyword`, `DebugLevel` to the rest of the system?**
  _119 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `lombok.Builder` be split into smaller, more focused modules?**
  _Cohesion score 0.07549634273772204 - nodes in this community are weakly interconnected._
- **Should `org.springframework.http.ResponseEntity` be split into smaller, more focused modules?**
  _Cohesion score 0.06721311475409836 - nodes in this community are weakly interconnected._
- **Should `CampaignDetailsComponent` be split into smaller, more focused modules?**
  _Cohesion score 0.06019871420222092 - nodes in this community are weakly interconnected._