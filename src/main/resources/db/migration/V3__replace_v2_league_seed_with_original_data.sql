-- Replace the incorrect V2 English-name league seed with the original local
-- project league data from DataInitializer#createTeams() and
-- LeagueService#generateLeagueSchedule().
--
-- Safety:
-- - Does not delete users, balances, or profiles.
-- - Deletes only bets that reference the wrong V2 English-name seeded matches.
-- - Removes only matches/teams created from the wrong V2 English team names.
-- - Re-inserts the original teams and the same 7-round / 28-match round-robin
--   structure used by the local dev DataInitializer.

DELETE b
FROM bets b
JOIN matches m ON m.id = b.match_id
JOIN teams h ON h.id = m.home_team_id
JOIN teams a ON a.id = m.away_team_id
WHERE h.name IN (
    'Maccabi Haifa',
    'Hapoel Beer Sheva',
    'Maccabi Tel Aviv',
    'Beitar Jerusalem',
    'Hapoel Petah Tikva',
    'Maccabi Netanya',
    'Hapoel Tel Aviv',
    'Ironi Kiryat Shmona'
)
   OR a.name IN (
    'Maccabi Haifa',
    'Hapoel Beer Sheva',
    'Maccabi Tel Aviv',
    'Beitar Jerusalem',
    'Hapoel Petah Tikva',
    'Maccabi Netanya',
    'Hapoel Tel Aviv',
    'Ironi Kiryat Shmona'
);

DELETE m
FROM matches m
JOIN teams h ON h.id = m.home_team_id
JOIN teams a ON a.id = m.away_team_id
WHERE h.name IN (
    'Maccabi Haifa',
    'Hapoel Beer Sheva',
    'Maccabi Tel Aviv',
    'Beitar Jerusalem',
    'Hapoel Petah Tikva',
    'Maccabi Netanya',
    'Hapoel Tel Aviv',
    'Ironi Kiryat Shmona'
)
   OR a.name IN (
    'Maccabi Haifa',
    'Hapoel Beer Sheva',
    'Maccabi Tel Aviv',
    'Beitar Jerusalem',
    'Hapoel Petah Tikva',
    'Maccabi Netanya',
    'Hapoel Tel Aviv',
    'Ironi Kiryat Shmona'
);

DELETE t
FROM teams t
WHERE t.name IN (
    'Maccabi Haifa',
    'Hapoel Beer Sheva',
    'Maccabi Tel Aviv',
    'Beitar Jerusalem',
    'Hapoel Petah Tikva',
    'Maccabi Netanya',
    'Hapoel Tel Aviv',
    'Ironi Kiryat Shmona'
)
AND NOT EXISTS (
    SELECT 1
    FROM matches m
    WHERE m.home_team_id = t.id OR m.away_team_id = t.id
);

INSERT IGNORE INTO teams (name, skill_level, points, goals_for, goals_against) VALUES
    ('מכבי חיפה', 89, 0, 0, 0),
    ('הפועל באר שבע', 91, 0, 0, 0),
    ('מכבי תל אביב', 90, 0, 0, 0),
    ('ביתר ירושלים', 92, 0, 0, 0),
    ('הפועל פתח תקווה', 85, 0, 0, 0),
    ('מכבי נתניה', 87, 0, 0, 0),
    ('הפועל תל אביב ', 85, 0, 0, 0),
    ('עירוני קרית שמונה', 82, 0, 0, 0);

INSERT INTO matches (
    home_team_id,
    away_team_id,
    round_number,
    home_score,
    away_score,
    expected_home_score,
    expected_away_score,
    status
)
SELECT
    h.id,
    a.id,
    seed.round_number,
    0,
    0,
    CAST(ROUND((RAND() * 2) + CASE WHEN h.skill_level > a.skill_level THEN 1.0 ELSE 0 END) AS SIGNED),
    CAST(ROUND((RAND() * 2) + CASE WHEN a.skill_level > h.skill_level THEN 1.0 ELSE 0 END) AS SIGNED),
    'PENDING'
FROM (
    SELECT 1 AS round_number, 'מכבי חיפה' AS home_name, 'עירוני קרית שמונה' AS away_name
    UNION ALL SELECT 1, 'הפועל באר שבע', 'הפועל תל אביב '
    UNION ALL SELECT 1, 'מכבי תל אביב', 'מכבי נתניה'
    UNION ALL SELECT 1, 'ביתר ירושלים', 'הפועל פתח תקווה'

    UNION ALL SELECT 2, 'מכבי חיפה', 'הפועל תל אביב '
    UNION ALL SELECT 2, 'עירוני קרית שמונה', 'מכבי נתניה'
    UNION ALL SELECT 2, 'הפועל באר שבע', 'הפועל פתח תקווה'
    UNION ALL SELECT 2, 'מכבי תל אביב', 'ביתר ירושלים'

    UNION ALL SELECT 3, 'מכבי חיפה', 'מכבי נתניה'
    UNION ALL SELECT 3, 'הפועל תל אביב ', 'הפועל פתח תקווה'
    UNION ALL SELECT 3, 'עירוני קרית שמונה', 'ביתר ירושלים'
    UNION ALL SELECT 3, 'הפועל באר שבע', 'מכבי תל אביב'

    UNION ALL SELECT 4, 'מכבי חיפה', 'הפועל פתח תקווה'
    UNION ALL SELECT 4, 'מכבי נתניה', 'ביתר ירושלים'
    UNION ALL SELECT 4, 'הפועל תל אביב ', 'מכבי תל אביב'
    UNION ALL SELECT 4, 'עירוני קרית שמונה', 'הפועל באר שבע'

    UNION ALL SELECT 5, 'מכבי חיפה', 'ביתר ירושלים'
    UNION ALL SELECT 5, 'הפועל פתח תקווה', 'מכבי תל אביב'
    UNION ALL SELECT 5, 'מכבי נתניה', 'הפועל באר שבע'
    UNION ALL SELECT 5, 'הפועל תל אביב ', 'עירוני קרית שמונה'

    UNION ALL SELECT 6, 'מכבי חיפה', 'מכבי תל אביב'
    UNION ALL SELECT 6, 'ביתר ירושלים', 'הפועל באר שבע'
    UNION ALL SELECT 6, 'הפועל פתח תקווה', 'עירוני קרית שמונה'
    UNION ALL SELECT 6, 'מכבי נתניה', 'הפועל תל אביב '

    UNION ALL SELECT 7, 'מכבי חיפה', 'הפועל באר שבע'
    UNION ALL SELECT 7, 'מכבי תל אביב', 'עירוני קרית שמונה'
    UNION ALL SELECT 7, 'ביתר ירושלים', 'הפועל תל אביב '
    UNION ALL SELECT 7, 'הפועל פתח תקווה', 'מכבי נתניה'
) seed
JOIN teams h ON h.name = seed.home_name
JOIN teams a ON a.name = seed.away_name
WHERE NOT EXISTS (
    SELECT 1
    FROM matches m
    WHERE m.round_number = seed.round_number
      AND m.home_team_id = h.id
      AND m.away_team_id = a.id
);
