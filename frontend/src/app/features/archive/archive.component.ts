import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatChipsModule } from '@angular/material/chips';
import { ToastrService } from 'ngx-toastr';
import {
  ArchivedCampaign,
  ArchivedCampaignSend,
  ArchivedClient,
  CampaignSendStatus,
  PageResponse
} from '../../core/models/models';
import { ArchiveService } from '../../core/services/archive.service';

@Component({
  selector: 'app-archive',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatPaginatorModule,
    MatChipsModule
  ],
  templateUrl: './archive.component.html'
})
export class ArchiveComponent implements OnInit {
  private archiveService = inject(ArchiveService);
  private toastr = inject(ToastrService);

  CampaignSendStatus = CampaignSendStatus;

  clientsColumns: string[] = ['name', 'email', 'archivedAt', 'actions'];
  campaignsColumns: string[] = ['name', 'status', 'total', 'sent', 'pending', 'failed', 'actions'];
  sendsColumns: string[] = ['email', 'institution', 'city', 'status', 'sentAt'];

  archivedClients: ArchivedClient[] = [];
  archivedCampaigns: ArchivedCampaign[] = [];
  archivedSends: ArchivedCampaignSend[] = [];

  selectedClient?: ArchivedClient;
  selectedCampaign?: ArchivedCampaign;
  selectedStatus: CampaignSendStatus | 'ALL' = 'ALL';

  clientsPageIndex = 0;
  clientsPageSize = 10;
  clientsTotalElements = 0;

  campaignsPageIndex = 0;
  campaignsPageSize = 10;
  campaignsTotalElements = 0;

  sendsPageIndex = 0;
  sendsPageSize = 25;
  sendsTotalElements = 0;

  isLoadingClients = false;
  isLoadingCampaigns = false;
  isLoadingSends = false;

  ngOnInit(): void {
    this.loadArchivedClients();
  }

  loadArchivedClients(): void {
    this.isLoadingClients = true;
    this.archiveService.getArchivedClients(this.clientsPageIndex, this.clientsPageSize).subscribe({
      next: (page: PageResponse<ArchivedClient>) => {
        this.archivedClients = page.content;
        this.clientsTotalElements = page.totalElements;
        this.isLoadingClients = false;
      },
      error: () => {
        this.isLoadingClients = false;
        this.toastr.error('Failed to load archived clients');
      }
    });
  }

  onClientsPageChange(event: PageEvent): void {
    this.clientsPageIndex = event.pageIndex;
    this.clientsPageSize = event.pageSize;
    this.loadArchivedClients();
  }

  viewClient(client: ArchivedClient): void {
    this.selectedClient = client;
    this.selectedCampaign = undefined;
    this.archivedSends = [];
    this.sendsTotalElements = 0;
    this.campaignsPageIndex = 0;
    this.loadArchivedCampaigns();
  }

  restoreClient(client: ArchivedClient): void {
    if (!confirm(`Restore ${client.name}?`)) {
      return;
    }

    this.archiveService.restoreClient(client.id).subscribe({
      next: () => {
        this.toastr.success('Client restored successfully');
        if (this.selectedClient?.id === client.id) {
          this.selectedClient = undefined;
          this.selectedCampaign = undefined;
          this.archivedCampaigns = [];
          this.archivedSends = [];
        }
        this.loadArchivedClients();
      },
      error: () => {
        this.toastr.error('Failed to restore client');
      }
    });
  }

  loadArchivedCampaigns(): void {
    if (!this.selectedClient) {
      return;
    }

    this.isLoadingCampaigns = true;
    this.archiveService
      .getArchivedCampaigns(this.selectedClient.id, this.campaignsPageIndex, this.campaignsPageSize)
      .subscribe({
        next: (page) => {
          this.archivedCampaigns = page.content;
          this.campaignsTotalElements = page.totalElements;
          this.isLoadingCampaigns = false;
        },
        error: () => {
          this.isLoadingCampaigns = false;
          this.toastr.error('Failed to load archived campaigns');
        }
      });
  }

  onCampaignsPageChange(event: PageEvent): void {
    this.campaignsPageIndex = event.pageIndex;
    this.campaignsPageSize = event.pageSize;
    this.loadArchivedCampaigns();
  }

  viewCampaign(campaign: ArchivedCampaign): void {
    this.selectedCampaign = campaign;
    this.sendsPageIndex = 0;
    this.selectedStatus = 'ALL';
    this.loadArchivedSends();
  }

  loadArchivedSends(): void {
    if (!this.selectedClient || !this.selectedCampaign) {
      return;
    }

    this.isLoadingSends = true;
    this.archiveService
      .getArchivedCampaignSends(
        this.selectedClient.id,
        this.selectedCampaign.id,
        this.sendsPageIndex,
        this.sendsPageSize,
        this.selectedStatus
      )
      .subscribe({
        next: (page) => {
          this.archivedSends = page.content;
          this.sendsTotalElements = page.totalElements;
          this.isLoadingSends = false;
        },
        error: () => {
          this.isLoadingSends = false;
          this.toastr.error('Failed to load archived sends');
        }
      });
  }

  onSendsPageChange(event: PageEvent): void {
    this.sendsPageIndex = event.pageIndex;
    this.sendsPageSize = event.pageSize;
    this.loadArchivedSends();
  }

  onStatusFilterChange(status: CampaignSendStatus | 'ALL'): void {
    this.selectedStatus = status;
    this.sendsPageIndex = 0;
    this.loadArchivedSends();
  }
}
