# PrathibaLanka

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
| `JWT_ISSUER` | `PrathibaLanka` | Token issuer claim |
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
| `MAIL_HOST` / `MAIL_PORT` | `smtp.gmail.com` / `587` | SMTP server |
| `MAIL_USERNAME` | `prathibhalankavoyages@gmail.com` | SMTP account |
| `MAIL_PASSWORD` | *(empty)* | **Must be set**: a Google App Password, not the account password |
| `MAIL_SMTP_AUTH` / `MAIL_SMTP_STARTTLS` | `true` / `true` | SMTP authentication and TLS (STARTTLS is for port 587) |
| `MAIL_FROM` / `MAIL_FROM_NAME` | `prathibhalankavoyages@gmail.com` / `PrathibaLanka` | Sender the recipient sees |

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
| `RATE_LIMIT_PATHS` | `/api/contact,/api/auth/login,/api/auth/register` | Endpoints covered |

**Health, logging**

| Variable | Default | Purpose |
|---|---|---|
| `ACTUATOR_ENDPOINTS` | `health,info` | What is exposed under `/actuator` |
| `ACTUATOR_HEALTH_DETAILS` | `never` | Keep datasource and disk details out of the response |
| `HEALTH_MAIL_ENABLED` | `false` | Mail is not a health criterion (see the mail notes below) |
| `HEALTH_CACHE` | `2s` | How long a health result is reused |
| `LOG_LEVEL` | `INFO` under `prod` | Root log level |
| `SQL_LOG_LEVEL` | `WARN` | Hibernate SQL logging; raise to `DEBUG` only locally |

Mail goes out as the agency's Gmail account. Two things have to be right before it will send:

1. **`MAIL_PASSWORD` must be a Google App Password**, not the account password — Gmail rejects plain
   SMTP logins. Create one at `myaccount.google.com/apppasswords` (2-step verification has to be on
   first); it is 16 characters and shown once.
2. **`MAIL_FROM` must be that same account**, or a verified alias on it. Gmail refuses to send as an
   address it has not verified.

`EmailService` sets the sender explicitly (`Name <address>`, both the header and the SMTP envelope),
because JavaMail otherwise invents one like `user@host` and providers reject it.

Mail is sent after the transaction commits, on a separate thread, so a failed send never fails the
request: the attempt is stored in `email_log` with `sent` and `failure_reason`. There is no retry - a
failure is logged and dropped, so watch that table when you change the mail settings.

Gmail's own limits apply: a free account can send to roughly 500 recipients a day. Move to a
transactional provider (SES, SendGrid, Postmark) before volume matters, and keep the same
`MAIL_FROM` - by then the sending domain wants SPF and DKIM records.

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

Customer (bearer token, `ROLE_CUSTOMER`)

| Method | Path |
|---|---|
| POST | `/api/bookings/request` |
| GET | `/api/customer/bookings` |
| POST | `/api/reviews` |

Admin (bearer token, `ROLE_ADMIN`)

| Method | Path |
|---|---|
| GET | `/api/admin/packages`, `/api/admin/bookings?status=`, `/api/admin/queries?onlyNew=`, `/api/admin/journal`, `/api/admin/journal/{id}` |
| GET | `/api/admin/media?type=`, `/api/admin/media/limits`, `/api/admin/content` |
| POST | `/api/admin/packages`, `/api/admin/gallery`, `/api/admin/journal`, `/api/admin/media` (multipart) |
| PUT | `/api/admin/packages/{id}`, `/api/admin/gallery/{id}`, `/api/admin/journal/{id}`, `/api/admin/content/{section}` |
| PATCH | `/api/admin/packages/{id}/deactivate`, `/api/admin/bookings/{id}/confirm`, `/api/admin/bookings/{id}/reject`, `/api/admin/queries/{id}/respond`, `/api/admin/journal/{id}/publish`, `/api/admin/journal/{id}/unpublish` |
| DELETE | `/api/admin/packages/{id}`, `/api/admin/gallery/{id}`, `/api/admin/journal/{id}`, `/api/admin/reviews/{id}`, `/api/admin/media/{id}` |

Booking status flow: `PENDING` → `CONFIRMED` or `REJECTED`; only pending bookings can be rejected.

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
   | `CORS_ALLOWED_ORIGINS` | only needed if the browser calls this API directly; with the front end proxying, leave it out |
   | `BOOTSTRAP_ADMIN_PASSWORD` | the first admin's password, for the first deploy only |
   | `BOOTSTRAP_ADMIN_EMAIL` | `prathibhalankavoyages@gmail.com` (this is the default) |
   | `MAIL_PASSWORD` | a Google App Password, or leave it out and no mail is sent (see below) |

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
- **The mail account is optional.** With no `MAIL_PASSWORD` the site works normally — enquiries and
  bookings are stored, and the send failure is recorded in `email_log` with `sent=false` and the
  reason. Gmail also has to accept a login from Railway's addresses; if it does not, the same
  `email_log` shows `Authentication failed` rather than anything failing silently.
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
```

`scripts/api-tests.ps1` covers every endpoint plus validation, authorization and ownership cases,
and deletes every record it can. It cannot delete all of them: no route removes a booking, a customer
or a contact query, so the packages those bookings point at stay as well — the run prints exactly
what it left behind, and it belongs against a development database. Upload fixtures (a 1×1 PNG, a
WebM header, a mislabeled file and an 11 MB image) are written to the temp directory at run time, so
the suite proves the upload path, the signature check, the per-type size limit and the byte round trip
without committing binaries. `mvn test` runs the context-load test.

Five suites cover the project between them — 249 checks in total:

| Suite | Needs | Checks |
|---|---|---|
| `scripts/api-tests.ps1` (this repo) | a running API | 161 — every endpoint over HTTP |
| `mvn test` (this repo) | nothing | 6 — the Spring context, the mail configuration |
| `npm run check:render` (front end) | nothing | 24 routes rendered in Node |
| `npm run test:e2e` (front end) | nothing (API mocked) | 37 browser tests |
| `npm run test:roles` (front end) | a running API + `BOOTSTRAP_ADMIN_PASSWORD` | 21 journeys through the real UI and database, one per role |

The last one writes to whichever database the API points at, so run it against a development
database. It marks everything it creates and deletes it again at the end of the run.

## Continuous integration

`.github/workflows/ci.yml` runs on every push to `main`/`updates` and on pull requests into `main`:

| Job | What it does |
|---|---|
| `Compile & test` | `mvn -B -ntp clean verify` against a `postgres:15` service container; uploads surefire reports + the application jar |
| `API endpoint tests` | boots the built jar against the same Postgres, points mail at `scripts/fake-smtp.ps1`, waits for readiness, then runs `scripts/api-tests.ps1` (161 checks) and verifies the mail sender |
| `Docker image starts with the prod profile` | builds the image Railway runs, proves it refuses to start without `JWT_SECRET`, then starts it against Postgres and waits for `/actuator/health` to report `UP` — the same three things a deploy depends on |

The last step of the second job posts a contact enquiry and asserts, from the sink's dump, that the
configured `From` reached the wire - both as the `From:` header and as the SMTP envelope sender
(`MAIL FROM:`), because providers reject a message without a sender and SPF aligns on the envelope.
Set `MAIL_FROM` / `MAIL_FROM_NAME` in that job's `env:` to change what it checks for.

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
- There is no error tracking. An exception that returns 500 is in the platform's log and nowhere
  else; add Sentry (or similar) before relying on the site unattended.
- Mail from the deployed environment has not been verified end to end: the account still needs a
  Google App Password, and whether Gmail accepts a login from the host's addresses is something only
  a real send will show. `email_log` records the outcome of every attempt either way.
- Scheduled work (reminders, stale pending bookings) is not implemented yet; when it is added,
  running more than one instance needs a lock (ShedLock) so jobs do not run twice.
