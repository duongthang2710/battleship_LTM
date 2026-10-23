package com.battleship.shared.game;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class Fleet {
    private final Map<ShipType, Ship> ships;

    public Fleet() { ships = Map.of(); }

    private Fleet(Map<ShipType, Ship> ships) { this.ships = Map.copyOf(ships); }

    public List<Ship> ships() {
        return List.of(ShipType.values()).stream().filter(ships::containsKey).map(ships::get).toList();
    }

    public Ship ship(ShipType type) { return type == null ? null : ships.get(type); }
    public boolean isComplete() { return ships.size() == ShipType.values().length; }
    public boolean isSunk() { return isComplete() && ships.values().stream().allMatch(Ship::isSunk); }

    public Fleet withShip(Ship ship) {
        EnumMap<ShipType, Ship> replacement = new EnumMap<>(ShipType.class);
        replacement.putAll(ships);
        replacement.put(ship.type(), ship);
        return new Fleet(replacement);
    }

    public Fleet withoutShip(ShipType type) {
        EnumMap<ShipType, Ship> replacement = new EnumMap<>(ShipType.class);
        replacement.putAll(ships);
        replacement.remove(type);
        return new Fleet(replacement);
    }
}
