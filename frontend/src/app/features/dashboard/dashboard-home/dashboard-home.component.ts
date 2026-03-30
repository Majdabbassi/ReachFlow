import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { ClientService } from '../../../core/services/client.service';
import { LeadService } from '../../../core/services/lead.service';
import { CampaignService } from '../../../core/services/campaign.service';
import { forkJoin } from 'rxjs';

@Component({
  selector: 'app-dashboard-home',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule],
  templateUrl: './dashboard-home.component.html',
  styleUrl: './dashboard-home.component.scss'
})
export class DashboardHomeComponent implements OnInit {
  private clientService = inject(ClientService);
  private leadService = inject(LeadService);
  private campaignService = inject(CampaignService);

  stats = {
    clients: 0,
    leads: 0,
    campaigns: 0,
    emailsSent: 0
  };

  ngOnInit() {
    forkJoin({
      clients: this.clientService.getClients(),
      leads: this.leadService.getLeads(0, 1),
      campaigns: this.campaignService.getCampaigns()
    }).subscribe(({ clients, leads, campaigns }) => {
      this.stats.clients = clients.length;
      this.stats.leads = leads.totalElements || 0;
      this.stats.campaigns = campaigns.length;
      
      // Calculate total emails sent across all campaigns
      campaigns.forEach(c => {
        this.campaignService.getStats(c.id!).subscribe(s => {
          this.stats.emailsSent += s.sent;
        });
      });
    });
  }
}
