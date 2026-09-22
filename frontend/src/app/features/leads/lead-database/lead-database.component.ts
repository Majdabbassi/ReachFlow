import { Component, ElementRef, ViewChild, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatMenuModule } from '@angular/material/menu';
import { ToastrService } from 'ngx-toastr';
import { Observable, catchError, finalize, map, of } from 'rxjs';
import { SelectionModel } from '@angular/cdk/collections';
import { LeadService } from '../../../core/services/lead.service';
import { Lead } from '../../../core/models/models';
import { ConfirmDialogComponent } from '../../../shared/components/confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-lead-database',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatCheckboxModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatPaginatorModule,
    MatDialogModule,
    MatTooltipModule,
    MatMenuModule
  ],
  templateUrl: './lead-database.component.html',
  styleUrl: './lead-database.component.scss'
})
export class LeadDatabaseComponent implements OnInit {
  private leadService = inject(LeadService);
  private dialog = inject(MatDialog);
  private toastr = inject(ToastrService);
  private fb = inject(FormBuilder);
  private currentLeads: Lead[] = [];
  @ViewChild('csvImportInput') csvImportInput?: ElementRef<HTMLInputElement>;

  selection = new SelectionModel<Lead>(true, []);

  isLoadingLeads = false;
  loadError: string | null = null;

  isAllSelected(leads: Lead[]) {
    const numSelected = this.selection.selected.length;
    const numRows = leads.length;
    return numSelected === numRows;
  }

  toggleAllRows(leads: Lead[]) {
    if (this.isAllSelected(leads)) {
      this.selection.clear();
    } else {
      leads.forEach(row => this.selection.select(row));
    }
  }

  toggleLeadRow(lead: Lead) {
    this.expandedElement = this.expandedElement === lead ? null : lead;
  }

  isLeadDetailRow = (_index: number, row: Lead) => this.expandedElement === row;

  hasLocationDetails(lead: Lead): boolean {
    return !!((lead.address) || (lead.latitude != null && lead.longitude != null));
  }

  exportToCsv() {
    const selected = this.selection.selected;
    if (selected.length === 0) {
      this.toastr.warning('Please select leads to export');
      return;
    }

    const headers = ['Email', 'Institution', 'City', 'Website', 'Source'];
    const csvContent = [
      headers.join(','),
      ...selected.map(l => [
        l.email,
        `"${l.institutionName}"`,
        `"${l.city}"`,
        l.website || '',
        l.source || ''
      ].join(','))
    ].join('\n');

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    const url = URL.createObjectURL(blob);
    link.setAttribute('href', url);
    link.setAttribute('download', `leads_export_${new Date().toISOString().slice(0, 10)}.csv`);
    link.style.visibility = 'hidden';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    this.toastr.success(`Exported ${selected.length} leads to CSV`);
  }

  deleteSelected() {
    const selected = this.selection.selected;
    if (selected.length === 0) return;

    const ids = selected.map(l => l.id).filter((id): id is number => id != null);
    if (ids.length === 0) return;

    if (!confirm(`Are you sure you want to delete ${ids.length} leads?`)) {
      return;
    }

    this.leadService.deleteLeads(ids).subscribe({
      next: (result) => {
        this.toastr.success(`Successfully deleted ${result.deletedCount} leads`);
        this.selection.clear();
        this.loadLeads();
      },
      error: () => {
        this.toastr.error('Failed to delete leads');
      }
    });
  }

  leads$: Observable<Lead[]> = of([]);
  displayedColumns: string[] = ['select', 'email', 'institution', 'city', 'phone', 'status', 'website', 'actions'];
  expandedElement: Lead | null = null;
  totalElements = 0;
  pageSize = 100;
  pageIndex = 0;

  filterForm: FormGroup = this.fb.group({
    city: [''],
    source: [''],
    keyword: [''],
    searchTerm: ['']
  });

  ngOnInit() {
    this.loadLeads();
  }

  loadLeads() {
    this.isLoadingLeads = true;
    this.loadError = null;

    const { city, source, searchTerm } = this.filterForm.value;

    this.leads$ = this.leadService.getLeads(this.pageIndex, this.pageSize, city, source).pipe(
      map(response => {
        this.totalElements = response.totalElements;
        let content = response.content;

        if (searchTerm) {
          const s = searchTerm.toLowerCase();
          content = content.filter(l =>
            l.institutionName?.toLowerCase().includes(s) ||
            l.email.toLowerCase().includes(s) ||
            l.website?.toLowerCase().includes(s)
          );
        }

        this.currentLeads = content;

        return content;
      }),
      catchError(err => {
        this.loadError = err.message || 'Failed to load leads';
        return of([]);
      }),
      finalize(() => this.isLoadingLeads = false)
    );
  }

  onPageChange(event: PageEvent) {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadLeads();
  }

  confirmDeleteLead(lead: Lead) {
    if (!lead.id) {
      return;
    }

    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      width: '420px',
      data: {
        title: 'Delete Lead',
        message: `Are you sure you want to delete ${lead.email}?`,
        confirmText: 'Delete',
        cancelText: 'Cancel'
      }
    });

    dialogRef.afterClosed().subscribe((confirmed: boolean) => {
      if (!confirmed) {
        return;
      }

      this.leadService.deleteLead(lead.id!).subscribe({
        next: () => {
          this.currentLeads = this.currentLeads.filter((item) => item.id !== lead.id);
          this.leads$ = of(this.currentLeads);
          this.selection.clear();
          this.toastr.success('Lead deleted successfully');
        },
        error: () => {
          this.toastr.error('Failed to delete lead');
        }
      });
    });
  }

  downloadAllEmailsFromDb() {
    this.leadService.downloadAllEmailsFile().subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `all-emails-${new Date().toISOString().slice(0, 10)}.txt`;
        link.click();
        URL.revokeObjectURL(url);
        this.toastr.success('All emails file downloaded');
      },
      error: () => {
        this.toastr.error('Failed to download all emails file');
      }
    });
  }

  downloadLeadsCsvFromDb() {
    this.leadService.exportLeadsCsv().subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `leads-${new Date().toISOString().slice(0, 10)}.csv`;
        link.click();
        URL.revokeObjectURL(url);
        this.toastr.success('Leads CSV downloaded');
      },
      error: () => {
        this.toastr.error('Failed to download leads CSV');
      }
    });
  }

  openCsvImportPicker() {
    this.csvImportInput?.nativeElement.click();
  }

  onCsvImportSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input?.files?.[0];
    if (!file) {
      return;
    }

    if (!file.name.toLowerCase().endsWith('.csv')) {
      this.toastr.error('Please select a CSV file');
      input.value = '';
      return;
    }

    this.leadService.importLeadsCsv(file).subscribe({
      next: (result) => {
        this.toastr.success(`Imported ${result.imported}, Skipped ${result.skipped}, Failed ${result.failed}`);
        if (result.errors?.length) {
          this.toastr.warning(`Import reported ${result.errors.length} row errors`);
        }
        input.value = '';
        this.loadLeads();
      },
      error: () => {
        this.toastr.error('Failed to import CSV');
        input.value = '';
      }
    });
  }

  downloadCsvTemplate() {
    const header = 'institutionName,city,phone,address,website,email,categories';
    const example = 'Example School,Berlin,+49 30 123456,Example Street 1,https://example-school.de,info@example-school.de|contact@example-school.de,Healthcare|Education';
    const csv = `${header}\n${example}`;
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'leads-import-template.csv';
    link.click();
    URL.revokeObjectURL(url);
  }
}