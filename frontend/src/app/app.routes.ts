import { Routes } from '@angular/router';
import { MainLayoutComponent } from './shared/components/main-layout/main-layout.component';
import { DashboardHomeComponent } from './features/dashboard/dashboard-home/dashboard-home.component';
import { ClientListComponent } from './features/clients/client-list/client-list.component';
import { LeadListComponent } from './features/leads/lead-list/lead-list.component';
import { CampaignListComponent } from './features/campaigns/campaign-list/campaign-list.component';
import { CampaignDetailsComponent } from './features/campaigns/campaign-details/campaign-details.component';
import { SettingsPageComponent } from './features/settings/settings-page/settings-page.component';

export const routes: Routes = [
  {
    path: '',
    component: MainLayoutComponent,
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', component: DashboardHomeComponent, data: { animation: 'Dashboard' } },
      { path: 'clients', component: ClientListComponent, data: { animation: 'Clients' } },
      { path: 'leads', component: LeadListComponent, data: { animation: 'Leads' } },
      { path: 'campaigns', component: CampaignListComponent, data: { animation: 'Campaigns' } },
      { path: 'campaigns/:id', component: CampaignDetailsComponent, data: { animation: 'CampaignDetails' } },
      { path: 'settings', component: SettingsPageComponent, data: { animation: 'Settings' } }
    ]
  }
];
