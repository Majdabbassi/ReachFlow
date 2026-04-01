import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { LoadingService } from '../services/loading.service';
import { finalize } from 'rxjs';

export const loadingInterceptor: HttpInterceptorFn = (req, next) => {
  const loadingService = inject(LoadingService);
  
  // Skip loading for background polling if needed
  const isBackground = req.params.has('background') || req.url.includes('/stats');
  
  if (!isBackground) {
    loadingService.setLoading(true);
  }

  return next(req).pipe(
    finalize(() => {
      if (!isBackground) {
        loadingService.setLoading(false);
      }
    })
  );
};
