package com.battleship.backend.match;

import com.battleship.backend.server.TcpServer;
import com.battleship.backend.database.Database;
import com.battleship.shared.game.*;
import com.battleship.shared.match.*;
import com.battleship.shared.protocol.*;
import com.battleship.shared.room.*;
import java.io.*;
import java.util.*;
import static com.battleship.backend.match.DesignerMatchChecks.*;

/** Real server/auth/PostgreSQL and two TCP peers. Only these two fresh test accounts are cleaned up. */
public final class DesignerTcpChecks {
    public static void main(String[] args) throws Exception {
        var ids=new ArrayList<java.util.UUID>();
        try {
            try(TcpServer server=new TcpServer(0)) {
                Thread.ofVirtual().start(() -> { try { server.start(); } catch(IOException e) { throw new UncheckedIOException(e); } });
                TcpMatchChecks.Peer a=null,b=null;
                try {
                    a=new TcpMatchChecks.Peer(server.port(),"Designer A"); ids.add(a.accountId);
                    b=new TcpMatchChecks.Peer(server.port(),"Designer B"); ids.add(b.accountId);
                    a.send(MessageType.CREATE_ROOM,new CreateRoomRequest(GameMode.DESIGNER));
                    RoomSnapshot created=a.read(MessageType.ROOM_CREATED).payloadAs(RoomSnapshot.class);
                    require(created.mode()==GameMode.DESIGNER,"Creator receives authoritative mode");
                    b.send(MessageType.JOIN_ROOM,new JoinRoomRequest(created.roomCode()));
                    require(b.read(MessageType.ROOM_JOINED).payloadAs(RoomSnapshot.class).mode()==GameMode.DESIGNER,"Joining peer receives mode");
                    a.read(MessageType.PLAYER_JOINED);
                    a.send(MessageType.READY,null); a.read(MessageType.ROOM_UPDATED); b.read(MessageType.ROOM_UPDATED);
                    b.send(MessageType.READY,null); a.read(MessageType.ROOM_UPDATED); b.read(MessageType.ROOM_UPDATED);
                    require(a.read(MessageType.START_PLACEMENT).payloadAs(RoomSnapshot.class).mode()==GameMode.DESIGNER,"Start placement mode"); b.read(MessageType.START_PLACEMENT);
                    var shapes=ShipShapes.all(5).stream().filter(c -> c.stream().map(Coordinate::row).distinct().count()>1 && c.stream().map(Coordinate::column).distinct().count()>1).toList();
                    for(int round=0;round<2;round++) {
                        Board board=fleet(ShipType.CARRIER,shapes.get(round));
                        var valid=submission(board); var invalid=new ArrayList<>(valid.ships());
                        invalid.set(0,new ShipPlacement(ShipType.CARRIER,new Coordinate(0,0),ShipOrientation.HORIZONTAL,
                                List.of(new Coordinate(0,0),new Coordinate(0,1),new Coordinate(0,2),new Coordinate(0,3),new Coordinate(2,3))));
                        a.send(MessageType.SUBMIT_FLEET,new FleetSubmission(invalid)); a.read(MessageType.ERROR);
                        a.send(MessageType.SUBMIT_FLEET,valid);
                        var waiting=a.read(MessageType.FLEET_ACCEPTED).payloadAs(MatchSnapshot.class); b.read(MessageType.FLEET_ACCEPTED);
                        require(waiting.phase()==GamePhase.PLACEMENT && waiting.ownBoard().shots().isEmpty() && waiting.enemyBoard().ships().isEmpty(),"Wait/reset/privacy after accepted fleet");
                        b.send(MessageType.SUBMIT_FLEET,valid);
                        var state=a.read(MessageType.START_BATTLE).payloadAs(MatchSnapshot.class); var other=b.read(MessageType.START_BATTLE).payloadAs(MatchSnapshot.class);
                        require(state.currentPlayerId().equals(a.id) && state.timer().remainingMillis()<=60_000 && state.timer().remainingMillis()>59_000 && other.enemyBoard().ships().isEmpty(),"Both peers start with hidden fleet and 60s");
                        a.send(MessageType.ATTACK,new AttackRequest(new Coordinate(9,9))); a.read(MessageType.MATCH_UPDATED); b.read(MessageType.MATCH_UPDATED);
                        b.send(MessageType.ATTACK,new AttackRequest(new Coordinate(9,8))); a.read(MessageType.MATCH_UPDATED); b.read(MessageType.MATCH_UPDATED);
                        var cells=board.ships().stream().flatMap(s -> s.occupiedCells().stream()).toList(); int ai=0,bi=0;
                        TcpMatchChecks.Peer actor=a;
                        while(state.phase()!=GamePhase.GAME_OVER) {
                            actor.send(MessageType.ATTACK,new AttackRequest(cells.get(actor==a?ai++:bi++)));
                            state=a.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class); other=b.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class);
                            require(state.revision()==other.revision(),"Same authoritative revision on both clients");
                            BoardView enemyView=state.enemyBoard();
                            require(enemyView.ships().stream().allMatch(p -> p.ship().occupiedCells().stream().allMatch(c -> stateCellSunk(enemyView,c))),"Only sunk enemy footprints exposed");
                            actor=actor==a?b:a;
                        }
                        require(state.winnerId().equals(a.id) && state.endReason()==MatchEndReason.FLEET_DESTROYED,"Full custom fleet destroyed");
                        a.send(MessageType.ATTACK,new AttackRequest(new Coordinate(9,7))); a.read(MessageType.ERROR);
                        if(round==0) {
                            a.send(MessageType.REMATCH_REQUEST,null); a.read(MessageType.REMATCH_UPDATED); b.read(MessageType.REMATCH_UPDATED);
                            b.send(MessageType.REMATCH_REQUEST,null); a.read(MessageType.REMATCH_UPDATED); b.read(MessageType.REMATCH_UPDATED);
                            RoomSnapshot next=a.read(MessageType.START_PLACEMENT).payloadAs(RoomSnapshot.class); b.read(MessageType.START_PLACEMENT);
                            require(next.mode()==GameMode.DESIGNER && next.roomCode().equals(created.roomCode()),"Rematch keeps room and designer rules");
                        }
                    }
                    a.send(MessageType.LEAVE_ROOM,null); a.read(MessageType.ROOM_LEFT); b.read(MessageType.REMATCH_UPDATED); b.read(MessageType.PLAYER_LEFT);
                    b.send(MessageType.LEAVE_ROOM,null); b.read(MessageType.ROOM_LEFT);
                    a.send(MessageType.CREATE_ROOM,new CreateRoomRequest(GameMode.CLASSIC));
                    RoomSnapshot classicRoom=a.read(MessageType.ROOM_CREATED).payloadAs(RoomSnapshot.class);
                    require(classicRoom.mode()==GameMode.CLASSIC,"Classic room creation still works");
                    b.send(MessageType.JOIN_ROOM,new JoinRoomRequest(classicRoom.roomCode())); b.read(MessageType.ROOM_JOINED); a.read(MessageType.PLAYER_JOINED);
                    a.send(MessageType.READY,null); a.read(MessageType.ROOM_UPDATED); b.read(MessageType.ROOM_UPDATED);
                    b.send(MessageType.READY,null); a.read(MessageType.ROOM_UPDATED); b.read(MessageType.ROOM_UPDATED);
                    a.read(MessageType.START_PLACEMENT); b.read(MessageType.START_PLACEMENT);
                    a.send(MessageType.SUBMIT_FLEET,new com.google.gson.Gson().fromJson("{\"ships\":[{\"type\":\"UNKNOWN\"}]}",com.google.gson.JsonObject.class));
                    require(a.read(MessageType.ERROR).payloadAs(RoomError.class).message().contains("Missing or unknown"),"Classic error semantics remain compatible");
                    a.send(MessageType.SUBMIT_FLEET,submission(fleet(ShipType.CARRIER,shapes.getFirst()))); a.read(MessageType.ERROR);
                    a.send(MessageType.SUBMIT_FLEET,TcpMatchChecks.fleet()); a.read(MessageType.FLEET_ACCEPTED); b.read(MessageType.FLEET_ACCEPTED);
                    b.send(MessageType.SUBMIT_FLEET,TcpMatchChecks.fleet());
                    var classicState=a.read(MessageType.START_BATTLE).payloadAs(MatchSnapshot.class); b.read(MessageType.START_BATTLE);
                    require(classicState.ownBoard().ships().stream().allMatch(p -> p.cells()==null),"Classic keeps the original straight-ship protocol");
                    var classicCells=TcpMatchChecks.fleet().ships().stream().flatMap(p -> p.ship().occupiedCells().stream()).toList();
                    int ai=0,bi=0; TcpMatchChecks.Peer actor=a;
                    while(classicState.phase()!=GamePhase.GAME_OVER) {
                        actor.send(MessageType.ATTACK,new AttackRequest(classicCells.get(actor==a?ai++:bi++)));
                        classicState=a.read(MessageType.MATCH_UPDATED).payloadAs(MatchSnapshot.class); b.read(MessageType.MATCH_UPDATED);
                        actor=actor==a?b:a;
                    }
                    require(classicState.winnerId().equals(a.id),"Classic full battle still completes");
                    System.out.println("PASS real TCP designer: auth, create/join/mode, invalid fleet retry, private placement, full battle, two rounds/rematch, timer reset and a full Classic regression round.");
                } finally { if(a!=null)a.close(); if(b!=null)b.close(); }
            }
        } finally {
            if(!ids.isEmpty()) try(var connection=Database.fromEnvironment().open()) {
                connection.setAutoCommit(false);
                for(var id:ids) {
                    try(var delete=connection.prepareStatement("DELETE FROM matches WHERE id IN (SELECT match_id FROM match_players WHERE user_id=?)")) { delete.setObject(1,id); delete.executeUpdate(); }
                    try(var delete=connection.prepareStatement("DELETE FROM users WHERE id=? AND username LIKE 'tcp_%'")) { delete.setObject(1,id); delete.executeUpdate(); }
                }
                connection.commit();
            }
        }
    }
    private static boolean stateCellSunk(BoardView board,Coordinate cell) { return board.shots().stream().anyMatch(s -> s.coordinate().equals(cell) && s.state()==CellState.SUNK); }
}
