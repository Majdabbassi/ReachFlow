import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipInputEvent, MatChipsModule } from '@angular/material/chips';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { finalize } from 'rxjs';
import { AusbildungResult, AusbildungSearchRequest, CategoryWithKeywords, PlaceCountryTree, PlaceStateTree, PlaceCityTree, PlaceDistrictTree, Keyword } from '../../../core/models/models';
import { AusbildungService } from '../../../core/services/ausbildung.service';
import { LeadService } from '../../../core/services/lead.service';
import { CategoryService } from '../../../core/services/category.service';
import { ToastrService } from 'ngx-toastr';

interface RawAusbildungResult {
  applyUrl?: string;
  jobTitle?: string;
  company?: string;
  emails?: string[];
  // Backward-compat fields from old webhook shape
  url?: string;
}

interface RawAusbildungResponse {
  results?: RawAusbildungResult[];
  total?: number;
  message?: string;
}

type UiAusbildungResult = AusbildungResult & {
  sourceUrl?: string;
};

@Component({
  selector: 'app-ausbildung-finder',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatChipsModule,
    MatCheckboxModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatBadgeModule,
    MatExpansionModule
  ],
  templateUrl: './ausbildung-finder.component.html',
  styleUrl: './ausbildung-finder.component.scss'
})
export class AusbildungFinderComponent implements OnInit {
  private ausbildungService = inject(AusbildungService);
  private leadService = inject(LeadService);
  private categoryService = inject(CategoryService);
  private snackBar = inject(MatSnackBar);
  private toastr = inject(ToastrService);

  readonly separatorKeysCodes = [13, 188]; // Enter, Comma

  // Keywords
  keywords: string[] = ['ausbildung'];
  keywordInput = '';

  // Places
  private selectedPlaces = new Map<string, { key: string; label: string }>();
  placeCountries: PlaceCountryTree[] = [];
  germanyPlaces: PlaceStateTree[] = [];
  filteredPlaceTree: PlaceStateTree[] = [];
  selectedPlaceLabels: Array<{ key: string; label: string }> = [];
  placeSearch = '';
  placesLoaded = false;

  // Categories/Keywords
  categoryDomains: CategoryWithKeywords[] = [];
  selectedCategoryIds = new Set<number>();
  categorySearch = '';
  categoriesLoaded = false;

  maxResults = 10;
  isLoading = false;
  hasSearched = false;
  total = 0;
  results: UiAusbildungResult[] = [];
  errorMessage = '';
  
  // Progress tracking
  searchProgress: { message: string; status: 'idle' | 'searching' | 'completed' | 'failed' } = { message: '', status: 'idle' };

  ngOnInit() {
    this.initializePlaces();
    this.loadCategories();
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
        this.toastr.error('Failed to load places tree');
      }
    });
  }

  loadCategories() {
    this.categoryService.getCategories().subscribe({
      next: (categories) => {
        this.categoryDomains = categories;
        this.categoriesLoaded = true;
      },
      error: () => {
        this.toastr.error('Failed to load categories');
        this.categoriesLoaded = true;
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

        const filteredCities = state.cities
          .map((city) => {
            const cityMatch = city.name.toLowerCase().includes(query);
            if (cityMatch) {
              return city;
            }

            const filteredDistricts = city.districts?.filter((d) =>
              d.name.toLowerCase().includes(query)
            ) || [];

            return filteredDistricts.length > 0 ? { ...city, districts: filteredDistricts } : null;
          })
          .filter((city): city is PlaceCityTree => city !== null);

        return filteredCities.length > 0 ? { ...state, cities: filteredCities } : null;
      })
      .filter((state): state is PlaceStateTree => state !== null);
  }

  toggleState(state: PlaceStateTree, checked: boolean) {
    if (checked) {
      this.selectedPlaces.set(`state-${state.id}`, { key: `state-${state.id}`, label: state.name });
      state.cities.forEach((city) => {
        this.selectedPlaces.set(`city-${city.id}`, { key: `city-${city.id}`, label: city.name });
        city.districts?.forEach((district) => {
          this.selectedPlaces.set(`district-${district.id}`, { key: `district-${district.id}`, label: district.name });
        });
      });
    } else {
      this.selectedPlaces.delete(`state-${state.id}`);
      state.cities.forEach((city) => {
        this.selectedPlaces.delete(`city-${city.id}`);
        city.districts?.forEach((district) => {
          this.selectedPlaces.delete(`district-${district.id}`);
        });
      });
    }
    this.updateSelectedPlaceLabels();
  }

  toggleCity(city: PlaceCityTree, checked: boolean) {
    if (checked) {
      this.selectedPlaces.set(`city-${city.id}`, { key: `city-${city.id}`, label: city.name });
      city.districts?.forEach((district) => {
        this.selectedPlaces.set(`district-${district.id}`, { key: `district-${district.id}`, label: district.name });
      });
    } else {
      this.selectedPlaces.delete(`city-${city.id}`);
      city.districts?.forEach((district) => {
        this.selectedPlaces.delete(`district-${district.id}`);
      });
    }
    this.updateSelectedPlaceLabels();
  }

  toggleDistrict(city: PlaceCityTree, district: PlaceDistrictTree, checked: boolean) {
    if (checked) {
      this.selectedPlaces.set(`district-${district.id}`, { key: `district-${district.id}`, label: district.name });
    } else {
      this.selectedPlaces.delete(`district-${district.id}`);
    }
    this.updateSelectedPlaceLabels();
  }

  isStateSelected(stateId: number): boolean {
    return this.selectedPlaces.has(`state-${stateId}`);
  }

  isCitySelected(cityId: number): boolean {
    return this.selectedPlaces.has(`city-${cityId}`);
  }

  isDistrictSelected(districtId: number): boolean {
    return this.selectedPlaces.has(`district-${districtId}`);
  }

  removeSelectedPlace(key: string) {
    this.selectedPlaces.delete(key);
    this.updateSelectedPlaceLabels();
  }

  clearPlaces() {
    this.selectedPlaces.clear();
    this.updateSelectedPlaceLabels();
  }

  selectAllPlaces() {
    this.germanyPlaces.forEach((state) => this.toggleState(state, true));
  }

  private updateSelectedPlaceLabels() {
    this.selectedPlaceLabels = Array.from(this.selectedPlaces.values());
  }

  toggleCategory(keyword: Keyword) {
    if (keyword.categoryId) {
      if (this.selectedCategoryIds.has(keyword.categoryId)) {
        this.selectedCategoryIds.delete(keyword.categoryId);
      } else {
        this.selectedCategoryIds.add(keyword.categoryId);
      }
    }
  }

  isCategorySelected(categoryId: number): boolean {
    return this.selectedCategoryIds.has(categoryId);
  }

  get filteredCategories() {
    if (!this.categorySearch) return this.categoryDomains;
    const search = this.categorySearch.toLowerCase();
    return this.categoryDomains.filter(cat => 
      cat.name.toLowerCase().includes(search)
    );
  }

  addKeyword(event: MatChipInputEvent): void {
    const value = (event.value || '').trim();
    if (!value) {
      return;
    }
    if (!this.keywords.some((k) => k.toLowerCase() === value.toLowerCase())) {
      this.keywords.push(value);
    }
    event.chipInput?.clear();
    this.keywordInput = '';
  }

  removeKeyword(keyword: string): void {
    this.keywords = this.keywords.filter((k) => k !== keyword);
  }

  search(): void {
    const request: AusbildungSearchRequest = {
      keywords: this.keywords,
      categories: Array.from(this.selectedCategoryIds).map(id => id.toString()),
      countries: this.selectedPlaceLabels.map(p => p.label),
      maxResults: Math.min(50, Math.max(1, Number(this.maxResults) || 10))
    };

    this.maxResults = request.maxResults;
    this.isLoading = true;
    this.errorMessage = '';
    this.searchProgress = { message: 'Searching Ausbildung opportunities...', status: 'searching' };

    this.ausbildungService.search(request)
      .pipe(finalize(() => {
        this.isLoading = false;
        this.hasSearched = true;
      }))
      .subscribe({
        next: (response) => {
          const normalized = this.normalizeResponse(response);
          this.results = normalized.results;
          this.total = normalized.total;
          this.searchProgress = { message: `Found ${normalized.total} opportunities`, status: 'completed' };
          this.toastr.success(`Found ${normalized.total} Ausbildung opportunities`);
        },
        error: (err) => {
          this.results = [];
          this.total = 0;
          this.errorMessage = err?.error?.message || err?.message || 'Failed to search Ausbildung opportunities.';
          this.searchProgress = { message: this.errorMessage, status: 'failed' };
          this.toastr.error('Failed to search Ausbildung opportunities');
        }
      });
  }

  copyEmail(email: string): void {
    navigator.clipboard.writeText(email)
      .then(() => {
        this.snackBar.open('Email copied!', 'Close', { duration: 2000 });
      })
      .catch(() => {
        this.snackBar.open('Could not copy email', 'Close', { duration: 2000 });
      });
  }

  private normalizeResponse(payload: unknown): { results: UiAusbildungResult[]; total: number } {
    const source = this.unwrapPayload(payload);
    const rawResults = source.results;
    const mapped = rawResults
      .map((item) => this.toUiResult(item))
      .filter((item) => item.applyUrl.length > 0);
    const total = source.total ?? mapped.length;
    return { results: mapped, total };
  }

  private unwrapPayload(payload: unknown): { results: RawAusbildungResult[]; total?: number } {
    if (Array.isArray(payload)) {
      if (payload.length === 0) {
        return { results: [] };
      }

      const first = payload[0] as unknown;
      if (first && typeof first === 'object' && Array.isArray((first as RawAusbildungResponse).results)) {
        const wrapped = first as RawAusbildungResponse;
        return {
          results: wrapped.results || [],
          total: wrapped.total
        };
      }

      return {
        results: payload as RawAusbildungResult[],
        total: payload.length
      };
    }

    if (payload && typeof payload === 'object') {
      const obj = payload as RawAusbildungResponse & RawAusbildungResult;
      if (Array.isArray(obj.results)) {
        return {
          results: obj.results,
          total: obj.total
        };
      }
      return {
        results: [obj],
        total: 1
      };
    }

    return { results: [] };
  }

  private toUiResult(raw: RawAusbildungResult): UiAusbildungResult {
    const applyUrl = (raw.applyUrl || raw.url || '').trim();
    const fallbackHost = this.extractHost(applyUrl);
    const jobTitle = (raw.jobTitle || '').trim() || 'Untitled Opportunity';
    const company = (raw.company || '').trim() || fallbackHost || 'Unknown Company';
    const emails = Array.isArray(raw.emails) ? raw.emails : [];

    const uniqueEmails = Array.from(
      new Set(
        emails
          .map((email) => (email || '').trim().toLowerCase())
          .filter((email) => email.length > 0)
      )
    );

    return {
      applyUrl,
      jobTitle,
      company,
      emails: uniqueEmails,
      sourceUrl: raw.url
    };
  }

  private extractHost(url: string): string {
    if (!url) {
      return '';
    }

    try {
      return new URL(url).hostname.replace(/^www\./, '');
    } catch {
      return '';
    }
  }
}
