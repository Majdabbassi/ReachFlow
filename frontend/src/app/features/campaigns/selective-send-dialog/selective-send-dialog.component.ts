import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface SelectiveSendDialogData {
  selectedCount: number;
  subject?: string;
  body?: string;
  delaySeconds?: number;
  htmlBody?: boolean;
}

export interface SelectiveSendDialogResult {
  subject: string;
  body: string;
  delaySeconds: number;
  htmlBody: boolean;
}

@Component({
  selector: 'app-selective-send-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule
  ],
  templateUrl: './selective-send-dialog.component.html'
})
export class SelectiveSendDialogComponent {
  private fb = inject(FormBuilder);
  private dialogRef = inject(MatDialogRef<SelectiveSendDialogComponent>);
  data: SelectiveSendDialogData = inject(MAT_DIALOG_DATA);

  form = this.fb.group({
    subject: [this.data.subject || 'Hello from {name}', Validators.required],
    body: [this.data.body || 'Hi,\n\nI am writing to you regarding {city}.\n\nBest regards.', Validators.required],
    delaySeconds: [this.data.delaySeconds ?? 2, [Validators.required, Validators.min(0)]],
    htmlBody: [this.data.htmlBody ?? false]
  });

  cancel() {
    this.dialogRef.close();
  }

  submit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.dialogRef.close(this.form.getRawValue() as SelectiveSendDialogResult);
  }
}
