package org.example.footballleague.config;

import org.example.footballleague.security.JwtAuthenticationFilter;
import org.example.footballleague.security.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

/**
 * Phase 1 (additive): adding spring-boot-starter-security would otherwise lock
 * every endpoint behind HTTP Basic. This config keeps the API open exactly as
 * before — it only prevents that default lockdown while the JWT foundation is
 * introduced. Authentication is NOT enforced here: all requests are permitted,
 * no JWT is required, and the login prompt / CSRF are disabled for the stateless
 * REST API. Endpoint restrictions and the JWT filter come in later phases.
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
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                // Phase 2: recognize Bearer tokens and populate the SecurityContext.
                // Still non-enforcing — anyRequest().permitAll() above is unchanged.
                .addFilterBefore(new JwtAuthenticationFilter(jwtService),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
