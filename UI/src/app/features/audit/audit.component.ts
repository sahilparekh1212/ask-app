import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { AuditService } from './audit.service';
import {
  AuditLog,
  AuditLogFilter,
  AuditLogStats,
  AuditLogTimeBucket,
  PagedResponse,
} from './audit.models';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { TranslateService } from '../../core/i18n/translate.service';
import { environment } from '../../../environments/environment';

type SortDir = 'asc' | 'desc';
type SortField = 'createdAt' | 'entityType' | 'action';
type TimelineWindow = '24h' | '7d';

/** One zero-filled column of the events-over-time chart. */
interface TimelineBar {
  time: number; // bucket start, epoch ms
  count: number;
  label: string; // locale-formatted tooltip text
}

/** Headline numbers for the KPI cards — always the last 24h, independent of the filter form. */
interface Kpis {
  total: number;
  busiest: string | null; // top byEntityType bucket
  blockedRate: number | null; // (blocked + errored) / total; null when there were no events
  eventTypes: number; // distinct actions seen
}

const HOUR_MS = 3_600_000;
const DAY_MS = 24 * HOUR_MS;

@Component({
  selector: 'app-audit',
  imports: [ReactiveFormsModule, DatePipe, TranslatePipe],
  templateUrl: './audit.component.html',
  styleUrl: './audit.component.scss',
})
export class AuditComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly audit = inject(AuditService);
  private readonly translate = inject(TranslateService);

  // The deployment's read-only Grafana — the system-view counterpart to this page's domain
  // view (linked from the sticky bottom bar). Environment-driven so the demo user on the
  // local compose stack and the SSO test user on prod both land on *their* Grafana.
  readonly grafanaUrl = environment.grafanaUrl;

  readonly filterForm = this.fb.nonNullable.group({
    entityType: [''],
    action: [''],
    details: [''],
    from: [''],
    to: [''],
    includeDeleted: [false],
  });

  readonly result = signal<PagedResponse<AuditLog> | null>(null);
  readonly stats = signal<AuditLogStats | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  // Dropdown options are the union of every stats bucket key seen so far — the unfiltered
  // initial load seeds the full set, and later (narrower) stats responses can only add,
  // never remove, so picking a filter doesn't make the other options disappear.
  private readonly seenEntityTypes = new Set<string>();
  private readonly seenActions = new Set<string>();
  readonly entityTypeOptions = signal<string[]>([]);
  readonly actionOptions = signal<string[]>([]);

  // "Add demo logs" control — the rows are generated server-side; this only sends the count.
  readonly demoCount = this.fb.nonNullable.control(10);
  readonly demoBusy = signal(false);
  readonly demoMessage = signal<string | null>(null);
  // Hidden until the backend confirms the demo endpoint exists (LOCAL/DEV only), so a PROD
  // deployment never shows a button whose click would 404. Starts false → never flashes.
  readonly demoAvailable = signal(false);

  // KPI headline cards (24h window, filter-independent — the "what's happening" row).
  readonly kpis = signal<Kpis | null>(null);

  // Events-over-time chart: window picks both the span and the bucket granularity.
  readonly timelineWindow = signal<TimelineWindow>('24h');
  readonly timelineBars = signal<TimelineBar[]>([]);
  readonly maxTimelineCount = computed(() =>
    this.timelineBars().reduce((m, b) => Math.max(m, b.count), 0),
  );

  readonly page = signal(0);
  readonly size = signal(20);
  readonly sortField = signal<SortField>('createdAt');
  readonly sortDir = signal<SortDir>('desc');

  readonly totalPages = computed(() => this.result()?.totalPages ?? 0);
  /** Largest bucket count, so the stat bars can be scaled to a shared max width. */
  readonly maxActionCount = computed(() => this.max(this.stats()?.byAction));
  readonly maxEntityCount = computed(() => this.max(this.stats()?.byEntityType));

  ngOnInit(): void {
    this.reload();
    this.loadKpis();
    this.audit.features().subscribe({
      next: (f) => this.demoAvailable.set(f.demoData),
      error: () => this.demoAvailable.set(false),
    });
  }

  applyFilters(): void {
    this.page.set(0);
    this.reload();
  }

  addDemoLogs(): void {
    const count = this.demoCount.value;
    if (this.demoBusy() || count < 1 || count > 500) {
      this.demoMessage.set(count < 1 || count > 500 ? this.translate.t('audit.demoRange') : null);
      return;
    }
    this.demoBusy.set(true);
    this.demoMessage.set(null);
    this.audit.addDemoLogs(count).subscribe({
      next: (res) => {
        this.demoBusy.set(false);
        this.demoMessage.set(this.translate.t('audit.demoAdded', { count: res.created }));
        this.reload();
        this.loadKpis();
      },
      error: () => {
        this.demoBusy.set(false);
        this.demoMessage.set(this.translate.t('audit.addDemoError'));
      },
    });
  }

  sortBy(field: SortField): void {
    if (this.sortField() === field) {
      this.sortDir.set(this.sortDir() === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortField.set(field);
      this.sortDir.set('asc');
    }
    this.reload();
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.totalPages()) {
      return;
    }
    this.page.set(page);
    this.reload();
  }

  barWidth(count: number, max: number): string {
    return max > 0 ? `${Math.round((count / max) * 100)}%` : '0%';
  }

  blockedRateLabel(kpis: Kpis): string {
    return kpis.blockedRate === null ? '—' : `${Math.round(kpis.blockedRate * 1000) / 10}%`;
  }

  /**
   * The KPI row is deliberately filter-independent: it answers "what's happening on this
   * deployment right now" over a fixed 24h window, while the panel below answers "what
   * matches my filter". Blocked/errored are detail-field facts, not actions, so they come
   * from the same details-contains filter the search box uses.
   */
  private loadKpis(): void {
    const from = new Date(Date.now() - DAY_MS).toISOString();
    forkJoin({
      day: this.audit.stats({ from }),
      blocked: this.audit.stats({ from, details: 'blocked=true' }),
      errored: this.audit.stats({ from, details: 'error=' }),
    }).subscribe({
      next: ({ day, blocked, errored }) =>
        this.kpis.set({
          total: day.total,
          busiest: day.byEntityType[0]?.key ?? null,
          eventTypes: day.byAction.length,
          blockedRate: day.total > 0 ? (blocked.total + errored.total) / day.total : null,
        }),
      error: () => this.kpis.set(null),
    });
  }

  setTimelineWindow(window: TimelineWindow): void {
    if (this.timelineWindow() === window) {
      return;
    }
    this.timelineWindow.set(window);
    this.loadTimeline();
  }

  private loadTimeline(): void {
    const hourly = this.timelineWindow() === '24h';
    const stepMs = hourly ? HOUR_MS : DAY_MS;
    const now = Date.now();
    const form = this.buildFilter();
    // The chart's span is the toggle window intersected with the form's date range,
    // so it honours the same filters as the table and the other charts.
    const windowStart = Math.max(
      now - (hourly ? DAY_MS : 7 * DAY_MS),
      form.from ? Date.parse(form.from) : 0,
    );
    const windowEnd = Math.min(form.to ? Date.parse(form.to) : now, now);

    this.audit
      .timeline({ ...form, from: new Date(windowStart).toISOString() }, hourly ? 'hour' : 'day')
      .subscribe({
        next: (buckets) =>
          this.timelineBars.set(this.zeroFill(buckets, windowStart, windowEnd, stepMs)),
        error: () => this.timelineBars.set([]),
      });
  }

  /**
   * Empty buckets are absent from the API response; rebuild the full axis so quiet
   * periods render as gaps rather than disappearing. The axis is anchored on the first
   * returned bucket — the server truncates in its own session time zone, so client-side
   * "top of the UTC hour" assumptions could misalign with the real bucket boundaries.
   */
  private zeroFill(
    buckets: AuditLogTimeBucket[],
    windowStart: number,
    windowEnd: number,
    stepMs: number,
  ): TimelineBar[] {
    const counts = new Map(buckets.map((b) => [Date.parse(b.bucket), b.count]));
    const anchor = buckets.length ? Date.parse(buckets[0].bucket) : windowStart;
    const bars: TimelineBar[] = [];
    for (
      let n = Math.floor((windowStart - anchor) / stepMs);
      anchor + n * stepMs < windowEnd;
      n++
    ) {
      const time = anchor + n * stepMs;
      bars.push({ time, count: counts.get(time) ?? 0, label: this.barLabel(time, stepMs) });
    }
    return bars;
  }

  private barLabel(time: number, stepMs: number): string {
    const date = new Date(time);
    return stepMs === HOUR_MS
      ? date.toLocaleString(undefined, { month: 'short', day: 'numeric', hour: 'numeric' })
      : date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  }

  private reload(): void {
    const filter = this.buildFilter();
    this.loading.set(true);
    this.error.set(null);

    this.audit
      .search(filter, { page: this.page(), size: this.size(), sort: this.sortParam() })
      .subscribe({
        next: (result) => {
          this.result.set(result);
          this.loading.set(false);
        },
        error: () => {
          this.error.set(this.translate.t('audit.loadError'));
          this.loading.set(false);
        },
      });

    this.audit.stats(filter).subscribe({
      next: (stats) => {
        this.stats.set(stats);
        this.mergeOptions(stats);
      },
      error: () => this.stats.set(null),
    });

    this.loadTimeline();
  }

  private mergeOptions(stats: AuditLogStats): void {
    stats.byEntityType.forEach((b) => this.seenEntityTypes.add(b.key));
    stats.byAction.forEach((b) => this.seenActions.add(b.key));
    this.entityTypeOptions.set([...this.seenEntityTypes].sort());
    this.actionOptions.set([...this.seenActions].sort());
  }

  private sortParam(): string {
    return `${this.sortField()},${this.sortDir()}`;
  }

  private buildFilter(): AuditLogFilter {
    const v = this.filterForm.getRawValue();
    return {
      entityType: v.entityType || null,
      action: v.action || null,
      details: v.details.trim() || null,
      // datetime-local yields local "YYYY-MM-DDTHH:mm"; the API wants an ISO instant.
      from: v.from ? new Date(v.from).toISOString() : null,
      to: v.to ? new Date(v.to).toISOString() : null,
      includeDeleted: v.includeDeleted,
    };
  }

  private max(counts?: { count: number }[]): number {
    return counts?.reduce((m, c) => Math.max(m, c.count), 0) ?? 0;
  }
}
