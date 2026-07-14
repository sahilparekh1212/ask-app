import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { marked } from 'marked';
import { ARCHITECTURE_SVG } from './architecture-diagram';

@Component({
  selector: 'app-home',
  imports: [],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent {
  private readonly http = inject(HttpClient);
  private readonly sanitizer = inject(DomSanitizer);

  readonly content = signal<SafeHtml | null>(null);
  readonly failed = signal(false);
  readonly repoUrl = 'https://github.com/sahilparekh1212/ask-app';

  constructor() {
    // The About page renders the repository README verbatim — one source of truth for what the
    // project is. The root README is copied into the UI's public/ assets at build time (see
    // scripts/copy-readme.mjs + the Dockerfile's named build context), so it ships inside the
    // image and nginx serves it same-origin — the page has no runtime dependency on the backend.
    this.http.get('README.md', { responseType: 'text' }).subscribe({
      next: (md) => this.content.set(this.sanitizer.bypassSecurityTrustHtml(this.toHtml(md))),
      error: () => this.failed.set(true),
    });
  }

  /** Markdown → HTML, with the Architecture Mermaid block swapped for the static SVG diagram. */
  private toHtml(markdown: string): string {
    const html = marked.parse(markdown, { async: false, gfm: true }) as string;
    return html.replace(
      /<pre><code class="language-mermaid">[\s\S]*?<\/code><\/pre>/,
      ARCHITECTURE_SVG,
    );
  }
}
