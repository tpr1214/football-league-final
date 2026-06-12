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

    // הזרקת התלויות דרך הבנאי
    public LeagueService(TeamRepository teamRepository,
                         MatchRepository matchRepository,
                         SimulationEngine simulationEngine) {
        this.teamRepository = teamRepository;
        this.matchRepository = matchRepository;
        this.simulationEngine = simulationEngine;
    }

    /**
     * 1. יצירת לוח משחקים דינמי (Round-Robin) ושמירתו בדאטאבייס
     * שינינו את החתימה ל-List<Match> כדי שזה יתאים למה ש-DataInitializer שלך מצפה לקבל!
     */
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

                // חישוב מתמטי של השערים הצפויים (Expected Scores) לפי רמת ה-Skill Level של הקבוצות
                calculateExpectedScores(match, home, away);

                allMatches.add(match);
            }

            // אלגוריתם הסיבוב המקורי והטוב שלך
            Team lastTeam = rotationList.remove(totalTeams - 1);
            rotationList.add(1, lastTeam);
        }

        // שומרים את כל משחקי העונה בדאטאבייס ומחזירים את הרשימה השמורה
        return matchRepository.saveAll(allMatches);
    }

    /**
     * פונקציית עזר לחישוב מתמטי של השערים הצפויים על בסיס ה-Skill Level של הקבוצות
     */
    private void calculateExpectedScores(Match match, Team home, Team away) {
        // בסיס שערים רנדומלי (הגרלה בין 0 ל-2)
        double baseHome = Math.random() * 2;
        double baseAway = Math.random() * 2;

        // הוספת יתרון מתמטי קל לקבוצה בעלת ה-Skill Level הגבוה יותר
        if (home.getSkillLevel() > away.getSkillLevel()) {
            baseHome += 1.0;
        } else if (away.getSkillLevel() > home.getSkillLevel()) {
            baseAway += 1.0;
        }

        // השמה של הערכים השלמים (מעוגלים) לתוך השדות ב-Match שלך
        match.setExpectedHomeScore((int) Math.round(baseHome));
        match.setExpectedAwayScore((int) Math.round(baseAway));
    }

    /**
     * 2. שליפת כל המשחקים (עבור ה-LeagueController)
     */
    public List<Match> getAllMatches() {
        return matchRepository.findAll();
    }

    /**
     * 3. שליפת טבלת הליגה ממוינת (עבור ה-LeagueController)
     */
    public List<Team> getLeagueTable() {
        List<Team> teams = teamRepository.findAll();

        // אלגוריתם המיון המצוין והמדויק שכתבת
        teams.sort((t1, t2) -> {
            // 1. מיון לפי נקודות
            if (t1.getPoints() != t2.getPoints()) {
                return Integer.compare(t2.getPoints(), t1.getPoints());
            }

            // 2. מיון לפי הפרש שערים
            int diff1 = t1.getGoalsFor() - t1.getGoalsAgainst();
            int diff2 = t2.getGoalsFor() - t2.getGoalsAgainst();
            if (diff1 != diff2) {
                return Integer.compare(diff2, diff1);
            }

            // 3. מיון אלפביתי
            return t1.getName().compareTo(t2.getName());
        });

        return teams;
    }

    /**
     * 4. התחלת המחזור הבא שעדיין ממתין להרצה.
     */
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
