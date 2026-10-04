import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { BehaviorSubject, finalize, interval, switchMap, take } from 'rxjs';
import { CategoryService } from '../../../core/services/category.service';
import { LeadService } from '../../../core/services/lead.service';
import {
  CategoryWithKeywords,
  PlaceCityTree,
  PlaceCountryTree,
  PlaceDistrictTree,
  PlaceStateTree,
  ScrapeProgress
} from '../../../core/models/models';
import { N8nSettingsService } from '../../../core/services/n8n-settings.service';

// One poll every 2 s: a little over the backend's 10-minute scraper timeout, so long jobs keep their progress UI.
const MAX_SCRAPE_POLLS = 330;

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

@Component({
  selector: 'app-lead-new-search',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatCheckboxModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatExpansionModule,
    MatTooltipModule
  ],
  templateUrl: './lead-new-search.component.html',
  styleUrl: './lead-new-search.component.scss'
})
export class LeadNewSearchComponent implements OnInit {
  private leadService = inject(LeadService);
  private categoryService = inject(CategoryService);
  private toastr = inject(ToastrService);
  private router = inject(Router);
  private n8nSettings = inject(N8nSettingsService);

  placeSearch = '';
  cities: string[] = [];
  maxResults = 10;
  webhookUrl = this.n8nSettings.getWebhookUrl();
  isCollecting = false;
  private lastSelectedCategoryIds: number[] = [];
  private lastSelectedCategoryNames: string[] = [];

  currentJobId: string | null = null;
  scrapeProgress: ScrapeProgress | null = null;
  private pollingSubscriptions: Map<string, any> = new Map();

  isGeneratingCombinations = false;

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

  keywordDomains: CollectorCategory[] = [];
  keywordSearch = '';

  ngOnInit() {
    this.loadCategories();
    this.initializePlaces();
    this.n8nSettings.webhookUrl$.subscribe((url) => (this.webhookUrl = url));
  }

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
        const toast = this.toastr.success(
          `Combinations created: ${response.created}, existing: ${response.existing}. <b style="cursor:pointer;text-decoration:underline">View Queue</b>`,
          'Combinations ready',
          { enableHtml: true, timeOut: 8000, tapToDismiss: true }
        );
        toast.onTap.subscribe(() => this.router.navigate(['/leads/combinations']));
      },
      error: () => {
        this.toastr.error('Failed to generate combinations');
      }
    });
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
      webhookUrl: this.n8nSettings.getWebhookUrl(),
      categoryIds: this.lastSelectedCategoryIds
    }).subscribe({
      next: (response) => {
        this.currentJobId = response.jobId;
        this.scrapeProgress = null;
        this.startPolling(response.jobId);
        this.toastr.success('Scraping job started');
      },
      error: () => {
        this.isCollecting = false;
        this.toastr.error('Failed to start scraping job');
      }
    });
  }

  private startPolling(jobId: string) {
    if (this.pollingSubscriptions.has(jobId)) {
      this.stopPolling(jobId);
    }

    const subscription = interval(2000).pipe(
      switchMap(() => this.leadService.getScrapeProgress(jobId)),
      take(MAX_SCRAPE_POLLS)
    ).subscribe({
      next: (progress) => {
        this.scrapeProgress = progress;

        if (progress.status === 'COMPLETED') {
          this.stopPolling(jobId);
          this.isCollecting = false;
          this.toastr.success(`Scraping completed! ${progress.leadsImported} leads imported`);
        } else if (progress.status === 'FAILED') {
          this.stopPolling(jobId);
          this.isCollecting = false;
          this.toastr.error(`Scraping failed: ${progress.errorMessage}`);
        }
      },
      error: () => {
        this.stopPolling(jobId);
        this.isCollecting = false;
      },
      complete: () => {
        this.stopPolling(jobId);
        this.isCollecting = false;
      }
    });

    this.pollingSubscriptions.set(jobId, subscription);
  }

  private stopPolling(jobId: string) {
    const subscription = this.pollingSubscriptions.get(jobId);
    if (subscription) {
      subscription.unsubscribe();
      this.pollingSubscriptions.delete(jobId);
    }
  }

  toggleDebug() {
    this.showDebug = !this.showDebug;
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

    if (level === 'error') {
      console.error(`[LeadCollector] ${message}`, details);
    }
  }

  clearDebugLogs() {
    this.debugLogsSubject.next([]);
    this.rawResponsePreviewSubject.next('');
  }

  onWebhookUrlBlur() {
    this.n8nSettings.setUrl(this.webhookUrl);
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
}