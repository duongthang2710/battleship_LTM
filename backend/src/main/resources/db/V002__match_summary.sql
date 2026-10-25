ALTER TABLE users ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ;
ALTER TABLE matches ADD COLUMN IF NOT EXISTS mode VARCHAR(4) NOT NULL DEFAULT 'PVP' CHECK (mode IN ('PVP', 'PVAI'));
ALTER TABLE matches ADD COLUMN IF NOT EXISTS winner_slot SMALLINT CHECK (winner_slot IN (1, 2));
ALTER TABLE matches ADD COLUMN IF NOT EXISTS total_turn INTEGER NOT NULL DEFAULT 0 CHECK (total_turn >= 0);
ALTER TABLE match_players ADD COLUMN IF NOT EXISTS player_type VARCHAR(5) NOT NULL DEFAULT 'HUMAN' CHECK (player_type IN ('HUMAN', 'AI'));

-- Preserve earlier games and derive missing summary fields from their recorded results.
UPDATE matches m SET winner_slot = p.player_slot
FROM match_players p WHERE p.match_id = m.id AND p.result = 'VICTORY' AND m.winner_slot IS NULL;
UPDATE matches m SET total_turn = s.shots
FROM (SELECT match_id, SUM(shots)::INTEGER AS shots FROM match_players GROUP BY match_id) s
WHERE s.match_id = m.id AND m.total_turn = 0 AND s.shots > 0;
