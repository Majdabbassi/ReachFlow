import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ArchivedCampaign,
  ArchivedCampaignSend,
  ArchivedClient,
  CampaignSendStatus,
  PageResponse
} from '../models/models';

@Injectable({
  providedIn: 'root'
})
export class ArchiveService {
  private http = inject(HttpClient);
  private apiClientsUrl = '/api/clients';
  private apiArchiveUrl = '/api/archive';

  archiveClient(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiClientsUrl}/${id}/archive`);
  }

  restoreClient(id: number): Observable<void> {
    return this.http.post<void>(`${this.apiArchiveUrl}/clients/${id}/restore`, {});
  }

  getArchivedClients(page = 0, size = 10): Observable<PageResponse<ArchivedClient>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    return this.http.get<PageResponse<ArchivedClient>>(`${this.apiArchiveUrl}/clients`, { params });
  }

  getArchivedClient(id: number): Observable<ArchivedClient> {
    return this.http.get<ArchivedClient>(`${this.apiArchiveUrl}/clients/${id}`);
  }

  getArchivedCampaigns(clientId: number, page = 0, size = 10): Observable<PageResponse<ArchivedCampaign>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    return this.http.get<PageResponse<ArchivedCampaign>>(`${this.apiArchiveUrl}/clients/${clientId}/campaigns`, { params });
  }

  getArchivedCampaignSends(
    clientId: number,
    campaignId: number,
    page = 0,
    size = 25,
    status?: CampaignSendStatus | 'ALL'
  ): Observable<PageResponse<ArchivedCampaignSend>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (status && status !== 'ALL') {
      params = params.set('status', status);
    }

    return this.http.get<PageResponse<ArchivedCampaignSend>>(
      `${this.apiArchiveUrl}/clients/${clientId}/campaigns/${campaignId}/sends`,
      { params }
    );
  }
}
