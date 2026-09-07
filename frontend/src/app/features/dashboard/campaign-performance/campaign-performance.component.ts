import { Component, Input, OnChanges, SimpleChanges, ElementRef, ViewChild, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Chart, registerables } from 'chart.js';

Chart.register(...registerables);

@Component({
  selector: 'app-campaign-performance',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="chart-container">
      <canvas #chartCanvas></canvas>

      <div class="chart-center" *ngIf="hasData">
        <div class="center-value" [class.empty]="!hasData">{{ deliveryRate }}%</div>
        <div class="center-label">Delivery Rate</div>
      </div>
    </div>
  `,
  styles: [`
    .chart-container {
      width: 100%;
      height: 300px;
      position: relative;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .chart-center {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      text-align: center;
      pointer-events: none;
      z-index: 2;
    }
    .center-value {
      font-family: var(--font-mono);
      font-size: 2rem;
      font-weight: 700;
      color: var(--color-text);
      line-height: 1;
    }
    .center-label {
      margin-top: 6px;
      font-size: 0.7rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: var(--color-text-muted);
    }
  `]
})
export class CampaignPerformanceComponent implements OnChanges, AfterViewInit {
  @Input() stats: any = {};
  @ViewChild('chartCanvas') chartCanvas!: ElementRef;

  private chart?: Chart;
  hasData = false;
  deliveryRate = 0;

  ngAfterViewInit() {
    this.initChart();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['stats']) {
      this.compute();
      if (this.chart) {
        this.updateChart();
      }
    }
  }

  private compute() {
    const stats = this.stats || {};
    const sent = stats.emailsSent || 0;
    const failed = stats.failedEmails || 0;
    const total = sent + failed;
    this.deliveryRate = total > 0 ? Math.round((sent / total) * 100) : 0;
    this.hasData = total > 0;
  }

  private initChart() {
    const ctx = this.chartCanvas.nativeElement.getContext('2d');
    this.chart = new Chart(ctx, {
      type: 'doughnut',
      data: {
        labels: ['Sent', 'Failed'],
        datasets: [{
          data: [0, 0],
          backgroundColor: ['#22D3C4', '#E5484D'],
          borderWidth: 0,
          hoverOffset: 10
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '78%',
        plugins: {
          legend: { display: false },
          tooltip: { enabled: false }
        }
      }
    });
    this.compute();
    if (!this.hasData) {
      this.setEmptyChart();
    }
    this.mapCanvasCenter();
  }

  private setEmptyChart() {
    if (!this.chart) return;
    this.chart.data.datasets[0].data = [1, 0];
    this.chart.data.datasets[0].backgroundColor = ['rgba(230,234,240,0.12)'];
    this.chart.update();
  }

  private updateChart() {
    if (!this.chart) return;
    if (this.hasData) {
      this.chart.data.datasets[0].data = [
        this.deliveryRate,
        100 - this.deliveryRate
      ];
      this.chart.data.datasets[0].backgroundColor = ['#22D3C4', '#E5484D'];
    } else {
      this.setEmptyChart();
    }
    this.chart.update();
  }

  private mapCanvasCenter() {
    const container = this.chartCanvas.nativeElement.parentElement;
    const wrapper = container?.parentElement;
    if (wrapper) {
      wrapper.style.display = 'flex';
      wrapper.style.alignItems = 'center';
      wrapper.style.justifyContent = 'center';
    }
  }
}
