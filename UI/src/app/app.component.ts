import { Component, afterNextRender, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, fromEvent, map, merge } from 'rxjs';

import { AuthService } from './core/auth/auth.service';
import { TranslatePipe } from './core/i18n/translate.pipe';

/** A primary section: an icon-rail entry that also keys its contextual sidebar. */
interface Section {
  key: string;
  labelKey: string;
  route: string;
  icon: string; // 16x16 path data
}

/** One item inside a sidebar group — exactly one of route / fragment / href is set. */
interface SidebarItem {
  labelKey?: string; // i18n key (chrome)
  text?: string; // literal (page content / brand names, which stay untranslated)
  route?: string; // in-app navigation
  fragment?: string; // scroll to an element id on the current page
  href?: string; // external link (opens in a new tab)
}

interface SidebarGroup {
  id: string;
  titleKey: string;
  items: SidebarItem[];
}

// 16x16 GitHub-Octicon path data — matches the app's Primer-derived theme.
const ICON = {
  chat: 'M1 2.75C1 1.784 1.784 1 2.75 1h10.5c.966 0 1.75.784 1.75 1.75v7.5A1.75 1.75 0 0 1 13.25 11H9.06l-2.573 2.573A1.458 1.458 0 0 1 4 12.543V11H2.75A1.75 1.75 0 0 1 1 9.25Zm1.75-.25a.25.25 0 0 0-.25.25v6.5c0 .138.112.25.25.25h2a.75.75 0 0 1 .75.75v1.94l2.22-2.22a.75.75 0 0 1 .53-.22h4.25a.25.25 0 0 0 .25-.25v-7.5a.25.25 0 0 0-.25-.25Z',
  observability:
    'M1.5 1.75V13.5h13.75a.75.75 0 0 1 0 1.5H.75a.75.75 0 0 1-.75-.75V1.75a.75.75 0 0 1 1.5 0Zm14.28 2.53-5.25 5.25a.75.75 0 0 1-1.06 0L7 7.06 4.28 9.78a.751.751 0 0 1-1.042-.018.751.751 0 0 1-.018-1.042l3.25-3.25a.75.75 0 0 1 1.06 0L10 7.94l4.72-4.72a.751.751 0 0 1 1.042.018.751.751 0 0 1 .018 1.042Z',
  profile:
    'M10.561 8.073a6 6 0 0 1 3.432 5.142.75.75 0 1 1-1.498.07 4.5 4.5 0 0 0-8.99 0 .75.75 0 0 1-1.498-.07 6 6 0 0 1 3.431-5.142 3.999 3.999 0 1 1 5.123 0ZM10.5 5a2.5 2.5 0 1 0-5 0 2.5 2.5 0 0 0 5 0Z',
  signin:
    'M2 2.75C2 1.784 2.784 1 3.75 1h2.5a.75.75 0 0 1 0 1.5h-2.5a.25.25 0 0 0-.25.25v10.5c0 .138.112.25.25.25h2.5a.75.75 0 0 1 0 1.5h-2.5A1.75 1.75 0 0 1 2 13.25Zm6.56 4.5h5.69a.75.75 0 0 1 0 1.5H8.56l1.97 1.97a.749.749 0 1 1-1.06 1.06L5.72 8.53a.751.751 0 0 1 0-1.06l3.75-3.75a.749.749 0 1 1 1.06 1.06L8.56 6.75Z',
  // Brand marks for the top-bar links. GitHub's is a 16x16 Octicon; LinkedIn's is a 24x24 glyph
  // (each <svg> carries its own viewBox — see `topbarLinks`).
  github:
    'M8 0c4.42 0 8 3.58 8 8a8.013 8.013 0 0 1-5.45 7.59c-.4.075-.55-.17-.55-.38 0-.27.01-1.13.01-2.2 0-.75-.25-1.23-.54-1.48 1.78-.2 3.65-.88 3.65-3.95 0-.88-.31-1.59-.82-2.15.08-.2.36-1.02-.08-2.12 0 0-.67-.22-2.2.82-.64-.18-1.32-.27-2-.27-.68 0-1.36.09-2 .27-1.53-1.03-2.2-.82-2.2-.82-.44 1.1-.16 1.92-.08 2.12-.51.56-.82 1.28-.82 2.15 0 3.06 1.86 3.75 3.64 3.95-.23.2-.44.55-.51 1.07-.46.21-1.61.55-2.33-.66-.15-.24-.6-.83-1.23-.82-.67.01-.27.38.01.53.34.19.73.9.82 1.13.16.45.68 1.31 2.69.94 0 .67.01 1.3.01 1.49 0 .21-.15.45-.55.38A7.995 7.995 0 0 1 0 8c0-4.42 3.58-8 8-8Z',
  linkedin:
    'M19 0h-14c-2.761 0-5 2.239-5 5v14c0 2.761 2.239 5 5 5h14c2.762 0 5-2.239 5-5v-14c0-2.761-2.238-5-5-5zm-11 19h-3v-11h3v11zm-1.5-12.268c-.966 0-1.75-.79-1.75-1.764s.784-1.764 1.75-1.764 1.75.79 1.75 1.764-.783 1.764-1.75 1.764zm13.5 12.268h-3v-5.604c0-3.368-4-3.113-4 0v5.604h-3v-11h3v1.765c1.396-2.586 7-2.777 7 2.476v6.759z',
};

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly title = 'ask-app';
  readonly isAuthenticated = this.auth.isAuthenticated;

  /** First letter of the signed-in user's name/email, for the rail's account avatar ('' if unknown). */
  readonly avatarInitial = computed(() => {
    const profile = this.auth.profile();
    const source = (profile?.name || profile?.email || '').trim();
    return source ? source.charAt(0).toUpperCase() : '';
  });

  /** External links pinned in the activity rail (reachable from every page). */
  readonly externalLinks = [
    {
      key: 'github',
      label: 'GitHub',
      href: 'https://github.com/sahilparekh1212/ask-app',
      viewBox: '0 0 16 16',
      icon: ICON.github,
    },
    {
      key: 'linkedin',
      label: 'LinkedIn',
      href: 'https://www.linkedin.com/in/sahilparekh1212/',
      viewBox: '0 0 24 24',
      icon: ICON.linkedin,
    },
  ];

  // Current URL as a signal, so the active section/sidebar recompute on navigation.
  private readonly url = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  /** Which primary section the current URL belongs to (drives the rail highlight + sidebar). */
  readonly section = computed(() => {
    const u = this.url().split('?')[0].split('#')[0];
    if (u.startsWith('/observability')) return 'observability';
    if (u.startsWith('/profile')) return 'profile';
    if (u.startsWith('/login')) return 'login';
    // Everything else routes (or redirects) to Chat, the default section.
    return 'chat';
  });

  /** The rail: primary sections. Profile/sign-in are rendered separately at the rail's bottom. */
  readonly sections: Section[] = [
    { key: 'chat', labelKey: 'nav.chat', route: '/chat', icon: ICON.chat },
    {
      key: 'observability',
      labelKey: 'nav.observability',
      route: '/observability',
      icon: ICON.observability,
    },
  ];

  readonly profileIcon = ICON.profile;
  readonly signinIcon = ICON.signin;

  // Per-section sidebar content. Only sections with genuine per-page context carry a sidebar.
  // About renders the repository README full-width (its own source of truth), so it has no
  // contextual sidebar; chat/profile have none either. Observability keeps its "On this page"
  // anchors. The Connect links (GitHub/LinkedIn) live in the always-visible top bar.
  private readonly sidebars: Record<string, { titleKey: string; groups: SidebarGroup[] }> = {
    observability: {
      titleKey: 'nav.observability',
      groups: [
        {
          id: 'obs-onpage',
          titleKey: 'nav.onThisPage',
          // These are UI chrome (the dashboard's own sections), so they're translated via
          // labelKey — unlike About's anchors, which name English prose headings verbatim.
          items: [
            { labelKey: 'nav.filters', fragment: 'filters' },
            { labelKey: 'nav.summary', fragment: 'summary' },
            { labelKey: 'nav.trends', fragment: 'trends' },
            { labelKey: 'nav.log', fragment: 'log' },
            { labelKey: 'nav.refdata', fragment: 'refdata' },
          ],
        },
      ],
    },
  };

  /** The sidebar for the active section (null for sections with no per-page context). */
  readonly activeSidebar = computed(() => this.sidebars[this.section()] ?? null);

  // Which groups the user has collapsed (VS-Code dropdowns default to open).
  private readonly collapsed = signal<Set<string>>(new Set());
  isCollapsed(id: string): boolean {
    return this.collapsed().has(id);
  }
  toggleGroup(id: string): void {
    this.collapsed.update((set) => {
      const next = new Set(set);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  /** The route to attach fragment links to (fragments scroll within the current page). */
  readonly currentPath = computed(() => this.url().split('?')[0].split('#')[0]);

  // ── Scroll-spy: highlight the "On this page" anchor for whichever section is currently in
  // view. Driven by scroll position (getBoundingClientRect) rather than IntersectionObserver so
  // the highlight is deterministic and the sections are looked up live on each pass — no need to
  // rebind observers across navigations, and it survives a page whose sections render a tick late.
  readonly activeFragment = signal<string | null>(null);

  // The active section is the last one whose heading has scrolled up past this line (just below
  // the sticky top bar), so the highlight tracks the section you've actually scrolled into.
  private static readonly SPY_LINE = 96;

  constructor() {
    // Fetch the profile whenever we're authenticated but don't have it yet, so the rail's account
    // avatar shows its initial. As an effect (not a one-off constructor check) it fires both on a
    // reload with a stored token AND the moment a fresh login flips `isAuthenticated` — so the
    // avatar appears right after login, not only after visiting /profile. Best-effort: a failure
    // just leaves the generic person icon. Runs once (profile becoming set makes the guard false).
    effect(() => {
      if (this.auth.isAuthenticated() && !this.auth.profile()) {
        this.auth.loadProfile().subscribe({
          error: () => {
            /* best-effort — a failure just leaves the generic person icon */
          },
        });
      }
    });

    // Recompute after each navigation (once the freshly-routed page has had a tick to render)
    // and on every scroll/resize. Reading four getBoundingClientRects per scroll is cheap enough
    // to do inline — no rAF/observer indirection, which keeps it working in background tabs too.
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => setTimeout(() => this.updateActiveFragment(), 60));
    merge(fromEvent(window, 'scroll', { passive: true }), fromEvent(window, 'resize'))
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.updateActiveFragment());
    // First pass once the initial routed view has rendered.
    afterNextRender(() => this.updateActiveFragment());
  }

  private updateActiveFragment(): void {
    const fragments = (this.activeSidebar()?.groups ?? [])
      .flatMap((g) => g.items)
      .map((it) => it.fragment)
      .filter((f): f is string => !!f);
    if (fragments.length === 0) {
      this.activeFragment.set(null);
      return;
    }
    let current: string | null = null;
    let firstPresent: string | null = null;
    for (const fragment of fragments) {
      const el = document.getElementById(fragment);
      if (!el) continue;
      firstPresent ??= fragment;
      if (el.getBoundingClientRect().top <= AppComponent.SPY_LINE) current = fragment;
    }
    // Above the first heading, highlight the first section so the sidebar always shows where you are.
    this.activeFragment.set(current ?? firstPresent);
  }
}
