# Sarafrika M-Pesa

M-Pesa payments for Sarafrika products, and the console the team uses to run them.

| Folder | What it is |
|---|---|
| [`mpesa-service`](mpesa-service) | Spring Boot API. It is the only part of the platform that holds Daraja credentials or calls Safaricom. |
| [`mpesa-ui`](mpesa-ui) | Next.js console for managing paybills, payments, approvals and connected products. |
| [`docker`](docker) | Keycloak realm for local development. |

## Architecture

```
Elimika and other products ──────────────┐
                                         ├──▶  mpesa-service  ──▶  Safaricom Daraja
Staff ──▶ mpesa-ui (Next.js server) ─────┘           │
                                                     ▼
                                                 PostgreSQL
```

- **Products** call `mpesa-service` with their own Keycloak client credentials.
- **Staff** sign in to the console through Keycloak. The console's server holds their tokens and forwards their requests, so tokens never reach the browser.
- **Safaricom** sends results to the service's callback URLs. Each paybill has its own secret callback URL.

## Features

- **Collections:** STK push, C2B with validation rules, dynamic QR codes and B2B Express Checkout.
- **Payouts:** B2C (including bulk from CSV), B2B sweeps, Business to Pochi and Tax Remittance to KRA.
- **Approvals:** every outgoing payment and reversal needs a second person to approve it.
- **Reconciliation:** matches payments against M-Pesa statements or Pull Transactions.
- **Webhooks:** signed payment events for each product, with retries and replay.
- **Operations:** float balances, paybill health checks, a callback inbox and a full audit log.
- **Security:**
  - Credentials are encrypted at rest.
  - Access uses four roles: viewer, operator, finance and admin.
  - Phone numbers and names are masked unless you have the finance role.

## Getting started

You need Docker, Java 21, Node 24 and pnpm.

**1. Start Postgres and Keycloak**

```bash
docker compose up
```

Keycloak runs on `localhost:8081`, with a `console-admin` user (password `console-admin`).

**2. Start the service** on `localhost:8080`

```bash
cd mpesa-service
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/mpesa \
SPRING_DATASOURCE_USERNAME=mpesa \
SPRING_DATASOURCE_PASSWORD=mpesa \
KEYCLOAK_ISSUER_URI=http://localhost:8081/realms/sarafrika \
KEYCLOAK_AUDIENCE=mpesa-service \
CALLBACK_IP_WHITELIST_ENABLED=false \
MPESA_ENCRYPTION_KEY=ZGV2LW9ubHktbXBlc2EtZW5jcnlwdGlvbi1rZXktMzI= \
./gradlew bootRun
```

**3. Start the console** on `localhost:3000`

```bash
cd mpesa-ui
cp .env.example .env.local
pnpm install && pnpm dev
```

To run everything in containers instead, use `docker compose --profile apps up --build`.

## Integrating a product

1. Ask an admin to connect your Keycloak client in **Products** and grant it the paybills and operations it needs.
2. Call the payment API at `/api/v1/mpesa/**` with a client-credentials token.
3. Add a webhook URL to receive payment events. Verify the `X-Sarafrika-Signature` header on every delivery.

**Before going live, handle these two rules:**

- **Use the M-Pesa receipt to avoid counting a payment twice.** The same payment can arrive as both `c2b.confirmed` and `stk.completed`. Both events carry the receipt as `trans_id`.
- **Never resend a payout marked `outcome_unknown: true`.** Safaricom may still complete it. Wait for the `b2c.completed` or `b2c.failed` event.

## Configuration

Every service setting is listed with its default in [`application.yaml`](mpesa-service/src/main/resources/application.yaml). In production, set at least these:

| Variable | Purpose |
|---|---|
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | PostgreSQL connection |
| `KEYCLOAK_ISSUER_URI` | Keycloak realm that issues tokens |
| `MPESA_ENCRYPTION_KEY` | Key for encrypting credentials; generate one with `openssl rand -base64 32` |
| `CALLBACK_BASE_URL` | Public base URL that Safaricom sends callbacks to |

The console's settings are in [`mpesa-ui/.env.example`](mpesa-ui/.env.example).

## Development

The console's API client is generated from `mpesa-ui/openapi.json`. After changing the API, regenerate the file and commit it with your change:

```bash
cd mpesa-service && ./gradlew exportOpenApi
```

CI runs each app's tests on pull requests and fails if `openapi.json` is out of date. Merging to `main` publishes a Docker image for each app.
