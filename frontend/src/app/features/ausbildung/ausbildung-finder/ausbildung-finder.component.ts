import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ToastrService } from 'ngx-toastr';
import { Subscription, interval, switchMap, take } from 'rxjs';
import { CategoryService } from '../../../core/services/category.service';
import { LeadService } from '../../../core/services/lead.service';
import { N8nSettingsService } from '../../../core/services/n8n-settings.service';
import { ScrapeProgress } from '../../../core/models/models';

// One poll every 2 s: a little over the backend's 10-minute scraper timeout.
const MAX_SCRAPE_POLLS = 330;

/** Common German apprenticeship professions; each becomes the keyword "Ausbildung <profession>". */
const PROFESSIONS = [
  'Fachinformatiker Anwendungsentwicklung',
  'Fachinformatiker Systemintegration',
  'Kaufmann für Büromanagement',
  'Industriekaufmann',
  'Bankkaufmann',
  'Mechatroniker',
  'Elektroniker',
  'Anlagenmechaniker SHK',
  'Kfz-Mechatroniker',
  'Koch',
  'Hotelfachmann',
  'Pflegefachmann'
];

const POPULAR_CITIES = ['Berlin', 'Hamburg', 'München', 'Köln', 'Frankfurt am Main', 'Stuttgart', 'Düsseldorf', 'Leipzig'];

/**
 * The reason ReachFlow exists: find companies in Germany that train apprentices. It is a guided
 * front end for the same collection pipeline as "New Search" (one job, polled for progress); the
 * difference is that the leads are filed under the Ausbildung category right away, so the
 * campaign for that category picks them up.
 */
@Component({
  selector: 'app-ausbildung-finder',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterLink,
    MatButtonModule, MatCardModule, MatChipsModule, MatFormFieldModule,
    MatIconModule, MatInputModule, MatProgressBarModule
  ],
  templateUrl: './ausbildung-finder.component.html',
  styleUrl: './ausbildung-finder.component.scss'
})
export class AusbildungFinderComponent implements OnInit, OnDestroy {
  private leadService = inject(LeadService);
  private categoryService = inject(CategoryService);
  private n8nSettings = inject(N8nSettingsService);
  private toastr = inject(ToastrService);

  readonly professions = PROFESSIONS;
  readonly popularCities = POPULAR_CITIES;

  selectedProfessions = new Set<string>();
  cities: string[] = [];
  cityInput = '';
  maxResults = 30;

  ausbildungCategoryId: number | null = null;
  categoryMissing = false;
  categoryLoadFailed = false;

  isRunning = false;
  progress: ScrapeProgress | null = null;
  private polling?: Subscription;

  ngOnInit(): void {
    this.loadCategory();
  }

  loadCategory(): void {
    this.categoryLoadFailed = false;
    this.categoryService.getCategories().subscribe({
      next: (categories) => {
        const category = categories.find((c) => c.name.toLowerCase() === 'ausbildung');
        this.ausbildungCategoryId = category?.id ?? null;
        this.categoryMissing = !category;
      },
      error: () => {
        this.categoryLoadFailed = true;
      }
    });
  }

  ngOnDestroy(): void {
    this.polling?.unsubscribe();
  }

  get keywords(): string[] {
    return Array.from(this.selectedProfessions).map((profession) => `Ausbildung ${profession}`);
  }

  get searchCount(): number {
    return this.keywords.length * this.cities.length;
  }

  get canRun(): boolean {
    return !this.isRunning && this.searchCount > 0 && this.ausbildungCategoryId !== null;
  }

  get percent(): number {
    const found = this.progress?.leadsFound ?? 0;
    return found > 0 ? Math.round(((this.progress?.leadsImported ?? 0) / found) * 100) : 0;
  }

  toggleProfession(profession: string): void {
    if (this.selectedProfessions.has(profession)) {
      this.selectedProfessions.delete(profession);
    } else {
      this.selectedProfessions.add(profession);
    }
  }

  addCity(name: string): void {
    const city = name.trim();
    if (city && !this.cities.some((c) => c.toLowerCase() === city.toLowerCase())) {
      this.cities = [...this.cities, city];
    }
    this.cityInput = '';
  }

  removeCity(city: string): void {
    this.cities = this.cities.filter((c) => c !== city);
  }

  run(): void {
    if (!this.canRun) {
      return;
    }
    this.isRunning = true;
    this.progress = null;

    this.leadService.collectLeads({
      keywords: this.keywords,
      cities: this.cities,
      maxResults: this.maxResults,
      webhookUrl: this.n8nSettings.getWebhookUrl(),
      categoryIds: [this.ausbildungCategoryId as number]
    }).subscribe({
      next: (response) => {
        this.toastr.success('Search started');
        this.poll(response.jobId);
      },
      error: () => {
        this.isRunning = false;
        this.toastr.error('Failed to start the search');
      }
    });
  }

  private poll(jobId: string): void {
    this.polling?.unsubscribe();
    this.polling = interval(2000).pipe(
      switchMap(() => this.leadService.getScrapeProgress(jobId)),
      take(MAX_SCRAPE_POLLS)
    ).subscribe({
      next: (progress) => {
        this.progress = progress;
        if (progress.status === 'COMPLETED') {
          this.finish();
          this.toastr.success(`${progress.leadsImported ?? 0} training companies saved`);
        } else if (progress.status === 'FAILED') {
          this.finish();
          this.toastr.error(`Search failed: ${progress.errorMessage ?? 'unknown error'}`);
        }
      },
      error: () => {
        this.finish();
        this.toastr.error('Lost contact with the backend while waiting for the search');
      }
    });
  }

  private finish(): void {
    this.isRunning = false;
    this.polling?.unsubscribe();
  }
}
