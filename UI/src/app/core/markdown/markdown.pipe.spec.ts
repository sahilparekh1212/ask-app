import { MarkdownPipe } from './markdown.pipe';

describe('MarkdownPipe', () => {
  const pipe = new MarkdownPipe();

  it('returns empty string for null/undefined/empty', () => {
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform(undefined)).toBe('');
    expect(pipe.transform('')).toBe('');
  });

  it('renders bold and inline code', () => {
    expect(pipe.transform('**bold** and `code`')).toBe(
      '<p><strong>bold</strong> and <code>code</code></p>',
    );
  });

  it('does not reinterpret ** inside inline code', () => {
    // The asterisks are part of the code span, not a bold marker.
    expect(pipe.transform('`a**b**c`')).toBe('<p><code>a**b**c</code></p>');
  });

  it('keeps underscores in code identifiers intact (no italic corruption)', () => {
    expect(pipe.transform('needs `VOYAGE_API_KEY`')).toBe(
      '<p>needs <code>VOYAGE_API_KEY</code></p>',
    );
  });

  it('renders an unordered list', () => {
    expect(pipe.transform('- one\n- two')).toBe('<ul><li>one</li><li>two</li></ul>');
  });

  it('renders an ordered list', () => {
    expect(pipe.transform('1. first\n2. second')).toBe('<ol><li>first</li><li>second</li></ol>');
  });

  it('renders headings shifted down two levels', () => {
    expect(pipe.transform('## Retrieval')).toBe('<h4>Retrieval</h4>');
  });

  it('renders a fenced code block with the content escaped', () => {
    expect(pipe.transform('```\nconst x = a < b;\n```')).toBe(
      '<pre><code>const x = a &lt; b;</code></pre>',
    );
  });

  it('renders http(s) links and ignores non-http schemes', () => {
    expect(pipe.transform('see [docs](https://example.com/x)')).toBe(
      '<p>see <a href="https://example.com/x" target="_blank" rel="noopener noreferrer">docs</a></p>',
    );
    // A javascript: URL does not match the http(s) pattern, so it stays inert text.
    expect(pipe.transform('[x](javascript:alert(1))')).toContain('[x](javascript:alert(1))');
  });

  it('escapes HTML so model output cannot inject markup', () => {
    const html = pipe.transform('<img src=x onerror=alert(1)>');
    expect(html).not.toContain('<img');
    expect(html).toContain('&lt;img');
  });

  it('separates paragraphs on blank lines and joins wrapped lines with <br>', () => {
    expect(pipe.transform('line one\nline two\n\nnext para')).toBe(
      '<p>line one<br>line two</p><p>next para</p>',
    );
  });
});
