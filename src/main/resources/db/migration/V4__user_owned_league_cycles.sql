ALTER TABLE teams
    ADD COLUMN owner_id BIGINT NULL;

ALTER TABLE matches
    ADD COLUMN owner_id BIGINT NULL,
    ADD COLUMN cycle_number INTEGER NOT NULL DEFAULT 1;

ALTER TABLE teams
    DROP INDEX uk_teams_name;

ALTER TABLE teams
    ADD CONSTRAINT uk_teams_owner_name UNIQUE (owner_id, name),
    ADD CONSTRAINT fk_teams_owner FOREIGN KEY (owner_id) REFERENCES users (id);

ALTER TABLE matches
    ADD CONSTRAINT fk_matches_owner FOREIGN KEY (owner_id) REFERENCES users (id);
