import { Pipe, PipeTransform } from '@angular/core';

/**
 * Renders the small Markdown subset the chat assistant emits (headings, bold, inline code, fenced
 * code, bullet/numbered lists, links, paragraphs) to an HTML string for `[innerHTML]`. Hand-rolled
 * rather than pulling in a Markdown library — the same no-framework posture as the i18n layer, and
 * the input grammar is narrow and known.
 *
 * <p>Safe by construction: every piece of model text is HTML-escaped <em>before</em> any Markdown
 * transform runs, and only a fixed set of safe tags is ever emitted — so no markup from the model
 * (or a prompt-injection attempt) survives as live HTML. Angular's `[innerHTML]` sanitizer is a
 * second layer on top. Links are limited to `http(s)` URLs, closing off `javascript:` hrefs.
 */
@Pipe({ name: 'markdown' })
export class MarkdownPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    if (!value) {
      return '';
    }
    const lines = value.replace(/\r\n?/g, '\n').split('\n');
    const out: string[] = [];
    let i = 0;

    while (i < lines.length) {
      const trimmed = lines[i].trim();

      // Fenced code block ``` ... ```
      if (/^```/.test(trimmed)) {
        i++;
        const buf: string[] = [];
        while (i < lines.length && !/^```/.test(lines[i].trim())) {
          buf.push(lines[i]);
          i++;
        }
        i++; // consume the closing fence
        out.push(`<pre><code>${this.escape(buf.join('\n'))}</code></pre>`);
        continue;
      }

      // Heading (#..######) — shifted down two levels so it stays modest inside a chat bubble.
      const heading = /^(#{1,6})\s+(.*)$/.exec(trimmed);
      if (heading) {
        const level = Math.min(heading[1].length + 2, 6);
        out.push(`<h${level}>${this.inline(this.escape(heading[2]))}</h${level}>`);
        i++;
        continue;
      }

      // Unordered list (-, *, +)
      if (/^[-*+]\s+/.test(trimmed)) {
        const items: string[] = [];
        while (i < lines.length && /^\s*[-*+]\s+/.test(lines[i])) {
          items.push(`<li>${this.inline(this.escape(lines[i].replace(/^\s*[-*+]\s+/, '')))}</li>`);
          i++;
        }
        out.push(`<ul>${items.join('')}</ul>`);
        continue;
      }

      // Ordered list (1. 2. ...)
      if (/^\d+\.\s+/.test(trimmed)) {
        const items: string[] = [];
        while (i < lines.length && /^\s*\d+\.\s+/.test(lines[i])) {
          items.push(`<li>${this.inline(this.escape(lines[i].replace(/^\s*\d+\.\s+/, '')))}</li>`);
          i++;
        }
        out.push(`<ol>${items.join('')}</ol>`);
        continue;
      }

      // Blank line — paragraph separator.
      if (trimmed === '') {
        i++;
        continue;
      }

      // Paragraph — consecutive plain lines, joined with <br>.
      const para: string[] = [];
      while (i < lines.length && !this.isBlockStart(lines[i])) {
        para.push(this.inline(this.escape(lines[i].trim())));
        i++;
      }
      out.push(`<p>${para.join('<br>')}</p>`);
    }

    return out.join('');
  }

  /** True when a line opens a non-paragraph block (so a paragraph run should stop before it). */
  private isBlockStart(line: string): boolean {
    const t = line.trim();
    return (
      t === '' ||
      /^```/.test(t) ||
      /^#{1,6}\s+/.test(t) ||
      /^[-*+]\s+/.test(t) ||
      /^\d+\.\s+/.test(t)
    );
  }

  /**
   * Inline spans on already-escaped text: inline code, bold, then http(s) links. Splitting on the
   * code-span pattern keeps a capturing group, so `split` yields alternating segments — even
   * indices are ordinary text, odd indices are code content. Bold/link transforms run only on the
   * ordinary-text segments, so `**` or `[]` that happen to sit inside `` `code` `` are left alone.
   */
  private inline(text: string): string {
    return text
      .split(/`([^`]+)`/g)
      .map((segment, index) => {
        if (index % 2 === 1) {
          return `<code>${segment}</code>`;
        }
        return segment
          .replace(/\*\*([^*]+?)\*\*/g, '<strong>$1</strong>')
          .replace(
            /\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g,
            '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>',
          );
      })
      .join('');
  }

  /** Escape the HTML special characters so no model text can inject markup. */
  private escape(text: string): string {
    return text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }
}
