import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

export const DEFAULT_N8N_WEBHOOK_URL = 'http://localhost:5678/webhook-test/3dd78525-b1e6-4775-98bc-1c88aeb0e313';

const STORAGE_KEY = 'reachflow.n8nWebhookUrl';

@Injectable({
  providedIn: 'root'
})
export class N8nSettingsService {
  private webhookUrlSubject: BehaviorSubject<string> = new BehaviorSubject<string>(
    this.normalizeForBackend(this.readStored() || DEFAULT_N8N_WEBHOOK_URL)
  );

  webhookUrl$: Observable<string> = this.webhookUrlSubject.asObservable();

  getWebhookUrl(): string {
    return this.webhookUrlSubject.getValue();
  }

  setUrl(rawUrl: string): void {
    const normalized = this.normalizeForBackend((rawUrl || '').trim());
    this.webhookUrlSubject.next(normalized);
    this.persist(normalized);
  }

  private readStored(): string | null {
    try {
      return localStorage.getItem(STORAGE_KEY);
    } catch {
      return null;
    }
  }

  private persist(value: string): void {
    try {
      localStorage.setItem(STORAGE_KEY, value);
    } catch {
      // storage unavailable — keep service value in memory only
    }
  }

  private normalizeForBackend(url: string): string {
    if (!url) return url;
    try {
      const parsed = new URL(url);
      if (parsed.hostname.toLowerCase() === 'localhost') {
        parsed.hostname = 'n8n';
      }
      return parsed.toString();
    } catch {
      return url;
    }
  }
}