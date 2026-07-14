// Copies the repository root README.md into the Angular public/ assets so the About page can
// fetch and render it at runtime (single source of truth for "what this project is").
//
// Runs as a `prestart`/`prebuild` hook. Locally, in `ng serve`, `ng build`, and Frontend CI the
// root README sits one level above UI/, so it is copied here. Inside the UI Docker build the root
// is outside the build context, so the Dockerfile injects README.md via the `repo` named build
// context *before* `npm run build` runs — in that case the source below is absent and we simply
// keep the file the Dockerfile already placed.
import { copyFileSync, existsSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const uiRoot = dirname(dirname(fileURLToPath(import.meta.url))); // .../UI
const source = join(uiRoot, '..', 'README.md'); // repo root README
const destDir = join(uiRoot, 'public');
const dest = join(destDir, 'README.md');

if (existsSync(source)) {
  mkdirSync(destDir, { recursive: true });
  copyFileSync(source, dest);
  console.log('[copy-readme] copied root README.md -> UI/public/README.md');
} else if (existsSync(dest)) {
  console.log('[copy-readme] root README.md not present; keeping existing UI/public/README.md');
} else {
  console.warn('[copy-readme] no README.md found (repo root or public/); the About page will 404');
}
