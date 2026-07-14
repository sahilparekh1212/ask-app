# Multi-stage: build the Angular production bundle, then serve it (plus the /auth-api and
# /audit-api reverse proxies — see nginx.conf) from a plain nginx image. Build context is UI/.
#
# The About page renders the repo README, which lives at the repo root — outside this UI/ context.
# Callers must pass the root as the `repo` named build context (compose: additional_contexts;
# cd.yml: build-contexts; manual: --build-context repo=..):
#
#   docker build --build-context repo=.. -t ask-app-ui .
#
# Node 22 matches the Frontend CI workflow (.github/workflows/frontend-ci.yml).
FROM node:22-alpine AS build
WORKDIR /app

# Dependencies first, so source-only changes reuse the cached npm ci layer.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# README.md (repo root) is outside this context; drop it into public/ so the Angular asset pipeline
# bundles it and nginx serves it same-origin. The `prebuild` copy script is a no-op here (the root
# README isn't in the container) and keeps this file. See UI/scripts/copy-readme.mjs.
COPY --from=repo README.md ./public/README.md
RUN npm run build

FROM nginx:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
# The Angular application builder emits the browser bundle under dist/<project>/browser
# (the server/ sibling is for SSR, which this app doesn't use).
COPY --from=build /app/dist/ask-app-ui/browser /usr/share/nginx/html
