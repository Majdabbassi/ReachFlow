import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { Campaign, CampaignScheduleRequest, CampaignSend, CampaignStats, CampaignSendStatus, PageResponse, SelectiveSendRequest } from '../models/models';

@Injectable({
  providedIn: 'root'
})
export class CampaignService {
  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8080/api/campaigns';
  private campaignsSubject = new BehaviorSubject<Campaign[]>([]);
  readonly campaigns$ = this.campaignsSubject.asObservable();

  getCampaigns(): Observable<Campaign[]> {
    return this.http.get<Campaign[]>(this.apiUrl);
  }

  loadCampaigns(): Observable<Campaign[]> {
    return this.getCampaigns().pipe(tap((campaigns) => this.campaignsSubject.next(campaigns)));
  }

  getCampaign(id: number): Observable<Campaign> {
    return this.http.get<Campaign>(`${this.apiUrl}/${id}`);
  }

  updateCampaign(id: number, campaign: Partial<Campaign>): Observable<Campaign> {
    return this.http.put<Campaign>(`${this.apiUrl}/${id}`, campaign);
  }

  generateSends(id: number): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${id}/generate`, {});
  }

  startCampaign(id: number, request: { subject: string, body: string, delaySeconds: number, htmlBody: boolean }): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${id}/start`, request);
  }

  scheduleCampaign(id: number, request: CampaignScheduleRequest): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${id}/schedule`, request);
  }

  stopCampaign(id: number): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${id}/stop`, {});
  }

  sendSelected(id: number, request: SelectiveSendRequest): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${id}/send-selected`, request);
  }

  getCampaignSends(id: number, page = 0, size = 25, status?: CampaignSendStatus | 'ALL'): Observable<PageResponse<CampaignSend>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (status && status !== 'ALL') {
      params = params.set('status', status);
    }

    return this.http.get<PageResponse<CampaignSend>>(`${this.apiUrl}/${id}/sends`, { params });
  }

  getStats(id: number): Observable<CampaignStats> {
    return this.http.get<CampaignStats>(`${this.apiUrl}/${id}/stats`);
  }
}
