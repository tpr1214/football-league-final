package org.example.footballleague.repositories;

import org.example.footballleague.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
@Repository
public interface UserRepository extends JpaRepository<User,Long> {// שימוש במקום class השתמשתי בinterface זו בקשה למחלקה שכבר קיימת בספריה Spring Boot שקיימים שם כבר הפונקציות של הDB


        Optional<User> findByEmail(String email);

        // Atomic, idempotent daily-bonus grant. The WHERE clause guarantees the
        // bonus is added at most once per day even under concurrent requests:
        // only the first call (where the stored date is null or before today)
        // updates a row; subsequent calls affect 0 rows. Admins are excluded.
        @Modifying(clearAutomatically = true)
        @Query("UPDATE User u SET u.balance = u.balance + :amount, u.lastDailyBonusDate = :today " +
               "WHERE u.id = :id AND u.role <> 'ADMIN' " +
               "AND (u.lastDailyBonusDate IS NULL OR u.lastDailyBonusDate < :today)")
        int grantDailyBonus(@Param("id") Long id,
                            @Param("amount") double amount,
                            @Param("today") LocalDate today);
}
