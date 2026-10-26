import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { SafeText } from '../../src/renderer/src/features/ai/SafeText';

describe('SafeText (XSS)', () => {
  it('renders HTML from the model as inert text', () => {
    const html = renderToStaticMarkup(
      <SafeText text={'<script>alert(1)</script>\n<img src=x onerror=alert(2)>'} />,
    );
    expect(html).not.toContain('<script');
    expect(html).not.toContain('<img');
    expect(html).toContain('&lt;script&gt;');
  });
  it('never creates links', () => {
    const html = renderToStaticMarkup(
      <SafeText text={'[click](javascript:alert(1)) https://evil.example'} />,
    );
    expect(html).not.toContain('<a');
  });
  it('supports bullets, bold and code', () => {
    const html = renderToStaticMarkup(<SafeText text={'Intro **key** `x`\n\n- one\n- two'} />);
    expect(html).toContain('<strong>key</strong>');
    expect(html).toContain('<code>x</code>');
    expect(html).toContain('<li>one</li>');
  });
});
