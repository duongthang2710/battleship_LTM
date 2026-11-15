package com.battleship.backend.match;

import com.battleship.shared.game.*;
import com.battleship.shared.match.*;
import com.battleship.shared.room.RoomOptions;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;
import static com.battleship.backend.match.RoomRulesChecks.require;

public final class SkillMatchChecks {
    public static void main(String[] args) {
        AtomicLong time=new AtomicLong();
        var rules=new GameRules(GameMode.CLASSIC,5,TurnRule.ALTERNATING,CombatMode.SKILLS);
        var match=new MatchService(DesignerMatchChecks.PLAYERS,rules,new RoomOptions(true),time::get);
        var fleet=RoomRulesChecks.fleet(rules);
        match.submit("a",fleet); match.submit("b",fleet);
        require(match.snapshot("a").skills().ownEnergy()==0,"Countdown starts at zero energy");
        require(!match.useSkill("a",new SkillRequest(SkillType.SONAR,new Coordinate(0,0),null)).accepted(),"Countdown blocks skills");
        time.set(TimeUnit.SECONDS.toNanos(3));
        require(match.timer().energy().get("a")==0 && match.timer().energy().get("a")==0,"Timer reads grant no energy");
        require(match.attack("a",new Coordinate(9,9)).accepted()
                && match.snapshot("a").skills().ownEnergy()==1 && match.snapshot("b").skills().ownEnergy()==0,
                "A miss grants exactly one point over match snapshots");
        require(match.attack("b",new Coordinate(9,9)).accepted()
                && match.snapshot("a").skills().ownEnergy()==1 && match.snapshot("b").skills().ownEnergy()==1,
                "Changing turn adds no extra energy to either player");
        charge(match,3);
        time.addAndGet(TimeUnit.SECONDS.toNanos(20));
        long remaining=match.timer().remainingMillis();
        var sonar=match.useSkill("a",new SkillRequest(SkillType.SONAR,new Coordinate(0,0),null));
        require(sonar.accepted() && match.timer().remainingMillis()==remaining && match.timer().currentPlayerId().equals("a"),"Support preserves turn and deadline");
        require(match.snapshot("a").skills().ownScans().getFirst().count()==6
                && match.snapshot("b").skills().ownScans().isEmpty() && match.snapshot("b").skills().lastSkill().sonarCount()==null,"Only scanner gets total count");
        require(match.useSkill("a",new SkillRequest(SkillType.SONAR,new Coordinate(7,7),null)).type()==AttackResultType.SKILL_ALREADY_USED
                && match.timer().remainingMillis()==remaining,"Server rejects repeat skill without resetting deadline");
        require(match.snapshot("a").skills().ownUsedSkills().equals(java.util.List.of(SkillType.SONAR))
                && match.snapshot("b").skills().ownUsedSkills().isEmpty(),"Used skill inventory is per recipient");
        charge(match,4);
        match.useSkill("a",new SkillRequest(SkillType.MINE,new Coordinate(8,8),null));
        require(match.snapshot("a").skills().ownMines().size()==1 && match.snapshot("b").skills().ownMines().isEmpty()
                && match.snapshot("b").skills().lastSkill()==null,"Mine location is absent from opponent payload");
        time.addAndGet(TimeUnit.SECONDS.toNanos(60));
        require(!match.useSkill("a",new SkillRequest(SkillType.SHIELD,new Coordinate(2,1),null)).accepted()
                && match.endReason()==MatchEndReason.TURN_TIMEOUT,"Support cannot extend deadline or bypass timeout");
        System.out.println("PASS skill match: count-only sonar, shot-only energy, single-use inventory, unchanged support deadline and timeout lock.");
    }
    private static void charge(MatchService match,int amount) {
        while(match.snapshot("a").skills().ownEnergy()<amount) {
            var target=match.snapshot("a").enemyBoard().board();
            Coordinate water=java.util.stream.IntStream.range(0,50)
                    .mapToObj(index->new Coordinate(9-index/5,5+index%5))
                    .filter(cell->!target.wasAttacked(cell)).findFirst().orElseThrow();
            require(match.attack("a",water).accepted() && match.attack("b",water).accepted(),"Charge by valid normal shots");
        }
    }
}
