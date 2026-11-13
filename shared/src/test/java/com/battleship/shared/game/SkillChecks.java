package com.battleship.shared.game;

import java.util.*;

public final class SkillChecks {
    private static final GameRules RULES = new GameRules(GameMode.CLASSIC,5,TurnRule.ALTERNATING,CombatMode.SKILLS);
    public static void main(String[] args) {
        energyAndSonar();
        singleUsePerMatch();
        mineAndCounterVictory();
        shieldAndAirstrike();
        victoryAndRestart();
        System.out.println("PASS tactical rules: one energy per valid normal shot, count-only sonar, one use per skill/player/match, private mine, shield, airstrike and reset.");
    }
    private static void energyAndSonar() {
        GameEngine game = battle(new Random(1));
        check(game.energy("a")==0 && game.energy("b")==0,"Both players start at zero");
        check(game.useSkill("a",SkillType.SONAR,new Coordinate(0,0),null).type()==AttackResultType.NOT_ENOUGH_ENERGY,"Insufficient energy");
        miss(game,"a");
        check(game.energy("a")==1 && game.energy("b")==0,"Miss grants exactly one to the shooter");
        miss(game,"b");
        check(game.energy("a")==1 && game.energy("b")==1,"Changing turn grants no extra energy");
        check(game.attack("a",new Coordinate(-1,0)).type()==AttackResultType.INVALID_COORDINATE
                && game.attack("a",new Coordinate(0,5)).type()==AttackResultType.ALREADY_ATTACKED
                && game.attack("b",new Coordinate(0,0)).type()==AttackResultType.NOT_YOUR_TURN
                && game.energy("a")==1 && game.energy("b")==1,"Rejected shots grant no energy");
        charge(game,3);
        int turn=game.state().turnNumber();
        SkillResult sonar=game.useSkill("a",SkillType.SONAR,new Coordinate(0,0),null);
        check(sonar.accepted() && sonar.sonarCount()==6 && game.scans("a").getFirst().count()==6,"Sonar reports only the area's count");
        check(!new com.google.gson.Gson().toJson(game.scans("a")).contains("contacts"),"Sonar payload contains no ship-contact coordinates");
        check(game.state().currentPlayerId().equals("a") && game.state().turnNumber()==turn && game.energy("a")==0,"Sonar preserves the main shot and grants no energy");
        check(game.state().enemy().board().attackedCells().size()==3,"Sonar causes no damage");
        game.attack("a",new Coordinate(0,0));
        check(game.energy("a")==1,"Normal hit grants exactly one energy");
        miss(game,"b"); charge(game,10); miss(game,"a");
        check(game.energy("a")==10,"Energy capped at ten");
        GameEngine chain=battle(new Random(7),TurnRule.HIT_CONTINUES);
        for(int column=0;column<5;column++) {
            check(chain.attack("a",new Coordinate(0,column)).accepted() && chain.energy("a")==column+1
                    && chain.energy("b")==0,"Hit-again and sinking grant one point per shot");
        }
    }
    private static void singleUsePerMatch() {
        for(SkillType skill:SkillType.values()) {
            GameEngine game=battle(new Random(6)); charge(game,10);
            Coordinate origin=skill==SkillType.MINE?new Coordinate(8,8):new Coordinate(0,0);
            check(game.useSkill("a",skill,origin,SkillDirection.SOUTH).accepted(),"First use accepted for "+skill);
            if(skill.endsTurn()) miss(game,"b");
            int energy=game.energy("a"), turn=game.state().turnNumber();
            check(game.useSkill("a",skill,new Coordinate(7,7),SkillDirection.EAST).type()==AttackResultType.SKILL_ALREADY_USED
                    && game.energy("a")==energy && game.state().turnNumber()==turn,"Repeat cannot spend energy or change state for "+skill);
            check(game.usedSkills("a").contains(skill) && game.usedSkills("b").isEmpty(),"Skill use belongs to its player");
            miss(game,"a");
            check(game.useSkill("b",skill,origin,SkillDirection.SOUTH).accepted(),"Opponent can independently use "+skill);
            if(!skill.endsTurn()) miss(game,"b");
            charge(game,skill.cost());
            int laterEnergy=game.energy("a"),laterTurn=game.state().turnNumber();
            check(game.useSkill("a",skill,origin,SkillDirection.SOUTH).type()==AttackResultType.SKILL_ALREADY_USED
                    && game.energy("a")==laterEnergy && game.state().turnNumber()==laterTurn,
                    "Skill remains spent on later turns even after recharging for "+skill);
            game.restart();
            check(game.usedSkills("a").isEmpty() && game.usedSkills("b").isEmpty(),"Replay restores both skill inventories");
            var fleet=Arrays.stream(ShipType.values()).map(type->new Ship(type,new Coordinate(type.ordinal()*2,0),ShipOrientation.HORIZONTAL)).toList();
            game.submitFleet("a",fleet); game.submitFleet("b",fleet); charge(game,skill.cost());
            check(game.useSkill("a",skill,origin,SkillDirection.SOUTH).accepted(),"Skill is available again in a new match for "+skill);
        }
    }
    private static void mineAndCounterVictory() {
        GameEngine game=battle(new Random(2)); charge(game,4);
        Coordinate mine=new Coordinate(9,8);
        int energy=game.energy("a");
        check(game.useSkill("a",SkillType.MINE,new Coordinate(0,0),null).type()==AttackResultType.INVALID_SKILL
                && game.energy("a")==energy,"Mine cannot occupy a ship");
        check(game.useSkill("a",SkillType.MINE,mine,null).accepted() && game.mines("a").contains(mine),"Mine deployed privately");
        check(!game.useSkill("a",SkillType.MINE,mine,null).accepted(),"Duplicate mine rejected");
        miss(game,"a");
        int previous=game.state().enemy().board().attackedCells().size();
        AttackResult counter=game.attack("b",mine);
        check(counter.defense()==DefenseKind.MINE && counter.counterattacks().size()==1 && game.mines("a").isEmpty(),"Mine consumes itself and counterattacks");
        check(game.state().enemy().board().attackedCells().size()==previous+1,"Counter chooses an unshot cell");

        game=battle(new Random(0) { @Override public int nextInt(int bound) { return 0; } });
        var cells=game.state().enemy().board().ships().stream().flatMap(ship->ship.occupiedCells().stream()).toList();
        for(Coordinate cell:cells) if(!cell.equals(new Coordinate(0,0))) { game.attack("a",cell); miss(game,"b"); }
        check(game.useSkill("a",SkillType.MINE,mine,null).accepted(),"Winning defense deployed");
        miss(game,"a");
        AttackResult finish=game.attack("b",mine);
        check(finish.gameOver() && "a".equals(finish.winnerId()) && "a".equals(game.state().winnerId()),"Counterattack victory belongs to defender");
    }
    private static void shieldAndAirstrike() {
        GameEngine game=battle(new Random(3)); charge(game,4);
        check(game.useSkill("a",SkillType.SHIELD,new Coordinate(2,1),null).accepted() && game.shields("a").size()==5,"Shield covers a cross");
        miss(game,"a");
        int beforeBlock=game.energy("b");
        AttackResult blocked=game.attack("b",new Coordinate(2,1));
        check(game.energy("b")==beforeBlock+1,"A valid blocked shot grants one point");
        check(blocked.type()==AttackResultType.BLOCKED && blocked.defense()==DefenseKind.SHIELD
                && !game.state().player().board().wasAttacked(new Coordinate(2,1)),"Shield absorbs damage and leaves the tile attackable");
        check(game.shields("a").size()==4 && game.shields("a").getFirst().remainingTurns()==1,"Only hit segment breaks");
        miss(game,"a"); miss(game,"b");
        check(game.shields("a").isEmpty(),"Shield expires after two enemy main actions");
        miss(game,"a");
        check(game.attack("b",new Coordinate(2,1)).type()==AttackResultType.HIT,"Unshielded tile can be hit later");

        game=battle(new Random(4)); charge(game,10);
        game.useSkill("a",SkillType.SHIELD,new Coordinate(2,1),null); miss(game,"a");
        SkillResult strike=game.useSkill("b",SkillType.AIRSTRIKE,new Coordinate(2,7),SkillDirection.EAST);
        check(strike.impacts().size()==10 && strike.impacts().stream().filter(i->i.defense()==DefenseKind.SHIELD).count()==3,"Airstrike respects every shield segment");
        check(game.state().player().board().ship(ShipType.BATTLESHIP).hitCells().size()==1,"Only unshielded ship segment is damaged");
        check(game.state().currentPlayerId().equals("a") && game.energy("b")==2,"Airstrike costs eight, ends turn and grants no hit energy");
        check(SkillTargeting.area(SkillType.AIRSTRIKE,new Coordinate(7,3),SkillDirection.SOUTH).stream().allMatch(c->c.column()==3),"Vertical airstrike targets a column");
        check(SkillTargeting.area(SkillType.SONAR,new Coordinate(9,9),null).size()==9,"Corner scan stays 3x3 within bounds");
    }
    private static void victoryAndRestart() {
        GameEngine game=battle(new Random(5));
        var cells=game.state().enemy().board().ships().stream().flatMap(ship->ship.occupiedCells().stream()).toList();
        for(Coordinate cell:cells) if(!cell.equals(new Coordinate(0,0))) { game.attack("a",cell); miss(game,"b"); }
        var finish=game.useSkill("a",SkillType.AIRSTRIKE,new Coordinate(0,0),SkillDirection.EAST);
        check(finish.gameOver() && game.state().phase()==GamePhase.GAME_OVER,"Airstrike can win");
        check(!game.useSkill("a",SkillType.SONAR,new Coordinate(0,0),null).accepted(),"Skills locked after game over");
        game.restart();
        check(game.energy("a")==0 && game.usedSkills("a").isEmpty() && game.scans("a").isEmpty() && game.mines("a").isEmpty()
                && game.shields("a").isEmpty() && game.lastSkill()==null,"Restart clears all tactical state");
    }
    static GameEngine battle(Random random) {
        return battle(random,TurnRule.ALTERNATING);
    }
    private static GameEngine battle(Random random,TurnRule turnRule) {
        GameEngine game=new GameEngine(new Player("a","A",new Board()),new Player("b","B",new Board()),random);
        game.setRules(new GameRules(RULES.mode(),RULES.shipCount(),turnRule,RULES.combatMode()));
        var fleet=Arrays.stream(ShipType.values()).map(type->new Ship(type,new Coordinate(type.ordinal()*2,0),ShipOrientation.HORIZONTAL)).toList();
        game.submitFleet("a",fleet); game.submitFleet("b",fleet);
        return game;
    }
    static void charge(GameEngine game,int amount) {
        while(game.energy("a")<amount) { miss(game,"a"); miss(game,"b"); }
    }
    static void miss(GameEngine game,String actor) {
        Board target=actor.equals("a")?game.state().enemy().board():game.state().player().board();
        for(int row=0;row<10;row++) for(int column=5;column<10;column++) {
            Coordinate cell=new Coordinate(row,column);
            if(!target.wasAttacked(cell) && !game.mines(actor.equals("a")?"b":"a").contains(cell)) {
                check(game.attack(actor,cell).accepted(),"Water shot accepted"); return;
            }
        }
        throw new AssertionError("Water exhausted");
    }
    static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
