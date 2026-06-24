package org.example.footballleague.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for {@link JwtService}. No Spring context — the service is
 * constructed directly with a test secret (HS256 requires >= 256 bits).
 */
class JwtServiceTest {

    private static final String SECRET =
            "test-only-change-me-test-only-change-me-test-only-change-me";
    private static final long ONE_DAY_MS = 86_400_000L;

    private JwtService service() {
        return new JwtService(SECRET, ONE_DAY_MS);
    }

    @Nested
    @DisplayName("generateToken / extraction")
    class GenerateAndExtract {

        @Test
        @DisplayName("Generates a non-empty token")
        void generatesToken() {
            String token = service().generateToken(42L, "USER");
            assertNotNull(token);
            assertFalse(token.isBlank());
        }

        @Test
        @DisplayName("Round-trips the user id via the subject")
        void extractsUserId() {
            JwtService jwt = service();
            String token = jwt.generateToken(42L, "USER");
            assertEquals(42L, jwt.extractUserId(token));
        }

        @Test
        @DisplayName("Round-trips the role claim")
        void extractsRole() {
            JwtService jwt = service();
            String token = jwt.generateToken(7L, "ADMIN");
            assertEquals("ADMIN", jwt.extractRole(token));
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("Accepts a freshly generated token")
        void validTokenAccepted() {
            JwtService jwt = service();
            String token = jwt.generateToken(1L, "USER");
            assertTrue(jwt.isTokenValid(token));
        }

        @Test
        @DisplayName("Rejects a tampered token")
        void tamperedTokenRejected() {
            JwtService jwt = service();
            String token = jwt.generateToken(1L, "USER");
            // Flip the last character of the signature segment.
            char last = token.charAt(token.length() - 1);
            String tampered = token.substring(0, token.length() - 1)
                    + (last == 'A' ? 'B' : 'A');
            assertFalse(jwt.isTokenValid(tampered));
        }

        @Test
        @DisplayName("Rejects a token signed with a different secret")
        void differentSecretRejected() {
            String token = service().generateToken(1L, "USER");
            JwtService otherKey = new JwtService(
                    "another-secret-another-secret-another-secret-xx", ONE_DAY_MS);
            assertFalse(otherKey.isTokenValid(token));
        }

        @Test
        @DisplayName("Rejects an expired token")
        void expiredTokenRejected() {
            // Negative lifetime => the token is already expired at creation.
            JwtService expiring = new JwtService(SECRET, -1_000L);
            String token = expiring.generateToken(1L, "USER");
            assertFalse(expiring.isTokenValid(token));
        }

        @Test
        @DisplayName("Rejects obvious garbage")
        void garbageRejected() {
            assertFalse(service().isTokenValid("not-a-jwt"));
        }
    }
}
