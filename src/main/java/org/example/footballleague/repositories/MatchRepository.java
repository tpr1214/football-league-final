package org.example.footballleague.repositories;

import org.example.footballleague.model.Match;
import org.example.footballleague.model.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    List<Match> findByRoundNumberAndStatus(int roundNumber, MatchStatus status);

    List<Match> findByStatus(MatchStatus status);

    long countByOwnerId(Long ownerId);

    List<Match> findByOwnerIdAndCycleNumberOrderByRoundNumberAscIdAsc(Long ownerId, int cycleNumber);

    List<Match> findByOwnerIdAndCycleNumberAndStatus(Long ownerId, int cycleNumber, MatchStatus status);

    List<Match> findByOwnerIdAndCycleNumberAndRoundNumberAndStatus(
            Long ownerId,
            int cycleNumber,
            int roundNumber,
            MatchStatus status
    );

    @Query("select coalesce(max(m.cycleNumber), 0) from Match m where m.owner.id = :ownerId")
    int findMaxCycleNumberByOwnerId(@Param("ownerId") Long ownerId);
}
