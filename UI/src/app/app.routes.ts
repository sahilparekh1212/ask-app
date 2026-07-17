import { Routes } from '@angular/router';
import { LoginComponent } from './features/login/login.component';
import { AuthCallbackComponent } from './features/auth-callback/auth-callback.component';
import { ProfileComponent } from './features/profile/profile.component';
import { AuditComponent } from './features/audit/audit.component';
import { AssistantComponent } from './features/assistant/assistant.component';
import { authGuard } from './core/auth/auth.guard';

// URLs mirror the nav labels (Chat, Observability). The old paths (/, /about, /audit,
// /assistant) stay as redirects so bookmarks and inbound links keep working, and anything
// unrecognized lands on Chat rather than a dead end (unauthenticated visitors are routed to
// the login page by the guard). The project README lives on GitHub, linked from the top bar.
export const routes: Routes = [
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
  // Same component — the id is a ChatGPT-style conversation handle in the URL (set via
  // Location.replaceState on the first message); a direct hit re-opens an empty chat.
  {
    path: 'chat/:id',
    component: AssistantComponent,
    title: 'Chat',
    canActivate: [authGuard],
  },
  // Legacy paths from before the URL/label alignment (and the removed About page).
  { path: 'about', redirectTo: 'chat' },
  { path: 'audit', redirectTo: 'observability' },
  { path: 'assistant', redirectTo: 'chat' },
  { path: 'flashcards', redirectTo: 'chat' },
  { path: '', redirectTo: 'chat', pathMatch: 'full' },
  { path: '**', redirectTo: 'chat' },
];
