/**
 * Static architecture diagram for the About page.
 *
 * The README carries the same architecture as a Mermaid code block (GitHub renders it natively).
 * The About page renders the README with `marked`, which does not render Mermaid, so the component
 * swaps that code block for this hand-authored SVG. It is inlined into the DOM (not an <img>), so
 * it inherits the app's Primer design tokens (`--fg`, `--accent`, `--border`, …) and stays in sync
 * with the single dark theme.
 */
export const ARCHITECTURE_SVG = `<figure class="arch-figure" aria-label="Architecture diagram">
<svg viewBox="0 0 740 466" role="img" class="arch-svg" xmlns="http://www.w3.org/2000/svg">
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

  <rect class="arch-band" x="6" y="20"  width="728" height="54" rx="10"/>
  <rect class="arch-band" x="6" y="92"  width="728" height="54" rx="10"/>
  <rect class="arch-band" x="6" y="164" width="728" height="62" rx="10"/>
  <rect class="arch-band" x="6" y="244" width="728" height="54" rx="10"/>
  <rect class="arch-band" x="6" y="316" width="728" height="54" rx="10"/>
  <rect class="arch-band" x="6" y="388" width="728" height="54" rx="10"/>

  <text class="arch-glabel" x="20" y="41">CLIENT</text>
  <text class="arch-glabel" x="20" y="113">EDGE</text>
  <text class="arch-glabel" x="20" y="189">SERVICES</text>
  <text class="arch-glabel" x="20" y="265">BACKING STORES</text>
  <text class="arch-glabel" x="20" y="337">EXTERNAL AI</text>
  <text class="arch-glabel" x="20" y="409">OBSERVABILITY</text>

  <g><rect class="arch-pill" x="250" y="31" width="180" height="32" rx="8"/><text class="arch-nt" x="340" y="51" text-anchor="middle">Reviewer / browser</text></g>
  <g><rect class="arch-pill" x="452" y="31" width="212" height="32" rx="8"/><text class="arch-nt" x="558" y="51" text-anchor="middle">MCP client (Claude Code)</text></g>

  <g><rect class="arch-pill" x="250" y="103" width="150" height="32" rx="8"/><text class="arch-nt" x="325" y="123" text-anchor="middle">Caddy · TLS</text></g>
  <g><rect class="arch-pill" x="430" y="103" width="234" height="32" rx="8"/><text class="arch-nt" x="547" y="123" text-anchor="middle">UI · nginx — SPA + proxy</text></g>

  <g><rect class="arch-pill" x="176" y="177" width="248" height="36" rx="8"/><text class="arch-nt" x="300" y="199" text-anchor="middle">Auth :8085 · JWT / JWKS</text></g>
  <g><rect class="arch-pill" x="452" y="177" width="252" height="36" rx="8"/><text class="arch-nt" x="578" y="199" text-anchor="middle">Audit :8083 · chat · RAG · /mcp</text></g>

  <g><rect class="arch-pill" x="176" y="255" width="110" height="32" rx="8"/><text class="arch-nt" x="231" y="275" text-anchor="middle">Redis</text></g>
  <g><rect class="arch-pill" x="306" y="255" width="176" height="32" rx="8"/><text class="arch-nt" x="394" y="275" text-anchor="middle">Kafka (Redpanda)</text></g>
  <g><rect class="arch-pill" x="502" y="255" width="202" height="32" rx="8"/><text class="arch-nt" x="603" y="275" text-anchor="middle">PostgreSQL + pgvector</text></g>

  <g><rect class="arch-pill" x="300" y="327" width="176" height="32" rx="8"/><text class="arch-nt" x="388" y="347" text-anchor="middle">Claude (Anthropic)</text></g>
  <g><rect class="arch-pill" x="500" y="327" width="190" height="32" rx="8"/><text class="arch-nt" x="595" y="347" text-anchor="middle">Voyage embeddings</text></g>

  <g><rect class="arch-pill" x="150" y="399" width="128" height="32" rx="8"/><text class="arch-nt" x="214" y="419" text-anchor="middle">Prometheus</text></g>
  <g><rect class="arch-pill" x="298" y="399" width="86"  height="32" rx="8"/><text class="arch-nt" x="341" y="419" text-anchor="middle">Loki</text></g>
  <g><rect class="arch-pill" x="404" y="399" width="92"  height="32" rx="8"/><text class="arch-nt" x="450" y="419" text-anchor="middle">Tempo</text></g>
  <g><rect class="arch-pill" x="516" y="399" width="188" height="32" rx="8"/><text class="arch-nt" x="610" y="419" text-anchor="middle">Grafana (read-only)</text></g>

  <path class="arch-ea" marker-end="url(#arch-ah-a)" d="M340 63 L340 101"/>
  <path class="arch-e"  marker-end="url(#arch-ah)"   d="M400 119 L428 119"/>
  <path class="arch-ea" marker-end="url(#arch-ah-a)" d="M470 135 L332 175"/>
  <path class="arch-ea" marker-end="url(#arch-ah-a)" d="M520 135 L560 175"/>
  <path class="arch-e"  marker-end="url(#arch-ah)"   d="M250 213 L238 253"/>
  <path class="arch-e"  marker-end="url(#arch-ah)"   d="M330 213 L385 253"/>
  <path class="arch-e"  marker-end="url(#arch-ah)"   d="M470 253 L548 215"/>
  <path class="arch-e"  marker-end="url(#arch-ah)"   d="M590 213 L603 253"/>

  <path class="arch-e" stroke-dasharray="3 3" marker-end="url(#arch-ah)" d="M540 213 C468 268 420 300 398 325"/>
  <path class="arch-e" stroke-dasharray="3 3" marker-end="url(#arch-ah)" d="M604 213 C690 250 690 300 600 325"/>
  <text class="arch-el" x="650" y="248">AI calls</text>

  <path class="arch-e" stroke-dasharray="3 3" marker-end="url(#arch-ah)" d="M300 213 C150 252 118 350 196 397"/>
  <text class="arch-el" x="44" y="330">metrics · logs · traces</text>

  <path class="arch-e" marker-end="url(#arch-ah)" d="M278 411 L514 409"/>
  <path class="arch-e" marker-end="url(#arch-ah)" d="M384 415 L514 414"/>
  <path class="arch-e" marker-end="url(#arch-ah)" d="M496 419 L514 418"/>
</svg>
</figure>`;
