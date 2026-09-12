# PrathibaLanka

REST backend for a Sri Lanka travel agency: travel packages, booking requests with PIN tracking,
customer reviews, gallery, journal posts and contact queries.

## Stack

- Java 21, Spring Boot 4.1.1 (Web MVC, Data JPA, Security, Validation, Mail)
- PostgreSQL 15 (Docker), Hibernate `ddl-auto=update`
- JWT (JJWT 0.12.6) bearer tokens, BCrypt password hashes

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
| POST | `/api/admin/packages`, `/api/admin/gallery`, `/api/admin/journal` |
| PUT | `/api/admin/packages/{id}`, `/api/admin/gallery/{id}`, `/api/admin/journal/{id}` |
| PATCH | `/api/admin/packages/{id}/deactivate`, `/api/admin/bookings/{id}/confirm`, `/api/admin/bookings/{id}/reject`, `/api/admin/queries/{id}/respond`, `/api/admin/journal/{id}/publish`, `/api/admin/journal/{id}/unpublish` |
| DELETE | `/api/admin/packages/{id}`, `/api/admin/gallery/{id}`, `/api/admin/journal/{id}`, `/api/admin/reviews/{id}` |

Booking status flow: `PENDING` → `CONFIRMED` or `REJECTED`; only pending bookings can be rejected.

## Errors

```json
{ "timestamp": "...", "status": 400, "error": "Validation Failed",
  "message": "Request validation failed.", "details": ["email: must be a well-formed email address"],
  "path": "/api/auth/register" }
```

`400` validation/malformed input · `401` missing or invalid token, bad credentials ·
`403` wrong role or acting on another account · `404` unknown id/route ·
`405` wrong method · `409` duplicate or referenced record · `500` unexpected (details stay in the log).

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
and cleans up the data it creates. `mvn test` runs the context-load test.

## Known gaps

- Schema comes from Hibernate `ddl-auto=update`; use Flyway or Liquibase before production.
- No rate limiting on login, PIN tracking or the public contact form.
- List endpoints return everything (`findAll`) — add pagination as data grows.
- `spring.jpa.show-sql=true` is left on for development.
- Capacity is checked per request, not against already-confirmed bookings.
- Tokens are stateless with no revocation or refresh.
