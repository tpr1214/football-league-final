-- Baseline schema for the FootballBet backend, derived from the current JPA
-- entity mappings. Runs only under the default/production profile (Flyway is
-- disabled in dev and test). Created in FK-safe order: teams -> users ->
-- matches -> bets. Column names follow Spring Boot's default
-- CamelCaseToUnderscores naming strategy so Hibernate's ddl-auto=validate
-- accepts the result.

CREATE TABLE users (
    id                     BIGINT        NOT NULL AUTO_INCREMENT,
    username               VARCHAR(255)  NOT NULL,
    email                  VARCHAR(255)  NOT NULL,
    password_hash          VARCHAR(255),
    balance                DOUBLE,
    role                   VARCHAR(50)   NOT NULL DEFAULT 'USER',
    last_daily_bonus_date  DATE,
    profile_image_url      VARCHAR(2048),
    profile_image_link     VARCHAR(2048),
    profile_link           VARCHAR(2048),
    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB;

CREATE TABLE teams (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    owner_id       BIGINT,
    name           VARCHAR(255) NOT NULL,
    skill_level    INTEGER      NOT NULL,
    points         INTEGER      NOT NULL,
    goals_for      INTEGER      NOT NULL,
    goals_against  INTEGER      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_teams_owner_name UNIQUE (owner_id, name),
    CONSTRAINT fk_teams_owner FOREIGN KEY (owner_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE matches (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    owner_id             BIGINT,
    home_team_id         BIGINT       NOT NULL,
    away_team_id         BIGINT       NOT NULL,
    cycle_number         INTEGER      NOT NULL DEFAULT 1,
    round_number         INTEGER      NOT NULL,
    home_score           INTEGER      NOT NULL,
    away_score           INTEGER      NOT NULL,
    expected_home_score  INTEGER      NOT NULL,
    expected_away_score  INTEGER      NOT NULL,
    status               VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT fk_matches_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT fk_matches_home_team FOREIGN KEY (home_team_id) REFERENCES teams (id),
    CONSTRAINT fk_matches_away_team FOREIGN KEY (away_team_id) REFERENCES teams (id)
) ENGINE=InnoDB;

CREATE TABLE bets (
    id                    BIGINT  NOT NULL AUTO_INCREMENT,
    user_id               BIGINT  NOT NULL,
    match_id              BIGINT  NOT NULL,
    predicted_outcome     VARCHAR(255),
    amount                DOUBLE,
    odds                  DOUBLE,
    predicted_home_score  INTEGER,
    predicted_away_score  INTEGER,
    status                VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT fk_bets_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_bets_match FOREIGN KEY (match_id) REFERENCES matches (id)
) ENGINE=InnoDB;
