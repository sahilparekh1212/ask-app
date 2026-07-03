# Multi-stage: build the Angular production bundle, then serve it (plus the /auth-api and
# /audit-api reverse proxies — see nginx.conf) from a plain nginx image. Build context is UI/.
#
#   docker build -t ai-sandbox-ui .
#
# Node 22 matches the Frontend CI workflow (.github/workflows/frontend-ci.yml).
FROM node:22-alpine AS build
WORKDIR /app

# Dependencies first, so source-only changes reuse the cached npm ci layer.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
# The Angular application builder emits the browser bundle under dist/<project>/browser
# (the server/ sibling is for SSR, which this app doesn't use).
COPY --from=build /app/dist/ai-sandbox-ui/browser /usr/share/nginx/html
