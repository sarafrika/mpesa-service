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
pnpm format       # Biome fixes
pnpm api:spec     # re-exports openapi.json from mpesa-service
```

The theme tokens in `src/app/globals.css` follow the console designs, with a light and a dark value for each colour. Status colours (`success`, `warning`, `info`, `destructive-soft`) are extra tokens I added for `StatusBadge`.

Sign-out clears the console's session but not the Keycloak single sign-on session, so the next sign-in goes straight through. Ending the Keycloak session as well is on the list.
