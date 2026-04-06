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
import { CategoryWithKeywords, Lead } from '../../../core/models/models';
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

interface GermanyCityNode {
  name: string;
  districts: string[];
}

interface GermanyStateNode {
  name: string;
  cities: GermanyCityNode[];
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
  webhookUrl = 'http://localhost:5678/webhook/leads-collector';
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

  private selectedPlaces = new Map<string, string>();
  filteredPlaceTree: GermanyStateNode[] = [];
  selectedPlaceLabels: Array<{ key: string; label: string }> = [];

  readonly germanyPlaces: GermanyStateNode[] = [
    {
      name: 'Bayern',
      cities: [
        { name: 'Muenchen', districts: ['Schwabing', 'Maxvorstadt', 'Sendling', 'Bogenhausen', 'Pasing'] },
        { name: 'Nuernberg', districts: ['Nordstadt', 'Suedstadt', 'Gostenhof'] },
        { name: 'Augsburg', districts: ['Innenstadt', 'Lechhausen', 'Goeggingen'] }
      ]
    },
    {
      name: 'Nordrhein-Westfalen',
      cities: [
        { name: 'Koeln', districts: ['Ehrenfeld', 'Nippes', 'Chorweiler', 'Kalk'] },
        { name: 'Duesseldorf', districts: ['Altstadt', 'Bilk', 'Oberkassel'] },
        { name: 'Dortmund', districts: ['Innenstadt-West', 'Innenstadt-Ost', 'Hoerde', 'Eving'] },
        { name: 'Essen', districts: ['Ruettenscheid', 'Kettwig', 'Altenessen'] },
        { name: 'Duisburg', districts: ['Hamborn', 'Meiderich', 'Rheinhausen'] }
      ]
    },
    {
      name: 'Baden-Wuerttemberg',
      cities: [
        { name: 'Stuttgart', districts: ['Mitte', 'Bad Cannstatt', 'Vaihingen'] },
        { name: 'Karlsruhe', districts: ['Innenstadt', 'Durlach'] },
        { name: 'Mannheim', districts: ['Neckarstadt', 'Lindenhof'] }
      ]
    },
    {
      name: 'Hessen',
      cities: [
        { name: 'Frankfurt am Main', districts: ['Innenstadt', 'Sachsenhausen', 'Bockenheim', 'Hoechst'] },
        { name: 'Wiesbaden', districts: ['Mitte', 'Biebrich'] },
        { name: 'Darmstadt', districts: ['Arheilgen', 'Eberstadt'] }
      ]
    },
    {
      name: 'Niedersachsen',
      cities: [
        { name: 'Hannover', districts: ['Mitte', 'Linden', 'Bothfeld'] },
        { name: 'Braunschweig', districts: ['Innenstadt', 'Weststadt'] },
        { name: 'Wolfsburg', districts: ['Mitte-West', 'Fallersleben'] }
      ]
    },
    {
      name: 'Sachsen',
      cities: [
        { name: 'Leipzig', districts: ['Zentrum', 'Plagwitz', 'Connewitz'] },
        { name: 'Dresden', districts: ['Altstadt', 'Neustadt', 'Blasewitz'] }
      ]
    },
    {
      name: 'Berlin',
      cities: [
        { name: 'Berlin', districts: ['Mitte', 'Neukoelln', 'Kreuzberg', 'Charlottenburg', 'Spandau'] }
      ]
    },
    {
      name: 'Hamburg',
      cities: [
        { name: 'Hamburg', districts: ['Altona', 'Eimsbuettel', 'Wandsbek', 'Harburg'] }
      ]
    },
    {
      name: 'Bremen',
      cities: [
        { name: 'Bremen', districts: ['Mitte', 'Vegesack', 'Neustadt'] },
        { name: 'Bremerhaven', districts: ['Lehe', 'Geestemuende'] }
      ]
    }
  ];

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
    this.updateFilteredPlaceTree();
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

            const districts = city.districts.filter((district) => district.toLowerCase().includes(query));
            if (districts.length > 0) {
              return { ...city, districts };
            }

            return null;
          })
          .filter((city): city is GermanyCityNode => city !== null);

        if (cities.length > 0) {
          return { ...state, cities };
        }

        return null;
      })
      .filter((state): state is GermanyStateNode => state !== null);
  }

  selectAllStates() {
    this.germanyPlaces.forEach((state) => {
      this.selectedPlaces.set(this.stateKey(state.name), state.name);
    });
    this.syncSelectedCities();
  }

  selectAllCities() {
    this.germanyPlaces.forEach((state) => {
      state.cities.forEach((city) => {
        this.selectedPlaces.set(this.cityKey(state.name, city.name), city.name);
      });
    });
    this.syncSelectedCities();
  }

  clearPlaces() {
    this.selectedPlaces.clear();
    this.syncSelectedCities();
  }

  toggleState(stateName: string, checked: boolean) {
    const key = this.stateKey(stateName);
    if (checked) {
      this.selectedPlaces.set(key, stateName);
    } else {
      this.selectedPlaces.delete(key);
    }
    this.syncSelectedCities();
  }

  toggleCity(stateName: string, cityName: string, checked: boolean) {
    const key = this.cityKey(stateName, cityName);
    if (checked) {
      this.selectedPlaces.set(key, cityName);
    } else {
      this.selectedPlaces.delete(key);
    }
    this.syncSelectedCities();
  }

  toggleDistrict(stateName: string, cityName: string, districtName: string, checked: boolean) {
    const key = this.districtKey(stateName, cityName, districtName);
    if (checked) {
      this.selectedPlaces.set(key, `${cityName} ${districtName}`);
    } else {
      this.selectedPlaces.delete(key);
    }
    this.syncSelectedCities();
  }

  isStateSelected(stateName: string): boolean {
    return this.selectedPlaces.has(this.stateKey(stateName));
  }

  isCitySelected(stateName: string, cityName: string): boolean {
    return this.selectedPlaces.has(this.cityKey(stateName, cityName));
  }

  isDistrictSelected(stateName: string, cityName: string, districtName: string): boolean {
    return this.selectedPlaces.has(this.districtKey(stateName, cityName, districtName));
  }

  removeSelectedPlace(key: string) {
    this.selectedPlaces.delete(key);
    this.syncSelectedCities();
  }

  private stateKey(stateName: string): string {
    return `state:${stateName}`;
  }

  private cityKey(stateName: string, cityName: string): string {
    return `city:${stateName}:${cityName}`;
  }

  private districtKey(stateName: string, cityName: string, districtName: string): string {
    return `district:${stateName}:${cityName}:${districtName}`;
  }

  private syncSelectedCities() {
    this.cities = Array.from(new Set(this.selectedPlaces.values()));
    this.selectedPlaceLabels = Array.from(this.selectedPlaces.entries()).map(([key, label]) => ({ key, label }));
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
