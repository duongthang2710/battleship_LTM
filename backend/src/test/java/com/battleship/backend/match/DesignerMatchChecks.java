package com.battleship.backend.match;

import com.battleship.shared.game.*;
import com.battleship.shared.match.*;
import com.battleship.shared.room.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public final class DesignerMatchChecks {
    static final List<RoomPlayer> PLAYERS = List.of(new RoomPlayer("a","Admiral A",true,Side.WHITE),new RoomPlayer("b","Admiral B",true,Side.BLACK));
    public static void main(String[] args) {
        int cases=0;
        for (ShipType type:ShipType.values()) for (var shape:ShipShapes.all(type.length())) {
            Board board = fleet(type,shape); FleetSubmission submission = submission(board);
            var now = new AtomicLong(); var match = new MatchService(PLAYERS,GameMode.DESIGNER,now::get);
            match.submit("a",submission);
            require(match.phase()==GamePhase.PLACEMENT && match.snapshot("b").enemyBoard().ships().isEmpty(),"One fleet waits and stays private");
            match.submit("b",submission);
            require(match.phase()==GamePhase.BATTLE && match.timer().remainingMillis()==60_000 && match.snapshot("a").currentPlayerId().equals("a"),"Server starts with Player 1 / 60 seconds");
            require(match.snapshot("a").ownBoard().board().ship(type).occupiedCells().equals(shape),"Snapshot preserves custom shape");
            int hit=0;
            for (Coordinate cell:shape) {
                var result=match.attack("a",cell); hit++;
                require(result.accepted(),"Valid custom attack");
                if(hit<shape.size()) require(match.snapshot("a").enemyBoard().ships().isEmpty(),"Unsunk shape is not disclosed");
                if(hit==shape.size()) require(match.snapshot("a").enemyBoard().board().ship(type).occupiedCells().equals(shape),"Sunk shape disclosed exactly");
                match.attack("b",new Coordinate(9,hit));
            }
            now.addAndGet(60_000_000_000L); require(match.expireTurn(),"Designer uses authoritative 60-second timeout");
            require(match.attack("a",new Coordinate(8,8)).type()==AttackResultType.GAME_FINISHED,"No attacks after timeout");
            cases++;
        }
        var classic = new MatchService(PLAYERS);
        try { classic.submit("a",submission(fleet(ShipType.CARRIER,ShipShapes.all(5).getFirst()))); throw new AssertionError("Classic accepted custom payload"); }
        catch(IllegalArgumentException expected) { }
        var invalid = new MatchService(PLAYERS,GameMode.DESIGNER);
        Board valid=fleet(ShipType.CARRIER,ShipShapes.all(5).getFirst());
        var entries=new ArrayList<>(submission(valid).ships());
        entries.set(0,new ShipPlacement(ShipType.CARRIER,new Coordinate(0,0),ShipOrientation.HORIZONTAL,
                List.of(new Coordinate(0,0),new Coordinate(0,1),new Coordinate(0,2),new Coordinate(0,3),new Coordinate(2,3))));
        try { invalid.submit("a",new FleetSubmission(entries)); throw new AssertionError("Disconnected hull accepted"); } catch(IllegalArgumentException expected) { }
        require(invalid.snapshot("a").ownBoard().ships().isEmpty(),"Invalid fleet submission is atomic");
        invalid.submit("a",submission(valid)); require(invalid.snapshot("a").fleetSubmitted(),"Can retry after rejection");
        System.out.println("PASS server designer: " + cases + " shapes, placement/privacy/sunk disclosure/first turn/timeout, classic guard and rejected-fleet retry.");
    }
    static Board fleet(ShipType chosen,List<Coordinate> shape) {
        Board board=new Board().withShip(new Ship(chosen,shape));
        for(ShipType type:ShipType.values()) if(type!=chosen) {
            boolean placed=false;
            for(int r=0;r<8&&!placed;r++) for(int c=0;c<10&&!placed;c++) for(ShipOrientation orientation:ShipOrientation.values()) {
                var cells=new Ship(type,new Coordinate(r,c),orientation).occupiedCells();
                if(board.validatePlacement(type,cells).success()) { board=board.withShip(new Ship(type,cells)); placed=true; break; }
            }
            require(placed,"Packing helper failed");
        }
        return board;
    }
    static FleetSubmission submission(Board board) { return new FleetSubmission(board.ships().stream().map(ShipPlacement::from).toList()); }
    static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
