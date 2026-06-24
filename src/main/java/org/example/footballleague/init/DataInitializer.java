package org.example.footballleague.init;

import jakarta.annotation.PostConstruct;
import org.example.footballleague.Service.LeagueService;
import org.example.footballleague.model.Match;
import org.example.footballleague.model.Team;
import org.example.footballleague.model.User;
import org.example.footballleague.repositories.BetRepository;
import org.example.footballleague.repositories.MatchRepository;
import org.example.footballleague.repositories.TeamRepository;
import org.example.footballleague.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Component
public class DataInitializer {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final BetRepository betRepository;
    private final UserRepository userRepository;
    private final LeagueService leagueService;
    private final Environment environment;

    @Value("${app.seed.reset-data:false}")
    private boolean resetData;

    @Value("${app.seed.create-dev-admin:false}")
    private boolean createDevAdmin;


    public DataInitializer(TeamRepository teamRepository,
                           MatchRepository matchRepository,
                           BetRepository betRepository,
                           UserRepository userRepository,
                           LeagueService leagueService,
                           Environment environment) {
        this.teamRepository = teamRepository;
        this.matchRepository = matchRepository;
        this.betRepository = betRepository;
        this.userRepository = userRepository;
        this.leagueService = leagueService;
        this.environment = environment;
    }

    @PostConstruct
    @Transactional
    public void initData() {
        if (!shouldRunDevSeed()) {
            System.out.println(">> DataInitializer skipped. Destructive seed/reset is disabled outside explicit dev mode.");
            return;
        }

        betRepository.deleteAll();
        matchRepository.deleteAll(); 
        teamRepository.deleteAll();

        System.out.println(">> הדאטאבייס נוקה בהצלחה משאריות קודמות!");


        List<Team> teamsToSave = createTeams();


        List<Team> savedTeams = teamRepository.saveAll(teamsToSave);
        System.out.println(">> 8 קבוצות ישראליות חדשות נשמרו בהצלחה!");


        List<Match> generatedMatches = leagueService.generateLeagueSchedule(savedTeams);


        matchRepository.saveAll(generatedMatches);
        System.out.println(">> אלגוריתם רואנד-רובין הופעל בהצלחה! 28 משחקים הוכנסו ל-DB.");

        if (createDevAdmin) {
            seedAdminUser();
        }
    }

    private boolean shouldRunDevSeed() {
        return resetData && environment.acceptsProfiles(Profiles.of("dev"));
    }

    private void seedAdminUser() {
        String adminEmail = "admin@football.com";
        if (userRepository.findByEmail(adminEmail).isPresent()) {
            System.out.println(">> משתמש מנהל כבר קיים, מדלג על יצירה.");
            return;
        }

        User admin = new User();
        admin.setUsername("admin");
        admin.setEmail(adminEmail);
        admin.setPasswordHash(PASSWORD_ENCODER.encode("admin123"));
        admin.setBalance(1000000.0);
        admin.setRole("ADMIN");

        userRepository.save(admin);
        System.out.println(">> משתמש מנהל נוצר בהצלחה! email=admin@football.com | password=admin123");
    }

    private List<Team> createTeams() {
        return Arrays.asList(
                createTeam("מכבי חיפה", 89),
                createTeam("הפועל באר שבע", 91),
                createTeam("מכבי תל אביב", 90),
                createTeam("ביתר ירושלים", 92),
                createTeam("הפועל פתח תקווה", 85),
                createTeam("מכבי נתניה", 87),
                createTeam("הפועל תל אביב ", 85),
                createTeam("עירוני קרית שמונה", 82)
        );
    }

    private Team createTeam(String name, int skillLevel) {
        Team team = new Team();
        team.setName(name);
        team.setSkillLevel(skillLevel);
        team.setPoints(0);
        team.setGoalsFor(0);
        team.setGoalsAgainst(0);
        return team;
    }
}
