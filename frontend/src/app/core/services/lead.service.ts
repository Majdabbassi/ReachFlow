
import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BulkLeadImportResponse, Lead, PageResponse } from '../models/models';

@Injectable({ providedIn: 'root' })
export class LeadService {
  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8080/api/leads';

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

  createLead(lead: Lead): Observable<Lead> {
    return this.http.post<Lead>(this.apiUrl, lead);
  }

  updateLead(id: number, lead: Lead): Observable<Lead> {
    return this.http.put<Lead>(`${this.apiUrl}/${id}`, lead);
  }

  deleteLead(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
