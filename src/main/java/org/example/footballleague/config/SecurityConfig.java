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
 * endpoints — admin APIs and the operational "start next round" action — which
 * now require a JWT carrying role ADMIN. Everything else (auth, read-only league
 * data, bets, profiles, SSE) remains permitAll for now; those phases come later.
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
                        // Public read-only league data.
                        .requestMatchers(HttpMethod.GET,
                                "/api/league/matches",
                                "/api/league/table",
                                "/api/league/matches/upcoming").permitAll()
                        // Phase 3A enforced endpoints: admin APIs + operational league action.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/league/start-next-round").hasRole("ADMIN")
                        // Phase 3B-2: bet endpoints require authentication; per-user
                        // ownership (or ADMIN) is enforced in the controller.
                        .requestMatchers("/api/bets/**").authenticated()
                        // Everything else stays open for now (profile/user/SSE).
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
