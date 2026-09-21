-- A booking request can come from somebody who has no account.
--
-- The public form on /plan asks for a journey, so the person filling it in is a traveller with an
-- email address rather than a registered customer. Their details travel with the booking - exactly
-- how contact_query already works - so an admin always has an address to reply to.
--
-- No account is invented on their behalf on purpose: customer.email is unique, so a row created here
-- would stop that person registering later with their own address.
--
-- customer_id stays for the requests made from a signed-in account. Those show up on the customer's
-- own page as well as in the console, which is what makes it worth keeping - so it becomes optional
-- rather than going away.

ALTER TABLE public.booking_request ADD COLUMN contact_name character varying(100);
ALTER TABLE public.booking_request ADD COLUMN contact_email character varying(150);

-- Every existing row was made by a signed-in customer, so their contact details come from that
-- account. The foreign key guarantees the customer row is there to read them from.
UPDATE public.booking_request b
   SET contact_name = c.full_name,
       contact_email = c.email
  FROM public.customer c
 WHERE b.customer_id = c.customer_id;

ALTER TABLE public.booking_request ALTER COLUMN contact_name SET NOT NULL;
ALTER TABLE public.booking_request ALTER COLUMN contact_email SET NOT NULL;
ALTER TABLE public.booking_request ALTER COLUMN customer_id DROP NOT NULL;
