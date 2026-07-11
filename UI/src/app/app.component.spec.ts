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
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it(`should have the 'AI-Sandbox' title`, () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app.title).toEqual('AI-Sandbox');
  });

  it('should order the tabs with the feature tabs leading and About after Flashcards', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const tabs = [...compiled.querySelectorAll('.tabs a')].map((a) => a.textContent?.trim());
    expect(tabs).toEqual(['Dashboard', 'Chat', 'Flashcards', 'About']);
  });

  it('should show a Login link (and no avatar) when signed out', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.sign-in')?.textContent).toContain('Login');
    expect(compiled.querySelector('.avatar')).toBeNull();
  });

  it('should show the circular Profile avatar (and no Sign in link) when signed in', () => {
    authenticated.set(true);
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const avatar = compiled.querySelector('a.avatar');
    expect(avatar).withContext('avatar link should render').not.toBeNull();
    expect(avatar?.getAttribute('aria-label')).toBe('Profile');
    expect(avatar?.getAttribute('href')).toBe('/profile');
    expect(avatar?.querySelector('svg')).withContext('icon, not a text link').not.toBeNull();
    expect(compiled.querySelector('.sign-in')).toBeNull();
  });
});
