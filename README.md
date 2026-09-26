# PrathibhaLanka

REST backend for a Sri Lanka travel agency: travel packages, booking requests with PIN tracking,
customer reviews, gallery, journal posts, contact queries, uploaded media and the editable copy of
the public About/Contact pages.

## Stack

- Java 21, Spring Boot 4.1.1 (Web MVC, Data JPA, Security, Validation, Mail, Actuator)
- PostgreSQL 15 (Docker); the schema is owned by Flyway (`src/main/resources/db/migration`) and
  Hibernate runs with `ddl-auto=validate`, so a migration that drifts from the entities fails the boot
- JWT (JJWT 0.12.6) bearer tokens, BCrypt password hashes
- Jackson 3 (`tools.jackson`) — Spring Boot 4 no longer uses Jackson 2 for HTTP bodies
- Packaged as a container image (`Dockerfile`) for Railway; see [Deployment](#deployment)

## Run

```bash
# 1. database
docker compose up -d

# 2. application (http://localhost:8080)
./mvnw spring-boot:run
```

## Configuration

Every setting is an environment variable, and the defaults are what local development uses (they
match `docker-compose.yml`). `application.properties` carries the reasoning next to each one.

**Database**

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/travel_agency` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `travel_admin` / `secret` | DB credentials |
| `HIKARI_MAX_POOL_SIZE` / `HIKARI_MIN_IDLE` | `10` / `2` | Connection pool size |
| `FLYWAY_ENABLED` | `true` | Whether migrations run at startup |

**HTTP**

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | Listen port — the platform injects this |
| `FORWARD_HEADERS_STRATEGY` | `framework` | Trust the platform proxy's `X-Forwarded-*` headers |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | Origins allowed to call this API, comma separated. Empty means the app refuses to start |

**Secrets and first start**

| Variable | Default | Purpose |
|---|---|---|
| `JWT_SECRET` | dev placeholder | Token signing key, ≥32 bytes. **Has no default under the `prod` profile** |
| `JWT_EXPIRATION_MS` | `86400000` (24 h) | Token lifetime |
| `JWT_ISSUER` | `PrathibhaLanka` | Token issuer claim. Changing it invalidates tokens already issued |
| `BOOTSTRAP_ADMIN_ENABLED` | `true` | Create/repair the first admin on startup |
| `BOOTSTRAP_ADMIN_EMAIL` | `prathibhalankavoyages@gmail.com` | Bootstrap admin login |
| `BOOTSTRAP_ADMIN_PASSWORD` | *(none)* | **Must be set** for an admin to be created; there is no default so no password is ever committed |
| `BOOTSTRAP_ADMIN_NAME` | `Prathibha Lanka Voyages` | Display name of that account |
| `BOOTSTRAP_ADMIN_RESET_PASSWORD` | `false` | One start that resets an existing admin's password to `BOOTSTRAP_ADMIN_PASSWORD` |
| `BOOTSTRAP_CONTENT_ENABLED` | `true` | Seed the About/Contact pages when their rows are missing |

The bootstrap admin is created only if that email does not exist, and its password is reset only
when the stored hash is not a valid BCrypt hash. A valid password is never overwritten. Disable it
in production and change the password after the first login.

**Mail**

| Variable | Default | Purpose |
|---|---|---|
| `MAIL_TRANSPORT` | `smtp` | `smtp`, `bird` (the HTTPS API production uses) or `none` — see [Email](#email) |
| `MAIL_FROM` / `MAIL_FROM_NAME` | `prathibhalankavoyages@gmail.com` / `PrathibhaLanka` | Sender the recipient sees. Over the Bird API it must be on the verified sending domain |
| `MAIL_REPLY_TO` | `prathibhalankavoyages@gmail.com` | Where replies go. The sending subdomain has no MX, so without this a reply bounces |
| `BIRD_API_KEY` | *(empty)* | Bird API key, needed when `MAIL_TRANSPORT=bird` |
| `BIRD_API_URL` | `https://eu1.platform.bird.com/v1/email/messages` | Bird's email endpoint. The host follows the key's region prefix — see [Email](#email) |
| `BIRD_SENDING_DOMAIN` | `mail.prathibalanka.com` | Only used to warn at startup when `MAIL_FROM` is not on it |
| `MAIL_HOST` / `MAIL_PORT` | `smtp.gmail.com` / `587` | SMTP server (`MAIL_TRANSPORT=smtp`) |
| `MAIL_USERNAME` | `prathibhalankavoyages@gmail.com` | SMTP account |
| `MAIL_PASSWORD` | *(empty)* | **Must be set**: a Google App Password, not the account password |
| `MAIL_SMTP_AUTH` / `MAIL_SMTP_STARTTLS` | `true` / `true` | SMTP authentication and TLS (STARTTLS is for port 587) |

**Links in outgoing mail**

| Variable | Default | Purpose |
|---|---|---|
| `APP_PUBLIC_URL` | `http://localhost:5173` | The public site, used to build the links that go in an email: the customer's own enquiry page and the console address in the note that says somebody wrote in. Set it to the deployed front end, or the mails arrive with links only a developer can open |

**Uploaded media**

| Variable | Default | Purpose |
|---|---|---|
| `MEDIA_DIR` | `uploads` | Directory the uploaded files are written to. **Has no default under the `prod` profile** — point it at a mounted volume |
| `MEDIA_URL_PREFIX` | `/media` | Public path the files are served from |
| `MEDIA_MAX_IMAGE_BYTES` | `10485760` (10 MB) | Image upload limit |
| `MEDIA_MAX_VIDEO_BYTES` | `62914560` (60 MB) | Video upload limit |
| `MEDIA_MAX_UPLOAD` / `MEDIA_MAX_REQUEST` | `64MB` / `70MB` | Multipart ceiling (hard limit above the per-type ones) |

**Rate limiting** — a token bucket per client and per endpoint, on the endpoints a stranger can write
to. Keep `numReplicas` at 1 while it is on: each instance counts in its own memory.

| Variable | Default | Purpose |
|---|---|---|
| `RATE_LIMIT_ENABLED` | `true` | Master switch |
| `RATE_LIMIT_PER_MINUTE` / `RATE_LIMIT_BURST` | `20` / `5` | Sustained rate and how much may arrive at once |
| `RATE_LIMIT_PATHS` | `/api/contact,POST /api/enquiries,/api/bookings/request,/api/auth/login,/api/auth/register` | Endpoints covered. An entry is a path prefix, or `METHOD /prefix` to limit one method only — the customer's enquiry page is a read *and* a write, and refreshing your own enquiry should not answer "too many requests" |

**Health, logging**

| Variable | Default | Purpose |
|---|---|---|
| `ACTUATOR_ENDPOINTS` | `health,info` | What is exposed under `/actuator` |
| `ACTUATOR_HEALTH_DETAILS` | `never` | Keep datasource and disk details out of the response |
| `HEALTH_MAIL_ENABLED` | `false` | Mail is not a health criterion (see the mail notes below) |
| `HEALTH_CACHE` | `2s` | How long a health result is reused |
| `LOG_LEVEL` | `INFO` under `prod` | Root log level |
| `SQL_LOG_LEVEL` | `WARN` | Hibernate SQL logging; raise to `DEBUG` only locally |

## Email

Three ways out, chosen by `MAIL_TRANSPORT`:

| Value | What it does | Used by |
|---|---|---|
| `smtp` *(default)* | JavaMail to a mail server | Local development, CI (pointed at `scripts/fake-smtp.ps1`) |
| `bird` | Bird's email API over HTTPS | Production — see below |
| `none` | Sends nothing; every attempt is still written to `email_log` | Anywhere mail must not leave |

**Why production cannot use SMTP.** Railway blocks outbound SMTP on its Free, Trial and Hobby plans
([their words](https://station.railway.com/questions/is-outbound-smtp-ports-465-587-blocked-8a0c4874):
"use a dedicated email service that provides HTTPS APIs"), which is exactly why the Gmail settings
never worked there. Bird — the platform SparkPost became — sends over HTTPS, which is not blocked.
`EmailService` does not know the difference: it hands a message to whichever `MailTransport` the
configuration selected, and both write the same `email_log` row.

### Bird (production)

The sending domain is **`mail.prathibalanka.com`**, a subdomain of the agency's own domain. Three
records make it authenticated, and all three are published and verified:

| Type | Name | Value | Why |
|---|---|---|---|
| TXT | `bird-844-0926._domainkey.mail` | `v=DKIM1; k=rsa; p=MIIBIjANBg…` | Signs every message as this domain, which is what DMARC aligns on |
| CNAME | `send.mail` | `eu1.bounce.bird.com` | The envelope sender (Return-Path). That host publishes `v=spf1 exists:%{i}._spf.sparkpostmail.com ~all`, so the bounce address passes SPF |
| TXT | `_dmarc.mail` | `v=DMARC1; p=none; rua=mailto:dmarc-agg@dmarc.bird.com;` | Policy plus Bird's aggregate reports |

Four further records are deliberately **not** published: the three inbound `MX` records and
`links.mail`. Click tracking does not need it (Bird sends with `track_clicks` and `track_opens` off,
and turning them on would rewrite links to `links.mail`, which does not resolve until that record
exists). The MX records would make `@mail.prathibalanka.com` a mailbox; until then everything sends
from the subdomain and nothing receives on it, which is why **`MAIL_REPLY_TO` points at the agency's
Gmail inbox** — the one address that is certainly read. Publishing the MX records later is what makes
`bookings@mail.prathibalanka.com` possible; note that inbound mail then lands in **Bird**, so its
routing (agent mailbox or forwarding) has to be configured there as well.

### The API it calls

A Bird key's **region prefix picks the host**: `bk_us1_` keys call `us1.platform.bird.com`, `bk_eu1_`
keys call `eu1.platform.bird.com`, and the email endpoint there is `POST /v1/email/messages`. The
older SparkPost-compatible endpoint (`api.eu.sparkpost.com/api/v1/transmissions`) answers such a key
with `401 Unauthorized`, which reads as a bad key rather than the wrong host — an afternoon's worth of
confusion, recorded here so nobody repeats it.

```
POST https://eu1.platform.bird.com/v1/email/messages
Authorization: Bearer <key>

{ "from":     { "email": "bookings@mail.prathibalanka.com", "name": "Prathibha Lanka Voyages" },
  "to":       [ "traveller@example.com" ],
  "reply_to": [ "prathibhalankavoyages@gmail.com" ],
  "category": "transactional",
  "subject":  "Your Trip Booking is Pending – PIN: ABC12345",
  "text":     "Dear …" }
```

Two details came from the API refusing the alternatives: `reply_to` must be an **array** (a string is
`422 … "got string, want array"`), and without `category` the message is filed as **marketing** —
wrong for a booking acknowledgement.

| Variable | Value | Notes |
|---|---|---|
| `MAIL_TRANSPORT` | `bird` | Selects the API transport |
| `BIRD_API_KEY` | *(secret)* | Bird dashboard → Developers → API keys, with send permission. Sent as `Authorization: Bearer <key>` |
| `BIRD_API_URL` | `https://eu1.platform.bird.com/v1/email/messages` | Change the host if the key's prefix is not `eu1` |
| `MAIL_FROM` | `bookings@mail.prathibalanka.com` | **Must be on the verified subdomain.** Bird refuses anything else, and the app warns at startup when it does not match `BIRD_SENDING_DOMAIN` |
| `MAIL_REPLY_TO` | `prathibhalankavoyages@gmail.com` | Where replies go — the agency's own inbox, which is the one that answers. `bookings@mail.prathibalanka.com` becomes possible once Bird's inbound MX records are published (see below) |
| `BIRD_SENDING_DOMAIN` | `mail.prathibalanka.com` | Only used for that startup warning |

`EmailService` sets the sender explicitly (`Name <address>`, both header and envelope), because
JavaMail otherwise invents one like `user@host` and providers reject it. It also logs one line at
startup saying which transport it is using and as whom, so a deployment is not a guess:

```
Mail goes out over Bird API (https://eu1.platform.bird.com/v1/email/messages) as Prathibha Lanka Voyages <bookings@mail.prathibalanka.com>
Replies are directed to prathibhalankavoyages@gmail.com
```

Mail is sent after the transaction commits, on a separate thread, so a failed send never fails the
request: the attempt is stored in `email_log` with `sent` and `failure_reason` — including the
provider's own words, so a rejected key reads `401 … {"error":{"code":"Unauthorized"}}` rather than
"something went wrong". There is no retry; a failure is logged and dropped, so watch that table when
you change the mail settings.

**Proven end to end.** An enquiry posted through a running instance with `MAIL_TRANSPORT=bird` was
sent from `bookings@mail.prathibalanka.com` and Bird's own record of it reads `status: delivered`,
`delivered: 1`, `bounced: 0`, `rejected: 0`, with events `email.accepted` → `email.processed
(gmail)` → `email.delivered`. A `202` from the API only means "queued", which is why the delivery
event is the thing to look at:

```bash
curl -s -H "Authorization: Bearer $BIRD_API_KEY" \
  "https://eu1.platform.bird.com/v1/email/messages?limit=5" | jq '.results[] | {id, status}'
```

`scripts/api-tests.ps1` posts an enquiry and asserts `autoResponseSent` flips to true, which is the
whole path: request → queue → transport → `email_log`. `scripts/verify-mail.ps1` goes further and reads
what the mail server was actually handed: the acknowledgement, the console's reply with its reference in
the subject, the link inside it opening that customer's own enquiry, and the note telling the agency the
customer wrote again.

Six kinds of mail are sent, and each is stored in `email_log` as it goes:

| `EMAIL_TYPE` | Goes to | Sent when |
|---|---|---|
| `AUTO_RESPONSE` | the customer | an enquiry arrives |
| `PENDING_NOTIFICATION` | the customer | a journey is requested (carries the PIN) |
| `CONFIRMATION` | the customer | an admin confirms the request |
| `CANCELLATION` | the customer | an admin cancels the request |
| `QUERY_RESPONSE` | the customer | an admin answers an enquiry from the console |
| `QUERY_MESSAGE` | the agency inbox | a customer writes again on their enquiry |

`email_log.email_type` is guarded by a check constraint, so a new type needs a migration: the message is
sent *before* the row is written, and a refused insert means the mail went out and the audit trail
silently lost it. `V3` (cancellation) and `V4` (the two enquiry types) both exist for that reason.

### SMTP (`MAIL_TRANSPORT=smtp`)

The development default, and the agency's Gmail account. Two things have to be right:

1. **`MAIL_PASSWORD` must be a Google App Password**, not the account password — Gmail rejects plain
   SMTP logins. Create one at `myaccount.google.com/apppasswords` (2-step verification first); it is
   16 characters and shown once. `MailConfig` compacts it if it is pasted with its display spaces.
2. **`MAIL_FROM` must be that same account**, or a verified alias on it.

`spring.mail.default-encoding=UTF-8` matters here: without it JavaMail uses the platform default and
the en dash in a subject line arrives as `â€“`.

## Authentication

`POST /api/auth/login` returns `{ token, email, role, userId }`. Send it as
`Authorization: Bearer <token>`. Emails are stored lower-case and compared case-insensitively.

- `ROLE_CUSTOMER` — created by `POST /api/auth/register`; may book, review and read own bookings.
- `ROLE_ADMIN` — created by the bootstrap runner; required for every `/api/admin/**` route.

Booking and review submission ignore client-supplied identity: the acting customer always comes
from the token, and a `customerId` in the body that points at another account is rejected with 403.

## Endpoints

Public

| Method | Path |
|---|---|
| POST | `/api/auth/register`, `/api/auth/login` |
| GET | `/api/packages`, `/api/packages/{id}`, `/api/packages/search?destination=` |
| GET | `/api/reviews`, `/api/reviews/package/{packageId}` |
| GET | `/api/gallery`, `/api/gallery/{id}`, `/api/gallery/package/{packageId}` |
| GET | `/api/journal/published`, `/api/journal/published/{id}` |
| GET | `/api/bookings/track?pin=` |
| GET | `/api/content/{section}` (`about`, `contact`) |
| GET | `/media/{file}` |
| POST | `/api/contact` |
| POST | `/api/bookings/request` — **no account needed**; see [Requesting a journey](#requesting-a-journey) |
| GET | `/api/bookings/track?pin=` |
| GET | `/api/enquiries/{token}` — the customer's own enquiry; the token is the credential |
| POST | `/api/enquiries/{token}/messages` — they write again; rate limited |

Customer (bearer token, `ROLE_CUSTOMER`)

| Method | Path |
|---|---|
| GET | `/api/customer/bookings` |
| POST | `/api/reviews` |

Admin (bearer token, `ROLE_ADMIN`)

| Method | Path |
|---|---|
| GET | `/api/admin/packages`, `/api/admin/bookings?status=`, `/api/admin/queries?onlyNew=`, `/api/admin/journal`, `/api/admin/journal/{id}` |
| GET | `/api/admin/media?type=`, `/api/admin/media/limits`, `/api/admin/content` |
| POST | `/api/admin/packages`, `/api/admin/gallery`, `/api/admin/journal`, `/api/admin/media` (multipart) |
| POST | `/api/admin/queries/{id}/answered-outside` — recorded as answered, nothing emailed |
| PUT | `/api/admin/packages/{id}`, `/api/admin/gallery/{id}`, `/api/admin/journal/{id}`, `/api/admin/content/{section}` |
| PATCH | `/api/admin/packages/{id}/deactivate`, `/api/admin/bookings/{id}/confirm`, `/api/admin/bookings/{id}/reject`, `/api/admin/queries/{id}/respond`, `/api/admin/journal/{id}/publish`, `/api/admin/journal/{id}/unpublish` |
| DELETE | `/api/admin/packages/{id}`, `/api/admin/gallery/{id}`, `/api/admin/journal/{id}`, `/api/admin/reviews/{id}`, `/api/admin/media/{id}` |

Booking status flow: `PENDING` → `CONFIRMED` or `REJECTED`; only pending bookings can be rejected.

Enquiry status flow: `NEW` (needs a reply) → `RESPONDED` (answered), and back to `NEW` when the customer
writes again. `PATCH /api/admin/queries/{id}/respond` sends the reply to the customer by email.

`POST /api/admin/journal` and `PUT /api/admin/journal/{id}` accept an optional `publishedAt`. Left out,
the API stamps the moment the story was published, which is right for the console; sent, it is honoured -
which is what lets a second instance be seeded without dating the whole archive the day of the import,
and lets an editor backdate a story written before the site existed. A draft is never given a date.

## Enquiries, and the conversation that follows them

Two kinds of enquiry arrive, and they are answered differently:

| | General enquiry | About a package |
|---|---|---|
| Comes from | the contact form (also on `/plan`) | the Request button on a journey card |
| Stored as | `contact_query` | `booking_request`, with a PIN and a status |
| Status | `NEW` → `RESPONDED` | `PENDING` → `CONFIRMED` / `REJECTED` |
| Automated mail | acknowledgement with the reference | acknowledgement, confirmation, cancellation |
| Customer's own view | `/enquiry/{access_token}` — opened from the link in the acknowledgement | the PIN tracker |

The model this implements is deliberately half-automated, because that is how the agency works: **the
state changes are automated and the discussion is not.** Confirmations, cancellations and
acknowledgements are templates this application sends; everything else is a person writing from the
agency's own inbox, which is where `MAIL_REPLY_TO` points and why every message carries it as its
`Reply-To`. What the application does is carry the messages that matter, and keep the record:

- **`query_message`** is the thread — one row per message, `CUSTOMER` or `AGENCY`. An `AGENCY` message
  with `emailed = true` is one this application sent; `emailed = false` means somebody wrote from Gmail
  and (if they kept a copy) recorded it here. Both are normal, and the console shows which is which.
- **`contact_query.reply_sent`** records whether the reply the console composed actually reached the
  customer, written by the mail worker from what the transport said — never from what the request hoped.
  `answered_outside` is the third outcome: the agency answered from its own inbox, which is recorded
  rather than treated as a failure.
- **`contact_query.access_token`** is 128 random bits, not the query id: `/api/enquiries/{token}` is
  public, and a sequential id would let anybody read anybody's enquiry by counting upwards. The id stays
  the human-readable reference (`#44`), which is what goes in the subject lines of every message so a
  Gmail thread can be matched to a row in the console.
- When a customer writes again the enquiry returns to `NEW` and the agency inbox is told, with the
  **customer** as that notification's reply-to — so whoever reads it in Gmail can hit reply and reach
  the person who wrote in, without opening the console first.

Links in outgoing mail are built from `APP_PUBLIC_URL` (see [Configuration](#configuration)); that is
why a deployment must set it.


## Requesting a journey

The "Request" button on a journey card opens a form with the journey already chosen
(`/plan?package=12`). Sending it creates a **booking request**, not a checkout: a row in
`booking_request` with status `PENDING`, a PIN, and an email to the traveller saying the request is
pending and a consultant will be in touch.

**No account is needed**, because the person filling it in is somebody browsing the site - so the
row carries the contact details itself (`contact_name`, `contact_email`, migration V2) and
`customer_id` is null. Nothing is invented on their behalf: creating a customer row would take their
email address, and `customer.email` is unique, so they could not register with it afterwards. A
request sent from a signed-in account is linked to that account instead and takes its name and email
from it, and a guest who later registers is matched by email so their request is waiting for them.

The request is protected the way the other public writes are: rate limited (see
`app.rate-limit.paths`), and **staff are refused** — `@PreAuthorize("!hasRole('ADMIN')")` on the
handler, because an administrator is not a customer and a booking they made would sit in the queue
they themselves work.

Every state change emails the traveller, and each attempt is recorded in `email_log`:

| Change | Email | `email_type` |
|---|---|---|
| Request sent | "Your request is pending – {journey} – PIN: …" | `PENDING_NOTIFICATION` |
| Admin confirms | "Your journey is confirmed – {journey} – PIN: …" with the agreed date and price | `CONFIRMATION` |
| Admin cancels | "We could not confirm your request – {journey} – PIN: …" | `CANCELLATION` |

The two admin actions are `PATCH /api/admin/bookings/{id}/confirm` (with the agreed price and date)
and `PATCH .../reject`. The console labels that second one **Cancel** and the status **Cancelled**,
which is the word the agency uses; the API and the database keep `REJECTED`, and the console maps
the label (`STATUS_LABELS`) rather than the value. Cancelling asks for confirmation first: it closes
somebody's request and emails them, and it sits next to Confirm.

Everything the traveller needs is on the console's booking table and the dashboard's *Latest booking
requests*: the PIN, their name, **their email**, the journey, the dates and the status.

## Media (images and short videos)

`POST /api/admin/media` takes `multipart/form-data` with a `file` part and an optional `title`. The
stored file is written to `app.media.dir` under a generated `<uuid>.<ext>` name and served publicly
from `/media/<name>` with a 30-day cache header (the name never changes, so it is safe to cache).

- Accepted types: `image/jpeg`, `image/png`, `image/webp`, `image/gif`, `image/avif`,
  `video/mp4`, `video/webm`, `video/quicktime`. Anything else is a `400`.
- The extension is chosen by the server from that table, never from the client's filename, and the
  first bytes of the file must match the declared format — so a renamed file is refused rather than
  served as an image.
- Images are capped at 10 MB and videos at 60 MB (configurable); exceeding it is a `413`.
- Deleting a file that a gallery item or journal cover still points at is refused with `409`, so a
  page cannot be left pointing at a missing file. Clear the field that uses it first.
- Nothing transcodes or resizes: the bytes are stored as uploaded. Keep clips short.

`GalleryImage.mediaType` is `IMAGE` or `VIDEO` (rows written before videos existed read as `IMAGE`),
so the public gallery can render a clip with controls.

Three records point at an uploaded file, all of them set from the admin console:

| Record | Field | Shown as |
|---|---|---|
| `travel_package` | `image_url` | the journey card and the header of the journey page |
| `journal_post` | `cover_image_url` | the journal card, the featured story and the story cover |
| `gallery_image` | `image_url` + `media_type` | the public gallery (and the home strip) |

Each accepts a stored path (`/media/<name>`) or any hosted URL, and each falls back to a drawn scene
on the front end when it is empty. Package covers are optional, so `image_url = ""` clears one.

## Editable page content

The About and Contact pages are stored in `page_content` as one JSON document per section, so the
copy changes without a deployment.

- `GET /api/content/{section}` — public; `404` when the section has no row.
- `PUT /api/admin/content/{section}` — replaces the payload. The section is validated first:
  required blocks (`hero`, `story`, `values`, `timeline` for About; `hero`, `aside` and `cards` for
  Contact), required lists with their maximum lengths, text-only leaves and per-field length budgets.
  A rejected save returns `400` listing every problem, and the stored copy is left untouched.
- On startup `ContentBootstrapConfig` seeds any missing section from `src/main/resources/content/`.
  An edited page is never overwritten by a restart.

## Errors

```json
{ "timestamp": "...", "status": 400, "error": "Validation Failed",
  "message": "Request validation failed.", "details": ["email: must be a well-formed email address"],
  "path": "/api/auth/register" }
```

`400` validation/malformed input · `401` missing or invalid token, bad credentials ·
`403` wrong role or acting on another account · `404` unknown id/route ·
`405` wrong method · `409` duplicate or referenced record · `413` upload too large ·
`500` unexpected (details stay in the log).

## Deployment

The image is self-contained and starts with `SPRING_PROFILES_ACTIVE=prod`, which is what removes the
default JWT secret and the default media directory. Build and run it locally:

```bash
docker build -t prathibalanka-api .
docker run -p 8080:8080 \
  -e JWT_SECRET="$(openssl rand -base64 48)" \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/travel_agency \
  -e DB_USERNAME=travel_admin -e DB_PASSWORD=secret \
  -v prathiba-media:/data \
  prathibalanka-api
```

### Railway

Two services: this repository (the API) and the front-end repository (nginx, which also proxies
`/api` and `/media` here). `railway.json` sets the builder, the health check and the restart policy,
so the dashboard needs no build settings.

1. **Postgres** — *New → Database → PostgreSQL*. Nothing to configure.
2. **API service** — *New → GitHub repo → this repository*. Railway builds the `Dockerfile`.
   Reference the database rather than copying its values, so a password change follows:
   `DB_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}`,
   `DB_USERNAME=${{Postgres.PGUSER}}`, `DB_PASSWORD=${{Postgres.PGPASSWORD}}`.
3. **Variables** — the ones without a safe default:

   | Variable | Value |
   |---|---|
   | `JWT_SECRET` | at least 32 random bytes (`openssl rand -base64 48`). **The app refuses to boot without it** |
   | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | as above |
   | `MEDIA_DIR` | `/data/uploads` — matches the volume below |
   | `MAIL_TRANSPORT` | `bird` — SMTP is blocked on this plan, so the API is the only transport that works |
   | `BIRD_API_KEY` | the key from Bird's dashboard. **Set this, or every send is recorded in `email_log` as a failure** |
   | `MAIL_FROM` | `bookings@mail.prathibalanka.com` |
   | `MAIL_REPLY_TO` | `prathibhalankavoyages@gmail.com` — replies go to the inbox the agency reads. Moving this to `bookings@mail.prathibalanka.com` is a one-variable change, but only worth making once the inbound MX records are published and Bird is routing that mail somewhere |
   | `APP_PUBLIC_URL` | `https://prathibalanka.com` — the front end's address, used to build the links inside outgoing mail (the customer's own enquiry page, and the console link in the "somebody wrote in" note). Left at the default, those links point at a laptop |
   | `CORS_ALLOWED_ORIGINS` | only needed if the browser calls this API directly; with the front end proxying, leave it out |
   | `BOOTSTRAP_ADMIN_PASSWORD` | the first admin's password, for the first deploy only |
   | `BOOTSTRAP_ADMIN_EMAIL` | `prathibhalankavoyages@gmail.com` (this is the default) |

4. **Volume** — *Service → Variables/Volumes → New Volume*, mounted at **`/data`**. Without it every
   uploaded photograph is deleted on the next deploy while the database still points at it.
5. **First deploy** — the app runs `db/migration/V1__baseline.sql` on the empty database (about ten
   seconds), creates the admin from `BOOTSTRAP_ADMIN_PASSWORD`, then reports `{"status":"UP"}` on
   `/actuator/health`, which is what Railway's health check waits for. Set
   `BOOTSTRAP_ADMIN_ENABLED=false` afterwards so a redeploy cannot recreate the account.

Notes that come from running it this way:

- **Health.** `/actuator/health` is public and answers in milliseconds; the mail health indicator is
  switched off, because it opens an SMTP connection on every poll and would otherwise make a health
  check wait five seconds for Gmail.
- **Rate limits are per instance.** `numReplicas: 1` is deliberate: two instances would each allow a
  full bucket, and media on a volume cannot be mounted into more than one anyway.
- **The site works without mail.** Miss a mail variable and nothing else breaks: enquiries and
  bookings are stored, and the failed send is recorded in `email_log` with `sent=false` and the
  provider's own reason — `401 Unauthorized` for a wrong Bird key, `Authentication failed` for a
  rejected SMTP login. Watch that table, because a failed send is otherwise invisible.
- **Migrations.** The schema ships as `V1__baseline.sql`, which is the schema as it stood when Flyway
  was introduced, so an existing database is baselined at 1 and a new one runs it. Add changes as
  `V2__…` and never edit V1: a checksum mismatch stops the next deploy.
- **Backups are not automatic.** Railway's Postgres does not take them by itself, and a booking
  record is the one thing here that cannot be recreated from the repository. `DATABASE_URL` is
  provided by the Postgres service, so a dump is one command — run it on a schedule, and keep the
  file somewhere other than this project:

  ```bash
  pg_dump "$DATABASE_URL" --format=custom --file="prathibhalanka-$(date +%F).dump"
  pg_restore --clean --if-exists --dbname="$DATABASE_URL" prathibhalanka-2026-09-20.dump
  ```

  The custom format is a single compressed file that `pg_restore` can list and read back
  selectively; both commands were run against a PostgreSQL 15 database before being written down
  here.
- **Scaling up** means moving uploads to an object store and the rate-limit counters to something
  shared, and putting the mail worker in one instance only.

### Going live, in order

Written as the order that actually matters, because two of these are silent when they are wrong: the
site comes up and looks right, and only a customer finds out.

1. **Push the branch the service watches.** GitHub has to have the code before Railway can build it,
   and a service watching `main` will not see work that only exists on `updates`. Check *Settings →
   Source* on each service.
2. **Set the variables**, at least: `JWT_SECRET`, `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`,
   `MEDIA_DIR=/data/uploads`, `MAIL_TRANSPORT=bird`, `BIRD_API_KEY`,
   `MAIL_FROM=bookings@mail.prathibalanka.com`, `MAIL_FROM_NAME`, `MAIL_REPLY_TO`, and
   **`APP_PUBLIC_URL`** — the last one is what goes into the link in every acknowledgement and reply
   email, and left at its default those emails point at `http://localhost:5173`, which the customer
   cannot open. The app refuses to boot without `JWT_SECRET` and `MEDIA_DIR`, so those two are loud.
3. **Add the volume** at `/data` before the first upload. Without it the database points at
   photographs that a deploy will delete.
4. **Point the domain** at the front end service (the API needs no public domain - nginx proxies
   `/api` and `/media` to it). On the front end, set `BACKEND_URL` to the API service
   (`http://<service>.railway.internal:8080` inside the project, so that traffic is private and not
   billed as egress) and build with `SITE_URL` set to the public address, which fills in the
   `og:url`/`og:image` tags that link previews use.
5. **Seed the content** from the instance that already has it - see the front end's
   `scripts/seed-production.mjs`. A fresh database has the schema and an administrator and nothing
   else: no journeys, no stories, no photographs.
6. **Smoke test the deployment**, which is the same suite this repository uses everywhere:

   ```bash
   powershell -ExecutionPolicy Bypass -File scripts/api-tests.ps1 -BaseUrl https://<front-end-host>
   ```

   It exercises every endpoint over HTTP. It is written for a development database and leaves records
   behind, so run it once and tidy up, or accept a few test rows.
7. **Turn the bootstrap admin off** (`BOOTSTRAP_ADMIN_ENABLED=false`) once the account exists, and
   change its password from the console if it was ever in a chat window.
8. **Schedule the dump** from the section above, and put the file somewhere that is not this project.

Two things that only go wrong in production, so they are worth checking once by hand: send one real
enquiry through the deployed contact form and confirm the acknowledgement arrives (that is the whole
mail path: HTTPS to Bird, DKIM, and the link back to the customer's own page), and open that link from
a phone, where the customer will.

## Tests

```bash
# backend must be running; pass -BaseUrl if it is not on 18080
powershell -ExecutionPolicy Bypass -File scripts/api-tests.ps1 -BaseUrl http://localhost:8080

# optional: local SMTP sink, so mail paths can be tested without a provider
powershell -ExecutionPolicy Bypass -File scripts/fake-smtp.ps1 -Port 2525
# then start the app with --spring.mail.host=127.0.0.1 --spring.mail.port=2525 \
#   --spring.mail.properties.mail.smtp.auth=false

# the same sink can record what the app sent, headers included, for inspecting the sender:
powershell -ExecutionPolicy Bypass -File scripts/fake-smtp.ps1 -Port 2525 -Dump smtp-dump.txt

# then prove the mail really goes out: acknowledgement, the console's reply, the link inside it, and the
# note back to the agency when the customer writes again (16 checks; needs BOOTSTRAP_ADMIN_PASSWORD)
powershell -ExecutionPolicy Bypass -File scripts/verify-mail.ps1 -BaseUrl http://localhost:8080
```

`scripts/api-tests.ps1` covers every endpoint plus validation, authorization and ownership cases,
and deletes every record it can. It cannot delete all of them: no route removes a booking, a customer
or a contact query, so the packages those bookings point at stay as well — the run prints exactly
what it left behind, and it belongs against a development database. Upload fixtures (a 1×1 PNG, a
WebM header, a mislabeled file and an 11 MB image) are written to the temp directory at run time, so
the suite proves the upload path, the signature check, the per-type size limit and the byte round trip
without committing binaries. `mvn test` runs the context-load test and the mail-transport tests.

`scripts/verify-mail.ps1` is the one suite that reads the wire rather than the API, which is the only way
to catch the class of bug this project has actually shipped: a reply that was stored, reported as saved,
and never sent. It needs a running instance pointed at `scripts/fake-smtp.ps1 -Dump`.

Six suites cover the project between them — 309 checks in total:

| Suite | Needs | Checks |
|---|---|---|
| `scripts/api-tests.ps1` (this repo) | a running API | 185 — every endpoint over HTTP |
| `scripts/verify-mail.ps1` (this repo) | a running API + the SMTP sink | 16 — what the mail server was handed |
| `mvn test` (this repo) | nothing | 23 — the Spring context, the mail transports |
| `npm run check:render` (front end) | nothing | 25 routes rendered in Node |
| `npm run test:e2e` (front end) | nothing (API mocked) | 43 browser tests |
| `npm run test:roles` (front end) | a running API + `BOOTSTRAP_ADMIN_PASSWORD` | 22 journeys through the real UI and database, one per role |

The last one writes to whichever database the API points at, so run it against a development
database. It marks everything it creates and deletes it again at the end of the run.

## Continuous integration

`.github/workflows/ci.yml` runs on every push to `main`/`updates` and on pull requests into `main`:

| Job | What it does |
|---|---|
| `Compile & test` | `mvn -B -ntp clean verify` against a `postgres:15` service container; uploads surefire reports + the application jar |
| `API endpoint tests` | boots the built jar against the same Postgres, points mail at `scripts/fake-smtp.ps1`, waits for readiness, then runs `scripts/api-tests.ps1` (185 checks) and `scripts/verify-mail.ps1` (16 checks) |
| `Docker image starts with the prod profile` | builds the image Railway runs, proves it refuses to start without `JWT_SECRET`, then starts it against Postgres and waits for `/actuator/health` to report `UP` — the same three things a deploy depends on |

The last step of the second job reads the sink's dump rather than the API, because the failures this
catches are invisible from the API. It checks the configured `From` on both the `From:` header and the
SMTP envelope (`MAIL FROM:`) — providers reject a message without a sender and SPF aligns on the
envelope — and then walks the whole enquiry conversation: a reply stored in the console, mailed to the
person who asked, carrying the enquiry reference in its subject, and holding a link that opens that
customer's own enquiry. Set `MAIL_FROM` / `MAIL_FROM_NAME` in that job's `env:` to change what it
checks for.

Branch protection on `main` should require both checks. Mail settings, the test database and the
bootstrap admin come from the workflow `env:` block (see `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`).

## Concurrency

Request handling is concurrent by default (Tomcat threads + a HikariCP pool). Three things are
handled explicitly:

- **Notification mail is asynchronous.** `BookingService` / `ContactQueryService` publish an event;
  `MailNotificationListener` reacts with `@TransactionalEventListener(AFTER_COMMIT)` and `@Async`,
  so mail is sent *after* the transaction commits (a rolled-back booking never mails a PIN) and on
  the `mailExecutor` pool instead of the request thread. Every attempt is still written to
  `email_log`.
  Consequence: `POST /api/contact` returns `autoResponseSent: false`; the mail worker flips it to the
  real outcome a moment later.
- **Capacity is enforced under a row lock.** `requestBooking` loads the package with
  `findByIdForUpdate` (`SELECT ... FOR UPDATE`) and compares
  `maxCapacity` against outstanding travelers (`PENDING` + `CONFIRMED`). Requests hold capacity
  until they are rejected, so concurrent bookings for the same package serialize and cannot
  oversubscribe it.
- **Bookings use optimistic locking.** `BookingRequest.version` (`@Version`) makes concurrent updates
  — e.g. two admins confirming the same booking — conflict instead of overwriting. The loser gets
  `409 Conflict`.

`scripts/api-tests.ps1` includes a concurrency section: 5 parallel bookings against a 3-seat package
(exactly 3 accepted) and 2 parallel confirmations of one booking (one wins, one 409).

## Known gaps

- List endpoints return everything (`findAll`) — add pagination as data grows.
- Tokens are stateless with no revocation or refresh: signing out ends the session in that browser,
  but the token itself stays valid until it expires.
- `GET /api/bookings/track?pin=` is public and not rate limited, so PINs could be guessed in bulk.
  It is the one write-adjacent route outside `RATE_LIMIT_PATHS`; add it there before the site is
  busy, and consider a longer PIN.
- Uploads live on a mounted volume, which survives a deploy but not a lost volume, and cannot be
  shared between instances — move `app.media.dir` to an object store (S3 and the like) before
  scaling out. Nothing takes backups of the database on its own either; see
  [Deployment](#deployment) for the one command that does.
- Uploaded bytes are served back as stored: no transcoding, resizing or thumbnail generation, and
  clips are not length-checked, only size-checked. A 60 MB phone video therefore ships 60 MB to every
  visitor who opens the gallery.
- The media library is admin-only with no per-file ownership or audit trail beyond `uploaded_by`.
- Nothing can be deleted except a package, a gallery item, a story, a review and a media file: an
  admin can confirm or reject a booking and answer an enquiry, but there is no route that removes a
  booking, a customer or an enquiry. That is why the API test suite leaves some records behind, and
  it is the first thing to add if the agency ever needs to honour a deletion request (or to clear
  out spam).
- **The conversation lives in Gmail, and the application only records it.** That is deliberate — the
  agency answers from the address it actually reads, and `scripts/verify-mail.ps1` proves the parts this
  application does send — but it has two consequences worth knowing. The console's "waiting for a reply"
  list is only as true as the staff keep it: a reply written in Gmail and never recorded here leaves the
  enquiry looking unanswered, which is what the "I answered from my inbox" action is for. And a customer
  writing back on their page is only mailed to the agency; nothing files the reply into the thread unless
  somebody pastes it in. Closing that gap means Bird's inbound side: publishing the MX records below and
  routing replies to `reply+<token>@mail.prathibalanka.com` so a webhook can match them to the enquiry.
- **The link in an acknowledgement is a bearer credential.** `/enquiry/{access_token}` is public and
  needs no login — 128 random bits, the same trade as the booking PIN — so anybody the customer forwards
  that email to can read the enquiry and write on it. That is the price of not demanding an account for a
  contact form; if it ever matters, the token should also expire, which it currently does not.
- There is no error tracking. An exception that returns 500 is in the platform's log and nowhere
  else; add Sentry (or similar) before relying on the site unattended.
- Mail over the Bird API is proven: a real enquiry through a running instance was **delivered** from
  `bookings@mail.prathibalanka.com` (Bird's events: accepted → processed by Gmail → delivered, nothing
  bounced or rejected).
- Bird's **inbound** side is not set up, and by choice: the subdomain sends and does not receive, so
  there is no `bookings@mail.prathibalanka.com` mailbox. `MAIL_REPLY_TO` points at the agency's Gmail
  inbox instead, which is the address that actually gets read. Publishing the three
  `rx*.eu1.inbound.bird.com` MX records is what would change that — and it also needs Bird's inbound
  routing configured, because that mail arrives at Bird, not at Gmail.
- There is no bounce or complaint handling: Bird reports them to its dashboard and to the DMARC
  address, and nothing in this application notices. At volume, add a Bird webhook that records
  bounces, or the first sign of a bad address list is a silent drop in deliverability.
- Scheduled work (reminders, stale pending bookings) is not implemented yet; when it is added,
  running more than one instance needs a lock (ShedLock) so jobs do not run twice.
