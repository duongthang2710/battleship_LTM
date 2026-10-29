package com.battleship.frontend.graphics.ship;

import com.battleship.frontend.graphics.Materials;
import com.battleship.shared.game.Ship;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.*;
import com.jme3.scene.control.AbstractControl;
import com.jme3.scene.shape.Sphere;
import java.util.IdentityHashMap;
import java.util.Map;
import static com.battleship.frontend.graphics.ProceduralMeshes.*;

/** Applied only to a visible, server-confirmed sunk piece; intact templates stay untouched. */
public final class ShipWreckBuilder {
    private final Materials materials;
    public ShipWreckBuilder(Materials materials) { this.materials = materials; }

    public void apply(Node placement, Ship ship) {
        Map<Material, Material> charred = new IdentityHashMap<>();
        placement.depthFirstTraversal(spatial -> {
            if (!(spatial instanceof Geometry geometry)) return;
            geometry.setMaterial(charred.computeIfAbsent(geometry.getMaterial(), this::charMaterial));
        });
        for (Spatial child : placement.getChildren()) {
            child.setLocalScale(child.getLocalScale().mult(new Vector3f(1, .58f, 1)));
            child.rotate(0, 0, .10f);
        }
        Node damage = new Node("Sunk ship damage");
        placement.attachChild(damage);
        Material soot = materials.metal(Materials.linear(0x171B20), 8);
        Material scorchedMetal = materials.metal(Materials.linear(0x514638), 15);
        Material ember = materials.glow(Materials.linear(0xB45C28), .55f);
        float center = (ship.type().length() - 1) / 2f;
        for (int i = 0; i < ship.shape().size(); i++) {
            var cell = ship.shape().get(i);
            Vector3f point = ship.custom() ? new Vector3f(cell.row(), .22f, -cell.column())
                    : new Vector3f(0, .22f, i - center);
            Geometry scar = new Geometry("Burned deck breach", new Sphere(6, 10, 1));
            scar.setMaterial(soot); scar.setLocalScale(.24f, .035f, .28f);
            scar.setLocalTranslation(point); damage.attachChild(scar);
            rod(damage, "Broken spar", point.add(-.12f, .02f, -.18f), point.add(.20f, .16f, .17f), .023f, scorchedMetal);
            box(damage, "Bent armor fragment", new Vector3f(.12f, .018f, .065f), point.add(.09f, .08f, -.10f), scorchedMetal).rotate(.35f, .5f, -.4f);
            Geometry coal = new Geometry("Smouldering breach", new Sphere(5, 8, .055f));
            coal.setMaterial(ember); coal.setLocalTranslation(point.add(0, .04f, 0)); damage.attachChild(coal);
        }
        var origin = ship.custom() ? ship.shape().get(ship.shape().size() / 2) : null;
        Vector3f smokeOrigin = origin == null ? new Vector3f(0, .32f, 0) : new Vector3f(origin.row(), .32f, -origin.column());
        for (int i = 0; i < 3; i++) {
            Geometry puff = new Geometry("Wreck smoke", new Sphere(6, 8, .16f));
            Material smoke = materials.flat(new ColorRGBA(.07f, .075f, .09f, .18f));
            smoke.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
            smoke.getAdditionalRenderState().setDepthWrite(false);
            puff.setMaterial(smoke); puff.setQueueBucket(Bucket.Transparent); puff.setShadowMode(ShadowMode.Off);
            puff.addControl(new Smoke(smokeOrigin, i / 3f)); damage.attachChild(puff);
        }
        placement.setUserData("wreck", true);
    }

    private Material charMaterial(Material source) {
        Material result = source.clone();
        for (String param : new String[] {"Diffuse", "Ambient", "Specular", "Color"}) {
            if (result.getParam(param) == null) continue;
            ColorRGBA color = ((ColorRGBA) result.getParam(param).getValue()).clone();
            float grey = color.r * .25f + color.g * .5f + color.b * .25f;
            color.interpolateLocal(new ColorRGBA(grey * .28f + .016f, grey * .25f + .016f, grey * .24f + .019f, color.a), .88f);
            result.setColor(param, color);
        }
        if (result.getParam("GlowColor") != null) result.setColor("GlowColor", ColorRGBA.Black);
        if (result.getParam("Shininess") != null) result.setFloat("Shininess", 9);
        return result;
    }

    private static final class Smoke extends AbstractControl {
        private final Vector3f origin;
        private float age;
        Smoke(Vector3f origin, float age) { this.origin = origin; this.age = age; }
        @Override protected void controlUpdate(float dt) {
            age = (age + Math.max(0, dt) * .32f) % 1;
            spatial.setLocalTranslation(origin.add(age * .17f, age * .9f, age * .08f));
            spatial.setLocalScale(.5f + age * 1.5f);
            ((Geometry) spatial).getMaterial().setColor("Color", new ColorRGBA(.07f, .075f, .09f,
                    (float) Math.sin(age * Math.PI) * .30f));
        }
        @Override protected void controlRender(RenderManager rm, ViewPort vp) { }
    }
}
