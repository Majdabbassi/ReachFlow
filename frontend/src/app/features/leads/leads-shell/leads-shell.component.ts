import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterOutlet, Router, NavigationEnd } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { filter, Subscription, interval } from 'rxjs';
import { LeadService } from '../../../core/services/lead.service';
import { N8nSettingsService } from '../../../core/services/n8n-settings.service';

@Component({
  selector: 'app-leads-shell',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, MatButtonModule, MatIconModule],
  templateUrl: './leads-shell.component.html',
  styleUrl: './leads-shell.component.scss'
})
export class LeadsShellComponent implements OnInit, OnDestroy {
  private leadService = inject(LeadService);
  private n8nSettings = inject(N8nSettingsService);
  private router = inject(Router);

  pendingCount = 0;
  webhookUrl = this.n8nSettings.getWebhookUrl();
  connectionOk = false;
  testResultShown = false;
  connectionMessage = '';
  activePath = '';

  private subscriptions: Subscription[] = [];

  ngOnInit() {
    this.refreshPendingCount();
    this.subscriptions.push(
      interval(10000).subscribe(() => this.refreshPendingCount()),
      this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => {
        this.activePath = this.router.url;
        this.refreshPendingCount();
      }),
      this.n8nSettings.webhookUrl$.subscribe((url) => {
        this.webhookUrl = url;
        this.connectionOk = this.isHostnameN8n(url);
      })
    );
    this.activePath = this.router.url;
    this.connectionOk = this.isHostnameN8n(this.webhookUrl);
  }

  isStripActive(prefix: string): boolean {
    return this.activePath.startsWith(prefix);
  }

  refreshPendingCount() {
    this.leadService.getSearchCombinations(0, 1, 'PENDING').subscribe({
      next: (res) => (this.pendingCount = res.totalElements || 0),
      error: () => {}
    });
  }

  testConnection() {
    this.testResultShown = true;
    this.connectionMessage = this.connectionOk
      ? 'Looks correctly configured for Docker'
      : "Warning: hostname isn't 'n8n' — this won't reach n8n from inside the backend container";
  }

  private isHostnameN8n(url: string): boolean {
    try {
      return new URL(url).hostname.toLowerCase() === 'n8n';
    } catch {
      return false;
    }
  }

  ngOnDestroy() {
    this.subscriptions.forEach((s) => s.unsubscribe());
  }
}
