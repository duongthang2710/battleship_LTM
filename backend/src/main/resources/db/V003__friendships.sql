CREATE TABLE IF NOT EXISTS friendships (
    user_low UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_high UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    requester_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(10) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED', 'REMOVED')),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_low, user_high),
    CHECK (user_low < user_high),
    CHECK (requester_id IN (user_low, user_high))
);
CREATE INDEX IF NOT EXISTS friendships_high_status_idx ON friendships(user_high, status);
CREATE INDEX IF NOT EXISTS friendships_low_status_idx ON friendships(user_low, status);
