import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { Client, GmailScanResult } from '../models/models';

@Injectable({
  providedIn: 'root'
})
export class ClientService {
  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8080/api/clients';
  private clientsSubject = new BehaviorSubject<Client[]>([]);
  readonly clients$ = this.clientsSubject.asObservable();

  getClients(): Observable<Client[]> {
    return this.http.get<Client[]>(this.apiUrl);
  }

  loadClients(): Observable<Client[]> {
    return this.getClients().pipe(tap((clients) => this.clientsSubject.next(clients)));
  }

  getClient(id: number): Observable<Client> {
    return this.http.get<Client>(`${this.apiUrl}/${id}`);
  }

  createClient(client: Client): Observable<Client> {
    return this.http.post<Client>(this.apiUrl, client);
  }

  updateClient(id: number, client: Client): Observable<Client> {
    return this.http.put<Client>(`${this.apiUrl}/${id}`, client);
  }

  uploadCategoryDocument(clientId: number, categoryId: number, file: File): Observable<void> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.put<void>(`${this.apiUrl}/${clientId}/categories/${categoryId}/document`, formData);
  }

  downloadCategoryDocument(clientId: number, categoryId: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${clientId}/categories/${categoryId}/document`, {
      responseType: 'blob'
    });
  }

  scanSentEmails(clientId: number): Observable<GmailScanResult> {
    return this.http.post<GmailScanResult>(`${this.apiUrl}/${clientId}/scan-sent`, {});
  }
}
