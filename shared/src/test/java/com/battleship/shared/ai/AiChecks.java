package com.battleship.shared.ai;

import com.battleship.shared.game.*;
import com.battleship.shared.match.BoardView;
import java.util.*;

public final class AiChecks {
    public static void main(String[] args) {
        intersection(); followHits(); publicInformation();
        double[] averages = new double[AiDifficulty.values().length];
        for (AiDifficulty level : AiDifficulty.values()) {
            int total = 0;
            for (int seed = 0; seed < 80; seed++) {
                GameEngine engine = new GameEngine("Fixture", new Random(seed)); engine.randomPlacement();
                Board board = engine.state().player().board();
                AiStrategy bot = AiStrategy.forDifficulty(level); Random random = new Random(seed + 500);
                int shots = 0;
                while (!board.fleet().isSunk()) {
                    Coordinate target = bot.chooseTarget(AiKnowledge.from(BoardView.from(board, true)), random);
                    require(Board.inBounds(target) && !board.wasAttacked(target), "Legal unique target: " + level);
                    board = board.withAttack(target);
                    require(++shots <= 100, "Bot finishes within the board size");
                }
                total += shots;
            }
            averages[level.ordinal()] = total / 80.0;
            System.out.printf(Locale.ROOT, "%s: %.2f shots to sink fleet (80 paired fleets)%n", level, total / 80.0);
        }
        require(averages[1] < averages[0], "Medium improves over Easy on paired fleet benchmark");
        require(averages[2] < averages[1], "Hard improves over Medium on paired fleet benchmark");
        System.out.println("PASS: legal shots, intersecting placements, hit pursuit, public information and difficulty benchmark.");
    }
    private static void intersection() {
        Set<Coordinate> open = Set.of(c(0,2),c(1,2),c(2,2),c(1,0),c(1,1),c(1,3));
        Map<Coordinate, CellState> shots = new HashMap<>();
        for (int r=0;r<10;r++) for (int col=0;col<10;col++) if (!open.contains(c(r,col))) shots.put(c(r,col),CellState.MISS);
        AiKnowledge knowledge = new AiKnowledge(shots, Set.of(ShipType.CARRIER,ShipType.CRUISER,ShipType.DESTROYER));
        HardAi bot = new HardAi();
        require(bot.chooseTarget(knowledge,new Random(1)).equals(c(1,2)), "Hard chooses intersection of length-3 and length-4 candidates");
        require(bot.scores(knowledge).get(c(1,2)) > bot.scores(knowledge).get(c(1,1)), "Intersection accumulates candidate coverage");
    }
    private static void followHits() {
        AiKnowledge knowledge = new AiKnowledge(Map.of(c(4,4),CellState.HIT,c(4,5),CellState.HIT),Set.of());
        Set<Coordinate> ends = Set.of(c(4,3),c(4,6));
        require(ends.contains(new MediumAi().chooseTarget(knowledge,new Random(2))), "Medium pursues line ends");
        require(ends.contains(new HardAi().chooseTarget(knowledge,new Random(2))), "Hard pursues supported line");
        // A sunk ship does not imply that its neighbours are water: touching fleets are legal.
        Map<Coordinate,CellState> shots = new HashMap<>();
        for(int r=0;r<10;r++) for(int col=0;col<10;col++) shots.put(c(r,col),CellState.MISS);
        shots.put(c(0,0),CellState.SUNK); shots.put(c(0,1),CellState.SUNK);
        shots.remove(c(1,0)); shots.remove(c(1,1)); shots.remove(c(1,2));
        AiKnowledge touching = new AiKnowledge(shots,Set.of(ShipType.CARRIER,ShipType.BATTLESHIP,ShipType.CRUISER,ShipType.DESTROYER));
        require(new HardAi().scores(touching).getOrDefault(c(1,0),0)>0,"Hard retains candidates next to a sunk ship");
    }
    private static void publicInformation() {
        Board a = new Board().withPlacement(ShipType.CARRIER,c(0,0),ShipOrientation.HORIZONTAL);
        Board b = new Board().withPlacement(ShipType.CARRIER,c(8,0),ShipOrientation.HORIZONTAL);
        AiKnowledge one = AiKnowledge.from(BoardView.from(a,true)), two = AiKnowledge.from(BoardView.from(b,true));
        require(one.equals(two),"Hidden fleets produce identical knowledge before any shot");
        for (AiDifficulty difficulty : AiDifficulty.values()) require(
                AiStrategy.forDifficulty(difficulty).chooseTarget(one,new Random(7)).equals(
                AiStrategy.forDifficulty(difficulty).chooseTarget(two,new Random(7))),"No hidden-fleet influence: " + difficulty);
        require(AiKnowledge.from(BoardView.from(a.withAttack(c(0,0)),true)).shots().get(c(0,0))==CellState.HIT,"Unresolved hit remains public");
    }
    private static Coordinate c(int row,int column) { return new Coordinate(row,column); }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
