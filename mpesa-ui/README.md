# mpesa-ui

The Sarafrika M-Pesa console: a React app served by a Next.js server.

The server signs staff in with Keycloak (PKCE), keeps their tokens in an encrypted session cookie and forwards API calls to `mpesa-service`. The browser never receives a token.

**Stack:** Next.js 16, React 19, Tailwind CSS 4, shadcn/ui, React Query with an Orval-generated client, and Biome.

## Getting started

```bash
cp .env.example .env.local
pnpm install
pnpm dev
```

The console runs on `localhost:3000`. It needs `mpesa-service` and Keycloak running; see the [root README](../README.md).

## Scripts

| Command | Purpose |
|---|---|
| `pnpm dev` | Generates the API client, then starts the dev server |
| `pnpm build` | Production build |
| `pnpm lint` | Biome lint and format check |
| `pnpm format` | Applies Biome fixes |
| `pnpm test` | Unit tests |
| `pnpm api:spec` | Exports `openapi.json` from `mpesa-service` |

## Project structure

| Path | Contents |
|---|---|
| `src/app/(console)/` | Console screens, one folder per route |
| `src/app/api/mpesa/[...path]/route.ts` | Proxy to `mpesa-service` |
| `src/auth.ts` | Keycloak sign-in and token refresh |
| `src/proxy.ts` | Redirects signed-out users to `/sign-in` |
| `src/components/` | Shared components such as `StatusBadge`, `Money` and `ErrorState` |
| `src/components/ui/` | shadcn/ui components |
| `src/api/generated/` | Orval output; generated, not committed |
| `src/app/globals.css` | Theme tokens for light and dark mode |

## Conventions

- **Lists** keep their page, size and sort in the URL with `usePaging()`, with `DataTablePagination` below the table.
- **Loading** shows a skeleton shaped like the content.
- **Errors:**
  - Each section sits in a `SectionBoundary`, so a failure stays inside one card.
  - Failed loads show `ErrorState`.
  - If a refresh fails, the cached data stays on screen with a `StaleNotice`.
- **Forms** show server validation errors on their fields with `applyFieldErrors`.
- **Colours** come from theme tokens only. Money in uses `--chart-1` and money out `--chart-2`.
