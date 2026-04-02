import { CommonModule, NgIf } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatChipsModule } from '@angular/material/chips';
import { ToastrService } from 'ngx-toastr';
import { Category, CategoryWithKeywords, Keyword } from '../../core/models/models';
import { CategoryService } from '../../core/services/category.service';

@Component({
  selector: 'app-categories',
  standalone: true,
  imports: [
    CommonModule,
    NgIf,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatFormFieldModule,
    MatInputModule,
    MatChipsModule
  ],
  templateUrl: './categories.component.html',
  styleUrl: './categories.component.scss'
})
export class CategoriesComponent implements OnInit {
  private categoryService = inject(CategoryService);
  private toastr = inject(ToastrService);

  categories: CategoryWithKeywords[] = [];
  selectedCategory?: CategoryWithKeywords;
  categoryColumns = ['name', 'keywords', 'active', 'actions'];
  keywordColumns = ['nameEn', 'nameDe', 'active', 'actions'];

  newCategoryName = '';
  newCategoryColor = '#6366f1';
  newKeywordNameEn = '';
  newKeywordNameDe = '';

  editingCategoryId?: number;
  editingCategoryName = '';
  editingCategoryColor = '#6366f1';
  editingKeywordId?: number;
  editingKeywordNameEn = '';
  editingKeywordNameDe = '';

  ngOnInit() {
    this.loadCategories();
  }

  loadCategories(selectCategoryId?: number) {
    this.categoryService.getCategories().subscribe({
      next: (categories) => {
        this.categories = categories;
        if (selectCategoryId) {
          this.selectedCategory = this.categories.find((category) => category.id === selectCategoryId) || this.categories[0];
        } else if (!this.selectedCategory) {
          this.selectedCategory = this.categories[0];
        } else {
          this.selectedCategory = this.categories.find((category) => category.id === this.selectedCategory?.id) || this.categories[0];
        }
      },
      error: () => this.toastr.error('Failed to load categories')
    });
  }

  selectCategory(category: CategoryWithKeywords) {
    this.selectedCategory = category;
    this.cancelCategoryEdit();
    this.cancelKeywordEdit();
    this.newKeywordNameEn = '';
    this.newKeywordNameDe = '';
  }

  addCategory() {
    if (!this.newCategoryName.trim()) {
      return;
    }

    this.categoryService.createCategory({ name: this.newCategoryName, color: this.newCategoryColor }).subscribe({
      next: () => {
        this.toastr.success('Category created');
        this.newCategoryName = '';
        this.newCategoryColor = '#6366f1';
        this.loadCategories();
      },
      error: () => this.toastr.error('Failed to create category')
    });
  }

  startCategoryEdit(category: CategoryWithKeywords) {
    this.editingCategoryId = category.id;
    this.editingCategoryName = category.name;
    this.editingCategoryColor = category.color || '#6366f1';
  }

  saveCategoryEdit() {
    if (!this.editingCategoryId || !this.editingCategoryName.trim()) {
      return;
    }

    this.categoryService.updateCategory(this.editingCategoryId, {
      name: this.editingCategoryName,
      color: this.editingCategoryColor
    }).subscribe({
      next: () => {
        this.toastr.success('Category updated');
        const selectedId = this.selectedCategory?.id;
        this.cancelCategoryEdit();
        this.loadCategories(selectedId);
      },
      error: () => this.toastr.error('Failed to update category')
    });
  }

  deactivateCategory(category: CategoryWithKeywords) {
    if (!category.id) {
      return;
    }

    if (!confirm(`Deactivate ${category.name}? This will also deactivate all its keywords.`)) {
      return;
    }

    this.categoryService.deleteCategory(category.id).subscribe({
      next: () => {
        this.toastr.success('Category deactivated');
        this.loadCategories();
      },
      error: (err) => this.toastr.error(err?.error?.message || 'Failed to deactivate category')
    });
  }

  addKeyword() {
    if (!this.selectedCategory?.id || !this.newKeywordNameEn.trim() || !this.newKeywordNameDe.trim()) {
      return;
    }

    this.categoryService.addKeyword(this.selectedCategory.id, {
      nameEn: this.newKeywordNameEn,
      nameDe: this.newKeywordNameDe
    }).subscribe({
      next: () => {
        this.toastr.success('Keyword added');
        this.newKeywordNameEn = '';
        this.newKeywordNameDe = '';
        this.loadCategories(this.selectedCategory?.id);
      },
      error: () => this.toastr.error('Failed to add keyword')
    });
  }

  startKeywordEdit(keyword: Keyword) {
    this.editingKeywordId = keyword.id;
    this.editingKeywordNameEn = keyword.nameEn;
    this.editingKeywordNameDe = keyword.nameDe;
  }

  saveKeywordEdit() {
    if (!this.editingKeywordId || !this.editingKeywordNameEn.trim() || !this.editingKeywordNameDe.trim()) {
      return;
    }

    this.categoryService.updateKeyword(this.editingKeywordId, {
      nameEn: this.editingKeywordNameEn,
      nameDe: this.editingKeywordNameDe
    }).subscribe({
      next: () => {
        this.toastr.success('Keyword updated');
        const selectedId = this.selectedCategory?.id;
        this.cancelKeywordEdit();
        this.loadCategories(selectedId);
      },
      error: () => this.toastr.error('Failed to update keyword')
    });
  }

  deactivateKeyword(keyword: Keyword) {
    if (!keyword.id) {
      return;
    }

    if (!confirm(`Deactivate keyword ${keyword.nameEn}?`)) {
      return;
    }

    this.categoryService.deleteKeyword(keyword.id).subscribe({
      next: () => {
        this.toastr.success('Keyword deactivated');
        this.loadCategories(this.selectedCategory?.id);
      },
      error: () => this.toastr.error('Failed to deactivate keyword')
    });
  }

  cancelCategoryEdit() {
    this.editingCategoryId = undefined;
    this.editingCategoryName = '';
    this.editingCategoryColor = '#6366f1';
  }

  cancelKeywordEdit() {
    this.editingKeywordId = undefined;
    this.editingKeywordNameEn = '';
    this.editingKeywordNameDe = '';
  }

  get selectedKeywords(): Keyword[] {
    return this.selectedCategory?.keywords || [];
  }
}