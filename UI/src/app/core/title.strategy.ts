import { Injectable, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';

/**
 * Browser-tab titles as "Ask App - <page>" (e.g. "Ask App - Chat"), derived from each route's
 * `title` in {@link app.routes}. One strategy instead of hardcoding the prefix into every route,
 * so future routes get the format for free; a route without a title falls back to plain "Ask App".
 */
@Injectable({ providedIn: 'root' })
export class AppTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);

  override updateTitle(snapshot: RouterStateSnapshot): void {
    const page = this.buildTitle(snapshot);
    this.title.setTitle(page ? `Ask App - ${page}` : 'Ask App');
  }
}
