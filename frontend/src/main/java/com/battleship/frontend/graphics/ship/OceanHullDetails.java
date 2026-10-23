package com.battleship.frontend.graphics.ship;

import com.battleship.frontend.graphics.Materials;
import com.jme3.material.Material;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Torus;

import static com.battleship.frontend.graphics.ProceduralMeshes.*;

final class OceanHullDetails {
    private final Materials materials;

    OceanHullDetails(Materials materials) { this.materials = materials; }

    void add(Node ship, boolean radiant, float[][] outline, Material hull, Material trim,
             Material enamel, Material jewel) {
        Material glass = materials.glow(Materials.linear(radiant ? 0xACDEEE : 0xB396DA), 0.40f);
        layer(ship, "Sapphire hull belt", outline, new float[] {0.025f, 0.08f, 0.17f, 0.20f},
                new float[] {0.755f, 0.815f, 0.925f, 0.94f}, enamel);
        layer(ship, "Lower gilded sheer line", outline, new float[] {-0.005f, 0.018f},
                new float[] {0.715f, 0.752f}, trim);
        layer(ship, "Polished upper hull stringer", outline, new float[] {0.205f, 0.225f},
                new float[] {0.945f, 0.967f}, trim);
        for (int side : new int[] {-1, 1}) {
            hullCompass(ship, side, trim, enamel, jewel);
            for (int i = 0; i < 5; i++) {
                float z = -1.3f + i * 0.49f;
                float x = side * (0.50f - Math.abs(z) * 0.025f);
                Vector3f previous = new Vector3f(x * 0.72f, -0.02f, z - 0.13f);
                for (int j = 1; j <= 8; j++) {
                    float u = j / 8f;
                    Vector3f next = new Vector3f(x * (0.72f + 0.36f * FastMath.sin(u * FastMath.HALF_PI)),
                            -0.02f + u * 0.35f, z - 0.13f + 0.30f * FastMath.sin(u * FastMath.PI));
                    rod(ship, "Swept gilded hull rib", previous, next, 0.016f, trim);
                    previous = next;
                }
                gem(ship, new Vector3f(side * 0.565f, 0.31f, z),
                        new Vector3f(0.018f, 0.038f, 0.095f), trim);
            }
            Vector3f previous = new Vector3f(side * 0.44f, 0.42f, 1.18f);
            for (int i = 1; i <= 16; i++) {
                float u = i / 16f;
                Vector3f next = new Vector3f(side * (0.44f * (1-u) + 0.26f * FastMath.sin(u * FastMath.PI)),
                        0.42f + 0.48f * FastMath.sin(u * FastMath.PI) + u * 0.22f, 1.18f + u * 2.05f);
                rod(ship, "Crown-shaped swept prow wing", previous, next, 0.022f, trim);
                if (i % 4 == 0 && i < 16) {
                    gem(ship, next, new Vector3f(0.055f, 0.16f, 0.11f), trim)
                            .rotate(side * 0.28f, 0, side * 0.35f);
                }
                previous = next;
            }
            rod(ship, "Prow crown lancet", new Vector3f(side * 0.38f, 0.64f, 2.09f),
                    new Vector3f(side * 0.14f, 1.12f, 2.76f), 0.016f, trim);
            tower(ship, side * 0.24f, -1.94f, radiant, hull, trim, enamel, glass);
            rod(ship, "Stern flying buttress", new Vector3f(side * 0.38f, 0.42f, -1.40f),
                    new Vector3f(side * 0.31f, 1.37f, -2.05f), 0.021f, trim);
            for (int i = 0; i < 3; i++) {
                float z = -1.99f + i * 0.23f;
                pointedWindow(ship, new Vector3f(side * 0.28f, 0.84f, z), side, trim, glass);
                Geometry muzzle = new Geometry("Dark inset cannon muzzle", new Cylinder(2, 10, 0.026f, 0.009f, true));
                muzzle.setMaterial(enamel);
                muzzle.rotate(0, FastMath.HALF_PI, 0);
                muzzle.setLocalTranslation(side * 0.665f, 0.43f, -0.75f + i * 0.55f);
                ship.attachChild(muzzle);
            }
            for (float z : new float[] {-1.29f, 1.20f}) {
                rod(ship, "Deck lantern stem", new Vector3f(side * 0.44f, 0.44f, z),
                        new Vector3f(side * 0.44f, 0.71f, z), 0.012f, trim);
                gem(ship, new Vector3f(side * 0.44f, 0.76f, z),
                        new Vector3f(0.045f, 0.072f, 0.045f), glass);
                gem(ship, new Vector3f(side * 0.44f, 0.86f, z),
                        new Vector3f(0.048f, 0.05f, 0.048f), trim);
            }
        }
        layer(ship, "Aft celestial roof ridge", clippedSquare(0.14f, 0.035f),
                new float[] {1.32f, 1.43f, 1.57f}, new float[] {1, 0.80f, 0.06f}, enamel)
                .setLocalTranslation(0, 0, -1.85f);
        rod(ship, "Stern central ceremonial spire", new Vector3f(0, 1.53f, -1.85f),
                new Vector3f(0, 1.88f, -1.85f), 0.015f, trim);
        gem(ship, new Vector3f(0, 1.91f, -1.85f), new Vector3f(0.048f, 0.11f, 0.048f), jewel);
    }

    private void hullCompass(Node ship, int side, Material trim, Material enamel, Material jewel) {
        Node seal = new Node("Ornate fleet hull compass");
        seal.setLocalTranslation(side * 0.58f, 0.22f, 0.38f);
        seal.rotate(0, side * FastMath.HALF_PI, 0);
        Geometry inset = new Geometry("Royal enamel compass inset", new Cylinder(2, 24, 0.20f, 0.015f, true));
        inset.setMaterial(enamel);
        seal.attachChild(inset);
        Geometry ring = new Geometry("Polished compass setting", new Torus(8, 32, 0.014f, 0.225f));
        ring.setMaterial(trim);
        seal.attachChild(ring);
        for (int i = 0; i < 8; i++) {
            float a = i * FastMath.QUARTER_PI;
            float length = i % 2 == 0 ? 0.20f : 0.14f;
            rod(seal, "Eight-point nautical star", new Vector3f(0, 0, 0.014f),
                    new Vector3f(FastMath.cos(a) * length, FastMath.sin(a) * length, 0.014f), 0.009f, trim);
        }
        gem(seal, new Vector3f(0, 0, 0.03f), new Vector3f(0.05f, 0.075f, 0.02f), jewel);
        ship.attachChild(seal);
    }

    private void tower(Node ship, float x, float z, boolean radiant, Material hull,
                       Material trim, Material enamel, Material glass) {
        float top = radiant ? 1.48f : 1.62f;
        float[][] octagon = clippedSquare(0.105f, 0.043f);
        layer(ship, "Stern palace turret", octagon, new float[] {1.00f, 1.07f, top-0.05f, top},
                new float[] {1.10f, 1, 1, 1.12f}, hull).setLocalTranslation(x, 0, z);
        layer(ship, "Gilded turret cornice", octagon, new float[] {top-0.03f, top+0.01f},
                new float[] {1.18f, 1.18f}, trim).setLocalTranslation(x, 0, z);
        layer(ship, "Tapered fantasy turret roof", octagon, new float[] {top, top+0.06f, top+0.34f},
                new float[] {1.12f, 1, 0.03f}, enamel).setLocalTranslation(x, 0, z);
        rod(ship, "Turret roof finial", new Vector3f(x, top+0.30f, z),
                new Vector3f(x, top+0.49f, z), 0.012f, trim);
        for (int side : new int[] {-1, 1})
            pointedWindow(ship, new Vector3f(x + side * 0.107f, top-0.18f, z), side, trim, glass);
    }

    private void pointedWindow(Node ship, Vector3f center, int side, Material trim, Material glass) {
        box(ship, "Palace lancet window", new Vector3f(0.006f, 0.060f, 0.047f), center, glass);
        Vector3f left = center.add(side * 0.007f, -0.066f, -0.052f);
        Vector3f right = center.add(side * 0.007f, -0.066f, 0.052f);
        Vector3f apex = center.add(side * 0.007f, 0.106f, 0);
        rod(ship, "Lancet jamb", left, left.add(0, 0.12f, 0), 0.008f, trim);
        rod(ship, "Lancet jamb", right, right.add(0, 0.12f, 0), 0.008f, trim);
        rod(ship, "Lancet pointed arch", left.add(0, 0.12f, 0), apex, 0.008f, trim);
        rod(ship, "Lancet pointed arch", right.add(0, 0.12f, 0), apex, 0.008f, trim);
    }

    private static Geometry layer(Node ship, String name, float[][] outline, float[] heights,
                                  float[] scales, Material material) {
        Geometry part = new Geometry(name, beveledPrism(outline, heights, scales));
        part.setMaterial(material);
        ship.attachChild(part);
        return part;
    }
}
