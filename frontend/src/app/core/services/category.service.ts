import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Category, CategoryWithKeywords, Keyword } from '../models/models';

@Injectable({
  providedIn: 'root'
})
export class CategoryService {
  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8080/api/categories';

  getCategories(): Observable<CategoryWithKeywords[]> {
    return this.http.get<CategoryWithKeywords[]>(this.apiUrl);
  }

  createCategory(dto: Partial<Category>): Observable<Category> {
    return this.http.post<Category>(this.apiUrl, dto);
  }

  updateCategory(id: number, dto: Partial<Category>): Observable<Category> {
    return this.http.put<Category>(`${this.apiUrl}/${id}`, dto);
  }

  deleteCategory(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  addKeyword(categoryId: number, dto: Partial<Keyword>): Observable<Keyword> {
    return this.http.post<Keyword>(`${this.apiUrl}/${categoryId}/keywords`, dto);
  }

  updateKeyword(keywordId: number, dto: Partial<Keyword>): Observable<Keyword> {
    return this.http.put<Keyword>(`${this.apiUrl}/keywords/${keywordId}`, dto);
  }

  deleteKeyword(keywordId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/keywords/${keywordId}`);
  }
}