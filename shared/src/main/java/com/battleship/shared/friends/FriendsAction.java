package com.battleship.shared.friends;

public enum FriendsAction {
    SEARCH, FRIENDS, RECEIVED, SENT, SEND, ACCEPT, DECLINE, CANCEL, REMOVE;

    public boolean listing() { return this == SEARCH || this == FRIENDS || this == RECEIVED || this == SENT; }
}
