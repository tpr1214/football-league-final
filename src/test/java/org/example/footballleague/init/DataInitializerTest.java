package org.example.footballleague.init;

import org.example.footballleague.Service.LeagueService;
import org.example.footballleague.model.Match;
import org.example.footballleague.model.Team;
import org.example.footballleague.repositories.BetRepository;
import org.example.footballleague.repositories.MatchRepository;
import org.example.footballleague.repositories.TeamRepository;
import org.example.footballleague.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Mock
    private TeamRepository teamRepository;
    @Mock
    private MatchRepository matchRepository;
    @Mock
    private BetRepository betRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LeagueService leagueService;

    @Test
    void defaultConfigurationSkipsDestructiveReset() {
        DataInitializer initializer = initializer(new MockEnvironment(), false, false);

        initializer.initData();

        verifyNoInteractions(betRepository, matchRepository, teamRepository, userRepository, leagueService);
    }

    @Test
    void resetFlagWithoutDevProfileStillSkipsDestructiveReset() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        DataInitializer initializer = initializer(environment, true, true);

        initializer.initData();

        verifyNoInteractions(betRepository, matchRepository, teamRepository, userRepository, leagueService);
    }

    @Test
    void devProfileWithResetFlagRunsExistingResetAndSeedFlow() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        DataInitializer initializer = initializer(environment, true, true);
        List<Match> generatedMatches = List.of(new Match());

        when(teamRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(leagueService.generateLeagueSchedule(anyList())).thenReturn(generatedMatches);
        when(matchRepository.saveAll(generatedMatches)).thenReturn(generatedMatches);
        when(userRepository.findByEmail("admin@football.com")).thenReturn(Optional.empty());

        initializer.initData();

        verify(betRepository).deleteAll();
        verify(matchRepository).deleteAll();
        verify(teamRepository).deleteAll();

        ArgumentCaptor<List<Team>> teamsCaptor = ArgumentCaptor.forClass(List.class);
        verify(teamRepository).saveAll(teamsCaptor.capture());
        assertEquals(8, teamsCaptor.getValue().size());

        verify(leagueService).generateLeagueSchedule(teamsCaptor.getValue());
        verify(matchRepository).saveAll(generatedMatches);
        verify(userRepository).findByEmail("admin@football.com");
        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(user ->
                "admin@football.com".equals(user.getEmail())
                        && "ADMIN".equals(user.getRole())
                        && PASSWORD_ENCODER.matches("admin123", user.getPasswordHash())));
    }

    @Test
    void devAdminIsNotCreatedUnlessAdminSeedFlagIsEnabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        DataInitializer initializer = initializer(environment, true, false);
        List<Match> generatedMatches = List.of(new Match());

        when(teamRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(leagueService.generateLeagueSchedule(anyList())).thenReturn(generatedMatches);
        when(matchRepository.saveAll(generatedMatches)).thenReturn(generatedMatches);

        initializer.initData();

        verify(userRepository, never()).findByEmail("admin@football.com");
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private DataInitializer initializer(MockEnvironment environment, boolean resetData, boolean createDevAdmin) {
        DataInitializer initializer = new DataInitializer(
                teamRepository,
                matchRepository,
                betRepository,
                userRepository,
                leagueService,
                environment
        );
        ReflectionTestUtils.setField(initializer, "resetData", resetData);
        ReflectionTestUtils.setField(initializer, "createDevAdmin", createDevAdmin);
        return initializer;
    }
}
