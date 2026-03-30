import { Component, inject, OnInit, ViewChild, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatPaginator, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { LeadService } from '../../../core/services/lead.service';
import { Lead } from '../../../core/models/models';
import * as L from 'leaflet';

@Component({
  selector: 'app-lead-list',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    ReactiveFormsModule
  ],
  templateUrl: './lead-list.component.html',
  styleUrl: './lead-list.component.scss'
})
export class LeadListComponent implements OnInit, AfterViewInit {
  private leadService = inject(LeadService);
  private fb = inject(FormBuilder);

  leads: Lead[] = [];
  displayedColumns: string[] = ['email', 'institution', 'city', 'website'];
  totalElements = 0;
  pageSize = 10;
  currentPage = 0;
  
  filterForm: FormGroup = this.fb.group({
    city: [''],
    source: ['']
  });

  private map?: L.Map;

  ngOnInit() {
    this.loadLeads();
  }

  ngAfterViewInit() {
    this.initMap();
  }

  initMap() {
    this.map = L.map('map').setView([0, 0], 2);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap contributors'
    }).addTo(this.map);
  }

  loadLeads() {
    const { city, source } = this.filterForm.value;
    this.leadService.getLeads(this.currentPage, this.pageSize, city, source).subscribe(res => {
      this.leads = res.content;
      this.totalElements = res.totalElements;
      this.updateMarkers();
    });
  }

  updateMarkers() {
    if (!this.map) return;

    // Clear existing markers if needed
    this.map.eachLayer((layer) => {
      if (layer instanceof L.Marker) {
        this.map?.removeLayer(layer);
      }
    });

    const markers: L.Marker[] = [];
    this.leads.forEach(lead => {
      if (lead.latitude && lead.longitude) {
        const marker = L.marker([lead.latitude, lead.longitude])
          .bindPopup(`<b>${lead.institutionName || lead.email}</b><br>${lead.address || lead.city || ''}`);
        markers.push(marker);
        marker.addTo(this.map!);
      }
    });

    if (markers.length > 0) {
      const group = L.featureGroup(markers);
      this.map.fitBounds(group.getBounds().pad(0.1));
    }
  }

  onPageChange(event: PageEvent) {
    this.currentPage = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadLeads();
  }
}
