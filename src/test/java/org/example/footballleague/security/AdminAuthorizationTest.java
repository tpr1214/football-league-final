package org.example.footballleague.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3A authorization tests. Boots the full context against H2 (test profile)
 * and drives the real security filter chain via MockMvc. Tokens are minted with
 * the application's own {@link JwtService} so the {@link JwtAuthenticationFilter}
 * accepts them.
 *
 * Only admin APIs and POST /api/league/start-next-round are enforced; public
 * read-only league endpoints must remain open.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private String bearer(String role) {
        return "Bearer " + jwtService.generateToken(1L, role);
    }

    // ---------- /api/admin/users ----------

    @Test
    @DisplayName("GET /api/admin/users without a token is rejected (401)")
    void adminUsersNoToken() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/admin/users with a USER token is forbidden (403)")
    void adminUsersUserToken() throws Exception {
        mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/admin/users with an ADMIN token is allowed (200)")
    void adminUsersAdminToken() throws Exception {
        mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN")))
                .andExpect(status().isOk());
    }

    // ---------- POST /api/league/start-next-round ----------

    @Test
    @DisplayName("POST /api/league/start-next-round without a token is rejected (401)")
    void startRoundNoToken() throws Exception {
        mockMvc.perform(post("/api/league/start-next-round"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/league/start-next-round with a USER token is forbidden (403)")
    void startRoundUserToken() throws Exception {
        mockMvc.perform(post("/api/league/start-next-round").header(HttpHeaders.AUTHORIZATION, bearer("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/league/start-next-round with an ADMIN token passes authorization (not 401/403)")
    void startRoundAdminToken() throws Exception {
        // With an empty H2 DB the service throws (no pending rounds) -> a 4xx/5xx
        // business error, NOT an auth failure. Proving it is neither 401 nor 403
        // is enough to show authorization was passed and the controller was reached.
        mockMvc.perform(post("/api/league/start-next-round").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN")))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    if (sc == 401 || sc == 403) {
                        throw new AssertionError("expected to pass authorization but got " + sc);
                    }
                });
    }

    // ---------- public endpoint still open ----------

    @Test
    @DisplayName("GET /api/league/matches stays public (no token, 200)")
    void publicMatchesOpen() throws Exception {
        mockMvc.perform(get("/api/league/matches"))
                .andExpect(status().isOk());
    }
}
