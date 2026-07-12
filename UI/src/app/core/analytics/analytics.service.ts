import { Injectable, inject } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';

import { environment } from '../../../environments/environment';

/** The gtag globals the loader snippet normally sets up — we do it ourselves, typed. */
interface GtagWindow extends Window {
  dataLayer?: unknown[];
  gtag?: (...args: unknown[]) => void;
}

/**
 * Google Analytics 4 for the SPA, hand-rolled (no wrapper library): loads gtag.js and reports
 * a `page_view` per router navigation. GA's automatic page_view only fires on full page loads,
 * which a SPA has exactly one of — so `send_page_view` is disabled and NavigationEnd events
 * (post-redirect URL) drive the page views instead.
 *
 * No-op when the Measurement ID is empty (dev, or a fork without a GA property): nothing is
 * loaded and no network calls are made — the same graceful "not configured" posture as the
 * backend's provider keys. Only ever reports the route path; the app puts no PII in URLs.
 */
@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private readonly router = inject(Router);
  private started = false;

  /** The Measurement ID parameter exists as a test seam; production callers pass nothing. */
  init(measurementId: string = environment.gaMeasurementId): void {
    if (!measurementId || this.started) {
      return;
    }
    this.started = true;

    const w = window as GtagWindow;
    w.dataLayer = w.dataLayer ?? [];
    // GA's collect endpoint requires the IArguments object shape, so no arrow/rest here.
    w.gtag =
      w.gtag ??
      function gtag() {
        // eslint-disable-next-line prefer-rest-params
        (w.dataLayer as unknown[]).push(arguments);
      };
    w.gtag('js', new Date());
    w.gtag('config', measurementId, { send_page_view: false });

    const script = document.createElement('script');
    script.async = true;
    script.src = `https://www.googletagmanager.com/gtag/js?id=${measurementId}`;
    document.head.appendChild(script);

    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe((e) => {
      w.gtag!('event', 'page_view', {
        page_path: e.urlAfterRedirects,
        page_location: window.location.href,
        page_title: document.title,
      });
    });
  }
}
