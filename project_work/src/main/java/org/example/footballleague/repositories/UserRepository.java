package org.example.footballleague.repositories;

import org.example.footballleague.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Repository
public interface UserRepository extends JpaRepository<User,Long> {// שימוש במקום class השתמשתי בinterface זו בקשה למחלקה שכבר קיימת בספריה Spring Boot שקיימים שם כבר הפונקציות של הDB


        Optional<User> findByEmail(String email);
}
