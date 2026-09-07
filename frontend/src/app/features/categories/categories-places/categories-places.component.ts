import { CommonModule, NgIf } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatExpansionModule } from '@angular/material/expansion';
import { ToastrService } from 'ngx-toastr';
import { PlaceCountryTree, PlaceDistrictTree, PlaceStateTree } from '../../../core/models/models';
import { LeadService } from '../../../core/services/lead.service';

@Component({
  selector: 'app-categories-places',
  standalone: true,
  imports: [
    CommonModule,
    NgIf,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatExpansionModule
  ],
  templateUrl: './categories-places.component.html',
  styleUrl: './categories-places.component.scss'
})
export class CategoriesPlacesComponent implements OnInit {
  private leadService = inject(LeadService);
  private toastr = inject(ToastrService);

  placesLoading = false;
  placesSeeding = false;
  placeCountries: PlaceCountryTree[] = [];

  ngOnInit() {
    this.loadPlaces();
  }

  loadPlaces() {
    this.placesLoading = true;
    this.leadService.getPlaceTree('DE').subscribe({
      next: (countries) => {
        this.placeCountries = countries;
        this.placesLoading = false;
      },
      error: () => {
        this.placesLoading = false;
        this.toastr.error('Failed to load places from database');
      }
    });
  }

  seedPlaces() {
    this.placesSeeding = true;
    this.leadService.seedGermanyPlaces().subscribe({
      next: () => {
        this.toastr.success('Germany places seeded successfully');
        this.placesSeeding = false;
        this.loadPlaces();
      },
      error: () => {
        this.placesSeeding = false;
        this.toastr.error('Failed to seed Germany places');
      }
    });
  }

  get placeStatesCount(): number {
    return this.placeCountries.reduce((sum, country) => sum + (country.states?.length || 0), 0);
  }

  get placeCitiesCount(): number {
    return this.placeCountries.reduce(
      (sum, country) => sum + (country.states || []).reduce((stateSum, state) => stateSum + (state.cities?.length || 0), 0),
      0
    );
  }

  get placeDistrictsCount(): number {
    return this.placeCountries.reduce(
      (sum, country) => sum + (country.states || []).reduce(
        (stateSum, state) => stateSum + (state.cities || []).reduce(
          (citySum, city) => citySum + (city.districts?.length || 0),
          0
        ),
        0
      ),
      0
    );
  }

  trackCountry(_index: number, country: PlaceCountryTree): string {
    return `${country.code}-${country.id}`;
  }

  trackState(_index: number, state: PlaceStateTree): number {
    return state.id;
  }

  getDistrictNames(districts: PlaceDistrictTree[]): string {
    return districts.map((district) => district.name).join(', ');
  }
}