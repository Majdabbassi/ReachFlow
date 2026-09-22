import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { SelectionModel } from '@angular/cdk/collections';
import { CampaignService } from '../../../core/services/campaign.service';
import { Campaign, CampaignStatus } from '../../../core/models/models';
import { ToastrService } from 'ngx-toastr';
import { of, BehaviorSubject } from 'rxjs';
import { catchError, finalize, map } from 'rxjs/operators';

import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-campaign-list',
  standalone: true,
  imports: [
    CommonModule, 
    FormsModule,
    MatTableModule, 
    MatButtonModule, 
    MatIconModule, 
    MatCardModule, 
    MatDialogModule, 
    MatChipsModule, 
    MatTooltipModule, 
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatProgressSpinnerModule,
    RouterLink
  ],
  templateUrl: './campaign-list.component.html',
  styleUrl: './campaign-list.component.scss'
})
export class CampaignListComponent implements OnInit {
  private campaignService = inject(CampaignService);
  private toastr = inject(ToastrService);

  campaignsSubject = new BehaviorSubject<Campaign[]>([]);
  campaigns$ = this.campaignsSubject.asObservable();
  
  displayedColumns: string[] = ['select', 'name', 'status', 'createdAt', 'actions'];
  selection = new SelectionModel<Campaign>(true, []);
  
  isLoading = false;
  loadError: string | null = null;
  searchTerm = '';
  selectedStatus: CampaignStatus | 'ALL' = 'ALL';
  campaignStatusOptions: Array<CampaignStatus | 'ALL'> = ['ALL', CampaignStatus.DRAFT, CampaignStatus.RUNNING, CampaignStatus.COMPLETED];

  ngOnInit() {
    this.loadCampaigns();
  }

  loadCampaigns() {
    this.isLoading = true;
    this.loadError = null;
    this.campaignService.loadCampaigns().pipe(
      catchError((err) => {
        this.loadError = err?.message || 'Failed to load campaigns';
        this.toastr.error('Failed to load campaigns');
        return of([]);
      }),
      finalize(() => {
        this.isLoading = false;
      })
    ).subscribe(campaigns => {
      this.campaignsSubject.next(campaigns);
    });
  }

  get filteredCampaigns$() {
    return this.campaigns$.pipe(
      map(campaigns => {
        const s = this.searchTerm.trim().toLowerCase();
        return campaigns.filter(campaign => {
          const matchesName = !s || campaign.name.toLowerCase().includes(s);
          const matchesStatus = this.selectedStatus === 'ALL' || campaign.status === this.selectedStatus;
          return matchesName && matchesStatus;
        });
      })
    );
  }

  getStatusColor(status: CampaignStatus): string {
    switch (status) {
      case CampaignStatus.DRAFT: return 'secondary';
      case CampaignStatus.RUNNING: return 'primary';
      case CampaignStatus.COMPLETED: return 'accent';
      default: return 'secondary';
    }
  }

  // Selection
  isAllSelected(campaigns: Campaign[]) {
    const numSelected = this.selection.selected.length;
    const numRows = campaigns.length;
    return numSelected === numRows;
  }

  toggleAllRows(campaigns: Campaign[]) {
    if (this.isAllSelected(campaigns)) {
      this.selection.clear();
    } else {
      campaigns.forEach(row => this.selection.select(row));
    }
  }

  deleteSelected() {
    const selected = this.selection.selected;
    if (selected.length === 0) return;

    const ids = selected.map(c => c.id).filter((id): id is number => id != null);
    if (ids.length === 0) return;

    if (!confirm(`Are you sure you want to delete ${ids.length} campaigns?`)) {
      return;
    }

    this.campaignService.deleteCampaigns(ids).subscribe({
      next: (result) => {
        this.toastr.success(`Successfully deleted ${result.deletedCount} campaigns`);
        this.selection.clear();
        this.loadCampaigns();
      },
      error: () => {
        this.toastr.error('Failed to delete campaigns');
      }
    });
  }

}
