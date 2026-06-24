package org.example.footballleague.controller;

import org.example.footballleague.Service.UserService;
import org.example.footballleague.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174"}, allowCredentials = "true")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody User user) {
        boolean isSuccess = userService.register(user);

        if (!isSuccess) {
            return ResponseEntity.badRequest().body("כתובת האימייל הזו כבר רשומה במערכת!");
        }

        return ResponseEntity.ok("המשתמש נרשם בהצלחה!");
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody User loginRequest) {
        User loggedInUser = userService.login(loginRequest.getEmail(), loginRequest.getPasswordHash());

        if (loggedInUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("אימייל או סיסמה שגויים!");
        }

        UserService.DailyBonusResult bonus = userService.applyDailyBonus(loggedInUser);
        return ResponseEntity.ok(UserResponse.from(bonus.user(), bonus.granted()));
    }

    @GetMapping("/profile/{id}")
    public ResponseEntity<?> getUserProfile(@PathVariable Long id) {
        Optional<User> userOpt = userService.getUserById(id);

        if (userOpt.isPresent()) {
            // Validating the current user is also a natural daily-bonus checkpoint
            // (covers refresh / reopen / session restore). Idempotent per day.
            UserService.DailyBonusResult bonus = userService.applyDailyBonus(userOpt.get());
            return ResponseEntity.ok(UserResponse.from(bonus.user(), bonus.granted()));
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("משתמש לא נמצא");
    }

    @PutMapping("/profile/{id}")
    public ResponseEntity<UserResponse> updateUserProfile(@PathVariable Long id, @RequestBody UpdateProfileRequest request) {
        User updatedUser = userService.updateProfile(id, request.username(), request.profileImageUrl(), request.profileLink());
        return ResponseEntity.ok(UserResponse.from(updatedUser));
    }

    @PostMapping("/profile/{id}/image")
    public ResponseEntity<UserResponse> uploadProfileImage(@PathVariable Long id,
                                                           @RequestParam("file") MultipartFile file) {
        User updatedUser = userService.updateProfileImage(id, file);
        return ResponseEntity.ok(UserResponse.from(updatedUser));
    }

    public record UpdateProfileRequest(String username, String profileImageUrl, String profileLink) {
    }

    public record UserResponse(Long id, String username, String email, Double balance, String role,
                               String profileImageUrl, String profileLink, boolean dailyBonusGranted) {
        public static UserResponse from(User user) {
            return from(user, false);
        }

        public static UserResponse from(User user, boolean dailyBonusGranted) {
            return new UserResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getBalance(),
                    user.getRole(),
                    user.getProfileImageUrl(),
                    user.getProfileLink(),
                    dailyBonusGranted
            );
        }
    }
}
