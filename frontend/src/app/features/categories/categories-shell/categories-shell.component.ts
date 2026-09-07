import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterOutlet, Router, NavigationEnd } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { filter, Subscription } from 'rxjs';

@Component({
  selector: 'app-categories-shell',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, MatIconModule],
  templateUrl: './categories-shell.component.html',
  styleUrl: './categories-shell.component.scss'
})
export class CategoriesShellComponent implements OnInit {
  private router = inject(Router);
  activePath = '';
  private subscription: Subscription = Subscription.EMPTY;

  ngOnInit() {
    this.activePath = this.router.url;
    this.subscription = this.router.events
      .pipe(filter((e) => e instanceof NavigationEnd))
      .subscribe(() => (this.activePath = this.router.url));
  }

  isStripActive(prefix: string): boolean {
    return this.activePath.startsWith(prefix);
  }
}
