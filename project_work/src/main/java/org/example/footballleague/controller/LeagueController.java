package org.example.footballleague.controller;

import org.example.footballleague.Service.LeagueService;
import org.example.footballleague.model.Match;
import org.example.footballleague.model.Team;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/league")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174"}, allowCredentials = "true")
public class LeagueController {

    private final LeagueService leagueService;

    // שימוש ב-Constructor Injection (מומלץ יותר מ-@Autowired על השדה)
    public LeagueController(LeagueService leagueService) {
        this.leagueService = leagueService;
    }

    // שליפת כל המשחקים (הלוח המלא)
    @GetMapping("/matches")
    public ResponseEntity<List<Match>> getAllMatches() {
        List<Match> matches = leagueService.getAllMatches();
        return ResponseEntity.ok(matches);
    }

    // שליפת טבלת הליגה (ממוינת לפי החוקים)
    @GetMapping("/table")
    public ResponseEntity<List<Team>> getLeagueTable() {
        List<Team> table = leagueService.getLeagueTable();
        return ResponseEntity.ok(table);
    }

    // התחלת המחזור הבא שעדיין נמצא ב-PENDING
    @PostMapping("/start-next-round")
    public ResponseEntity<List<Match>> startNextRound() {
        return ResponseEntity.ok(leagueService.startNextRound());
    }

    // ======================================================================
    // 🔥 תוספת חדשה: הנתיב הדינמי שה-React מחפש עבור ווידג'ט המשחקים החמים בדשבורד!
    // ======================================================================
    @GetMapping("/matches/upcoming")
    public ResponseEntity<List<MatchDashboardResponse>> getUpcomingMatches() {
        // אנחנו משתמשים ב-Service הקיים שלכם כדי להביא את כל המשחקים מה-DB
        List<MatchDashboardResponse> upcoming = leagueService.getAllMatches().stream()
                .map(MatchDashboardResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(upcoming);
    }

    // ה-DTO (Data Transfer Object) שמסדר את הנתונים במבנה מדויק שה-React מצפה לקבל
    public record MatchDashboardResponse(
            Long id,
            String homeTeam,
            String awayTeam,
            String matchTime,
            boolean isLive
    ) {
        public static MatchDashboardResponse from(Match match) {
            // בודק האם הסטטוס של המשחק הוא LIVE (מותאם ל-MatchStatus Enum שלכם)
            boolean live = match.getStatus() == org.example.footballleague.model.MatchStatus.LIVE;

            return new MatchDashboardResponse(
                    match.getId(),
                    match.getHomeTeam().getName(),  // שולף את שם קבוצת הבית מטבלת ה-teams
                    match.getAwayTeam().getName(),  // שולף את שם קבוצת החוץ מטבלת ה-teams
                    live ? "LIVE" : "מחזור " + match.getRoundNumber(),
                    live
            );
        }
    }
}