# PrathibaLanka

REST backend for a Sri Lanka travel agency: travel packages, booking requests with PIN tracking,
customer reviews, gallery, journal posts, contact queries, uploaded media and the editable copy of
the public About/Contact pages.

## Stack

- Java 21, Spring Boot 4.1.1 (Web MVC, Data JPA, Security, Validation, Mail)
- PostgreSQL 15 (Docker), Hibernate `ddl-auto=update`
- JWT (JJWT 0.12.6) bearer tokens, BCrypt password hashes
- Jackson 3 (`tools.jackson`) — Spring Boot 4 no longer uses Jackson 2 for HTTP bodies

## Run

```bash
# 1. database
docker compose up -d

# 2. application (http://localhost:8080)
./mvnw spring-boot:run
```

Defaults match `docker-compose.yml`. Everything can be overridden with environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/travel_agency` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `travel_admin` / `secret` | DB credentials |
| `JWT_SECRET` | dev placeholder | Token signing key, min 32 bytes |
| `JWT_EXPIRATION_MS` | `86400000` (24 h) | Token lifetime |
| `MAIL_HOST` / `MAIL_PORT` | `smtp.mailtrap.io` / `2525` | SMTP server |
| `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_SMTP_AUTH` | `testing` / `123` / `true` | SMTP credentials |
| `BOOTSTRAP_ADMIN_ENABLED` | `true` | Create/repair the first admin on startup |
| `BOOTSTRAP_ADMIN_EMAIL` | `admin@test.com` | Bootstrap admin login |
| `BOOTSTRAP_ADMIN_PASSWORD` | `Admin@12345` | Bootstrap admin password |
| `BOOTSTRAP_CONTENT_ENABLED` | `true` | Seed the About/Contact pages when their rows are missing |
| `MEDIA_DIR` | `uploads` | Directory the uploaded files are written to |
| `MEDIA_URL_PREFIX` | `/media` | Public path the files are served from |
| `MEDIA_MAX_IMAGE_BYTES` | `10485760` (10 MB) | Image upload limit |
| `MEDIA_MAX_VIDEO_BYTES` | `62914560` (60 MB) | Video upload limit |
| `MEDIA_MAX_UPLOAD` / `MEDIA_MAX_REQUEST` | `64MB` / `70MB` | Multipart ceiling (hard limit above the per-type ones) |

The bootstrap admin is created only if that email does not exist, and its password is reset only
when the stored hash is not a valid BCrypt hash. A valid password is never overwritten. Disable it
in production and change the password after the first login.

Mail is sent inline while handling the request. A failed send never fails the request: the attempt
is stored in `email_log` with `sent` and `failure_reason`.

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

## Tests

```bash
# backend must be running; pass -BaseUrl if it is not on 18080
powershell -ExecutionPolicy Bypass -File scripts/api-tests.ps1 -BaseUrl http://localhost:8080

# optional: local SMTP sink, so mail paths can be tested without a provider
powershell -ExecutionPolicy Bypass -File scripts/fake-smtp.ps1 -Port 2525
# then start the app with --spring.mail.host=127.0.0.1 --spring.mail.port=2525 \
#   --spring.mail.properties.mail.smtp.auth=false
```

`scripts/api-tests.ps1` covers every endpoint plus validation, authorization and ownership cases,
and cleans up the data it creates. Upload fixtures (a 1×1 PNG, a WebM header, a mislabeled file and
an 11 MB image) are written to the temp directory at run time, so the suite proves the upload path,
the signature check, the per-type size limit and the byte round trip without committing binaries.
`mvn test` runs the context-load test.

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

## Continuous integration

`.github/workflows/ci.yml` runs on every push to `main`/`updates` and on pull requests into `main`:

| Job | What it does |
|---|---|
| `Compile & test` | `mvn -B -ntp clean verify` against a `postgres:15` service container; uploads surefire reports + the application jar |
| `API endpoint tests` | boots the built jar against the same Postgres, points mail at `scripts/fake-smtp.ps1`, waits for readiness, then runs `scripts/api-tests.ps1` |

Branch protection on `main` should require both checks. Mail settings, the test database and the
bootstrap admin come from the workflow `env:` block (see `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`).

## Known gaps

- Schema comes from Hibernate `ddl-auto=update`; use Flyway or Liquibase before production.
- No rate limiting on login, PIN tracking or the public contact form.
- List endpoints return everything (`findAll`) — add pagination as data grows.
- `spring.jpa.show-sql=true` is left on for development.
- Tokens are stateless with no revocation or refresh.
- Uploads go to the local disk, which does not survive a container rebuild and is not shared between
  instances — move `app.media.dir` to an object store (S3 and the like) before scaling out.
- Uploaded bytes are served back as stored: no transcoding, resizing or thumbnail generation, and
  clips are not length-checked, only size-checked.
- The media library is admin-only with no per-file ownership or audit trail beyond `uploaded_by`.
- Scheduled work (reminders, stale pending bookings) is not implemented yet; when it is added,
  running more than one instance needs a lock (ShedLock) so jobs do not run twice.
