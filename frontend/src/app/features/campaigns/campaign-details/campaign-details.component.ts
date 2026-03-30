import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { CampaignService } from '../../../core/services/campaign.service';
import { ClientService } from '../../../core/services/client.service';
import { Campaign, CampaignStats, CampaignStatus, Client } from '../../../core/models/models';
import { ToastrService } from 'ngx-toastr';
import { interval, Subscription, switchMap, takeWhile, tap } from 'rxjs';

@Component({
  selector: 'app-campaign-details',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatProgressBarModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    ReactiveFormsModule,
    RouterLink
  ],
  templateUrl: './campaign-details.component.html',
  styleUrl: './campaign-details.component.scss'
})
export class CampaignDetailsComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private campaignService = inject(CampaignService);
  private clientService = inject(ClientService);
  private fb = inject(FormBuilder);
  private toastr = inject(ToastrService);

  CampaignStatus = CampaignStatus;
  campaign?: Campaign;
  client?: Client;
  stats: CampaignStats = { total: 0, sent: 0, pending: 0, failed: 0 };
  
  templateForm: FormGroup = this.fb.group({
    subject: ['Hello from {name}', Validators.required],
    body: ['Hi,\n\nI am writing to you regarding {city}.\n\nBest regards.', Validators.required]
  });

  previewVisible = false;
  private statsSubscription?: Subscription;
  private pollCount = 0;
  private maxPollCount = 120; // 120 * 5s = 10 minutes max polling

  ngOnInit() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (id) {
      this.loadCampaign(id);
    }
  }

  ngOnDestroy() {
    this.statsSubscription?.unsubscribe();
  }

  loadCampaign(id: number) {
    this.campaignService.getCampaign(id).subscribe(campaign => {
      this.campaign = campaign;
      this.clientService.getClient(campaign.clientId).subscribe(client => this.client = client);
      this.loadStats(id);
      this.startStatsPolling(id);
    });
  }

  loadStats(id: number) {
    this.campaignService.getStats(id).subscribe(stats => this.stats = stats);
  }

  startStatsPolling(id: number) {
    this.pollCount = 0;
    this.statsSubscription?.unsubscribe();
    
    this.statsSubscription = interval(5000).pipe(
      tap(() => this.pollCount++),
      takeWhile(() => this.campaign?.status === CampaignStatus.RUNNING && this.pollCount < this.maxPollCount, true),
      switchMap(() => this.campaignService.getStats(id))
    ).subscribe(stats => {
      this.stats = stats;
      if (this.pollCount >= this.maxPollCount) {
        this.toastr.warning('Polling timed out. Check if n8n is processing.');
      }
      if (stats.total > 0 && stats.pending === 0 && this.campaign?.status === CampaignStatus.RUNNING) {
        this.loadCampaign(id); // Reload to get completed status
      }
    });
  }

  generateEmails() {
    if (this.campaign?.id) {
      this.campaignService.generateSends(this.campaign.id).subscribe(() => {
        this.toastr.success('Emails generated successfully');
        this.loadStats(this.campaign!.id!);
      });
    }
  }

  startCampaign() {
    if (this.campaign?.id) {
      const webhookUrl = localStorage.getItem('n8n_webhook_url');
      if (!webhookUrl) {
        this.toastr.error('Please configure the n8n webhook URL in Settings');
        return;
      }

      if (this.templateForm.invalid) {
        this.toastr.error('Please complete the email template');
        return;
      }

      const request = {
        webhookUrl: webhookUrl,
        subject: this.templateForm.value.subject,
        body: this.templateForm.value.body
      };

      this.campaignService.startCampaign(this.campaign.id, request).subscribe(() => {
        this.toastr.success('Campaign started successfully');
        this.loadCampaign(this.campaign!.id!);
      });
    }
  }

  getStatusColor(status?: CampaignStatus): string {
    switch (status) {
      case CampaignStatus.DRAFT: return 'secondary';
      case CampaignStatus.RUNNING: return 'primary';
      case CampaignStatus.COMPLETED: return 'accent';
      default: return 'secondary';
    }
  }

  getProgress(): number {
    if (this.stats.total === 0) return 0;
    return ((this.stats.sent + this.stats.failed) / this.stats.total) * 100;
  }

  previewEmail() {
    this.previewVisible = true;
  }

  getPreviewBody(): string {
    let body = this.templateForm.value.body;
    body = body.replace(/{name}/g, 'John Doe');
    body = body.replace(/{city}/g, 'New York');
    return body;
  }
}
