package com.battleship.shared.friends;

import java.util.List;

public record FriendsResult(FriendsAction action, int page, int pageSize, long total,
                            List<FriendEntry> entries, String message) {
    public FriendsResult { entries = List.copyOf(entries); }
}
