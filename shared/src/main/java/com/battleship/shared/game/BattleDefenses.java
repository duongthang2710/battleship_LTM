package com.battleship.shared.game;

import java.util.*;

/** Private defensive state. Counterattacks consume traps before resolving the next impact. */
final class BattleDefenses {
    record Resolution(Board attacker, Board defender, AttackResult result) { }
    private final Map<String, Set<Coordinate>> mines = new HashMap<>();
    private final Map<String, Map<Coordinate, Integer>> shields = new HashMap<>();

    List<Coordinate> mines(String player) { return List.copyOf(mines.getOrDefault(player, Set.of())); }
    List<ShieldCell> shields(String player) {
        return shields.getOrDefault(player, Map.of()).entrySet().stream()
                .map(entry -> new ShieldCell(entry.getKey(), entry.getValue())).toList();
    }
    boolean canMine(String player, Board board, Coordinate cell) {
        return board.shipAt(cell) == null && !board.wasAttacked(cell) && !mines(player).contains(cell);
    }
    void placeMine(String player, Coordinate cell) { mines.computeIfAbsent(player, key -> new HashSet<>()).add(cell); }
    void shield(String player, List<Coordinate> area) {
        var protection = shields.computeIfAbsent(player, key -> new HashMap<>());
        area.forEach(cell -> protection.put(cell, 2));
    }
    void finishAttack(String defender) {
        var protection = shields.get(defender);
        if (protection == null) return;
        protection.replaceAll((cell, remaining) -> remaining - 1);
        protection.values().removeIf(remaining -> remaining <= 0);
    }
    void clear() { mines.clear(); shields.clear(); }

    Resolution resolve(String attackerId, String defenderId, Board attacker, Board defender,
                       Coordinate cell, GameRules rules, Random random) {
        var protection = shields.get(defenderId);
        if (protection != null && protection.remove(cell) != null) {
            return new Resolution(attacker, defender, new AttackResult(AttackResultType.BLOCKED, cell, null,
                    false, false, attackerId, defenderId, null, List.of(cell), DefenseKind.SHIELD, List.of()));
        }
        var traps = mines.get(defenderId);
        if (traps != null && traps.remove(cell)) {
            defender = defender.withAttack(cell);
            List<Coordinate> open = new ArrayList<>();
            for (int row = 0; row < Board.SIZE; row++) for (int column = 0; column < Board.SIZE; column++) {
                Coordinate candidate = new Coordinate(row, column);
                if (!attacker.wasAttacked(candidate)) open.add(candidate);
            }
            List<AttackResult> counters = List.of();
            String winner = null;
            if (!open.isEmpty()) {
                Resolution counter = resolve(defenderId, attackerId, defender, attacker,
                        open.get(random.nextInt(open.size())), rules, random);
                attacker = counter.defender();
                defender = counter.attacker();
                counters = List.of(counter.result());
                winner = counter.result().winnerId();
            }
            return new Resolution(attacker, defender, new AttackResult(winner == null ? AttackResultType.BLOCKED : AttackResultType.GAME_OVER,
                    cell, null, false, winner != null, attackerId, defenderId, winner, List.of(cell), DefenseKind.MINE, counters));
        }
        Board damaged = defender.withAttack(cell);
        Ship target = damaged.shipAt(cell);
        boolean sunk = target != null && target.isSunk();
        String winner = damaged.fleet().isSunk(rules) ? attackerId : null;
        AttackResultType type = winner != null ? AttackResultType.GAME_OVER
                : target == null ? AttackResultType.MISS : sunk ? AttackResultType.SUNK : AttackResultType.HIT;
        return new Resolution(attacker, damaged, new AttackResult(type, cell, target == null ? null : target.type(),
                sunk, winner != null, attackerId, defenderId, winner, sunk ? target.occupiedCells() : List.of(cell)));
    }
}
