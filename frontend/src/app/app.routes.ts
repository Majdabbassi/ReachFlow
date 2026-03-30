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
      { path: 'dashboard', component: DashboardHomeComponent },
      { path: 'clients', component: ClientListComponent },
      { path: 'leads', component: LeadListComponent },
      { path: 'campaigns', component: CampaignListComponent },
      { path: 'campaigns/:id', component: CampaignDetailsComponent },
      { path: 'settings', component: SettingsPageComponent }
    ]
  }
];
