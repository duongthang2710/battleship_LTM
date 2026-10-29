package com.battleship.shared.game;


public record GameState(GamePhase phase, Player player, Player enemy, String currentPlayerId,
                        String winnerId, AttackResult lastAttack, PlacementState placement, boolean ready,
                        int turnNumber) {
    public boolean canReady(GameRules rules) {
        return phase == GamePhase.PLACEMENT && player.fleet().isComplete(rules);
    }
    public boolean canReady() {
        return phase == GamePhase.PLACEMENT && player.fleet().isComplete();
    }
}
