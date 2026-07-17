import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { AppComponent } from './app.component';
import { AuthService } from './core/auth/auth.service';

describe('AppComponent', () => {
  const authenticated = signal(false);

  beforeEach(async () => {
    authenticated.set(false);
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AuthService,
          useValue: {
            isAuthenticated: authenticated,
            profile: signal(null),
            loadProfile: () => of(null),
          },
        },
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it(`should have the 'ask-app' title`, () => {
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance.title).toEqual('ask-app');
  });

  it('orders the activity rail Chat, Observability with matching URLs', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const rail = [
      ...(fixture.nativeElement as HTMLElement).querySelectorAll<HTMLAnchorElement>(
        '.rail-items .rail-item',
      ),
    ];
    expect(rail.map((a) => a.textContent?.trim())).toEqual(['Chat', 'Observability']);
    expect(rail.map((a) => a.getAttribute('href'))).toEqual(['/chat', '/observability']);
  });

  it('pins a Sign-in entry (and no Profile) as the bottom account slot when signed out', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const items = [
      ...(fixture.nativeElement as HTMLElement).querySelectorAll<HTMLAnchorElement>(
        '.rail-bottom .rail-item',
      ),
    ];
    const account = items[items.length - 1];
    expect(account?.getAttribute('aria-label')).toBe('Login');
    expect(account?.getAttribute('href')).toBe('/login');
  });

  it('pins the Profile entry (icon, /profile) as the bottom account slot when signed in', () => {
    authenticated.set(true);
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const items = [
      ...(fixture.nativeElement as HTMLElement).querySelectorAll<HTMLAnchorElement>(
        '.rail-bottom .rail-item',
      ),
    ];
    const account = items[items.length - 1];
    expect(account?.getAttribute('aria-label')).toBe('Profile');
    expect(account?.getAttribute('href')).toBe('/profile');
    expect(account?.querySelector('svg')).withContext('icon, not a text link').not.toBeNull();
  });

  it('renders the default (Chat) section full-width with no contextual sidebar', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    // Default route → Chat has no per-page context; there is no secondary panel.
    expect(el.querySelector('.sidebar')).toBeNull();
    expect(el.querySelector('.content')?.classList.contains('no-sidebar')).toBeTrue();
  });

  it('pins GitHub + LinkedIn as always-visible links in the top-right corner', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const links = [
      ...(fixture.nativeElement as HTMLElement).querySelectorAll<HTMLAnchorElement>(
        '.corner-links .corner-link',
      ),
    ];
    expect(links.map((a) => a.getAttribute('aria-label'))).toEqual(['GitHub', 'LinkedIn']);
    expect(links.map((a) => a.getAttribute('href'))).toEqual([
      'https://github.com/sahilparekh1212/ask-app',
      'https://www.linkedin.com/in/sahilparekh1212/',
    ]);
    // Each renders an icon and opens in a new tab safely.
    links.forEach((a) => {
      expect(a.querySelector('svg')).not.toBeNull();
      expect(a.getAttribute('rel')).toContain('noopener');
    });
  });
});
