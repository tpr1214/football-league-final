package org.example.footballleague.controller;

import org.example.footballleague.Service.BettingService;
import org.example.footballleague.model.Bet;
import org.example.footballleague.model.BetOutcome;
import org.example.footballleague.model.Match;
import org.example.footballleague.model.User;
import org.example.footballleague.repositories.BetRepository;
import org.example.footballleague.repositories.MatchRepository;
import org.example.footballleague.repositories.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bets")
public class BetController {

    private final BettingService bettingService;
    private final BetRepository betRepository;
    private final UserRepository userRepository;
    private final MatchRepository matchRepository;

    public BetController(BettingService bettingService,
                         BetRepository betRepository,
                         UserRepository userRepository,
                         MatchRepository matchRepository) {
        this.bettingService = bettingService;
        this.betRepository = betRepository;
        this.userRepository = userRepository;
        this.matchRepository = matchRepository;
    }

    @PostMapping("/place")
    public ResponseEntity<BetResponse> placeBet(@RequestBody PlaceBetRequest request) {
        if (request.amount() == null || request.amount() <= 0) {
            throw new IllegalArgumentException("Bet amount must be greater than zero");
        }
        if (request.predictedOutcome() == null) {
            throw new IllegalArgumentException("Predicted outcome is required");
        }
        if (request.predictedHomeScore() == null || request.predictedAwayScore() == null) {
            throw new IllegalArgumentException("Predicted score is required");
        }
        if (request.predictedHomeScore() < 0 || request.predictedAwayScore() < 0) {
            throw new IllegalArgumentException("Predicted score cannot be negative");
        }
        if (request.predictedHomeScore() > 3 || request.predictedAwayScore() > 3) {
            throw new IllegalArgumentException("Predicted score cannot be higher than 3 goals per team");
        }
        validateScoreMatchesOutcome(request.predictedOutcome(), request.predictedHomeScore(), request.predictedAwayScore());

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Match match = matchRepository.findById(request.matchId())
                .orElseThrow(() -> new IllegalArgumentException("Match not found"));

        Bet bet = new Bet();
        bet.setUser(user);
        bet.setMatch(match);
        bet.setPredictedOutcome(request.predictedOutcome());
        bet.setPredictedHomeScore(request.predictedHomeScore());
        bet.setPredictedAwayScore(request.predictedAwayScore());
        bet.setAmount(request.amount());

        Bet savedBet = bettingService.placeBet(bet, user, match);
        return ResponseEntity.ok(BetResponse.from(savedBet));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<BetResponse>> getUserBets(@PathVariable Long userId) {
        List<BetResponse> bets = betRepository.findByUserId(userId).stream()
                .map(BetResponse::from)
                .toList();
        return ResponseEntity.ok(bets);
    }

    @GetMapping("/user/{userId}/pending")
    public ResponseEntity<List<BetResponse>> getUserPendingBets(@PathVariable Long userId) {
        List<BetResponse> bets = betRepository.findByUserId(userId).stream()
                .filter(bet -> bet.getStatus() == org.example.footballleague.model.BetStatus.PENDING)
                .map(BetResponse::from)
                .toList();
        return ResponseEntity.ok(bets);
    }

    public record PlaceBetRequest(
            Long userId,
            Long matchId,
            BetOutcome predictedOutcome,
            Integer predictedHomeScore,
            Integer predictedAwayScore,
            Double amount
    ) {
    }

    public record BetResponse(
            Long id,
            Long userId,
            Long matchId,
            String homeTeam,
            String awayTeam,
            int roundNumber,
            BetOutcome predictedOutcome,
            Integer predictedHomeScore,
            Integer predictedAwayScore,
            Double amount,
            Double odds,
            String status,
            int actualHomeScore,
            int actualAwayScore
    ) {
        public static BetResponse from(Bet bet) {
            Match match = bet.getMatch();
            return new BetResponse(
                    bet.getId(),
                    bet.getUser().getId(),
                    match.getId(),
                    match.getHomeTeam().getName(),
                    match.getAwayTeam().getName(),
                    match.getRoundNumber(),
                    bet.getPredictedOutcome(),
                    bet.getPredictedHomeScore(),
                    bet.getPredictedAwayScore(),
                    bet.getAmount(),
                    bet.getOdds(),
                    bet.getStatus().name(),
                    match.getHomeScore(),
                    match.getAwayScore()
            );
        }
    }

    private static void validateScoreMatchesOutcome(BetOutcome outcome, int homeScore, int awayScore) {
        if (outcome == BetOutcome.HOME_WIN && homeScore <= awayScore) {
            throw new IllegalArgumentException("Home win prediction must have home score greater than away score");
        }
        if (outcome == BetOutcome.AWAY_WIN && awayScore <= homeScore) {
            throw new IllegalArgumentException("Away win prediction must have away score greater than home score");
        }
        if (outcome == BetOutcome.DRAW && !Integer.valueOf(homeScore).equals(awayScore)) {
            throw new IllegalArgumentException("Draw prediction must have equal scores");
        }
    }
}
