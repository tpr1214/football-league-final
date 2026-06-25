CREATE DATABASE IF NOT EXISTS football_league CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE football_league;

CREATE TABLE IF NOT EXISTS users (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     username VARCHAR(255) NOT NULL UNIQUE,
                                     password_hash VARCHAR(255) NOT NULL,
                                     email VARCHAR(255) NOT NULL UNIQUE,
                                     balance DOUBLE DEFAULT 0.0,
                                     role VARCHAR(50) NOT NULL DEFAULT 'USER',
                                     last_daily_bonus_date DATE,
                                     profile_image_url VARCHAR(2048),
                                     profile_image_link VARCHAR(2048),
                                     profile_link VARCHAR(2048)
);


CREATE TABLE IF NOT EXISTS teams (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     owner_id BIGINT,
                                     name VARCHAR(255) NOT NULL,
                                     skill_level INT NOT NULL,
                                     points INT DEFAULT 0,
                                     goals_for INT DEFAULT 0,
                                     goals_against INT DEFAULT 0,
                                     FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
                                     UNIQUE KEY uk_teams_owner_name (owner_id, name)
);


CREATE TABLE IF NOT EXISTS matches (
                                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                       owner_id BIGINT,
                                       home_team_id BIGINT NOT NULL,
                                       away_team_id BIGINT NOT NULL,
                                       cycle_number INT DEFAULT 1,
                                       round_number INT NOT NULL,
                                       home_score INT DEFAULT 0,
                                       away_score INT DEFAULT 0,
                                       expected_home_score INT DEFAULT 0,
                                       expected_away_score INT DEFAULT 0,
                                       status VARCHAR(50) DEFAULT 'PENDING',
                                       FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
                                       FOREIGN KEY (home_team_id) REFERENCES teams(id) ON DELETE CASCADE,
                                       FOREIGN KEY (away_team_id) REFERENCES teams(id) ON DELETE CASCADE
);


CREATE TABLE IF NOT EXISTS bets (
                                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                    user_id BIGINT NOT NULL,
                                    match_id BIGINT NOT NULL,
                                    predicted_outcome VARCHAR(50) NOT NULL,
                                    predicted_home_score INT,
                                    predicted_away_score INT,
                                    amount DOUBLE NOT NULL,
                                    odds DOUBLE NOT NULL,
                                    status VARCHAR(50) DEFAULT 'PENDING',
                                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                                    FOREIGN KEY (match_id) REFERENCES matches(id) ON DELETE CASCADE
);

SELECT * FROM users ORDER BY id DESC;
