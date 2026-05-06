import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AusbildungSearchRequest, AusbildungSearchResponse } from '../models/models';

@Injectable({ providedIn: 'root' })
export class AusbildungService {
  private http = inject(HttpClient);
  private webhookUrl = 'http://localhost:5678/webhook-test/ausbildung-finder';

  search(request: AusbildungSearchRequest): Observable<AusbildungSearchResponse> {
    return this.http.post<AusbildungSearchResponse>(this.webhookUrl, request);
  }
}
