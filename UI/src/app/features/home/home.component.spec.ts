import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { HomeComponent } from './home.component';

describe('HomeComponent', () => {
  let component: HomeComponent;
  let fixture: ComponentFixture<HomeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(HomeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('renders the tech-stack, and the grouped decisions covering stack, patterns, data and CI/CD', () => {
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Tech stack');
    expect(text).toContain('Spring Boot 3');
    expect(text).toContain('Design decisions');
    // one heading per required theme
    const headings = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.group-heading'),
    ).map((h) => h.textContent ?? '');
    expect(headings.some((h) => h.includes('Tech stack'))).toBeTrue();
    expect(headings.some((h) => h.includes('Design patterns'))).toBeTrue();
    expect(headings.some((h) => h.includes('Liquibase'))).toBeTrue();
    expect(headings.some((h) => h.includes('CI / CD'))).toBeTrue();
    // representative content actually rendered
    expect(text).toContain('Event-driven audit');
    expect(text).toContain('Liquibase owns the schema');
    expect(text).toContain('coverage gate');
  });

  it('shows a why and a how for every decision across all groups', () => {
    const el = fixture.nativeElement as HTMLElement;
    const total = component.decisionGroups.reduce((n, g) => n + g.items.length, 0);
    const tags = Array.from(el.querySelectorAll('.decision .tag')).map((t) =>
      t.textContent?.trim(),
    );
    expect(tags.filter((t) => t === 'Why').length).toBe(total);
    expect(tags.filter((t) => t === 'How').length).toBe(total);
  });

  it('links each feature that declares a route into the app, plus external counterparts', () => {
    const el = fixture.nativeElement as HTMLElement;
    const links = el.querySelectorAll('.feature a[href]');
    const routed = component.features.filter((f) => f.link).length;
    const external = component.features.filter((f) => f.extLink).length;
    expect(links.length).toBe(routed + external);
  });

  it('links the live read-only Grafana from the observability decision and the dashboard feature', () => {
    const el = fixture.nativeElement as HTMLElement;
    // getAttribute, not .href — the environment URL is same-origin relative ('/grafana')
    // and .href would resolve it against the test origin.
    const grafanaLinks = Array.from(el.querySelectorAll<HTMLAnchorElement>('a')).filter(
      (a) => a.getAttribute('href') === component.grafanaUrl,
    );
    // one in the design-decision entry, one in the feature tour
    expect(grafanaLinks.length).toBe(2);
    // external links must open in a new tab without leaking the opener
    for (const a of grafanaLinks) {
      expect(a.target).toBe('_blank');
      expect(a.rel).toContain('noopener');
    }
  });
});
