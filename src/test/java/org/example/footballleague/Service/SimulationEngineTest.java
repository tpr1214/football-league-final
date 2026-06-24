package org.example.footballleague.Service;

import org.example.footballleague.model.Match;
import org.example.footballleague.model.Team;
import org.example.footballleague.repositories.MatchRepository;
import org.example.footballleague.repositories.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Pure unit tests for {@link SimulationEngine}. Dependencies are mocked; no Spring
 * context and no database. The internal {@code Random} is seeded for determinism,
 * so no test is time- or randomness-dependent. The asynchronous 30-second live
 * round inside runNextRound() is intentionally NOT exercised (see report).
 */
@ExtendWith(MockitoExtension.class)
class SimulationEngineTest {

    @Mock
    private MatchRepository matchRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private SseService sseService;
    @Mock
    private BettingService bettingService;

    @InjectMocks
    private SimulationEngine simulationEngine;

    @BeforeEach
    void seedRandom() {
        // Make every randomness-based path deterministic and non-flaky.
        ReflectionTestUtils.setField(simulationEngine, "random", new Random(12345L));
    }

    // ---------- helpers ----------

    private Team team(String name, int skill) {
        Team t = new Team();
        t.setName(name);
        t.setSkillLevel(skill);
        return t;
    }

    private Match match(Team home, Team away, int homeScore, int awayScore) {
        Match m = new Match();
        m.setHomeTeam(home);
        m.setAwayTeam(away);
        m.setHomeScore(homeScore);
        m.setAwayScore(awayScore);
        return m;
    }

    private int callCalculateGoals(double winProbability) {
        return (int) (Integer) ReflectionTestUtils.invokeMethod(
                simulationEngine, "calculateGoals", winProbability);
    }

    private boolean callCheckAndApplyGoals(Match m, int second) {
        return (boolean) (Boolean) ReflectionTestUtils.invokeMethod(
                simulationEngine, "checkAndApplyGoalsForSecond", m, second);
    }

    // ========================================================
    // updateTeamSkills (public, deterministic)
    // ========================================================
    @Nested
    @DisplayName("updateTeamSkills")
    class UpdateTeamSkills {

        @Test
        @DisplayName("Home win: home +2, away -2")
        void homeWin() {
            Team home = team("H", 80);
            Team away = team("A", 80);
            simulationEngine.updateTeamSkills(match(home, away, 2, 1));
            assertEquals(82, home.getSkillLevel());
            assertEquals(78, away.getSkillLevel());
        }

        @Test
        @DisplayName("Away win: home -2, away +2")
        void awayWin() {
            Team home = team("H", 80);
            Team away = team("A", 80);
            simulationEngine.updateTeamSkills(match(home, away, 1, 2));
            assertEquals(78, home.getSkillLevel());
            assertEquals(82, away.getSkillLevel());
        }

        @Test
        @DisplayName("Draw: no skill change for either team")
        void drawNoChange() {
            Team home = team("H", 80);
            Team away = team("A", 70);
            simulationEngine.updateTeamSkills(match(home, away, 1, 1));
            assertEquals(80, home.getSkillLevel());
            assertEquals(70, away.getSkillLevel());
        }

        @Test
        @DisplayName("Skill is capped at 100 for a winner")
        void cappedAt100() {
            Team home = team("H", 99);
            Team away = team("A", 50);
            simulationEngine.updateTeamSkills(match(home, away, 3, 0));
            assertEquals(100, home.getSkillLevel(), "99+2 must clamp to 100");
        }

        @Test
        @DisplayName("Skill never drops below 1 for a loser")
        void flooredAt1() {
            Team home = team("H", 80);
            Team away = team("A", 1);
            simulationEngine.updateTeamSkills(match(home, away, 5, 0));
            assertEquals(1, away.getSkillLevel(), "1-2 must clamp to 1");
        }
    }

    // ========================================================
    // updateLeagueTableStats (private via reflection, deterministic)
    // ========================================================
    @Nested
    @DisplayName("updateLeagueTableStats")
    class UpdateLeagueTableStats {

        @Test
        @DisplayName("Home win: home +3 points, away +0; goals for/against updated")
        void homeWin() {
            Team home = team("H", 80);
            Team away = team("A", 80);
            ReflectionTestUtils.invokeMethod(simulationEngine, "updateLeagueTableStats",
                    match(home, away, 2, 1));
            assertEquals(3, home.getPoints());
            assertEquals(0, away.getPoints());
            assertEquals(2, home.getGoalsFor());
            assertEquals(1, home.getGoalsAgainst());
            assertEquals(1, away.getGoalsFor());
            assertEquals(2, away.getGoalsAgainst());
        }

        @Test
        @DisplayName("Away win: away +3 points, home +0")
        void awayWin() {
            Team home = team("H", 80);
            Team away = team("A", 80);
            ReflectionTestUtils.invokeMethod(simulationEngine, "updateLeagueTableStats",
                    match(home, away, 0, 2));
            assertEquals(0, home.getPoints());
            assertEquals(3, away.getPoints());
        }

        @Test
        @DisplayName("Draw: both teams +1 point; goals updated")
        void draw() {
            Team home = team("H", 80);
            Team away = team("A", 80);
            ReflectionTestUtils.invokeMethod(simulationEngine, "updateLeagueTableStats",
                    match(home, away, 1, 1));
            assertEquals(1, home.getPoints());
            assertEquals(1, away.getPoints());
            assertEquals(1, home.getGoalsFor());
            assertEquals(1, home.getGoalsAgainst());
        }

        @Test
        @DisplayName("Stats accumulate on top of existing values")
        void accumulates() {
            Team home = team("H", 80);
            home.setPoints(3);
            home.setGoalsFor(5);
            Team away = team("A", 80);
            ReflectionTestUtils.invokeMethod(simulationEngine, "updateLeagueTableStats",
                    match(home, away, 2, 0));
            assertEquals(6, home.getPoints(), "3 existing + 3 for the win");
            assertEquals(7, home.getGoalsFor(), "5 existing + 2 scored");
        }
    }

    // ========================================================
    // calculateGoals (private via reflection; deterministic edges + range)
    // ========================================================
    @Nested
    @DisplayName("calculateGoals")
    class CalculateGoals {

        @Test
        @DisplayName("Probability 0.0 always yields 0 goals (no negative scores)")
        void zeroProbabilityYieldsZero() {
            for (int i = 0; i < 100; i++) {
                assertEquals(0, callCalculateGoals(0.0));
            }
        }

        @Test
        @DisplayName("A saturating probability is capped at 3 goals")
        void cappedAtThree() {
            // winProbability * 0.7 >= 1 (e.g. 2.0 -> 1.4) means every trial scores,
            // and the loop runs exactly 3 times -> always exactly 3.
            for (int i = 0; i < 100; i++) {
                assertEquals(3, callCalculateGoals(2.0));
            }
        }

        @Test
        @DisplayName("Goals always stay within [0, 3] across many random draws")
        void alwaysWithinRange() {
            for (int i = 0; i < 2000; i++) {
                double p = (i % 11) / 10.0; // 0.0 .. 1.0
                int goals = callCalculateGoals(p);
                assertTrue(goals >= 0 && goals <= 3, "out of range: " + goals);
            }
        }
    }

    // ========================================================
    // calculateMatchOutcome (public; seeded random -> deterministic)
    // ========================================================
    @Nested
    @DisplayName("calculateMatchOutcome")
    class CalculateMatchOutcome {

        @Test
        @DisplayName("Expected scores always stay within [0, 3] and are never negative")
        void scoresWithinRange() {
            for (int i = 0; i < 1000; i++) {
                Match m = match(team("H", 50 + (i % 50)), team("A", 50 + (i % 30)), 0, 0);
                simulationEngine.calculateMatchOutcome(m);
                assertTrue(m.getExpectedHomeScore() >= 0 && m.getExpectedHomeScore() <= 3,
                        "home expected out of range: " + m.getExpectedHomeScore());
                assertTrue(m.getExpectedAwayScore() >= 0 && m.getExpectedAwayScore() <= 3,
                        "away expected out of range: " + m.getExpectedAwayScore());
            }
        }

        @Test
        @DisplayName("A far stronger home team has a higher total expected score over many matches")
        void strongerTeamScoresMore() {
            long homeTotal = 0;
            long awayTotal = 0;
            for (int i = 0; i < 3000; i++) {
                Match m = match(team("Strong", 99), team("Weak", 1), 0, 0);
                simulationEngine.calculateMatchOutcome(m);
                homeTotal += m.getExpectedHomeScore();
                awayTotal += m.getExpectedAwayScore();
            }
            assertTrue(homeTotal > awayTotal,
                    "stronger team should out-score the weaker one: home=" + homeTotal + " away=" + awayTotal);
        }
    }

    // ========================================================
    // checkAndApplyGoalsForSecond (private; deterministic branches only)
    // ========================================================
    @Nested
    @DisplayName("checkAndApplyGoalsForSecond")
    class CheckAndApplyGoals {

        @Test
        @DisplayName("Forces a goal when time runs out and the team is below its expected score")
        void forcesGoalWhenTimeRunsOut() {
            Match m = match(team("H", 80), team("A", 80), 0, 0);
            m.setExpectedHomeScore(2);
            m.setExpectedAwayScore(0);

            // second 30: (30-30)=0 <= (2-0) -> home goal is forced regardless of randomness
            boolean updated = callCheckAndApplyGoals(m, 30);

            assertTrue(updated);
            assertEquals(1, m.getHomeScore(), "home must score the forced goal");
            assertEquals(0, m.getAwayScore(), "away at expected 0 must not score");
        }

        @Test
        @DisplayName("No goal once both scores already reached the expected values")
        void noGoalWhenAtExpected() {
            Match m = match(team("H", 80), team("A", 80), 1, 1);
            m.setExpectedHomeScore(1);
            m.setExpectedAwayScore(1);

            boolean updated = callCheckAndApplyGoals(m, 30);

            assertEquals(false, updated, "no side is below expected, so no goal can be applied");
            assertEquals(1, m.getHomeScore());
            assertEquals(1, m.getAwayScore());
        }
    }

    // ========================================================
    // runNextRound (only the synchronous input guard is safe to test)
    // ========================================================
    @Nested
    @DisplayName("runNextRound input validation")
    class RunNextRoundValidation {

        @Test
        @DisplayName("Null input is rejected before any work/thread starts")
        void nullRejected() {
            assertThrows(IllegalArgumentException.class, () -> simulationEngine.runNextRound(null));
            verifyNoInteractions(matchRepository, teamRepository, sseService, bettingService);
        }

        @Test
        @DisplayName("Empty input is rejected before any work/thread starts")
        void emptyRejected() {
            assertThrows(IllegalArgumentException.class, () -> simulationEngine.runNextRound(List.of()));
            verifyNoInteractions(matchRepository, teamRepository, sseService, bettingService);
        }
    }
}
