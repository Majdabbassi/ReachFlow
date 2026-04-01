import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-skeleton-loader',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="skeleton-wrapper" [class.animate]="animate" [style.height]="height" [style.width]="width" [style.border-radius]="borderRadius">
      <div class="skeleton-shimmer"></div>
    </div>
  `,
  styles: [`
    :host {
      display: inline-block;
      width: 100%;
    }
    .skeleton-wrapper {
      position: relative;
      overflow: hidden;
      background-color: rgba(255, 255, 255, 0.05);
      width: 100%;
      height: 20px;
      border-radius: 4px;
    }
    .skeleton-shimmer {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: linear-gradient(90deg, 
        transparent 0%, 
        rgba(255, 255, 255, 0.03) 50%, 
        transparent 100%
      );
      transform: translateX(-100%);
    }
    .animate .skeleton-shimmer {
      animation: shimmer 1.5s infinite;
    }
    @keyframes shimmer {
      100% {
        transform: translateX(100%);
      }
    }
  `]
})
export class SkeletonLoaderComponent {
  @Input() height = '20px';
  @Input() width = '100%';
  @Input() borderRadius = '4px';
  @Input() animate = true;
}
