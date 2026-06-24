package org.example.footballleague.security;

/**
 * Lightweight principal placed in the Spring SecurityContext when a request
 * carries a valid JWT. Holds just the authenticated identity derived from the
 * token. Phase 2: populated by {@link JwtAuthenticationFilter} but not yet
 * required by any endpoint.
 */
public record AuthenticatedUser(Long userId, String role) {
}
