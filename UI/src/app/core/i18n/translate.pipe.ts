import { Pipe, PipeTransform, inject } from '@angular/core';
import { TranslateService } from './translate.service';

/**
 * Template shortcut for {@link TranslateService.t}: {@code {{ 'nav.dashboard' | t }}}.
 *
 * <p>Impure by design so it re-evaluates on every change-detection pass — a language switch
 * writes the service's language signal, which triggers CD, and the pipe then returns the string
 * from the newly-active dictionary. The set of keys is tiny, so the per-CD cost is negligible.
 */
@Pipe({ name: 't', standalone: true, pure: false })
export class TranslatePipe implements PipeTransform {
  private readonly translate = inject(TranslateService);

  transform(key: string, params?: Record<string, string | number>): string {
    return this.translate.t(key, params);
  }
}
