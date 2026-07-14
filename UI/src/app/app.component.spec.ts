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

  it('pins About at the top of the rail bottom (above language + account)', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const first = (fixture.nativeElement as HTMLElement).querySelector<HTMLAnchorElement>(
      '.rail-bottom .rail-item',
    );
    expect(first?.getAttribute('href')).toBe('/about');
    expect(first?.getAttribute('aria-label')).toBe('About');
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

  it('renders About full-width with no contextual sidebar', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    // Default route → About renders the README on its own; there is no secondary panel.
    expect(el.querySelector('.sidebar')).toBeNull();
    expect(el.querySelector('.content')?.classList.contains('no-sidebar')).toBeTrue();
  });

  it('promotes GitHub + LinkedIn to always-visible top-bar links', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const links = [
      ...(fixture.nativeElement as HTMLElement).querySelectorAll<HTMLAnchorElement>(
        '.topbar-right .topbar-link',
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

  it('shows the section title in the top bar even when a section has no sidebar', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    // Default route → About: title comes from the section, not the sidebar.
    expect(fixture.componentInstance.sectionTitleKey()).toBe('nav.about');
    const title = (fixture.nativeElement as HTMLElement).querySelector('.topbar-title');
    expect(title?.textContent?.trim()).toBe('About');
  });
});
