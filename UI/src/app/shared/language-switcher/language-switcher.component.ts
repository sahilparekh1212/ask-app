import { Component, ElementRef, HostListener, computed, inject, signal } from '@angular/core';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { TranslateService } from '../../core/i18n/translate.service';

/** Minimal typing for the Google Website Translator globals we touch (loaded lazily). */
interface GoogleTranslateGlobal {
  google?: { translate?: { TranslateElement: new (options: object, elementId: string) => void } };
  googleTranslateElementInit?: () => void;
}

/**
 * Language switcher for the header (next to the profile avatar). Lists the app's built-in
 * languages (driven by the JSON dictionaries via {@link TranslateService}) and, as an escape
 * hatch for anything not translated in-app (e.g. the long-form About page), a "Google Translate"
 * option that lazily loads Google's Website Translator and lets the visitor pick any language.
 */
@Component({
  selector: 'app-language-switcher',
  imports: [TranslatePipe],
  templateUrl: './language-switcher.component.html',
  styleUrl: './language-switcher.component.scss',
})
export class LanguageSwitcherComponent {
  private readonly translate = inject(TranslateService);
  private readonly host = inject(ElementRef<HTMLElement>);

  readonly languages = this.translate.languages;
  readonly current = this.translate.lang;
  readonly open = signal(false);

  /** Short code shown on the trigger, e.g. "EN", "FR", "ZH". */
  readonly currentCode = computed(() => this.current().toUpperCase());

  private googleLoaded = false;

  toggle(): void {
    this.open.update((o) => !o);
  }

  select(code: string): void {
    this.translate.use(code);
    this.open.set(false);
  }

  translateWithGoogle(): void {
    this.open.set(false);
    this.loadGoogleTranslate();
  }

  /** Close the menu when clicking anywhere outside this component. */
  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  private loadGoogleTranslate(): void {
    const container = document.getElementById('google_translate_element');
    container?.removeAttribute('hidden');

    const w = window as unknown as GoogleTranslateGlobal;
    if (this.googleLoaded || w.google?.translate) {
      return;
    }
    this.googleLoaded = true;
    // The Google script calls this global once it has loaded.
    w.googleTranslateElementInit = () => {
      if (w.google?.translate) {
        new w.google.translate.TranslateElement(
          { pageLanguage: 'en', autoDisplay: false },
          'google_translate_element',
        );
      }
    };
    const script = document.createElement('script');
    script.src =
      'https://translate.google.com/translate_a/element.js?cb=googleTranslateElementInit';
    document.body.appendChild(script);
  }
}
