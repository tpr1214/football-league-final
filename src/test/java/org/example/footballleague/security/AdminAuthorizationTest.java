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
 * Admin APIs require ADMIN. League APIs require an authenticated user because
 * league state is now scoped per user.
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

    // ---------- POST /api/league/start-next-round ----------

    @Test
    @DisplayName("POST /api/league/start-next-round without a token is rejected (401)")
    void startRoundNoToken() throws Exception {
        mockMvc.perform(post("/api/league/start-next-round"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/league/start-next-round is allowed with a USER token")
    void startRoundUserToken() throws Exception {
        mockMvc.perform(post("/api/league/start-next-round").header(HttpHeaders.AUTHORIZATION, bearer("USER")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/league/start-next-round is allowed with an ADMIN token")
    void startRoundAdminToken() throws Exception {
        mockMvc.perform(post("/api/league/start-next-round").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN")))
                .andExpect(status().isOk());
    }

    // ---------- user-owned league endpoint ----------

    @Test
    @DisplayName("GET /api/league/matches without a token is rejected (401)")
    void matchesRequireToken() throws Exception {
        mockMvc.perform(get("/api/league/matches"))
                .andExpect(status().isUnauthorized());
    }
}
