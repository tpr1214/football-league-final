package org.example.footballleague.Service;

import org.example.footballleague.model.Match;
import org.example.footballleague.model.MatchStatus;
import org.example.footballleague.model.Team;
import org.example.footballleague.repositories.MatchRepository;
import org.example.footballleague.repositories.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
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


    @Transactional
    public List<Match> startNextRound() {
        // On a fresh database (e.g. production after deploy) there are no teams or
        // matches yet. Bootstrap them on the first click so any user can simply
        // press "start round" and have the league come to life, with no separate
        // admin/seed step.
        ensureLeagueInitialized();

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

    /**
     * Ensures the league has teams and a full fixture list. Idempotent: returns
     * immediately once a schedule exists, so repeated clicks never duplicate data.
     */
    private void ensureLeagueInitialized() {
        if (matchRepository.count() > 0) {
            return;
        }

        List<Team> teams = teamRepository.findAll();
        if (teams.isEmpty()) {
            teams = teamRepository.saveAll(createDefaultTeams());
        }

        generateLeagueSchedule(teams);
    }

    private List<Team> createDefaultTeams() {
        return Arrays.asList(
                createTeam("מכבי חיפה", 89),
                createTeam("הפועל באר שבע", 91),
                createTeam("מכבי תל אביב", 90),
                createTeam("ביתר ירושלים", 92),
                createTeam("הפועל פתח תקווה", 85),
                createTeam("מכבי נתניה", 87),
                createTeam("הפועל תל אביב", 85),
                createTeam("עירוני קרית שמונה", 82)
        );
    }

    private Team createTeam(String name, int skillLevel) {
        Team team = new Team();
        team.setName(name);
        team.setSkillLevel(skillLevel);
        team.setPoints(0);
        team.setGoalsFor(0);
        team.setGoalsAgainst(0);
        return team;
    }
}
