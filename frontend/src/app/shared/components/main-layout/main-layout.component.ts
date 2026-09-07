import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet, ActivatedRoute, Router, NavigationEnd } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { CommonModule } from '@angular/common';
import { animate, query, style, transition, trigger } from '@angular/animations';
import { LoadingService } from '../../../core/services/loading.service';
import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { filter, map, shareReplay } from 'rxjs/operators';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatButtonModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatDividerModule,
    MatProgressBarModule
  ],
  templateUrl: './main-layout.component.html',
  styleUrl: './main-layout.component.scss',
  animations: [
    trigger('routeAnimations', [
      transition('* <=> *', [
        query(':enter, :leave', [
          style({
            position: 'absolute',
            left: 0,
            width: '100%',
            opacity: 0,
            transform: 'translateY(10px)',
          })
        ], { optional: true }),
        query(':enter', [
          animate('300ms cubic-bezier(0.4, 0, 0.2, 1)', style({ opacity: 1, transform: 'translateY(0)' }))
        ], { optional: true })
      ])
    ])
  ]
})
export class MainLayoutComponent implements OnInit, OnDestroy {
  private loadingService = inject(LoadingService);
  private breakpointObserver = inject(BreakpointObserver);
  private router = inject(Router);
  private navigationSubscription: Subscription = Subscription.EMPTY;

  loading$ = this.loadingService.loading$;

  isHandset$ = this.breakpointObserver.observe(Breakpoints.Handset)
    .pipe(
      map(result => result.matches),
      shareReplay()
    );

  user = {
    name: 'Majd Abbassi',
    role: 'Administrator',
    initials: 'MA'
  };

  leadsExpanded = true;
  categoriesExpanded = false;
  leadsGroupActive = false;
  categoriesGroupActive = false;

  constructor(protected route: ActivatedRoute) {}

  ngOnInit() {
    this.navigationSubscription = this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe(() => {
        const url = this.router.url;
        this.leadsGroupActive = ['/leads/new-search', '/leads/combinations', '/leads/database'].some((path) => url.startsWith(path));
        this.categoriesGroupActive = url.startsWith('/categories/taxonomy') || url.startsWith('/categories/places');
        if (this.leadsGroupActive) {
          this.leadsExpanded = true;
        }
        if (this.categoriesGroupActive) {
          this.categoriesExpanded = true;
        }
      });
  }

  ngOnDestroy() {
    this.navigationSubscription.unsubscribe();
  }

  toggleLeadsGroup() {
    this.leadsExpanded = !this.leadsExpanded;
  }

  toggleCategoriesGroup() {
    this.categoriesExpanded = !this.categoriesExpanded;
  }

  prepareRoute(outlet: RouterOutlet) {
    return outlet && outlet.activatedRouteData && outlet.activatedRouteData['animation'];
  }

  closeSidenav(drawer: any) {
    this.isHandset$.subscribe(isHandset => {
      if (isHandset) {
        drawer.close();
      }
    });
  }
}
