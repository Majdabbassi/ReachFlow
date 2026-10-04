import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { ToastrService } from 'ngx-toastr';
import { finalize, interval, switchMap, take } from 'rxjs';
import { LeadService } from '../../../core/services/lead.service';
import { SearchCombination, SearchCombinationStatus } from '../../../core/models/models';
import { N8nSettingsService } from '../../../core/services/n8n-settings.service';

// One poll every 2 s: a little over the backend's 10-minute scraper timeout, so long jobs keep their progress UI.
const MAX_SCRAPE_POLLS = 330;

@Component({
  selector: 'app-lead-combinations',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatPaginatorModule,
    MatTableModule,
    RouterLink
  ],
  templateUrl: './lead-combinations.component.html',
  styleUrl: './lead-combinations.component.scss'
})
export class LeadCombinationsComponent implements OnInit {
  private leadService = inject(LeadService);
  private toastr = inject(ToastrService);
  private router = inject(Router);
  private n8nSettings = inject(N8nSettingsService);

  combinations: SearchCombination[] = [];
  isLoadingCombinations = false;
  combinationsPageSize = 20;
  combinationsPageIndex = 0;
  combinationsTotalElements = 0;
  combinationStatusFilter: 'ALL' | SearchCombinationStatus = 'PENDING';
  combinationDisplayedColumns: string[] = ['keyword', 'place', 'status', 'launchedAt', 'actions'];
  launchingCombinationId: number | null = null;

  private pollingSubscriptions: Map<string, any> = new Map();

  ngOnInit() {
    this.loadCombinations();
  }

  loadCombinations() {
    this.isLoadingCombinations = true;
    const status = this.combinationStatusFilter === 'ALL' ? undefined : this.combinationStatusFilter;

    this.leadService.getSearchCombinations(this.combinationsPageIndex, this.combinationsPageSize, status).pipe(
      finalize(() => this.isLoadingCombinations = false)
    ).subscribe({
      next: (response) => {
        this.combinations = response.content.sort((a, b) => {
          if (a.launchedAt && b.launchedAt) {
            return new Date(b.launchedAt).getTime() - new Date(a.launchedAt).getTime();
          }
          if (a.launchedAt && !b.launchedAt) return -1;
          if (!a.launchedAt && b.launchedAt) return 1;
          return new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime();
        });
        this.combinationsTotalElements = response.totalElements;
      },
      error: () => {
        this.toastr.error('Failed to load search combinations');
      }
    });
  }

  onCombinationsPageChange(event: PageEvent) {
    this.combinationsPageIndex = event.pageIndex;
    this.combinationsPageSize = event.pageSize;
    this.loadCombinations();
  }

  onCombinationStatusFilterChange(status: 'ALL' | SearchCombinationStatus) {
    this.combinationStatusFilter = status;
    this.combinationsPageIndex = 0;
    this.loadCombinations();
  }

  launchCombination(combination: SearchCombination) {
    const launchMaxResults = 45;
    const keywordName = combination.keywordNameDe || combination.keywordNameEn;

    this.launchingCombinationId = combination.id;

    this.leadService.collectLeads({
      keywords: [keywordName],
      cities: [combination.placeDisplayName],
      maxResults: launchMaxResults,
      webhookUrl: this.n8nSettings.getWebhookUrl(),
      categoryIds: combination.categoryId ? [combination.categoryId] : []
    }).subscribe({
      next: (response) => {
        this.startPollingForLaunch(response.jobId, combination.id, launchMaxResults);
        this.toastr.success('Launch job started');
      },
      error: () => {
        this.launchingCombinationId = null;
        this.toastr.error('Failed to start launch job');
      }
    });
  }

  private startPollingForLaunch(jobId: string, combinationId: number, maxResults: number) {
    if (this.pollingSubscriptions.has(jobId)) {
      this.stopPolling(jobId);
    }

    const subscription = interval(2000).pipe(
      switchMap(() => this.leadService.getScrapeProgress(jobId)),
      take(MAX_SCRAPE_POLLS)
    ).subscribe({
      next: (progress) => {
        if (progress.status === 'COMPLETED') {
          this.stopPolling(jobId);
          this.leadService.launchSearchCombination(combinationId, {
            status: 'LAUNCHED',
            maxResults: 45
          }).pipe(
            finalize(() => this.launchingCombinationId = null)
          ).subscribe({
            next: () => {
              this.toastr.success(`Launch completed! ${progress.leadsImported} leads imported`);
              this.loadCombinations();
            },
            error: () => {
              this.toastr.error('Launch succeeded but status update failed');
              this.loadCombinations();
            }
          });
        } else if (progress.status === 'FAILED') {
          this.stopPolling(jobId);
          this.leadService.launchSearchCombination(combinationId, {
            status: 'FAILED',
            failureReason: progress.errorMessage,
            maxResults: 45
          }).pipe(
            finalize(() => this.launchingCombinationId = null)
          ).subscribe({
            next: () => {
              this.toastr.error('Launch failed');
              this.loadCombinations();
            },
            error: () => {
              this.toastr.error('Launch failed and status update failed');
              this.loadCombinations();
            }
          });
        }
      },
      error: () => {
        this.stopPolling(jobId);
        this.launchingCombinationId = null;
      },
      complete: () => {
        this.stopPolling(jobId);
        this.launchingCombinationId = null;
      }
    });

    this.pollingSubscriptions.set(jobId, subscription);
  }

  private stopPolling(jobId: string) {
    const subscription = this.pollingSubscriptions.get(jobId);
    if (subscription) {
      subscription.unsubscribe();
      this.pollingSubscriptions.delete(jobId);
    }
  }
}