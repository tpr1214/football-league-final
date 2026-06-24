package org.example.footballleague.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JwtAuthenticationFilter}. Uses a real {@link JwtService}
 * and mocked servlet objects. Verifies the filter authenticates valid Bearer
 * tokens, stays unauthenticated otherwise, and always continues the chain
 * (non-breaking while endpoints are permitAll).
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET =
            "test-only-change-me-test-only-change-me-test-only-change-me";

    private final JwtService jwtService = new JwtService(SECRET, 86_400_000L);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);

    @BeforeEach
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void invoke(String authHeader, FilterChain chain) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(authHeader);
        filter.doFilterInternal(request, response, chain);
    }

    @Test
    @DisplayName("Valid Bearer token populates the SecurityContext and proceeds")
    void validTokenAuthenticates() throws Exception {
        String token = jwtService.generateToken(42L, "ADMIN");
        FilterChain chain = mock(FilterChain.class);

        invoke("Bearer " + token, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertInstanceOf(AuthenticatedUser.class, auth.getPrincipal());
        AuthenticatedUser principal = (AuthenticatedUser) auth.getPrincipal();
        assertEquals(42L, principal.userId());
        assertEquals("ADMIN", principal.role());
        assertTrue(auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        verify(chain, times(1)).doFilter(any(), any()); // chain always continues
    }

    @Test
    @DisplayName("Missing Authorization header stays unauthenticated but proceeds")
    void missingHeaderStaysUnauthenticated() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        invoke(null, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    @DisplayName("Invalid/garbage token stays unauthenticated but proceeds")
    void invalidTokenStaysUnauthenticated() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        invoke("Bearer not-a-real-token", chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    @DisplayName("Non-Bearer Authorization header is ignored but proceeds")
    void nonBearerHeaderIgnored() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        invoke("Basic dXNlcjpwYXNz", chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain, times(1)).doFilter(any(), any());
    }
}
