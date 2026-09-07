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
        loadComponent: () => import('./features/leads/leads-shell/leads-shell.component').then((m) => m.LeadsShellComponent),
        data: { animation: 'Leads' },
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'database' },
          {
            path: 'new-search',
            loadComponent: () => import('./features/leads/lead-new-search/lead-new-search.component').then((m) => m.LeadNewSearchComponent),
            data: { animation: 'LeadsNewSearch' }
          },
          {
            path: 'combinations',
            loadComponent: () => import('./features/leads/lead-combinations/lead-combinations.component').then((m) => m.LeadCombinationsComponent),
            data: { animation: 'LeadsCombinations' }
          },
          {
            path: 'database',
            loadComponent: () => import('./features/leads/lead-database/lead-database.component').then((m) => m.LeadDatabaseComponent),
            data: { animation: 'LeadsDatabase' }
          }
        ]
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
        loadComponent: () => import('./features/categories/categories-shell/categories-shell.component').then((m) => m.CategoriesShellComponent),
        data: { animation: 'Categories' },
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'taxonomy' },
          {
            path: 'taxonomy',
            loadComponent: () => import('./features/categories/categories-keywords/categories-keywords.component').then((m) => m.CategoriesKeywordsComponent),
            data: { animation: 'CategoriesTaxonomy' }
          },
          {
            path: 'places',
            loadComponent: () => import('./features/categories/categories-places/categories-places.component').then((m) => m.CategoriesPlacesComponent),
            data: { animation: 'CategoriesPlaces' }
          }
        ]
      },
      {
        path: 'archive',
        loadComponent: () => import('./features/archive/archive.component').then((m) => m.ArchiveComponent),
        data: { animation: 'Archive' }
      },
      {
        path: 'ausbildung-finder',
        loadComponent: () => import('./features/ausbildung/ausbildung-finder/ausbildung-finder.component').then((m) => m.AusbildungFinderComponent),
        data: { animation: 'AusbildungFinder' }
      }
    ]
  }
];
