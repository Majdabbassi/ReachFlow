import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { ToastrService } from 'ngx-toastr';

@Component({
  selector: 'app-settings-page',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule
  ],
  templateUrl: './settings-page.component.html',
  styleUrl: './settings-page.component.scss'
})
export class SettingsPageComponent implements OnInit {
  private fb = inject(FormBuilder);
  private toastr = inject(ToastrService);

  settingsForm: FormGroup = this.fb.group({
    webhookUrl: ['', [Validators.required, Validators.pattern('https?://.+')]]
  });

  ngOnInit() {
    const savedUrl = localStorage.getItem('n8n_webhook_url');
    if (savedUrl) {
      this.settingsForm.patchValue({ webhookUrl: savedUrl });
    }
  }

  saveSettings() {
    if (this.settingsForm.valid) {
      localStorage.setItem('n8n_webhook_url', this.settingsForm.value.webhookUrl);
      this.toastr.success('Settings saved successfully');
    }
  }
}
