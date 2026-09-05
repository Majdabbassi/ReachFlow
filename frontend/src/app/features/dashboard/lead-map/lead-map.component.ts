import { Component, Input, OnChanges, SimpleChanges, ElementRef, ViewChild, AfterViewInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Lead } from '../../../core/models/models';
import * as L from 'leaflet';

@Component({
  selector: 'app-lead-map',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="map-container">
      <div #mapElement class="map-frame"></div>
    </div>
  `,
  styles: [`
    .map-container {
      width: 100%;
      height: 400px;
      border-radius: 8px;
      overflow: hidden;
      box-shadow: 0 4px 6px rgba(0,0,0,0.1);
    }
    .map-frame {
      width: 100%;
      height: 100%;
    }
  `]
})
export class LeadMapComponent implements OnChanges, AfterViewInit, OnDestroy {
  @Input() leads: Lead[] = [];
  @ViewChild('mapElement') mapElement!: ElementRef;

  private map?: L.Map;
  private markers: L.Marker[] = [];

  ngAfterViewInit() {
    this.initMap();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['leads'] && this.map) {
      this.updateMarkers();
    }
  }

  ngOnDestroy() {
    if (this.map) {
      this.map.remove();
    }
  }

  private initMap() {
    this.map = L.map(this.mapElement.nativeElement).setView([51.1657, 10.4515], 6); // Center on Germany

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
    }).addTo(this.map);

    this.updateMarkers();
  }

  private updateMarkers() {
    if (!this.map) return;

    // Clear existing markers
    this.markers.forEach(marker => marker.remove());
    this.markers = [];

    const leadsWithCoords = this.leads.filter(lead => lead.latitude && lead.longitude);

    leadsWithCoords.forEach(lead => {
      const marker = L.marker([lead.latitude!, lead.longitude!])
        .bindPopup(`
          <strong>${lead.institutionName || 'Lead'}</strong><br>
          ${lead.city || ''}<br>
          <a href="${lead.website}" target="_blank">Website</a>
        `);
      
      marker.addTo(this.map!);
      this.markers.push(marker);
    });

    if (this.markers.length > 0) {
      const group = L.featureGroup(this.markers);
      this.map.fitBounds(group.getBounds().pad(0.1));
    }
  }
}
