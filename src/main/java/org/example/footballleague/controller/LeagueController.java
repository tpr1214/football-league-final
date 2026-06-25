package org.example.footballleague.controller;

import org.example.footballleague.Service.LeagueService;
import org.example.footballleague.model.Match;
import org.example.footballleague.model.Team;
import org.example.footballleague.security.AuthenticatedUser;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/league")
public class LeagueController {

    private final LeagueService leagueService;


    public LeagueController(LeagueService leagueService) {
        this.leagueService = leagueService;
    }


    @GetMapping("/matches")
    public ResponseEntity<List<Match>> getAllMatches(@AuthenticationPrincipal AuthenticatedUser principal) {
        List<Match> matches = leagueService.getAllMatches(principal.userId());
        return ResponseEntity.ok(matches);
    }


    @GetMapping("/table")
    public ResponseEntity<List<Team>> getLeagueTable(@AuthenticationPrincipal AuthenticatedUser principal) {
        List<Team> table = leagueService.getLeagueTable(principal.userId());
        return ResponseEntity.ok(table);
    }


    @PostMapping("/start-next-round")
    public ResponseEntity<List<Match>> startNextRound(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(leagueService.startNextRound(principal.userId()));
    }

    @PostMapping("/regenerate-rounds")
    public ResponseEntity<List<Match>> regenerateRounds(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(leagueService.regenerateSchedule(principal.userId()));
    }

    @GetMapping("/matches/upcoming")
    public ResponseEntity<List<MatchDashboardResponse>> getUpcomingMatches(
            @AuthenticationPrincipal AuthenticatedUser principal) {

        List<MatchDashboardResponse> upcoming = leagueService.getAllMatches(principal.userId()).stream()
                .filter(match -> match.getStatus() == org.example.footballleague.model.MatchStatus.PENDING || 
                                 match.getStatus() == org.example.footballleague.model.MatchStatus.LIVE)
                .map(MatchDashboardResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(upcoming);
    }


    public record MatchDashboardResponse(
            Long id,
            String homeTeam,
            String awayTeam,
            String matchTime,
            boolean isLive
    ) {
        public static MatchDashboardResponse from(Match match) {

            boolean live = match.getStatus() == org.example.footballleague.model.MatchStatus.LIVE;

            return new MatchDashboardResponse(
                    match.getId(),
                    match.getHomeTeam().getName(),
                    match.getAwayTeam().getName(),
                    live ? "LIVE" : "מחזור " + match.getRoundNumber(),
                    live
            );
        }
    }
}
