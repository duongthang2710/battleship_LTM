CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    username VARCHAR(24) NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    display_name VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT username_format CHECK (username ~ '^[a-z0-9_]{3,24}$'),
    CONSTRAINT display_name_length CHECK (length(display_name) BETWEEN 1 AND 32)
);

CREATE TABLE IF NOT EXISTS matches (
    id UUID PRIMARY KEY,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    status VARCHAR(16) NOT NULL CHECK (status IN ('BATTLE', 'FINISHED', 'ABORTED')),
    end_reason VARCHAR(24) CHECK (end_reason IN ('FLEET_DESTROYED', 'TURN_TIMEOUT', 'PLAYER_LEFT')),
    CHECK ((status = 'BATTLE' AND ended_at IS NULL AND end_reason IS NULL)
        OR (status <> 'BATTLE' AND ended_at IS NOT NULL AND end_reason IS NOT NULL)),
    CHECK (ended_at IS NULL OR ended_at >= started_at)
);

CREATE TABLE IF NOT EXISTS match_players (
    match_id UUID NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    player_slot SMALLINT NOT NULL CHECK (player_slot IN (1, 2)),
    side VARCHAR(5) NOT NULL CHECK (side IN ('WHITE', 'BLACK')),
    result VARCHAR(8) CHECK (result IN ('VICTORY', 'DEFEAT', 'ABORTED')),
    shots INTEGER NOT NULL DEFAULT 0 CHECK (shots >= 0),
    hits INTEGER NOT NULL DEFAULT 0 CHECK (hits BETWEEN 0 AND shots),
    ships_sunk SMALLINT NOT NULL DEFAULT 0 CHECK (ships_sunk BETWEEN 0 AND 5),
    PRIMARY KEY (match_id, user_id),
    UNIQUE (match_id, player_slot)
);

CREATE INDEX IF NOT EXISTS match_players_user_id_idx ON match_players(user_id);
CREATE INDEX IF NOT EXISTS matches_started_at_idx ON matches(started_at DESC);
