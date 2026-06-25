package org.example.footballleague.security;

import org.example.footballleague.Service.LeagueService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
 * Only the admin APIs are enforced; public read-only league data AND the now-open
 * POST /api/league/start-next-round action must remain reachable by anyone.
 * {@link LeagueService} is mocked so exercising start-next-round here verifies
 * authorization only, without kicking off the real (threaded) round simulation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private LeagueService leagueService;

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

    // ---------- POST /api/league/start-next-round (now public) ----------

    @Test
    @DisplayName("POST /api/league/start-next-round is public without a token (not 401/403)")
    void startRoundNoToken() throws Exception {
        mockMvc.perform(post("/api/league/start-next-round"))
                .andExpect(result -> assertNotAuthBlocked(result.getResponse().getStatus()));
    }

    @Test
    @DisplayName("POST /api/league/start-next-round is public with a USER token (not 401/403)")
    void startRoundUserToken() throws Exception {
        mockMvc.perform(post("/api/league/start-next-round").header(HttpHeaders.AUTHORIZATION, bearer("USER")))
                .andExpect(result -> assertNotAuthBlocked(result.getResponse().getStatus()));
    }

    @Test
    @DisplayName("POST /api/league/start-next-round is also reachable with an ADMIN token (not 401/403)")
    void startRoundAdminToken() throws Exception {
        mockMvc.perform(post("/api/league/start-next-round").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN")))
                .andExpect(result -> assertNotAuthBlocked(result.getResponse().getStatus()));
    }

    private static void assertNotAuthBlocked(int status) {
        if (status == 401 || status == 403) {
            throw new AssertionError("start-next-round must be public, but got " + status);
        }
    }

    // ---------- public endpoint still open ----------

    @Test
    @DisplayName("GET /api/league/matches stays public (no token, 200)")
    void publicMatchesOpen() throws Exception {
        mockMvc.perform(get("/api/league/matches"))
                .andExpect(status().isOk());
    }
}
