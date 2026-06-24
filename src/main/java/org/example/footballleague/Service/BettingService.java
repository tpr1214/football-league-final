package org.example.footballleague.Service;

import org.example.footballleague.model.Bet;
import org.example.footballleague.model.BetOutcome; // הוספנו את הייבוא של האינאם
import org.example.footballleague.model.BetStatus;
import org.example.footballleague.model.Match;
import org.example.footballleague.model.MatchStatus;
import org.example.footballleague.model.User;
import org.example.footballleague.repositories.BetRepository;
import org.example.footballleague.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BettingService {

    private final BetRepository betRepository;
    private final UserRepository userRepository;

    public BettingService(BetRepository betRepository, UserRepository userRepository) {
        this.betRepository = betRepository;
        this.userRepository = userRepository;
    }


    public double calculateOdds(Match match, BetOutcome predictedOutcome) {
        double homeSkill = match.getHomeTeam().getSkillLevel();
        double awaySkill = match.getAwayTeam().getSkillLevel();
        double totalSkill = homeSkill + awaySkill;

        double probability;


        switch (predictedOutcome) {
            case HOME_WIN:
                probability = homeSkill / totalSkill;
                break;
            case AWAY_WIN:
                probability = awaySkill / totalSkill;
                break;
            case DRAW:
                probability = 0.25;
                break;
            default:
                probability = 0.33;
        }

        double odds = 1.0 / probability;
        return Math.round(odds * 100.0) / 100.0;
    }

    public double calculateOdds(Match match, Bet bet) {
        double baseOdds = calculateOdds(match, bet.getPredictedOutcome());

        if (bet.getPredictedHomeScore() == null || bet.getPredictedAwayScore() == null) {
            return baseOdds;
        }

        int totalGoals = bet.getPredictedHomeScore() + bet.getPredictedAwayScore();
        double exactScoreMultiplier = 3.0 + (Math.min(totalGoals, 6) * 0.35);
        double odds = baseOdds * exactScoreMultiplier;

        return Math.round(odds * 100.0) / 100.0;
    }


    @Transactional
    public Bet placeBet(Bet bet, User user, Match match) {
        if (bet.getAmount() == null || bet.getAmount() <= 0) {
            throw new IllegalArgumentException("סכום ההימור חייב להיות גדול מאפס");
        }

        if (match.getStatus() != MatchStatus.PENDING) {
            throw new IllegalStateException("ניתן להמר רק על משחקים שטרם החלו");
        }

        if (user.getBalance() < bet.getAmount()) {
            throw new IllegalArgumentException("אין מספיק יתרה בחשבון לביצוע ההימור");
        }

        double odds = calculateOdds(match, bet);
        bet.setOdds(odds);

        bet.setStatus(BetStatus.PENDING);

        user.setBalance(user.getBalance() - bet.getAmount());
        userRepository.save(user);

        return betRepository.save(bet);
    }


    @Transactional
    public void settleBets(Match match) {

        BetOutcome actualOutcome;
        if (match.getHomeScore() > match.getAwayScore()) {
            actualOutcome = BetOutcome.HOME_WIN;
        } else if (match.getAwayScore() > match.getHomeScore()) {
            actualOutcome = BetOutcome.AWAY_WIN;
        } else {
            actualOutcome = BetOutcome.DRAW;
        }


        List<Bet> matchBets = betRepository.findByMatch(match);

        for (Bet bet : matchBets) {
            boolean exactScoreBet = bet.getPredictedHomeScore() != null && bet.getPredictedAwayScore() != null;
            boolean exactScoreWon = exactScoreBet
                    && bet.getPredictedHomeScore() == match.getHomeScore()
                    && bet.getPredictedAwayScore() == match.getAwayScore();
            boolean outcomeWon = !exactScoreBet && bet.getPredictedOutcome() == actualOutcome;

            if (exactScoreWon || outcomeWon) {
                bet.setStatus(BetStatus.WON);
                User user = bet.getUser();

                double winnings = bet.getAmount() * bet.getOdds();
                user.setBalance(user.getBalance() + winnings);
                userRepository.save(user);
            } else {
                bet.setStatus(BetStatus.LOST);
            }
        }

        betRepository.saveAll(matchBets);
    }
}
