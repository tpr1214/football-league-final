package org.example.footballleague.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Phase 2 (non-enforcing): if a request carries a valid {@code Authorization:
 * Bearer <token>}, this filter authenticates it by placing an
 * {@link AuthenticatedUser} principal (with a ROLE_* authority) into the
 * SecurityContext. It never blocks a request:
 * <ul>
 *   <li>No / non-Bearer header → continue unauthenticated.</li>
 *   <li>Invalid or expired token → continue UNAUTHENTICATED (we do NOT return 401).
 *       Phase 2 must stay non-breaking while endpoints are still permitAll; a stale
 *       or garbage token must not start failing requests that work today. Rejecting
 *       invalid tokens with 401 is deferred to the enforcement phase.</li>
 * </ul>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith(BEARER_PREFIX)
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                String token = header.substring(BEARER_PREFIX.length());
                if (jwtService.isTokenValid(token)) {
                    Long userId = jwtService.extractUserId(token);
                    String role = jwtService.extractRole(token);

                    var authority = new SimpleGrantedAuthority("ROLE_" + role);
                    var principal = new AuthenticatedUser(userId, role);
                    var authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(authority));
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception exception) {
            // Never let token handling crash the request; treat as unauthenticated.
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
