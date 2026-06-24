package org.example.footballleague.Service;

import org.example.footballleague.model.User;
import org.example.footballleague.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class UserService {

    public static final double DAILY_BONUS_AMOUNT = 1000.0;

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Map<String, String> EXTENSION_BY_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );
    private static final long MAX_IMAGE_SIZE_BYTES = 5L * 1024 * 1024; // 5MB

    private final UserRepository userRepository;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

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
        user.setRole("USER");
        user.setPasswordHash(PASSWORD_ENCODER.encode(user.getPasswordHash()));

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
            if (password != null && user.getPasswordHash() != null && PASSWORD_ENCODER.matches(password, user.getPasswordHash())) {
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

    /**
     * Grants the once-per-day bonus to a regular user if they haven't received
     * today's yet. The actual grant is an atomic, conditional DB update so it
     * cannot be exploited by repeated/concurrent calls. Admins are excluded.
     * Returns the (reloaded) user plus whether a bonus was granted on this call.
     */
    public DailyBonusResult applyDailyBonus(User user) {
        boolean granted = false;

        if (user != null && user.getId() != null && !"ADMIN".equalsIgnoreCase(user.getRole())) {
            int rowsUpdated = userRepository.grantDailyBonus(user.getId(), DAILY_BONUS_AMOUNT, LocalDate.now());
            granted = rowsUpdated == 1;
        }

        User refreshedUser = (user != null && user.getId() != null)
                ? userRepository.findById(user.getId()).orElse(user)
                : user;

        return new DailyBonusResult(refreshedUser, granted);
    }

    public record DailyBonusResult(User user, boolean granted) {
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        return userRepository.findById(userId)
                .map(user -> "ADMIN".equalsIgnoreCase(user.getRole()))
                .orElse(false);
    }

    public User updateBalance(Long id, Double newBalance) {
        if (newBalance == null) {
            throw new IllegalArgumentException("יש להזין יתרה");
        }
        if (newBalance < 0) {
            throw new IllegalArgumentException("היתרה לא יכולה להיות שלילית");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("משתמש לא נמצא"));

        user.setBalance(newBalance);
        return userRepository.save(user);
    }

    public User updateProfile(Long id, String username, String profileImageUrl, String profileLink) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("משתמש לא נמצא"));

        if (username != null && !username.isBlank()) {
            user.setUsername(username.trim());
        }

        user.setProfileImageUrl(cleanOptionalUrl(profileImageUrl));
        user.setProfileImageLink(cleanOptionalUrl(profileImageUrl));
        user.setProfileLink(profileLink);

        return userRepository.save(user);
    }

    public User updateProfileImage(Long id, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("לא נבחר קובץ תמונה להעלאה");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("סוג קובץ לא נתמך. יש להעלות תמונה מסוג JPG, JPEG, PNG או WEBP בלבד");
        }

        if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new IllegalArgumentException("הקובץ גדול מדי. הגודל המרבי המותר הוא 5MB");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("משתמש לא נמצא"));

        String extension = EXTENSION_BY_TYPE.getOrDefault(contentType.toLowerCase(), ".jpg");
        String fileName = "user-" + id + "-" + UUID.randomUUID().toString().replace("-", "") + extension;

        try {
            Path targetDir = Paths.get(uploadDir, "profile-images").toAbsolutePath().normalize();
            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve(fileName);
            file.transferTo(targetFile);
        } catch (IOException exception) {
            throw new IllegalStateException("העלאת התמונה נכשלה, אנא נסה שוב");
        }

        String publicUrl = baseUrl + "/uploads/profile-images/" + fileName;
        user.setProfileImageUrl(publicUrl);
        user.setProfileImageLink(publicUrl);

        return userRepository.save(user);
    }

    private String cleanOptionalUrl(String value) {
        if (value == null) {
            return null;
        }

        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
    }
}
