package com.battleship.shared.game;

import java.util.*;

/** All translation-normalized, four-neighbour connected footprints of up to five cells. */
public final class ShipShapes {
    private static final Comparator<Coordinate> ORDER = Comparator.comparingInt(Coordinate::row).thenComparingInt(Coordinate::column);
    private static final int[][] DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    private static final Map<Integer, List<List<Coordinate>>> CATALOG = generate();
    private ShipShapes() { }

    public static PlacementResult.Failure validate(ShipType type, List<Coordinate> cells) {
        if (type == null || cells == null || cells.stream().anyMatch(Objects::isNull)) return PlacementResult.Failure.INVALID_ARGUMENT;
        if (cells.size() != type.length()) return PlacementResult.Failure.WRONG_SIZE;
        Set<Coordinate> unique = new HashSet<>(cells);
        if (unique.size() != cells.size()) return PlacementResult.Failure.DUPLICATE_CELLS;
        Set<Coordinate> visited = new HashSet<>();
        var queue = new ArrayDeque<Coordinate>(); queue.add(cells.getFirst()); visited.add(cells.getFirst());
        while (!queue.isEmpty()) {
            Coordinate cell = queue.removeFirst();
            for (int[] d : DIRECTIONS) {
                Coordinate next = cell.offset(d[0], d[1]);
                if (unique.contains(next) && visited.add(next)) queue.add(next);
            }
        }
        return visited.size() == cells.size() ? PlacementResult.Failure.NONE : PlacementResult.Failure.DISCONNECTED;
    }
    public static List<Coordinate> normalize(List<Coordinate> cells) {
        int row = cells.stream().mapToInt(Coordinate::row).min().orElseThrow();
        int column = cells.stream().mapToInt(Coordinate::column).min().orElseThrow();
        return cells.stream().map(c -> c.offset(-row, -column)).sorted(ORDER).toList();
    }
    public static List<Coordinate> rotate(List<Coordinate> cells) {
        var shape = normalize(cells);
        int height = shape.stream().mapToInt(Coordinate::row).max().orElseThrow() + 1;
        return normalize(shape.stream().map(c -> new Coordinate(c.column(), height - 1 - c.row())).toList());
    }
    public static String key(List<Coordinate> cells) { return normalize(cells).toString(); }
    public static List<List<Coordinate>> all(int length) {
        var result = CATALOG.get(length);
        if (result == null) throw new IllegalArgumentException("Supported ship sizes: 1-5");
        return result;
    }
    private static Map<Integer, List<List<Coordinate>>> generate() {
        Map<Integer, List<List<Coordinate>>> result = new HashMap<>();
        result.put(1, List.of(List.of(new Coordinate(0, 0))));
        for (int length = 2; length <= 5; length++) {
            Map<String, List<Coordinate>> unique = new TreeMap<>();
            for (var previous : result.get(length - 1)) for (Coordinate cell : previous) for (int[] d : DIRECTIONS) {
                Coordinate next = cell.offset(d[0], d[1]);
                if (previous.contains(next)) continue;
                var grown = new ArrayList<>(previous); grown.add(next);
                var shape = normalize(grown); unique.put(shape.toString(), shape);
            }
            result.put(length, List.copyOf(unique.values()));
        }
        return Map.copyOf(result);
    }
}
