import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class LoadingService {
  private loadingSubject = new BehaviorSubject<boolean>(false);
  private requestsInProgress = 0;

  loading$: Observable<boolean> = this.loadingSubject.asObservable();

  setLoading(loading: boolean) {
    if (loading) {
      this.requestsInProgress++;
    } else {
      this.requestsInProgress = Math.max(0, this.requestsInProgress - 1);
    }
    
    this.loadingSubject.next(this.requestsInProgress > 0);
  }
}
