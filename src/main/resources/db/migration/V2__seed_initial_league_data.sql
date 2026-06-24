-- Production-safe initial league seed.
-- Does not delete, truncate, or reset any existing data. Flyway runs each
-- migration once, and the INSERT guards below avoid duplicate teams/matches if
-- the SQL is ever applied manually during recovery.

INSERT IGNORE INTO teams (name, skill_level, points, goals_for, goals_against) VALUES
    ('Maccabi Haifa', 89, 0, 0, 0),
    ('Hapoel Beer Sheva', 91, 0, 0, 0),
    ('Maccabi Tel Aviv', 90, 0, 0, 0),
    ('Beitar Jerusalem', 92, 0, 0, 0),
    ('Hapoel Petah Tikva', 85, 0, 0, 0),
    ('Maccabi Netanya', 87, 0, 0, 0),
    ('Hapoel Tel Aviv', 85, 0, 0, 0),
    ('Ironi Kiryat Shmona', 82, 0, 0, 0);

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
SELECT h.id, a.id, seed.round_number, 0, 0, seed.expected_home_score, seed.expected_away_score, 'PENDING'
FROM (
    SELECT 1 AS round_number, 'Maccabi Haifa' AS home_name, 'Ironi Kiryat Shmona' AS away_name, 2 AS expected_home_score, 1 AS expected_away_score
    UNION ALL SELECT 1, 'Hapoel Beer Sheva', 'Hapoel Tel Aviv', 2, 1
    UNION ALL SELECT 1, 'Maccabi Tel Aviv', 'Maccabi Netanya', 2, 1
    UNION ALL SELECT 1, 'Beitar Jerusalem', 'Hapoel Petah Tikva', 2, 1

    UNION ALL SELECT 2, 'Maccabi Haifa', 'Hapoel Tel Aviv', 2, 1
    UNION ALL SELECT 2, 'Ironi Kiryat Shmona', 'Maccabi Netanya', 1, 2
    UNION ALL SELECT 2, 'Hapoel Beer Sheva', 'Hapoel Petah Tikva', 2, 1
    UNION ALL SELECT 2, 'Maccabi Tel Aviv', 'Beitar Jerusalem', 1, 2

    UNION ALL SELECT 3, 'Maccabi Haifa', 'Maccabi Netanya', 2, 1
    UNION ALL SELECT 3, 'Hapoel Tel Aviv', 'Hapoel Petah Tikva', 1, 1
    UNION ALL SELECT 3, 'Ironi Kiryat Shmona', 'Beitar Jerusalem', 1, 2
    UNION ALL SELECT 3, 'Hapoel Beer Sheva', 'Maccabi Tel Aviv', 1, 1

    UNION ALL SELECT 4, 'Maccabi Haifa', 'Hapoel Petah Tikva', 2, 1
    UNION ALL SELECT 4, 'Maccabi Netanya', 'Beitar Jerusalem', 1, 2
    UNION ALL SELECT 4, 'Hapoel Tel Aviv', 'Maccabi Tel Aviv', 1, 2
    UNION ALL SELECT 4, 'Ironi Kiryat Shmona', 'Hapoel Beer Sheva', 1, 2

    UNION ALL SELECT 5, 'Maccabi Haifa', 'Beitar Jerusalem', 1, 2
    UNION ALL SELECT 5, 'Hapoel Petah Tikva', 'Maccabi Tel Aviv', 1, 2
    UNION ALL SELECT 5, 'Maccabi Netanya', 'Hapoel Beer Sheva', 1, 2
    UNION ALL SELECT 5, 'Hapoel Tel Aviv', 'Ironi Kiryat Shmona', 2, 1

    UNION ALL SELECT 6, 'Maccabi Haifa', 'Maccabi Tel Aviv', 1, 1
    UNION ALL SELECT 6, 'Beitar Jerusalem', 'Hapoel Beer Sheva', 1, 1
    UNION ALL SELECT 6, 'Hapoel Petah Tikva', 'Ironi Kiryat Shmona', 2, 1
    UNION ALL SELECT 6, 'Maccabi Netanya', 'Hapoel Tel Aviv', 2, 1

    UNION ALL SELECT 7, 'Maccabi Haifa', 'Hapoel Beer Sheva', 1, 1
    UNION ALL SELECT 7, 'Maccabi Tel Aviv', 'Ironi Kiryat Shmona', 2, 1
    UNION ALL SELECT 7, 'Beitar Jerusalem', 'Hapoel Tel Aviv', 2, 1
    UNION ALL SELECT 7, 'Hapoel Petah Tikva', 'Maccabi Netanya', 1, 2
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
