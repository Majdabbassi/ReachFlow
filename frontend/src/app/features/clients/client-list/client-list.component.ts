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
import { FormsModule } from '@angular/forms';
import { ClientService } from '../../../core/services/client.service';
import { Client } from '../../../core/models/models';
import { ClientDialogComponent } from '../client-dialog/client-dialog.component';
import { ToastrService } from 'ngx-toastr';
import { Observable, of, BehaviorSubject } from 'rxjs';
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
  private dialog = inject(MatDialog);
  private toastr = inject(ToastrService);

  private clientsSubject = new BehaviorSubject<Client[]>([]);
  clients$ = this.clientsSubject.asObservable();
  
  displayedColumns: string[] = ['name', 'email', 'phone', 'actions'];
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

        if (client?.id) {
          return this.clientService.updateClient(client.id, result);
        }

        return this.clientService.createClient(result);
      })
    ).subscribe((saved) => {
      if (saved) {
        this.toastr.success(client?.id ? 'Client updated successfully' : 'Client created successfully');
        this.loadClients();
      }
    });
  }

  uploadDocument(client: Client) {
    this.selectedClientId = client.id;
    document.querySelector<HTMLInputElement>('input[type="file"]')?.click();
  }

  onFileSelected(event: any) {
    const file: File = event.target.files[0];
    if (file && this.selectedClientId) {
      this.clientService.uploadDocument(this.selectedClientId, file).subscribe(() => {
        this.toastr.success('Document uploaded successfully');
      });
    }
  }

  downloadDocument(client: Client) {
    if (!client.id) {
      return;
    }

    this.clientService.downloadDocument(client.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = client.documentName || `client-${client.id}-document`;
        link.click();
        URL.revokeObjectURL(url);
        this.toastr.success('Document downloaded successfully');
      },
      error: () => {
        this.toastr.error('No document found for this client');
      }
    });
  }

  deleteClient(client: Client) {
    if (confirm(`Are you sure you want to delete ${client.name}?`)) {
      this.toastr.info('Delete feature coming soon');
    }
  }
}
