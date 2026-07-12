import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
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
        { provide: AuthService, useValue: { isAuthenticated: authenticated } },
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it(`should have the 'AI-Sandbox' title`, () => {
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance.title).toEqual('AI-Sandbox');
  });

  it('orders the activity rail About, Chat, Flashcards, Observability with matching URLs', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const rail = [
      ...(fixture.nativeElement as HTMLElement).querySelectorAll<HTMLAnchorElement>(
        '.rail-items .rail-item',
      ),
    ];
    expect(rail.map((a) => a.textContent?.trim())).toEqual([
      'About',
      'Chat',
      'Flashcards',
      'Observability',
    ]);
    expect(rail.map((a) => a.getAttribute('href'))).toEqual([
      '/about',
      '/chat',
      '/flashcards',
      '/observability',
    ]);
  });

  it('pins a Sign-in entry (and no Profile) at the rail bottom when signed out', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const bottom = (fixture.nativeElement as HTMLElement).querySelector<HTMLAnchorElement>(
      '.rail-bottom .rail-item',
    );
    expect(bottom?.getAttribute('aria-label')).toBe('Login');
    expect(bottom?.getAttribute('href')).toBe('/login');
  });

  it('pins the Profile entry (icon, /profile) at the rail bottom when signed in', () => {
    authenticated.set(true);
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const bottom = (fixture.nativeElement as HTMLElement).querySelector<HTMLAnchorElement>(
      '.rail-bottom .rail-item',
    );
    expect(bottom?.getAttribute('aria-label')).toBe('Profile');
    expect(bottom?.getAttribute('href')).toBe('/profile');
    expect(bottom?.querySelector('svg')).withContext('icon, not a text link').not.toBeNull();
  });

  it('renders the contextual sidebar with collapsible dropdown groups', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    // Default route → About section → its sidebar groups render, each with items visible.
    const groupsBefore = el.querySelectorAll('.sb-items').length;
    expect(groupsBefore).toBeGreaterThan(0);

    // Collapsing a group's header hides that group's item list (the "dropdown").
    el.querySelector<HTMLButtonElement>('.sb-group-head')!.click();
    fixture.detectChanges();
    expect(el.querySelectorAll('.sb-items').length).toBe(groupsBefore - 1);
  });
});
