package org.example.footballleague.model;

import jakarta.persistence.*;

@Entity
@Table(name = "matches") // מגדיר את שם הטבלה בדאטאבייס
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // קשר לקבוצת הבית הרבה משחקים יכולים לשתף את אותה קבוצת בית
    @ManyToOne
    @JoinColumn(name = "home_team_id", nullable = false)
    private Team homeTeam;

    // קשר לקבוצת החוץ  הרבה משחקים יכולים לשתף את אותה קבוצת חוץ
    @ManyToOne
    @JoinColumn(name = "away_team_id", nullable = false)
    private Team awayTeam;

    private int roundNumber;
    private int homeScore = 0;
    private int awayScore = 0;

    // שמירת האינאם כמחרוזת טקסט בדאטאבייס
    @Enumerated(EnumType.STRING)
    private MatchStatus status = MatchStatus.PENDING;
private int expectedHomeScore;
private int expectedAwayScore;

    public int getExpectedHomeScore() {
        return expectedHomeScore;
    }

    public void setExpectedHomeScore(int expectedHomeScore) {
        this.expectedHomeScore = expectedHomeScore;
    }

    public int getExpectedAwayScore() {
        return expectedAwayScore;
    }

    public void setExpectedAwayScore(int expectedAwayScore) {
        this.expectedAwayScore = expectedAwayScore;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Team getHomeTeam() {
        return homeTeam;
    }

    public void setHomeTeam(Team homeTeam) {
        this.homeTeam = homeTeam;
    }

    public Team getAwayTeam() {
        return awayTeam;
    }

    public void setAwayTeam(Team awayTeam) {
        this.awayTeam = awayTeam;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public void setRoundNumber(int roundNumber) {
        this.roundNumber = roundNumber;
    }

    public int getHomeScore() {
        return homeScore;
    }

    public void setHomeScore(int homeScore) {
        this.homeScore = homeScore;
    }

    public int getAwayScore() {
        return awayScore;
    }

    public void setAwayScore(int awayScore) {
        this.awayScore = awayScore;
    }

    public MatchStatus getStatus() {
        return status;
    }

    public void setStatus(MatchStatus status) {
        this.status = status;
    }
}