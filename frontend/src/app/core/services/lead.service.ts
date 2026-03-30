import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Lead } from '../models/models';

@Injectable({
  providedIn: 'root'
})
export class LeadService {
  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8080/api/leads';

  getLeads(page: number = 0, size: number = 10, city?: string, source?: string): Observable<any> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (city) params = params.set('city', city);
    if (source) params = params.set('source', source);

    return this.http.get<any>(this.apiUrl, { params });
  }

  getLead(id: number): Observable<Lead> {
    return this.http.get<Lead>(`${this.apiUrl}/${id}`);
  }

  createLead(lead: Lead): Observable<Lead> {
    return this.http.post<Lead>(this.apiUrl, lead);
  }

  updateLead(id: number, lead: Lead): Observable<Lead> {
    return this.http.put<Lead>(`${this.apiUrl}/${id}`, lead);
  }
}
