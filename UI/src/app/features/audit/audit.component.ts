import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { AuditService } from './audit.service';
import { AuditLog, AuditLogFilter, AuditLogStats, PagedResponse } from './audit.models';

type SortDir = 'asc' | 'desc';
type SortField = 'createdAt' | 'entityType' | 'action';

@Component({
  selector: 'app-audit',
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './audit.component.html',
  styleUrl: './audit.component.scss',
})
export class AuditComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly audit = inject(AuditService);

  readonly filterForm = this.fb.nonNullable.group({
    entityType: [''],
    action: [''],
    from: [''],
    to: [''],
    includeDeleted: [false],
  });

  readonly result = signal<PagedResponse<AuditLog> | null>(null);
  readonly stats = signal<AuditLogStats | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

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
  }

  applyFilters(): void {
    this.page.set(0);
    this.reload();
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
          this.error.set(
            'Could not load audit logs — is the Audit service running and are you signed in?',
          );
          this.loading.set(false);
        },
      });

    this.audit.stats(filter).subscribe({
      next: (stats) => this.stats.set(stats),
      error: () => this.stats.set(null),
    });
  }

  private sortParam(): string {
    return `${this.sortField()},${this.sortDir()}`;
  }

  private buildFilter(): AuditLogFilter {
    const v = this.filterForm.getRawValue();
    return {
      entityType: v.entityType || null,
      action: v.action || null,
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
