import { Routes } from '@angular/router';
import { HomeComponent } from './features/home/home.component';
import { LoginComponent } from './features/login/login.component';
import { AuthCallbackComponent } from './features/auth-callback/auth-callback.component';
import { ProfileComponent } from './features/profile/profile.component';
import { AuditComponent } from './features/audit/audit.component';
import { AssistantComponent } from './features/assistant/assistant.component';
import { FlashcardsComponent } from './features/flashcards/flashcards.component';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  { path: '', component: HomeComponent, title: 'AI-Sandbox' },
  { path: 'login', component: LoginComponent, title: 'Sign in' },
  { path: 'login/callback', component: AuthCallbackComponent, title: 'Signing in…' },
  { path: 'profile', component: ProfileComponent, title: 'Profile', canActivate: [authGuard] },
  { path: 'audit', component: AuditComponent, title: 'Dashboard', canActivate: [authGuard] },
  {
    path: 'assistant',
    component: AssistantComponent,
    title: 'Chat',
    canActivate: [authGuard],
  },
  {
    path: 'flashcards',
    component: FlashcardsComponent,
    title: 'Flashcards',
    canActivate: [authGuard],
  },
  { path: '**', redirectTo: '' },
];
