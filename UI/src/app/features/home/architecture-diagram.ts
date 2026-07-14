/**
 * Static architecture diagram for the About page.
 *
 * The README carries the same architecture as a Mermaid code block (GitHub renders it natively).
 * The About page renders the README with `marked`, which does not render Mermaid, so the component
 * swaps that code block for this hand-authored SVG. It is inlined into the DOM (not an <img>), so
 * it inherits the app's Primer design tokens (`--fg`, `--accent`, `--border`, …) and stays in sync
 * with the single dark theme.
 *
 * Layered bands, top to bottom: Client, Edge, Services, Backing stores, External AI, External SaaS
 * (Google OAuth2 · Google Analytics 4 · Sentry), and self-hosted Observability. Solid arrows are
 * the request/data path; dotted arrows are dependencies (AI calls, telemetry, SaaS integrations).
 */
export const ARCHITECTURE_SVG = `<figure class="arch-figure" aria-label="Architecture diagram">
<svg viewBox="0 0 760 492" role="img" class="arch-svg" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <marker id="arch-ah" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
      <path d="M0 0 L6 3 L0 6 Z" fill="var(--fg-muted)"/>
    </marker>
    <marker id="arch-ah-a" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
      <path d="M0 0 L6 3 L0 6 Z" fill="var(--accent)"/>
    </marker>
    <style>
      .arch-band{fill:var(--canvas-subtle);stroke:var(--border-muted);}
      .arch-glabel{fill:var(--fg-muted);font:600 10px var(--font);letter-spacing:.09em;}
      .arch-pill{fill:var(--canvas-overlay);stroke:var(--border);}
      .arch-nt{fill:var(--fg);font:600 12px var(--font);}
      .arch-e{stroke:var(--fg-muted);fill:none;stroke-width:1.3;}
      .arch-ea{stroke:var(--accent);fill:none;stroke-width:1.6;}
      .arch-el{fill:var(--fg-muted);font:400 9px var(--font);}
    </style>
  </defs>

  <rect class="arch-band" x="6" y="16"  width="748" height="50" rx="10"/>
  <rect class="arch-band" x="6" y="82"  width="748" height="50" rx="10"/>
  <rect class="arch-band" x="6" y="148" width="748" height="58" rx="10"/>
  <rect class="arch-band" x="6" y="222" width="748" height="50" rx="10"/>
  <rect class="arch-band" x="6" y="288" width="748" height="50" rx="10"/>
  <rect class="arch-band" x="6" y="354" width="748" height="50" rx="10"/>
  <rect class="arch-band" x="6" y="420" width="748" height="50" rx="10"/>

  <text class="arch-glabel" x="20" y="34">CLIENT</text>
  <text class="arch-glabel" x="20" y="100">EDGE</text>
  <text class="arch-glabel" x="20" y="166">SERVICES</text>
  <text class="arch-glabel" x="20" y="240">BACKING STORES</text>
  <text class="arch-glabel" x="20" y="306">EXTERNAL AI</text>
  <text class="arch-glabel" x="20" y="372">EXTERNAL SaaS</text>
  <text class="arch-glabel" x="20" y="438">OBSERVABILITY · SELF-HOSTED</text>

  <g><rect class="arch-pill" x="250" y="27"  width="180" height="30" rx="8"/><text class="arch-nt" x="340" y="46"  text-anchor="middle">Reviewer / browser</text></g>
  <g><rect class="arch-pill" x="452" y="27"  width="210" height="30" rx="8"/><text class="arch-nt" x="557" y="46"  text-anchor="middle">MCP client (Claude Code)</text></g>

  <g><rect class="arch-pill" x="250" y="93"  width="150" height="30" rx="8"/><text class="arch-nt" x="325" y="112" text-anchor="middle">Caddy · TLS</text></g>
  <g><rect class="arch-pill" x="430" y="93"  width="232" height="30" rx="8"/><text class="arch-nt" x="546" y="112" text-anchor="middle">UI · nginx — SPA + proxy</text></g>

  <g><rect class="arch-pill" x="176" y="161" width="248" height="34" rx="8"/><text class="arch-nt" x="300" y="183" text-anchor="middle">Auth :8085 · JWT / JWKS</text></g>
  <g><rect class="arch-pill" x="452" y="161" width="252" height="34" rx="8"/><text class="arch-nt" x="578" y="183" text-anchor="middle">Audit :8083 · chat · RAG · /mcp</text></g>

  <g><rect class="arch-pill" x="176" y="233" width="110" height="30" rx="8"/><text class="arch-nt" x="231" y="252" text-anchor="middle">Redis</text></g>
  <g><rect class="arch-pill" x="306" y="233" width="176" height="30" rx="8"/><text class="arch-nt" x="394" y="252" text-anchor="middle">Kafka (Redpanda)</text></g>
  <g><rect class="arch-pill" x="502" y="233" width="202" height="30" rx="8"/><text class="arch-nt" x="603" y="252" text-anchor="middle">PostgreSQL + pgvector</text></g>

  <g><rect class="arch-pill" x="300" y="299" width="176" height="30" rx="8"/><text class="arch-nt" x="388" y="318" text-anchor="middle">Claude (Anthropic)</text></g>
  <g><rect class="arch-pill" x="500" y="299" width="190" height="30" rx="8"/><text class="arch-nt" x="595" y="318" text-anchor="middle">Voyage embeddings</text></g>

  <g><rect class="arch-pill" x="176" y="365" width="156" height="30" rx="8"/><text class="arch-nt" x="254" y="384" text-anchor="middle">Google OAuth2</text></g>
  <g><rect class="arch-pill" x="352" y="365" width="182" height="30" rx="8"/><text class="arch-nt" x="443" y="384" text-anchor="middle">Google Analytics 4</text></g>
  <g><rect class="arch-pill" x="556" y="365" width="110" height="30" rx="8"/><text class="arch-nt" x="611" y="384" text-anchor="middle">Sentry</text></g>

  <g><rect class="arch-pill" x="150" y="431" width="128" height="30" rx="8"/><text class="arch-nt" x="214" y="450" text-anchor="middle">Prometheus</text></g>
  <g><rect class="arch-pill" x="298" y="431" width="86"  height="30" rx="8"/><text class="arch-nt" x="341" y="450" text-anchor="middle">Loki</text></g>
  <g><rect class="arch-pill" x="404" y="431" width="92"  height="30" rx="8"/><text class="arch-nt" x="450" y="450" text-anchor="middle">Tempo</text></g>
  <g><rect class="arch-pill" x="516" y="431" width="188" height="30" rx="8"/><text class="arch-nt" x="610" y="450" text-anchor="middle">Grafana (read-only)</text></g>

  <path class="arch-ea" marker-end="url(#arch-ah-a)" d="M338 57 L332 91"/>
  <path class="arch-e"  marker-end="url(#arch-ah)"   d="M400 108 L428 108"/>
  <path class="arch-ea" marker-end="url(#arch-ah-a)" d="M500 123 L334 159"/>
  <path class="arch-ea" marker-end="url(#arch-ah-a)" d="M580 123 L580 159"/>
  <path class="arch-e" stroke-dasharray="3 3" marker-end="url(#arch-ah)" d="M557 57 C660 96 650 128 590 159"/>
  <text class="arch-el" x="628" y="104">/mcp</text>

  <path class="arch-e" marker-end="url(#arch-ah)" d="M250 195 L238 231"/>
  <path class="arch-e" marker-end="url(#arch-ah)" d="M330 195 L388 231"/>
  <path class="arch-e" marker-end="url(#arch-ah)" d="M470 233 L548 197"/>
  <path class="arch-e" marker-end="url(#arch-ah)" d="M600 195 L606 231"/>

  <path class="arch-e" stroke-dasharray="3 3" marker-end="url(#arch-ah)" d="M560 195 C470 245 415 275 392 297"/>
  <path class="arch-e" stroke-dasharray="3 3" marker-end="url(#arch-ah)" d="M600 195 C700 235 700 275 598 297"/>
  <text class="arch-el" x="646" y="260">AI calls</text>

  <path class="arch-e" stroke-dasharray="3 3" marker-end="url(#arch-ah)" d="M250 197 C112 250 112 400 208 429"/>
  <text class="arch-el" x="12" y="300">metrics · logs · traces</text>

  <path class="arch-e" stroke-dasharray="3 3" marker-end="url(#arch-ah)" d="M604 197 C732 250 732 336 622 365"/>
  <text class="arch-el" x="556" y="350">OAuth · analytics · errors</text>
</svg>
</figure>`;
