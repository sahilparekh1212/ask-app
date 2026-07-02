import { Routes } from '@angular/router';
import { HomeComponent } from './features/home/home.component';
import { LoginComponent } from './features/login/login.component';
import { AuthCallbackComponent } from './features/auth-callback/auth-callback.component';
import { ProfileComponent } from './features/profile/profile.component';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  { path: '', component: HomeComponent, title: 'AI-Sandbox' },
  { path: 'login', component: LoginComponent, title: 'Sign in' },
  { path: 'login/callback', component: AuthCallbackComponent, title: 'Signing in…' },
  { path: 'profile', component: ProfileComponent, title: 'Profile', canActivate: [authGuard] },
  // The audit feature (paginated table + stats) is added in a later slice, behind authGuard.
  { path: '**', redirectTo: '' },
];
