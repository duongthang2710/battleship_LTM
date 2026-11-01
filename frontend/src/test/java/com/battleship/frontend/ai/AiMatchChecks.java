package com.battleship.frontend.ai;

import com.battleship.shared.ai.*;
import com.battleship.shared.game.*;
import com.battleship.shared.match.MatchEndReason;
import com.jme3.asset.DesktopAssetManager;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class AiMatchChecks {
    public static void main(String[] args) {
        setupPanel(); timingAndReset(); fullGames();
        System.out.println("PASS: AI setup, countdown, timeout, presentation gates, hidden fleet, complete games and rematch reset.");
    }
    private static void setupPanel() {
        AiSetupPanel panel = new AiSetupPanel(new DesktopAssetManager(true));
        require(panel.difficulty()==AiDifficulty.MEDIUM,"Default difficulty"); panel.show();
        for(int[] size:new int[][]{{960,600},{1440,900},{1920,1080}}) {
            panel.resize(size[0],size[1]);
            for(var action:AiSetupPanel.Action.values()) require(panel.actionAt(panel.buttonCenter(action)).orElseThrow()==action,"Scaled setup hit target");
        }
        panel.select(AiDifficulty.HARD); require(panel.difficulty()==AiDifficulty.HARD,"Difficulty selection");
        panel.hide(); require(panel.actionAt(panel.buttonCenter(AiSetupPanel.Action.START)).isEmpty(),"Hidden card cannot start");
    }
    private static void timingAndReset() {
        AtomicLong clock=new AtomicLong(); GameEngine engine=new GameEngine("Fixture",new Random(1));
        var match=new AiMatchController(engine,AiDifficulty.HARD,"Captain",new Random(2),clock::get);
        require(!match.ready().success(),"Incomplete fleet rejected"); engine.randomPlacement(); require(match.ready().success(),"Valid fleet starts");
        require(match.state().enemy().board().ships().isEmpty(),"Unhit fleet hidden");
        require(match.state().enemy().name().equals("AI Hard"),"Opponent difficulty in name");
        require(match.remainingStartMillis()==3000 && match.remainingTurnMillis()==60_000,"Countdown precedes full turn budget");
        require(match.attackPlayer(new Coordinate(0,0))==null,"Countdown rejects attack");
        clock.set(TimeUnit.MILLISECONDS.toNanos(3000));
        Coordinate hit=engine.state().enemy().board().ships().getFirst().occupiedCells().getFirst();
        AttackResult shot=match.attackPlayer(hit);
        require(shot!=null && shot.shipType()==null && match.state().lastAttack().shipType()==null,"Unresolved hit hides ship type");
        long remaining=match.remainingTurnMillis();
        require(match.attackPlayer(new Coordinate(0,1))==null,"Human cannot fire during bot turn");
        require(match.remainingTurnMillis()==remaining,"Rejected shot does not reset deadline");
        require(match.updateBot(true)==null,"Bot waits for thinking delay"); clock.addAndGet(TimeUnit.SECONDS.toNanos(2));
        require(match.updateBot(false)==null,"Bot respects animation gate");
        require(match.updateBot(true)!=null && match.updateBot(true)==null,"Exactly one bot shot per turn");
        require(match.attackPlayer(hit)==null,"Repeated shot rejected");
        clock.addAndGet(TimeUnit.SECONDS.toNanos(60));
        require(match.attackPlayer(new Coordinate(9,9))==null,"Deadline wins race with incoming shot");
        require(match.state().phase()==GamePhase.GAME_OVER && match.endReason()==MatchEndReason.TURN_TIMEOUT,"Timeout is defeat");
        require(match.state().winnerId().equals(match.state().enemy().id()),"Timed-out player loses");
        require(match.updateBot(true)==null,"No bot callback after game over");
        engine.restart(); match.reset();
        require(match.state().phase()==GamePhase.PLACEMENT && match.endReason()==null && match.state().enemy().board().attackedCells().isEmpty(),"Rematch discards knowledge/result");
        engine.randomPlacement(); match.ready(); clock.addAndGet(TimeUnit.SECONDS.toNanos(63));
        require(match.expireTurn(),"Timer advances without interaction or presentation updates, including Settings");
    }
    private static void fullGames() {
        for(AiDifficulty difficulty:AiDifficulty.values()) {
            AtomicLong clock=new AtomicLong(); GameEngine engine=new GameEngine("Fixture",new Random(21));
            var match=new AiMatchController(engine,difficulty,"Captain",new Random(9),clock::get);
            for(int game=0;game<2;game++) {
                engine.restart(); match.reset(); engine.randomPlacement(); match.ready(); clock.addAndGet(TimeUnit.SECONDS.toNanos(3));
                int shots=0;
                while(match.state().phase()==GamePhase.BATTLE) {
                    AttackResult result;
                    if(match.state().currentPlayerId().equals(match.state().player().id())) {
                        Coordinate cell=match.state().enemy().board().attackedCells().size()<100
                                ? firstUnshot(match.state().enemy().board()) : null;
                        result=match.attackPlayer(cell);
                    } else { clock.addAndGet(TimeUnit.SECONDS.toNanos(2)); result=match.updateBot(true); }
                    require(result!=null && result.accepted(),"Full game advances: " + difficulty);
                    require(++shots<=199,"Finite game");
                }
                require(match.state().winnerId()!=null && match.endReason()==MatchEndReason.FLEET_DESTROYED,"Complete game and rematch: " + difficulty);
            }
        }
    }
    private static Coordinate firstUnshot(Board board) {
        for(int r=0;r<10;r++) for(int col=0;col<10;col++) {Coordinate cell=new Coordinate(r,col);if(!board.wasAttacked(cell))return cell;}
        throw new AssertionError("No target");
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
