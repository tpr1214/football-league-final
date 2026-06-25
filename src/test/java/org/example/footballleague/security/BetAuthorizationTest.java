package org.example.footballleague.security;

import org.example.footballleague.model.Match;
import org.example.footballleague.model.MatchStatus;
import org.example.footballleague.model.Team;
import org.example.footballleague.model.User;
import org.example.footballleague.repositories.MatchRepository;
import org.example.footballleague.repositories.TeamRepository;
import org.example.footballleague.repositories.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3B-2 authorization tests for /api/bets/**. Boots the full context against
 * H2 and drives the real security chain via MockMvc. Verifies that bets are bound
 * to the JWT user (not a spoofable body/path id) and that reads are self-or-ADMIN.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BetAuthorizationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private MatchRepository matchRepository;
    @Autowired private TeamRepository teamRepository;

    private String bearer(Long userId, String role) {
        return "Bearer " + jwtService.generateToken(userId, role);
    }

    private User newUser() {
        User u = new User();
        String tag = UUID.randomUUID().toString().substring(0, 8);
        u.setUsername("user-" + tag);
        u.setEmail(tag + "@example.com");
        u.setPasswordHash("x");
        u.setBalance(100_000.0);
        u.setRole("USER");
        return userRepository.save(u);
    }

    private Team newTeam() {
        Team t = new Team();
        t.setName("team-" + UUID.randomUUID().toString().substring(0, 8));
        t.setSkillLevel(80);
        return teamRepository.save(t);
    }

    private Match newPendingMatch() {
        Match m = new Match();
        m.setHomeTeam(newTeam());
        m.setAwayTeam(newTeam());
        m.setRoundNumber(1);
        m.setStatus(MatchStatus.PENDING);
        return matchRepository.save(m);
    }

    private String betBody(Long bodyUserId, Long matchId) {
        return "{"
                + "\"userId\":" + bodyUserId + ","
                + "\"matchId\":" + matchId + ","
                + "\"predictedOutcome\":\"HOME_WIN\","
                + "\"predictedHomeScore\":2,"
                + "\"predictedAwayScore\":1,"
                + "\"amount\":50"
                + "}";
    }

    // ---------- POST /api/bets/place ----------

    @Test
    @DisplayName("POST /api/bets/place without a token is rejected (401)")
    void placeNoToken() throws Exception {
        Match match = newPendingMatch();
        mockMvc.perform(post("/api/bets/place").contentType(MediaType.APPLICATION_JSON)
                        .content(betBody(1L, match.getId())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/bets/place with a USER token succeeds")
    void placeWithUserToken() throws Exception {
        User user = newUser();
        Match match = newPendingMatch();
        mockMvc.perform(post("/api/bets/place")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId(), "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(betBody(user.getId(), match.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getId()));
    }

    @Test
    @DisplayName("Body userId is ignored: the bet is created for the TOKEN user, not the spoofed id")
    void placeIgnoresSpoofedBodyUserId() throws Exception {
        User tokenUser = newUser();
        User otherUser = newUser();
        Match match = newPendingMatch();
        mockMvc.perform(post("/api/bets/place")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenUser.getId(), "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        // body claims the OTHER user — must be ignored
                        .content(betBody(otherUser.getId(), match.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(tokenUser.getId()));
    }

    // ---------- GET /api/bets/user/{id} ----------

    @Test
    @DisplayName("GET own bets with USER token is allowed (200)")
    void ownBetsAllowed() throws Exception {
        User user = newUser();
        mockMvc.perform(get("/api/bets/user/" + user.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId(), "USER")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET another user's bets with USER token is forbidden (403)")
    void otherBetsForbidden() throws Exception {
        User user = newUser();
        mockMvc.perform(get("/api/bets/user/" + (user.getId() + 999))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId(), "USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET another user's bets with ADMIN token is allowed (200)")
    void otherBetsAdminAllowed() throws Exception {
        User user = newUser();
        mockMvc.perform(get("/api/bets/user/" + (user.getId() + 999))
                        .header(HttpHeaders.AUTHORIZATION, bearer(424242L, "ADMIN")))
                .andExpect(status().isOk());
    }

    // ---------- GET /api/bets/user/{id}/pending ----------

    @Test
    @DisplayName("GET own pending bets with USER token is allowed (200)")
    void ownPendingAllowed() throws Exception {
        User user = newUser();
        mockMvc.perform(get("/api/bets/user/" + user.getId() + "/pending")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId(), "USER")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET another user's pending bets with USER token is forbidden (403)")
    void otherPendingForbidden() throws Exception {
        User user = newUser();
        mockMvc.perform(get("/api/bets/user/" + (user.getId() + 999) + "/pending")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId(), "USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET another user's pending bets with ADMIN token is allowed (200)")
    void otherPendingAdminAllowed() throws Exception {
        User user = newUser();
        mockMvc.perform(get("/api/bets/user/" + (user.getId() + 999) + "/pending")
                        .header(HttpHeaders.AUTHORIZATION, bearer(424242L, "ADMIN")))
                .andExpect(status().isOk());
    }

    // ---------- user-owned league endpoint ----------

    @Test
    @DisplayName("GET /api/league/matches without a token is rejected (401)")
    void leagueMatchesRequireToken() throws Exception {
        mockMvc.perform(get("/api/league/matches"))
                .andExpect(status().isUnauthorized());
    }
}
