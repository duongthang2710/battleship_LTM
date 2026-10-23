package com.battleship.frontend.scene.board;

import com.battleship.frontend.graphics.FantasyPalette;
import com.battleship.frontend.graphics.Materials;
import com.jme3.bounding.BoundingBox;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.BatchNode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.control.AbstractControl;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Quad;
import com.jme3.scene.shape.Torus;

import static com.battleship.frontend.graphics.ProceduralMeshes.*;

public final class BoardBuilder {
    private static final float COORDINATE_CENTER = 5.505f;
    private final Materials materials;
    private final Material gold;
    private final Material blue;
    private final Material paleGold;

    public BoardBuilder(Materials materials) {
        this.materials = materials;
        gold = materials.metal(FantasyPalette.GOLD, 76);
        gold.setColor("Specular", Materials.linear(0xFFF0C6).mult(0.95f));
        blue = materials.glow(FantasyPalette.CYAN, 1.1f);
        paleGold = materials.metal(FantasyPalette.PALE_GOLD, 108);
        paleGold.setColor("Specular", Materials.linear(0xFFF8E6));
    }

    public Node create(BitmapFont font) {
        Node root = new Node("Collector edition battle board");
        root.rotate(0, FastMath.HALF_PI, 0);
        BatchNode board = new BatchNode("Layered enamel and outer gold filigree");
        root.attachChild(board);
        Geometry base = new Geometry("Carved obsidian foundation", beveledPrism(clippedSquare(6.42f, 0.30f),
                new float[] {-1.06f, -0.99f, 0.06f, 0.12f}, new float[] {0.985f, 1, 1, 0.99f}));
        base.setMaterial(materials.metal(FantasyPalette.NAVY, 120));
        board.attachChild(base);
        Geometry skirt = new Geometry("Fine golden lower lip", beveledPrism(clippedSquare(6.44f, 0.32f),
                new float[] {-1.04f, -0.99f}, new float[] {0.995f, 1}));
        skirt.setMaterial(gold);
        board.attachChild(skirt);
        Material enamel = materials.flat(Materials.linear(0x0B192C));
        Material channel = materials.metal(Materials.linear(0x102D4D), 110);
        playingSurface(root);

        // Grid <=5, coordinates 5.13..5.88, filigree >=5.98. The outer footprint stays unchanged.
        for (int sign : new int[] {-1, 1}) {
            box(board, "Clear horizontal coordinate band", new Vector3f(5.13f, 0.027f, 0.375f),
                    new Vector3f(0, 0.205f, sign * COORDINATE_CENTER), enamel);
            box(board, "Clear vertical coordinate band", new Vector3f(0.375f, 0.027f, 5.13f),
                    new Vector3f(sign * COORDINATE_CENTER, 0.205f, 0), enamel);
            for (int other : new int[] {-1, 1}) {
                box(board, "Coordinate band corner", new Vector3f(0.375f, 0.027f, 0.375f),
                        new Vector3f(sign * COORDINATE_CENTER, 0.205f, other * COORDINATE_CENTER), enamel);
            }
            for (float distance : new float[] {5.035f, 5.12f, 5.89f, 5.97f, 6.34f, 6.405f}) {
                box(board, "Fine gold frame molding", new Vector3f(6.08f, 0.013f, 0.012f),
                        new Vector3f(0, 0.25f, sign * distance), gold);
                box(board, "Fine gold frame molding", new Vector3f(0.012f, 0.013f, 6.08f),
                        new Vector3f(sign * distance, 0.25f, 0), gold);
            }
            box(board, "Recessed sapphire filigree channel", new Vector3f(5.80f, 0.018f, 0.18f),
                    new Vector3f(0, 0.213f, sign * 6.16f), channel);
            box(board, "Recessed sapphire filigree channel", new Vector3f(0.18f, 0.018f, 5.80f),
                    new Vector3f(sign * 6.16f, 0.213f, 0), channel);
            beveledRail(board, sign, false);
            beveledRail(board, sign, true);
            sideRelief(board, sign, false);
            sideRelief(board, sign, true);
            for (int i = 0; i <= 10; i++) {
                float p = i - 5;
                box(board, "Coordinate divider tick", new Vector3f(0.006f, 0.004f, 0.028f),
                        new Vector3f(p, 0.243f, sign * 5.16f), paleGold);
                box(board, "Coordinate divider tick", new Vector3f(0.028f, 0.004f, 0.006f),
                        new Vector3f(sign * 5.16f, 0.243f, p), paleGold);
            }
            rod(board, "Raised outer gold rail", new Vector3f(-5.86f, 0.24f, sign * 6.35f),
                    new Vector3f(5.86f, 0.24f, sign * 6.35f), 0.025f, paleGold);
            rod(board, "Raised outer gold rail", new Vector3f(sign * 6.35f, 0.24f, -5.86f),
                    new Vector3f(sign * 6.35f, 0.24f, 5.86f), 0.025f, paleGold);
            for (int i = 0; i < 9; i++) {
                if (i == 4) continue;
                ornament(board, new Vector3f(-5.12f + i * 1.28f, 0.24f, sign * 6.16f), 0);
                ornament(board, new Vector3f(sign * 6.16f, 0.24f, -5.12f + i * 1.28f), FastMath.HALF_PI);
            }
            for (int i = 0; i < 39; i++) {
                float p = -5.7f + i * 0.30f;
                gem(board, new Vector3f(p, 0.23f, sign * 6.35f),
                        new Vector3f(0.028f, 0.026f, 0.028f), paleGold);
                gem(board, new Vector3f(sign * 6.35f, 0.23f, p),
                        new Vector3f(0.028f, 0.026f, 0.028f), paleGold);
            }
        }
        wovenRails(board);
        Material grid = materials.glow(Materials.linear(0x4DB4E7), 0.85f);
        for (int i = 0; i <= 10; i++) {
            float position = i - 5;
            box(board, "Grid column " + i, new Vector3f(0.0055f, 0.002f, 5),
                    new Vector3f(position, 0.216f, 0), grid);
            box(board, "Grid row " + i, new Vector3f(5, 0.002f, 0.0055f),
                    new Vector3f(0, 0.216f, position), grid);
        }
        for (int sign : new int[] {-1, 1}) {
            medallion(board, sign * 6.12f, 0);
            medallion(board, 0, sign * 6.12f);
        }
        for (int x : new int[] {-1, 1})
            for (int z : new int[] {-1, 1}) beacon(board, x * 6.13f, z * 6.13f, x > 0);
        crown(board, -1);
        crown(board, 1);
        board.setShadowMode(ShadowMode.CastAndReceive);
        board.batch();
        Node coordinates = new Node("Unobstructed coordinate labels");
        root.attachChild(coordinates);
        for (int i = 0; i < 10; i++) {
            // Labels counter-rotate so they remain upright for the default player.
            label(coordinates, font, String.valueOf((char) ('A' + i)), COORDINATE_CENTER, 4.5f - i);
            label(coordinates, font, String.valueOf((char) ('A' + i)), -COORDINATE_CENTER, 4.5f - i);
            label(coordinates, font, Integer.toString(i + 1), i - 4.5f, COORDINATE_CENTER);
            label(coordinates, font, Integer.toString(i + 1), i - 4.5f, -COORDINATE_CENTER);
        }
        return root;
    }

    private void sideRelief(Node board, int sign, boolean side) {
        Node fascia = new Node("Sculpted gold and sapphire side fascia");
        fascia.setLocalTranslation(side ? sign * 6.433f : 0, -0.465f, side ? 0 : sign * 6.433f);
        fascia.setLocalScale(1, 2, 1);
        fascia.rotate(0, side ? sign * FastMath.HALF_PI : (sign < 0 ? FastMath.PI : 0), 0);
        for (int edge : new int[] {-1, 1}) {
            rod(fascia, "Fascia raised gold border", new Vector3f(-6.03f, edge * 0.175f, 0),
                    new Vector3f(6.03f, edge * 0.175f, 0), 0.012f, paleGold);
            rod(fascia, "Fine parallel fascia engraving", new Vector3f(-6.03f, edge * 0.145f, 0),
                    new Vector3f(6.03f, edge * 0.145f, 0), 0.006f, gold);
        }
        for (int i = 0; i < 9; i++) {
            float x = -5.12f + i * 1.28f;
            Geometry star = new Geometry("Raised celestial fascia seal", beveledPrism(starOutline(0.115f, 0.036f),
                    new float[] {0, 0.009f, 0.018f}, new float[] {1, 1, 0.82f}));
            star.rotate(FastMath.HALF_PI, 0, 0);
            star.setLocalTranslation(x, 0, 0.002f);
            star.setMaterial(paleGold);
            fascia.attachChild(star);
            gem(fascia, new Vector3f(x, 0, 0.021f), new Vector3f(0.038f, 0.052f, 0.012f), blue);
            for (int direction : new int[] {-1, 1}) {
                Vector3f previous = new Vector3f(x + direction * 0.10f, 0, 0.004f);
                for (int j = 1; j <= 16; j++) {
                    float u = j / 16f;
                    float angle = u * FastMath.TWO_PI * 1.15f;
                    float radius = 0.09f * (1 - u * 0.65f);
                    Vector3f next = new Vector3f(x + direction * (0.10f + u * 0.41f),
                            radius * FastMath.sin(angle), 0.004f);
                    rod(fascia, "Repousse gold scroll", previous, next, 0.010f, gold);
                    previous = next;
                }
                gem(fascia, new Vector3f(x + direction * 0.36f, 0.095f, 0.006f),
                        new Vector3f(0.12f, 0.035f, 0.012f), paleGold).rotate(0, 0, direction * 0.25f);
                gem(fascia, new Vector3f(x + direction * 0.36f, -0.095f, 0.006f),
                        new Vector3f(0.12f, 0.035f, 0.012f), paleGold).rotate(0, 0, -direction * 0.25f);
            }
        }
        board.attachChild(fascia);
    }

    private void beveledRail(Node board, int sign, boolean side) {
        Geometry rail = new Geometry("Polished chamfered outer molding",
                beveledPrism(new float[][] {{-5.82f, -0.040f}, {5.82f, -0.040f},
                        {5.82f, 0.040f}, {-5.82f, 0.040f}},
                        new float[] {0, 0.025f, 0.040f}, new float[] {1, 1, 0.78f}));
        rail.setMaterial(paleGold);
        rail.setLocalTranslation(side ? sign * 6.405f : 0, 0.245f, side ? 0 : sign * 6.405f);
        if (side) rail.rotate(0, FastMath.HALF_PI, 0);
        board.attachChild(rail);
    }

    private void playingSurface(Node root) {
        Material surface = new Material(gold.getMaterialDef().getAssetManager(), "materials/BoardSurface.j3md");
        surface.setFloat("Time", 0);
        Geometry geometry = new Geometry("Crystalline playing surface", new Quad(10, 10));
        geometry.rotate(-FastMath.HALF_PI, 0, 0);
        geometry.setLocalTranslation(-5, 0.185f, 5);
        geometry.setMaterial(surface);
        geometry.setShadowMode(ShadowMode.Receive);
        geometry.addControl(new AbstractControl() {
            private float time;
            @Override protected void controlUpdate(float delta) {
                time += delta;
                surface.setFloat("Time", time);
            }
            @Override protected void controlRender(RenderManager manager, ViewPort viewport) { }
        });
        root.attachChild(geometry);
    }

    private void wovenRails(Node board) {
        for (int sign : new int[] {-1, 1}) {
            for (int strand : new int[] {-1, 1}) {
                Vector3f previous = new Vector3f(-5.78f, 0.29f, sign * 6.16f);
                for (int i = 1; i <= 144; i++) {
                    float x = -5.78f + i * 11.56f / 144;
                    float wave = strand * 0.15f * FastMath.sin((x + 5.78f) * FastMath.TWO_PI / 1.28f);
                    Vector3f next = new Vector3f(x, 0.29f, sign * (6.16f + wave));
                    rod(board, "Interlaced gold ribbon", previous, next, 0.009f, paleGold);
                    rod(board, "Interlaced side ribbon", new Vector3f(previous.z, previous.y, previous.x),
                            new Vector3f(next.z, next.y, next.x), 0.009f, paleGold);
                    previous = next;
                }
            }
        }
    }

    private void ornament(Node board, Vector3f position, float angle) {
        Node motif = new Node("Gilded leaf filigree");
        motif.setLocalTranslation(position);
        motif.rotate(0, angle, 0);
        for (int side : new int[] {-1, 1}) {
            Vector3f previous = new Vector3f();
            for (int i = 1; i <= 24; i++) {
                float u = i / 24f, scrollAngle = u * FastMath.TWO_PI * 1.5f;
                Vector3f next = new Vector3f(side * (0.24f - 0.24f * FastMath.cos(scrollAngle)),
                        0.025f + 0.055f * FastMath.sin(u * FastMath.PI), 0.16f * FastMath.sin(scrollAngle));
                rod(motif, "Golden scrollwork", previous, next, 0.012f, gold);
                previous = next;
            }
            gem(motif, new Vector3f(side * 0.30f, 0.045f, 0.05f),
                    new Vector3f(0.17f, 0.04f, 0.09f), paleGold).rotate(0, side * 0.4f, 0);
            gem(motif, new Vector3f(side * 0.15f, 0.07f, -0.06f),
                    new Vector3f(0.07f, 0.035f, 0.14f), gold).rotate(0, side * 0.6f, 0);
            gem(motif, new Vector3f(side * 0.47f, 0.08f, 0),
                    new Vector3f(0.13f, 0.045f, 0.11f), paleGold);
            rod(motif, "Engraved leaf vein", new Vector3f(side * 0.25f, 0.095f, 0.06f),
                    new Vector3f(side * 0.56f, 0.095f, 0), 0.007f, gold);
        }
        Geometry star = new Geometry("Celestial filigree rosette", beveledPrism(starOutline(0.13f, 0.045f),
                new float[] {0.09f, 0.12f, 0.14f}, new float[] {1, 1, 0.80f}));
        star.setMaterial(paleGold);
        motif.attachChild(star);
        for (int side : new int[] {-1, 1}) {
            Vector3f previous = new Vector3f(0, 0.035f, -0.12f);
            for (int i = 1; i <= 12; i++) {
                float u = i / 12f;
                Vector3f next = new Vector3f(side * u * 0.57f,
                        0.055f + 0.12f * FastMath.sin(u * FastMath.PI), -0.12f + u * 0.05f);
                rod(motif, "Raised acanthus arch", previous, next, 0.010f, paleGold);
                previous = next;
            }
            gem(motif, new Vector3f(side * 0.44f, 0.17f, -0.07f),
                    new Vector3f(0.12f, 0.055f, 0.07f), gold).rotate(0, side * 0.45f, 0);
        }
        gem(motif, new Vector3f(0, 0.10f, 0), new Vector3f(0.07f, 0.07f, 0.07f), blue);
        board.attachChild(motif);
    }

    private void medallion(Node board, float x, float z) {
        Node seal = new Node("Celestial edge seal");
        seal.setLocalTranslation(x, 0.25f, z);
        Geometry ring = new Geometry("Etched gold seal ring", new Torus(10, 32, 0.017f, 0.20f));
        ring.rotate(-FastMath.HALF_PI, 0, 0);
        ring.setMaterial(paleGold);
        seal.attachChild(ring);
        Geometry outerRing = new Geometry("Fine concentric compass border", new Torus(8, 32, 0.007f, 0.225f));
        outerRing.rotate(-FastMath.HALF_PI, 0, 0);
        outerRing.setMaterial(gold);
        seal.attachChild(outerRing);
        Geometry star = new Geometry("Inset eight-point compass", beveledPrism(starOutline(0.21f, 0.07f),
                new float[] {0.008f, 0.033f, 0.045f}, new float[] {1, 1, 0.83f}));
        star.setMaterial(paleGold);
        seal.attachChild(star);
        for (int i = 0; i < 8; i++) {
            float angle = i * FastMath.QUARTER_PI;
            rod(seal, "Compass engraving", new Vector3f(),
                    new Vector3f(FastMath.cos(angle) * 0.22f, 0, FastMath.sin(angle) * 0.22f), 0.009f, gold);
        }
        gem(seal, new Vector3f(0, 0.035f, 0), new Vector3f(0.07f, 0.055f, 0.07f), blue);
        board.attachChild(seal);
    }

    private void beacon(Node board, float x, float z, boolean violet) {
        Node beacon = new Node("Crystal corner finial");
        beacon.setLocalTranslation(x, 0.13f, z);
        Geometry plinth = new Geometry("Octagonal carved crystal pedestal", beveledPrism(clippedSquare(0.31f, 0.09f),
                new float[] {0, 0.04f, 0.13f, 0.17f}, new float[] {1, 1, 0.84f, 0.78f}));
        plinth.setMaterial(paleGold);
        beacon.attachChild(plinth);
        Geometry foot = new Geometry("Finial base", new Cylinder(2, 24, 0.32f, 0.15f, true));
        foot.rotate(FastMath.HALF_PI, 0, 0);
        foot.setMaterial(gold);
        beacon.attachChild(foot);
        for (int i = 0; i < 3; i++) {
            Geometry rim = new Geometry("Layered corner crown", new Torus(12, 40, 0.023f, 0.33f - i * 0.045f));
            rim.rotate(-FastMath.HALF_PI, 0, 0);
            rim.setLocalTranslation(0, 0.05f + i * 0.09f, 0);
            rim.setMaterial(i == 1 ? paleGold : gold);
            beacon.attachChild(rim);
        }
        Material crystal = materials.metal(Materials.linear(violet ? 0xB9A8F5 : 0xA8DAF5), 95);
        crystal.setColor("Specular", Materials.linear(0xF4F7FF).mult(1.15f));
        crystal.setColor("GlowColor", Materials.linear(violet ? 0xAF75EE : 0x73CFFF).mult(0.42f));
        gem(beacon, new Vector3f(0, 0.77f, 0), new Vector3f(0.24f, 0.60f, 0.24f), crystal);
        gem(beacon, new Vector3f(0, 0.77f, 0), new Vector3f(0.09f, 0.45f, 0.09f),
                materials.glow(Materials.linear(violet ? 0xE5D7FF : 0xECF8FF), 0.60f));
        Geometry collar = new Geometry("Crystal equatorial gold setting", new Torus(8, 32, 0.011f, 0.245f));
        collar.rotate(-FastMath.HALF_PI, 0, 0);
        collar.setLocalTranslation(0, 0.77f, 0);
        collar.setMaterial(paleGold);
        beacon.attachChild(collar);
        for (int i = 0; i < 8; i++) {
            float angle = i * FastMath.QUARTER_PI;
            Vector3f flute = new Vector3f(FastMath.cos(angle) * 0.25f, 0.07f, FastMath.sin(angle) * 0.25f);
            rod(beacon, "Carved pedestal flute", flute, flute.add(0, 0.16f, 0), 0.010f, gold);
            gem(beacon, new Vector3f(FastMath.cos(angle) * 0.32f, 0.26f, FastMath.sin(angle) * 0.32f),
                    new Vector3f(0.045f, 0.06f, 0.045f), i % 2 == 0 ? crystal : paleGold);
        }
        for (int i = 0; i < 4; i++) {
            float angle = i * FastMath.HALF_PI;
            Vector3f a = new Vector3f(0.26f * FastMath.cos(angle), 0.12f, 0.26f * FastMath.sin(angle));
            Vector3f b = new Vector3f(0.23f * FastMath.cos(angle), 0.57f, 0.23f * FastMath.sin(angle));
            rod(beacon, "Crystal cage", a, b, 0.022f, gold);
            rod(beacon, "Crystal crown", b, new Vector3f(0, 1.40f, 0), 0.019f, gold);
            gem(beacon, b, new Vector3f(0.06f, 0.19f, 0.055f), paleGold);
        }
        gem(beacon, new Vector3f(0, 1.40f, 0), new Vector3f(0.04f, 0.08f, 0.04f), gold);
        board.attachChild(beacon);
    }

    private void crown(Node board, int side) {
        Node crown = new Node(side < 0 ? "North compass crown" : "South compass crown");
        crown.setLocalTranslation(side * 6.28f, 0.95f, 0);
        crown.rotate(0, -side * FastMath.HALF_PI, 0);
        Geometry ring = new Geometry("Compass ring", new Torus(12, 64, 0.024f, 0.64f));
        ring.setMaterial(gold);
        crown.attachChild(ring);
        Geometry outer = new Geometry("Outer celestial astrolabe", new Torus(12, 64, 0.016f, 0.83f));
        outer.setMaterial(paleGold);
        crown.attachChild(outer);
        for (int i = 0; i < 32; i++) {
            float angle = i * FastMath.TWO_PI / 32;
            float radius = i % 4 == 0 ? 0.70f : 0.75f;
            rod(crown, "Astrolabe graduated gold index",
                    new Vector3f(FastMath.cos(angle) * radius, FastMath.sin(angle) * radius, 0.018f),
                    new Vector3f(FastMath.cos(angle) * 0.80f, FastMath.sin(angle) * 0.80f, 0.018f),
                    i % 4 == 0 ? 0.008f : 0.005f, paleGold);
        }
        Geometry inset = new Geometry("Royal blue compass enamel", new Cylinder(2, 48, 0.60f, 0.035f, true));
        inset.setMaterial(materials.metal(Materials.linear(0x123B79), 145));
        inset.setLocalTranslation(0, 0, -0.035f);
        crown.attachChild(inset);
        Geometry star = new Geometry("Sculpted eight-point celestial star", beveledPrism(starOutline(0.74f, 0.18f),
                new float[] {-0.02f, 0.012f, 0.045f}, new float[] {1, 1, 0.85f}));
        star.rotate(FastMath.HALF_PI, 0, 0);
        star.setMaterial(paleGold);
        crown.attachChild(star);
        gem(crown, new Vector3f(0, 0, 0.06f), new Vector3f(0.17f, 0.29f, 0.10f), blue);
        for (int i = 0; i < 8; i++) {
            float angle = i * FastMath.QUARTER_PI;
            rod(crown, "Compass star", new Vector3f(),
                    new Vector3f(FastMath.cos(angle) * (i % 2 == 0 ? 0.77f : 0.46f),
                            FastMath.sin(angle) * (i % 2 == 0 ? 0.77f : 0.46f), 0), 0.017f, gold);
            gem(crown, new Vector3f(FastMath.cos(angle) * 0.83f, FastMath.sin(angle) * 0.83f, 0),
                    new Vector3f(0.04f, 0.06f, 0.04f), i % 2 == 0 ? blue : gold);
        }
        for (int sign : new int[] {-1, 1}) {
            Vector3f previous = new Vector3f(sign * 0.58f, 0, 0);
            for (int i = 1; i <= 12; i++) {
                float u = i / 12f;
                Vector3f next = new Vector3f(sign * (0.58f + u * 1.75f),
                        0.35f * FastMath.sin(u * FastMath.PI) - u * 0.45f, 0);
                rod(crown, "Crown scroll", previous, next, 0.024f, gold);
                previous = next;
            }
            for (int i = 1; i <= 6; i++) {
                float u = i / 7f;
                float x = sign * (0.58f + u * 1.75f);
                float y = 0.35f * FastMath.sin(u * FastMath.PI) - u * 0.45f;
                for (int leafSide : new int[] {-1, 1}) {
                    Geometry leaf = gem(crown, new Vector3f(x, y + leafSide * 0.08f, 0.018f),
                            new Vector3f(0.17f, 0.065f, 0.028f), leafSide > 0 ? paleGold : gold);
                    leaf.rotate(0, 0, sign * leafSide * 0.45f);
                }
            }
            box(crown, "Compass gilded mounting foot", new Vector3f(0.15f, 0.065f, 0.09f),
                    new Vector3f(sign * 1.38f, -0.63f, 0), gold);
            rod(crown, "Sculpted compass support", new Vector3f(sign * 1.38f, -0.57f, 0),
                    new Vector3f(sign * 1.17f, -0.08f, 0), 0.018f, paleGold);
        }
        board.attachChild(crown);
    }

    private static float[][] starOutline(float radius, float innerRadius) {
        float[][] outline = new float[16][2];
        for (int i = 0; i < outline.length; i++) {
            float angle = i * FastMath.PI / 8;
            float r = i % 2 != 0 ? innerRadius : (i % 4 == 0 ? radius : radius * 0.72f);
            outline[i][0] = FastMath.cos(angle) * r;
            outline[i][1] = FastMath.sin(angle) * r;
        }
        return outline;
    }

    private static void label(Node board, BitmapFont font, String value, float x, float z) {
        BitmapText text = new BitmapText(font);
        text.setName("Board coordinate " + value);
        text.setText(value);
        text.setSize(font.getCharSet().getRenderedSize());
        text.setColor(Materials.linear(0xFFF2D7));
        text.setShadowMode(ShadowMode.Off);
        text.updateLogicalState(0);
        text.updateGeometricState();
        BoundingBox bounds = (BoundingBox) text.getWorldBound();
        text.setLocalScale(Math.min(0.62f / (2 * bounds.getYExtent()), 0.66f / (2 * bounds.getXExtent())));
        text.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
        text.updateGeometricState();
        Vector3f center = ((BoundingBox) text.getWorldBound()).getCenter();
        text.setLocalTranslation(x - center.x, 0.247f, z - center.z);
        board.attachChild(text);
    }
}
