package org.example.footballleague.repositories;

import org.example.footballleague.model.Bet;
import org.example.footballleague.model.Match; // הוספנו את הייבוא הזה
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BetRepository extends JpaRepository<Bet, Long> {


    List<Bet> findByMatch(Match match);

    List<Bet> findByUserId(Long userId);
}
