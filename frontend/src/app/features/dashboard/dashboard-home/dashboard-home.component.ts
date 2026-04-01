import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router } from '@angular/router';
import { SkeletonLoaderComponent } from '../../../shared/components/skeleton-loader/skeleton-loader.component';
import { ClientService } from '../../../core/services/client.service';
import { LeadService } from '../../../core/services/lead.service';
import { CampaignService } from '../../../core/services/campaign.service';
import { Lead } from '../../../core/models/models';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError, finalize, map, switchMap } from 'rxjs/operators';

interface DashboardStats {
  clients: number;
  leads: number;
  campaigns: number;
  emailsSent: number;
}

@Component({
  selector: 'app-dashboard-home',
  standalone: true,
  imports: [
    CommonModule, 
    MatCardModule, 
    MatIconModule, 
    MatButtonModule, 
    MatTooltipModule,
    SkeletonLoaderComponent
  ],
  templateUrl: './dashboard-home.component.html',
  styleUrl: './dashboard-home.component.scss'
})
export class DashboardHomeComponent implements OnInit {
  private clientService = inject(ClientService);
  private leadService = inject(LeadService);
  private campaignService = inject(CampaignService);
  private router: Router = inject(Router);

  readonly emptyStats: DashboardStats = {
    clients: 0,
    leads: 0,
    campaigns: 0,
    emailsSent: 0
  };
  stats$: Observable<DashboardStats> = of(this.emptyStats);
  recentLeads$: Observable<Lead[]> = of([]);
  
  isLoading = true;
  isLoadingLeads = true;
  loadError: string | null = null;

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

  loadRecentLeads() {
    this.isLoadingLeads = true;
    this.recentLeads$ = this.leadService.getLeads(0, 5).pipe(
      map(page => page.content),
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
      campaigns: this.campaignService.getCampaigns()
    }).pipe(
      switchMap(({ clients, leads, campaigns }) => {
        if (!campaigns.length) {
          return of({
            clients: clients.length,
            leads: leads.totalElements || 0,
            campaigns: 0,
            emailsSent: 0
          });
        }

        return forkJoin(
          campaigns
            .filter((campaign) => !!campaign.id)
            .map((campaign) => this.campaignService.getStats(campaign.id!))
        ).pipe(
          map((allStats) => ({
            clients: clients.length,
            leads: leads.totalElements || 0,
            campaigns: campaigns.length,
            emailsSent: allStats.reduce((sum, stats) => sum + stats.sent, 0)
          }))
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
}