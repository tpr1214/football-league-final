package org.example.footballleague.Service;

import org.example.footballleague.model.User;
import org.example.footballleague.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link UserService}. The repository is mocked; no Spring
 * context and no real database. Profile-image tests use a JUnit @TempDir for the
 * upload directory so the only filesystem touch is an isolated temp folder.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final double DELTA = 0.0001;
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @TempDir
    Path tempUploadDir;

    @BeforeEach
    void setUpValueFields() {
        // @Value fields are not populated by Mockito; set them explicitly.
        ReflectionTestUtils.setField(userService, "uploadDir", tempUploadDir.toString());
        ReflectionTestUtils.setField(userService, "baseUrl", "http://localhost:8080");
    }

    private User user(Long id, String role) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        return u;
    }

    private User registrationUser(String role) {
        User u = user(null, role);
        u.setUsername("new-user");
        u.setEmail("new-user@example.com");
        u.setPasswordHash("plain-password");
        return u;
    }

    // ========================================================
    // register
    // ========================================================
    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("Normal registration saves a USER role")
        void normalUserGetsUserRole() {
            User request = registrationUser(null);
            when(userRepository.findByEmail("new-user@example.com")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            boolean result = userService.register(request);

            assertTrue(result);
            User saved = savedUser();
            assertEquals("USER", saved.getRole());
            assertEquals(1000.0, saved.getBalance(), DELTA);
            assertNotEquals("plain-password", saved.getPasswordHash());
            assertTrue(saved.getPasswordHash().startsWith("$2"));
            assertTrue(PASSWORD_ENCODER.matches("plain-password", saved.getPasswordHash()));
        }

        @Test
        @DisplayName("Client-supplied ADMIN role is ignored")
        void adminRoleInjectionIsForcedToUser() {
            User request = registrationUser("ADMIN");
            when(userRepository.findByEmail("new-user@example.com")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            boolean result = userService.register(request);

            assertTrue(result);
            assertEquals("USER", savedUser().getRole());
        }

        @Test
        @DisplayName("Client-supplied lowercase/admin-like role is ignored")
        void lowercaseAdminLikeRoleInjectionIsForcedToUser() {
            User request = registrationUser("admin");
            when(userRepository.findByEmail("new-user@example.com")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            boolean result = userService.register(request);

            assertTrue(result);
            assertEquals("USER", savedUser().getRole());
        }

        private User savedUser() {
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            return captor.getValue();
        }
    }

    // ========================================================
    // login
    // ========================================================
    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("Correct raw password matches the stored BCrypt hash")
        void correctPasswordSucceeds() {
            User stored = registrationUser("USER");
            stored.setId(10L);
            stored.setPasswordHash(PASSWORD_ENCODER.encode("plain-password"));
            when(userRepository.findByEmail("new-user@example.com")).thenReturn(Optional.of(stored));

            User result = userService.login("new-user@example.com", "plain-password");

            assertSame(stored, result);
        }

        @Test
        @DisplayName("Wrong raw password does not match the stored BCrypt hash")
        void wrongPasswordFails() {
            User stored = registrationUser("USER");
            stored.setId(10L);
            stored.setPasswordHash(PASSWORD_ENCODER.encode("plain-password"));
            when(userRepository.findByEmail("new-user@example.com")).thenReturn(Optional.of(stored));

            User result = userService.login("new-user@example.com", "wrong-password");

            assertNull(result);
        }
    }

    // ========================================================
    // updateBalance (admin balance management)
    // ========================================================
    @Nested
    @DisplayName("updateBalance")
    class UpdateBalance {

        @Test
        @DisplayName("Valid amount updates the balance and saves")
        void validAmount() {
            User u = user(5L, "USER");
            u.setBalance(100.0);
            when(userRepository.findById(5L)).thenReturn(Optional.of(u));
            when(userRepository.save(u)).thenReturn(u);

            User result = userService.updateBalance(5L, 7777.0);

            assertEquals(7777.0, u.getBalance(), DELTA);
            assertSame(u, result);
            verify(userRepository).save(u);
        }

        @Test
        @DisplayName("Zero is a valid balance")
        void zeroAllowed() {
            User u = user(5L, "USER");
            u.setBalance(100.0);
            when(userRepository.findById(5L)).thenReturn(Optional.of(u));
            when(userRepository.save(u)).thenReturn(u);

            userService.updateBalance(5L, 0.0);

            assertEquals(0.0, u.getBalance(), DELTA);
            verify(userRepository).save(u);
        }

        @Test
        @DisplayName("Negative balance is rejected; user not loaded, nothing saved")
        void negativeRejected() {
            assertThrows(IllegalArgumentException.class, () -> userService.updateBalance(5L, -1.0));

            verify(userRepository, never()).findById(any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Null balance is rejected; user not loaded, nothing saved")
        void nullRejected() {
            assertThrows(IllegalArgumentException.class, () -> userService.updateBalance(5L, null));

            verify(userRepository, never()).findById(any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Unknown user is rejected; nothing saved")
        void userNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> userService.updateBalance(99L, 500.0));

            verify(userRepository, never()).save(any());
        }
    }

    // ========================================================
    // isAdmin (role / admin checks)
    // ========================================================
    @Nested
    @DisplayName("isAdmin")
    class IsAdmin {

        @Test
        @DisplayName("ADMIN role returns true")
        void adminTrue() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "ADMIN")));
            assertTrue(userService.isAdmin(1L));
        }

        @Test
        @DisplayName("USER role returns false")
        void userFalse() {
            when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "USER")));
            assertFalse(userService.isAdmin(2L));
        }

        @Test
        @DisplayName("Unknown user returns false")
        void notFoundFalse() {
            when(userRepository.findById(3L)).thenReturn(Optional.empty());
            assertFalse(userService.isAdmin(3L));
        }

        @Test
        @DisplayName("Null id returns false without hitting the repository")
        void nullIdFalse() {
            assertFalse(userService.isAdmin(null));
            verify(userRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Role check is case-insensitive (admin)")
        void caseInsensitive() {
            when(userRepository.findById(4L)).thenReturn(Optional.of(user(4L, "admin")));
            assertTrue(userService.isAdmin(4L));
        }
    }

    // ========================================================
    // applyDailyBonus
    // ========================================================
    @Nested
    @DisplayName("applyDailyBonus")
    class ApplyDailyBonus {

        @Test
        @DisplayName("Eligible USER is granted: uses atomic repo method and returns granted=true + refreshed balance")
        void eligibleUserGranted() {
            User original = user(7L, "USER");
            original.setBalance(0.0);
            User refreshed = user(7L, "USER");
            refreshed.setBalance(1000.0);

            when(userRepository.grantDailyBonus(eq(7L), eq(UserService.DAILY_BONUS_AMOUNT), any(LocalDate.class)))
                    .thenReturn(1);
            when(userRepository.findById(7L)).thenReturn(Optional.of(refreshed));

            UserService.DailyBonusResult result = userService.applyDailyBonus(original);

            assertTrue(result.granted());
            assertEquals(1000.0, result.user().getBalance(), DELTA, "returned user must carry the refreshed balance");
            verify(userRepository).grantDailyBonus(eq(7L), eq(UserService.DAILY_BONUS_AMOUNT), any(LocalDate.class));
        }

        @Test
        @DisplayName("Already-claimed USER (repo updates 0 rows) returns granted=false")
        void alreadyClaimedNotGranted() {
            User u = user(7L, "USER");
            u.setBalance(2000.0);
            when(userRepository.grantDailyBonus(eq(7L), eq(UserService.DAILY_BONUS_AMOUNT), any(LocalDate.class)))
                    .thenReturn(0);
            when(userRepository.findById(7L)).thenReturn(Optional.of(u));

            UserService.DailyBonusResult result = userService.applyDailyBonus(u);

            assertFalse(result.granted());
            assertEquals(2000.0, result.user().getBalance(), DELTA);
        }

        @Test
        @DisplayName("ADMIN never receives the bonus: atomic repo method is NOT called")
        void adminNotGranted() {
            User admin = user(8L, "ADMIN");
            admin.setBalance(1_000_000.0);
            when(userRepository.findById(8L)).thenReturn(Optional.of(admin));

            UserService.DailyBonusResult result = userService.applyDailyBonus(admin);

            assertFalse(result.granted());
            verify(userRepository, never()).grantDailyBonus(anyLong(), anyDouble(), any(LocalDate.class));
        }

        @Test
        @DisplayName("User without an id is not eligible and the repo grant is not attempted")
        void userWithoutIdNotGranted() {
            User transientUser = user(null, "USER");

            UserService.DailyBonusResult result = userService.applyDailyBonus(transientUser);

            assertFalse(result.granted());
            assertSame(transientUser, result.user());
            verify(userRepository, never()).grantDailyBonus(anyLong(), anyDouble(), any(LocalDate.class));
        }
    }

    // ========================================================
    // updateProfileImage
    // ========================================================
    @Nested
    @DisplayName("updateProfileImage")
    class UpdateProfileImage {

        private MultipartFile imageFile(String contentType, long size) {
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getContentType()).thenReturn(contentType);
            when(file.getSize()).thenReturn(size);
            return file;
        }

        @Test
        @DisplayName("Valid PNG is accepted: saved URL/link point at /uploads/profile-images and user is saved")
        void validPng() {
            User u = user(7L, "USER");
            MultipartFile file = imageFile("image/png", 2048);
            when(userRepository.findById(7L)).thenReturn(Optional.of(u));
            when(userRepository.save(u)).thenReturn(u);

            userService.updateProfileImage(7L, file);

            String url = u.getProfileImageUrl();
            assertTrue(url.matches("http://localhost:8080/uploads/profile-images/user-7-[0-9a-fA-F]+\\.png"),
                    "unexpected url: " + url);
            assertEquals(url, u.getProfileImageLink(), "image link must mirror the saved url");
            verify(userRepository).save(u);
        }

        @Test
        @DisplayName("Valid WEBP is accepted with a .webp extension")
        void validWebp() {
            User u = user(7L, "USER");
            MultipartFile file = imageFile("image/webp", 2048);
            when(userRepository.findById(7L)).thenReturn(Optional.of(u));
            when(userRepository.save(u)).thenReturn(u);

            userService.updateProfileImage(7L, file);

            assertTrue(u.getProfileImageUrl().endsWith(".webp"), u.getProfileImageUrl());
            verify(userRepository).save(u);
        }

        @Test
        @DisplayName("Valid JPEG is accepted with a .jpg extension")
        void validJpeg() {
            User u = user(7L, "USER");
            MultipartFile file = imageFile("image/jpeg", 2048);
            when(userRepository.findById(7L)).thenReturn(Optional.of(u));
            when(userRepository.save(u)).thenReturn(u);

            userService.updateProfileImage(7L, file);

            assertTrue(u.getProfileImageUrl().endsWith(".jpg"), u.getProfileImageUrl());
            verify(userRepository).save(u);
        }

        @Test
        @DisplayName("Invalid file type is rejected; user not loaded, nothing saved (existing image untouched)")
        void invalidTypeRejected() {
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getContentType()).thenReturn("text/plain");

            assertThrows(IllegalArgumentException.class, () -> userService.updateProfileImage(7L, file));

            verify(userRepository, never()).findById(any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("File larger than 5MB is rejected; nothing saved")
        void oversizeRejected() {
            MultipartFile file = imageFile("image/png", 5L * 1024 * 1024 + 1);

            assertThrows(IllegalArgumentException.class, () -> userService.updateProfileImage(7L, file));

            verify(userRepository, never()).findById(any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Empty file is rejected; nothing saved")
        void emptyRejected() {
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(true);

            assertThrows(IllegalArgumentException.class, () -> userService.updateProfileImage(7L, file));

            verify(userRepository, never()).findById(any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Null file is rejected; nothing saved")
        void nullRejected() {
            assertThrows(IllegalArgumentException.class, () -> userService.updateProfileImage(7L, null));

            verify(userRepository, never()).findById(any());
            verify(userRepository, never()).save(any());
        }
    }
}
