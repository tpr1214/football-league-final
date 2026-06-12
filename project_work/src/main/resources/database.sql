-- 1. יצירת DB (אם הוא לא קיים) ומעבר אליו
CREATE DATABASE IF NOT EXISTS football_league_db;
USE football_league_db;


-- 2. יצירת טבלת משתמשים (Users)
CREATE TABLE IF NOT EXISTS users (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     username VARCHAR(255) NOT NULL UNIQUE,
                                     password VARCHAR(255) NOT NULL,
                                     email VARCHAR(255),
                                     role VARCHAR(50) DEFAULT 'USER'
);

-- 3. יצירת טבלת קבוצות (Teams)
CREATE TABLE IF NOT EXISTS teams (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     name VARCHAR(255) NOT NULL UNIQUE,
                                     skill_level INT NOT NULL,
                                     points INT DEFAULT 0,
                                     goals_for INT DEFAULT 0,
                                     goals_against INT DEFAULT 0
);

--  יצירת טבלת משחקים (Matches)
-- שימי לב: יש כאן מפתחות זרים (Foreign Keys) המקשרים לטבלת הקבוצות
CREATE TABLE IF NOT EXISTS matches (
                                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                       home_team_id BIGINT NOT NULL,
                                       away_team_id BIGINT NOT NULL,
                                       round_number INT NOT NULL,
                                       home_score INT DEFAULT 0,
                                       away_score INT DEFAULT 0,
                                       status VARCHAR(50) DEFAULT 'PENDING',
                                       FOREIGN KEY (home_team_id) REFERENCES teams(id) ON DELETE CASCADE,
                                       FOREIGN KEY (away_team_id) REFERENCES teams(id) ON DELETE CASCADE
);

-- 5. יצירת טבלת הימורים (Bets)
-- שימי לב: הטבלה הזו תלויה גם ב-users וגם ב-matches
CREATE TABLE IF NOT EXISTS bets (
                                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                    user_id BIGINT NOT NULL,
                                    match_id BIGINT NOT NULL,
                                    predicted_outcome VARCHAR(50) NOT NULL, -- למשל: HOME_WIN, AWAY_WIN, DRAW
                                    amount DOUBLE NOT NULL,
                                    odds DOUBLE NOT NULL,
                                    status VARCHAR(50) DEFAULT 'PENDING',  -- למשל: PENDING, WON, LOST
                                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                                    FOREIGN KEY (match_id) REFERENCES matches(id) ON DELETE CASCADE
);
USE football_league;
SELECT * FROM users ORDER BY id DESC;