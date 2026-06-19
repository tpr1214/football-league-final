package org.example.footballleague.Service;

import org.example.footballleague.model.Match;
import org.example.footballleague.model.MatchStatus;
import org.example.footballleague.model.Team;
import org.example.footballleague.repositories.MatchRepository;
import org.example.footballleague.repositories.TeamRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LeagueService {

    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final SimulationEngine simulationEngine;


    public LeagueService(TeamRepository teamRepository,
                         MatchRepository matchRepository,
                         SimulationEngine simulationEngine) {
        this.teamRepository = teamRepository;
        this.matchRepository = matchRepository;
        this.simulationEngine = simulationEngine;
    }


    public List<Match> generateLeagueSchedule(List<Team> teams) {
        List<Team> rotationList = new ArrayList<>(teams);
        int totalTeams = rotationList.size();

        if (totalTeams % 2 != 0) {
            throw new IllegalArgumentException("מספר הקבוצות חייב להיות זוגי כדי לייצר לוח משחקים.");
        }

        int totalRounds = totalTeams - 1;
        int matchesPerRound = totalTeams / 2;

        List<Match> allMatches = new ArrayList<>();

        for (int round = 1; round <= totalRounds; round++) {
            for (int i = 0; i < matchesPerRound; i++) {
                Team home = rotationList.get(i);
                Team away = rotationList.get(totalTeams - 1 - i);

                Match match = new Match();
                match.setHomeTeam(home);
                match.setAwayTeam(away);
                match.setRoundNumber(round);
                match.setStatus(MatchStatus.PENDING);
                match.setHomeScore(0);
                match.setAwayScore(0);


                calculateExpectedScores(match, home, away);

                allMatches.add(match);
            }


            Team lastTeam = rotationList.remove(totalTeams - 1);
            rotationList.add(1, lastTeam);
        }


        return matchRepository.saveAll(allMatches);
    }


    private void calculateExpectedScores(Match match, Team home, Team away) {

        double baseHome = Math.random() * 2;
        double baseAway = Math.random() * 2;


        if (home.getSkillLevel() > away.getSkillLevel()) {
            baseHome += 1.0;
        } else if (away.getSkillLevel() > home.getSkillLevel()) {
            baseAway += 1.0;
        }


        match.setExpectedHomeScore((int) Math.round(baseHome));
        match.setExpectedAwayScore((int) Math.round(baseAway));
    }


    public List<Match> getAllMatches() {
        return matchRepository.findAll();
    }


    public List<Team> getLeagueTable() {
        List<Team> teams = teamRepository.findAll();


        teams.sort((t1, t2) -> {

            if (t1.getPoints() != t2.getPoints()) {
                return Integer.compare(t2.getPoints(), t1.getPoints());
            }


            int diff1 = t1.getGoalsFor() - t1.getGoalsAgainst();
            int diff2 = t2.getGoalsFor() - t2.getGoalsAgainst();
            if (diff1 != diff2) {
                return Integer.compare(diff2, diff1);
            }


            return t1.getName().compareTo(t2.getName());
        });

        return teams;
    }


    public List<Match> startNextRound() {
        boolean roundAlreadyLive = !matchRepository.findByStatus(MatchStatus.LIVE).isEmpty();
        if (roundAlreadyLive) {
            throw new IllegalStateException("יש כבר מחזור שרץ כרגע");
        }

        int nextRoundNumber = matchRepository.findByStatus(MatchStatus.PENDING).stream()
                .mapToInt(Match::getRoundNumber)
                .min()
                .orElseThrow(() -> new IllegalStateException("אין מחזורים נוספים להרצה"));

        List<Match> nextRoundMatches = matchRepository.findByRoundNumberAndStatus(
                nextRoundNumber,
                MatchStatus.PENDING
        );

        simulationEngine.runNextRound(nextRoundMatches);
        return nextRoundMatches;
    }
}
