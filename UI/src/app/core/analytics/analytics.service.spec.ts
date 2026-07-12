import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { NavigationEnd } from '@angular/router';
import { Subject } from 'rxjs';

import { AnalyticsService } from './analytics.service';

interface GtagWindow extends Window {
  dataLayer?: unknown[];
  gtag?: (...args: unknown[]) => void;
}

describe('AnalyticsService', () => {
  let service: AnalyticsService;
  let routerEvents: Subject<unknown>;
  const w = window as GtagWindow;

  beforeEach(() => {
    routerEvents = new Subject();
    TestBed.configureTestingModule({
      providers: [{ provide: Router, useValue: { events: routerEvents.asObservable() } }],
    });
    service = TestBed.inject(AnalyticsService);
    delete w.dataLayer;
    delete w.gtag;
  });

  afterEach(() => {
    document.querySelectorAll('script[src*="googletagmanager"]').forEach((s) => s.remove());
    delete w.dataLayer;
    delete w.gtag;
  });

  it('does nothing without a measurement id (the dev/default posture)', () => {
    service.init('');
    expect(w.dataLayer).toBeUndefined();
    expect(document.querySelector('script[src*="googletagmanager"]')).toBeNull();
  });

  it('loads gtag, configures without the automatic page_view, and reports router navigations', () => {
    service.init('G-TEST12345');

    // loader script injected for the given id
    const script = document.querySelector<HTMLScriptElement>('script[src*="googletagmanager"]');
    expect(script?.src).toContain('id=G-TEST12345');

    // config disables the full-page-load page_view (a SPA has exactly one of those)
    const asArrays = () => (w.dataLayer ?? []).map((e) => Array.from(e as IArguments));
    const config = asArrays().find((e) => e[0] === 'config');
    expect(config?.[1]).toBe('G-TEST12345');
    expect((config?.[2] as { send_page_view: boolean }).send_page_view).toBeFalse();

    // a router navigation becomes a page_view with the post-redirect URL
    routerEvents.next(new NavigationEnd(1, '/audit', '/observability'));
    const pageView = asArrays().find((e) => e[0] === 'event' && e[1] === 'page_view');
    expect(pageView).withContext('page_view pushed on NavigationEnd').toBeDefined();
    expect((pageView?.[2] as { page_path: string }).page_path).toBe('/observability');
  });

  it('initializes at most once', () => {
    service.init('G-TEST12345');
    service.init('G-TEST12345');
    expect(document.querySelectorAll('script[src*="googletagmanager"]').length).toBe(1);
  });
});
