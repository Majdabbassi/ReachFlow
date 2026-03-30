import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatChipsModule } from '@angular/material/chips';
import { RouterLink } from '@angular/router';
import { CampaignService } from '../../../core/services/campaign.service';
import { Campaign, CampaignStatus } from '../../../core/models/models';
import { CampaignDialogComponent } from '../campaign-dialog/campaign-dialog.component';
import { ToastrService } from 'ngx-toastr';

@Component({
  selector: 'app-campaign-list',
  standalone: true,
  imports: [CommonModule, MatTableModule, MatButtonModule, MatIconModule, MatDialogModule, MatChipsModule, RouterLink],
  templateUrl: './campaign-list.component.html',
  styleUrl: './campaign-list.component.scss'
})
export class CampaignListComponent implements OnInit {
  private campaignService = inject(CampaignService);
  private dialog = inject(MatDialog);
  private toastr = inject(ToastrService);

  campaigns: Campaign[] = [];
  displayedColumns: string[] = ['name', 'status', 'createdAt', 'actions'];

  ngOnInit() {
    this.loadCampaigns();
  }

  loadCampaigns() {
    this.campaignService.getCampaigns().subscribe(campaigns => this.campaigns = campaigns);
  }

  getStatusColor(status: CampaignStatus): string {
    switch (status) {
      case CampaignStatus.DRAFT: return 'secondary';
      case CampaignStatus.RUNNING: return 'primary';
      case CampaignStatus.COMPLETED: return 'accent';
      default: return 'secondary';
    }
  }

  openCampaignDialog() {
    const dialogRef = this.dialog.open(CampaignDialogComponent, {
      width: '500px'
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.campaignService.createCampaign(result).subscribe(() => {
          this.toastr.success('Campaign created successfully');
          this.loadCampaigns();
        });
      }
    });
  }
}
