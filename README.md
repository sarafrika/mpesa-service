# Sarafrika M-Pesa

This repo holds two things that ship together: `mpesa-service`, the only part of Sarafrika that talks to Safaricom Daraja, and `mpesa-ui`, the console our team uses to manage paybills, tills and the products that pay through them, starting with Elimika.

```
mpesa-service/   Spring Boot resource server: STK push, C2B, B2C payouts, Daraja callbacks
mpesa-ui/        Next.js console; its server signs staff in and proxies the API for the React app
docker/          Keycloak realm for local development
```

## How the pieces fit

I kept every Daraja credential and every Safaricom call inside `mpesa-service`. It accepts only Keycloak tokens that carry the `mpesa-service` audience, so an ordinary login to another Sarafrika app can't call it.

People reach it through `mpesa-ui`. Its Next.js server signs them in with a Keycloak PKCE client, keeps the tokens in an encrypted cookie, and forwards `/api/mpesa/api/v1/mpesa/**` and `/api/mpesa/api/v1/admin/**` with the user's token. The browser never holds a token, and the callback endpoints are never proxied.

Products such as Elimika call `mpesa-service` directly with their own Keycloak client credentials.

## The API contract

`mpesa-ui` builds its typed client from `mpesa-ui/openapi.json` with Orval: React Query hooks, models and zod schemas. When you change the service's API, refresh that file and commit it with the change:

```
cd mpesa-service && ./gradlew exportOpenApi
```

The service's CI check fails if the committed spec is stale, and the UI's CI check regenerates the client and builds it, so a breaking change shows up in the same pull request.

## Running it locally

`docker compose up` starts Postgres 17 and Keycloak on `localhost:8081` with the `sarafrika` realm. The realm has the `mpesa-ui` PKCE client, an `elimika-backend` machine client, the four console roles and a `console-admin` user (password `console-admin`).

Then run the two apps on your machine:

```
# mpesa-service on :8080
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/mpesa SPRING_DATASOURCE_USERNAME=mpesa \
SPRING_DATASOURCE_PASSWORD=mpesa KEYCLOAK_ISSUER_URI=http://localhost:8081/realms/sarafrika \
KEYCLOAK_AUDIENCE=mpesa-service CALLBACK_IP_WHITELIST_ENABLED=false \
MPESA_ENCRYPTION_KEY=ZGV2LW9ubHktbXBlc2EtZW5jcnlwdGlvbi1rZXktMzI= ./gradlew bootRun

# mpesa-ui on :3000, after copying .env.example to .env.local
pnpm install && pnpm dev
```

`MPESA_ENCRYPTION_KEY` encrypts the Daraja credentials stored for each shortcode, and the service won't start without it. That key is for local use only; production gets its own from `openssl rand -base64 32`.

To run everything in containers instead, use `docker compose --profile apps up --build`. Inside Docker the apps reach Keycloak as `keycloak:8080` while tokens keep the `localhost:8081` issuer, which is why `KEYCLOAK_JWK_SET_URI` and `KEYCLOAK_INTERNAL_ISSUER` exist.

## Releases

Each folder has its own CI job in `.github/workflows`, triggered only by changes under that folder. Merging to `main` publishes `mpesa-service` and `mpesa-ui` as separate Docker images.

The plan for the console and its admin API, and the screen designs, live outside the repo.

## What the console covers

The admin API under `/api/v1/admin/**` now backs four areas. Overview shows money in and out, paybill health, float and what needs attention. Paybills is where I onboard a paybill, till or B2C shortcode through a four-step wizard, rotate its encrypted credentials, register its C2B URLs, set its C2B validation rules and send a small test STK push. Transactions is one paged feed of STK, C2B and B2C payments with a timeline per payment, including the webhooks sent about it. Products grants each Keycloak client the paybills and operations it may use and manages its signed webhooks. Every change lands in the audit trail, and the settings for all of this are documented with their defaults in `mpesa-service/src/main/resources/application.yaml`.
