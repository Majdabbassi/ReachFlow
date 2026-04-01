import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { ClientService } from '../../../core/services/client.service';
import { Client } from '../../../core/models/models';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { MatIconModule } from '@angular/material/icon';
import { FilterByIdPipe } from '../../../shared/pipes/filter-by-id.pipe';

@Component({
  selector: 'app-campaign-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatSelectModule,
    MatIconModule,
    FilterByIdPipe
  ],
  templateUrl: './campaign-dialog.component.html',
  styleUrl: './campaign-dialog.component.scss'
})
export class CampaignDialogComponent implements OnInit {
  private fb = inject(FormBuilder);
  private clientService = inject(ClientService);
  private dialogRef = inject(MatDialogRef<CampaignDialogComponent>);

  clients$: Observable<Client[]> = of([]);
  campaignForm: FormGroup = this.fb.group({
    name: ['', Validators.required],
    clientId: ['', Validators.required]
  });

  ngOnInit() {
    this.clients$ = this.clientService.getClients().pipe(
      catchError(() => of([]))
    );
  }

  onSubmit() {
    if (this.campaignForm.valid) {
      this.dialogRef.close(this.campaignForm.value);
    }
  }

  onCancel() {
    this.dialogRef.close();
  }
}
