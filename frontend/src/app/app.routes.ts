import { Routes } from '@angular/router';
import { MainLayoutComponent } from './shared/components/main-layout/main-layout.component';

export const routes: Routes = [
  {
    path: '',
    component: MainLayoutComponent,
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard-home/dashboard-home.component').then((m) => m.DashboardHomeComponent),
        data: { animation: 'Dashboard' }
      },
      {
        path: 'clients',
        loadComponent: () => import('./features/clients/client-list/client-list.component').then((m) => m.ClientListComponent),
        data: { animation: 'Clients' }
      },
      {
        path: 'leads',
        loadComponent: () => import('./features/leads/lead-list/lead-list.component').then((m) => m.LeadListComponent),
        data: { animation: 'Leads' }
      },
      {
        path: 'leads/email-audit',
        loadComponent: () => import('./features/leads/email-audit/email-audit.component').then((m) => m.EmailAuditComponent),
        data: { animation: 'EmailAudit' }
      },
      {
        path: 'campaigns',
        loadComponent: () => import('./features/campaigns/campaign-list/campaign-list.component').then((m) => m.CampaignListComponent),
        data: { animation: 'Campaigns' }
      },
      {
        path: 'campaigns/:id',
        loadComponent: () => import('./features/campaigns/campaign-details/campaign-details.component').then((m) => m.CampaignDetailsComponent),
        data: { animation: 'CampaignDetails' }
      },
      {
        path: 'categories',
        loadComponent: () => import('./features/categories/categories.component').then((m) => m.CategoriesComponent),
        data: { animation: 'Categories' }
      },
      {
        path: 'archive',
        loadComponent: () => import('./features/archive/archive.component').then((m) => m.ArchiveComponent),
        data: { animation: 'Archive' }
      }
    ]
  }
];
