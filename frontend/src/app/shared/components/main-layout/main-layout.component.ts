import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet, ActivatedRoute } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { animate, query, style, transition, trigger } from '@angular/animations';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatButtonModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatDividerModule
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
export class MainLayoutComponent {
  constructor(protected route: ActivatedRoute) {}

  prepareRoute(outlet: RouterOutlet) {
    return outlet && outlet.activatedRouteData && outlet.activatedRouteData['animation'];
  }
}
