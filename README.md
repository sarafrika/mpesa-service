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

Everything below sits under `/api/v1/admin/**` and needs one of four realm roles: `mpesa-viewer` reads, `mpesa-operator` makes changes, `mpesa-finance` decides on money and sees personal data in full, and `mpesa-admin` holds both and more. The service checks the role before it even reads a request body, so a caller without it gets 403, never a list of field errors. Every change lands in the audit trail.

**Overview** shows money in and out for a period, a chart of both, the health of each paybill, the float and a list of what needs attention: low float, paybills gone quiet or with failing callbacks, STK requests stuck pending, deposits that may duplicate an STK payment, unlinked deposits, open reconciliation exceptions and approvals waiting.

**Paybills** lists every paybill, till and B2C shortcode with its health, and exports the list as CSV. I onboard a new one through a four-step wizard that saves a draft, registers its C2B URLs and checks that Daraja issues a token; an admin then makes it live, and only then can I send it a KES 1 test STK push. Each paybill has tabs for its profile, C2B URLs and QR codes, validation rules, product access, credentials, float, transactions and Daraja features. Credentials are write-only and show only their last four characters. Daraja has no API for changing them, so the console only stores values someone already changed at Safaricom.

**Transactions** is one paged feed of STK, C2B, B2B Express Checkout, B2C and B2B payments, with a side panel that shows the timeline, the raw callback and the webhooks sent about each one. Finance can download the current filters as CSV; the download is audited and stops at `mpesa.exports.max-rows`. Reversals don't have rows of their own: they show on the payment they reverse and under Approvals.

**Products** connects each Keycloak client, such as Elimika, to the paybills and operations it may use, and manages its signed webhooks with their recent deliveries.

**Approvals** is maker-checker for every way money leaves: reversals, bulk B2C payouts from a CSV, B2B sweeps between our own shortcodes, Business to Pochi payouts and Tax Remittance to KRA. A finance user who did not raise a request has to approve it before anything reaches Safaricom.

**Float** is a paybill tab with its Account Balance results, a low-float line per paybill, the B2B destinations it may sweep to and the sweeps it sent.

**Callback inbox** keeps every request Safaricom sent to a callback URL, how it was handled, and a replay for the ones that failed. A result that matched nothing can be attached by hand to the payout or reversal it belongs to.

**Reconciliation** matches our ledger against an uploaded M-Pesa organisation statement, or against Pull Transactions for a paybill where Safaricom enabled it, on the M-Pesa receipt. Finance resolves each exception by backfilling the deposit, attaching its receipt to an STK payment, querying its status, merging a duplicate or marking it resolved, and a nightly run can do the Pull side on its own.

**Features** are per-paybill switches for the Daraja products Safaricom enables separately: Pull Transactions, Business to Pochi, Tax Remittance and B2B Express Checkout. All are off by default. Ratiba stays switched off because Safaricom's own pages disagree on its fields.

**Audit log** (admins) lists every change with who made it, when and from where, filtered by actor, action or entity, with a field-by-field before and after. Secrets never reach it.

## Callback URLs

Each paybill gets its own callback URLs: `<CALLBACK_BASE_URL>/<callback token>/...`, where the token is a random 64-character secret made when the paybill is saved. A callback to a token no paybill holds is refused with 403 and never stored. The token is a path segment, so it only works because Safaricom posts to exactly the URL it was given; screens show it as `••••` and its last four characters.

C2B registration, STK pushes and every B2C, B2B, reversal, status, balance, Pochi, Tax Remittance and B2B Express Checkout request now give Safaricom the token URL. The older shared paths (`<CALLBACK_BASE_URL>/c2b/confirmation` and the rest) still work, so URLs Safaricom already holds keep working until a paybill's C2B URLs are registered again. The IP allow-list applies to both kinds of path.

## Secrets at rest

Daraja credentials and webhook signing secrets are encrypted with AES-256-GCM using the key in `MPESA_ENCRYPTION_KEY`, directly: there is no key hierarchy or envelope encryption, so whoever holds that key can read every secret, and changing the key means re-encrypting. Rows saved before encryption existed still read as plaintext. Setting `MPESA_REENCRYPT_LEGACY_SECRETS=true` seals them once at startup; it skips values that are already sealed, so it is safe to leave on, and it logs and audits only counts.

## Webhooks for products

A product subscribes a URL to any of `c2b.confirmed`, `stk.completed`, `stk.failed`, `stk.cancelled`, `b2c.completed`, `b2c.failed`, `reversal.completed`, `reversal.failed` and `b2b_checkout.completed`, and only gets events for paybills and operations it holds a grant for. Each delivery is a JSON body with `id`, `type`, `created_at`, `shortcode_uuid`, `payment_uuid` and `data`. It carries `X-Sarafrika-Event`, `X-Sarafrika-Delivery` and `X-Sarafrika-Signature: t=<unix seconds>,v1=<hex HMAC-SHA256 of "<t>.<raw body>">` made with the subscription's secret, which the console shows once. A failed delivery is retried after 10 s, 1 min, 10 min and 1 h. `c2b.confirmed` carries `source`, so a product can tell a deposit backfilled from a statement (`STATEMENT`) or Pull Transactions (`PULL_TRANSACTIONS`) from a live one.

A word on B2C payouts, because money depends on it. When Daraja refuses a payout outright, `POST /api/v1/mpesa/b2c` answers it as `FAILED` and no webhook follows. When Daraja never answers (a timeout, a network error, a 5xx), the payout stays `PENDING` with `outcome_unknown: true`: Safaricom may still pay it, so a product must wait for the result and never resend it under a new `client_reference`. Each payout gets exactly one webhook, `b2c.completed` or `b2c.failed`, from Safaricom's result. The one exception to "refused means failed" is a success result that still arrives for a payout refused when it was sent: the payout then becomes `SUCCESS`, the change is audited, and `b2c.completed` is sent, so a product never sees `b2c.failed` followed by `b2c.completed`.

STK pushes to a paybill need the same care. When an STK callback is lost, the status job settles the payment from Safaricom's STK query and sends `stk.completed`, but that reply carries no receipt, so the payment keeps its checkout id. If Safaricom then also sends a C2B confirmation for that money, I don't store it as a second deposit: when exactly one receiptless STK payment on that paybill has the same amount, a phone that fits (the MSISDN may be masked or hashed, so only the digits shown are compared) and a request time within `mpesa.reconciliation.stk-match-window` of the confirmation, the confirmation's TransID becomes that payment's receipt and no `c2b.confirmed` is sent, because the product already heard about the money through `stk.completed`. When several STK payments fit, the confirmation is stored and announced as `c2b.confirmed` as before, but flagged on the Overview so someone checks Safaricom's record for which payment it is.

The other order happens too: the C2B confirmation arrives while the STK request is still pending, so it is stored and announced as `c2b.confirmed`, and then the lost callback is replaced by the STK query. When exactly one such deposit fits the payment the same way (and no other receiptless STK payment fits that deposit), I merge them into the STK payment, just as when an STK callback brings a receipt a C2B row already holds. `stk.completed` still goes out, because the product that started the push waits for it, but it carries the receipt and `c2b_receipt_announced: true`. When several deposits fit, the payment settles without a receipt as before and is flagged on the Overview. So products should treat the M-Pesa receipt as the idempotency key across `c2b.*` and `stk.*` events: `c2b.confirmed` carries it as `trans_id`, and every `stk.completed` carries it as both `receipt` and `trans_id` whenever I have it.

## Deployment settings

Every setting of `mpesa-service` is documented with its default in `mpesa-service/src/main/resources/application.yaml`. These are the ones a deployment has to think about:

| Variable | What it does |
|---|---|
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | The Postgres database; Flyway migrates it on start. |
| `KEYCLOAK_ISSUER_URI` | The realm whose tokens the service accepts. |
| `KEYCLOAK_AUDIENCE` | Tokens must name this in `aud` (`mpesa-service`); blank accepts any realm token. |
| `KEYCLOAK_JWK_SET_URI` | Where to fetch signing keys when the issuer URL isn't reachable from the service. |
| `MPESA_ENCRYPTION_KEY` | Base64 of 32 random bytes; the service won't start without it. |
| `MPESA_REENCRYPT_LEGACY_SECRETS` | `true` seals plaintext secrets left from before encryption, once at startup. |
| `MPESA_SANDBOX_CERTIFICATE`, `MPESA_PRODUCTION_CERTIFICATE` | Safaricom's public certificate (PEM text or a path), used to turn an initiator password into a SecurityCredential. |
| `CALLBACK_BASE_URL` | The public base of the callback URLs, ending in `/api/v1/callbacks`. |
| `CALLBACK_IP_WHITELIST_ENABLED` | Only Safaricom's listed IPs may post callbacks; on by default, off for local work. |
| `CALLBACK_TRUSTED_PROXIES` | Proxies whose `X-Forwarded-For` is believed when finding a callback's source IP. |
| `MPESA_GRANTS_ENFORCE` | Products may only use the paybills and operations granted to them; on by default. |
| `MPESA_EXPORT_MAX_ROWS` | Where a CSV export stops; 100,000 by default. |

`mpesa-ui` needs these:

| Variable | What it does |
|---|---|
| `NEXTAUTH_URL` | The console's public URL. |
| `NEXTAUTH_SECRET` | Encrypts the session cookie that holds the user's tokens. |
| `KEYCLOAK_ISSUER` | The realm users sign in to. |
| `KEYCLOAK_INTERNAL_ISSUER` | The same realm as the Next.js server reaches it, when that differs (Docker). |
| `KEYCLOAK_CLIENT_ID` | The console's public PKCE client, `mpesa-ui`. |
| `MPESA_SERVICE_URL` | Where the proxy forwards API calls. |

## What I could not confirm with Safaricom

A few things rest on Safaricom's sandbox documentation alone, so check them with Safaricom before relying on them in production:

- The production paths for Tax Remittance and B2B Express Checkout. Safaricom only documents the sandbox URLs, so I made the production paths settings (`MPESA_TAX_REMITTANCE_PRODUCTION_PATH`, `MPESA_B2B_EXPRESS_CHECKOUT_PRODUCTION_PATH`) that default to the sandbox ones.
- The format of the nominated number for Pull Transactions registration. The service sends it as `07…`, like Safaricom's sample.
- Whether an STK payment to a paybill always produces a C2B confirmation as well. The service copes with both, as described above.
- The time zone of C2B `TransTime`. It has no offset, and the service stores it as given.
