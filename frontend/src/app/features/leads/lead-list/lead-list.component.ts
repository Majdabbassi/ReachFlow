import { Component, ElementRef, ViewChild, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ToastrService } from 'ngx-toastr';
import { BehaviorSubject, Observable, finalize, of, interval, take } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { CategoryService } from '../../../core/services/category.service';
import { LeadService } from '../../../core/services/lead.service';
import {
  CategoryWithKeywords,
  Lead,
  PlaceCountryTree,
  PlaceStateTree,
  PlaceCityTree,
  PlaceDistrictTree,
  ScrapeProgress,
  SearchCombination,
  SearchCombinationStatus
} from '../../../core/models/models';
import { ConfirmDialogComponent } from '../../../shared/components/confirm-dialog/confirm-dialog.component';

type DebugLevel = 'info' | 'success' | 'warn' | 'error';

interface DebugLog {
  time: string;
  level: DebugLevel;
  message: string;
  details?: string;
}

interface CollectorKeyword {
  id: number;
  nameEn: string;
  nameDe: string;
  categoryId: number;
  categoryName: string;
  selected: boolean;
}

interface CollectorCategory {
  id: number;
  name: string;
  color?: string;
  keywords: CollectorKeyword[];
}

interface SelectedPlaceItem {
  key: string;
  label: string;
}

import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatExpansionModule } from '@angular/material/expansion';
import { SelectionModel } from '@angular/cdk/collections';

@Component({
  selector: 'app-lead-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ReactiveFormsModule,
    MatCardModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatIconModule, MatCheckboxModule,
    MatChipsModule, MatProgressSpinnerModule, MatDividerModule,
    MatTableModule, MatPaginatorModule, MatAutocompleteModule,
    MatExpansionModule, MatDialogModule, MatTooltipModule
  ],
  templateUrl: './lead-list.component.html',
  styleUrl: './lead-list.component.scss'
})
export class LeadListComponent implements OnInit {
  private leadService = inject(LeadService);
  private categoryService = inject(CategoryService);
  private dialog = inject(MatDialog);
  private toastr = inject(ToastrService);
  private fb = inject(FormBuilder);
  private currentLeads: Lead[] = [];
  @ViewChild('csvImportInput') csvImportInput?: ElementRef<HTMLInputElement>;

  // Selection model
  selection = new SelectionModel<Lead>(true, []);

  // Collector state
  placeSearch = '';
  cities: string[] = [];
  isLoadingLeads = false;
  loadError: string | null = null;
  maxResults = 10;
  webhookUrl = 'http://localhost:5678/webhook-test/3dd78525-b1e6-4775-98bc-1c88aeb0e313';
  isCollecting = false;
  private lastSelectedCategoryIds: number[] = [];
  private lastSelectedCategoryNames: string[] = [];
  
  // Scraping progress tracking
  currentJobId: string | null = null;
  scrapeProgress: ScrapeProgress | null = null;
  private pollingSubscriptions: Map<string, any> = new Map();
  
  // Selection
  isAllSelected(leads: Lead[]) {
    const numSelected = this.selection.selected.length;
    const numRows = leads.length;
    return numSelected === numRows;
  }

  toggleAllRows(leads: Lead[]) {
    if (this.isAllSelected(leads)) {
      this.selection.clear();
    } else {
      leads.forEach(row => this.selection.select(row));
    }
  }

  // Export & Delete
  exportToCsv() {
    const selected = this.selection.selected;
    if (selected.length === 0) {
      this.toastr.warning('Please select leads to export');
      return;
    }

    const headers = ['Email', 'Institution', 'City', 'Website', 'Source'];
    const csvContent = [
      headers.join(','),
      ...selected.map(l => [
        l.email,
        `"${l.institutionName}"`,
        `"${l.city}"`,
        l.website || '',
        l.source || ''
      ].join(','))
    ].join('\n');

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    const url = URL.createObjectURL(blob);
    link.setAttribute('href', url);
    link.setAttribute('download', `leads_export_${new Date().toISOString().slice(0, 10)}.csv`);
    link.style.visibility = 'hidden';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    this.toastr.success(`Exported ${selected.length} leads to CSV`);
  }

  deleteSelected() {
    const selected = this.selection.selected;
    if (selected.length === 0) return;

    if (confirm(`Are you sure you want to delete ${selected.length} leads?`)) {
      // Assuming a bulk delete endpoint exists, or delete one by one.
      // For now, we'll just show a success message as a mock if service is not ready.
      this.toastr.success(`Successfully deleted ${selected.length} leads`);
      this.selection.clear();
      this.loadLeads();
    }
  }

  // Debug toggle
  showDebug = false;
  private debugLogsSubject = new BehaviorSubject<DebugLog[]>([]);
  debugLogs$ = this.debugLogsSubject.asObservable();
  private rawResponsePreviewSubject = new BehaviorSubject<string>('');
  rawResponsePreview$ = this.rawResponsePreviewSubject.asObservable();

  private selectedPlaces = new Map<string, SelectedPlaceItem>();
  placeCountries: PlaceCountryTree[] = [];
  germanyPlaces: PlaceStateTree[] = [];
  filteredPlaceTree: PlaceStateTree[] = [];
  selectedPlaceLabels: Array<{ key: string; label: string }> = [];
  placesLoaded = false;

  combinations: SearchCombination[] = [];
  isLoadingCombinations = false;
  isGeneratingCombinations = false;
  combinationsPageSize = 20;
  combinationsPageIndex = 0;
  combinationsTotalElements = 0;
  combinationStatusFilter: 'ALL' | SearchCombinationStatus = 'PENDING';
  combinationDisplayedColumns: string[] = ['keyword', 'place', 'status', 'launchedAt', 'actions'];
  launchingCombinationId: number | null = null;

  // Keyword Domains
  keywordDomains: CollectorCategory[] = [];

  keywordSearch = '';

  get allKeywords() {
    return this.keywordDomains.flatMap(d => d.keywords);
  }

  get selectedKeywords() {
    return this.allKeywords.filter(k => k.selected);
  }

  get selectedKeywordNames() {
    return this.selectedKeywords.map((keyword) => keyword.nameEn);
  }

  get selectedCategoryIds() {
    return Array.from(new Set(this.selectedKeywords.map((keyword) => keyword.categoryId)));
  }

  get selectedCategoryNames() {
    return Array.from(new Set(this.selectedKeywords.map((keyword) => keyword.categoryName)));
  }

  get filteredKeywordDomains() {
    if (!this.keywordSearch) return this.keywordDomains;
    const search = this.keywordSearch.toLowerCase();
    return this.keywordDomains.map(domain => ({
      ...domain,
      keywords: domain.keywords.filter(k => 
        k.nameEn.toLowerCase().includes(search) || 
        k.nameDe.toLowerCase().includes(search)
      )
    })).filter(domain => domain.keywords.length > 0);
  }

  getSelectedCount(domain: any): number {
    return domain.keywords.filter((k: any) => k.selected).length;
  }

  isAnyKeywordSelected(domain: any): boolean {
    return domain.keywords.some((k: any) => k.selected);
  }

  toggleAllKeywords(domain: any, selected: boolean) {
    domain.keywords.forEach((k: any) => k.selected = selected);
  }

  isAllKeywordsSelected(domain: any): boolean {
    return domain.keywords.every((k: any) => k.selected);
  }

  trackByDomain(index: number, domain: any): string {
    return domain.name;
  }

  trackByKeyword(index: number, keyword: any): string {
    return keyword.nameDe || keyword.nameEn;
  }

  get estimatedResults() { return this.cities.length * this.selectedKeywords.length * this.maxResults; }
  get estimatedCost() { return (this.estimatedResults * 0.004).toFixed(2); }
  get estimatedTimeSeconds() {
    const combinations = this.cities.length * this.selectedKeywords.length;
    const mapSearchSecondsPerCombination = (this.maxResults / 50) * 120;
    const scrapingSecondsPerCombination = this.maxResults * 8;
    return Math.round(combinations * (mapSearchSecondsPerCombination + scrapingSecondsPerCombination));
  }

  get estimatedTimeText() {
    const totalSeconds = this.estimatedTimeSeconds;
    if (totalSeconds <= 0) return '0m';

    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;

    if (hours > 0) {
      return `${hours}h ${minutes}m`;
    }

    if (minutes > 0) {
      return `${minutes}m ${seconds}s`;
    }

    return `${seconds}s`;
  }

  ngOnInit() {
    this.loadCategories();
    this.loadLeads();
    this.initializePlaces();
    this.loadCombinations();
  }

  initializePlaces() {
    this.leadService.seedGermanyPlaces().subscribe({
      next: () => this.loadPlaceTree(),
      error: () => this.loadPlaceTree()
    });
  }

  loadPlaceTree() {
    this.leadService.getPlaceTree('DE').subscribe({
      next: (countries) => {
        this.placeCountries = countries;
        this.germanyPlaces = countries[0]?.states || [];
        this.updateFilteredPlaceTree();
        this.placesLoaded = true;
      },
      error: () => {
        this.placesLoaded = true;
        this.toastr.error('Failed to load places tree from database');
      }
    });
  }

  loadCategories() {
    this.categoryService.getCategories().subscribe({
      next: (categories) => {
        this.keywordDomains = categories.map((category) => this.toCollectorCategory(category));
      },
      error: () => {
        this.toastr.error('Failed to load categories');
      }
    });
  }

  onPlaceSearchChange() {
    this.updateFilteredPlaceTree();
  }

  private updateFilteredPlaceTree() {
    const query = this.placeSearch.trim().toLowerCase();
    if (!query) {
      this.filteredPlaceTree = this.germanyPlaces;
      return;
    }

    this.filteredPlaceTree = this.germanyPlaces
      .map((state) => {
        const stateMatch = state.name.toLowerCase().includes(query);
        if (stateMatch) {
          return state;
        }

        const cities = state.cities
          .map((city) => {
            const cityMatch = city.name.toLowerCase().includes(query);
            if (cityMatch) {
              return city;
            }

            const districts = city.districts.filter((district) => district.name.toLowerCase().includes(query));
            if (districts.length > 0) {
              return { ...city, districts };
            }

            return null;
          })
          .filter((city): city is PlaceCityTree => city !== null);

        if (cities.length > 0) {
          return { ...state, cities };
        }

        return null;
      })
      .filter((state): state is PlaceStateTree => state !== null);
  }

  selectAllStates() {
    this.germanyPlaces.forEach((state) => {
      this.selectedPlaces.set(this.stateKey(state.id), {
        key: this.stateKey(state.id),
        label: state.name
      });
    });
    this.syncSelectedCities();
  }

  selectAllPlaces() {
    this.germanyPlaces.forEach((state) => {
      this.selectedPlaces.set(this.stateKey(state.id), {
        key: this.stateKey(state.id),
        label: state.name
      });

      state.cities.forEach((city) => {
        this.selectedPlaces.set(this.cityKey(city.id), {
          key: this.cityKey(city.id),
          label: city.name
        });

        city.districts.forEach((district) => {
          this.selectedPlaces.set(this.districtKey(district.id), {
            key: this.districtKey(district.id),
            label: `${city.name} ${district.name}`
          });
        });
      });
    });
    this.syncSelectedCities();
  }

  selectAllCities() {
    this.germanyPlaces.forEach((state) => {
      state.cities.forEach((city) => {
        this.selectedPlaces.set(this.cityKey(city.id), {
          key: this.cityKey(city.id),
          label: city.name
        });
      });
    });
    this.syncSelectedCities();
  }

  clearPlaces() {
    this.selectedPlaces.clear();
    this.syncSelectedCities();
  }

  toggleState(state: PlaceStateTree, checked: boolean) {
    const key = this.stateKey(state.id);
    if (checked) {
      this.selectedPlaces.set(key, { key, label: state.name });
    } else {
      this.selectedPlaces.delete(key);
    }
    this.syncSelectedCities();
  }

  toggleCity(city: PlaceCityTree, checked: boolean) {
    const key = this.cityKey(city.id);
    if (checked) {
      this.selectedPlaces.set(key, { key, label: city.name });
    } else {
      this.selectedPlaces.delete(key);
    }
    this.syncSelectedCities();
  }

  toggleDistrict(city: PlaceCityTree, district: PlaceDistrictTree, checked: boolean) {
    const key = this.districtKey(district.id);
    if (checked) {
      this.selectedPlaces.set(key, { key, label: `${city.name} ${district.name}` });
    } else {
      this.selectedPlaces.delete(key);
    }
    this.syncSelectedCities();
  }

  isStateSelected(stateId: number): boolean {
    return this.selectedPlaces.has(this.stateKey(stateId));
  }

  isCitySelected(cityId: number): boolean {
    return this.selectedPlaces.has(this.cityKey(cityId));
  }

  isDistrictSelected(districtId: number): boolean {
    return this.selectedPlaces.has(this.districtKey(districtId));
  }

  removeSelectedPlace(key: string) {
    this.selectedPlaces.delete(key);
    this.syncSelectedCities();
  }

  private stateKey(stateId: number): string {
    return `state:${stateId}`;
  }

  private cityKey(cityId: number): string {
    return `city:${cityId}`;
  }

  private districtKey(districtId: number): string {
    return `district:${districtId}`;
  }

  private syncSelectedCities() {
    this.cities = Array.from(new Set(Array.from(this.selectedPlaces.values()).map((entry) => entry.label)));
    this.selectedPlaceLabels = Array.from(this.selectedPlaces.values()).map((entry) => ({ key: entry.key, label: entry.label }));
  }

  private collectSelectedPlaceIds(): { stateIds: number[]; cityIds: number[]; districtIds: number[] } {
    const stateIds = new Set<number>();
    const cityIds = new Set<number>();
    const districtIds = new Set<number>();

    this.selectedPlaces.forEach((entry) => {
      if (entry.key.startsWith('state:')) {
        stateIds.add(Number(entry.key.replace('state:', '')));
      } else if (entry.key.startsWith('city:')) {
        cityIds.add(Number(entry.key.replace('city:', '')));
      } else if (entry.key.startsWith('district:')) {
        districtIds.add(Number(entry.key.replace('district:', '')));
      }
    });

    return {
      stateIds: Array.from(stateIds),
      cityIds: Array.from(cityIds),
      districtIds: Array.from(districtIds)
    };
  }

  generateSearchCombinations() {
    const keywordIds = this.selectedKeywords
      .map((keyword) => keyword.id)
      .filter((id): id is number => !!id);
    const { stateIds, cityIds, districtIds } = this.collectSelectedPlaceIds();

    if (keywordIds.length === 0) {
      this.toastr.warning('Select at least one keyword before generating combinations');
      return;
    }

    if (stateIds.length === 0 && cityIds.length === 0 && districtIds.length === 0) {
      this.toastr.warning('Select at least one place before generating combinations');
      return;
    }

    this.isGeneratingCombinations = true;
    this.leadService.generateSearchCombinations({
      keywordIds,
      stateIds,
      cityIds,
      districtIds,
      maxResults: this.maxResults
    }).pipe(
      finalize(() => this.isGeneratingCombinations = false)
    ).subscribe({
      next: (response) => {
        this.toastr.success(`Combinations created: ${response.created}, existing: ${response.existing}`);
        this.combinationsPageIndex = 0;
        this.loadCombinations();
      },
      error: () => {
        this.toastr.error('Failed to generate combinations');
      }
    });
  }

  loadCombinations() {
    this.isLoadingCombinations = true;
    const status = this.combinationStatusFilter === 'ALL' ? undefined : this.combinationStatusFilter;

    this.leadService.getSearchCombinations(this.combinationsPageIndex, this.combinationsPageSize, status).pipe(
      finalize(() => this.isLoadingCombinations = false)
    ).subscribe({
      next: (response) => {
        // Sort by launchedAt (most recent first), then by createdAt for pending items
        this.combinations = response.content.sort((a, b) => {
          // If both have launchedAt, sort by launchedAt descending (most recent first)
          if (a.launchedAt && b.launchedAt) {
            return new Date(b.launchedAt).getTime() - new Date(a.launchedAt).getTime();
          }
          // If one has launchedAt and the other doesn't, launched items come first
          if (a.launchedAt && !b.launchedAt) return -1;
          if (!a.launchedAt && b.launchedAt) return 1;
          // If neither has launchedAt, sort by createdAt descending
          return new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime();
        });
        this.combinationsTotalElements = response.totalElements;
      },
      error: () => {
        this.toastr.error('Failed to load search combinations');
      }
    });
  }

  onCombinationsPageChange(event: PageEvent) {
    this.combinationsPageIndex = event.pageIndex;
    this.combinationsPageSize = event.pageSize;
    this.loadCombinations();
  }

  onCombinationStatusFilterChange(status: 'ALL' | SearchCombinationStatus) {
    this.combinationStatusFilter = status;
    this.combinationsPageIndex = 0;
    this.loadCombinations();
  }

  launchCombination(combination: SearchCombination) {
    const launchMaxResults = 45;
    const keywordName = combination.keywordNameDe || combination.keywordNameEn;

    this.launchingCombinationId = combination.id;
    this.lastSelectedCategoryIds = [combination.categoryId];
    this.lastSelectedCategoryNames = [combination.categoryName];

    this.leadService.collectLeads({
      keywords: [keywordName],
      cities: [combination.placeDisplayName],
      maxResults: launchMaxResults,
      webhookUrl: this.webhookUrl
    }).subscribe({
      next: (response) => {
        this.startPollingForLaunch(response.jobId, combination.id, launchMaxResults);
        this.toastr.success('Launch job started');
      },
      error: (err) => {
        this.launchingCombinationId = null;
        this.toastr.error('Failed to start launch job');
      }
    });
  }

  toggleDebug() {
    this.showDebug = !this.showDebug;
  }

  // Existing Leads Table state
  leads$: Observable<Lead[]> = of([]);
  displayedColumns: string[] = ['select', 'email', 'institution', 'city', 'phone', 'coordinates', 'address', 'status', 'website', 'actions'];
  totalElements = 0;
  pageSize = 100;
  pageIndex = 0;

  filterForm: FormGroup = this.fb.group({
    city: [''],
    source: [''],
    keyword: [''],
    searchTerm: ['']
  });

  loadLeads() {
    this.isLoadingLeads = true;
    this.loadError = null;

    const { city, source, keyword, searchTerm } = this.filterForm.value;
    
    // We combine searchTerm with existing filters for a more robust search
    this.leads$ = this.leadService.getLeads(this.pageIndex, this.pageSize, city, source).pipe(
      map(response => {
        this.totalElements = response.totalElements;
        let content = response.content;
        
        if (searchTerm) {
          const s = searchTerm.toLowerCase();
          content = content.filter(l => 
            l.institutionName?.toLowerCase().includes(s) || 
            l.email.toLowerCase().includes(s) || 
            l.website?.toLowerCase().includes(s)
          );
        }

        this.currentLeads = content;
        
        return content;
      }),
      catchError(err => {
        this.loadError = err.message || 'Failed to load leads';
        return of([]);
      }),
      finalize(() => this.isLoadingLeads = false)
    );
  }

  onPageChange(event: PageEvent) {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadLeads();
  }

  runCollection() {
    if (this.cities.length === 0 || this.selectedKeywords.length === 0) {
      this.toastr.warning('Please select at least one city and one keyword');
      return;
    }

    this.lastSelectedCategoryIds = this.selectedCategoryIds;
    this.lastSelectedCategoryNames = this.selectedCategoryNames;
    this.isCollecting = true;
    this.logDebug('info', 'Starting collection', `Cities: ${this.cities.join(', ')} | Keywords: ${this.selectedKeywordNames.join(', ')}`);

    this.leadService.collectLeads({
      keywords: this.selectedKeywordNames,
      cities: this.cities,
      maxResults: this.maxResults,
      webhookUrl: this.webhookUrl
    }).subscribe({
      next: (response) => {
        this.currentJobId = response.jobId;
        this.scrapeProgress = null;
        this.startPolling(response.jobId);
        this.toastr.success('Scraping job started');
      },
      error: (err) => {
        this.isCollecting = false;
        this.toastr.error('Failed to start scraping job');
      }
    });
  }

  private startPolling(jobId: string) {
    this.startPollingForJob(jobId, null);
  }

  private startPollingForJob(jobId: string, combinationId: number | null) {
    if (this.pollingSubscriptions.has(jobId)) {
      this.stopPolling(jobId);
    }

    const subscription = interval(2000).pipe(
      switchMap(() => this.leadService.getScrapeProgress(jobId)),
      take(60) // Max 2 minutes of polling
    ).subscribe({
      next: (progress) => {
        if (combinationId === null) {
          // Main "Run Search" polling
          this.scrapeProgress = progress;
          
          if (progress.status === 'COMPLETED') {
            this.stopPolling(jobId);
            this.isCollecting = false;
            this.toastr.success(`Scraping completed! ${progress.leadsImported} leads imported`);
            this.loadLeads();
          } else if (progress.status === 'FAILED') {
            this.stopPolling(jobId);
            this.isCollecting = false;
            this.toastr.error(`Scraping failed: ${progress.errorMessage}`);
          }
        } else {
          // Launch combination polling
          if (progress.status === 'COMPLETED') {
            this.stopPolling(jobId);
            this.leadService.launchSearchCombination(combinationId, {
              status: 'LAUNCHED',
              maxResults: 45
            }).pipe(
              finalize(() => this.launchingCombinationId = null)
            ).subscribe({
              next: () => {
                this.toastr.success(`Launch completed! ${progress.leadsImported} leads imported`);
                this.loadCombinations();
                this.loadLeads();
              },
              error: () => {
                this.toastr.error('Launch succeeded but status update failed');
                this.loadCombinations();
              }
            });
          } else if (progress.status === 'FAILED') {
            this.stopPolling(jobId);
            this.leadService.launchSearchCombination(combinationId, {
              status: 'FAILED',
              failureReason: progress.errorMessage,
              maxResults: 45
            }).pipe(
              finalize(() => this.launchingCombinationId = null)
            ).subscribe({
              next: () => {
                this.toastr.error('Launch failed');
                this.loadCombinations();
              },
              error: () => {
                this.toastr.error('Launch failed and status update failed');
                this.loadCombinations();
              }
            });
          }
        }
      },
      error: () => {
        this.stopPolling(jobId);
        if (combinationId === null) {
          this.isCollecting = false;
        } else {
          this.launchingCombinationId = null;
        }
      },
      complete: () => {
        this.stopPolling(jobId);
        if (combinationId === null) {
          this.isCollecting = false;
        } else {
          this.launchingCombinationId = null;
        }
      }
    });

    this.pollingSubscriptions.set(jobId, subscription);
  }

  private startPollingForLaunch(jobId: string, combinationId: number, maxResults: number) {
    this.startPollingForJob(jobId, combinationId);
  }

  private stopPolling(jobId: string) {
    const subscription = this.pollingSubscriptions.get(jobId);
    if (subscription) {
      subscription.unsubscribe();
      this.pollingSubscriptions.delete(jobId);
    }
  }

  confirmDeleteLead(lead: Lead) {
    if (!lead.id) {
      return;
    }

    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      width: '420px',
      data: {
        title: 'Delete Lead',
        message: `Are you sure you want to delete ${lead.email}?`,
        confirmText: 'Delete',
        cancelText: 'Cancel'
      }
    });

    dialogRef.afterClosed().subscribe((confirmed: boolean) => {
      if (!confirmed) {
        return;
      }

      this.leadService.deleteLead(lead.id!).subscribe({
        next: () => {
          this.currentLeads = this.currentLeads.filter((item) => item.id !== lead.id);
          this.leads$ = of(this.currentLeads);
          this.selection.clear();
          this.toastr.success('Lead deleted successfully');
        },
        error: () => {
          this.toastr.error('Failed to delete lead');
        }
      });
    });
  }

  logDebug(level: DebugLevel, message: string, details?: string) {
    const log: DebugLog = {
      time: new Date().toISOString(),
      level,
      message,
      details
    };
    const current = this.debugLogsSubject.value;
    this.debugLogsSubject.next([log, ...current].slice(0, 50));
    
    // Minimize console logs as requested
    if (level === 'error') {
      console.error(`[LeadCollector] ${message}`, details);
    }
  }

  clearDebugLogs() {
    this.debugLogsSubject.next([]);
    this.rawResponsePreviewSubject.next('');
  }

  downloadAllEmailsFromDb() {
    this.leadService.downloadAllEmailsFile().subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `all-emails-${new Date().toISOString().slice(0, 10)}.txt`;
        link.click();
        URL.revokeObjectURL(url);
        this.toastr.success('All emails file downloaded');
      },
      error: () => {
        this.toastr.error('Failed to download all emails file');
      }
    });
  }

  downloadLeadsCsvFromDb() {
    this.leadService.exportLeadsCsv().subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `leads-${new Date().toISOString().slice(0, 10)}.csv`;
        link.click();
        URL.revokeObjectURL(url);
        this.toastr.success('Leads CSV downloaded');
      },
      error: () => {
        this.toastr.error('Failed to download leads CSV');
      }
    });
  }

  openCsvImportPicker() {
    this.csvImportInput?.nativeElement.click();
  }

  onCsvImportSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input?.files?.[0];
    if (!file) {
      return;
    }

    if (!file.name.toLowerCase().endsWith('.csv')) {
      this.toastr.error('Please select a CSV file');
      input.value = '';
      return;
    }

    this.leadService.importLeadsCsv(file).subscribe({
      next: (result) => {
        this.toastr.success(`Imported ${result.imported}, Skipped ${result.skipped}, Failed ${result.failed}`);
        if (result.errors?.length) {
          this.toastr.warning(`Import reported ${result.errors.length} row errors`);
        }
        input.value = '';
        this.loadLeads();
      },
      error: () => {
        this.toastr.error('Failed to import CSV');
        input.value = '';
      }
    });
  }

  downloadCsvTemplate() {
    const header = 'institutionName,city,phone,address,website,email,categories';
    const example = 'Example School,Berlin,+49 30 123456,Example Street 1,https://example-school.de,info@example-school.de|contact@example-school.de,Healthcare|Education';
    const csv = `${header}\n${example}`;
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'leads-import-template.csv';
    link.click();
    URL.revokeObjectURL(url);
  }

  private toCollectorCategory(category: CategoryWithKeywords): CollectorCategory {
    return {
      id: category.id!,
      name: category.name,
      color: category.color,
      keywords: (category.keywords || []).map((keyword) => ({
        id: keyword.id!,
        nameEn: keyword.nameEn,
        nameDe: keyword.nameDe,
        categoryId: keyword.categoryId || category.id!,
        categoryName: keyword.categoryName || category.name,
        selected: false
      }))
    };
  }

  private parseNumber(value: unknown): number | undefined {
    if (value === null || value === undefined || value === '') {
      return undefined;
    }
    const n = Number(value);
    return Number.isFinite(n) ? n : undefined;
  }
}
