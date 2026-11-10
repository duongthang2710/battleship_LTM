package com.battleship.frontend.game;

import com.battleship.frontend.effects.AttackEffectManager;
import com.battleship.frontend.audio.GameAudio.Cue;
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
import com.battleship.shared.game.SkillDirection;
import com.battleship.shared.game.SkillResult;
import com.battleship.shared.game.SkillTargeting;
import com.battleship.shared.game.SkillType;
import com.battleship.shared.game.SonarReading;
import com.battleship.shared.match.MatchSnapshot;
import com.battleship.frontend.pvp.PvpController;
import com.jme3.math.Vector2f;
import java.util.List;
import java.util.logging.Logger;

public final class GameController {
    private static final Logger LOG = Logger.getLogger(GameController.class.getName());
    private final GameEngine engine;
    private com.battleship.frontend.ai.AiMatchController ai;
    public boolean aiMode() { return ai != null; }
    private final ShipDesignEditor designer;
    private boolean drawingMode;
    public ShipDesignEditor designer() { return designer; }
    public boolean designerMode() { return engine.mode() == com.battleship.shared.game.GameMode.DESIGNER; }
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
    private GameState beforeShot;
    private Side ownSide = Side.WHITE;
    private boolean submitted;
    private Runnable leaveRoom = () -> { };
    private Runnable askLeave;
    public void onLeave(Runnable askLeave) { this.askLeave=askLeave; }
    private java.util.function.Consumer<Cue> sound = cue -> { };
    public void onSound(java.util.function.Consumer<Cue> sound) { this.sound = sound; }
    private Board lastPlacementBoard;
    private boolean resultSoundPlayed;
    private boolean countingIntoBattle;
    private float startFlash;
    private int soundedTurn = -1, warnedTurn = -1;
    private boolean placementSoundHandled;
    private Runnable openSettings = () -> { };
    public void onSettings(Runnable action) { openSettings = action; }
    private String message = "Choose a ship, then click a cell.";
    private SkillType armedSkill;
    private SkillDirection torpedoDirection = SkillDirection.EAST;
    private String skillHint = "";

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
        designer = new ShipDesignEditor(engine);
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
        refresh();
    }

    public GameState state() { return ai != null ? ai.state() : networkState == null ? engine.state() : networkState; }
    public boolean enemyView() { return enemyView; }
    public boolean attackAnimating() { return effects.isBusy(); }
    public boolean inputBusy() { return effects.isBusy() || introBlocked(); }

    public void useLocalMode() {
        ai = null; controls.setAiMode(false);
        soundedTurn = warnedTurn = -1;
        effects.clear(); beforeShot = null; lastPlacementBoard = null; resultSoundPlayed = false;
        countingIntoBattle = false; startFlash = 0; hud.setStartCountdown(0, false);
        networkPlacement = false;
        clearSkills();
        designer.clear(); drawingMode = false;
        engine.restart(); engine.setRules(com.battleship.shared.game.GameRules.DEFAULT);
        network = null;
        networkState = null;
        submitted = false;
        hud.hideTurnClock();
        ownSide = Side.WHITE;
        hud.setPlayerSide(null);
        hud.setPlayerName(engine.state().player().name());
        minimap.setSide(null);
        gameOver.setNetworkMode(false);
        controls.setNetworkPlacement(false);
        hud.setOpponentName(state().enemy().name());
        refresh();
    }

    public void useAiMode(com.battleship.shared.ai.AiDifficulty difficulty, String playerName) {
        useAiMode(difficulty, playerName, System::nanoTime);
    }

    void useAiMode(com.battleship.shared.ai.AiDifficulty difficulty, String playerName, java.util.function.LongSupplier nanoTime) {
        useLocalMode();
        ai = new com.battleship.frontend.ai.AiMatchController(engine, difficulty, playerName, new java.util.Random(), nanoTime);
        restartLocalMatch();
        controls.setAiMode(true);
        hud.setFleetReportVisible(true);
        hud.setPlayerName(playerName); hud.setOpponentName("AI " + difficulty.label());
        ownSide = Side.WHITE; hud.setPlayerSide(ownSide); minimap.setSide(ownSide);
        message = "AI " + difficulty.label() + ". Deploy your fleet.";
        refresh();
    }

    public void useNetworkPlacement(String opponent, String roomCode, Side side, PvpController network) {
        useNetworkPlacement(opponent, roomCode, side, com.battleship.shared.game.GameMode.CLASSIC, network);
    }
    public void useNetworkPlacement(String opponent, String roomCode, Side side, com.battleship.shared.game.GameMode mode, PvpController network) {
        useNetworkPlacement(opponent, roomCode, side, new com.battleship.shared.game.GameRules(mode, 5,
                com.battleship.shared.game.TurnRule.ALTERNATING), network);
    }
    public void useNetworkPlacement(String opponent, String roomCode, Side side, com.battleship.shared.game.GameRules rules, PvpController network) {
        ai = null; controls.setAiMode(false);
        soundedTurn = warnedTurn = -1;
        effects.clear(); beforeShot = null; lastPlacementBoard = null; resultSoundPlayed = false;
        countingIntoBattle = false; startFlash = 0; hud.setStartCountdown(0, false);
        engine.setRules(rules); designer.clear(); drawingMode = designerMode();
        clearSkills();
        var options=network.room()==null?com.battleship.shared.room.RoomOptions.DEFAULT:network.room().options();
        hud.setFleetReportVisible(!options.hideSunkInfo());
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
        hud.setPlayerName(network.account().displayName());
        hud.setOpponentName(opponent);
        message = designerMode() ? "Draw connected cells. Right click erases." : side + " fleet. Place ships, then Ready.";
        refresh();
    }

    public void applyNetworkState(MatchSnapshot snapshot) {
        if (!networkPlacement) return;
        submitted = snapshot.fleetSubmitted();
        gameOver.setEndReason(snapshot.endReason());
        if (snapshot.phase() == GamePhase.PLACEMENT) {
            message = submitted ? "Fleet locked. Waiting for opponent." : snapshot.opponentSubmitted()
                    ? "Opponent confirmed fleet. Finish yours." : "Deploy your fleet, then Ready.";
            refreshHud();
            return;
        }
        GameState previous = state();
        networkState = new GameState(snapshot.phase(),
                new Player(snapshot.playerId(), network.account().displayName(), snapshot.ownBoard().board()),
                new Player(snapshot.opponentId(), snapshot.opponentName(), snapshot.enemyBoard().board()),
                snapshot.currentPlayerId(), snapshot.winnerId(), snapshot.lastAttack(), PlacementState.initial(), true,
                snapshot.turnNumber());
        SkillResult skill = snapshot.skills() == null ? null : snapshot.skills().lastSkill();
        if (!snapshot.currentPlayerId().equals(snapshot.playerId()) || snapshot.phase() != GamePhase.BATTLE) armedSkill = null;
        if (skill != null) skillHint = skillSummary(skill, snapshot.playerId());
        // A skill volley is shown as one lead projectile; every affected cell appears on impact.
        AttackResult attack = skill != null ? leadImpact(skill) : snapshot.lastAttack();
        effects.clear();
        if (snapshot.phase() == GamePhase.GAME_OVER && snapshot.endReason() != com.battleship.shared.match.MatchEndReason.FLEET_DESTROYED) {
            if (snapshot.timedOut()) sound.accept(Cue.TIMEOUT);
            message = snapshot.timedOut() ? "Turn time expired." : "A player left the match.";
            refresh();
            showGameOver();
            return;
        }
        String shooter = skill != null ? skill.attackerId() : attack == null ? null : attack.attackerId();
        enemyView = skill != null && attack == null ? state().currentPlayerId().equals(snapshot.playerId())
                : shooter == null || shooter.equals(snapshot.playerId());
        message = skill != null ? skillHint : attack == null ? "Battle started. Player 1 goes first." : attack.type() + " at " + cellName(attack.coordinate());
        if (skill != null && attack == null) sound.accept(Cue.TARGET);
        beforeShot = previous;
        if (attack != null) sound.accept(Cue.CANNON);
        if (attack != null) effects.play(attack, attack.sunk() ? attack.affectedCells() : List.of(),
                attack.attackerId().equals(snapshot.playerId()) ? snapshot.side() : snapshot.side().opponent(),
                () -> { impactSound(attack); refresh(); }, () -> {
                    if (state().phase() == GamePhase.GAME_OVER) showGameOver();
                    else enemyView = state().currentPlayerId().equals(state().player().id());
                    refresh();
                });
        refresh();
    }

    public void networkError(String detail) {
        if (!networkPlacement) return;
        message = detail;
        if (skillMode() && state().phase() == GamePhase.BATTLE) skillHint = detail;
        refreshHud();
    }

    private boolean placementLocked() { return networkPlacement && (submitted || network.pending()); }

    private boolean canTarget(Coordinate cell) {
        if (ai != null) return enemyView && !introBlocked() && ai.playerCanAttack(cell);
        return networkPlacement ? state().phase() == GamePhase.BATTLE && !introBlocked() && enemyView && !network.pending()
                && state().currentPlayerId().equals(state().player().id()) && !state().enemy().board().wasAttacked(cell)
                : engine.canAttack(viewAttacker(), cell);
    }

    public void update(float delta, Vector2f cursor) {
        hud.update(delta);
        updateIntro(delta);
        effects.update(delta);
        if (ai != null) {
            GameState before = state();
            AttackResult botShot = ai.updateBot(!effects.isBusy() && !introBlocked());
            if (state().phase() == GamePhase.GAME_OVER
                    && ai.endReason() == com.battleship.shared.match.MatchEndReason.TURN_TIMEOUT && !resultSoundPlayed) {
                effects.clear(); beforeShot = null; targeting.clear();
                sound.accept(Cue.TIMEOUT);
                gameOver.setEndReason(ai.endReason()); refresh(); showGameOver();
            } else if (botShot != null) { beforeShot = before; enemyView = false; playLocalAttack(botShot); }
        }
        if (effects.isBusy()) refreshShotStatus();
        update(cursor);
    }

    public void update(Vector2f cursor) {
        updateTurnClock();
        if (networkPlacement && state().phase() == GamePhase.GAME_OVER) {
            var rematch = network.rematch();
            boolean available = network.room() != null && network.room().players().size() == 2
                    && (rematch == null || rematch.available());
            boolean busy = network.pending() || network.returningToMenu();
            gameOver.setRematchStatus(network.rematchStatus(), available && !busy
                            && (rematch == null || !rematch.acceptedPlayerIds().contains(network.clientId())),
                    available && !busy && rematch != null && !rematch.acceptedPlayerIds().isEmpty());
        }
        if (!effects.isBusy()) minimap.update(state().player().board());
        if (draggedShip != null) { dragShip(cursor); return; }
        hover = overHud(cursor) ? null : picker.pick(cursor).orElse(null);
        if (skillMode()) refreshSkillTray();
        targeting.clear();
        if (state().phase() == GamePhase.PLACEMENT && designerMode() && !placementLocked() && drawingMode) {
            if (designer.hasDraft()) feedback.showPreview(designer.cells(), designer.validation().success());
            else if (hover != null) feedback.showPreview(List.of(hover), state().player().board().shipAt(hover) == null);
            else feedback.clearPreview();
        } else if (state().phase() == GamePhase.PLACEMENT && !designerMode() && !placementLocked() && hover != null
                && state().player().board().ship(state().placement().selectedType()) == null) {
            PlacementResult preview = engine.previewPlacement(hover);
            feedback.showPreview(preview.affectedCells(), preview.success());
        } else if (armedSkill != null && state().phase() == GamePhase.BATTLE && enemyView && hover != null && !effects.isBusy()) {
            feedback.showPreview(skillPreview(armedSkill, hover), canUseSkills() && energy() >= armedSkill.cost());
        } else if (!effects.isBusy() && hover != null && canTarget(hover)) {
            feedback.clearPreview();
            targeting.show(hover);
        } else {
            feedback.clearPreview();
        }
    }

    public void primaryClick(Vector2f cursor) {
        if (controls.actionAt(cursor).filter(a -> a == PlacementHud.Action.SETTINGS).isPresent()) {
            perform(PlacementHud.Action.SETTINGS); return;
        }
        if (introBlocked() || effects.isBusy() || draggedShip != null) return;
        if (gameOver.mainMenuAt(cursor)) {
            if (networkPlacement) network.returnToMainMenu(); else returnToMenu.run();
            return;
        }
        if (gameOver.declineAt(cursor)) { network.declineRematch(); return; }
        if (gameOver.restartAt(cursor)) { perform(PlacementHud.Action.RESTART); return; }
        var action = controls.actionAt(cursor);
        if (action.isPresent()) { perform(action.get()); return; }
        if (overHud(cursor)) return;
        update(cursor);
        if (state().phase() == GamePhase.PLACEMENT) {
            if (placementLocked()) return;
            if (designerMode() && drawingMode) return;
            var hit = picker.pickShip(cursor, fleet.root()).orElse(null);
            Ship placed = hit == null ? (hover == null ? null : state().player().board().shipAt(hover))
                    : state().player().board().ship(hit.type());
            if (placed != null) {
                engine.selectShip(placed.type());
                message = selectionMessage();
            } else if (!designerMode() && hover != null && state().player().board().ship(state().placement().selectedType()) == null) {
                PlacementResult result = engine.clickPlacement(hover);
                if (!result.success()) sound.accept(Cue.ERROR);
                message = result.success() ? selectionMessage() : failureMessage(result);
            } else {
                message = "Drag a placed ship to move it.";
            }
            refresh();
        }
        else if (state().phase() == GamePhase.BATTLE && hover != null) attack();
    }

    public boolean beginDrawing(Vector2f cursor, boolean erase) {
        if (!designerMode() || !drawingMode || state().phase() != GamePhase.PLACEMENT || placementLocked() || overHud(cursor)) return false;
        Coordinate cell = picker.pick(cursor).orElse(null);
        if (cell == null) return false;
        Ship placed = state().player().board().shipAt(cell);
        if (!designer.hasDraft() && placed != null && placed.type() != state().placement().selectedType()) engine.selectShip(placed.type());
        designer.beginStroke(); designer.paint(cell, erase); refresh();
        return true;
    }
    public void drawAt(Vector2f cursor, boolean erase) {
        if (!designer.hasDraft() || overHud(cursor)) return;
        picker.pick(cursor).ifPresent(cell -> { if (designer.paint(cell, erase)) refresh(); });
    }
    public void endDrawing() {
        designer.endStroke();
        if (!designer.hasDraft()) return;
        if (designer.cells().isEmpty()) {
            if (state().player().board().ship(designer.type()) != null) engine.removeSelected();
            designer.clear(); message = "Hull cleared.";
        } else {
            PlacementResult result = designer.commit();
            if (result.success()) {
                message = "Hull built. G switches to move mode.";
                for (ShipType type : engine.rules().shipTypes()) if (state().player().board().ship(type) == null) { engine.selectShip(type); break; }
            } else message = result.reason() == PlacementResult.Failure.WRONG_SIZE
                    ? "Draw " + state().placement().selectedType().length() + " cells; right click erases." : failureMessage(result);
        }
        refresh();
    }
    public void cancelDrawingStroke() { designer.endStroke(); }

    public boolean beginShipDrag(Vector2f cursor) {
        if (state().phase() != GamePhase.PLACEMENT || placementLocked() || effects.isBusy() || overHud(cursor) || designerMode() && drawingMode) return false;
        var hit = picker.pickShip(cursor, fleet.root()).orElse(null);
        if (hit == null) return false;
        engine.selectShip(hit.type());
        Ship picked = state().player().board().ship(hit.type());
        Coordinate grab = picked.occupiedCells().contains(hit.cell()) ? hit.cell() : picked.occupiedCells().stream()
                .min(java.util.Comparator.comparingInt(c -> Math.abs(c.row() - hit.cell().row()) + Math.abs(c.column() - hit.cell().column()))).orElseThrow();
        PlacementResult result = engine.clickPlacement(grab);
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
        if (result != null && !result.success()) sound.accept(Cue.ERROR);
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
        soundedTurn = warnedTurn = -1;
        cancelShipDrag();
        engine.restart();
        if (ai != null) ai.reset();
        countingIntoBattle = false; startFlash = 0; hud.setStartCountdown(0, false);
        gameOver.setEndReason(null);
        lastPlacementBoard = null; resultSoundPlayed = false;
        designer.clear(); drawingMode = designerMode();
        clearSkills();
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
        if (action == PlacementHud.Action.SETTINGS) { sound.accept(Cue.CLICK); cancelShipDrag(); openSettings.run(); return; }
        if (skillMode() && state().phase() == GamePhase.BATTLE && skillAction(action)) return;
        if (action.name().startsWith("SELECT_")
                && !engine.rules().allows(ShipType.valueOf(action.name().substring(7)))) return;
        if (networkPlacement && action == PlacementHud.Action.LEAVE_ROOM) { cancelShipDrag(); if(askLeave!=null) askLeave.run(); else leaveRoom.run(); return; }
        if (ai != null && action == PlacementHud.Action.LEAVE_ROOM) { cancelShipDrag(); if(askLeave!=null) askLeave.run(); else returnToMenu.run(); return; }
        if (introBlocked()) return;
        if (effects.isBusy()) return;
        if (action == PlacementHud.Action.MORE) { sound.accept(Cue.CLICK); controls.toggleMore(); return; }
        if (action.name().startsWith("SELECT_") || action == PlacementHud.Action.TOGGLE_BOARD || action == PlacementHud.Action.TOGGLE_DRAW) sound.accept(Cue.CLICK);
        if (networkPlacement && (action == PlacementHud.Action.RESTART
                || (action == PlacementHud.Action.READY && state().phase() == GamePhase.GAME_OVER))) {
            if (state().phase() == GamePhase.GAME_OVER) network.requestRematch();
            return;
        }
        if (networkPlacement && action == PlacementHud.Action.READY) {
            if (placementLocked()) {
                message = submitted ? "Fleet locked. Waiting for opponent." : "Submitting fleet...";
                refreshHud();
                return;
            }
            cancelShipDrag();
            if (designer.hasDraft()) { sound.accept(Cue.ERROR); message = "Finish or clear your draft before Ready."; refreshHud(); return; }
            if (engine.canReady()) {
                boolean sent = network.submitFleet(state().player().board());
                message = sent ? "Submitting fleet..." : "Connection unavailable.";
                if (sent) sound.accept(Cue.READY);
            } else { sound.accept(Cue.ERROR); message = submitted ? "Waiting for opponent." : "Place all " + engine.rules().shipCount() + " ships."; }
            refreshHud();
            return;
        }
        if (state().phase() == GamePhase.PLACEMENT && placementLocked()) return;
        if (designerMode() && action == PlacementHud.Action.TOGGLE_DRAW && state().phase() == GamePhase.PLACEMENT) {
            cancelShipDrag(); designer.clear(); drawingMode = !drawingMode;
            message = drawingMode ? "Left drag draws; right drag erases." : "Drag ships to move; T rotates.";
            refresh(); return;
        }
        if (designerMode() && action == PlacementHud.Action.REMOVE && designer.hasDraft()) { sound.accept(Cue.REMOVE); designer.clear(); message = "Draft cancelled."; refresh(); return; }
        if (designerMode() && action == PlacementHud.Action.ROTATE && draggedShip != null) cancelShipDrag();
        if (designerMode() && action == PlacementHud.Action.ROTATE && designer.hasDraft()) { message = "Finish or clear the draft before rotating."; refreshHud(); return; }
        if (action.name().startsWith("SELECT_") || action == PlacementHud.Action.RESET || action == PlacementHud.Action.RANDOM) designer.clear();
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
            case READY -> result = ai == null ? engine.ready() : ai.ready();
            default -> { return; }
        }
        if (!result.success()) sound.accept(Cue.ERROR);
        else if (action == PlacementHud.Action.ROTATE || action == PlacementHud.Action.REMOVE
                || action == PlacementHud.Action.RESET || action == PlacementHud.Action.RANDOM) {
            sound.accept(switch (action) {
                case ROTATE -> Cue.ROTATE;
                case RANDOM -> Cue.RANDOM;
                default -> Cue.REMOVE;
            });
            placementSoundHandled = true;
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
            enemyView = true; sound.accept(Cue.READY);
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
            case FLEET_INCOMPLETE -> "Place every required ship before Ready.";
            case INVALID_ARGUMENT -> "Choose a ship and orientation.";
            case WRONG_SIZE -> "Hull must use exactly the selected ship's cells.";
            case DISCONNECTED -> "Connect all cells edge-to-edge.";
            case DUPLICATE_CELLS -> "A ship cannot repeat a cell.";
            case MODE_MISMATCH -> "Choose Fleet designer to draw ships.";
            case NONE -> "";
        };
    }

    private void attack() {
        if (networkPlacement && armedSkill != null) { fireSkill(); return; }
        if (networkPlacement) {
            if (!canTarget(hover)) { message = "Wait for your turn; target enemy waters."; refreshHud(); return; }
            if (network.attack(hover)) { sound.accept(Cue.TARGET); message = "Volley sent. Awaiting server."; targeting.clear(); refreshHud(); }
            return;
        }
        beforeShot = state();
        if (ai != null && !canTarget(hover)) { message = "Wait for your turn; target enemy waters."; refreshHud(); return; }
        AttackResult result = ai == null ? engine.attack(viewAttacker(), hover) : ai.attackPlayer(hover);
        if (result == null) return;
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
        playLocalAttack(result);
    }

    private void playLocalAttack(AttackResult result) {
        targeting.clear();
        feedback.clearPreview();
        hud.setGameStatus("Volley incoming", "Target " + cellName(result.coordinate()));
        LOG.info("Local attack: " + result.type() + " at " + result.coordinate());
        sound.accept(Cue.CANNON);
        effects.play(result, result.sunk() ? result.affectedCells() : List.of(),
                result.attackerId().equals(state().player().id()) ? ownSide : ownSide.opponent(), () -> {
            impactSound(result);
            message = result.sunk() ? result.shipType().displayName().toUpperCase(java.util.Locale.ROOT) + " SUNK"
                    : result.type() + " at " + cellName(result.coordinate());
            refresh();
            hud.setGameStatus(result.sunk() ? message : result.type().toString(), "Target " + cellName(result.coordinate()));
        }, () -> {
            if (state().phase() == GamePhase.GAME_OVER) showGameOver();
            else enemyView = state().currentPlayerId().equals(state().player().id());
            refresh();
        });
        if (ai != null) refresh();
    }

    private String viewAttacker() { return enemyView ? state().player().id() : state().enemy().id(); }

    private static String cellName(Coordinate cell) { return "" + (char) ('A' + cell.column()) + (cell.row() + 1); }

    private void impactSound(AttackResult shot) {
        sound.accept(shot.sunk() ? Cue.SUNK : shot.type() == com.battleship.shared.game.AttackResultType.MISS ? Cue.MISS : Cue.HIT);
    }
    private void showGameOver() {
        if (ai != null) gameOver.setEndReason(ai.endReason());
        gameOver.show(state());
        if (!resultSoundPlayed) {
            resultSoundPlayed = true;
            sound.accept(state().player().id().equals(state().winnerId()) ? Cue.VICTORY : Cue.DEFEAT);
        }
    }
    private void refresh() {
        if (state().phase() == GamePhase.PLACEMENT) {
            Board placement = state().player().board();
            if (!placementSoundHandled && lastPlacementBoard != null && lastPlacementBoard != placement) sound.accept(Cue.PLACE);
            lastPlacementBoard = placement;
        }
        placementSoundHandled = false;
        GameState shown = effects.isBusy() && !effects.hasImpacted() && beforeShot != null ? beforeShot : state();
        Board visible = enemyView ? shown.enemy().board() : shown.player().board();
        Side visibleSide = enemyView ? ownSide.opponent() : ownSide;
        fleet.setEditing(designer.hasDraft() ? designer.type() : null);
        boolean revealEnemyFleet = networkPlacement && network.room() != null
                && network.room().options().hideSunkInfo() && shown.phase() == GamePhase.GAME_OVER;
        fleet.display(visible, !enemyView || revealEnemyFleet,
                visibleSide == Side.WHITE ? ShipFactory.Fleet.RADIANT : ShipFactory.Fleet.ABYSSAL);
        feedback.displayAttacks(visible);
        var selected = state().player().board().ship(state().placement().selectedType());
        if (state().phase() == GamePhase.PLACEMENT && selected != null)
            feedback.showSelection(selected.occupiedCells());
        else if (skillMode() && enemyView && state().phase() != GamePhase.PLACEMENT && !scans().isEmpty())
            feedback.showSelection(scans().stream().flatMap(scan -> scan.area().stream()).distinct().toList());
        else feedback.clearSelection();
        feedback.clearPreview();
        if (designer.hasDraft()) feedback.showPreview(designer.cells(), designer.validation().success());
        targeting.clear();
        refreshHud();
    }

    private void refreshHud() {
        updateTurnClock();
        if (networkPlacement && network.room() != null) {
            hud.setFleetReportVisible(!network.room().options().hideSunkInfo() || state().phase() == GamePhase.GAME_OVER);
        }
        hud.setBattleState(state(), engine.rules());
        hud.setOpponentReady(ai != null || networkPlacement && network.match() != null && network.match().opponentSubmitted());
        minimap.setBattleVisible(state().phase() != GamePhase.PLACEMENT);
        GameState shown = effects.isBusy() && !effects.hasImpacted() && beforeShot != null ? beforeShot : state();
        minimap.update(shown.player().board());
        controls.setNetworkLocked(placementLocked());
        controls.setRules(engine.rules());
        controls.setDesigner(designerMode(), drawingMode, designer.cells().size(), designer.hasDraft());
        controls.update(state(), message, enemyView);
        refreshSkillTray();
        if (effects.isBusy()) { refreshShotStatus(); return; }
        hud.clearShotResult();
        if (state().phase() == GamePhase.PLACEMENT) {
            hud.setGameStatus(submitted ? "Fleet confirmed" : "Ship placement", submitted ? "Waiting for opponent"
                    : state().player().board().ships().size() + " / " + engine.rules().shipCount() + " ships ready");
        } else if (state().phase() == GamePhase.GAME_OVER) {
            hud.setGameStatus(ai != null && ai.endReason() == com.battleship.shared.match.MatchEndReason.TURN_TIMEOUT
                    || networkPlacement && network.match() != null && network.match().timedOut() ? "Time expired" : "Game over",
                    state().winnerId().equals(state().player().id()) ? "Your fleet wins." : "Enemy fleet wins.");
        } else {
            boolean ourTurn = state().currentPlayerId().equals(state().player().id());
            hud.setGameStatus(networkPlacement || ai != null ? (ourTurn ? "Your turn" : "Opponent turn")
                            : ourTurn ? "Radiant turn" : "Abyssal turn",
                    ourTurn ? "Choose a target" : "Awaiting enemy volley");
        }
    }

    private void refreshShotStatus() {
        AttackResult shot = effects.result();
        if (shot == null) return;
        String title = !effects.hasImpacted() ? "Volley incoming"
                : shot.sunk() ? "Ship sunk" : shot.type().toString();
        hud.setShotResult(effects.hasImpacted() ? shot.sunk() ? "SUNK!" : shot.type().toString() : "",
                shot.attackerId().equals(state().player().id()));
        hud.setGameStatus(title, "Target " + cellName(shot.coordinate())
                + (effects.hasImpacted() ? " / resolving volley" : ""));
    }

    private void updateTurnClock() {
        if (networkPlacement && state().phase() == GamePhase.BATTLE) {
            hud.setTurnClock(state().currentPlayerId().equals(state().player().id()), network.remainingTurnMillis());
        } else if (ai != null && state().phase() == GamePhase.BATTLE) {
            hud.setTurnClock(state().currentPlayerId().equals(state().player().id()), ai.remainingTurnMillis());
        } else hud.hideTurnClock();
        if (state().phase() == GamePhase.BATTLE && !effects.isBusy() && !introBlocked()) {
            boolean own = state().currentPlayerId().equals(state().player().id());
            int turn = state().turnNumber();
            if (soundedTurn != turn) {
                soundedTurn = turn;
                sound.accept(own ? Cue.YOUR_TURN : Cue.ENEMY_TURN);
            }
            long remaining = ai != null ? ai.remainingTurnMillis() : networkPlacement ? network.remainingTurnMillis() : 0;
            if ((ai != null || networkPlacement && !network.pending()) && own && remaining > 0
                    && remaining <= 10_000 && warnedTurn != turn) {
                warnedTurn = turn; sound.accept(Cue.TIMER_WARNING);
            }
        }
    }
    private boolean skillMode() { return networkPlacement && engine.rules().skills(); }
    private int energy() {
        return network == null || network.match() == null || network.match().skills() == null ? 0 : network.match().skills().ownEnergy();
    }
    private List<SonarReading> scans() {
        return network == null || network.match() == null || network.match().skills() == null ? List.of() : network.match().skills().ownScans();
    }
    private boolean canUseSkills() {
        return skillMode() && state().phase() == GamePhase.BATTLE && !introBlocked() && !effects.isBusy() && !network.pending()
                && state().currentPlayerId().equals(state().player().id());
    }
    private void clearSkills() { armedSkill = null; torpedoDirection = SkillDirection.EAST; skillHint = ""; }

    private void refreshSkillTray() {
        boolean shown = skillMode() && state().phase() == GamePhase.BATTLE;
        String hint = skillHint;
        if (armedSkill != null) hint = armedSkill.label() + (armedSkill == SkillType.TORPEDO ? " " + torpedoDirection + " [T]" : "")
                + ": pick a cell";
        else if (hover != null && enemyView) {
            for (SonarReading scan : scans()) if (scan.area().contains(hover)) { hint = "Sonar: " + scan.count() + " ship cells here"; break; }
        }
        if (hint.isEmpty()) hint = "[1-4] arm a skill";
        controls.setSkills(shown, energy(), SkillType.MAX_ENERGY, armedSkill, canUseSkills(), hint);
    }

    /** Keys 1-4 and the tray buttons arm/disarm skills; T turns the torpedo. Returns true when consumed. */
    private boolean skillAction(PlacementHud.Action action) {
        SkillType skill = switch (action) {
            case SKILL_CLUSTER, SELECT_CARRIER -> SkillType.CLUSTER;
            case SKILL_BARRAGE, SELECT_BATTLESHIP -> SkillType.BARRAGE;
            case SKILL_TORPEDO, SELECT_CRUISER -> SkillType.TORPEDO;
            case SKILL_SONAR, SELECT_SUBMARINE -> SkillType.SONAR;
            default -> null;
        };
        if (action == PlacementHud.Action.ROTATE && armedSkill == SkillType.TORPEDO) {
            torpedoDirection = torpedoDirection.next(); sound.accept(Cue.ROTATE); refreshSkillTray(); return true;
        }
        if (skill == null) return false;
        if (introBlocked() || effects.isBusy()) return true;
        if (armedSkill == skill) { armedSkill = null; skillHint = skill.label() + " cancelled."; sound.accept(Cue.CLICK); }
        else if (!canUseSkills()) { skillHint = "Skills can be used on your turn."; sound.accept(Cue.ERROR); }
        else if (energy() < skill.cost()) { skillHint = skill.label() + " needs " + skill.cost() + " energy."; sound.accept(Cue.ERROR); }
        else { armedSkill = skill; enemyView = true; sound.accept(Cue.CLICK); }
        refresh();
        return true;
    }

    private List<Coordinate> skillPreview(SkillType skill, Coordinate cell) {
        Board enemy = state().enemy().board();
        List<Coordinate> area = SkillTargeting.area(skill, cell, torpedoDirection);
        return skill == SkillType.TORPEDO ? area.stream().filter(c -> !enemy.wasAttacked(c)).toList() : area;
    }

    private void fireSkill() {
        if (!canUseSkills()) { skillHint = "Wait for your turn."; refreshHud(); return; }
        if (energy() < armedSkill.cost()) { skillHint = armedSkill.label() + " needs " + armedSkill.cost() + " energy."; refreshHud(); return; }
        if (network.useSkill(armedSkill, hover, armedSkill == SkillType.TORPEDO ? torpedoDirection : null)) {
            sound.accept(Cue.TARGET); skillHint = armedSkill.label() + " launched. Awaiting server.";
            targeting.clear(); feedback.clearPreview();
        }
        refreshHud();
    }

    /** The most important shot of a volley: the winning or sinking hit, else any hit, else the last shot. */
    private static AttackResult leadImpact(SkillResult skill) {
        var impacts = skill.impacts();
        if (impacts.isEmpty()) return null;
        return impacts.stream().filter(AttackResult::gameOver).findFirst()
                .or(() -> impacts.stream().filter(AttackResult::sunk).findFirst())
                .or(() -> impacts.stream().filter(i -> i.type() != com.battleship.shared.game.AttackResultType.MISS).findFirst())
                .orElse(impacts.getLast());
    }

    private static String skillSummary(SkillResult skill, String ownId) {
        String who = skill.attackerId().equals(ownId) ? "" : "Enemy ";
        if (skill.skill() == SkillType.SONAR)
            return skill.sonarCount() == null ? "Enemy sonar swept your waters near " + cellName(skill.origin()) + "."
                    : "Sonar: " + skill.sonarCount() + " ship cells near " + cellName(skill.origin()) + ".";
        long hits = skill.impacts().stream().filter(i -> i.type() != com.battleship.shared.game.AttackResultType.MISS).count();
        return who + skill.skill().label() + ": " + hits + " hit" + (hits == 1 ? "" : "s") + " / " + skill.impacts().size() + " shots.";
    }

    private boolean introBlocked() {
        return (networkPlacement || ai != null) && state().phase() == GamePhase.BATTLE
                && (remainingStartMillis() > 0 || startFlash > 0);
    }
    private long remainingStartMillis() { return ai != null ? ai.remainingStartMillis() : network.remainingStartMillis(); }
    private void updateIntro(float delta) {
        if ((!networkPlacement && ai == null) || state().phase() != GamePhase.BATTLE) {
            countingIntoBattle = false; startFlash = 0; hud.setStartCountdown(0, false); return;
        }
        long delay = remainingStartMillis();
        if (delay > 0) { countingIntoBattle = true; startFlash = 0; }
        else if (countingIntoBattle) { countingIntoBattle = false; startFlash = .45f; sound.accept(Cue.MATCH_START); }
        else startFlash = Math.max(0, startFlash - Math.max(0, delta));
        if (effects.isBusy()) startFlash = 0;
        hud.setStartCountdown(delay, startFlash > 0);
    }
}
