import { Component, inject, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router } from '@angular/router';
import { SkeletonLoaderComponent } from '../../../shared/components/skeleton-loader/skeleton-loader.component';
import { LeadMapComponent } from '../lead-map/lead-map.component';
import { CampaignPerformanceComponent } from '../campaign-performance/campaign-performance.component';
import { ClientService } from '../../../core/services/client.service';
import { LeadService } from '../../../core/services/lead.service';
import { CampaignService } from '../../../core/services/campaign.service';
import { CampaignStatus, Lead } from '../../../core/models/models';
import { ArchiveService } from '../../../core/services/archive.service';
import { BehaviorSubject, Observable, forkJoin, of } from 'rxjs';
import { catchError, finalize, map, switchMap, tap } from 'rxjs/operators';

interface DashboardStats {
  clients: number;
  leads: number;
  campaigns: number;
  emailsSent: number;
  clientsInRange: number;
  leadsInRange: number;
  campaignsInRange: number;
  emailsSentInRange: number;
  runningCampaigns: number;
  stopRequestedCampaigns: number;
  pendingEmails: number;
  failedEmails: number;
  repliedEmails: number;
  bouncedEmails: number;
  deliveryRate: number;
  invalidEmails: number;
  duplicateEmails: number;
  archivedClients: number;
  clientsTrend: number[];
  leadsTrend: number[];
  campaignsTrend: number[];
  emailsSentTrend: number[];
}

type DateRangePreset = '7d' | '30d' | '90d';

@Component({
  selector: 'app-dashboard-home',
  standalone: true,
  imports: [
    CommonModule, 
    MatCardModule, 
    MatIconModule, 
    MatButtonModule, 
    MatButtonToggleModule,
    MatTooltipModule,
    SkeletonLoaderComponent,
    LeadMapComponent,
    CampaignPerformanceComponent
  ],
  templateUrl: './dashboard-home.component.html',
  styleUrl: './dashboard-home.component.scss'
})
export class DashboardHomeComponent implements OnInit {
  private clientService = inject(ClientService);
  private leadService = inject(LeadService);
  private campaignService = inject(CampaignService);
  private archiveService = inject(ArchiveService);
  private router: Router = inject(Router);

  readonly emptyStats: DashboardStats = {
    clients: 0,
    leads: 0,
    campaigns: 0,
    emailsSent: 0,
    clientsInRange: 0,
    leadsInRange: 0,
    campaignsInRange: 0,
    emailsSentInRange: 0,
    runningCampaigns: 0,
    stopRequestedCampaigns: 0,
    pendingEmails: 0,
    failedEmails: 0,
    repliedEmails: 0,
    bouncedEmails: 0,
    deliveryRate: 0,
    invalidEmails: 0,
    duplicateEmails: 0,
    archivedClients: 0,
    clientsTrend: [0, 0, 0, 0, 0, 0, 0],
    leadsTrend: [0, 0, 0, 0, 0, 0, 0],
    campaignsTrend: [0, 0, 0, 0, 0, 0, 0],
    emailsSentTrend: [0, 0, 0, 0, 0, 0, 0]
  };
  readonly allLeads$ = new BehaviorSubject<Lead[]>([]);
  stats$: Observable<DashboardStats> = of(this.emptyStats);
  recentLeads$: Observable<Lead[]> = of([]);

  dateRange: DateRangePreset = '30d';
  
  isLoading = true;
  isLoadingLeads = true;
  loadError: string | null = null;

  mapExpanded = false;
  @ViewChild(LeadMapComponent) leadMapRef?: LeadMapComponent;

  toggleMap() {
    this.mapExpanded = !this.mapExpanded;
    this.leadMapRef?.invalidateSize();
  }

  ngOnInit() {
    this.loadStats();
    this.loadRecentLeads();
  }

  navigateToCampaigns() {
    this.router.navigate(['/campaigns']);
  }

  navigateToLeads() {
    this.router.navigate(['/leads']);
  }

  navigateToClients() {
    this.router.navigate(['/clients']);
  }

  navigateToEmailAudit() {
    this.router.navigate(['/leads/email-audit']);
  }

  navigateToArchive() {
    this.router.navigate(['/archive']);
  }

  onDateRangeChange(range: DateRangePreset) {
    this.dateRange = range;
    this.loadStats();
    this.loadRecentLeads();
  }

  getRangeDays(): number {
    if (this.dateRange === '7d') {
      return 7;
    }
    if (this.dateRange === '90d') {
      return 90;
    }
    return 30;
  }

  getRangeLabel(): string {
    return `Last ${this.getRangeDays()} days`;
  }

  getSparklinePath(values: number[]): string {
    if (!values || values.length === 0) {
      return 'M0,24 L100,24';
    }
    const maxValue = Math.max(...values, 1);
    const width = 100;
    const height = 24;
    const step = values.length > 1 ? width / (values.length - 1) : width;
    return values
      .map((value, index) => {
        const x = index * step;
        const y = height - (value / maxValue) * height;
        return `${index === 0 ? 'M' : 'L'}${x.toFixed(2)},${y.toFixed(2)}`;
      })
      .join(' ');
  }

  getTrendDelta(values: number[]): number {
    if (!values || values.length < 2) {
      return 0;
    }
    const first = values[0] ?? 0;
    const last = values[values.length - 1] ?? 0;
    if (first === 0) {
      return last > 0 ? 100 : 0;
    }
    return Math.round(((last - first) / first) * 100);
  }

  loadRecentLeads() {
    this.isLoadingLeads = true;
    const rangeStart = this.getRangeStartDate();
    this.recentLeads$ = this.leadService.getLeads(0, 200).pipe(
      map(page => page.content || []),
      map((leads) => leads
        .filter((lead) => this.parseDate(lead.createdAt)?.getTime() ? this.parseDate(lead.createdAt)!.getTime() >= rangeStart.getTime() : true)
        .sort((a, b) => {
          const aTime = this.parseDate(a.createdAt)?.getTime() || 0;
          const bTime = this.parseDate(b.createdAt)?.getTime() || 0;
          return bTime - aTime;
        })
        .slice(0, 5)
      ),
      catchError(() => of([])),
      finalize(() => this.isLoadingLeads = false)
    );
  }

  loadStats() {
    this.isLoading = true;
    this.loadError = null;

    this.stats$ = forkJoin({
      clients: this.clientService.getClients(),
      leads: this.leadService.getLeads(0, 1),
      leadsForTrend: this.leadService.getLeads(0, 2000),
      campaigns: this.campaignService.getCampaigns(),
      invalidEmails: this.leadService.getEmailAudit('invalid', 0, 1).pipe(map((page) => page.totalElements || 0)),
      duplicateEmails: this.leadService.getEmailAudit('duplicate', 0, 1).pipe(map((page) => page.totalElements || 0)),
      archivedClients: this.archiveService.getArchivedClients(0, 1).pipe(map((page) => page.totalElements || 0))
    }).pipe(
      tap(({ leadsForTrend }) => this.allLeads$.next(leadsForTrend.content || [])),
      switchMap(({ clients, leads, leadsForTrend, campaigns, invalidEmails, duplicateEmails, archivedClients }) => {
        const rangeDays = this.getRangeDays();
        const rangeStart = this.getRangeStartDate();
        const runningCampaigns = campaigns.filter((campaign) => campaign.status === CampaignStatus.RUNNING).length;
        const stopRequestedCampaigns = campaigns.filter((campaign) => campaign.status === CampaignStatus.STOP_REQUESTED).length;

        const clientsTrend = this.buildCountTrend(clients.map((client) => client.createdAt), rangeDays);
        const leadsTrend = this.buildCountTrend((leadsForTrend.content || []).map((lead) => lead.createdAt), rangeDays);
        const campaignsTrend = this.buildCountTrend(campaigns.map((campaign) => campaign.createdAt), rangeDays);

        const clientsInRange = this.countItemsInRange(clients.map((client) => client.createdAt), rangeStart);
        const leadsInRange = this.countItemsInRange((leadsForTrend.content || []).map((lead) => lead.createdAt), rangeStart);
        const campaignsInRange = this.countItemsInRange(campaigns.map((campaign) => campaign.createdAt), rangeStart);

        if (!campaigns.length) {
          return of({
            clients: clients.length,
            leads: leads.totalElements || 0,
            campaigns: 0,
            emailsSent: 0,
            clientsInRange,
            leadsInRange,
            campaignsInRange,
            emailsSentInRange: 0,
            runningCampaigns,
            stopRequestedCampaigns,
            pendingEmails: 0,
            failedEmails: 0,
            repliedEmails: 0,
            bouncedEmails: 0,
            deliveryRate: 0,
            invalidEmails,
            duplicateEmails,
            archivedClients,
            clientsTrend,
            leadsTrend,
            campaignsTrend,
            emailsSentTrend: [0, 0, 0, 0, 0, 0, 0]
          });
        }

        const campaignsWithIds = campaigns.filter((campaign) => !!campaign.id);

        return forkJoin(
          campaignsWithIds.map((campaign) => this.campaignService.getStats(campaign.id!))
        ).pipe(
          map((allStats) => {
            const emailsSent = allStats.reduce((sum, stats) => sum + stats.sent, 0);
            const repliedEmails = allStats.reduce((sum, stats) => sum + stats.replied, 0);
            const bouncedEmails = allStats.reduce((sum, stats) => sum + stats.bounced, 0);
            const pendingEmails = allStats.reduce((sum, stats) => sum + stats.pending, 0);
            const failedEmails = allStats.reduce((sum, stats) => sum + stats.failed, 0);
            const totalAttempts = emailsSent + failedEmails;
            const deliveryRate = totalAttempts > 0 ? Math.round((emailsSent / totalAttempts) * 100) : 0;

            const emailsSentTrend = this.buildCampaignSentTrend(campaignsWithIds, allStats, rangeDays);
            const emailsSentInRange = emailsSentTrend.reduce((sum, value) => sum + value, 0);

            return {
              clients: clients.length,
              leads: leads.totalElements || 0,
              campaigns: campaigns.length,
              emailsSent,
              clientsInRange,
              leadsInRange,
              campaignsInRange,
              emailsSentInRange,
              runningCampaigns,
              stopRequestedCampaigns,
              pendingEmails,
              failedEmails,
              repliedEmails,
              bouncedEmails,
              deliveryRate,
              invalidEmails,
              duplicateEmails,
              archivedClients,
              clientsTrend,
              leadsTrend,
              campaignsTrend,
              emailsSentTrend
            };
          })
        );
      }),
      catchError((err) => {
        this.loadError = err?.message || 'Failed to load dashboard data';
        return of(this.emptyStats);
      }),
      finalize(() => {
        this.isLoading = false;
      })
    );
  }

  private getRangeStartDate(): Date {
    const now = new Date();
    const start = new Date(now);
    start.setHours(0, 0, 0, 0);
    start.setDate(start.getDate() - (this.getRangeDays() - 1));
    return start;
  }

  private parseDate(value?: string): Date | null {
    if (!value) {
      return null;
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date;
  }

  private countItemsInRange(dateValues: Array<string | undefined>, rangeStart: Date): number {
    return dateValues.reduce((count, dateValue) => {
      const parsed = this.parseDate(dateValue);
      if (parsed && parsed.getTime() >= rangeStart.getTime()) {
        return count + 1;
      }
      return count;
    }, 0);
  }

  private buildCountTrend(dateValues: Array<string | undefined>, days: number): number[] {
    const points = 7;
    const now = new Date();
    const nowMs = now.getTime();
    const windowMs = Math.max(1, days) * 24 * 60 * 60 * 1000;
    const sliceMs = windowMs / points;
    const buckets = Array(points).fill(0);

    dateValues.forEach((dateValue) => {
      const parsed = this.parseDate(dateValue);
      if (!parsed) {
        return;
      }
      const ageMs = nowMs - parsed.getTime();
      if (ageMs < 0 || ageMs > windowMs) {
        return;
      }
      const index = points - 1 - Math.min(points - 1, Math.floor(ageMs / sliceMs));
      buckets[index] += 1;
    });

    return buckets;
  }

  private buildCampaignSentTrend(campaigns: Array<{ createdAt?: string }>, allStats: Array<{ sent: number }>, days: number): number[] {
    const points = 7;
    const now = new Date();
    const nowMs = now.getTime();
    const windowMs = Math.max(1, days) * 24 * 60 * 60 * 1000;
    const sliceMs = windowMs / points;
    const buckets = Array(points).fill(0);

    campaigns.forEach((campaign, index) => {
      const parsed = this.parseDate(campaign.createdAt);
      if (!parsed) {
        return;
      }
      const ageMs = nowMs - parsed.getTime();
      if (ageMs < 0 || ageMs > windowMs) {
        return;
      }
      const bucketIndex = points - 1 - Math.min(points - 1, Math.floor(ageMs / sliceMs));
      buckets[bucketIndex] += allStats[index]?.sent || 0;
    });

    return buckets;
  }
}