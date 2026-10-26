package com.battleship.backend.friends;

import com.battleship.shared.friends.*;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Predicate;

public final class FriendsService {
    private final FriendsRepository repository;
    private final Predicate<UUID> online;

    public FriendsService(FriendsRepository repository, Predicate<UUID> online) {
        this.repository = repository; this.online = online;
    }

    public void requireFriends(UUID actor, UUID target) throws SQLException {
        if (target == null || actor.equals(target) || !repository.areFriends(actor, target))
            throw new IllegalArgumentException("Only an existing friend can be invited to battle.");
    }

    public FriendsResult handle(UUID actor, FriendsRequest request) throws SQLException {
        if (request == null || request.action() == null) throw new IllegalArgumentException("Missing friends action.");
        if (request.action().listing()) {
            if (request.page() < 0 || request.page() > 10000) throw new IllegalArgumentException("Invalid page.");
            String query = request.query() == null ? "" : request.query().strip().toLowerCase(Locale.ROOT);
            if (request.action() == FriendsAction.SEARCH && !query.matches("[a-z0-9_]{1,24}"))
                throw new IllegalArgumentException("Search by 1-24 username letters, numbers or underscore.");
            var result = repository.list(actor, new FriendsRequest(request.action(), null, query, request.page()));
            return new FriendsResult(result.action(), result.page(), result.pageSize(), result.total(), result.entries().stream()
                    .map(e -> new FriendEntry(e.userId(), e.username(), e.displayName(), e.relationship(), e.requestedAt(),
                            e.relationship().equals("FRIEND") && online.test(e.userId()))).toList(), "");
        }
        if (request.targetId() == null) throw new IllegalArgumentException("Choose a player first.");
        if (actor.equals(request.targetId())) throw new IllegalArgumentException("You cannot add yourself.");
        try { repository.change(actor, request.targetId(), request.action()); }
        catch (SQLException exception) {
            if ("23503".equals(exception.getSQLState())) throw new IllegalArgumentException("Player no longer exists.");
            throw exception;
        }
        String message = switch (request.action()) {
            case SEND -> "Invitation sent.";
            case ACCEPT -> "Invitation accepted. You are now friends.";
            case DECLINE -> "Invitation declined.";
            case CANCEL -> "Invitation cancelled.";
            case REMOVE -> "Friend removed.";
            default -> throw new IllegalArgumentException("Invalid friends action.");
        };
        return new FriendsResult(request.action(), 0, FriendsRepository.PAGE_SIZE, 0, List.of(), message);
    }
}
