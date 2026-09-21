-- The cancellation mail is a fourth kind of notification, and email_log's check constraint was
-- written when there were three. The constraint matters more than it looks: the mail is sent *before*
-- the row is written, so an insert that is refused means the email went out and the audit trail
-- silently did not record it - which is how this was found (Bird had delivered the message while
-- email_log had no row for it).
--
-- Dropped by name rather than assumed: the constraint came from the pre-Flyway schema, and a database
-- that was baselined from V1 carries the same auto-generated name.

ALTER TABLE public.email_log DROP CONSTRAINT IF EXISTS email_log_email_type_check;

ALTER TABLE public.email_log ADD CONSTRAINT email_log_email_type_check
    CHECK (email_type::text = ANY (ARRAY[
        'AUTO_RESPONSE'::character varying,
        'PENDING_NOTIFICATION'::character varying,
        'CONFIRMATION'::character varying,
        'CANCELLATION'::character varying
    ]::text[]));
