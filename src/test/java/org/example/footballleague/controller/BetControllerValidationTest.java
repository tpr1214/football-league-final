package org.example.footballleague.controller;

import org.example.footballleague.controller.BetController.PlaceBetRequest;
import org.example.footballleague.model.BetOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link BetController#validatePlaceBetRequest} covering both bet
 * shapes: outcome-only bets (no predicted score, e.g. "bet on a draw") and the
 * existing exact-score bets.
 */
class BetControllerValidationTest {

    private PlaceBetRequest request(BetOutcome outcome, Integer home, Integer away, Double amount) {
        return new PlaceBetRequest(1L, 1L, outcome, home, away, amount);
    }

    @Nested
    @DisplayName("Outcome-only bets (no predicted score)")
    class OutcomeOnly {

        @Test
        @DisplayName("DRAW with no scores is accepted (bet on a draw, any score)")
        void drawWithoutScores() {
            assertDoesNotThrow(() -> BetController.validatePlaceBetRequest(
                    request(BetOutcome.DRAW, null, null, 50.0)));
        }

        @Test
        @DisplayName("HOME_WIN with no scores is accepted")
        void homeWinWithoutScores() {
            assertDoesNotThrow(() -> BetController.validatePlaceBetRequest(
                    request(BetOutcome.HOME_WIN, null, null, 50.0)));
        }

        @Test
        @DisplayName("Only one score provided is rejected (both or neither)")
        void partialScoreRejected() {
            assertThrows(IllegalArgumentException.class, () -> BetController.validatePlaceBetRequest(
                    request(BetOutcome.DRAW, 1, null, 50.0)));
            assertThrows(IllegalArgumentException.class, () -> BetController.validatePlaceBetRequest(
                    request(BetOutcome.DRAW, null, 1, 50.0)));
        }
    }

    @Nested
    @DisplayName("Exact-score bets")
    class ExactScore {

        @Test
        @DisplayName("Consistent exact draw (1-1) is accepted")
        void validExactDraw() {
            assertDoesNotThrow(() -> BetController.validatePlaceBetRequest(
                    request(BetOutcome.DRAW, 1, 1, 50.0)));
        }

        @Test
        @DisplayName("Exact score inconsistent with the outcome is rejected")
        void inconsistentScoreRejected() {
            assertThrows(IllegalArgumentException.class, () -> BetController.validatePlaceBetRequest(
                    request(BetOutcome.DRAW, 1, 0, 50.0)));
            assertThrows(IllegalArgumentException.class, () -> BetController.validatePlaceBetRequest(
                    request(BetOutcome.HOME_WIN, 0, 1, 50.0)));
        }

        @Test
        @DisplayName("Score above 3 per team is rejected")
        void scoreTooHighRejected() {
            assertThrows(IllegalArgumentException.class, () -> BetController.validatePlaceBetRequest(
                    request(BetOutcome.HOME_WIN, 4, 0, 50.0)));
        }
    }

    @Nested
    @DisplayName("Common validation")
    class Common {

        @Test
        @DisplayName("Non-positive or null amount is rejected")
        void badAmountRejected() {
            assertThrows(IllegalArgumentException.class, () -> BetController.validatePlaceBetRequest(
                    request(BetOutcome.DRAW, null, null, 0.0)));
            assertThrows(IllegalArgumentException.class, () -> BetController.validatePlaceBetRequest(
                    request(BetOutcome.DRAW, null, null, null)));
        }

        @Test
        @DisplayName("Missing predicted outcome is rejected")
        void missingOutcomeRejected() {
            assertThrows(IllegalArgumentException.class, () -> BetController.validatePlaceBetRequest(
                    request(null, null, null, 50.0)));
        }
    }
}
