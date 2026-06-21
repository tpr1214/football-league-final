package org.example.footballleague.controller;

import org.example.footballleague.Service.UserService;
import org.example.footballleague.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
        return ResponseEntity.ok(loggedInUser);
    }

    @GetMapping("/profile/{id}")
    public ResponseEntity<?> getUserProfile(@PathVariable Long id) {
        Optional<User> userOpt = userService.getUserById(id);

        if (userOpt.isPresent()) {
            return ResponseEntity.ok(userOpt.get());
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("משתמש לא נמצא");
    }

    @PutMapping("/profile/{id}")
    public ResponseEntity<User> updateUserProfile(@PathVariable Long id, @RequestBody UpdateProfileRequest request) {
        User updatedUser = userService.updateProfile(id, request.username(), request.profileImageUrl());
        return ResponseEntity.ok(updatedUser);
    }

    public record UpdateProfileRequest(String username, String profileImageUrl) {
    }
}
