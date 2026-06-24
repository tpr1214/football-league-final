package org.example.footballleague.controller;

import org.example.footballleague.Service.UserService;
import org.example.footballleague.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    // Authorization is enforced by Spring Security (ROLE_ADMIN on /api/admin/**),
    // derived from the JWT — no longer from the spoofable X-User-Id header.
    @GetMapping("/users")
    public ResponseEntity<?> listUsers() {
        List<AdminUserResponse> users = userService.getAllUsers().stream()
                .map(AdminUserResponse::from)
                .toList();
        return ResponseEntity.ok(users);
    }

    @PutMapping("/users/{id}/balance")
    public ResponseEntity<?> updateBalance(@PathVariable Long id,
                                           @RequestBody UpdateBalanceRequest request) {
        User updated = userService.updateBalance(id, request.balance());
        return ResponseEntity.ok(AdminUserResponse.from(updated));
    }

    public record UpdateBalanceRequest(Double balance) {
    }

    public record AdminUserResponse(Long id, String username, String email, Double balance, String role) {
        public static AdminUserResponse from(User user) {
            return new AdminUserResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getBalance(),
                    user.getRole()
            );
        }
    }
}
