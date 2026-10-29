package com.battleship.shared.game;

import com.battleship.shared.game.PlacementResult.Failure;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public final class GameEngine {
    private static final int MAX_PLACEMENT_ATTEMPTS = 10_000;
    private final Random random;
    private GameState state;
    private GameRules rules = GameRules.DEFAULT;
    public GameMode mode() { return rules.mode(); }
    public GameRules rules() { return rules; }
    public void setMode(GameMode mode) { setRules(new GameRules(mode, rules.shipCount(), rules.turnRule())); }
    public void setRules(GameRules rules) {
        if (state.phase() != GamePhase.PLACEMENT || !state.player().board().ships().isEmpty() || !state.enemy().board().ships().isEmpty())
            throw new IllegalStateException("Choose room rules before placing ships");
        this.rules = Objects.requireNonNull(rules);
    }

    public GameEngine(String playerName) { this(playerName, new Random()); }

    public GameEngine(String playerName, Random random) {
        this.random = Objects.requireNonNull(random);
        Player player = new Player("player", playerName, new Board());
        Player enemy = new Player("enemy", "Enemy", new Board());
        state = new GameState(GamePhase.PLACEMENT, player, enemy, player.id(), null, null,
                PlacementState.initial(), false, 0);
    }

    public GameState state() { return state; }

    /** Player 1 opens the server-owned match, independently of faction color. */
    public GameEngine(Player first, Player second) {
        random = new Random();
        state = new GameState(GamePhase.PLACEMENT, first, second, first.id(), null, null,
                PlacementState.initial(), false, 0);
    }

    public void submitFleet(String playerId, List<Ship> ships) {
        if (state.phase() != GamePhase.PLACEMENT) throw new IllegalStateException("Placement is closed");
        boolean first = state.player().id().equals(playerId);
        if (!first && !state.enemy().id().equals(playerId)) throw new IllegalArgumentException("Unknown player");
        if ((first ? state.player() : state.enemy()).fleet().isComplete(rules))
            throw new IllegalStateException("Fleet already submitted");
        if (ships == null || ships.size() != rules.shipCount())
            throw new IllegalArgumentException(rules.shipCount() == 5 ? "Exactly five ships required." : "Exactly " + rules.shipCount() + " ships required.");
        Board board = new Board();
        for (Ship ship : ships) {
            if (ship == null) throw new IllegalArgumentException("Missing ship data.");
            if (!rules.allows(ship.type())) throw new IllegalArgumentException("Ship type is not enabled in this room.");
            if (board.ship(ship.type()) != null) throw new IllegalArgumentException("Each ship type must appear once.");
            if (mode() == GameMode.CLASSIC && ship.custom()) throw new IllegalArgumentException("Custom ships require Fleet designer mode.");
            if (mode() == GameMode.DESIGNER) ship = new Ship(ship.type(), ship.occupiedCells());
            PlacementResult validation = board.validatePlacement(ship.type(), ship.occupiedCells());
            if (!validation.success()) throw new IllegalArgumentException(switch (validation.reason()) {
                case OUT_OF_BOUNDS -> ship.type().displayName() + " is outside the board.";
                case OVERLAP -> ship.type().displayName() + " overlaps another ship.";
                default -> "Invalid ship data.";
            });
            board = board.withShip(ship);
        }
        if (!board.fleet().isComplete(rules)) throw new IllegalArgumentException("Fleet is incomplete");
        Player player = first ? state.player().withBoard(board) : state.player();
        Player enemy = first ? state.enemy() : state.enemy().withBoard(board);
        boolean complete = player.fleet().isComplete(rules) && enemy.fleet().isComplete(rules);
        state = new GameState(complete ? GamePhase.BATTLE : GamePhase.PLACEMENT, player, enemy,
                player.id(), null, null, PlacementState.initial(), complete, complete ? 1 : 0);
    }

    public List<Coordinate> previewCells(ShipType type, Coordinate position, ShipOrientation orientation) {
        return new Ship(type, position, orientation).occupiedCells();
    }

    public boolean canPlace(ShipType type, Coordinate position, ShipOrientation orientation) {
        return state.phase() == GamePhase.PLACEMENT && rules.allows(type) && state.player().board().canPlace(type, position, orientation);
    }

    public PlacementResult placeShip(ShipType type, Coordinate position, ShipOrientation orientation) {
        if (state.phase() != GamePhase.PLACEMENT) return closed();
        if (!rules.allows(type)) return PlacementResult.rejected(Failure.INVALID_ARGUMENT, List.of());
        PlacementResult result = state.player().board().validatePlacement(type, position, orientation);
        if (!result.success()) return result;
        updatePlacement(state.player().board().withPlacement(type, position, orientation));
        return result;
    }

    public PlacementResult validateDesignedShip(ShipType type, List<Coordinate> cells) {
        if (state.phase() != GamePhase.PLACEMENT) return closed();
        if (mode() != GameMode.DESIGNER) return PlacementResult.rejected(Failure.MODE_MISMATCH, List.of());
        if (!rules.allows(type)) return PlacementResult.rejected(Failure.INVALID_ARGUMENT, List.of());
        return state.player().board().validatePlacement(type, cells);
    }
    public PlacementResult placeDesignedShip(ShipType type, List<Coordinate> cells) {
        PlacementResult result = validateDesignedShip(type, cells);
        if (result.success()) updatePlacement(state.player().board().withShip(new Ship(type, cells)));
        return result;
    }

    public PlacementResult removeShip(ShipType type) {
        if (state.phase() != GamePhase.PLACEMENT) return closed();
        if (type == null || !rules.allows(type)) return PlacementResult.rejected(Failure.INVALID_ARGUMENT, List.of());
        Ship ship = state.player().board().ship(type);
        if (ship == null) return PlacementResult.rejected(Failure.SHIP_NOT_FOUND, List.of());
        updatePlacement(state.player().board().withoutShip(type));
        return PlacementResult.accepted(ship.occupiedCells());
    }

    public PlacementResult rotateShip(ShipType type) {
        if (state.phase() != GamePhase.PLACEMENT) return closed();
        if (type == null || !rules.allows(type)) return PlacementResult.rejected(Failure.INVALID_ARGUMENT, List.of());
        Ship ship = state.player().board().ship(type);
        if (ship == null) return PlacementResult.rejected(Failure.SHIP_NOT_FOUND, List.of());
        if (!ship.custom()) return placeShip(type, ship.position(), ship.orientation().rotate());
        return placeDesignedShip(type, ship.rotated().occupiedCells());
    }

    public PlacementResult resetFleet() {
        if (state.phase() != GamePhase.PLACEMENT) return closed();
        List<Coordinate> cells = fleetCells(state.player().board());
        updatePlacement(new Board());
        updateSelection(PlacementState.initial());
        return PlacementResult.accepted(cells);
    }

    public PlacementResult randomPlacement() {
        if (state.phase() != GamePhase.PLACEMENT) return closed();
        updatePlacement(randomFleet());
        Ship selected = state.player().board().ship(state.placement().selectedType());
        updateSelection(new PlacementState(selected.type(), selected.orientation(), false, new Coordinate(0, 0)));
        return PlacementResult.accepted(fleetCells(state.player().board()));
    }

    public boolean canReady() { return state.canReady(rules); }

    public PlacementResult ready() {
        if (state.phase() != GamePhase.PLACEMENT) return closed();
        if (!canReady()) return PlacementResult.rejected(Failure.FLEET_INCOMPLETE, List.of());
        Player enemy = state.enemy().withBoard(randomFleet());
        PlacementState placement = state.placement();
        state = new GameState(GamePhase.BATTLE, state.player(), enemy, state.player().id(), null, null,
                new PlacementState(placement.selectedType(), placement.orientation(), false, new Coordinate(0, 0)), true, 1);
        return PlacementResult.accepted(fleetCells(state.player().board()));
    }

    public PlacementResult startGame() { return ready(); }

    public PlacementResult selectShip(ShipType type) {
        if (state.phase() != GamePhase.PLACEMENT) return closed();
        if (type == null || !rules.allows(type)) return PlacementResult.rejected(Failure.INVALID_ARGUMENT, List.of());
        Ship placed = state.player().board().ship(type);
        updateSelection(new PlacementState(type, placed == null ? state.placement().orientation() : placed.orientation(),
                false, new Coordinate(0, 0)));
        return PlacementResult.accepted(placed == null ? List.of() : placed.occupiedCells());
    }

    public PlacementResult previewPlacement(Coordinate hover) {
        if (state.phase() != GamePhase.PLACEMENT) return closed();
        if (hover == null) return PlacementResult.rejected(Failure.INVALID_ARGUMENT, List.of());
        PlacementState placement = state.placement();
        Coordinate anchor = hover.offset(-placement.grabOffset().row(), -placement.grabOffset().column());
        Ship selected = state.player().board().ship(placement.selectedType());
        return selected != null && selected.custom() ? state.player().board().validatePlacement(selected.type(), selected.translatedTo(anchor).occupiedCells())
                : state.player().board().validatePlacement(placement.selectedType(), anchor, placement.orientation());
    }

    public PlacementResult clickPlacement(Coordinate coordinate) {
        if (state.phase() != GamePhase.PLACEMENT) return closed();
        if (coordinate == null) return PlacementResult.rejected(Failure.INVALID_ARGUMENT, List.of());
        if (!Board.inBounds(coordinate)) return PlacementResult.rejected(Failure.OUT_OF_BOUNDS, List.of());
        Board board = state.player().board();
        PlacementState placement = state.placement();
        Ship underCursor = board.shipAt(coordinate);
        if (underCursor != null && !placement.moving()) {
            Coordinate grab = coordinate.offset(-underCursor.position().row(), -underCursor.position().column());
            updateSelection(new PlacementState(underCursor.type(), underCursor.orientation(), true, grab));
            return PlacementResult.accepted(underCursor.occupiedCells());
        }
        boolean existed = board.ship(placement.selectedType()) != null;
        Coordinate anchor = coordinate.offset(-placement.grabOffset().row(), -placement.grabOffset().column());
        Ship selected = board.ship(placement.selectedType());
        PlacementResult result = selected != null && selected.custom() ? placeDesignedShip(selected.type(), selected.translatedTo(anchor).occupiedCells())
                : placeShip(placement.selectedType(), anchor, placement.orientation());
        if (result.success()) {
            updateSelection(new PlacementState(placement.selectedType(), placement.orientation(), false, new Coordinate(0, 0)));
            if (!existed) {
                for (ShipType type : rules.shipTypes()) {
                    if (state.player().board().ship(type) == null) { selectShip(type); break; }
                }
            }
        }
        return result;
    }

    public PlacementResult rotateSelected() {
        if (state.phase() != GamePhase.PLACEMENT) return closed();
        PlacementState placement = state.placement();
        Ship placed = state.player().board().ship(placement.selectedType());
        if (placed != null && placed.custom()) {
            PlacementResult result = rotateShip(placed.type());
            selectShip(placed.type());
            return result;
        }
        if (placed != null && !placement.moving()) {
            PlacementResult result = rotateShip(placed.type());
            updateSelection(new PlacementState(placed.type(), placed.orientation().rotate(), !result.success(), new Coordinate(0, 0)));
            return result;
        }
        Coordinate grab = placement.grabOffset();
        updateSelection(new PlacementState(placement.selectedType(), placement.orientation().rotate(), placement.moving(),
                new Coordinate(grab.column(), grab.row())));
        return PlacementResult.accepted(List.of());
    }

    public PlacementResult removeSelected() {
        if (state.phase() != GamePhase.PLACEMENT) return closed();
        PlacementState placement = state.placement();
        PlacementResult result = removeShip(placement.selectedType());
        updateSelection(new PlacementState(placement.selectedType(), placement.orientation(), false, new Coordinate(0, 0)));
        return result;
    }

    public boolean canAttack(String attackerId, Coordinate coordinate) {
        return attackRejection(attackerId, coordinate) == null;
    }

    public AttackResult attack(Coordinate coordinate) { return attack(state.currentPlayerId(), coordinate); }

    public AttackResult attack(String attackerId, Coordinate coordinate) {
        AttackResultType rejection = attackRejection(attackerId, coordinate);
        if (rejection != null) {
            String defenderId = state.player().id().equals(attackerId) ? state.enemy().id()
                    : state.enemy().id().equals(attackerId) ? state.player().id() : null;
            return new AttackResult(rejection, coordinate, null, false, state.phase() == GamePhase.GAME_OVER,
                    attackerId, defenderId, state.winnerId(), List.of());
        }
        boolean playerAttacks = state.player().id().equals(attackerId);
        Player defender = playerAttacks ? state.enemy() : state.player();
        Board attacked = defender.board().withAttack(coordinate);
        Ship target = attacked.shipAt(coordinate);
        String winnerId = attacked.fleet().isSunk(rules) ? attackerId : null;
        boolean sunk = target != null && target.isSunk();
        AttackResultType type = winnerId != null ? AttackResultType.GAME_OVER
                : target == null ? AttackResultType.MISS : sunk ? AttackResultType.SUNK : AttackResultType.HIT;
        AttackResult result = new AttackResult(type, coordinate, target == null ? null : target.type(),
                sunk, winnerId != null, attackerId, defender.id(), winnerId,
                sunk ? target.occupiedCells() : List.of(coordinate));
        Player player = playerAttacks ? state.player() : state.player().withBoard(attacked);
        Player enemy = playerAttacks ? state.enemy().withBoard(attacked) : state.enemy();
        state = new GameState(winnerId == null ? GamePhase.BATTLE : GamePhase.GAME_OVER, player, enemy,
                winnerId == null && !(rules.turnRule() == TurnRule.HIT_CONTINUES && target != null) ? defender.id() : attackerId, winnerId, result, state.placement(), state.ready(),
                state.turnNumber() + 1);
        return result;
    }

    public boolean timeoutCurrentTurn() { return forfeit(state.currentPlayerId()); }

    public boolean forfeit(String playerId) {
        if (state.phase() != GamePhase.BATTLE) return false;
        if (!state.player().id().equals(playerId) && !state.enemy().id().equals(playerId))
            throw new IllegalArgumentException("Unknown player");
        String winner = playerId.equals(state.player().id()) ? state.enemy().id() : state.player().id();
        state = new GameState(GamePhase.GAME_OVER, state.player(), state.enemy(), state.currentPlayerId(),
                winner, null, state.placement(), state.ready(), state.turnNumber());
        return true;
    }

    public void restart() {
        Player player = state.player().withBoard(new Board());
        Player enemy = state.enemy().withBoard(new Board());
        state = new GameState(GamePhase.PLACEMENT, player, enemy, player.id(), null, null,
                PlacementState.initial(), false, 0);
    }

    private AttackResultType attackRejection(String attackerId, Coordinate coordinate) {
        if (state.phase() == GamePhase.GAME_OVER) return AttackResultType.GAME_FINISHED;
        if (state.phase() != GamePhase.BATTLE) return AttackResultType.NOT_IN_BATTLE;
        if (!state.currentPlayerId().equals(attackerId)) return AttackResultType.NOT_YOUR_TURN;
        if (!Board.inBounds(coordinate)) return AttackResultType.INVALID_COORDINATE;
        Player defender = state.player().id().equals(attackerId) ? state.enemy() : state.player();
        return defender.board().wasAttacked(coordinate) ? AttackResultType.ALREADY_ATTACKED : null;
    }

    private void updatePlacement(Board board) {
        state = new GameState(GamePhase.PLACEMENT, state.player().withBoard(board), state.enemy(),
                state.player().id(), null, null, state.placement(), false, 0);
    }

    private void updateSelection(PlacementState placement) {
        state = new GameState(state.phase(), state.player(), state.enemy(), state.currentPlayerId(),
                state.winnerId(), state.lastAttack(), placement, state.ready(), state.turnNumber());
    }

    private static PlacementResult closed() {
        return PlacementResult.rejected(Failure.PHASE_CLOSED, List.of());
    }

    private static List<Coordinate> fleetCells(Board board) {
        return board.ships().stream().flatMap(ship -> ship.occupiedCells().stream()).toList();
    }

    private Board randomFleet() {
        if (mode() == GameMode.DESIGNER) {
            Board designed = new Board();
            for (ShipType type : rules.shipTypes()) {
                boolean placed = false;
                var catalog = ShipShapes.all(type.length());
                for (int attempt = 0; attempt < MAX_PLACEMENT_ATTEMPTS && !placed; attempt++) {
                    int row = random.nextInt(Board.SIZE), column = random.nextInt(Board.SIZE);
                    var cells = catalog.get(random.nextInt(catalog.size())).stream().map(c -> c.offset(row, column)).toList();
                    if (designed.validatePlacement(type, cells).success()) { designed = designed.withShip(new Ship(type, cells)); placed = true; }
                }
                if (!placed) throw new IllegalStateException("Could not generate designed fleet");
            }
            return designed;
        }
        Board board = placeRandom(new Board(), 0, new int[] {0});
        if (board == null) throw new IllegalStateException("Could not generate a valid fleet");
        return board;
    }

    private Board placeRandom(Board board, int index, int[] attempts) {
        ShipType[] types = rules.shipTypes().toArray(ShipType[]::new);
        if (index == types.length) return board;
        List<Ship> candidates = new ArrayList<>();
        for (ShipOrientation orientation : ShipOrientation.values()) {
            for (int row = 0; row < Board.SIZE; row++) {
                for (int column = 0; column < Board.SIZE; column++) {
                    Coordinate position = new Coordinate(row, column);
                    if (board.canPlace(types[index], position, orientation)) candidates.add(new Ship(types[index], position, orientation));
                }
            }
        }
        Collections.shuffle(candidates, random);
        for (Ship candidate : candidates) {
            if (++attempts[0] > MAX_PLACEMENT_ATTEMPTS) return null;
            Board next = board.withPlacement(candidate.type(), candidate.position(), candidate.orientation());
            Board complete = placeRandom(next, index + 1, attempts);
            if (complete != null) return complete;
        }
        return null;
    }
}
