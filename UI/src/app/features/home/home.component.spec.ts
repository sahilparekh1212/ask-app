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

  it('renders the tech-stack groups and design decisions', () => {
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Tech stack');
    expect(text).toContain('Spring Boot 3');
    expect(text).toContain('Design decisions');
    expect(text).toContain('Event-driven audit trail');
  });

  it('shows a why and a how for every design decision', () => {
    const el = fixture.nativeElement as HTMLElement;
    const tags = Array.from(el.querySelectorAll('.decision .tag')).map((t) =>
      t.textContent?.trim(),
    );
    expect(tags.filter((t) => t === 'Why').length).toBe(component.decisions.length);
    expect(tags.filter((t) => t === 'How').length).toBe(component.decisions.length);
  });

  it('links each feature that declares a route into the app', () => {
    const el = fixture.nativeElement as HTMLElement;
    const links = el.querySelectorAll('.feature a[href]');
    const routed = component.features.filter((f) => f.link).length;
    expect(links.length).toBe(routed);
  });
});
