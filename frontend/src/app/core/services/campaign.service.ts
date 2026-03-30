import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Campaign, CampaignStats } from '../models/models';

@Injectable({
  providedIn: 'root'
})
export class CampaignService {
  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8080/api/campaigns';

  getCampaigns(): Observable<Campaign[]> {
    return this.http.get<Campaign[]>(this.apiUrl);
  }

  getCampaign(id: number): Observable<Campaign> {
    return this.http.get<Campaign>(`${this.apiUrl}/${id}`);
  }

  createCampaign(campaign: Partial<Campaign>): Observable<Campaign> {
    return this.http.post<Campaign>(this.apiUrl, campaign);
  }

  updateCampaign(id: number, campaign: Partial<Campaign>): Observable<Campaign> {
    return this.http.put<Campaign>(`${this.apiUrl}/${id}`, campaign);
  }

  generateSends(id: number): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${id}/generate`, {});
  }

  startCampaign(id: number, request: { webhookUrl: string, subject: string, body: string }): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${id}/start`, request);
  }

  getStats(id: number): Observable<CampaignStats> {
    return this.http.get<CampaignStats>(`${this.apiUrl}/${id}/stats`);
  }
}
