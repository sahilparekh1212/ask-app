import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

/**
 * Landing route for the Google OAuth redirect. The Auth server's success handler puts the tokens in
 * the URL *fragment* (#access_token=...&refresh_token=...&expires_in=...); we read them, then call
 * history.replaceState so the tokens don't linger in browser history, then continue into the app.
 */
@Component({
  selector: 'app-auth-callback',
  imports: [],
  templateUrl: './auth-callback.component.html',
  styleUrl: './auth-callback.component.scss',
})
export class AuthCallbackComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  ngOnInit(): void {
    const fragment = window.location.hash.startsWith('#')
      ? window.location.hash.substring(1)
      : window.location.hash;
    const ok = this.auth.consumeOAuthFragment(fragment);

    // Strip the fragment from the address bar / history so the tokens aren't left behind.
    history.replaceState(null, '', window.location.pathname);

    void this.router.navigateByUrl(ok ? '/chat' : '/login');
  }
}
