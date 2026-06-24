package org.example.footballleague.Service;

import org.example.footballleague.model.Bet;
import org.example.footballleague.model.BetOutcome;
import org.example.footballleague.model.BetStatus;
import org.example.footballleague.model.Match;
import org.example.footballleague.model.MatchStatus;
import org.example.footballleague.model.Team;
import org.example.footballleague.model.User;
import org.example.footballleague.repositories.BetRepository;
import org.example.footballleague.repositories.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link BettingService} — the money/correctness core.
 * Repositories are mocked; no Spring context and no database are involved.
 */
@ExtendWith(MockitoExtension.class)
class BettingServiceTest {

    private static final double DELTA = 0.0001;

    @Mock
    private BetRepository betRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BettingService bettingService;

    // ---------- helpers ----------

    private Team team(String name, int skill) {
        Team t = new Team();
        t.setName(name);
        t.setSkillLevel(skill);
        return t;
    }

    private Match match(int homeSkill, int awaySkill, MatchStatus status) {
        Match m = new Match();
        m.setHomeTeam(team("Home", homeSkill));
        m.setAwayTeam(team("Away", awaySkill));
        m.setStatus(status);
        return m;
    }

    private Match finishedMatch(int homeScore, int awayScore) {
        Match m = match(50, 50, MatchStatus.COMPLETED);
        m.setHomeScore(homeScore);
        m.setAwayScore(awayScore);
        return m;
    }

    private User user(double balance) {
        User u = new User();
        u.setBalance(balance);
        return u;
    }

    private Bet outcomeBet(User u, Match m, BetOutcome outcome, double amount) {
        Bet b = new Bet();
        b.setUser(u);
        b.setMatch(m);
        b.setPredictedOutcome(outcome);
        b.setAmount(amount);
        return b;
    }

    private Bet exactScoreBet(User u, Match m, BetOutcome outcome, int home, int away, double amount) {
        Bet b = outcomeBet(u, m, outcome, amount);
        b.setPredictedHomeScore(home);
        b.setPredictedAwayScore(away);
        return b;
    }

    // ========================================================
    // calculateOdds
    // ========================================================
    @Nested
    @DisplayName("calculateOdds")
    class CalculateOdds {

        @Test
        @DisplayName("HOME_WIN odds = 1 / (homeSkill/total)")
        void homeWinOdds() {
            Match m = match(60, 40, MatchStatus.PENDING); // prob 0.6 -> 1.6667 -> 1.67
            assertEquals(1.67, bettingService.calculateOdds(m, BetOutcome.HOME_WIN), DELTA);
        }

        @Test
        @DisplayName("AWAY_WIN odds = 1 / (awaySkill/total)")
        void awayWinOdds() {
            Match m = match(60, 40, MatchStatus.PENDING); // prob 0.4 -> 2.5
            assertEquals(2.5, bettingService.calculateOdds(m, BetOutcome.AWAY_WIN), DELTA);
        }

        @Test
        @DisplayName("DRAW odds are fixed at 1/0.25 = 4.0")
        void drawOdds() {
            Match m = match(60, 40, MatchStatus.PENDING);
            assertEquals(4.0, bettingService.calculateOdds(m, BetOutcome.DRAW), DELTA);
        }

        @Test
        @DisplayName("Even teams (50/50) give HOME and AWAY odds of 2.0")
        void evenTeamsOdds() {
            Match m = match(50, 50, MatchStatus.PENDING);
            assertEquals(2.0, bettingService.calculateOdds(m, BetOutcome.HOME_WIN), DELTA);
            assertEquals(2.0, bettingService.calculateOdds(m, BetOutcome.AWAY_WIN), DELTA);
        }

        @Test
        @DisplayName("Bet without predicted scores returns the base outcome odds")
        void betWithoutScoresUsesBaseOdds() {
            Match m = match(50, 50, MatchStatus.PENDING);
            Bet b = outcomeBet(user(1000), m, BetOutcome.HOME_WIN, 100);
            assertEquals(2.0, bettingService.calculateOdds(m, b), DELTA);
        }

        @Test
        @DisplayName("Exact-score bet multiplies base odds by (3.0 + min(totalGoals,6)*0.35)")
        void exactScoreOddsMultiplier() {
            Match m = match(50, 50, MatchStatus.PENDING); // base 2.0
            Bet b = exactScoreBet(user(1000), m, BetOutcome.HOME_WIN, 2, 1, 100); // totalGoals=3
            // multiplier = 3.0 + 3*0.35 = 4.05 ; 2.0 * 4.05 = 8.1
            assertEquals(8.1, bettingService.calculateOdds(m, b), DELTA);
        }

        @Test
        @DisplayName("Exact-score multiplier caps total goals at 6")
        void exactScoreMultiplierCapsAtSix() {
            Match m = match(50, 50, MatchStatus.PENDING); // base 2.0
            Bet b = exactScoreBet(user(1000), m, BetOutcome.HOME_WIN, 5, 5, 100); // totalGoals=10 -> capped 6
            // multiplier = 3.0 + 6*0.35 = 5.1 ; 2.0 * 5.1 = 10.2
            assertEquals(10.2, bettingService.calculateOdds(m, b), DELTA);
        }
    }

    // ========================================================
    // placeBet
    // ========================================================
    @Nested
    @DisplayName("placeBet")
    class PlaceBet {

        @Test
        @DisplayName("Valid bet: deducts amount, sets PENDING + odds, saves user and bet")
        void validBet() {
            User u = user(1000);
            Match m = match(50, 50, MatchStatus.PENDING);
            Bet b = outcomeBet(u, m, BetOutcome.HOME_WIN, 100);
            when(betRepository.save(b)).thenReturn(b);

            Bet result = bettingService.placeBet(b, u, m);

            assertEquals(900.0, u.getBalance(), DELTA, "balance must be reduced by the stake");
            assertEquals(BetStatus.PENDING, b.getStatus());
            assertEquals(2.0, b.getOdds(), DELTA, "odds must be computed and stored on the bet");
            assertSame(b, result);
            verify(userRepository).save(u);
            verify(betRepository).save(b);
        }

        @Test
        @DisplayName("Rejects bet when match is not PENDING (LIVE) and does NOT touch balance/repos")
        void rejectsNonPendingMatch() {
            User u = user(1000);
            Match m = match(50, 50, MatchStatus.LIVE);
            Bet b = outcomeBet(u, m, BetOutcome.HOME_WIN, 100);

            assertThrows(IllegalStateException.class, () -> bettingService.placeBet(b, u, m));

            assertEquals(1000.0, u.getBalance(), DELTA, "balance must be untouched on rejection");
            verifyNoInteractions(userRepository);
            verifyNoInteractions(betRepository);
        }

        @Test
        @DisplayName("Rejects bet when balance is insufficient and does NOT touch balance/repos")
        void rejectsInsufficientBalance() {
            User u = user(50);
            Match m = match(50, 50, MatchStatus.PENDING);
            Bet b = outcomeBet(u, m, BetOutcome.HOME_WIN, 100);

            assertThrows(IllegalArgumentException.class, () -> bettingService.placeBet(b, u, m));

            assertEquals(50.0, u.getBalance(), DELTA, "balance must be untouched when stake > balance");
            verifyNoInteractions(userRepository);
            verifyNoInteractions(betRepository);
        }

        @Test
        @DisplayName("Exact-score bet stores the multiplied odds")
        void exactScoreBetStoresMultipliedOdds() {
            User u = user(1000);
            Match m = match(50, 50, MatchStatus.PENDING);
            Bet b = exactScoreBet(u, m, BetOutcome.HOME_WIN, 2, 1, 100);
            when(betRepository.save(b)).thenReturn(b);

            bettingService.placeBet(b, u, m);

            assertEquals(8.1, b.getOdds(), DELTA);
            assertEquals(900.0, u.getBalance(), DELTA);
        }

        // ----- edge cases -----

        @Test
        @DisplayName("Stake exactly equal to balance is allowed (check is strict '<') and balance becomes 0")
        void exactBalanceEqualsStakeAllowedAndBalanceBecomesZero() {
            User u = user(100);
            Match m = match(50, 50, MatchStatus.PENDING);
            Bet b = outcomeBet(u, m, BetOutcome.HOME_WIN, 100);
            when(betRepository.save(b)).thenReturn(b);

            Bet result = bettingService.placeBet(b, u, m);

            assertEquals(0.0, u.getBalance(), DELTA, "spending the entire balance must leave exactly 0");
            assertEquals(BetStatus.PENDING, b.getStatus());
            assertSame(b, result);
            verify(userRepository).save(u);
            verify(betRepository).save(b);
        }

        @Test
        @DisplayName("Failed bet (insufficient balance) does NOT change the balance")
        void failedBetDoesNotChangeBalance() {
            User u = user(50);
            Match m = match(50, 50, MatchStatus.PENDING);
            Bet b = outcomeBet(u, m, BetOutcome.HOME_WIN, 100);

            assertThrows(IllegalArgumentException.class, () -> bettingService.placeBet(b, u, m));

            assertEquals(50.0, u.getBalance(), DELTA);
        }

        @Test
        @DisplayName("Failed bet does NOT save a bet (and does not save the user)")
        void failedBetDoesNotSaveBet() {
            User u = user(50);
            Match m = match(50, 50, MatchStatus.PENDING);
            Bet b = outcomeBet(u, m, BetOutcome.HOME_WIN, 100);

            assertThrows(IllegalArgumentException.class, () -> bettingService.placeBet(b, u, m));

            verify(betRepository, never()).save(any());
            verify(userRepository, never()).save(any());
        }

        // Defense-in-depth: the service itself must reject non-positive stakes,
        // not only BetController. Balance must be untouched and nothing persisted.

        @Test
        @DisplayName("Zero amount is rejected; balance unchanged; nothing saved")
        void zeroAmountRejected() {
            User u = user(1000);
            Match m = match(50, 50, MatchStatus.PENDING);
            Bet b = outcomeBet(u, m, BetOutcome.HOME_WIN, 0);

            assertThrows(IllegalArgumentException.class, () -> bettingService.placeBet(b, u, m));

            assertEquals(1000.0, u.getBalance(), DELTA, "balance must be untouched for a zero stake");
            verify(betRepository, never()).save(any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Negative amount is rejected; balance unchanged; nothing saved")
        void negativeAmountRejected() {
            User u = user(1000);
            Match m = match(50, 50, MatchStatus.PENDING);
            Bet b = outcomeBet(u, m, BetOutcome.HOME_WIN, -100);

            assertThrows(IllegalArgumentException.class, () -> bettingService.placeBet(b, u, m));

            assertEquals(1000.0, u.getBalance(), DELTA, "balance must never increase from a negative stake");
            verify(betRepository, never()).save(any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Null amount is rejected; balance unchanged; nothing saved")
        void nullAmountRejected() {
            User u = user(1000);
            Match m = match(50, 50, MatchStatus.PENDING);
            Bet b = outcomeBet(u, m, BetOutcome.HOME_WIN, 100);
            b.setAmount(null);

            assertThrows(IllegalArgumentException.class, () -> bettingService.placeBet(b, u, m));

            assertEquals(1000.0, u.getBalance(), DELTA);
            verify(betRepository, never()).save(any());
            verify(userRepository, never()).save(any());
        }
    }

    // ========================================================
    // settleBets
    // ========================================================
    @Nested
    @DisplayName("settleBets")
    class SettleBets {

        @Test
        @DisplayName("Winning outcome bet: marked WON and credited amount*odds")
        void outcomeWinCredits() {
            Match m = finishedMatch(2, 1); // HOME_WIN
            User u = user(500);
            Bet b = outcomeBet(u, m, BetOutcome.HOME_WIN, 100);
            b.setOdds(2.0);
            when(betRepository.findByMatch(m)).thenReturn(List.of(b));

            bettingService.settleBets(m);

            assertEquals(BetStatus.WON, b.getStatus());
            assertEquals(700.0, u.getBalance(), DELTA, "winnings = amount*odds = 200 added to 500");
            verify(userRepository).save(u);
            verify(betRepository).saveAll(any());
        }

        @Test
        @DisplayName("Losing outcome bet: marked LOST and balance unchanged, user not saved")
        void outcomeLossNoCredit() {
            Match m = finishedMatch(2, 1); // HOME_WIN
            User u = user(500);
            Bet b = outcomeBet(u, m, BetOutcome.AWAY_WIN, 100); // wrong prediction
            b.setOdds(2.0);
            when(betRepository.findByMatch(m)).thenReturn(List.of(b));

            bettingService.settleBets(m);

            assertEquals(BetStatus.LOST, b.getStatus());
            assertEquals(500.0, u.getBalance(), DELTA);
            verify(userRepository, never()).save(any());
            verify(betRepository).saveAll(any());
        }

        @Test
        @DisplayName("Exact-score bet with the exact score: WON and credited")
        void exactScoreWinCredits() {
            Match m = finishedMatch(2, 1); // HOME_WIN, exact 2-1
            User u = user(500);
            Bet b = exactScoreBet(u, m, BetOutcome.HOME_WIN, 2, 1, 50);
            b.setOdds(8.1);
            when(betRepository.findByMatch(m)).thenReturn(List.of(b));

            bettingService.settleBets(m);

            assertEquals(BetStatus.WON, b.getStatus());
            assertEquals(500.0 + 50 * 8.1, u.getBalance(), DELTA);
            verify(userRepository).save(u);
        }

        @Test
        @DisplayName("Exact-score bet with right outcome but WRONG exact score: LOST, no credit")
        void exactScoreRightOutcomeWrongScoreLoses() {
            Match m = finishedMatch(2, 1); // HOME_WIN but score 2-1
            User u = user(500);
            Bet b = exactScoreBet(u, m, BetOutcome.HOME_WIN, 3, 0, 50); // home win but 3-0 != 2-1
            b.setOdds(8.1);
            when(betRepository.findByMatch(m)).thenReturn(List.of(b));

            bettingService.settleBets(m);

            assertEquals(BetStatus.LOST, b.getStatus());
            assertEquals(500.0, u.getBalance(), DELTA, "exact-score bets only win on the exact score");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("DRAW outcome is detected and a DRAW bet wins")
        void drawOutcomeWins() {
            Match m = finishedMatch(1, 1); // DRAW
            User u = user(500);
            Bet b = outcomeBet(u, m, BetOutcome.DRAW, 100);
            b.setOdds(4.0);
            when(betRepository.findByMatch(m)).thenReturn(List.of(b));

            bettingService.settleBets(m);

            assertEquals(BetStatus.WON, b.getStatus());
            assertEquals(900.0, u.getBalance(), DELTA);
        }

        @Test
        @DisplayName("Mixed batch: only winners credited; all bets persisted once")
        void mixedBatchCreditsOnlyWinners() {
            Match m = finishedMatch(2, 0); // HOME_WIN
            User winner = user(0);
            User loser = user(0);
            Bet winning = outcomeBet(winner, m, BetOutcome.HOME_WIN, 100);
            winning.setOdds(2.0);
            Bet losing = outcomeBet(loser, m, BetOutcome.AWAY_WIN, 100);
            losing.setOdds(2.0);
            when(betRepository.findByMatch(m)).thenReturn(List.of(winning, losing));

            bettingService.settleBets(m);

            assertEquals(BetStatus.WON, winning.getStatus());
            assertEquals(BetStatus.LOST, losing.getStatus());
            assertEquals(200.0, winner.getBalance(), DELTA);
            assertEquals(0.0, loser.getBalance(), DELTA);
            verify(userRepository, times(1)).save(winner);
            verify(userRepository, never()).save(loser);
            verify(betRepository, times(1)).saveAll(any());
        }
    }
}
