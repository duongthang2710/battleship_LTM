package com.battleship.frontend.effects;

import com.battleship.frontend.game.BoardCoordinates;
import com.battleship.frontend.graphics.Materials;
import com.battleship.shared.game.*;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.*;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.*;
import com.jme3.scene.shape.Cylinder;
import java.util.ArrayList;
import java.util.List;
import static com.battleship.frontend.graphics.ProceduralMeshes.*;

final class AirstrikeEffect {
    private static final float HEIGHT=4.2f, INTERVAL=.22f, RELEASE_START=.45f, DROP_DURATION=.65f;
    private final Node root=new Node("Airstrike bombing run");
    private final Node formation=new Node("Airstrike formation");
    private final Node shadow=new Node("Airstrike formation shadow");
    private final Vector3f first,direction;
    private final List<Node> bombs=new ArrayList<>();
    private final List<Vector3f> targets=new ArrayList<>();
    private final List<Float> releases=new ArrayList<>();

    AirstrikeEffect(Materials materials,BoardCoordinates coordinates,SkillResult result,Side side) {
        first=coordinates.localCenter(result.area().getFirst(),HEIGHT);
        direction=coordinates.localCenter(result.area().getLast(),HEIGHT).subtract(first).normalizeLocal();
        Node aircraft=AirstrikeAircraft.create(materials,side);
        ColorRGBA shade=new ColorRGBA(.01f,.025f,.045f,.17f);
        Material shadowMaterial=materials.flat(shade);
        shadowMaterial.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        shadowMaterial.getAdditionalRenderState().setDepthWrite(false);
        shadowMaterial.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        for(int slot=-1;slot<=1;slot++) {
            Node bomber=aircraft.clone(false);
            float scale=slot==0?1:.75f;
            bomber.setLocalScale(scale); bomber.setLocalTranslation(slot*1.8f,0,-Math.abs(slot)*1.25f);
            formation.attachChild(bomber);
            Node silhouette=bomber.clone(false);
            silhouette.depthFirstTraversal(spatial->{if(spatial instanceof Geometry geometry) {
                geometry.setMaterial(shadowMaterial); geometry.setQueueBucket(RenderQueue.Bucket.Transparent);
            }});
            shadow.attachChild(silhouette);
        }
        shadow.setLocalScale(1,.015f,1);
        root.attachChild(shadow); root.attachChild(formation);
        Node bombTemplate=bomb(materials);
        for(AttackResult impact:result.impacts()) {
            targets.add(coordinates.localCenter(impact.coordinate(),.25f));
            releases.add(RELEASE_START+result.area().indexOf(impact.coordinate())*INTERVAL);
            Node bomb=bombTemplate.clone(false); root.attachChild(bomb); bombs.add(bomb);
        }
        update(0);
    }

    Node node() { return root; }
    float impactTime(int index) { return releases.get(index)+DROP_DURATION; }
    float shake(float elapsed) {
        return elapsed>=impactTime(0) && elapsed<impactTime(releases.size()-1)+.25f?.025f:0;
    }

    private Vector3f flight(float time) {
        return first.add(direction.mult((time-RELEASE_START-DROP_DURATION)/INTERVAL));
    }

    void update(float elapsed) {
        Vector3f position=flight(elapsed);
        float yaw=FastMath.atan2(direction.x,direction.z);
        formation.setLocalTranslation(position);
        formation.setLocalRotation(new Quaternion().fromAngleAxis(yaw,Vector3f.UNIT_Y));
        shadow.setLocalTranslation(position.x,.26f,position.z); shadow.setLocalRotation(formation.getLocalRotation());
        boolean gone=elapsed>RELEASE_START+DROP_DURATION+12*INTERVAL;
        formation.setCullHint(gone?Spatial.CullHint.Always:Spatial.CullHint.Inherit);
        shadow.setCullHint(formation.getLocalCullHint());
        for(int index=0;index<bombs.size();index++) {
            Node bomb=bombs.get(index);
            float age=elapsed-releases.get(index);
            bomb.setCullHint(age<0 || age>=DROP_DURATION?Spatial.CullHint.Always:Spatial.CullHint.Inherit);
            float progress=FastMath.clamp(age/DROP_DURATION,0,1);
            Vector3f start=flight(releases.get(index)).add(0,-.18f,0),target=targets.get(index);
            Vector3f point=FastMath.interpolateLinear(progress,start,target);
            point.y=FastMath.interpolateLinear(progress*progress,start.y,target.y);
            bomb.setLocalTranslation(point);
            Vector3f velocity=target.subtract(start); velocity.y=-2*progress*(start.y-target.y);
            bomb.setLocalRotation(new Quaternion().lookAt(velocity.normalizeLocal(),Vector3f.UNIT_Y));
        }
    }

    private static Node bomb(Materials materials) {
        Node bomb=new Node("Falling airstrike bomb");
        Material casing=materials.metal(Materials.linear(0x344854),100);
        Material brass=materials.metal(Materials.linear(0xDBBC76),125);
        Geometry body=new Geometry("Streamlined bomb casing",new Cylinder(3,12,.085f,.055f,.42f,true,false));
        body.setMaterial(casing); bomb.attachChild(body);
        Geometry nose=new Geometry("Tapered bomb nose",new Cylinder(2,12,.055f,.005f,.14f,true,false));
        nose.setMaterial(brass); nose.setLocalTranslation(0,0,.28f); bomb.attachChild(nose);
        rod(bomb,"Bomb gilded band",new Vector3f(0,0,.08f),new Vector3f(0,0,.12f),.088f,brass);
        for(int side:new int[]{-1,1}) {
            box(bomb,"Bomb stabilizer fin",new Vector3f(.075f,.012f,.09f),new Vector3f(side*.075f,0,-.18f),brass);
            box(bomb,"Bomb stabilizer fin",new Vector3f(.012f,.075f,.09f),new Vector3f(0,side*.075f,-.18f),brass);
        }
        Geometry wake=new Geometry("Bomb descent wake",new Cylinder(2,8,.012f,.045f,.40f,true,false));
        Material trail=materials.flat(new ColorRGBA(.7f,.85f,.95f,.20f));
        trail.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        trail.getAdditionalRenderState().setDepthWrite(false);
        wake.setMaterial(trail); wake.setLocalTranslation(0,0,-.44f); wake.setQueueBucket(RenderQueue.Bucket.Transparent);
        bomb.attachChild(wake); bomb.setShadowMode(RenderQueue.ShadowMode.Off);
        return bomb;
    }
}
