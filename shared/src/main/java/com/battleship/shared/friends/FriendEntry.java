package com.battleship.shared.friends;

import java.util.UUID;

/** Relationship is relative to the authenticated viewer. Times are UTC ISO-8601. */
public record FriendEntry(UUID userId, String username, String displayName, String relationship,
                          String requestedAt, boolean online) {}
