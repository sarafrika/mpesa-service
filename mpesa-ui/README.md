# mpesa-ui

The Sarafrika M-Pesa console. I built it as a React app behind a Next.js server: the server signs staff in through Keycloak and forwards their API calls to `mpesa-service`, so the browser only ever talks to this app.

## What lives where

- `src/auth.ts` sets up next-auth with the Keycloak provider as a public PKCE client, refreshes tokens on the server, and gives the browser only the user's name and realm roles.
- `src/app/api/mpesa/[...path]/route.ts` is the proxy. It forwards only `api/v1/mpesa/**` and `api/v1/admin/**`, adding the user's bearer token.
- `src/proxy.ts` sends anyone without a session to `/sign-in`.
- `src/api/fetcher.ts` is the fetch function Orval's hooks call; `src/api/generated/` is Orval's output and is not committed.
- `src/components/ui/` holds shadcn/ui components as the CLI added them; my own components sit beside that folder.

## Working on it

```
cp .env.example .env.local
pnpm install
pnpm dev          # regenerates the API client, then starts on :3000
pnpm lint         # Biome lint and format check
pnpm test         # unit tests for the pure helpers (src/**/*.test.ts, Node's test runner)
pnpm format       # Biome fixes
pnpm api:spec     # re-exports openapi.json from mpesa-service
```

The theme tokens in `src/app/globals.css` follow the console designs, with a light and a dark value for each colour. Status colours (`success`, `warning`, `info`, `destructive-soft`) are extra tokens I added for `StatusBadge`.

Signing out ends the Keycloak session too. The user menu asks `src/app/api/auth/federated-logout/route.ts` for Keycloak's end-session URL (built on the server from the id token next-auth keeps), clears the console's own session, then sends the browser to Keycloak, which comes back to `/sign-in`.

## Shared pieces

Screens build on the components beside `src/components/ui/`: `PageHeader`, `SectionCard`, `StatCard`, `StatusBadge`, `Money`, `MaskedPhone`, `KeyValueList`, `Timeline`, `FilterChips`, `SearchInput`, `DataTablePagination`, `ConfirmDialog`, `EmptyState`, `ErrorState` and `RoleGate` (with the `useHasRole` hook). Formatting lives in `src/lib/format.ts` and the role hierarchy in `src/lib/roles.ts`; `pnpm test` runs every `*.test.ts` under `src` (these and the screens' pure helpers) with Node's own test runner, so a test file may only import modules without `@/` paths. Charts take `moneyChartConfig` from `src/lib/chart.ts`, so money in is always `--chart-1` and money out `--chart-2`.

## Paged tables

Every list keeps its page, size and sort in the URL through `usePaging()` (`src/hooks/use-paging.ts`), so refresh, back and shared links land on the same rows. The URL shows pages from 1; the API counts from 0, and `readPaging`/`writePaging` in `src/lib/page.ts` translate. Changing size, sort or a filter (`setFilters`) goes back to the first page, and `useClampPage` moves to the last real page when rows disappear. Pass `pagedQueryOptions` to the query so the current rows stay visible while the next page loads, and put `DataTablePagination` with `onPageSizeChange` under the table. The service caps `size` at 100.

## Loading and errors

I want one broken card to stay one broken card. Every section of a screen sits inside `SectionBoundary`, which catches render errors, resets React Query and offers a retry, while `(console)/error.tsx`, `not-found.tsx` and `global-error.tsx` catch whatever gets past that. While a query has no data yet, show the skeleton shaped like the content (`TableSkeleton`, `StatCardSkeleton`, `ChartSkeleton`, `DetailPanelSkeleton`, `FormSkeleton`); when it fails with nothing to show, render `ErrorState` with the error; when a refresh fails over data you already have, keep the data and add `StaleNotice`.

The fetcher turns every failure into an `ApiError`, and `describeApiError` in `src/lib/api-errors.ts` turns that into plain copy (offline, no permission, not found, conflict, field errors, service down). Queries retry network and 5xx failures twice but never 4xx. Failed mutations toast on their own; forms that show field errors inline set `meta: { errorToast: false }` and call `applyFieldErrors(error, form.setError)`. If mpesa-service itself is down, the proxy answers 503 and the header shows a "service unreachable" pill; an offline banner covers the browser losing its connection.
