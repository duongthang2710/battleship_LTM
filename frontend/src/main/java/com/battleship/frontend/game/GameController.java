package com.battleship.frontend.game;

import com.battleship.frontend.effects.AttackEffectManager;
import com.battleship.frontend.graphics.ship.ShipFactory;
import com.battleship.shared.game.GameEngine;
import com.battleship.shared.game.GamePhase;
import com.battleship.shared.game.GameState;
import com.battleship.shared.game.PlacementResult;
import com.battleship.shared.game.AttackResult;
import com.battleship.shared.game.Board;
import com.battleship.shared.game.Coordinate;
import com.battleship.shared.game.Ship;
import com.battleship.shared.game.ShipType;
import com.battleship.shared.game.Side;
import com.battleship.shared.game.Player;
import com.battleship.shared.game.PlacementState;
import com.battleship.shared.match.MatchSnapshot;
import com.battleship.frontend.pvp.PvpController;
import com.jme3.math.Vector2f;
import java.util.List;
import java.util.logging.Logger;

public final class GameController {
    private static final Logger LOG = Logger.getLogger(GameController.class.getName());
    private final GameEngine engine;
    private final BoardPicker picker;
    private final FleetRenderer fleet;
    private final PlacementFeedback feedback;
    private final PlacementHud controls;
    private final BattleHud hud;
    private final OwnBoardMinimap minimap;
    private final TargetingRenderer targeting;
    private final AttackEffectManager effects;
    private final GameOverOverlay gameOver;
    private final Runnable returnToMenu;
    private Coordinate hover;
    private ShipType draggedShip;
    private float dragHeight;
    private Vector2f dragCursor;
    private boolean enemyView;
    private boolean networkPlacement;
    private PvpController network;
    private GameState networkState;
    private Side ownSide = Side.WHITE;
    private boolean submitted;
    private Runnable leaveRoom = () -> { };
    private String message = "Choose a ship, then click a cell.";

    public GameController(GameEngine engine, BoardPicker picker, FleetRenderer fleet,
                          PlacementFeedback feedback, PlacementHud controls, BattleHud hud, OwnBoardMinimap minimap,
                          TargetingRenderer targeting, AttackEffectManager effects, GameOverOverlay gameOver) {
        this(engine, picker, fleet, feedback, controls, hud, minimap, targeting, effects, gameOver, () -> { });
    }

    public GameController(GameEngine engine, BoardPicker picker, FleetRenderer fleet,
                          PlacementFeedback feedback, PlacementHud controls, BattleHud hud, OwnBoardMinimap minimap,
                          TargetingRenderer targeting, AttackEffectManager effects, GameOverOverlay gameOver,
                          Runnable returnToMenu) {
        this.engine = engine;
        this.picker = picker;
        this.fleet = fleet;
        this.feedback = feedback;
        this.controls = controls;
        this.hud = hud;
        this.minimap = minimap;
        this.targeting = targeting;
        this.effects = effects;
        this.gameOver = gameOver;
        this.returnToMenu = returnToMenu;
        hud.setOpponentName(engine.state().enemy().name());
        hud.setConnectionStatus("Local game - no server required");
        refresh();
    }

    public GameState state() { return networkState == null ? engine.state() : networkState; }
    public boolean enemyView() { return enemyView; }
    public boolean attackAnimating() { return effects.isBusy(); }

    public void useLocalMode() {
        networkPlacement = false;
        network = null;
        networkState = null;
        submitted = false;
        ownSide = Side.WHITE;
        hud.setPlayerSide(null);
        minimap.setSide(null);
        gameOver.setNetworkMode(false);
        controls.setNetworkPlacement(false);
        hud.setOpponentName(state().enemy().name());
        hud.setConnectionStatus("Local game - no server required");
        refresh();
    }

    public void useNetworkPlacement(String opponent, String roomCode, Side side, PvpController network) {
        networkPlacement = true;
        this.network = network;
        this.leaveRoom = network::leaveRoom;
        this.ownSide = side;
        hud.setPlayerSide(side);
        networkState = null;
        submitted = false;
        minimap.setSide(side);
        gameOver.setNetworkMode(true);
        controls.setNetworkPlacement(true);
        hud.setOpponentName(opponent);
        hud.setConnectionStatus("PVP " + roomCode + " / " + side + " / Player 1 starts");
        message = side + " fleet. Place ships, then Ready.";
        refresh();
    }

    public void applyNetworkState(MatchSnapshot snapshot) {
        if (!networkPlacement) return;
        submitted = snapshot.fleetSubmitted();
        if (snapshot.phase() == GamePhase.PLACEMENT) {
            message = submitted ? "Fleet locked. Waiting for opponent." : "Opponent ready. Finish your fleet.";
            refreshHud();
            return;
        }
        GameState previous = state();
        networkState = new GameState(snapshot.phase(),
                new Player(snapshot.playerId(), previous.player().name(), snapshot.ownBoard().board()),
                new Player(snapshot.opponentId(), snapshot.opponentName(), snapshot.enemyBoard().board()),
                snapshot.currentPlayerId(), snapshot.winnerId(), snapshot.lastAttack(), PlacementState.initial(), true,
                snapshot.turnNumber());
        AttackResult attack = snapshot.lastAttack();
        effects.clear();
        enemyView = attack == null || attack.attackerId().equals(snapshot.playerId());
        message = attack == null ? "Battle started. Player 1 goes first." : attack.type() + " at " + cellName(attack.coordinate());
        refresh();
        if (attack != null) effects.play(attack, attack.sunk() ? attack.affectedCells() : List.of(),
                this::refresh, () -> {
                    if (state().phase() == GamePhase.GAME_OVER) gameOver.show(state());
                    else enemyView = state().currentPlayerId().equals(state().player().id());
                    refresh();
                });
    }

    public void networkError(String detail) {
        if (!networkPlacement) return;
        message = detail;
        refreshHud();
    }

    private boolean placementLocked() { return networkPlacement && (submitted || network.pending()); }

    private boolean canTarget(Coordinate cell) {
        return networkPlacement ? state().phase() == GamePhase.BATTLE && enemyView && !network.pending()
                && state().currentPlayerId().equals(state().player().id()) && !state().enemy().board().wasAttacked(cell)
                : engine.canAttack(viewAttacker(), cell);
    }

    public void update(float delta, Vector2f cursor) {
        effects.update(delta);
        update(cursor);
    }

    public void update(Vector2f cursor) {
        if (!effects.isBusy()) minimap.update(state().player().board());
        if (draggedShip != null) { dragShip(cursor); return; }
        hover = overHud(cursor) ? null : picker.pick(cursor).orElse(null);
        targeting.clear();
        if (state().phase() == GamePhase.PLACEMENT && !placementLocked() && hover != null
                && state().player().board().ship(state().placement().selectedType()) == null) {
            PlacementResult preview = engine.previewPlacement(hover);
            feedback.showPreview(preview.affectedCells(), preview.success());
        } else if (!effects.isBusy() && hover != null && canTarget(hover)) {
            feedback.clearPreview();
            targeting.show(hover);
        } else {
            feedback.clearPreview();
        }
    }

    public void primaryClick(Vector2f cursor) {
        if (effects.isBusy() || draggedShip != null) return;
        if (gameOver.mainMenuAt(cursor)) { returnToMenu.run(); return; }
        if (gameOver.restartAt(cursor)) { perform(PlacementHud.Action.RESTART); return; }
        var action = controls.actionAt(cursor);
        if (action.isPresent()) { perform(action.get()); return; }
        if (overHud(cursor)) return;
        update(cursor);
        if (state().phase() == GamePhase.PLACEMENT) {
            if (placementLocked()) return;
            var hit = picker.pickShip(cursor, fleet.root()).orElse(null);
            Ship placed = hit == null ? (hover == null ? null : state().player().board().shipAt(hover))
                    : state().player().board().ship(hit.type());
            if (placed != null) {
                engine.selectShip(placed.type());
                message = selectionMessage();
            } else if (hover != null && state().player().board().ship(state().placement().selectedType()) == null) {
                PlacementResult result = engine.clickPlacement(hover);
                message = result.success() ? selectionMessage() : failureMessage(result);
            } else {
                message = "Drag a placed ship to move it.";
            }
            refresh();
        }
        else if (state().phase() == GamePhase.BATTLE && hover != null) attack();
    }

    public boolean beginShipDrag(Vector2f cursor) {
        if (state().phase() != GamePhase.PLACEMENT || placementLocked() || effects.isBusy() || overHud(cursor)) return false;
        var hit = picker.pickShip(cursor, fleet.root()).orElse(null);
        if (hit == null) return false;
        engine.selectShip(hit.type());
        PlacementResult result = engine.clickPlacement(hit.cell());
        if (!result.success() || !state().placement().moving()) return false;
        draggedShip = hit.type();
        dragHeight = hit.height();
        dragCursor = cursor.clone();
        message = "Drag to move. Release to place.";
        refresh();
        return true;
    }

    public void dragShip(Vector2f cursor) {
        if (draggedShip == null) return;
        dragCursor = cursor.clone();
        hover = overHud(cursor) ? null : picker.pickAtHeight(cursor, dragHeight).orElse(null);
        targeting.clear();
        if (hover == null) {
            feedback.clearPreview();
            return;
        }
        var placement = state().placement();
        Coordinate anchor = hover.offset(-placement.grabOffset().row(), -placement.grabOffset().column());
        PlacementResult preview = engine.previewPlacement(hover);
        fleet.previewPlacement(draggedShip, anchor, placement.orientation());
        feedback.showPreview(preview.affectedCells(), preview.success());
        feedback.showSelection(preview.affectedCells().stream().filter(Board::inBounds).toList());
    }

    public void endShipDrag(Vector2f cursor) {
        if (draggedShip == null) return;
        dragShip(cursor);
        PlacementResult result = hover == null ? null : engine.clickPlacement(hover);
        ShipType type = draggedShip;
        draggedShip = null;
        dragCursor = null;
        engine.selectShip(type);
        message = result == null ? "Move cancelled. Ship kept in place."
                : result.success() ? "Ship moved. Drag again to adjust." : failureMessage(result);
        refresh();
    }

    public void cancelShipDrag() {
        if (draggedShip == null) return;
        ShipType type = draggedShip;
        draggedShip = null;
        dragCursor = null;
        engine.selectShip(type);
        message = "Move cancelled. Ship kept in place.";
        refresh();
    }

    public void restartLocalMatch() {
        cancelShipDrag();
        engine.restart();
        networkState = null;
        effects.clear();
        targeting.clear();
        gameOver.hide();
        enemyView = false;
        message = "New match. Deploy your fleet.";
        refresh();
    }

    private boolean overHud(Vector2f cursor) {
        return controls.contains(cursor) || hud.contains(cursor) || minimap.contains(cursor) || gameOver.contains(cursor);
    }

    public void perform(PlacementHud.Action action) {
        if (networkPlacement && action == PlacementHud.Action.LEAVE_ROOM) { cancelShipDrag(); leaveRoom.run(); return; }
        if (effects.isBusy()) return;
        if (networkPlacement && action == PlacementHud.Action.RESTART) { leaveRoom.run(); return; }
        if (networkPlacement && action == PlacementHud.Action.READY) {
            if (placementLocked()) {
                message = submitted ? "Fleet locked. Waiting for opponent." : "Submitting fleet...";
                refreshHud();
                return;
            }
            cancelShipDrag();
            if (state().canReady()) {
                message = network.submitFleet(state().player().board()) ? "Submitting fleet..." : "Connection unavailable.";
            } else message = submitted ? "Waiting for opponent." : "Place all five ships.";
            refreshHud();
            return;
        }
        if (state().phase() == GamePhase.PLACEMENT && placementLocked()) return;
        if (draggedShip != null && action != PlacementHud.Action.ROTATE) cancelShipDrag();
        if (action == PlacementHud.Action.RESTART
                || (action == PlacementHud.Action.READY && state().phase() == GamePhase.GAME_OVER)) {
            if (state().phase() == GamePhase.GAME_OVER) {
                restartLocalMatch();
            }
            return;
        }
        if (action == PlacementHud.Action.TOGGLE_BOARD) {
            if (state().phase() != GamePhase.PLACEMENT) {
                enemyView = !enemyView;
                feedback.clearPreview();
                refresh();
            }
            return;
        }
        if (state().phase() != GamePhase.PLACEMENT) return;
        PlacementResult result;
        switch (action) {
            case SELECT_CARRIER -> result = engine.selectShip(ShipType.CARRIER);
            case SELECT_BATTLESHIP -> result = engine.selectShip(ShipType.BATTLESHIP);
            case SELECT_CRUISER -> result = engine.selectShip(ShipType.CRUISER);
            case SELECT_SUBMARINE -> result = engine.selectShip(ShipType.SUBMARINE);
            case SELECT_DESTROYER -> result = engine.selectShip(ShipType.DESTROYER);
            case ROTATE -> result = engine.rotateSelected();
            case REMOVE -> result = engine.removeSelected();
            case RESET -> result = engine.resetFleet();
            case RANDOM -> result = engine.randomPlacement();
            case READY -> result = engine.ready();
            default -> { return; }
        }
        message = result.success() ? switch (action) {
            case ROTATE -> "Ship orientation updated.";
            case REMOVE -> state().placement().selectedType().displayName() + " removed.";
            case RESET -> "Fleet cleared. Place your ships.";
            case RANDOM -> "Random fleet ready. Adjust or Ready.";
            case READY -> "Your turn. Choose an enemy cell.";
            default -> selectionMessage();
        } : failureMessage(result);
        if (action == PlacementHud.Action.READY && result.success()) {
            enemyView = true;
            LOG.info("Local match: PLACEMENT -> BATTLE; both fleets complete");
        }
        if (action == PlacementHud.Action.ROTATE && !result.success() && draggedShip == null)
            engine.selectShip(state().placement().selectedType());
        refresh();
        if (dragCursor != null) dragShip(dragCursor);
    }

    private String selectionMessage() {
        return state().player().board().ship(state().placement().selectedType()) != null
                ? "Drag selected ship; T rotates."
                : state().placement().selectedType().displayName() + " - click a cell to place.";
    }

    private static String failureMessage(PlacementResult result) {
        return switch (result.reason()) {
            case OUT_OF_BOUNDS -> "Invalid: outside the 10x10 grid.";
            case OVERLAP -> "Invalid: overlapping another ship.";
            case PHASE_CLOSED -> "Ship placement is closed.";
            case SHIP_NOT_FOUND -> "Select a placed ship.";
            case FLEET_INCOMPLETE -> "Place all five ships before Ready.";
            case INVALID_ARGUMENT -> "Choose a ship and orientation.";
            case NONE -> "";
        };
    }

    private void attack() {
        if (networkPlacement) {
            if (!canTarget(hover)) { message = "Wait for your turn; target enemy waters."; refreshHud(); return; }
            if (network.attack(hover)) { message = "Volley sent. Awaiting server."; targeting.clear(); refreshHud(); }
            return;
        }
        AttackResult result = engine.attack(viewAttacker(), hover);
        if (!result.accepted()) {
            message = switch (result.type()) {
                case ALREADY_ATTACKED -> "This cell has already been attacked.";
                case INVALID_COORDINATE -> "Choose a cell inside the board.";
                case NOT_YOUR_TURN -> "Tab: target the other fleet this turn.";
                case NOT_IN_BATTLE, GAME_FINISHED -> "Battle is not active.";
                default -> "Attack rejected.";
            };
            refreshHud();
            return;
        }
        targeting.clear();
        feedback.clearPreview();
        hud.setGameStatus("Volley incoming", "Target " + cellName(result.coordinate()));
        LOG.info("Local attack: " + result.type() + " at " + result.coordinate());
        effects.play(result, result.sunk() ? result.affectedCells() : List.of(), () -> {
            message = result.sunk() ? result.shipType().displayName().toUpperCase(java.util.Locale.ROOT) + " SUNK"
                    : result.type() + " at " + cellName(result.coordinate());
            refresh();
            hud.setGameStatus(result.sunk() ? message : result.type().toString(), "Target " + cellName(result.coordinate()));
        }, () -> {
            if (state().phase() == GamePhase.GAME_OVER) gameOver.show(state());
            else enemyView = state().currentPlayerId().equals(state().player().id());
            refresh();
        });
    }

    private String viewAttacker() { return enemyView ? state().player().id() : state().enemy().id(); }

    private static String cellName(Coordinate cell) { return "" + (char) ('A' + cell.column()) + (cell.row() + 1); }

    private void refresh() {
        Board visible = enemyView ? state().enemy().board() : state().player().board();
        Side visibleSide = enemyView ? ownSide.opponent() : ownSide;
        fleet.display(visible, !enemyView, visibleSide == Side.WHITE ? ShipFactory.Fleet.RADIANT : ShipFactory.Fleet.ABYSSAL);
        feedback.displayAttacks(visible);
        var selected = state().player().board().ship(state().placement().selectedType());
        if (state().phase() == GamePhase.PLACEMENT && selected != null)
            feedback.showSelection(selected.occupiedCells());
        else feedback.clearSelection();
        feedback.clearPreview();
        targeting.clear();
        refreshHud();
    }

    private void refreshHud() {
        minimap.update(state().player().board());
        controls.setNetworkLocked(placementLocked());
        controls.update(state(), message, enemyView);
        hud.setControlHints(state().phase() == GamePhase.PLACEMENT
                ? "DRAG SHIP: MOVE / DRAG SEA: ORBIT / CLICK: PLACE / R: RESET"
                : "LEFT DRAG: ORBIT / CLICK: FIRE / SCROLL: ZOOM / R: RESET");
        if (state().phase() == GamePhase.PLACEMENT) {
            hud.setGameStatus(submitted ? "Waiting for opponent" : "Deploy your fleet", state().player().board().ships().size()
                    + "/5 ships - " + (networkPlacement ? ownSide + " fleet" : "cyan valid / red blocked"));
        } else if (state().phase() == GamePhase.GAME_OVER) {
            hud.setGameStatus("Game over", state().winnerId().equals(state().player().id()) ? "Your fleet wins." : "Enemy fleet wins.");
        } else {
            boolean ourTurn = state().currentPlayerId().equals(state().player().id());
            hud.setGameStatus(networkPlacement ? (ourTurn ? "Your turn / " + ownSide : "Opponent turn / " + ownSide.opponent())
                            : ourTurn ? "Radiant turn" : "Abyssal turn",
                    "Turn " + state().turnNumber() + (networkPlacement ? " / server synchronized"
                            : ourTurn ? " - fire at Abyssal waters." : " - fire at Radiant waters."));
        }
    }
}
