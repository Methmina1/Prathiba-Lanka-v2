package com.example.PrathibaLanka.security;

import com.example.PrathibaLanka.exception.ForbiddenException;

/**
 * Guards object ownership for endpoints that act on a customer account.
 *
 * <p>Booking and review submission used to trust the {@code customerId} sent in the request
 * body, which let any anonymous caller create bookings or post reviews in another customer's
 * name. The acting customer is now always taken from the authenticated JWT.
 */
public final class OwnershipGuard {

    private OwnershipGuard() {
    }

    /**
     * @param requestedCustomerId     customerId from the request body (may be null)
     * @param authenticatedCustomerId customerId from the authenticated principal
     * @return the customerId to act on
     * @throws ForbiddenException when there is no authenticated customer, or when the body
     *                            tries to act on a different account
     */
    public static Long requireOwnCustomerId(Long requestedCustomerId, Long authenticatedCustomerId) {
        if (authenticatedCustomerId == null) {
            throw new ForbiddenException("You must be signed in as a customer to perform this action.");
        }
        if (requestedCustomerId != null && !requestedCustomerId.equals(authenticatedCustomerId)) {
            throw new ForbiddenException("You may only perform this action on your own account.");
        }
        return authenticatedCustomerId;
    }
}
