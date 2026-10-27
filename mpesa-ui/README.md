# mpesa-ui

This is the console our team uses to manage Sarafrika's M-Pesa paybills. It's a React app with a small Next.js server in front of it. The server signs people in with Keycloak and passes their requests on to `mpesa-service`, so the browser never sees a token.

## Getting started

```
cp .env.example .env.local
pnpm install
pnpm dev       # starts on :3000
```

| Command | What it does |
|---|---|
| `pnpm lint` | Checks lint and formatting with Biome |
| `pnpm format` | Fixes what Biome can |
| `pnpm test` | Runs the unit tests |
| `pnpm api:spec` | Gets a fresh `openapi.json` from `mpesa-service` |

`pnpm dev` regenerates the API client from `openapi.json` before it starts. The generated code goes into `src/api/generated/`, which isn't committed, so don't edit it by hand.

## Where things are

| Path | What's there |
|---|---|
| `src/auth.ts` | Keycloak sign-in and token refresh |
| `src/app/api/mpesa/[...path]/route.ts` | The proxy that forwards calls to the service |
| `src/proxy.ts` | Sends anyone who isn't signed in to `/sign-in` |
| `src/components/ui/` | shadcn components, as the CLI added them |
| `src/components/` | Our own components, such as `StatusBadge`, `Money` and `ErrorState` |
| `src/app/globals.css` | Colours for light and dark mode |

## Building a screen

- **Lists:** use `usePaging()` for the page, size and sort, which keeps them in the URL. Put `DataTablePagination` under the table.
- **Loading:** show a skeleton shaped like the content, never a bare spinner.
- **Errors:**
  - Wrap each section in `SectionBoundary`, so one broken card doesn't take the whole page down.
  - Use `ErrorState` for a failed load.
  - If a refresh fails but data is already on screen, keep the data and add `StaleNotice`.
- **Forms:** show field errors on the form with `applyFieldErrors`.
- **Colours:** use the theme tokens, never a hard-coded colour. Money in is `--chart-1` and money out is `--chart-2`.
