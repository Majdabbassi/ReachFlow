import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatOptionModule } from '@angular/material/core';
import { Client, CategoryWithKeywords, ClientCategoryDTO } from '../../../core/models/models';
import { CategoryService } from '../../../core/services/category.service';

interface ClientDialogResult {
  client: Client;
  categoryDocuments: Array<{ categoryId: number; file: File }>;
}

@Component({
  selector: 'app-client-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatOptionModule
  ],
  templateUrl: './client-dialog.component.html',
  styleUrl: './client-dialog.component.scss'
})
export class ClientDialogComponent implements OnInit {
  private fb = inject(FormBuilder);
  private dialogRef = inject(MatDialogRef<ClientDialogComponent>);
  private categoryService = inject(CategoryService);
  public data: Client = inject(MAT_DIALOG_DATA);

  categoryOptions: CategoryWithKeywords[] = [];
  uploadedFiles = new Map<number, File>();
  existingDocumentCategoryIds = new Set<number>((this.data.categories || []).filter((category) => category.hasDocument).map((category) => category.categoryId));

  clientForm: FormGroup = this.fb.group({
    name: [this.data.name || '', Validators.required],
    email: [this.data.email || '', [Validators.required, Validators.email]],
    appPassword: [this.data.appPassword || '', Validators.required],
    phone: [this.data.phone || ''],
    categoryIds: [(this.data.categories || []).map((category) => category.categoryId), Validators.required]
  });

  ngOnInit() {
    this.categoryService.getCategories().subscribe((categories) => {
      this.categoryOptions = categories;
      const selectedIds = this.clientForm.value.categoryIds || [];
      this.clientForm.patchValue({ categoryIds: selectedIds.length ? selectedIds : [] });
    });
  }

  get selectedCategoryIds(): number[] {
    return this.clientForm.value.categoryIds || [];
  }

  get selectedCategories() {
    return this.categoryOptions.filter((category) => this.selectedCategoryIds.includes(category.id!));
  }

  onFileChange(categoryId: number, event: Event) {
    const target = event.target as HTMLInputElement;
    const file = target.files?.[0];
    if (file) {
      this.uploadedFiles.set(categoryId, file);
    } else {
      this.uploadedFiles.delete(categoryId);
    }
  }

  hasDocumentFor(categoryId: number): boolean {
    return this.existingDocumentCategoryIds.has(categoryId) || this.uploadedFiles.has(categoryId);
  }

  private missingDocuments(): string[] {
    return this.selectedCategories
      .filter((category) => !this.hasDocumentFor(category.id!))
      .map((category) => category.name);
  }

  onSubmit() {
    if (this.clientForm.invalid) {
      return;
    }

    const missingDocuments = this.missingDocuments();
    if (missingDocuments.length > 0) {
      this.clientForm.get('categoryIds')?.setErrors({ missingDocuments: true });
      return;
    }

    const selectedCategories = this.selectedCategories;
    const client: Client = {
      ...this.data,
      name: this.clientForm.value.name,
      email: this.clientForm.value.email,
      appPassword: this.clientForm.value.appPassword,
      phone: this.clientForm.value.phone,
      categories: selectedCategories.map((category) => ({
        categoryId: category.id!,
        categoryName: category.name,
        color: category.color,
        hasDocument: this.hasDocumentFor(category.id!)
      } as ClientCategoryDTO))
    };

    const categoryDocuments = Array.from(this.uploadedFiles.entries()).map(([categoryId, file]) => ({ categoryId, file }));
    this.dialogRef.close({ client, categoryDocuments } satisfies ClientDialogResult);
  }

  onCancel() {
    this.dialogRef.close();
  }
}
