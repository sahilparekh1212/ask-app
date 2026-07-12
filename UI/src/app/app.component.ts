import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

import { AuthService } from './core/auth/auth.service';
import { TranslatePipe } from './core/i18n/translate.pipe';
import { LanguageSwitcherComponent } from './shared/language-switcher/language-switcher.component';
import { environment } from '../environments/environment';

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
  about:
    'M0 8a8 8 0 1 1 16 0A8 8 0 0 1 0 8Zm8-6.5a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13ZM6.5 7.75A.75.75 0 0 1 7.25 7h1a.75.75 0 0 1 .75.75v2.75h.25a.75.75 0 0 1 0 1.5h-2a.75.75 0 0 1 0-1.5h.25v-2h-.25a.75.75 0 0 1-.75-.75ZM8 6a1 1 0 1 1 0-2 1 1 0 0 1 0 2Z',
  chat: 'M1 2.75C1 1.784 1.784 1 2.75 1h10.5c.966 0 1.75.784 1.75 1.75v7.5A1.75 1.75 0 0 1 13.25 11H9.06l-2.573 2.573A1.458 1.458 0 0 1 4 12.543V11H2.75A1.75 1.75 0 0 1 1 9.25Zm1.75-.25a.25.25 0 0 0-.25.25v6.5c0 .138.112.25.25.25h2a.75.75 0 0 1 .75.75v1.94l2.22-2.22a.75.75 0 0 1 .53-.22h4.25a.25.25 0 0 0 .25-.25v-7.5a.25.25 0 0 0-.25-.25Z',
  flashcards:
    'M7.122.392a1.75 1.75 0 0 1 1.756 0l5.003 2.902c.83.481.83 1.68 0 2.162L8.878 8.358a1.75 1.75 0 0 1-1.756 0L2.119 5.456c-.83-.482-.83-1.681 0-2.162Zm1.003 1.297a.25.25 0 0 0-.25 0L3.245 4.374l4.63 2.685a.25.25 0 0 0 .25 0l4.63-2.685ZM1.593 10.365a.75.75 0 0 1 1.025-.273l5.257 3.05a.25.25 0 0 0 .25 0l5.257-3.05a.75.75 0 0 1 .752 1.298l-5.257 3.05a1.75 1.75 0 0 1-1.756 0l-5.257-3.05a.75.75 0 0 1-.274-1.025Zm0-3.192a.75.75 0 0 1 1.025-.273l5.257 3.05a.25.25 0 0 0 .25 0l5.257-3.05a.75.75 0 1 1 .752 1.298l-5.257 3.05a1.75 1.75 0 0 1-1.756 0l-5.257-3.05a.75.75 0 0 1-.274-1.025Z',
  observability:
    'M1.5 1.75V13.5h13.75a.75.75 0 0 1 0 1.5H.75a.75.75 0 0 1-.75-.75V1.75a.75.75 0 0 1 1.5 0Zm14.28 2.53-5.25 5.25a.75.75 0 0 1-1.06 0L7 7.06 4.28 9.78a.751.751 0 0 1-1.042-.018.751.751 0 0 1-.018-1.042l3.25-3.25a.75.75 0 0 1 1.06 0L10 7.94l4.72-4.72a.751.751 0 0 1 1.042.018.751.751 0 0 1 .018 1.042Z',
  profile:
    'M10.561 8.073a6 6 0 0 1 3.432 5.142.75.75 0 1 1-1.498.07 4.5 4.5 0 0 0-8.99 0 .75.75 0 0 1-1.498-.07 6 6 0 0 1 3.431-5.142 3.999 3.999 0 1 1 5.123 0ZM10.5 5a2.5 2.5 0 1 0-5 0 2.5 2.5 0 0 0 5 0Z',
  signin:
    'M2 2.75C2 1.784 2.784 1 3.75 1h2.5a.75.75 0 0 1 0 1.5h-2.5a.25.25 0 0 0-.25.25v10.5c0 .138.112.25.25.25h2.5a.75.75 0 0 1 0 1.5h-2.5A1.75 1.75 0 0 1 2 13.25Zm6.56 4.5h5.69a.75.75 0 0 1 0 1.5H8.56l1.97 1.97a.749.749 0 1 1-1.06 1.06L5.72 8.53a.751.751 0 0 1 0-1.06l3.75-3.75a.749.749 0 1 1 1.06 1.06L8.56 6.75Z',
};

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe, LanguageSwitcherComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly title = 'AI-Sandbox';
  readonly isAuthenticated = this.auth.isAuthenticated;
  private readonly grafanaUrl = environment.grafanaUrl;

  // Current URL as a signal, so the top-bar title and the active sidebar recompute on navigation.
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
    if (u.startsWith('/chat')) return 'chat';
    if (u.startsWith('/flashcards')) return 'flashcards';
    if (u.startsWith('/observability')) return 'observability';
    if (u.startsWith('/profile')) return 'profile';
    if (u.startsWith('/login')) return 'login';
    return 'about';
  });

  /** The rail: primary sections. Profile/sign-in are rendered separately at the rail's bottom. */
  readonly sections: Section[] = [
    { key: 'about', labelKey: 'nav.about', route: '/about', icon: ICON.about },
    { key: 'chat', labelKey: 'nav.chat', route: '/chat', icon: ICON.chat },
    { key: 'flashcards', labelKey: 'nav.flashcards', route: '/flashcards', icon: ICON.flashcards },
    {
      key: 'observability',
      labelKey: 'nav.observability',
      route: '/observability',
      icon: ICON.observability,
    },
  ];

  readonly profileIcon = ICON.profile;
  readonly signinIcon = ICON.signin;

  // Per-section sidebar content. About's on-page anchors use literals (the About prose is
  // English by design — Google Translate covers it); cross-section links reuse the nav.* keys.
  private readonly sidebars: Record<string, { titleKey: string; groups: SidebarGroup[] }> = {
    about: {
      titleKey: 'nav.about',
      groups: [
        {
          id: 'about-sections',
          titleKey: 'nav.onThisPage',
          items: [
            { text: 'Overview', fragment: 'overview' },
            { text: 'Tech stack', fragment: 'tech-stack' },
            { text: 'Design decisions', fragment: 'design-decisions' },
            { text: 'Features', fragment: 'features' },
          ],
        },
        {
          id: 'about-connect',
          titleKey: 'nav.connect',
          items: [
            { text: 'GitHub ↗', href: 'https://github.com/sahilparekh1212/AI-Sandbox' },
            { text: 'LinkedIn ↗', href: 'https://www.linkedin.com/in/sahilparekh1212/' },
          ],
        },
      ],
    },
    chat: {
      titleKey: 'nav.chat',
      groups: [
        {
          id: 'chat-related',
          titleKey: 'nav.related',
          items: [
            { labelKey: 'nav.observability', route: '/observability' },
            { labelKey: 'nav.about', route: '/about' },
          ],
        },
      ],
    },
    flashcards: {
      titleKey: 'nav.flashcards',
      groups: [
        {
          id: 'flashcards-related',
          titleKey: 'nav.related',
          items: [
            { labelKey: 'nav.chat', route: '/chat' },
            { labelKey: 'nav.about', route: '/about' },
          ],
        },
      ],
    },
    observability: {
      titleKey: 'nav.observability',
      groups: [
        {
          id: 'obs-onpage',
          titleKey: 'nav.onThisPage',
          items: [
            { text: 'Filters', fragment: 'filters' },
            { text: 'Summary', fragment: 'summary' },
            { text: 'Trends', fragment: 'trends' },
            { text: 'Log', fragment: 'log' },
          ],
        },
        {
          id: 'obs-related',
          titleKey: 'nav.related',
          items: [
            { text: 'Grafana ↗', href: this.grafanaUrl },
            { labelKey: 'nav.about', route: '/about' },
          ],
        },
      ],
    },
    profile: {
      titleKey: 'nav.profile',
      groups: [
        {
          id: 'profile-related',
          titleKey: 'nav.related',
          items: [
            { labelKey: 'nav.observability', route: '/observability' },
            { labelKey: 'nav.about', route: '/about' },
          ],
        },
      ],
    },
  };

  /** The sidebar for the active section (empty on /login, which has no chrome). */
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
}
