package org.example.footballleague.config;

import org.example.footballleague.security.JwtAuthenticationFilter;
import org.example.footballleague.security.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

/**
 * Phase 3A: begins enforcing real authorization, but ONLY on the highest-risk
 * endpoints — the admin APIs — which require a JWT carrying role ADMIN. The
 * League, bet and profile endpoints are user-scoped and require a JWT. Admin
 * APIs require an ADMIN JWT. Auth and SSE remain public; the SSE stream is
 * filtered by the requested user id because EventSource cannot send auth headers.
 *
 * Unauthenticated requests to a protected endpoint get 401 (via the entry point);
 * authenticated-but-wrong-role requests get 403 (default access-denied handling).
 *
 * CORS continues to be governed by {@link WebConfig#addCorsMappings(CorsRegistry)};
 * enabling cors() here lets the security chain defer to that existing policy.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public authentication endpoints.
                        .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
                        // User-owned league data.
                        .requestMatchers(HttpMethod.GET,
                                "/api/league/matches",
                                "/api/league/table",
                                "/api/league/matches/upcoming").authenticated()
                        // Admin APIs stay ADMIN-only.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // These actions advance only the authenticated user's league.
                        .requestMatchers(HttpMethod.POST,
                                "/api/league/start-next-round",
                                "/api/league/regenerate-rounds").authenticated()
                        // Phase 3B-2: bet endpoints require authentication; per-user
                        // ownership (or ADMIN) is enforced in the controller.
                        .requestMatchers("/api/bets/**").authenticated()
                        // Phase 3B-3: profile endpoints require authentication; per-user
                        // ownership (or ADMIN) is enforced in the controller.
                        .requestMatchers("/api/auth/profile/**").authenticated()
                        // Everything else stays open for now (user/SSE).
                        .anyRequest().permitAll())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                // 401 for missing/invalid auth; 403 (default) for wrong role.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // Recognize Bearer tokens and populate the SecurityContext.
                .addFilterBefore(new JwtAuthenticationFilter(jwtService),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
