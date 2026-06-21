package org.example.footballleague.Service;

import org.example.footballleague.model.User;
import org.example.footballleague.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean register(User user) {
        System.out.println("🔍 Checking if email exists: " + user.getEmail());

        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            System.out.println("❌ Email already exists!");
            return false;
        }

        user.setBalance(1000.0);

        System.out.println("💾 Saving new user: " + user.getUsername() + " | " + user.getEmail());
        User savedUser = userRepository.save(user);
        System.out.println("✅ User saved successfully! ID = " + savedUser.getId());

        return true;
    }

    public User login(String email, String password) {
        System.out.println("🔍 Attempting login for email: " + email);

        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getPasswordHash().equals(password)) {
                System.out.println("✅ Login successful for user ID: " + user.getId());
                return user;
            }
        }

        System.out.println("❌ Login failed: Invalid email or password");
        return null;
    }

    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public User updateProfile(Long id, String username, String profileImageUrl) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("משתמש לא נמצא"));

        if (username != null && !username.isBlank()) {
            user.setUsername(username.trim());
        }

        user.setProfileImageUrl(profileImageUrl);

        return userRepository.save(user);
    }
}
