package org.example.footballleague.security;

import org.example.footballleague.model.User;
import org.example.footballleague.repositories.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3B-3 authorization tests for /api/auth/profile/**. Boots the full
 * context against H2 and drives the real security chain via MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.upload.dir=target/test-uploads/profile-authorization")
class ProfileAuthorizationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;

    private String bearer(Long userId, String role) {
        return "Bearer " + jwtService.generateToken(userId, role);
    }

    private User newUser() {
        User u = new User();
        String tag = UUID.randomUUID().toString().substring(0, 8);
        u.setUsername("profile-user-" + tag);
        u.setEmail("profile-" + tag + "@example.com");
        u.setPasswordHash("x");
        u.setBalance(1000.0);
        u.setRole("USER");
        return userRepository.save(u);
    }

    private MockMultipartFile pngFile() {
        return new MockMultipartFile("file", "avatar.png", "image/png", new byte[] {1, 2, 3});
    }

    private String updateBody(String username) {
        return "{"
                + "\"username\":\"" + username + "\","
                + "\"profileImageUrl\":\"https://example.com/avatar.png\","
                + "\"profileLink\":\"https://example.com/profile\""
                + "}";
    }

    // ---------- GET /api/auth/profile/{id} ----------

    @Test
    @DisplayName("GET own profile with USER token is allowed (200)")
    void getOwnProfileAllowed() throws Exception {
        User user = newUser();

        mockMvc.perform(get("/api/auth/profile/" + user.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId(), "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()));
    }

    @Test
    @DisplayName("GET another user's profile with USER token is forbidden (403)")
    void getOtherProfileForbidden() throws Exception {
        User user = newUser();
        User other = newUser();

        mockMvc.perform(get("/api/auth/profile/" + other.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId(), "USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET another user's profile with ADMIN token is allowed (200)")
    void getOtherProfileAdminAllowed() throws Exception {
        User other = newUser();

        mockMvc.perform(get("/api/auth/profile/" + other.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(424242L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(other.getId()));
    }

    @Test
    @DisplayName("GET profile without a token is rejected (401)")
    void getProfileNoToken() throws Exception {
        User user = newUser();

        mockMvc.perform(get("/api/auth/profile/" + user.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ---------- PUT /api/auth/profile/{id} ----------

    @Test
    @DisplayName("PUT own profile with USER token is allowed (200)")
    void putOwnProfileAllowed() throws Exception {
        User user = newUser();

        mockMvc.perform(put("/api/auth/profile/" + user.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId(), "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("own-profile-update")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("own-profile-update"));
    }

    @Test
    @DisplayName("PUT another user's profile with USER token is forbidden (403)")
    void putOtherProfileForbidden() throws Exception {
        User user = newUser();
        User other = newUser();

        mockMvc.perform(put("/api/auth/profile/" + other.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId(), "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("blocked-update")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT another user's profile with ADMIN token is allowed (200)")
    void putOtherProfileAdminAllowed() throws Exception {
        User other = newUser();

        mockMvc.perform(put("/api/auth/profile/" + other.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(424242L, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("admin-profile-update")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin-profile-update"));
    }

    // ---------- POST /api/auth/profile/{id}/image ----------

    @Test
    @DisplayName("POST own profile image with USER token is allowed (200)")
    void uploadOwnProfileImageAllowed() throws Exception {
        User user = newUser();

        mockMvc.perform(multipart("/api/auth/profile/" + user.getId() + "/image")
                        .file(pngFile())
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId(), "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageUrl").value(org.hamcrest.Matchers.containsString("/uploads/profile-images/")));
    }

    @Test
    @DisplayName("POST another user's profile image with USER token is forbidden (403)")
    void uploadOtherProfileImageForbidden() throws Exception {
        User user = newUser();
        User other = newUser();

        mockMvc.perform(multipart("/api/auth/profile/" + other.getId() + "/image")
                        .file(pngFile())
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.getId(), "USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST another user's profile image with ADMIN token is allowed (200)")
    void uploadOtherProfileImageAdminAllowed() throws Exception {
        User other = newUser();

        mockMvc.perform(multipart("/api/auth/profile/" + other.getId() + "/image")
                        .file(pngFile())
                        .header(HttpHeaders.AUTHORIZATION, bearer(424242L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageUrl").value(org.hamcrest.Matchers.containsString("/uploads/profile-images/")));
    }

    // ---------- public endpoints still open ----------

    @Test
    @DisplayName("Login and register stay public")
    void loginAndRegisterStayPublic() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        String email = "public-profile-" + tag + "@example.com";
        String password = "local-test-password";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"public-profile-" + tag + "\",\"email\":\"" + email + "\",\"passwordHash\":\"" + password + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"passwordHash\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    @DisplayName("League read endpoints require a token")
    void leagueReadEndpointsRequireToken() throws Exception {
        mockMvc.perform(get("/api/league/matches"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/league/table"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/league/matches/upcoming"))
                .andExpect(status().isUnauthorized());
    }
}
