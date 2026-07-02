import { Routes } from '@angular/router';
import { HomeComponent } from './features/home/home.component';

export const routes: Routes = [
  { path: '', component: HomeComponent, title: 'AI-Sandbox' },
  // The audit feature (paginated table + stats) is added in a later slice.
  { path: '**', redirectTo: '' },
];
