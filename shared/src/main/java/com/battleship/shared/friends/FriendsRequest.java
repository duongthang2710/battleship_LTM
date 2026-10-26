package com.battleship.shared.friends;

import java.util.UUID;

public record FriendsRequest(FriendsAction action, UUID targetId, String query, int page) {}
