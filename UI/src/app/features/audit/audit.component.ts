import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { AuditService } from './audit.service';
import { AuditLog, AuditLogFilter, AuditLogStats, PagedResponse } from './audit.models';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { TranslateService } from '../../core/i18n/translate.service';

type SortDir = 'asc' | 'desc';
type SortField = 'createdAt' | 'entityType' | 'action';

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
