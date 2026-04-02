import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { FormsModule } from '@angular/forms';
import { ClientService } from '../../../core/services/client.service';
import { ArchiveService } from '../../../core/services/archive.service';
import { Client } from '../../../core/models/models';
import { ClientDialogComponent } from '../client-dialog/client-dialog.component';
import { ToastrService } from 'ngx-toastr';
import { Observable, of, BehaviorSubject, forkJoin } from 'rxjs';
import { catchError, finalize, map, switchMap } from 'rxjs/operators';

@Component({
  selector: 'app-client-list',
  standalone: true,
  imports: [
    CommonModule, 
    FormsModule,
    MatTableModule, 
    MatButtonModule, 
    MatIconModule, 
    MatDialogModule, 
    MatCardModule, 
    MatChipsModule,
    MatTooltipModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './client-list.component.html',
  styleUrl: './client-list.component.scss'
})
export class ClientListComponent implements OnInit {
  private clientService = inject(ClientService);
  private archiveService = inject(ArchiveService);
  private dialog = inject(MatDialog);
  private toastr = inject(ToastrService);

  private clientsSubject = new BehaviorSubject<Client[]>([]);
  clients$ = this.clientsSubject.asObservable();
  
  displayedColumns: string[] = ['name', 'email', 'categories', 'phone', 'actions'];
  selectedClientId?: number;
  isLoading = false;
  loadError: string | null = null;
  searchTerm = '';

  ngOnInit() {
    this.loadClients();
  }

  loadClients() {
    this.isLoading = true;
    this.loadError = null;
    this.clientService.loadClients().pipe(
      catchError((err) => {
        this.loadError = err?.message || 'Failed to load clients';
        this.toastr.error('Failed to load clients');
        return of([]);
      }),
      finalize(() => {
        this.isLoading = false;
      })
    ).subscribe(clients => {
      this.clientsSubject.next(clients);
    });
  }

  get filteredClients$() {
    return this.clients$.pipe(
      map(clients => {
        if (!this.searchTerm) return clients;
        const s = this.searchTerm.toLowerCase();
        return clients.filter(c => 
          c.name.toLowerCase().includes(s) || 
          c.email.toLowerCase().includes(s) || 
          (c.phone && c.phone.includes(s))
        );
      })
    );
  }

  openClientDialog(client?: Client) {
    const dialogRef = this.dialog.open(ClientDialogComponent, {
      width: '500px',
      data: client || {}
    });

    dialogRef.afterClosed().pipe(
      switchMap((result) => {
        if (!result) {
          return of(null);
        }

        const saveRequest = client?.id
          ? this.clientService.updateClient(client.id, result.client)
          : this.clientService.createClient(result.client);

        return saveRequest.pipe(
          switchMap((savedClient) => {
            const uploads = (result.categoryDocuments || []).map((document: { categoryId: number; file: File }) =>
              this.clientService.uploadCategoryDocument(savedClient.id!, document.categoryId, document.file)
            );

            if (!uploads.length) {
              return of(savedClient);
            }

            return forkJoin(uploads).pipe(map(() => savedClient));
          })
        );
      })
    ).subscribe((saved) => {
      if (saved) {
        this.toastr.success(client?.id ? 'Client updated successfully' : 'Client created successfully');
        this.loadClients();
      }
    });
  }

  scanSentEmails(client: Client) {
    if (!client.id) {
      return;
    }

    this.clientService.scanSentEmails(client.id).subscribe({
      next: (result) => {
        this.toastr.success(`Scanned ${result.scannedCount} addresses, marked ${result.markedAsSentCount} as sent`);
      },
      error: () => {
        this.toastr.error('Failed to scan sent emails');
      }
    });
  }

  archiveClient(client: Client) {
    if (!client.id) {
      return;
    }

    if (!confirm(`Archive ${client.name}? This will move the client and its campaigns to archive.`)) {
      return;
    }

    this.archiveService.archiveClient(client.id).subscribe({
      next: () => {
        this.toastr.success('Client archived successfully');
        this.loadClients();
      },
      error: () => {
        this.toastr.error('Failed to archive client');
      }
    });
  }
}
