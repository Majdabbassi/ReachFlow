
import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  BulkImportResult,
  BulkLeadImportResponse,
  DeleteLeadEmailsResponse,
  EmailAuditItem,
  GenerateSearchCombinationsRequest,
  GenerateSearchCombinationsResponse,
  LaunchSearchCombinationRequest,
  Lead,
  PageResponse,
  PlaceCountryTree,
  SearchCombination,
  SearchCombinationStatus
} from '../models/models';

@Injectable({ providedIn: 'root' })
export class LeadService {
  private http = inject(HttpClient);
  private apiUrl = '/api/leads';
  private searchCombinationsApiUrl = '/api/search-combinations';

  getLeads(page = 0, size = 10, city?: string, source?: string): Observable<PageResponse<Lead>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    if (city) params = params.set('city', city);
    if (source) params = params.set('source', source);
    return this.http.get<PageResponse<Lead>>(this.apiUrl, { params });
  }

  collectFromWebhook(webhookUrl: string, payload: { cities: string[]; keywords: Array<{ name: string; categoryId: number }>; maxResults: number }): Observable<HttpResponse<string>> {
    return this.http.post(webhookUrl, payload, {
      observe: 'response',
      responseType: 'text'
    });
  }

  bulkImport(leads: Partial<Lead>[]): Observable<BulkLeadImportResponse> {
    return this.http.post<BulkLeadImportResponse>(`${this.apiUrl}/bulk`, leads);
  }

  downloadAllEmailsFile(): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/emails/download`, {
      responseType: 'blob'
    });
  }

  exportLeadsCsv(): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/export/csv`, {
      responseType: 'blob'
    });
  }

  importLeadsCsv(file: File): Observable<BulkImportResult> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<BulkImportResult>(`${this.apiUrl}/import/csv`, formData);
  }

  getEmailAudit(mode: 'invalid' | 'duplicate', page = 0, size = 20): Observable<PageResponse<EmailAuditItem>> {
    const params = new HttpParams()
      .set('mode', mode)
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<PageResponse<EmailAuditItem>>(`${this.apiUrl}/emails/audit`, { params });
  }

  deleteLeadEmails(emailIds: number[]): Observable<DeleteLeadEmailsResponse> {
    return this.http.delete<DeleteLeadEmailsResponse>(`${this.apiUrl}/emails`, {
      body: { emailIds }
    });
  }

  createLead(lead: Lead): Observable<Lead> {
    return this.http.post<Lead>(this.apiUrl, lead);
  }

  updateLead(id: number, lead: Lead): Observable<Lead> {
    return this.http.put<Lead>(`${this.apiUrl}/${id}`, lead);
  }

  deleteLead(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  seedGermanyPlaces(): Observable<void> {
    return this.http.post<void>(`${this.searchCombinationsApiUrl}/seed-germany`, {});
  }

  getPlaceTree(countryCode = 'DE'): Observable<PlaceCountryTree[]> {
    const params = new HttpParams().set('countryCode', countryCode);
    return this.http.get<PlaceCountryTree[]>(`${this.searchCombinationsApiUrl}/places/tree`, { params });
  }

  generateSearchCombinations(request: GenerateSearchCombinationsRequest): Observable<GenerateSearchCombinationsResponse> {
    return this.http.post<GenerateSearchCombinationsResponse>(`${this.searchCombinationsApiUrl}/generate`, request);
  }

  getSearchCombinations(
    page = 0,
    size = 20,
    status?: SearchCombinationStatus,
    categoryId?: number
  ): Observable<PageResponse<SearchCombination>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (status) {
      params = params.set('status', status);
    }
    if (categoryId != null) {
      params = params.set('categoryId', categoryId.toString());
    }

    return this.http.get<PageResponse<SearchCombination>>(this.searchCombinationsApiUrl, { params });
  }

  launchSearchCombination(id: number, request: LaunchSearchCombinationRequest): Observable<SearchCombination> {
    return this.http.post<SearchCombination>(`${this.searchCombinationsApiUrl}/${id}/launch`, request);
  }
}
