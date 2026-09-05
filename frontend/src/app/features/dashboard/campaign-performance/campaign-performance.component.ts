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
    </div>
  `,
  styles: [`
    .chart-container {
      width: 100%;
      height: 300px;
      position: relative;
    }
  `]
})
export class CampaignPerformanceComponent implements OnChanges, AfterViewInit {
  @Input() stats: any = {};
  @ViewChild('chartCanvas') chartCanvas!: ElementRef;

  private chart?: Chart;

  ngAfterViewInit() {
    this.initChart();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['stats'] && this.chart) {
      this.updateChart();
    }
  }

  private initChart() {
    const ctx = this.chartCanvas.nativeElement.getContext('2d');
    this.chart = new Chart(ctx, {
      type: 'doughnut',
      data: {
        labels: ['Sent', 'Replied', 'Pending', 'Failed', 'Bounced'],
        datasets: [{
          data: [0, 0, 0, 0, 0],
          backgroundColor: [
            '#6366f1', // Sent
            '#10b981', // Replied
            '#f59e0b', // Pending
            '#ef4444', // Failed
            '#94a3b8'  // Bounced
          ],
          borderWidth: 0,
          hoverOffset: 10
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            position: 'bottom',
            labels: {
              color: '#94a3b8',
              usePointStyle: true,
              padding: 20,
              font: {
                size: 12,
                weight: 'bold'
              }
            }
          },
          tooltip: {
            backgroundColor: 'rgba(15, 23, 42, 0.9)',
            titleColor: '#f1f5f9',
            bodyColor: '#cbd5e1',
            padding: 12,
            cornerRadius: 8,
            displayColors: true
          }
        },
        cutout: '70%'
      }
    });
    this.updateChart();
  }

  private updateChart() {
    if (!this.chart || !this.stats) return;

    this.chart.data.datasets[0].data = [
      this.stats.emailsSent || 0,
      this.stats.repliedEmails || 0,
      this.stats.pendingEmails || 0,
      this.stats.failedEmails || 0,
      this.stats.bouncedEmails || 0
    ];
    this.chart.update();
  }
}
