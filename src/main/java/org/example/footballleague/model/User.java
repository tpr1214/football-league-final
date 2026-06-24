package org.example.footballleague.model;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;


    private String passwordHash;

    private Double balance;

    @Column(nullable = false, columnDefinition = "VARCHAR(50) DEFAULT 'USER'")
    private String role = "USER";

    @Column(name = "last_daily_bonus_date")
    private LocalDate lastDailyBonusDate;

    @Column(length = 2048)
    private String profileImageUrl;

    @Column(length = 2048)
    private String profileImageLink;

    @Column(length = 2048)
    private String profileLink;

    public User() {
    }

    public User(Long id, String username, String email, String passwordHash, Double balance) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.balance = balance;

    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }


    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Double getBalance() { return balance; }
    public void setBalance(Double balance) { this.balance = balance; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public LocalDate getLastDailyBonusDate() { return lastDailyBonusDate; }
    public void setLastDailyBonusDate(LocalDate lastDailyBonusDate) { this.lastDailyBonusDate = lastDailyBonusDate; }

    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }

    public String getProfileImageLink() {
        return profileImageLink != null && !profileImageLink.isBlank() ? profileImageLink : profileImageUrl;
    }
    public void setProfileImageLink(String profileImageLink) { this.profileImageLink = profileImageLink; }

    public String getProfileLink() { return profileLink; }
    public void setProfileLink(String profileLink) { this.profileLink = profileLink; }
}
