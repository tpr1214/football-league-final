package org.example.footballleague.Service;

import org.example.footballleague.model.Match;
import org.example.footballleague.model.MatchStatus;
import org.example.footballleague.model.Team;
import org.example.footballleague.model.User;
import org.example.footballleague.repositories.MatchRepository;
import org.example.footballleague.repositories.TeamRepository;
import org.example.footballleague.repositories.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link LeagueService}. Repositories and the
 * {@link SimulationEngine} are mocked; no Spring context and no database.
 */
@ExtendWith(MockitoExtension.class)
class LeagueServiceTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private SimulationEngine simulationEngine;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private LeagueService leagueService;

    // ---------- helpers ----------

    private Team team(String name, int skill) {
        Team t = new Team();
        t.setName(name);
        t.setSkillLevel(skill);
        return t;
    }

    private Team tableTeam(String name, int points, int goalsFor, int goalsAgainst) {
        Team t = new Team();
        t.setName(name);
        t.setPoints(points);
        t.setGoalsFor(goalsFor);
        t.setGoalsAgainst(goalsAgainst);
        return t;
    }

    private List<Team> teams(int n) {
        List<Team> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(team("Team-" + (char) ('A' + i), 80 + i));
        }
        return list;
    }

    private Match roundMatch(int roundNumber) {
        Match m = new Match();
        m.setRoundNumber(roundNumber);
        m.setStatus(MatchStatus.PENDING);
        return m;
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setEmail("user-" + id + "@example.com");
        return user;
    }

    private String pairKey(Match m) {
        String a = m.getHomeTeam().getName();
        String b = m.getAwayTeam().getName();
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    // ========================================================
    // generateLeagueSchedule (round-robin)
    // ========================================================
    @Nested
    @DisplayName("generateLeagueSchedule")
    class GenerateSchedule {

        private void stubSaveAllEcho() {
            when(matchRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("Generates (N-1)*(N/2) matches for an even number of teams")
        void correctNumberOfMatches() {
            stubSaveAllEcho();
            assertEquals(6, leagueService.generateLeagueSchedule(teams(4)).size());  // 3 rounds * 2
        }

        @Test
        @DisplayName("Generates the correct count for 6 teams (15 matches)")
        void correctNumberForSixTeams() {
            stubSaveAllEcho();
            assertEquals(15, leagueService.generateLeagueSchedule(teams(6)).size()); // 5 rounds * 3
        }

        @Test
        @DisplayName("Creates the right rounds: N-1 rounds, each with N/2 matches and no team twice in a round")
        void roundsCreatedCorrectly() {
            stubSaveAllEcho();
            int n = 4;
            List<Match> matches = leagueService.generateLeagueSchedule(teams(n));

            Map<Integer, List<Match>> byRound = matches.stream()
                    .collect(Collectors.groupingBy(Match::getRoundNumber));

            assertEquals(n - 1, byRound.size(), "there must be N-1 rounds");
            for (Map.Entry<Integer, List<Match>> e : byRound.entrySet()) {
                assertEquals(n / 2, e.getValue().size(), "each round has N/2 matches");
                Set<String> teamsInRound = new HashSet<>();
                for (Match m : e.getValue()) {
                    teamsInRound.add(m.getHomeTeam().getName());
                    teamsInRound.add(m.getAwayTeam().getName());
                }
                assertEquals(n, teamsInRound.size(), "every team plays exactly once per round");
            }
        }

        @Test
        @DisplayName("Does not create invalid (self) or duplicate matches; every pair plays exactly once")
        void noInvalidOrDuplicateMatches() {
            stubSaveAllEcho();
            int n = 6;
            List<Match> matches = leagueService.generateLeagueSchedule(teams(n));

            Set<String> pairs = new HashSet<>();
            for (Match m : matches) {
                assertFalse(m.getHomeTeam().getName().equals(m.getAwayTeam().getName()),
                        "a team must not play itself");
                pairs.add(pairKey(m));
            }
            assertEquals(matches.size(), pairs.size(), "no duplicate fixtures");
            assertEquals(n * (n - 1) / 2, pairs.size(), "every unordered pair appears exactly once");
        }

        @Test
        @DisplayName("Generated matches are PENDING with 0-0 score and a valid round number")
        void matchesHaveValidInitialState() {
            stubSaveAllEcho();
            int n = 4;
            List<Match> matches = leagueService.generateLeagueSchedule(teams(n));
            for (Match m : matches) {
                assertEquals(MatchStatus.PENDING, m.getStatus());
                assertEquals(0, m.getHomeScore());
                assertEquals(0, m.getAwayScore());
                org.junit.jupiter.api.Assertions.assertTrue(
                        m.getRoundNumber() >= 1 && m.getRoundNumber() <= n - 1);
            }
        }

        @Test
        @DisplayName("Persists the generated schedule via saveAll and returns the saved result")
        void savesGeneratedMatches() {
            List<Match> saved = List.of(new Match(), new Match());
            when(matchRepository.saveAll(anyList())).thenReturn(saved);

            List<Match> result = leagueService.generateLeagueSchedule(teams(4));

            assertSame(saved, result);
            verify(matchRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("Rejects an odd number of teams and does not persist anything")
        void oddNumberOfTeamsRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> leagueService.generateLeagueSchedule(teams(3)));
            verify(matchRepository, never()).saveAll(anyList());
        }
    }

    // ========================================================
    // getLeagueTable (sorting)
    // ========================================================
    @Nested
    @DisplayName("getLeagueTable")
    class GetLeagueTable {

        private List<String> names(List<Team> table) {
            return table.stream().map(Team::getName).collect(Collectors.toList());
        }

        @Test
        @DisplayName("Sorts by points descending")
        void sortsByPointsDescending() {
            when(teamRepository.findAll()).thenReturn(new ArrayList<>(List.of(
                    tableTeam("Low", 5, 0, 0),
                    tableTeam("High", 10, 0, 0),
                    tableTeam("Mid", 7, 0, 0)
            )));

            assertEquals(List.of("High", "Mid", "Low"), names(leagueService.getLeagueTable()));
        }

        @Test
        @DisplayName("Equal points: sorts by goal difference descending")
        void tieBreakByGoalDifference() {
            when(teamRepository.findAll()).thenReturn(new ArrayList<>(List.of(
                    tableTeam("SmallDiff", 10, 5, 2),   // diff +3
                    tableTeam("BigDiff", 10, 9, 1)      // diff +8
            )));

            assertEquals(List.of("BigDiff", "SmallDiff"), names(leagueService.getLeagueTable()));
        }

        @Test
        @DisplayName("Equal points and goal difference: sorts alphabetically by name")
        void tieBreakByName() {
            when(teamRepository.findAll()).thenReturn(new ArrayList<>(List.of(
                    tableTeam("Zeta", 10, 4, 2),   // diff +2
                    tableTeam("Alpha", 10, 5, 3)   // diff +2
            )));

            assertEquals(List.of("Alpha", "Zeta"), names(leagueService.getLeagueTable()));
        }

        @Test
        @DisplayName("Applies all rules together (points > goal diff > name)")
        void combinedOrdering() {
            when(teamRepository.findAll()).thenReturn(new ArrayList<>(List.of(
                    tableTeam("Bravo", 6, 10, 5),   // 6 pts, diff +5
                    tableTeam("Alpha", 9, 3, 3),    // 9 pts
                    tableTeam("Charlie", 6, 7, 5),  // 6 pts, diff +2
                    tableTeam("Delta", 6, 8, 3)     // 6 pts, diff +5  -> tie with Bravo by diff, name first
            )));

            assertEquals(List.of("Alpha", "Bravo", "Delta", "Charlie"),
                    names(leagueService.getLeagueTable()));
        }
    }

    // ========================================================
    // startNextRound
    // ========================================================
    @Nested
    @DisplayName("startNextRound")
    class StartNextRound {

        @Test
        @DisplayName("Starts the lowest pending round and triggers the simulation engine once")
        void startsNextPendingRound() {
            when(matchRepository.count()).thenReturn(28L); // schedule already exists -> no bootstrap
            when(matchRepository.findByStatus(MatchStatus.LIVE)).thenReturn(List.of());
            when(matchRepository.findByStatus(MatchStatus.PENDING))
                    .thenReturn(List.of(roundMatch(3), roundMatch(2), roundMatch(3), roundMatch(2)));
            List<Match> round2 = List.of(roundMatch(2), roundMatch(2));
            when(matchRepository.findByRoundNumberAndStatus(2, MatchStatus.PENDING)).thenReturn(round2);

            List<Match> result = leagueService.startNextRound();

            assertSame(round2, result, "returns the matches of the next round (round 2)");
            verify(matchRepository).findByRoundNumberAndStatus(2, MatchStatus.PENDING);
            verify(simulationEngine).runNextRound(round2);
        }

        @Test
        @DisplayName("Does NOT start a new round if a LIVE round already exists")
        void rejectsWhenRoundAlreadyLive() {
            when(matchRepository.count()).thenReturn(28L); // schedule already exists -> no bootstrap
            when(matchRepository.findByStatus(MatchStatus.LIVE)).thenReturn(List.of(roundMatch(1)));

            assertThrows(IllegalStateException.class, () -> leagueService.startNextRound());

            verify(matchRepository, never()).findByStatus(MatchStatus.PENDING);
            verify(simulationEngine, never()).runNextRound(any());
        }

        @Test
        @DisplayName("Throws when there are no pending rounds and never calls the simulation engine")
        void noPendingRounds() {
            when(matchRepository.count()).thenReturn(28L); // schedule already exists -> no bootstrap
            when(matchRepository.findByStatus(MatchStatus.LIVE)).thenReturn(List.of());
            when(matchRepository.findByStatus(MatchStatus.PENDING)).thenReturn(List.of());

            assertThrows(IllegalStateException.class, () -> leagueService.startNextRound());

            verify(matchRepository, never()).findByRoundNumberAndStatus(eq(0), any());
            verify(simulationEngine, never()).runNextRound(any());
        }

        @Test
        @DisplayName("Creates a fresh round-robin when nothing is pending or live")
        void regenerateAppendsNewRounds() {
            when(matchRepository.findByStatus(MatchStatus.LIVE)).thenReturn(List.of());
            when(matchRepository.findByStatus(MatchStatus.PENDING)).thenReturn(List.of());
            when(teamRepository.findAll()).thenReturn(teams(8));
            when(matchRepository.findAll()).thenReturn(List.of(roundMatch(7))); // last played round = 7
            when(matchRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            List<Match> result = leagueService.regenerateSchedule();

            assertEquals(28, result.size(), "8 teams -> 7 rounds * 4 matches");
            org.junit.jupiter.api.Assertions.assertTrue(
                    result.stream().allMatch(m -> m.getRoundNumber() >= 1 && m.getRoundNumber() <= 7),
                    "new fixtures restart the round numbering");
            org.junit.jupiter.api.Assertions.assertTrue(
                    result.stream().allMatch(m -> m.getStatus() == MatchStatus.PENDING));
        }

        @Test
        @DisplayName("Refuses to regenerate while a round is still pending, and saves nothing")
        void regenerateRejectsWhenPendingExists() {
            when(matchRepository.findByStatus(MatchStatus.LIVE)).thenReturn(List.of());
            when(matchRepository.findByStatus(MatchStatus.PENDING)).thenReturn(List.of(roundMatch(3)));

            assertThrows(IllegalStateException.class, () -> leagueService.regenerateSchedule());

            verify(matchRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("Refuses to regenerate while a round is live, and saves nothing")
        void regenerateRejectsWhenLive() {
            when(matchRepository.findByStatus(MatchStatus.LIVE)).thenReturn(List.of(roundMatch(2)));

            assertThrows(IllegalStateException.class, () -> leagueService.regenerateSchedule());

            verify(matchRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("Bootstraps teams and a fixture list on a fresh DB, then starts round one")
        void bootstrapsLeagueWhenDatabaseEmpty() {
            when(matchRepository.count()).thenReturn(0L);                 // empty DB -> bootstrap
            when(teamRepository.findAll()).thenReturn(new ArrayList<>()); // no teams yet
            when(teamRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
            when(matchRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
            when(matchRepository.findByStatus(MatchStatus.LIVE)).thenReturn(List.of());
            when(matchRepository.findByStatus(MatchStatus.PENDING)).thenReturn(List.of(roundMatch(1)));
            when(matchRepository.findByRoundNumberAndStatus(1, MatchStatus.PENDING))
                    .thenReturn(List.of(roundMatch(1)));

            leagueService.startNextRound();

            verify(teamRepository).saveAll(anyList());     // default teams seeded
            verify(matchRepository).saveAll(anyList());    // fixture list generated
            verify(simulationEngine).runNextRound(anyList());
        }

        @Test
        @DisplayName("Regenerating a user's finished cycle creates a new private cycle with rounds 1..7")
        void regenerateUserCycleRestartsRoundNumbers() {
            Long userId = 42L;
            User owner = user(userId);
            List<Team> ownedTeams = teams(8);
            ownedTeams.forEach(team -> {
                team.setOwner(owner);
                team.setPoints(12);
                team.setGoalsFor(9);
                team.setGoalsAgainst(4);
            });

            when(matchRepository.countByOwnerId(userId)).thenReturn(28L);
            when(matchRepository.findMaxCycleNumberByOwnerId(userId)).thenReturn(1);
            when(matchRepository.findByOwnerIdAndCycleNumberAndStatus(userId, 1, MatchStatus.LIVE)).thenReturn(List.of());
            when(matchRepository.findByOwnerIdAndCycleNumberAndStatus(userId, 1, MatchStatus.PENDING)).thenReturn(List.of());
            when(teamRepository.findByOwnerId(userId)).thenReturn(ownedTeams);
            when(teamRepository.saveAll(ownedTeams)).thenReturn(ownedTeams);
            when(userRepository.findById(userId)).thenReturn(Optional.of(owner));
            when(matchRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            List<Match> result = leagueService.regenerateSchedule(userId);

            assertEquals(28, result.size());
            org.junit.jupiter.api.Assertions.assertTrue(result.stream().allMatch(match -> match.getOwner() == owner));
            org.junit.jupiter.api.Assertions.assertTrue(result.stream().allMatch(match -> match.getCycleNumber() == 2));
            org.junit.jupiter.api.Assertions.assertTrue(
                    result.stream().allMatch(match -> match.getRoundNumber() >= 1 && match.getRoundNumber() <= 7));
            org.junit.jupiter.api.Assertions.assertTrue(ownedTeams.stream().allMatch(team ->
                    team.getPoints() == 0 && team.getGoalsFor() == 0 && team.getGoalsAgainst() == 0));
        }
    }
}
