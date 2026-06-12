package org.example.footballleague.repositories;

import org.example.footballleague.model.Match;
import org.example.footballleague.model.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    List<Match> findByRoundNumberAndStatus(int roundNumber, MatchStatus status);

    List<Match> findByStatus(MatchStatus status);
}
