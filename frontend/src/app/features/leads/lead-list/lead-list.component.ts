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
import { ToastrService } from 'ngx-toastr';
import { BehaviorSubject, Observable, finalize, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { CategoryService } from '../../../core/services/category.service';
import { LeadService } from '../../../core/services/lead.service';
import {
  CategoryWithKeywords,
  Lead,
  PlaceCountryTree,
  PlaceStateTree,
  PlaceCityTree,
  PlaceDistrictTree,
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

interface WebhookLeadResult {
  title?: string;
  institution?: string;
  city?: string;
  phone?: string;
  website?: string;
  address?: string;
  email?: string;
  allEmails?: string[];
  emails?: string[];
  latitude?: number;
  longitude?: number;
  lat?: number;
  lng?: number;
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
    MatExpansionModule, MatDialogModule
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
  private collectedResultsSubject = new BehaviorSubject<WebhookLeadResult[]>([]);
  collectedResults$ = this.collectedResultsSubject.asObservable();
  isSaving = false;
  private lastSelectedCategoryIds: number[] = [];
  private lastSelectedCategoryNames: string[] = [];
  
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
    const webhookUrl = this.webhookUrl.trim();
    if (!webhookUrl) {
      this.toastr.warning('Webhook URL is required to launch a combination');
      return;
    }

    const launchMaxResults = 45;
    const keywordName = combination.keywordNameDe || combination.keywordNameEn;
    const payload = {
      cities: [combination.placeDisplayName],
      keywords: [{ name: keywordName, categoryId: combination.categoryId }],
      maxResults: launchMaxResults
    };

    this.launchingCombinationId = combination.id;
    this.leadService.collectFromWebhook(webhookUrl, payload).subscribe({
      next: (response) => {
        this.lastSelectedCategoryIds = [combination.categoryId];
        this.lastSelectedCategoryNames = [combination.categoryName];

        try {
          const body = typeof response.body === 'string' ? JSON.parse(response.body) : response.body;
          const results = this.extractResults(body);
          this.collectedResultsSubject.next(results);
          this.toastr.success(`Found ${results.length} potential leads`);
        } catch (e) {
          this.logDebug('error', 'Failed to parse launch response body', String(e));
          this.rawResponsePreviewSubject.next(String(response.body));
        }

        this.leadService.launchSearchCombination(combination.id, {
          status: 'LAUNCHED',
          maxResults: launchMaxResults
        }).pipe(
          finalize(() => this.launchingCombinationId = null)
        ).subscribe({
          next: () => {
            this.toastr.success('Combination launched and results loaded');
            this.loadCombinations();
          },
          error: () => {
            this.toastr.error('Webhook succeeded but status update failed');
            this.loadCombinations();
          }
        });
      },
      error: (err) => {
        const failureReason = err?.message || 'Webhook request failed';
        this.leadService.launchSearchCombination(combination.id, {
          status: 'FAILED',
          failureReason,
          maxResults: launchMaxResults
        }).pipe(
          finalize(() => this.launchingCombinationId = null)
        ).subscribe({
          next: () => {
            this.toastr.error('Failed to launch combination');
            this.loadCombinations();
          },
          error: () => {
            this.toastr.error('Failed to launch combination and failed to update status');
            this.loadCombinations();
          }
        });
      }
    });
  }

  toggleDebug() {
    this.showDebug = !this.showDebug;
  }

  // Existing Leads Table state
  leads$: Observable<Lead[]> = of([]);
  displayedColumns: string[] = ['select', 'email', 'institution', 'city', 'phone', 'coordinates', 'address', 'status', 'website', 'actions'];
  collectedColumns: string[] = ['institution', 'city', 'phone', 'coordinates', 'emails', 'address', 'website'];
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
    const webhookUrl = this.webhookUrl.trim();
    if (!webhookUrl || this.cities.length === 0 || this.selectedKeywords.length === 0) {
      this.toastr.warning('Please provide Webhook URL, at least one city and one keyword');
      return;
    }

    this.lastSelectedCategoryIds = this.selectedCategoryIds;
    this.lastSelectedCategoryNames = this.selectedCategoryNames;
    this.isCollecting = true;
    this.collectedResultsSubject.next([]);
    this.logDebug('info', 'Starting collection', `Cities: ${this.cities.join(', ')} | Keywords: ${this.selectedKeywordNames.join(', ')}`);

    this.leadService.collectFromWebhook(webhookUrl, {
      cities: this.cities,
      keywords: this.selectedKeywords.map((keyword) => ({
        name: keyword.nameDe || keyword.nameEn,
        categoryId: keyword.categoryId
      })),
      maxResults: this.maxResults
    }).pipe(
      finalize(() => this.isCollecting = false)
    ).subscribe({
      next: (response) => {
        this.logDebug('success', 'Webhook response received', `Status: ${response.status}`);
        try {
          const body = typeof response.body === 'string' ? JSON.parse(response.body) : response.body;
          const results = this.extractResults(body);
          this.collectedResultsSubject.next(results);
          this.toastr.success(`Found ${results.length} potential leads`);
        } catch (e) {
          this.logDebug('error', 'Failed to parse response body', String(e));
          this.rawResponsePreviewSubject.next(String(response.body));
        }
      },
      error: (err) => {
        this.logDebug('error', 'Webhook request failed', err.message);
        this.toastr.error('Failed to collect leads from webhook');
      }
    });
  }

  saveCollectedLeads() {
    const results = this.collectedResultsSubject.value;
    if (!results.length) return;

    this.isSaving = true;
    const leadsToSave: Lead[] = this.expandWebhookResultsToLeads(results, this.lastSelectedCategoryIds, this.lastSelectedCategoryNames);

    if (leadsToSave.length === 0) {
      this.isSaving = false;
      this.toastr.warning('No valid emails found in webhook output');
      return;
    }

    this.leadService.bulkImport(leadsToSave).pipe(
      finalize(() => this.isSaving = false)
    ).subscribe({
      next: (response) => {
        this.toastr.success(`Saved ${response.saved.length} lead(s)`);
        if (response.errors.length > 0) {
          this.toastr.warning(`${response.errors.length} lead(s) failed during import`);
        }
        this.collectedResultsSubject.next([]);
        this.loadLeads();
      },
      error: (err) => {
        this.toastr.error('Failed to save leads to database');
      }
    });
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

  getInstitutionName(result: WebhookLeadResult): string {
    return result.title || result.institution || 'Unknown Institution';
  }

  getEmailsForDisplay(result: WebhookLeadResult): string[] {
    const emails = new Set<string>();
    if (result.email) {
      emails.add(result.email.trim().toLowerCase());
    }
    (result.emails || []).forEach((email) => {
      if (email && email.trim()) {
        emails.add(email.trim().toLowerCase());
      }
    });
    (result.allEmails || []).forEach((email) => {
      if (email && email.trim()) {
        emails.add(email.trim().toLowerCase());
      }
    });
    return Array.from(emails);
  }

  getCoordinatesText(result: WebhookLeadResult): string {
    const lat = this.parseNumber(result.latitude ?? result.lat);
    const lng = this.parseNumber(result.longitude ?? result.lng);
    if (lat === undefined || lng === undefined) {
      return '—';
    }
    return `${lat.toFixed(6)}, ${lng.toFixed(6)}`;
  }

  private extractResults(body: any): WebhookLeadResult[] {
    if (Array.isArray(body)) {
      return body;
    }
    if (Array.isArray(body?.results)) {
      return body.results;
    }
    if (Array.isArray(body?.data)) {
      return body.data;
    }
    if (Array.isArray(body?.items)) {
      return body.items;
    }
    if (Array.isArray(body?.leads)) {
      return body.leads;
    }
    return [];
  }

  private expandWebhookResultsToLeads(results: WebhookLeadResult[], categoryIds: number[], categoryNames: string[]): Lead[] {
    const byInstitution = new Map<string, { lead: Lead; emails: Set<string> }>();

    results.forEach((result) => {
      const emails = this.getEmailsForDisplay(result);
      if (emails.length === 0) {
        return;
      }

      const institutionName = this.getInstitutionName(result);
      const city = result.city || this.cities[0] || 'Unknown City';
      const latitude = this.parseNumber(result.latitude ?? result.lat);
      const longitude = this.parseNumber(result.longitude ?? result.lng);

      const key = [
        institutionName.trim().toLowerCase(),
        (city || '').trim().toLowerCase(),
        (result.website || '').trim().toLowerCase(),
        (result.address || '').trim().toLowerCase()
      ].join('|');

      const existing = byInstitution.get(key);
      if (!existing) {
        byInstitution.set(key, {
          lead: {
            email: emails[0],
            primaryEmail: emails[0],
            emails: [...emails],
            institutionName,
            city,
            phone: result.phone || '',
            address: result.address || '',
            latitude,
            longitude,
            website: result.website || '',
            source: 'Webhook Collector',
            categoryIds,
            categoryNames
          },
          emails: new Set(emails)
        });
        return;
      }

      emails.forEach((email) => existing.emails.add(email));
      existing.lead.email = Array.from(existing.emails)[0];
      existing.lead.primaryEmail = Array.from(existing.emails)[0];
      existing.lead.emails = Array.from(existing.emails);
      if (!existing.lead.phone && result.phone) existing.lead.phone = result.phone;
      if (!existing.lead.address && result.address) existing.lead.address = result.address;
      if (existing.lead.latitude == null && latitude != null) existing.lead.latitude = latitude;
      if (existing.lead.longitude == null && longitude != null) existing.lead.longitude = longitude;
      if (!existing.lead.website && result.website) existing.lead.website = result.website;
    });

    return Array.from(byInstitution.values()).map((entry) => entry.lead);
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
