package org.example.footballleague.Service;

import org.example.footballleague.model.Match;
import org.example.footballleague.model.MatchStatus;
import org.example.footballleague.model.Team;
import org.example.footballleague.model.User;
import org.example.footballleague.repositories.MatchRepository;
import org.example.footballleague.repositories.TeamRepository;
import org.example.footballleague.repositories.UserRepository;
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
    private final UserRepository userRepository;


    public LeagueService(TeamRepository teamRepository,
                         MatchRepository matchRepository,
                         SimulationEngine simulationEngine,
                         UserRepository userRepository) {
        this.teamRepository = teamRepository;
        this.matchRepository = matchRepository;
        this.simulationEngine = simulationEngine;
        this.userRepository = userRepository;
    }


    public List<Match> generateLeagueSchedule(List<Team> teams) {
        return matchRepository.saveAll(buildRoundRobin(teams, 1, null));
    }

    public List<Match> generateLeagueSchedule(List<Team> teams, User owner, int cycleNumber) {
        return matchRepository.saveAll(buildRoundRobin(teams, cycleNumber, owner));
    }

    /**
     * Builds a single round-robin set of fixtures (unsaved). Every cycle starts
     * at rounds 1..N-1; cycleNumber separates old completed cycles from the
     * user's current cycle without leaking progress between users.
     */
    private List<Match> buildRoundRobin(List<Team> teams, int cycleNumber, User owner) {
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
                match.setOwner(owner);
                match.setHomeTeam(home);
                match.setAwayTeam(away);
                match.setCycleNumber(cycleNumber);
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


        return allMatches;
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

    @Transactional
    public List<Match> getAllMatches(Long userId) {
        ensureLeagueInitialized(userId);
        int currentCycle = getCurrentCycleNumber(userId);
        return matchRepository.findByOwnerIdAndCycleNumberOrderByRoundNumberAscIdAsc(userId, currentCycle);
    }


    public List<Team> getLeagueTable() {
        List<Team> teams = teamRepository.findAll();
        sortLeagueTable(teams);
        return teams;
    }

    @Transactional
    public List<Team> getLeagueTable(Long userId) {
        ensureLeagueInitialized(userId);
        List<Team> teams = teamRepository.findByOwnerId(userId);
        sortLeagueTable(teams);
        return teams;
    }

    private void sortLeagueTable(List<Team> teams) {

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

    @Transactional
    public List<Match> startNextRound(Long userId) {
        ensureLeagueInitialized(userId);
        int currentCycle = getCurrentCycleNumber(userId);

        boolean roundAlreadyLive = !matchRepository
                .findByOwnerIdAndCycleNumberAndStatus(userId, currentCycle, MatchStatus.LIVE)
                .isEmpty();
        if (roundAlreadyLive) {
            throw new IllegalStateException("יש כבר מחזור שרץ כרגע");
        }

        int nextRoundNumber = matchRepository
                .findByOwnerIdAndCycleNumberAndStatus(userId, currentCycle, MatchStatus.PENDING)
                .stream()
                .mapToInt(Match::getRoundNumber)
                .min()
                .orElseThrow(() -> new IllegalStateException("אין מחזורים נוספים להרצה"));

        List<Match> nextRoundMatches = matchRepository.findByOwnerIdAndCycleNumberAndRoundNumberAndStatus(
                userId,
                currentCycle,
                nextRoundNumber,
                MatchStatus.PENDING
        );

        simulationEngine.runNextRound(nextRoundMatches, userId);
        return nextRoundMatches;
    }

    /**
     * Legacy global cycle regeneration. User-owned leagues should call
     * {@link #regenerateSchedule(Long)} so every user gets an isolated cycle.
     */
    @Transactional
    public List<Match> regenerateSchedule() {
        if (!matchRepository.findByStatus(MatchStatus.LIVE).isEmpty()) {
            throw new IllegalStateException("יש מחזור שרץ כרגע, יש להמתין לסיומו לפני יצירת מחזורים חדשים");
        }
        if (!matchRepository.findByStatus(MatchStatus.PENDING).isEmpty()) {
            throw new IllegalStateException("עדיין יש מחזורים שלא שוחקו. סיים אותם לפני יצירת מחזורים חדשים");
        }

        List<Team> teams = teamRepository.findAll();
        if (teams.isEmpty()) {
            teams = teamRepository.saveAll(createDefaultTeams());
        }

        int nextCycle = matchRepository.findAll().stream()
                .mapToInt(Match::getCycleNumber)
                .max()
                .orElse(0) + 1;

        return matchRepository.saveAll(buildRoundRobin(teams, nextCycle, null));
    }

    @Transactional
    public List<Match> regenerateSchedule(Long userId) {
        ensureLeagueInitialized(userId);
        int currentCycle = getCurrentCycleNumber(userId);

        if (!matchRepository.findByOwnerIdAndCycleNumberAndStatus(userId, currentCycle, MatchStatus.LIVE).isEmpty()) {
            throw new IllegalStateException("יש מחזור שרץ כרגע, יש להמתין לסיומו לפני יצירת מחזורים חדשים");
        }
        if (!matchRepository.findByOwnerIdAndCycleNumberAndStatus(userId, currentCycle, MatchStatus.PENDING).isEmpty()) {
            throw new IllegalStateException("עדיין יש מחזורים שלא שוחקו. סיים אותם לפני יצירת מחזורים חדשים");
        }

        List<Team> teams = teamRepository.findByOwnerId(userId);
        resetLeagueTable(teams);
        teamRepository.saveAll(teams);

        return matchRepository.saveAll(buildRoundRobin(teams, currentCycle + 1, requireUser(userId)));
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

    private void ensureLeagueInitialized(Long userId) {
        if (matchRepository.countByOwnerId(userId) > 0) {
            return;
        }

        User owner = requireUser(userId);
        List<Team> teams = teamRepository.findByOwnerId(userId);
        if (teams.isEmpty()) {
            teams = teamRepository.saveAll(createDefaultTeams(owner));
        }

        generateLeagueSchedule(teams, owner, 1);
    }

    private int getCurrentCycleNumber(Long userId) {
        int currentCycle = matchRepository.findMaxCycleNumberByOwnerId(userId);
        if (currentCycle <= 0) {
            ensureLeagueInitialized(userId);
            currentCycle = matchRepository.findMaxCycleNumberByOwnerId(userId);
        }
        return currentCycle;
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
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

    private List<Team> createDefaultTeams(User owner) {
        List<Team> teams = createDefaultTeams();
        teams.forEach(team -> team.setOwner(owner));
        return teams;
    }

    private void resetLeagueTable(List<Team> teams) {
        for (Team team : teams) {
            team.setPoints(0);
            team.setGoalsFor(0);
            team.setGoalsAgainst(0);
        }
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
