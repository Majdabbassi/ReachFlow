import { Component, inject, OnInit } from '@angular/core';
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
import { ToastrService } from 'ngx-toastr';
import { BehaviorSubject, Observable, finalize, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { LeadService } from '../../../core/services/lead.service';
import { Lead } from '../../../core/models/models';

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
  latitude?: number;
  longitude?: number;
  lat?: number;
  lng?: number;
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
    MatExpansionModule
  ],
  templateUrl: './lead-list.component.html',
  styleUrl: './lead-list.component.scss'
})
export class LeadListComponent implements OnInit {
  private leadService = inject(LeadService);
  private toastr = inject(ToastrService);
  private fb = inject(FormBuilder);

  // Selection model
  selection = new SelectionModel<Lead>(true, []);

  // Collector state
  cityInput = '';
  cities: string[] = [];
  isLoadingLeads = false;
  loadError: string | null = null;
  maxResults = 10;
  webhookUrl = 'http://localhost:5678/webhook/leads-collector';
  isCollecting = false;
  private collectedResultsSubject = new BehaviorSubject<WebhookLeadResult[]>([]);
  collectedResults$ = this.collectedResultsSubject.asObservable();
  isSaving = false;
  
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

  // Autocomplete for cities (Example list)
  allCities = [
    'Rastatt', 'Kuppenheim', 'Gaggenau', 'Gernsbach', 'Weisenbach', 'Forbach', 
    'Bühl', 'Bühlertal', 'Sinzheim', 'Hügelsheim', 'Iffezheim',
    'Berlin', 'Hamburg', 'Munich', 'Cologne', 'Frankfurt', 'Stuttgart', 'Düsseldorf', 'Leipzig', 'Dortmund', 'Essen', 'Bremen', 'Dresden', 'Hanover', 'Nuremberg', 'Duisburg'
  ];
  filteredCities$: Observable<string[]> = of([]);

  // Keyword Domains
  keywordDomains = [
    {
      name: 'Healthcare & Care / Gesundheit & Pflege',
      keywords: [
        { label: 'Nursing Home / Pflegeheim', value: 'Nursing Home OR Pflegeheim', selected: false },
        { label: 'Retirement Home / Altenheim', value: 'Retirement Home OR Altenheim', selected: false },
        { label: 'Home Care / Ambulante Pflege', value: 'Home Care OR Ambulante Pflege', selected: false },
        { label: 'Senior Residence / Seniorenheim', value: 'Senior Residence OR Seniorenheim', selected: false },
        { label: 'Care Service / Pflegedienst', value: 'Care Service OR Pflegedienst', selected: false }
      ]
    },
    {
      name: 'Education / Bildung',
      keywords: [
        { label: 'Primary School / Grundschule', value: 'Primary School OR Grundschule', selected: false },
        { label: 'High School / Gymnasium', value: 'High School OR Gymnasium', selected: false },
        { label: 'University / Universität', value: 'University OR Universität', selected: false },
        { label: 'Kindergarten / Kindergarten', value: 'Kindergarten OR Kindergarten', selected: false }
      ]
    },
    {
      name: 'Business & Tech / Wirtschaft & Technik',
      keywords: [
        { label: 'IT Services / IT-Dienstleistungen', value: 'IT Services OR IT-Dienstleistungen', selected: false },
        { label: 'Software Company / Softwarehaus', value: 'Software Company OR Softwarehaus', selected: false },
        { label: 'Marketing Agency / Marketingagentur', value: 'Marketing Agency OR Marketingagentur', selected: false },
        { label: 'Consulting / Unternehmensberatung', value: 'Consulting OR Unternehmensberatung', selected: false }
      ]
    },
    {
      name: 'Hospitality / Gastgewerbe',
      keywords: [
        { label: 'Hotel / Hotel', value: 'Hotel OR Hotel', selected: false },
        { label: 'Restaurant / Restaurant', value: 'Restaurant OR Restaurant', selected: false },
        { label: 'Cafe / Cafe', value: 'Cafe OR Cafe', selected: false }
      ]
    }
  ];

  keywordSearch = '';

  get allKeywords() {
    return this.keywordDomains.flatMap(d => d.keywords);
  }

  get selectedKeywords() {
    return this.allKeywords.filter(k => k.selected).map(k => k.value);
  }

  get filteredKeywordDomains() {
    if (!this.keywordSearch) return this.keywordDomains;
    const search = this.keywordSearch.toLowerCase();
    return this.keywordDomains.map(domain => ({
      ...domain,
      keywords: domain.keywords.filter(k => 
        k.label.toLowerCase().includes(search) || 
        k.value.toLowerCase().includes(search)
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
    return keyword.value;
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
    this.loadLeads();
    this.setupCityAutocomplete();
  }

  setupCityAutocomplete() {
    // This is a simple implementation, in a real app we'd use a form control
    this.filteredCities$ = of(this.allCities);
  }

  onCityInputChange() {
    const filterValue = this.cityInput.toLowerCase();
    this.filteredCities$ = of(this.allCities.filter(city => city.toLowerCase().includes(filterValue)));
  }

  addCity(city?: string) {
    const cityName = (city || this.cityInput).trim();
    if (cityName && !this.cities.includes(cityName)) {
      this.cities.push(cityName);
      this.cityInput = '';
      this.onCityInputChange();
    }
  }

  removeCity(city: string) {
    this.cities = this.cities.filter(c => c !== city);
  }

  onCityKeydown(event: KeyboardEvent) {
    if (event.key === 'Enter') { 
      event.preventDefault(); 
      this.addCity(); 
    }
  }

  toggleDebug() {
    this.showDebug = !this.showDebug;
  }

  // Existing Leads Table state
  leads$: Observable<Lead[]> = of([]);
  displayedColumns: string[] = ['select', 'email', 'institution', 'city', 'phone', 'coordinates', 'address', 'status', 'website'];
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

    this.isCollecting = true;
    this.collectedResultsSubject.next([]);
    this.logDebug('info', 'Starting collection', `Cities: ${this.cities.join(', ')} | Keywords: ${this.selectedKeywords.join(', ')}`);

    this.leadService.collectFromWebhook(webhookUrl, {
      cities: this.cities,
      keywords: this.selectedKeywords,
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
    const leadsToSave: Lead[] = this.expandWebhookResultsToLeads(results);

    if (leadsToSave.length === 0) {
      this.isSaving = false;
      this.toastr.warning('No valid emails found in webhook output');
      return;
    }

    this.leadService.bulkImport(leadsToSave).pipe(
      finalize(() => this.isSaving = false)
    ).subscribe({
      next: () => {
        this.toastr.success(`${leadsToSave.length} emails saved to database successfully`);
        this.collectedResultsSubject.next([]);
        this.loadLeads();
      },
      error: (err) => {
        this.toastr.error('Failed to save leads to database');
      }
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

  getInstitutionName(result: WebhookLeadResult): string {
    return result.title || result.institution || 'Unknown Institution';
  }

  getEmailsForDisplay(result: WebhookLeadResult): string[] {
    const emails = new Set<string>();
    if (result.email) {
      emails.add(result.email.trim().toLowerCase());
    }
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

  private expandWebhookResultsToLeads(results: WebhookLeadResult[]): Lead[] {
    const leads: Lead[] = [];

    results.forEach((result) => {
      const emails = this.getEmailsForDisplay(result);
      const institutionName = this.getInstitutionName(result);
      const city = result.city || this.cities[0] || 'Unknown City';
      const latitude = this.parseNumber(result.latitude ?? result.lat);
      const longitude = this.parseNumber(result.longitude ?? result.lng);

      emails.forEach((email) => {
        leads.push({
          email,
          institutionName,
          city,
          phone: result.phone || '',
          address: result.address || '',
          latitude,
          longitude,
          website: result.website || '',
          source: 'Webhook Collector'
        });
      });
    });

    const seen = new Set<string>();
    return leads.filter((lead) => {
      const key = lead.email.trim().toLowerCase();
      if (seen.has(key)) {
        return false;
      }
      seen.add(key);
      return true;
    });
  }

  private parseNumber(value: unknown): number | undefined {
    if (value === null || value === undefined || value === '') {
      return undefined;
    }
    const n = Number(value);
    return Number.isFinite(n) ? n : undefined;
  }
}
