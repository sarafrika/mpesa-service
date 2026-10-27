# Sarafrika M-Pesa

This is how Sarafrika takes and sends money through M-Pesa. `mpesa-service` is the only thing that talks to Safaricom, and `mpesa-ui` is the console our team uses to look after our paybills and the products that use them, starting with Elimika.

```
mpesa-service/   Spring Boot API that talks to Safaricom Daraja
mpesa-ui/        Next.js console for the team
docker/          Keycloak realm for local work
```

## How it fits together

All the Daraja credentials live in `mpesa-service`, and nothing else calls Safaricom. Products like Elimika call the service with their own Keycloak client. Staff use the console, which signs them in through Keycloak and passes their requests on to the service, so their tokens never reach the browser.

The console's API client is generated from `mpesa-ui/openapi.json`. When you change the API, refresh that file with `./gradlew exportOpenApi` in `mpesa-service` and commit it with your change. CI fails if you forget.

## Running it locally

`docker compose up` starts Postgres and Keycloak on `localhost:8081`. You can sign in to the console as `console-admin` with the password `console-admin`.

Then start the two apps:

```
# mpesa-service on :8080
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/mpesa SPRING_DATASOURCE_USERNAME=mpesa \
SPRING_DATASOURCE_PASSWORD=mpesa KEYCLOAK_ISSUER_URI=http://localhost:8081/realms/sarafrika \
KEYCLOAK_AUDIENCE=mpesa-service CALLBACK_IP_WHITELIST_ENABLED=false \
MPESA_ENCRYPTION_KEY=ZGV2LW9ubHktbXBlc2EtZW5jcnlwdGlvbi1rZXktMzI= ./gradlew bootRun

# mpesa-ui on :3000, after copying .env.example to .env.local
pnpm install && pnpm dev
```

That encryption key is only for your machine. In production, make a new one with `openssl rand -base64 32`. If you'd rather run everything in Docker, use `docker compose --profile apps up --build`.

## What the console does

- **Overview:** money in and out, how each paybill is doing, and anything that needs someone to look at it.
- **Paybills:** add a paybill or till, register its C2B URLs, store its credentials and switch on extra Daraja products.
- **Transactions:** every payment in one list. Open one to see its history and Safaricom's raw reply.
- **Approvals:** no money leaves until someone other than the person who asked for it approves it. That covers reversals, bulk payouts, sweeps between our paybills, Pochi payouts and KRA tax payments.
- **Reconciliation:** checks our records against Safaricom's statement and fixes what doesn't match.
- **Products:** says which paybills each product may use and where we send its webhooks.
- **Callback inbox** and **Audit log:** everything Safaricom sent us, and every change anyone made.

There are four roles. Viewers can look, operators can make changes, finance approves money and sees full phone numbers and names, and admins can do everything.

## If you're building a product on it

You get webhooks, signed with a secret the console shows you once. Check the `X-Sarafrika-Signature` header before you trust one.

Use the M-Pesa receipt as your duplicate check. The same payment can reach you as both `c2b.confirmed` and `stk.completed`, and both carry the receipt as `trans_id`.

Never resend a payout that came back with `outcome_unknown: true`. Safaricom may still pay it, so wait for the `b2c.completed` or `b2c.failed` webhook.

## Settings

Every setting and its default is in `mpesa-service/src/main/resources/application.yaml`. In production, set at least the database, `KEYCLOAK_ISSUER_URI`, `MPESA_ENCRYPTION_KEY` and `CALLBACK_BASE_URL`. The console's settings are in `mpesa-ui/.env.example`.

## Still to check with Safaricom

Some things rest only on Safaricom's sandbox docs:

- the live URLs for Tax Remittance and B2B Express Checkout. I made both settings that default to the sandbox paths.
- whether Pull Transactions wants the phone number as `07…` or `2547…`. The service sends `07…`, like Safaricom's example.
- whether a phone payment prompt (STK push) to a paybill always triggers a C2B confirmation too. The service handles it either way.
- which time zone C2B times are in. The service stores them as they arrive.

I left out Ratiba, Safaricom's standing orders, because its docs contradict each other.
