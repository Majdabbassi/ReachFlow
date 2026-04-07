import { SelectionModel } from '@angular/cdk/collections';
import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleChange, MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { ToastrService } from 'ngx-toastr';
import { finalize } from 'rxjs';
import { EmailAuditItem } from '../../../core/models/models';
import { LeadService } from '../../../core/services/lead.service';

@Component({
  selector: 'app-email-audit',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatTableModule,
    MatPaginatorModule,
    MatCheckboxModule,
    MatButtonModule,
    MatIconModule,
    MatButtonToggleModule,
    MatProgressSpinnerModule,
    MatChipsModule
  ],
  templateUrl: './email-audit.component.html',
  styleUrl: './email-audit.component.scss'
})
export class EmailAuditComponent implements OnInit {
  private leadService = inject(LeadService);
  private toastr = inject(ToastrService);

  mode: 'invalid' | 'duplicate' = 'invalid';
  isLoading = false;
  rows: EmailAuditItem[] = [];
  totalElements = 0;
  pageSize = 20;
  pageIndex = 0;

  selection = new SelectionModel<EmailAuditItem>(true, []);

  get displayedColumns(): string[] {
    const baseColumns = ['select', 'email', 'institution', 'leadId', 'primary', 'issue'];
    return this.mode === 'duplicate' ? [...baseColumns, 'duplicateCount'] : baseColumns;
  }

  get selectedCount(): number {
    return this.selection.selected.length;
  }

  ngOnInit(): void {
    this.loadAudit();
  }

  onModeChange(event: MatButtonToggleChange): void {
    this.mode = event.value;
    this.pageIndex = 0;
    this.selection.clear();
    this.loadAudit();
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.selection.clear();
    this.loadAudit();
  }

  loadAudit(): void {
    this.isLoading = true;
    this.leadService
      .getEmailAudit(this.mode, this.pageIndex, this.pageSize)
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: (response) => {
          this.rows = response.content || [];
          this.totalElements = response.totalElements || 0;
        },
        error: () => {
          this.toastr.error('Failed to load email audit data');
          this.rows = [];
          this.totalElements = 0;
        }
      });
  }

  isAllSelected(): boolean {
    return this.rows.length > 0 && this.selection.selected.length === this.rows.length;
  }

  toggleAll(): void {
    if (this.isAllSelected()) {
      this.selection.clear();
      return;
    }
    this.rows.forEach((row) => this.selection.select(row));
  }

  deleteSelected(): void {
    const ids = this.selection.selected.map((row) => row.id);
    if (ids.length === 0) {
      this.toastr.warning('Select at least one email');
      return;
    }

    this.leadService.deleteLeadEmails(ids).subscribe({
      next: (result) => {
        if (result.deletedCount > 0) {
          this.toastr.success(`Deleted ${result.deletedCount} email(s)`);
        }
        if (result.skippedCount > 0) {
          this.toastr.warning(`${result.skippedCount} email(s) were skipped because they are the last email of their lead`);
        }
        this.selection.clear();
        this.loadAudit();
      },
      error: () => {
        this.toastr.error('Failed to delete selected emails');
      }
    });
  }

  exportSelected(): void {
    if (this.selectedCount === 0) {
      this.toastr.warning('Select at least one email to export');
      return;
    }

    const rows = this.selection.selected;
    const header = ['emailId', 'leadId', 'email', 'institution', 'isPrimary', 'issueType', 'duplicateCount'];
    const csvRows = rows.map((row) => [
      row.id,
      row.leadId,
      this.escapeCsv(row.email),
      this.escapeCsv(row.institutionName || ''),
      row.primary ? 'true' : 'false',
      row.issueType,
      row.duplicateCount
    ].join(','));

    const csv = [header.join(','), ...csvRows].join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `email-audit-selected-${this.mode}-${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    URL.revokeObjectURL(url);

    this.toastr.success(`Exported ${rows.length} selected email(s)`);
  }

  private escapeCsv(value: string): string {
    const normalized = (value || '').replace(/"/g, '""');
    return `"${normalized}"`;
  }
}
