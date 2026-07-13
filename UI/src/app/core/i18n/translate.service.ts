import { DOCUMENT } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import en from '../../../../public/i18n/en.json';

/** A selectable UI language: BCP-47 code + its endonym (name in that language). */
export interface LanguageOption {
  code: string;
  label: string;
}

/**
 * The languages offered in the switcher. The app currently ships **English only** — the
 * hand-rolled i18n machinery (dictionary lookups, the {@code t} pipe, the switcher, and the
 * on-demand Google Translate escape hatch) is kept intact so more languages can be re-added by
 * simply dropping a {@code public/i18n/<code>.json} file and one entry here. English is bundled
 * below as the always-present source/fallback; any additional entry is fetched as JSON on demand.
 */
export const LANGUAGES: readonly LanguageOption[] = [{ code: 'en', label: 'English' }];

const STORAGE_KEY = 'ais.lang';
type Dict = Record<string, string>;

/**
 * A tiny hand-rolled runtime i18n layer (no framework). The UI is driven from per-language JSON
 * dictionaries: English is bundled as the always-present fallback and the other languages are
 * fetched once from {@code public/i18n/} and cached in memory for the session (the files change
 * only on deploy, so an HTTP+in-memory cache is plenty). The current language is a signal, so
 * the {@code t} pipe re-renders the whole UI on switch; the choice is persisted and mirrored to
 * {@code <html lang>} (which also lets Google Translate detect the source page).
 */
@Injectable({ providedIn: 'root' })
export class TranslateService {
  private readonly http = inject(HttpClient);
  private readonly document = inject(DOCUMENT);

  private readonly fallback = en as Dict;
  private readonly cache = new Map<string, Dict>([['en', en as Dict]]);

  private readonly _lang = signal<string>('en');
  private readonly _dict = signal<Dict>(en as Dict);

  /** The active language code (signal — read it in templates to react to switches). */
  readonly lang = this._lang.asReadonly();
  readonly languages = LANGUAGES;

  constructor() {
    const saved = this.readSaved();
    if (saved && saved !== 'en' && LANGUAGES.some((l) => l.code === saved)) {
      this.use(saved);
    } else {
      this.applyDocumentLang('en');
    }
  }

  /**
   * Translate a key, interpolating {@code {token}} params. Falls back to English, then to the
   * key itself, so a missing translation degrades gracefully instead of showing a blank.
   */
  t(key: string, params?: Record<string, string | number>): string {
    let value = this._dict()[key] ?? this.fallback[key] ?? key;
    if (params) {
      for (const name of Object.keys(params)) {
        value = value.replace(`{${name}}`, String(params[name]));
      }
    }
    return value;
  }

  /** Switch language: apply immediately if cached, else fetch its JSON (keeping the current one on failure). */
  use(code: string): void {
    if (!LANGUAGES.some((l) => l.code === code)) {
      return;
    }
    const cached = this.cache.get(code);
    if (cached) {
      this.apply(code, cached);
      return;
    }
    this.http.get<Dict>(`i18n/${code}.json`).subscribe({
      next: (dict) => {
        // Overlay onto English so any key the translation is missing still renders (in English).
        const merged = { ...this.fallback, ...dict };
        this.cache.set(code, merged);
        this.apply(code, merged);
      },
      error: () => {
        /* leave the current language in place if the file can't be loaded */
      },
    });
  }

  private apply(code: string, dict: Dict): void {
    this._dict.set(dict);
    this._lang.set(code);
    this.persist(code);
    this.applyDocumentLang(code);
  }

  private applyDocumentLang(code: string): void {
    this.document.documentElement.lang = code;
  }

  private readSaved(): string | null {
    try {
      return localStorage.getItem(STORAGE_KEY);
    } catch {
      return null;
    }
  }

  private persist(code: string): void {
    try {
      localStorage.setItem(STORAGE_KEY, code);
    } catch {
      /* private mode / storage disabled — language just won't persist */
    }
  }
}
