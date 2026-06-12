package org.example.footballleague.Service;

import org.example.footballleague.model.Match;
import org.example.footballleague.model.MatchStatus;
import org.example.footballleague.model.Team;
import org.example.footballleague.repositories.MatchRepository;
import org.example.footballleague.repositories.TeamRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class SimulationEngine {

    private final Random random = new Random();
    private final MatchRepository matchRepository;
    private final TeamRepository teamRepository;
    private final SseService sseService;
    private final BettingService bettingService;

    public SimulationEngine(MatchRepository matchRepository,
                            TeamRepository teamRepository,
                            SseService sseService,
                            BettingService bettingService) {
        this.matchRepository = matchRepository;
        this.teamRepository = teamRepository;
        this.sseService = sseService;
        this.bettingService = bettingService;
    }

    public void runNextRound(List<Match> currentRoundMatches) {
        if (currentRoundMatches == null || currentRoundMatches.isEmpty()) {
            throw new IllegalArgumentException("אין משחקים להרצה במחזור הקרוב");
        }

        new Thread(() -> {

            for (Match match : currentRoundMatches) {
                match.setStatus(MatchStatus.LIVE);
                calculateMatchOutcome(match);
                matchRepository.save(match);
            }

            sseService.broadcastMatchUpdate(Map.of(
                    "type", "ROUND_STARTED",
                    "roundNumber", currentRoundMatches.get(0).getRoundNumber(),
                    "matches", currentRoundMatches
            ));


            for (int currentSecond = 1; currentSecond <= 30; currentSecond++) {

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Live match thread was interrupted");
                    return;
                }

                // בכל שנייה שחולפת, נבדוק אם יש שער לאחד המשחקים
                for (Match match : currentRoundMatches) {
                    boolean goalScored = checkAndApplyGoalsForSecond(match, currentSecond);

                    if (goalScored) {
                        matchRepository.save(match);
                        sseService.broadcastGoal(createMatchEvent("GOAL", match, currentSecond));
                        System.out.println("GOAL! Match " + match.getId() + " | " + match.getHomeTeam().getName() + " " + match.getHomeScore() + " - " + match.getAwayScore() + " " + match.getAwayTeam().getName());
                    }
                }
            }

            // 3. סיום המשחקים בתום 30 שניות
            for (Match match : currentRoundMatches) {
                match.setStatus(MatchStatus.COMPLETED);
                updateLeagueTableStats(match);
                updateTeamSkills(match);
                teamRepository.save(match.getHomeTeam());
                teamRepository.save(match.getAwayTeam());
                matchRepository.save(match);
                bettingService.settleBets(match);
            }

            sseService.broadcastRoundComplete(Map.of(
                    "type", "ROUND_COMPLETED",
                    "roundNumber", currentRoundMatches.get(0).getRoundNumber(),
                    "matches", currentRoundMatches
            ));

        }).start(); // אל תשכחי את ה-.start() בסוף כדי להפעיל את ה-Thread!
    }

    // מתודת עזר שמחליטה האם להבקיע שער בשנייה הנוכחית
    private boolean checkAndApplyGoalsForSecond(Match match, int currentSecond) {
        boolean updated = false;

        // בודקים אם חסרים לקבוצת הבית שערים כדי להגיע ליעד שניבאנו לה
        if (match.getHomeScore() < match.getExpectedHomeScore()) {
            // יש לה עוד שערים להבקיע. ניתן סיכוי קטן (כדי לפזר אותם על פני 30 שניות)
            if (random.nextInt(30) == 0 || (30 - currentSecond) <= (match.getExpectedHomeScore() - match.getHomeScore())) {
                match.setHomeScore(match.getHomeScore() + 1);
                updated = true;
            }
        }

        // אותו דבר לקבוצת החוץ
        if (match.getAwayScore() < match.getExpectedAwayScore()) {
            if (random.nextInt(30) == 0 || (30 - currentSecond) <= (match.getExpectedAwayScore() - match.getAwayScore())) {
                match.setAwayScore(match.getAwayScore() + 1);
                updated = true;
            }
        }

        return updated;
    }

    private Map<String, Object> createMatchEvent(String type, Match match, int currentSecond) {
        return Map.of(
                "type", type,
                "second", currentSecond,
                "matchId", match.getId(),
                "roundNumber", match.getRoundNumber(),
                "homeTeam", match.getHomeTeam().getName(),
                "awayTeam", match.getAwayTeam().getName(),
                "homeScore", match.getHomeScore(),
                "awayScore", match.getAwayScore(),
                "status", match.getStatus()
        );
    }

    public void calculateMatchOutcome(Match match) {
        Team home = match.getHomeTeam();
        Team away = match.getAwayTeam();

        // 1. גורמים אקראיים (מזג אוויר, פציעות וכו')
        // נגריל מספר בין 5- ל-5+ שיוסיף או יוריד מהיכולת ביום המשחק
        int homeRandomFactor = random.nextInt(11) - 5;
        int awayRandomFactor = random.nextInt(11) - 5;

        double effectiveHomeSkill = Math.max(1, home.getSkillLevel() + homeRandomFactor);
        double effectiveAwaySkill = Math.max(1, away.getSkillLevel() + awayRandomFactor);

        // 2. חישוב יחסי כוחות להבקעת שער
        double homeAdvantage = effectiveHomeSkill / (effectiveHomeSkill + effectiveAwaySkill);

        // 3. הגרלת התוצאה הסופית מראש
        int expectedHomeGoals = calculateGoals(homeAdvantage);
        int expectedAwayGoals = calculateGoals(1.0 - homeAdvantage);

        // שומרים את התוצאה המיועדת בצד (כדי שב-runNextRound נוכל לפזר אותה על פני ה-30 שניות)
        match.setExpectedHomeScore(expectedHomeGoals);
        match.setExpectedAwayScore(expectedAwayGoals);
    }

    // מתודת עזר: מדמה 5 התקפות (הזדמנויות) ומגרילה שערים לפי סיכוי ההצלחה
    private int calculateGoals(double winProbability) {
        int goals = 0;
        for (int i = 0; i < 5; i++) {
            if (random.nextDouble() < (winProbability * 0.7)) { // 0.7 שומר על תוצאות מציאותיות (שלא ייגמר 5-5 כל משחק)
                goals++;
            }
        }
        return goals;
    }

    public void updateTeamSkills(Match match) {
        Team home = match.getHomeTeam();
        Team away = match.getAwayTeam();

        // בודקים מי ניצח לפי התוצאה האמיתית הרשמית בסיום המשחק
        if (match.getHomeScore() > match.getAwayScore()) {
            // קבוצת הבית ניצחה: מקבלת +2 (עד גבול של 100), והמפסידה מאבדת 2 (עד גבול של 1)
            home.setSkillLevel(Math.min(100, home.getSkillLevel() + 2));
            away.setSkillLevel(Math.max(1, away.getSkillLevel() - 2));
        }
        else if (match.getAwayScore() > match.getHomeScore()) {
            // קבוצת החוץ ניצחה
            home.setSkillLevel(Math.max(1, home.getSkillLevel() - 2));
            away.setSkillLevel(Math.min(100, away.getSkillLevel() + 2));
        }
        // במקרה של תיקו אנחנו לא משנים את היכולות (אפשר לשנות בהמשך אם נרצה)
    }

    private void updateLeagueTableStats(Match match) {
        Team home = match.getHomeTeam();
        Team away = match.getAwayTeam();

        home.setGoalsFor(home.getGoalsFor() + match.getHomeScore());
        home.setGoalsAgainst(home.getGoalsAgainst() + match.getAwayScore());
        away.setGoalsFor(away.getGoalsFor() + match.getAwayScore());
        away.setGoalsAgainst(away.getGoalsAgainst() + match.getHomeScore());

        if (match.getHomeScore() > match.getAwayScore()) {
            home.setPoints(home.getPoints() + 3);
        } else if (match.getAwayScore() > match.getHomeScore()) {
            away.setPoints(away.getPoints() + 3);
        } else {
            home.setPoints(home.getPoints() + 1);
            away.setPoints(away.getPoints() + 1);
        }
    }
}
