package org.example.footballleague.repositories;

import org.example.footballleague.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findByOwnerId(Long ownerId);

}
