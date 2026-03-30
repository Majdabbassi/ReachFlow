import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { ClientService } from '../../../core/services/client.service';
import { Client } from '../../../core/models/models';
import { ClientDialogComponent } from '../client-dialog/client-dialog.component';
import { ToastrService } from 'ngx-toastr';

@Component({
  selector: 'app-client-list',
  standalone: true,
  imports: [CommonModule, MatTableModule, MatButtonModule, MatIconModule, MatDialogModule, MatCardModule],
  templateUrl: './client-list.component.html',
  styleUrl: './client-list.component.scss'
})
export class ClientListComponent implements OnInit {
  private clientService = inject(ClientService);
  private dialog = inject(MatDialog);
  private toastr = inject(ToastrService);

  clients: Client[] = [];
  displayedColumns: string[] = ['name', 'email', 'phone', 'actions'];
  selectedClientId?: number;

  ngOnInit() {
    this.loadClients();
  }

  loadClients() {
    this.clientService.getClients().subscribe(clients => this.clients = clients);
  }

  openClientDialog(client?: Client) {
    const dialogRef = this.dialog.open(ClientDialogComponent, {
      width: '500px',
      data: client || {}
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        if (client?.id) {
          this.clientService.updateClient(client.id, result).subscribe(() => {
            this.toastr.success('Client updated successfully');
            this.loadClients();
          });
        } else {
          this.clientService.createClient(result).subscribe(() => {
            this.toastr.success('Client created successfully');
            this.loadClients();
          });
        }
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
}
