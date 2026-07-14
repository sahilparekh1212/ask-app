import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { HomeComponent } from './home.component';

describe('HomeComponent', () => {
  let fixture: ComponentFixture<HomeComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    // The constructor fires the README fetch as the component is created.
    fixture = TestBed.createComponent(HomeComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('fetches README.md and renders it as HTML', () => {
    const req = httpMock.expectOne('README.md');
    expect(req.request.method).toBe('GET');
    req.flush('# ask-app\n\nHello **world**\n\n| A | B |\n|---|---|\n| 1 | 2 |\n');
    fixture.detectChanges();

    const article = (fixture.nativeElement as HTMLElement).querySelector('.readme');
    expect(article).not.toBeNull();
    expect(article!.querySelector('h1')?.textContent).toContain('ask-app');
    expect(article!.querySelector('strong')?.textContent).toBe('world');
    expect(article!.querySelector('table')).not.toBeNull();
  });

  it('swaps the Architecture mermaid block for the inline SVG diagram', () => {
    httpMock
      .expectOne('README.md')
      .flush('## Architecture\n\n```mermaid\nflowchart LR\n  A --> B\n```\n');
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.readme svg.arch-svg')).not.toBeNull();
    // the raw mermaid source must not survive as a code block
    expect(el.querySelector('code.language-mermaid')).toBeNull();
  });

  it('falls back to a GitHub link when the README cannot be loaded', () => {
    httpMock.expectOne('README.md').error(new ProgressEvent('error'));
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.readme')).toBeNull();
    const link = el.querySelector<HTMLAnchorElement>('.readme-status a');
    expect(link?.getAttribute('href')).toBe('https://github.com/sahilparekh1212/ask-app');
  });
});
