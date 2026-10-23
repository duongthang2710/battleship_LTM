package com.battleship.frontend.graphics.ship;

import com.battleship.frontend.graphics.FantasyPalette;
import com.battleship.frontend.graphics.Materials;
import com.jme3.material.Material;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.BatchNode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Cylinder;
import com.jme3.scene.shape.Torus;

import static com.battleship.frontend.graphics.ProceduralMeshes.*;

public final class ShipFactory {
    public enum Fleet { RADIANT, ABYSSAL }
    private final Materials materials;

    public ShipFactory(Materials materials) { this.materials = materials; }

    public Node create(Fleet fleet) {
        return create(fleet, false);
    }

    public Node createOceanVessel(Fleet fleet) {
        return create(fleet, true);
    }

    private Node create(Fleet fleet, boolean afloat) {
        boolean radiant = fleet == Fleet.RADIANT;
        BatchNode ship = new BatchNode(fleet + " fantasy flagship");
        Material hull = materials.metal(radiant ? FantasyPalette.IVORY
                : afloat ? Materials.linear(0x0E1930) : FantasyPalette.ABYSS, 125);
        Material trim = materials.metal(radiant ? (afloat ? FantasyPalette.PALE_GOLD : FantasyPalette.GOLD)
                : Materials.linear(afloat ? 0x9E82B4 : 0x8C4CCB), afloat ? 90 : 145);
        if (afloat) trim.setColor("Specular", Materials.linear(radiant ? 0xFFF2D5 : 0xDED3F2));
        Material deck = materials.metal(radiant ? Materials.linear(0x102A63) : Materials.linear(0x100A24), 90);
        Material jewel = materials.glow(radiant ? FantasyPalette.CYAN : FantasyPalette.VIOLET, afloat ? 1.2f : 2.3f);
        Material canvas = materials.metal(Materials.linear(radiant
                ? afloat ? 0xEEE8DE : 0xFAF0D9 : afloat ? 0x312144 : 0x160A2C), 22);
        Material enamel = materials.metal(radiant ? Materials.linear(0x155FAF) : Materials.linear(0x492168), 115);
        canvas.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
        float[][] outline = {{-0.27f, -2.15f}, {0.27f, -2.15f}, {0.44f, -1.72f},
                {0.55f, -1.05f}, {0.58f, -0.2f}, {0.52f, 0.85f}, {0.35f, 1.70f},
                {0.17f, 2.38f}, {0, 3.10f}, {-0.17f, 2.38f}, {-0.35f, 1.70f},
                {-0.52f, 0.85f}, {-0.58f, -0.2f}, {-0.55f, -1.05f}, {-0.44f, -1.72f}};
        prism(ship, "Sculpted slender keel", outline, new float[] {-0.28f, -0.06f, 0.23f, 0.35f},
                new float[] {0.18f, 0.63f, 0.96f, 1}, hull);
        prism(ship, "Gilded gunwale", outline, new float[] {0.35f, 0.39f},
                new float[] {1, 0.98f}, trim);
        prism(ship, "Azure deck", outline, new float[] {0.39f, 0.41f},
                new float[] {0.89f, 0.88f}, deck);
        prism(ship, "Raised stern castle", clippedSquare(0.34f, 0.10f),
                new float[] {0.38f, 0.63f, 0.69f}, new float[] {1, 0.92f, 1}, hull)
                .setLocalTranslation(0, 0, -1.68f);
        for (int i = 0; i < outline.length; i++) {
            float[] a = outline[i], b = outline[(i + 1) % outline.length];
            rod(ship, "Gilded railing", new Vector3f(a[0], 0.50f, a[1]),
                    new Vector3f(b[0], 0.50f, b[1]), 0.018f, trim);
            rod(ship, "Rail pillar", new Vector3f(a[0], 0.34f, a[1]),
                    new Vector3f(a[0], 0.54f, a[1]), 0.017f, trim);
        }
        for (int side : new int[] {-1, 1}) {
            for (int i = 0; i < 4; i++) {
                float z = -1.15f + i * 0.64f;
                rod(ship, "Sweeping hull ornament", new Vector3f(side * 0.38f, -0.03f, z - 0.18f),
                        new Vector3f(side * 0.54f, 0.32f, z + 0.20f), 0.024f, trim);
                rod(ship, "Hull ornament return", new Vector3f(side * 0.54f, 0.32f, z + 0.20f),
                        new Vector3f(side * 0.40f, 0.02f, z + 0.43f), 0.018f, trim);
            }
            if (!afloat) gem(ship, new Vector3f(side * 0.53f, 0.16f, 0.26f),
                    new Vector3f(0.12f, 0.24f, 0.20f), jewel).rotate(0, 0, FastMath.HALF_PI);
            for (int i = 0; i < 3; i++) {
                box(ship, "Polished fleet enamel inlay", new Vector3f(0.018f, 0.10f, 0.22f),
                        new Vector3f(side * 0.53f, 0.21f, -0.95f + i * 0.65f), enamel);
                gem(ship, new Vector3f(side * 0.56f, 0.23f, -0.95f + i * 0.65f),
                        new Vector3f(0.025f, 0.055f, 0.055f), trim);
            }
            for (int i = 0; i < 3; i++) {
                Geometry cannon = new Geometry("Broadside cannon", new Cylinder(2, 12, 0.035f, 0.24f, true));
                cannon.setMaterial(trim);
                cannon.rotate(0, FastMath.HALF_PI, 0);
                cannon.setLocalTranslation(side * 0.54f, 0.43f, -0.75f + i * 0.55f);
                ship.attachChild(cannon);
            }
            rod(ship, "Spearhead prow", new Vector3f(side * 0.31f, 0.35f, 1.40f),
                    new Vector3f(0, 0.92f, 3.18f), 0.028f, trim);
            rod(ship, "Prow crest", new Vector3f(side * 0.27f, 0.65f, 1.70f),
                    new Vector3f(0, 1.12f, 2.88f), 0.021f, trim);
            Vector3f previous = new Vector3f(side * 0.5f, 0.4f, 1.1f);
            for (int i = 1; i <= 16; i++) {
                float u = i / 16f;
                Vector3f next = new Vector3f(side * (0.5f * (1 - u) + 0.18f * FastMath.sin(u * FastMath.PI)),
                        0.4f + 0.32f * FastMath.sin(u * FastMath.PI), 1.1f + 2.0f * u);
                rod(ship, "Sweeping prow filigree", previous, next, 0.025f, trim);
                previous = next;
            }
            rod(ship, "Stern ornament spire", new Vector3f(side * 0.28f, 0.65f, -1.83f),
                    new Vector3f(side * 0.36f, radiant ? 1.18f : 1.4f, -2.12f), 0.025f, trim);
            gem(ship, new Vector3f(side * 0.36f, radiant ? 1.18f : 1.4f, -2.12f),
                    new Vector3f(0.05f, 0.13f, 0.05f), jewel);
        }
        gem(ship, new Vector3f(0, 0.66f, 2.62f), new Vector3f(0.18f, 0.30f, 0.13f), jewel);
        Geometry prowCompass = new Geometry("Prow celestial medallion", new Torus(12, 40, 0.018f, 0.29f));
        prowCompass.setMaterial(trim);
        prowCompass.setLocalTranslation(0, 0.68f, 2.69f);
        ship.attachChild(prowCompass);
        for (int i = 0; i < 8; i++) {
            float angle = i * FastMath.QUARTER_PI;
            rod(ship, "Prow star rays", new Vector3f(0, 0.68f, 2.70f),
                    new Vector3f(FastMath.cos(angle) * 0.31f, 0.68f + FastMath.sin(angle) * 0.31f, 2.70f), 0.01f, trim);
        }
        if (afloat) {
            new OceanSailBuilder().build(ship, radiant, canvas, trim, jewel, enamel);
            oceanDetails(ship, radiant, hull, trim, enamel);
            new OceanHullDetails(materials).add(ship, radiant, outline, hull, trim, enamel, jewel);
        } else {
            mast(ship, -1.40f, 2.70f, 1.03f, canvas, trim, jewel);
            mast(ship, -0.05f, 3.35f, 1.32f, canvas, trim, jewel);
            mast(ship, 1.21f, 2.80f, 0.99f, canvas, trim, jewel);
            Mesh jibMesh = new Mesh();
            jibMesh.setBuffer(VertexBuffer.Type.Position, 3, new float[] {0, 0.80f, 2.77f, 0, 2.50f, 1.18f, 0, 0.80f, 1.18f});
            jibMesh.setBuffer(VertexBuffer.Type.Normal, 3, new float[] {1, 0, 0, 1, 0, 0, 1, 0, 0});
            jibMesh.setBuffer(VertexBuffer.Type.TexCoord, 2, new float[6]);
            jibMesh.updateBound();
            Geometry jib = new Geometry("Pointed forward silk jib", jibMesh);
            jib.setMaterial(canvas);
            ship.attachChild(jib);
            rod(ship, "Forward jib gold hem", new Vector3f(0, 0.80f, 2.77f), new Vector3f(0, 2.50f, 1.18f), 0.013f, trim);
        }
        ship.setShadowMode(ShadowMode.CastAndReceive);
        ship.batch();
        ship.updateGeometricState();
        // Keep the existing render batches; rigid ships only need a root transform each frame.
        Node baked = new Node(ship.getName());
        baked.setShadowMode(ShadowMode.CastAndReceive);
        for (Spatial child : ship.getChildren()) {
            if (child instanceof Geometry batch && batch.isIgnoreTransform()) {
                Geometry geometry = new Geometry(batch.getName(), batch.getMesh());
                geometry.setMaterial(batch.getMaterial());
                geometry.getMesh().setStatic();
                baked.attachChild(geometry);
            }
        }
        return baked;
    }

    private void oceanDetails(Node ship, boolean radiant, Material hull, Material trim, Material enamel) {
        Material glass = materials.glow(Materials.linear(radiant ? 0x6DBBCD : 0x9678BC), 0.55f);
        box(ship, "Stern officers gallery", new Vector3f(0.25f, 0.16f, 0.34f),
                new Vector3f(0, 0.84f, -1.76f), hull);
        box(ship, "Gallery gilded roof", new Vector3f(0.29f, 0.025f, 0.38f),
                new Vector3f(0, 1.02f, -1.76f), trim);
        box(ship, "Upper navigation cabin", new Vector3f(0.17f, 0.11f, 0.23f),
                new Vector3f(0, 1.15f, -1.85f), enamel);
        prism(ship, "Crowned navigation roof", clippedSquare(0.21f, 0.05f),
                new float[] {1.26f, 1.29f, 1.36f}, new float[] {1, 1, 0.55f}, trim)
                .setLocalTranslation(0, 0, -1.85f);
        prism(ship, "Raised forecastle platform", clippedSquare(0.24f, 0.06f),
                new float[] {0.40f, 0.53f, 0.57f}, new float[] {1, 0.94f, 1}, hull)
                .setLocalTranslation(0, 0, 1.75f);
        for (int side : new int[] {-1, 1}) {
            for (int i = 0; i < 3; i++) {
                float z = -1.99f + i * 0.23f;
                box(ship, "Gallery window border", new Vector3f(0.013f, 0.073f, 0.077f),
                        new Vector3f(side * 0.258f, 0.84f, z), trim);
                box(ship, "Recessed gallery glass", new Vector3f(0.015f, 0.05f, 0.055f),
                        new Vector3f(side * 0.261f, 0.84f, z), glass);
            }
            box(ship, "Navigation cabin glass", new Vector3f(0.008f, 0.055f, 0.14f),
                    new Vector3f(side * 0.177f, 1.15f, -1.85f), glass);
            for (int i = 0; i < 5; i++) {
                float z = -1.19f + i * 0.48f;
                float x = side * (0.54f - Math.abs(z + 0.1f) * 0.045f);
                Geometry rim = new Geometry("Brass hull porthole", new Torus(8, 16, 0.009f, 0.037f));
                rim.setMaterial(trim);
                rim.rotate(0, FastMath.HALF_PI, 0);
                rim.setLocalTranslation(x, 0.26f, z);
                ship.attachChild(rim);
                box(ship, "Porthole glass", new Vector3f(0.008f, 0.026f, 0.026f),
                        new Vector3f(x, 0.26f, z), glass);
            }
            rod(ship, "Stern balcony rail", new Vector3f(side * 0.30f, 1.12f, -2.11f),
                    new Vector3f(side * 0.30f, 1.12f, -1.38f), 0.014f, trim);
            for (int i = 0; i < 5; i++) {
                float z = -2.11f + i * 0.18f;
                rod(ship, "Balcony baluster", new Vector3f(side * 0.30f, 1.03f, z),
                        new Vector3f(side * 0.30f, 1.12f, z), 0.009f, trim);
            }
        }
    }

    private void mast(Node ship, float z, float height, float width,
                      Material canvas, Material gold, Material jewel) {
        rod(ship, "Golden mast", new Vector3f(0, 0.37f, z), new Vector3f(0, height, z), 0.025f, gold);
        gem(ship, new Vector3f(0, height + 0.09f, z), new Vector3f(0.055f, 0.16f, 0.055f), jewel);
        for (int side : new int[] {-1, 1}) {
            rod(ship, "Rigging", new Vector3f(side * 0.42f, 0.39f, z - 0.42f),
                    new Vector3f(0, height - 0.12f, z), 0.006f, gold);
        }
        sail(ship, z, 0.66f, width, 0.95f, canvas, gold, jewel);
        sail(ship, z, 1.76f, width * 0.76f, height - 2.02f, canvas, gold, jewel);
    }

    private void sail(Node ship, float z, float y, float width, float height,
                      Material canvas, Material gold, Material jewel) {
        Node sail = new Node("Curved embroidered sail");
        sail.setLocalTranslation(0, y, z + 0.035f);
        Geometry cloth = new Geometry("Silk sail", curvedSail(width, height));
        cloth.setMaterial(canvas);
        sail.attachChild(cloth);
        rod(sail, "Lower sail hem", new Vector3f(-width / 2, 0, 0), new Vector3f(width / 2, 0, 0), 0.014f, gold);
        rod(sail, "Upper sail hem", new Vector3f(-width * 0.39f, height, 0),
                new Vector3f(width * 0.39f, height, 0), 0.014f, gold);
        for (int side : new int[] {-1, 1}) {
            rod(sail, "Sail border", new Vector3f(side * width / 2, 0, 0),
                    new Vector3f(side * width * 0.39f, height, 0), 0.012f, gold);
        }
        Geometry crest = new Geometry("Sail compass crest", new Torus(8, 24, 0.009f, height * 0.17f));
        crest.setMaterial(gold);
        crest.setLocalTranslation(0, height * 0.52f, 0.28f);
        sail.attachChild(crest);
        rod(sail, "Crest cross", new Vector3f(-height * 0.18f, height * 0.52f, 0.29f),
                new Vector3f(height * 0.18f, height * 0.52f, 0.29f), 0.008f, gold);
        rod(sail, "Crest cross", new Vector3f(0, height * 0.30f, 0.29f),
                new Vector3f(0, height * 0.74f, 0.29f), 0.008f, gold);
        gem(sail, new Vector3f(0, height * 0.52f, 0.30f), new Vector3f(0.035f, 0.055f, 0.03f), jewel);
        ship.attachChild(sail);
    }

    private static Geometry prism(Node parent, String name, float[][] outline,
                                   float[] heights, float[] scales, Material material) {
        Geometry geometry = new Geometry(name, beveledPrism(outline, heights, scales));
        geometry.setMaterial(material);
        parent.attachChild(geometry);
        return geometry;
    }
}
