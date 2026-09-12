package com.example.PrathibaLanka.security;

import com.example.PrathibaLanka.exception.ForbiddenException;

/**
 * Booking and review submission used to trust the {@code customerId} in the request body, which
 * allowed acting on another customer's account. The acting customer now always comes from the JWT.
 */
public final class OwnershipGuard {

    private OwnershipGuard() {
    }

    /**
     * @param requestedCustomerId     customerId from the request body, may be null
     * @param authenticatedCustomerId customerId of the authenticated principal
     * @return the customerId to act on
     * @throws ForbiddenException if there is no authenticated customer, or the body targets another one
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
