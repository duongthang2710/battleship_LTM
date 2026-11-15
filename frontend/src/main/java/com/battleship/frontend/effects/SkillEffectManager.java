package com.battleship.frontend.effects;

import com.battleship.frontend.audio.GameAudio.Cue;
import com.battleship.frontend.game.BoardCoordinates;
import com.battleship.frontend.graphics.Materials;
import com.battleship.shared.game.*;
import com.jme3.math.*;
import com.jme3.scene.*;
import com.jme3.scene.shape.Box;
import java.util.*;
import java.util.function.Consumer;

/** Presentation of server-approved skills and private tactical markers. */
public final class SkillEffectManager {
    // Board-local height above the hull, leaving the masts and sails exposed.
    private static final float SHIELD_BASE_HEIGHT = .85f;
    private record Impact(AttackResult result, float at) { }
    private record Burst(HitEffect hit, MissEffect miss, Node node, float at) { }
    private final Node animation = new Node("Tactical skill effects");
    private final Node markers = new Node("Private tactical markers");
    private final Materials materials;
    private final AegisShieldEffect shieldVisuals;
    private final List<Node> shieldNodes = new ArrayList<>();
    private final BoardCoordinates coordinates;
    private final OceanGunfire gunfire;
    private final List<Impact> impacts = new ArrayList<>();
    private final List<Burst> bursts = new ArrayList<>();
    private List<Coordinate> shownMines = List.of();
    private List<ShieldCell> shownShields = List.of();
    private List<SonarReading> shownScans = List.of();
    private Consumer<Cue> sound = cue -> { };
    private Consumer<String> showBoard = id -> { };
    private Runnable impactCallback = () -> { }, complete = () -> { };
    private SonarEffect sonar;
    private Node mine;
    private final List<ProjectileEffect> incoming = new ArrayList<>();
    private final List<Float> flightStarts = new ArrayList<>();
    private final List<String> flightTargets = new ArrayList<>();
    private final Set<Integer> launched = new HashSet<>();
    private int counterCount;
    private Side firingSide;
    private AirstrikeEffect airstrike;
    private Vector3f from, to;
    private float elapsed, duration;
    private int nextImpact;
    private boolean impacted;
    private SkillType skill;
    private float shake;

    SkillEffectManager(Node board, Materials materials) {
        this.materials = materials;
        shieldVisuals = new AegisShieldEffect(materials);
        coordinates = new BoardCoordinates(board); gunfire = new OceanGunfire(board);
        board.attachChild(animation); board.attachChild(markers);
    }
    public void onSound(Consumer<Cue> callback) { sound = callback; }
    public boolean busy() { return duration > 0; }
    public boolean impacted() { return impacted; }
    public float shake() { return shake; }

    public void play(SkillResult result, Side side, Consumer<String> boardView, Runnable onImpact, Runnable onDone) {
        clearAnimation();
        skill = result.skill(); showBoard = boardView;
        firingSide = side;
        impactCallback = onImpact; complete = onDone;
        duration = skill == SkillType.AIRSTRIKE ? 3.5f : skill == SkillType.SONAR ? SonarEffect.DURATION : 1.5f;
        from = coordinates.localCenter(result.origin(), .25f); to = from.clone();
        switch (skill) {
            case SONAR -> {
                sound.accept(Cue.SONAR);
                sonar = new SonarEffect(materials, coordinates, result.area());
                animation.attachChild(sonar.node());
            }
            case MINE -> {
                sound.accept(Cue.MISS);
                to = from.clone(); from = gunfire.source(side, to, true);
                mine = mineModel(animation, from);
            }
            case SHIELD -> {
                sound.accept(Cue.SHIELD);
                for(Coordinate cell:result.area()) dome(animation,cell);
            }
            case AIRSTRIKE -> {
                sound.accept(Cue.AIRSTRIKE);
                airstrike = new AirstrikeEffect(materials, coordinates, result, side);
                animation.attachChild(airstrike.node());
                for (int i = 0; i < result.impacts().size(); i++)
                    addImpact(result.impacts().get(i), airstrike.impactTime(i));
                impacts.sort(Comparator.comparingDouble(Impact::at));
                duration = Math.max(4.2f, impacts.getLast().at()+1.3f);
            }
        }
    }

    public void playDefense(AttackResult result, Side incomingSide, Consumer<String> boardView, Runnable onImpact, Runnable onDone) {
        clearAnimation(); skill = null; showBoard = boardView; impactCallback = onImpact; complete = onDone;
        firingSide = incomingSide;
        Vector3f target=coordinates.localCenter(result.coordinate(),result.defense()==DefenseKind.SHIELD?SHIELD_BASE_HEIGHT+.2f:.3f);
        ProjectileEffect projectile=new ProjectileEffect(materials,gunfire.source(incomingSide,target),target,incomingSide==Side.WHITE);
        animation.attachChild(projectile.node()); incoming.add(projectile); sound.accept(Cue.CANNON);
        flightStarts.add(0f); flightTargets.add(result.defenderId());
        addImpact(result, 1);
        impacts.sort(Comparator.comparingDouble(Impact::at));
        duration = impacts.isEmpty() ? 1.5f : impacts.getLast().at() + 1.5f;
    }

    private void addImpact(AttackResult result, float at) {
        impacts.add(new Impact(result, at));
        float delay = at + 3;
        for (AttackResult counter : result.counterattacks()) {
            if (counterCount++ >= 8) break;
            Vector3f target = coordinates.localCenter(counter.coordinate(),counter.defense()==DefenseKind.SHIELD?SHIELD_BASE_HEIGHT+.2f:.3f);
            ProjectileEffect flight = new ProjectileEffect(materials,gunfire.source(firingSide.opponent(),target),target,false);
            animation.attachChild(flight.node()); incoming.add(flight);
            flightStarts.add(delay-1); flightTargets.add(counter.defenderId());
            addImpact(counter, delay);
            delay += 1.35f;
        }
    }

    public void sync(List<Coordinate> mines, List<ShieldCell> shields, List<SonarReading> scans) {
        if (mines.equals(shownMines) && shields.equals(shownShields) && scans.equals(shownScans)) return;
        shownMines = List.copyOf(mines); shownShields = List.copyOf(shields); shownScans = List.copyOf(scans);
        markers.detachAllChildren();
        for (Coordinate cell : mines) mineModel(markers, coordinates.localCenter(cell, .25f));
        for (ShieldCell cell : shields) dome(markers,cell.coordinate());
        for (SonarReading scan : scans) markers.attachChild(SonarEffect.outline(materials, coordinates, scan.area()));
    }

    public void update(float delta) {
        updateShields();
        if (!busy()) { shake = 0; return; }
        elapsed += Math.max(0, delta);
        for (int i=0;i<incoming.size();i++) {
            float age=elapsed-flightStarts.get(i);
            if(age>=0 && launched.add(i)) showBoard.accept(flightTargets.get(i));
            incoming.get(i).update(age);
        }
        if (sonar != null) sonar.update(elapsed);
        if (mine != null) {
            float progress = FastMath.clamp(elapsed/1.15f,0,1);
            mine.setLocalTranslation(FastMath.interpolateLinear(progress,from,to).add(0,FastMath.sin(progress*FastMath.PI)*2,0));
            mine.setLocalScale(1-progress*.65f);
        }
        if (airstrike != null) { airstrike.update(elapsed); shake = airstrike.shake(elapsed); }
        while (nextImpact < impacts.size() && elapsed >= impacts.get(nextImpact).at()) {
            Impact impact = impacts.get(nextImpact++);
            showBoard.accept(impact.result().defenderId());
            burst(impact.result(),impact.at());
            if (!impacted) { impacted = true; impactCallback.run(); }
        }
        if (impacts.isEmpty() && !impacted && elapsed>=.35f) { impacted=true; impactCallback.run(); }
        for (Burst burst : bursts) {
            float age = elapsed-burst.at();
            if (burst.hit()!=null) burst.hit().update(age);
            if (burst.miss()!=null) burst.miss().update(age);
            if (age>1.2f) burst.node().removeFromParent();
        }
        if (elapsed >= duration) {
            Runnable callback = complete;
            clearAnimation();
            callback.run();
        }
    }

    private void burst(AttackResult result, float at) {
        Vector3f target = coordinates.localCenter(result.coordinate(), .25f);
        if (result.defense() == DefenseKind.SHIELD) {
            target.y=SHIELD_BASE_HEIGHT+.2f;
            sound.accept(Cue.SHIELD_BREAK);
            for (int i=0;i<10;i++) {
                Vector3f shard=target.add(EffectVisuals.direction(i,10).mult(.25f)); shard.y+=.18f+(i%3)*.12f;
                box(animation,shard,new Vector3f(.04f,.06f,.025f),0x80DDF8);
            }
        } else if (result.type()==AttackResultType.MISS) {
            MissEffect splash = new MissEffect(materials); splash.node().setLocalTranslation(target);
            animation.attachChild(splash.node()); bursts.add(new Burst(null,splash,splash.node(),at)); sound.accept(Cue.MISS);
        } else {
            if (result.defense()==DefenseKind.MINE) {
                MissEffect column=new MissEffect(materials); column.node().setLocalTranslation(target);
                column.node().setLocalScale(1.5f,2.4f,1.5f); animation.attachChild(column.node());
                bursts.add(new Burst(null,column,column.node(),at));
            }
            HitEffect explosion = new HitEffect(materials,result.defense()==DefenseKind.MINE || result.sunk());
            explosion.node().setLocalTranslation(target); animation.attachChild(explosion.node());
            bursts.add(new Burst(explosion,null,explosion.node(),at));
            sound.accept(result.defense()==DefenseKind.MINE ? Cue.MINE : result.sunk()?Cue.SUNK:Cue.HIT);
        }
    }

    private Node mineModel(Node parent, Vector3f center) {
        Node model = new Node("Submerged naval mine"); parent.attachChild(model); model.setLocalTranslation(center);
        EffectVisuals.sphere(model,materials,0x7D9FAF,.12f,false);
        for (int i=0;i<8;i++) box(model,EffectVisuals.direction(i,8).mult(.14f),new Vector3f(.035f,.035f,.035f),0xB7DCE2);
        return model;
    }
    private void dome(Node parent, Coordinate cell) {
        Node shield=shieldVisuals.create();
        shield.setLocalTranslation(coordinates.localCenter(cell,SHIELD_BASE_HEIGHT));
        parent.attachChild(shield); shieldNodes.add(shield);
    }
    private void updateShields() {
        shieldNodes.removeIf(shield->shield.getParent()==null);
        if(shieldNodes.isEmpty()) return;
        for(Node shield:shieldNodes) {
            shield.setCullHint(shield.getParent()==markers && busy() && skill==SkillType.SHIELD
                    ?Spatial.CullHint.Always:Spatial.CullHint.Inherit);
        }
    }
    private void box(Node parent,Vector3f position,Vector3f size,int color) {
        Geometry geometry=new Geometry("Tactical detail",new Box(size.x,size.y,size.z));
        geometry.setMaterial(materials.glow(Materials.linear(color),.4f)); geometry.setLocalTranslation(position); parent.attachChild(geometry);
    }
    public void clearAnimation() {
        animation.detachAllChildren(); impacts.clear(); bursts.clear(); incoming.clear(); nextImpact=0; elapsed=duration=shake=0;
        flightStarts.clear(); flightTargets.clear(); launched.clear(); counterCount=0;
        animation.setLocalTranslation(0,0,0);
        impacted=false; sonar=null; mine=null; airstrike=null;
        shieldNodes.removeIf(shield->shield.getParent()==null);
        for(Node shield:shieldNodes) shield.setCullHint(Spatial.CullHint.Inherit);
    }
    public void clear() {
        clearAnimation(); markers.detachAllChildren(); shieldNodes.clear(); shownMines=List.of(); shownShields=List.of(); shownScans=List.of();
    }
}
