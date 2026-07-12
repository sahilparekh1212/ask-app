import { Routes } from '@angular/router';
import { HomeComponent } from './features/home/home.component';
import { LoginComponent } from './features/login/login.component';
import { AuthCallbackComponent } from './features/auth-callback/auth-callback.component';
import { ProfileComponent } from './features/profile/profile.component';
import { AuditComponent } from './features/audit/audit.component';
import { AssistantComponent } from './features/assistant/assistant.component';
import { FlashcardsComponent } from './features/flashcards/flashcards.component';
import { authGuard } from './core/auth/auth.guard';

// URLs mirror the nav labels (About, Chat, Flashcards, Observability). The old paths
// (/, /audit, /assistant) stay as redirects so bookmarks and inbound links keep working,
// and anything unrecognized lands on About rather than a dead end.
export const routes: Routes = [
  { path: 'about', component: HomeComponent, title: 'AI-Sandbox' },
  { path: 'login', component: LoginComponent, title: 'Sign in' },
  { path: 'login/callback', component: AuthCallbackComponent, title: 'Signing in…' },
  { path: 'profile', component: ProfileComponent, title: 'Profile', canActivate: [authGuard] },
  {
    path: 'observability',
    component: AuditComponent,
    title: 'Observability',
    canActivate: [authGuard],
  },
  {
    path: 'chat',
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
  // Legacy paths from before the URL/label alignment.
  { path: 'audit', redirectTo: 'observability' },
  { path: 'assistant', redirectTo: 'chat' },
  { path: '', redirectTo: 'about', pathMatch: 'full' },
  { path: '**', redirectTo: 'about' },
];
