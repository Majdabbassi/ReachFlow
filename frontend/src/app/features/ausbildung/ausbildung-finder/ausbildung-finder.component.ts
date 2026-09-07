import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-ausbildung-finder',
  standalone: true,
  imports: [MatCardModule, MatIconModule],
  templateUrl: './ausbildung-finder.component.html',
  styleUrl: './ausbildung-finder.component.scss'
})
export class AusbildungFinderComponent {}