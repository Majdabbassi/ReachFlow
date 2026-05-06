import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { EmailTemplateService, EmailTemplate } from '../../../core/services/email-template.service';
import { ToastrService } from 'ngx-toastr';
import { Observable, of } from 'rxjs';

@Component({
  selector: 'app-template-loader-dialog',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatListModule
  ],
  template: `
    <div class="template-dialog">
      <h2 mat-dialog-title>
        <mat-icon>mail_outline</mat-icon>
        Select Email Template
      </h2>

      <mat-dialog-content>
        <div class="templates-list" *ngIf="(templates$ | async) as templates">
          <div *ngIf="templates.length === 0" class="empty-state">
            <p>No templates available.</p>
          </div>

          <mat-card 
            *ngFor="let template of templates"
            class="template-card"
            (click)="selectTemplate(template)">
            <mat-card-header>
              <mat-card-title>{{ template.name }}</mat-card-title>
              <mat-card-subtitle *ngIf="template.createdAt">
                Created {{ template.createdAt | date:'short' }}
              </mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <p class="subject-preview"><strong>Subject:</strong> {{ template.subject }}</p>
              <div class="body-preview" [innerHTML]="getBodyPreview(template.body)"></div>
            </mat-card-content>
            <mat-card-footer>
              <button mat-stroked-button color="primary" (click)="selectTemplate(template); $event.stopPropagation()">
                <mat-icon>check_circle</mat-icon>
                Use This Template
              </button>
            </mat-card-footer>
          </mat-card>
        </div>
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button mat-button (click)="onCancel()">Cancel</button>
      </mat-dialog-actions>
    </div>
  `,
  styles: [`
    .template-dialog {
      min-width: 500px;
      max-width: 700px;
    }

    h2 {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 16px;
    }

    mat-dialog-content {
      max-height: 60vh;
      overflow-y: auto;
    }

    .templates-list {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .template-card {
      cursor: pointer;
      transition: all 0.2s ease;
      padding: 16px;

      &:hover {
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
        transform: translateY(-2px);
      }
    }

    mat-card-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 12px;
    }

    mat-card-title {
      font-size: 16px;
      font-weight: 600;
    }

    mat-card-subtitle {
      font-size: 12px;
      color: #999;
    }

    .subject-preview {
      margin: 0 0 8px;
      font-size: 13px;
    }

    .body-preview {
      background: #f5f5f5;
      border-radius: 4px;
      padding: 8px;
      font-size: 12px;
      max-height: 80px;
      overflow: hidden;
      text-overflow: ellipsis;
      color: #666;
      line-height: 1.3;

      ::ng-deep p {
        margin: 4px 0;
      }
    }

    mat-card-footer {
      margin-top: 12px;
      padding-top: 12px;
      border-top: 1px solid #eee;
    }

    .empty-state {
      text-align: center;
      padding: 40px 20px;
      color: #999;
    }

    mat-dialog-actions {
      margin-top: 24px;
    }
  `]
})
export class TemplateLoaderDialogComponent implements OnInit {
  private templateService = inject(EmailTemplateService);
  private dialogRef = inject(MatDialogRef<TemplateLoaderDialogComponent>);
  private toastr = inject(ToastrService);

  templates$: Observable<EmailTemplate[]> = of([]);

  ngOnInit() {
    this.templates$ = this.templateService.getTemplates();
  }

  selectTemplate(template: EmailTemplate) {
    this.dialogRef.close({
      subject: template.subject,
      body: template.body,
      delaySeconds: template.delaySeconds || 2,
      htmlBody: template.htmlBody || false
    });
  }

  onCancel() {
    this.dialogRef.close();
  }

  getBodyPreview(body: string): string {
    // Strip HTML tags for preview
    const text = body.replace(/<[^>]*>/g, '');
    return text.substring(0, 100) + (text.length > 100 ? '...' : '');
  }
}
