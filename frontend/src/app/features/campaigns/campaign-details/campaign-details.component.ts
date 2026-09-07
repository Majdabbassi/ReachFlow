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
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatTabsModule } from '@angular/material/tabs';
import { MatMenuModule } from '@angular/material/menu';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { CampaignService } from '../../../core/services/campaign.service';
import { ClientService } from '../../../core/services/client.service';
import { Campaign, CampaignLog, CampaignSend, CampaignSendStatus, CampaignStats, CampaignStatus, Client } from '../../../core/models/models';
import { ToastrService } from 'ngx-toastr';
import { forkJoin, interval, of, Subscription, switchMap, takeWhile, tap, finalize } from 'rxjs';
import { SelectiveSendDialogComponent, SelectiveSendDialogResult } from '../selective-send-dialog/selective-send-dialog.component';
import { TemplateLoaderDialogComponent } from '../template-loader-dialog/template-loader-dialog.component';
import { QuillModule } from 'ngx-quill';

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
    MatPaginatorModule,
    MatTooltipModule,
    MatCheckboxModule,
    MatDialogModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatTabsModule,
    MatMenuModule,
    ReactiveFormsModule,
    RouterLink,
    QuillModule
  ],
  templateUrl: './campaign-details.component.html',
  styleUrl: './campaign-details.component.scss'
})
export class CampaignDetailsComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private campaignService = inject(CampaignService);
  private clientService = inject(ClientService);
  private dialog = inject(MatDialog);
  private fb = inject(FormBuilder);
  private toastr = inject(ToastrService);

  QuillConfiguration = {
    toolbar: [
      ['bold', 'italic', 'underline', 'strike'],
      ['blockquote', 'code-block'],
      [{ 'list': 'ordered' }, { 'list': 'bullet' }],
      [{ 'header': [1, 2, 3, 4, 5, 6, false] }],
      [{ 'color': [] }, { 'background': [] }],
      ['link'],
      ['clean']
    ]
  };

  CampaignStatus = CampaignStatus;
  CampaignSendStatus = CampaignSendStatus;
  campaign?: Campaign;
  client?: Client;
  stats: CampaignStats = { total: 0, sent: 0, replied: 0, pending: 0, failed: 0, bounced: 0 };
  campaignSends: CampaignSend[] = [];
  logs: CampaignLog[] = [];
  sendColumns: string[] = ['select', 'email', 'institution', 'city', 'status', 'sentAt'];
  selectedSendIds = new Set<number>();
  selectedSendStatus: CampaignSendStatus | 'ALL' = 'ALL';
  sendPageSize = 25;
  sendPageIndex = 0;
  sendTotalElements = 0;
  
  templateForm: FormGroup = this.fb.group({
    subject: ['Hello from {name}', Validators.required],
    body: ['Hi,\n\nI am writing to you regarding {city}.\n\nBest regards.', Validators.required],
    delaySeconds: [2, [Validators.required, Validators.min(0)]],
    htmlBody: [false],
    scheduleForLater: [false],
    scheduledDate: [null],
    scheduledTime: ['09:00']
  });

  previewVisible = true;
  showLogs = false;
  isLoading = false;
  loadError: string | null = null;

  private statsSubscription?: Subscription;
  private pollCount = 0;
  private maxPollCount = 120; // 120 * 5s = 10 minutes max polling

  ngOnInit() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (id) {
      this.loadCampaign(id);
      this.addLog('info', 'Campaign details loaded');
    }
  }

  ngOnDestroy() {
    this.statsSubscription?.unsubscribe();
  }

  addLog(level: 'info' | 'success' | 'warning' | 'error', message: string, details?: string) {
    this.logs.unshift({
      id: Math.random().toString(36).substring(7),
      timestamp: new Date(),
      level,
      message,
      details
    });
    if (this.logs.length > 100) this.logs.pop();
  }

  preloadTemplateData(template: any) {
    // Pre-fill the form with template data
    this.templateForm.patchValue({
      subject: template.subject || '',
      body: template.body || '',
      delaySeconds: template.delaySeconds || 2,
      htmlBody: template.htmlBody || false
    });
    this.toastr.success('Template loaded! You can now edit and launch.');
  }

  openTemplateLoader() {
    const dialogRef = this.dialog.open(TemplateLoaderDialogComponent);
    dialogRef.afterClosed().subscribe((template) => {
      if (template) {
        this.preloadTemplateData(template);
      }
    });
  }

  loadCampaign(id: number) {
    this.isLoading = true;
    this.loadError = null;

    this.campaignService.getCampaign(id).pipe(
      switchMap((campaign) =>
        forkJoin({
          campaign: of(campaign),
          client: this.clientService.getClient(campaign.clientId),
          stats: this.campaignService.getStats(id),
          sendsPage: this.campaignService.getCampaignSends(id, this.sendPageIndex, this.sendPageSize, this.selectedSendStatus)
        })
      ),
      finalize(() => {
        this.isLoading = false;
      })
    ).subscribe({
      next: ({ campaign, client, stats, sendsPage }) => {
        this.campaign = campaign;
        this.client = client;
        this.stats = stats;
        this.campaignSends = sendsPage.content;
        this.retainOnlyVisibleSelectedIds();
        this.sendTotalElements = sendsPage.totalElements;
        this.startStatsPolling(id);
        this.addLog('info', `Fetched stats for ${campaign.name}`);
      },
      error: (err) => {
        this.loadError = err?.message || 'Failed to load campaign details';
        this.addLog('error', 'Failed to load campaign details', err?.message);
      }
    });
  }

  loadStats(id: number) {
    this.campaignService.getStats(id).subscribe({
      next: (stats) => {
        this.stats = stats;
        this.addLog('info', 'Refreshed campaign stats');
      },
      error: () => {
        this.toastr.error('Failed to refresh campaign stats');
        this.addLog('error', 'Failed to refresh campaign stats');
      }
    });
  }

  loadCampaignSends(id: number) {
    this.campaignService
      .getCampaignSends(id, this.sendPageIndex, this.sendPageSize, this.selectedSendStatus)
      .subscribe({
        next: (page) => {
          this.campaignSends = page.content;
          this.retainOnlyVisibleSelectedIds();
          this.sendTotalElements = page.totalElements;
        },
        error: () => {
          this.toastr.error('Failed to load campaign sends');
          this.addLog('error', 'Failed to load campaign sends');
        }
      });
  }

  onSendStatusFilterChange(status: CampaignSendStatus | 'ALL') {
    this.selectedSendStatus = status;
    this.sendPageIndex = 0;
    if (this.campaign?.id) this.loadCampaignSends(this.campaign.id);
    this.addLog('info', `Filtered campaign sends by ${status}`);
  }

  onSendPageChange(event: PageEvent) {
    this.sendPageIndex = event.pageIndex;
    this.sendPageSize = event.pageSize;
    if (this.campaign?.id) this.loadCampaignSends(this.campaign.id);
  }

  startStatsPolling(id: number) {
    this.pollCount = 0;
    this.statsSubscription?.unsubscribe();
    this.statsSubscription = interval(5000).pipe(
      tap(() => this.pollCount++),
      takeWhile(() => this.campaign?.status === CampaignStatus.RUNNING, true),
      switchMap(() => this.campaignService.getStats(id))
    ).subscribe(stats => {
      const prevSent = this.stats.sent;
      const prevFailed = this.stats.failed;
      
      this.stats = stats;
      this.loadCampaignSends(id);
      
      if (stats.sent > prevSent) {
        this.addLog('success', `${stats.sent - prevSent} new emails sent successfully`);
      }
      if (stats.failed > prevFailed) {
        this.addLog('error', `${stats.failed - prevFailed} emails failed to send`);
      }

      if (stats.total > 0 && stats.pending === 0 && this.campaign?.status === CampaignStatus.RUNNING) {
        this.loadCampaign(id); // Reload to get completed status
        this.addLog('success', 'Campaign completed!');
      }
    });
  }

  startCampaign() {
    if (this.campaign?.id) {
      if (this.templateForm.invalid) {
        this.toastr.error('Please complete the email template');
        return;
      }

      const scheduleForLater = Boolean(this.templateForm.value.scheduleForLater);
      const request = {
        subject: this.templateForm.value.subject,
        body: this.templateForm.value.body,
        delaySeconds: Number(this.templateForm.value.delaySeconds),
        htmlBody: Boolean(this.templateForm.value.htmlBody)
      };

      if (scheduleForLater) {
        const scheduledAt = this.buildScheduledAtIso();
        if (!scheduledAt) {
          this.toastr.error('Please choose a valid schedule date and time');
          return;
        }

        this.campaignService.scheduleCampaign(this.campaign.id, {
          ...request,
          scheduledAt
        }).subscribe({
          next: () => {
            this.toastr.success('Campaign scheduled successfully');
            this.addLog('success', `Campaign scheduled for ${scheduledAt}`);
            this.loadCampaign(this.campaign!.id!);
          },
          error: () => {
            this.toastr.error('Failed to schedule campaign');
          }
        });
        return;
      }

      this.addLog('info', 'Launching outreach campaign...');
      this.campaignService.startCampaign(this.campaign.id, request).subscribe({
        next: () => {
          this.toastr.success('Campaign started successfully');
          this.addLog('success', 'Campaign launched successfully');
          this.loadCampaign(this.campaign!.id!);
        },
        error: (err) => {
          const message = err?.error?.message || err?.message || 'Failed to start campaign';
          this.toastr.error(message);
          this.addLog('error', 'Failed to launch campaign', message);
        }
      });
    }
  }

  scanSentEmails() {
    if (!this.client?.id) {
      return;
    }

    this.clientService.scanSentEmails(this.client.id).subscribe({
      next: (result) => {
        this.toastr.success(`Scanned ${result.scannedCount}, marked SENT ${result.markedAsSentCount || 0}`);
        this.addLog('info', 'Scan sent emails completed');
        if (this.campaign?.id) {
          this.loadCampaign(this.campaign.id);
        }
      },
      error: () => {
        this.toastr.error('Failed to scan sent emails');
      }
    });
  }

  scanReplies() {
    if (!this.client?.id) {
      return;
    }

    this.clientService.scanReplies(this.client.id).subscribe({
      next: (result) => {
        this.toastr.success(`Scanned ${result.scannedCount}, marked REPLIED ${result.markedAsRepliedCount || 0}`);
        this.addLog('info', 'Scan replies completed');
        if (this.campaign?.id) {
          this.loadCampaign(this.campaign.id);
        }
      },
      error: () => {
        this.toastr.error('Failed to scan replies');
      }
    });
  }

  stopCampaign() {
    if (!this.campaign?.id) {
      return;
    }

    this.campaignService.stopCampaign(this.campaign.id).subscribe({
      next: () => {
        this.toastr.success('Stop requested successfully');
        this.addLog('warning', 'Stop requested for running campaign');
        this.loadCampaign(this.campaign!.id!);
      },
      error: () => {
        this.toastr.error('Failed to stop campaign');
      }
    });
  }

  canStopCampaign(): boolean {
    if (!this.campaign) {
      return false;
    }

    return this.campaign.status === CampaignStatus.RUNNING || this.campaign.status === CampaignStatus.STOP_REQUESTED;
  }

  openSendSelectedDialog() {
    if (!this.campaign?.id || !this.hasSelectableSelected()) {
      return;
    }

    const dialogRef = this.dialog.open(SelectiveSendDialogComponent, {
      width: '640px',
      data: {
        selectedCount: this.selectedSendIds.size,
        subject: this.templateForm.value.subject,
        body: this.templateForm.value.body,
        delaySeconds: this.templateForm.value.delaySeconds,
        htmlBody: this.templateForm.value.htmlBody
      }
    });

    dialogRef.afterClosed().subscribe((result?: SelectiveSendDialogResult) => {
      if (!result || !this.campaign?.id) {
        return;
      }

      this.campaignService.sendSelected(this.campaign.id, {
        sendIds: Array.from(this.selectedSendIds),
        subject: result.subject,
        body: result.body,
        delaySeconds: Number(result.delaySeconds),
        htmlBody: Boolean(result.htmlBody)
      }).subscribe({
        next: () => {
          this.toastr.success('Selected emails were sent');
          this.addLog('success', `Sent ${this.selectedSendIds.size} selected email(s)`);
          this.selectedSendIds.clear();
          this.loadCampaign(this.campaign!.id!);
        },
        error: () => {
          this.toastr.error('Failed to send selected emails');
        }
      });
    });
  }

  toggleSendSelection(send: CampaignSend, checked: boolean) {
    if (!send.id || !this.isSelectableSend(send)) {
      return;
    }

    if (checked) {
      this.selectedSendIds.add(send.id);
    } else {
      this.selectedSendIds.delete(send.id);
    }
  }

  isSendSelected(send: CampaignSend): boolean {
    return !!send.id && this.selectedSendIds.has(send.id);
  }

  isSelectableSend(send: CampaignSend): boolean {
    return send.status === CampaignSendStatus.PENDING || send.status === CampaignSendStatus.FAILED;
  }

  hasSelectableSelected(): boolean {
    return this.selectedSendIds.size > 0;
  }

  private retainOnlyVisibleSelectedIds() {
    const visibleSelectableIds = new Set(
      this.campaignSends
        .filter((send) => this.isSelectableSend(send) && !!send.id)
        .map((send) => send.id!)
    );

    this.selectedSendIds = new Set(
      Array.from(this.selectedSendIds).filter((id) => visibleSelectableIds.has(id))
    );
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
    return ((this.stats.sent + this.stats.replied + this.stats.failed + this.stats.bounced) / this.stats.total) * 100;
  }

  previewEmail() {
    this.previewVisible = !this.previewVisible;
  }

  toggleLogs() {
    this.showLogs = !this.showLogs;
  }

  getSendStatusColor(status: CampaignSendStatus): string {
    switch (status) {
      case CampaignSendStatus.SENT: return 'primary';
      case CampaignSendStatus.REPLIED: return 'primary';
      case CampaignSendStatus.BOUNCED: return 'warn';
      case CampaignSendStatus.FAILED: return 'warn';
      default: return 'accent';
    }
  }

  private buildScheduledAtIso(): string | null {
    const dateValue = this.templateForm.value.scheduledDate;
    const timeValue = (this.templateForm.value.scheduledTime || '').trim();
    if (!dateValue || !timeValue || !timeValue.includes(':')) {
      return null;
    }

    const [hourText, minuteText] = timeValue.split(':');
    const hour = Number(hourText);
    const minute = Number(minuteText);
    if (!Number.isInteger(hour) || !Number.isInteger(minute) || hour < 0 || hour > 23 || minute < 0 || minute > 59) {
      return null;
    }

    const date = new Date(dateValue);
    date.setHours(hour, minute, 0, 0);
    if (Number.isNaN(date.getTime())) {
      return null;
    }

    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hh = String(date.getHours()).padStart(2, '0');
    const mm = String(date.getMinutes()).padStart(2, '0');
    return `${year}-${month}-${day}T${hh}:${mm}:00`;
  }

  getPreviewBody(): string {
    let body = (this.templateForm.value.body || '') as string;
    body = body.replace(/{name}/g, 'John Doe');
    body = body.replace(/{city}/g, 'New York');
    body = body.replace(/{institutionName}/g, 'Sample Institution');
    body = body.replace(/{website}/g, 'https://example.com');

    if (this.templateForm.value.htmlBody) {
      return body;
    }

    return this.escapeHtml(body).replace(/\n/g, '<br>');
  }

  getClientDocumentSummary(): string {
    if (!this.client?.categories?.length) {
      return 'No categories assigned';
    }

    const uploadedCategories = this.client.categories.filter((category) => category.hasDocument);
    if (!uploadedCategories.length) {
      return 'No category documents uploaded';
    }

    return uploadedCategories.map((category) => category.categoryName).join(', ');
  }

  private escapeHtml(text: string): string {
    return text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }
}
