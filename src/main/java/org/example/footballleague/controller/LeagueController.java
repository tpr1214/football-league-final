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
public class LeagueController {

    private final LeagueService leagueService;


    public LeagueController(LeagueService leagueService) {
        this.leagueService = leagueService;
    }


    @GetMapping("/matches")
    public ResponseEntity<List<Match>> getAllMatches() {
        List<Match> matches = leagueService.getAllMatches();
        return ResponseEntity.ok(matches);
    }


    @GetMapping("/table")
    public ResponseEntity<List<Team>> getLeagueTable() {
        List<Team> table = leagueService.getLeagueTable();
        return ResponseEntity.ok(table);
    }


    @PostMapping("/start-next-round")
    public ResponseEntity<List<Match>> startNextRound() {
        return ResponseEntity.ok(leagueService.startNextRound());
    }

    @GetMapping("/matches/upcoming")
    public ResponseEntity<List<MatchDashboardResponse>> getUpcomingMatches() {

        List<MatchDashboardResponse> upcoming = leagueService.getAllMatches().stream()
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