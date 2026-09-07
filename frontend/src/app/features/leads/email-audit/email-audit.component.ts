import { SelectionModel } from '@angular/cdk/collections';
import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { ToastrService } from 'ngx-toastr';
import { finalize, forkJoin } from 'rxjs';
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
    MatProgressSpinnerModule,
    MatChipsModule
  ],
  templateUrl: './email-audit.component.html',
  styleUrl: './email-audit.component.scss'
})
export class EmailAuditComponent implements OnInit {
  private leadService = inject(LeadService);
  private toastr = inject(ToastrService);

  issueFilter: 'all' | 'invalid' | 'duplicate' = 'all';
  isLoading = false;
  rows: EmailAuditItem[] = [];
  allRows: EmailAuditItem[] = [];
  invalidTotal = 0;
  duplicateTotal = 0;
  pageSize = 20;
  pageIndex = 0;

  selection = new SelectionModel<EmailAuditItem>(true, []);

  displayedColumns: string[] = ['select', 'email', 'institution', 'leadId', 'primary', 'issue', 'duplicateCount'];

  get selectedCount(): number {
    return this.selection.selected.length;
  }

  get totalElements(): number {
    return this.invalidTotal + this.duplicateTotal;
  }

  ngOnInit(): void {
    this.loadAudit();
  }

  onIssueFilterChange(filter: 'all' | 'invalid' | 'duplicate'): void {
    this.issueFilter = filter;
    this.selection.clear();
    this.applyFilter();
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.selection.clear();
    this.loadAudit();
  }

  loadAudit(): void {
    this.isLoading = true;
    forkJoin({
      invalid: this.leadService.getEmailAudit('invalid', this.pageIndex, this.pageSize),
      duplicate: this.leadService.getEmailAudit('duplicate', this.pageIndex, this.pageSize)
    })
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: ({ invalid, duplicate }) => {
          this.invalidTotal = invalid.totalElements || 0;
          this.duplicateTotal = duplicate.totalElements || 0;
          this.allRows = [...(invalid.content || []), ...(duplicate.content || [])];
          this.applyFilter();
        },
        error: () => {
          this.toastr.error('Failed to load email audit data');
          this.allRows = [];
          this.rows = [];
          this.invalidTotal = 0;
          this.duplicateTotal = 0;
        }
      });
  }

  private applyFilter(): void {
    if (this.issueFilter === 'invalid') {
      this.rows = this.allRows.filter((row) => row.issueType === 'INVALID');
    } else if (this.issueFilter === 'duplicate') {
      this.rows = this.allRows.filter((row) => row.issueType === 'DUPLICATE');
    } else {
      this.rows = [...this.allRows];
    }
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
    link.download = `email-audit-selected-${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    URL.revokeObjectURL(url);

    this.toastr.success(`Exported ${rows.length} selected email(s)`);
  }

  private escapeCsv(value: string): string {
    const normalized = (value || '').replace(/"/g, '""');
    return `"${normalized}"`;
  }
}