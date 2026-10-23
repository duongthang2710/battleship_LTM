package com.battleship.frontend.graphics.ship;

import com.battleship.frontend.graphics.Materials;
import com.battleship.shared.game.ShipType;
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
import com.jme3.scene.shape.Torus;
import java.util.EnumMap;
import java.util.Map;

import static com.battleship.frontend.graphics.ProceduralMeshes.*;

/** Board pieces use cell units, with the bow along local +Z. */
public final class BoardShipBuilder {
    private final Materials materials;
    private final Map<ShipFactory.Fleet, Map<ShipType, Node>> templates = new EnumMap<>(ShipFactory.Fleet.class);

    public BoardShipBuilder(Materials materials) { this.materials = materials; }

    public Node create(ShipType type, ShipFactory.Fleet fleet) {
        Map<ShipType, Node> ships = templates.computeIfAbsent(fleet, key -> new EnumMap<>(ShipType.class));
        return ships.computeIfAbsent(type, key -> build(key, fleet)).clone(false);
    }

    private Node build(ShipType type, ShipFactory.Fleet fleet) {
        boolean radiant = fleet == ShipFactory.Fleet.RADIANT;
        Material cloth = materials.metal(Materials.linear(radiant ? 0xF5EAD2 : 0x191A24), 24);
        cloth.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
        Paint paint = new Paint(
                materials.metal(Materials.linear(radiant ? 0xF0E7D5 : 0x151A24), 105),
                materials.metal(Materials.linear(radiant ? 0xE8BC66 : 0xB693CF), 135),
                materials.metal(Materials.linear(radiant ? 0x173451 : 0x27213D), 95),
                materials.metal(Materials.linear(radiant ? 0x247FA6 : 0x674791), 110),
                materials.glow(Materials.linear(radiant ? 0x85DAED : 0xB886E6), 1.15f), cloth);
        paint.gold.setColor("Specular", Materials.linear(radiant ? 0xFFF1CB : 0xE6D4FA));
        BatchNode ship = new BatchNode(fleet + " " + type.displayName());
        float halfLength = (type.length() - 0.16f) * 0.5f;
        switch (type) {
            case CARRIER -> carrier(ship, halfLength, paint);
            case BATTLESHIP -> battleship(ship, halfLength, paint);
            case CRUISER -> cruiser(ship, halfLength, paint);
            case SUBMARINE -> submarine(ship, halfLength, paint);
            case DESTROYER -> destroyer(ship, halfLength, paint);
        }
        ship.setShadowMode(ShadowMode.CastAndReceive);
        ship.batch();
        ship.updateGeometricState();
        // Share static meshes between pieces without BatchNode transform updates during placement.
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

    private void carrier(Node ship, float h, Paint p) {
        float w = 0.38f, y = 0.29f;
        float[][] outline = {{-w * .78f, -h}, {w * .78f, -h}, {w, -h * .72f},
                {w, h * .52f}, {w * .70f, h * .83f}, {0, h},
                {-w * .70f, h * .83f}, {-w, h * .52f}, {-w, -h * .72f}};
        hull(ship, outline, y, p);
        prism(ship, "Broad celestial flight deck", outline,
                new float[] {y + .02f, y + .07f}, new float[] {.93f, .93f}, p.deck);
        for (int side : new int[] {-1, 1}) {
            rod(ship, "Long launch-deck gilding", v(side * .23f, y + .075f, -h * .82f),
                    v(side * .23f, y + .075f, h * .62f), .009f, p.gold);
            for (int i = 0; i < 5; i++) {
                float z = -h * .73f + i * h * .28f;
                box(ship, "Recessed hangar enamel", v(.011f, .045f, .11f), v(side * w * .94f, .18f, z), p.enamel);
                rod(ship, "Hangar arch", v(side * w * .96f, .12f, z - .11f),
                        v(side * w * .96f, .24f, z), .007f, p.gold);
                rod(ship, "Hangar arch", v(side * w * .96f, .24f, z),
                        v(side * w * .96f, .12f, z + .11f), .007f, p.gold);
            }
            rod(ship, "Aft beacon mast", v(side * .29f, y, -h * .80f),
                    v(side * .29f, .88f, -h * .80f), .014f, p.gold);
            gem(ship, v(side * .29f, .92f, -h * .80f), v(.032f, .09f, .032f), p.jewel);
        }
        cabin(ship, .24f, -h * .48f, y + .07f, .10f, .23f, .31f, p);
        cabin(ship, .24f, -h * .50f, y + .30f, .074f, .15f, .19f, p);
        rod(ship, "Navigation spire", v(.24f, .71f, -h * .50f), v(.24f, 1.12f, -h * .50f), .012f, p.gold);
        gem(ship, v(.24f, 1.13f, -h * .50f), v(.032f, .075f, .032f), p.jewel);
        for (float z : new float[] {-h * .37f, .04f, h * .40f}) glider(ship, y + .10f, z, p);
        for (int i = 0; i < 4; i++) {
            float z = -h * .77f + i * .17f;
            rod(ship, "Flight-deck transverse engraving", v(-.17f, y + .078f, z),
                    v(.17f, y + .078f, z), .006f, p.gold);
        }
        medallion(ship, v(0, y + .09f, h * .72f), .16f, p);
        prow(ship, h, .26f, y, p);
        ornament(ship, w, h, y, 3, p);
    }

    private void battleship(Node ship, float h, Paint p) {
        float w = .39f, y = .34f;
        float[][] outline = {{-w * .64f, -h}, {w * .64f, -h}, {w, -h * .65f},
                {w, h * .29f}, {w * .82f, h * .65f}, {w * .38f, h * .88f}, {0, h},
                {-w * .38f, h * .88f}, {-w * .82f, h * .65f}, {-w, h * .29f}, {-w, -h * .65f}};
        hull(ship, outline, y, p);
        cabin(ship, 0, -h * .53f, y, .21f, .17f, .24f, p);
        cabin(ship, 0, -h * .53f, y + .17f, .14f, .13f, .18f, p);
        turret(ship, -h * .78f, y + .025f, .155f, 2, p);
        turret(ship, h * .54f, y + .025f, .17f, 3, p);
        squareMast(ship, -.28f, y, 1.55f, .72f, p);
        squareMast(ship, .59f, y, 1.34f, .64f, p);
        for (int side : new int[] {-1, 1}) {
            for (int i = 0; i < 4; i++) {
                float z = -.79f + i * .43f;
                box(ship, "Broadside armored port", v(.015f, .060f, .075f), v(side * .37f, .22f, z), p.deck);
                rod(ship, "Broadside brass cannon", v(side * .31f, .25f, z), v(side * .415f, .25f, z), .024f, p.gold);
            }
        }
        medallion(ship, v(0, y + .035f, h * .82f), .095f, p);
        prow(ship, h, .25f, y, p);
        ornament(ship, w, h, y, 3, p);
    }

    private void cruiser(Node ship, float h, Paint p) {
        float w = .31f, y = .27f;
        float[][] outline = {{-w * .48f, -h}, {w * .48f, -h}, {w * .89f, -h * .72f},
                {w, -h * .23f}, {w * .90f, h * .28f}, {w * .57f, h * .71f}, {0, h},
                {-w * .57f, h * .71f}, {-w * .90f, h * .28f}, {-w, -h * .23f}, {-w * .89f, -h * .72f}};
        hull(ship, outline, y, p);
        cabin(ship, 0, -h * .68f, y, .16f, .22f, .22f, p);
        lateenMast(ship, -.31f, y, 1.40f, .65f, p);
        lateenMast(ship, .47f, y, 1.13f, .56f, p);
        turret(ship, h * .67f, y, .105f, 1, p);
        for (int side : new int[] {-1, 1}) {
            rod(ship, "Swept stern fin", v(side * .20f, y + .02f, -h * .67f),
                    v(side * .31f, y + .32f, -h * .89f), .012f, p.gold);
            gem(ship, v(side * .31f, y + .32f, -h * .89f), v(.025f, .045f, .025f), p.jewel);
        }
        prow(ship, h, .17f, y, p);
        ornament(ship, w, h, y, 2, p);
    }

    private void submarine(Node ship, float h, Paint p) {
        float w = .245f;
        float[][] outline = {{0, -h}, {w * .55f, -h * .86f}, {w * .91f, -h * .56f},
                {w, 0}, {w * .89f, h * .58f}, {w * .42f, h * .89f}, {0, h},
                {-w * .42f, h * .89f}, {-w * .89f, h * .58f}, {-w, 0},
                {-w * .91f, -h * .56f}, {-w * .55f, -h * .86f}};
        prism(ship, "Sealed spindle hull", outline, new float[] {0, .07f, .18f, .29f, .33f},
                new float[] {.12f, .62f, 1, .80f, .14f}, p.hull);
        prism(ship, "Submarine equatorial gilding", outline, new float[] {.17f, .193f},
                new float[] {1.01f, 1.01f}, p.gold);
        box(ship, "Long dorsal enamel", v(.070f, .008f, h * .59f), v(0, .33f, 0), p.enamel);
        cabin(ship, 0, -.24f, .31f, .105f, .15f, .23f, p);
        prism(ship, "Streamlined conning crown", clippedSquare(.12f, .05f),
                new float[] {.47f, .50f, .55f}, new float[] {1, .9f, .3f}, p.gold)
                .setLocalTranslation(0, 0, -.24f);
        rod(ship, "Periscope", v(0, .53f, -.29f), v(0, .68f, -.29f), .012f, p.gold);
        rod(ship, "Periscope head", v(0, .68f, -.29f), v(0, .68f, -.20f), .020f, p.deck);
        for (int side : new int[] {-1, 1}) {
            for (float z : new float[] {-h * .75f, h * .28f}) {
                float[][] fin = side == 1
                        ? new float[][] {{0, -.16f}, {.13f, -.13f}, {.14f, .04f}, {0, .13f}}
                        : new float[][] {{0, .13f}, {-.14f, .04f}, {-.13f, -.13f}, {0, -.16f}};
                Geometry wing = prism(ship, "Swept hydroplane", fin,
                        new float[] {.12f, .145f}, new float[] {1, 1}, p.gold);
                wing.setLocalTranslation(side * .21f, 0, z);
            }
            for (int i = 0; i < 5; i++) {
                float z = -.74f + i * .30f;
                porthole(ship, side * .233f, .205f, z, p);
            }
            rod(ship, "Dorsal gold rib", v(side * .085f, .31f, -h * .62f),
                    v(side * .085f, .31f, h * .55f), .008f, p.gold);
        }
        gem(ship, v(0, .26f, h * .70f), v(.13f, .11f, .22f), p.jewel);
        ring(ship, "Crystal nose bezel", v(0, .24f, h * .80f), .013f, .13f, p.gold);
        ring(ship, "Aft propeller crown", v(0, .14f, -h * .88f), .010f, .105f, p.gold);
        for (int i = 0; i < 5; i++) {
            float a = i * FastMath.TWO_PI / 5;
            rod(ship, "Propeller petal", v(0, .14f, -h * .88f),
                    v(FastMath.cos(a) * .09f, .14f + FastMath.sin(a) * .09f, -h * .88f), .014f, p.gold);
        }
    }

    private void destroyer(Node ship, float h, Paint p) {
        float w = .25f, y = .23f;
        float[][] outline = {{-w * .60f, -h}, {w * .60f, -h}, {w, -h * .49f},
                {w * .84f, h * .17f}, {w * .38f, h * .65f}, {0, h},
                {-w * .38f, h * .65f}, {-w * .84f, h * .17f}, {-w, -h * .49f}};
        hull(ship, outline, y, p);
        cabin(ship, 0, -h * .62f, y, .13f, .13f, .17f, p);
        rod(ship, "Single escort mast", v(0, y, -.15f), v(0, 1.08f, -.15f), .015f, p.gold);
        squareSail(ship, -.15f, .47f, .50f, .44f, p);
        gem(ship, v(0, 1.10f, -.15f), v(.025f, .06f, .025f), p.jewel);
        turret(ship, h * .43f, y, .09f, 1, p);
        for (int side : new int[] {-1, 1}) {
            rod(ship, "Escort torpedo cradle", v(side * .16f, y + .06f, -.12f),
                    v(side * .16f, y + .06f, .25f), .030f, p.enamel);
            rod(ship, "Torpedo gilded collar", v(side * .16f, y + .06f, .12f),
                    v(side * .16f, y + .06f, .16f), .033f, p.gold);
        }
        prow(ship, h, .12f, y, p);
        ornament(ship, w, h, y, 1, p);
    }

    private void hull(Node ship, float[][] outline, float y, Paint p) {
        prism(ship, "Beveled sculpted keel", outline, new float[] {0, y * .24f, y * .78f, y},
                new float[] {.25f, .65f, .94f, 1}, p.hull);
        prism(ship, "Fine gilded gunwale", outline, new float[] {y, y + .025f}, new float[] {1, .98f}, p.gold);
        prism(ship, "Inset enamel deck", outline, new float[] {y + .025f, y + .032f}, new float[] {.87f, .87f}, p.deck);
        for (int i = 0; i < outline.length; i++) {
            float[] a = outline[i], b = outline[(i + 1) % outline.length];
            rod(ship, "Slender balustrade", v(a[0], y + .10f, a[1]), v(b[0], y + .10f, b[1]), .006f, p.gold);
            rod(ship, "Balustrade post", v(a[0], y, a[1]), v(a[0], y + .10f, a[1]), .007f, p.gold);
        }
    }

    private void ornament(Node ship, float w, float h, float y, int count, Paint p) {
        for (int side : new int[] {-1, 1}) {
            for (int i = 0; i < count; i++) {
                float z = -h * .52f + (i + .5f) * h * .95f / count;
                porthole(ship, side * w * .94f, y * .67f, z, p);
                float span = h * .78f / count;
                Vector3f previous = v(side * w * .88f, y * .44f, z - span * .5f);
                for (int k = 1; k <= 6; k++) {
                    float u = k / 6f;
                    Vector3f next = v(side * w * .94f, y * (.44f + .40f * FastMath.sin(u * FastMath.PI)),
                            z + (u - .5f) * span);
                    rod(ship, "Swept hull filigree", previous, next, .008f, p.gold);
                    previous = next;
                }
            }
        }
    }

    private void cabin(Node ship, float x, float z, float y, float w, float height, float depth, Paint p) {
        box(ship, "Ivory armored cabin", v(w, height * .5f, depth), v(x, y + height * .5f, z), p.hull);
        box(ship, "Cabin gilded eave", v(w + .022f, .018f, depth + .025f), v(x, y + height, z), p.gold);
        box(ship, "Recessed cabin roof", v(w * .80f, .009f, depth * .80f), v(x, y + height + .024f, z), p.enamel);
        for (int side : new int[] {-1, 1}) {
            for (int i = -1; i <= 1; i++) {
                box(ship, "Gallery window", v(.006f, height * .18f, depth * .15f),
                        v(x + side * (w + .005f), y + height * .56f, z + i * depth * .54f), p.enamel);
            }
        }
    }

    private void turret(Node ship, float z, float y, float radius, int barrels, Paint p) {
        prism(ship, "Turret gilded plinth", clippedSquare(radius, radius * .3f),
                new float[] {y, y + .025f}, new float[] {1, 1}, p.gold).setLocalTranslation(0, 0, z);
        prism(ship, "Faceted gun turret", clippedSquare(radius * .90f, radius * .24f),
                new float[] {y + .025f, y + .13f, y + .17f}, new float[] {1, 1, .65f}, p.hull)
                .setLocalTranslation(0, 0, z);
        for (int i = 0; i < barrels; i++) {
            float x = (i - (barrels - 1) * .5f) * .052f;
            rod(ship, "Enamel cannon barrel", v(x, y + .10f, z + .06f),
                    v(x, y + .14f, z + radius + .23f), .015f, p.deck);
            rod(ship, "Gilded cannon muzzle", v(x, y + .136f, z + radius + .19f),
                    v(x, y + .14f, z + radius + .23f), .019f, p.gold);
        }
    }

    private void squareMast(Node ship, float z, float deck, float height, float width, Paint p) {
        rod(ship, "Royal square-rig mast", v(0, deck, z), v(0, height, z), .017f, p.gold);
        squareSail(ship, z, deck + .18f, width, .45f, p);
        squareSail(ship, z, height - .43f, width * .71f, .30f, p);
        gem(ship, v(0, height + .035f, z), v(.027f, .07f, .027f), p.jewel);
        for (int side : new int[] {-1, 1}) {
            rod(ship, "Fine shroud rigging", v(side * width * .39f, deck + .02f, z - .21f),
                    v(0, height - .10f, z), .004f, p.gold);
        }
    }

    private void squareSail(Node ship, float z, float bottom, float width, float height, Paint p) {
        Geometry sail = new Geometry("Billowed embroidered square sail", curvedSail(width, height));
        sail.setMaterial(p.cloth);
        sail.setLocalTranslation(0, bottom, z);
        ship.attachChild(sail);
        rod(ship, "Square sail foot", v(-width * .5f, bottom, z), v(width * .5f, bottom, z), .008f, p.gold);
        rod(ship, "Square sail yard", v(-width * .39f, bottom + height, z),
                v(width * .39f, bottom + height, z), .010f, p.gold);
        for (int side : new int[] {-1, 1}) {
            rod(ship, "Gold sail edge", v(side * width * .5f, bottom, z),
                    v(side * width * .34f, bottom + height, z), .005f, p.gold);
        }
        ring(ship, "Sail celestial embroidery", v(0, bottom + height * .50f, z + .267f), .005f, height * .18f, p.gold);
        gem(ship, v(0, bottom + height * .50f, z + .274f), v(.017f, .027f, .012f), p.jewel);
    }

    private void lateenMast(Node ship, float z, float deck, float height, float width, Paint p) {
        rod(ship, "Cruiser tapered mast", v(0, deck, z), v(0, height, z), .014f, p.gold);
        float bottom = deck + .14f;
        Vector3f a = v(-width * .48f, bottom, z);
        Vector3f b = v(width * .50f, bottom + .16f, z);
        Vector3f c = v(-width * .18f, height - .07f, z);
        Vector3f center = a.add(b).addLocal(c).divideLocal(3).addLocal(0, 0, .13f);
        Mesh mesh = new Mesh();
        Vector3f[] points = {a, b, center, b, c, center, c, a, center};
        float[] positions = new float[27], normals = new float[27];
        for (int i = 0; i < points.length; i++) {
            positions[i * 3] = points[i].x;
            positions[i * 3 + 1] = points[i].y;
            positions[i * 3 + 2] = points[i].z;
            normals[i * 3 + 2] = 1;
        }
        mesh.setBuffer(VertexBuffer.Type.Position, 3, positions);
        mesh.setBuffer(VertexBuffer.Type.Normal, 3, normals);
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, new float[18]);
        mesh.updateBound();
        Geometry sail = new Geometry("Cruiser swept triangular sail", mesh);
        sail.setMaterial(p.cloth);
        ship.attachChild(sail);
        rod(ship, "Lateen diagonal yard", b, c, .011f, p.gold);
        rod(ship, "Lateen lower hem", a, b, .006f, p.gold);
        rod(ship, "Lateen trailing hem", a, c, .006f, p.gold);
        gem(ship, center.add(0, 0, .012f), v(.025f, .06f, .012f), p.jewel);
        gem(ship, v(0, height + .025f, z), v(.024f, .055f, .024f), p.jewel);
    }

    private void glider(Node ship, float y, float z, Paint p) {
        float[][] outline = {{0, -.24f}, {.045f, -.12f}, {.055f, .12f}, {0, .29f},
                {-.055f, .12f}, {-.045f, -.12f}};
        prism(ship, "Deck celestial glider", outline, new float[] {y, y + .05f, y + .09f},
                new float[] {.65f, 1, .25f}, p.hull).setLocalTranslation(0, 0, z);
        float[][] wings = {{-.19f, -.09f}, {-.06f, -.05f}, {0, .10f}, {.06f, -.05f},
                {.19f, -.09f}, {.14f, .015f}, {0, .12f}, {-.14f, .015f}};
        prism(ship, "Gilded swept glider wings", wings, new float[] {y + .035f, y + .053f},
                new float[] {1, 1}, p.gold).setLocalTranslation(0, 0, z);
        gem(ship, v(0, y + .075f, z + .09f), v(.024f, .025f, .046f), p.enamel);
    }

    private void prow(Node ship, float h, float width, float deck, Paint p) {
        for (int side : new int[] {-1, 1}) {
            rod(ship, "Raised spearhead scroll", v(side * width, deck + .015f, h * .66f),
                    v(0, deck + .25f, h * .95f), .013f, p.gold);
            rod(ship, "Prow crown return", v(0, deck + .25f, h * .95f),
                    v(side * width * .48f, deck + .06f, h * .83f), .008f, p.gold);
        }
        gem(ship, v(0, deck + .13f, h * .85f), v(.045f, .075f, .055f), p.jewel);
    }

    private void medallion(Node ship, Vector3f center, float radius, Paint p) {
        Geometry ring = ring(ship, "Deck compass rim", center, .007f, radius, p.gold);
        ring.rotate(FastMath.HALF_PI, 0, 0);
        for (int i = 0; i < 8; i++) {
            float angle = i * FastMath.QUARTER_PI;
            rod(ship, "Compass star engraving", center,
                    center.add(FastMath.cos(angle) * radius * .89f, 0, FastMath.sin(angle) * radius * .89f), .004f, p.gold);
        }
        gem(ship, center.add(0, .02f, 0), v(.027f, .05f, .027f), p.jewel);
    }

    private void porthole(Node ship, float x, float y, float z, Paint p) {
        Geometry rim = ring(ship, "Gilded porthole bezel", v(x, y, z), .006f, .026f, p.gold);
        rim.rotate(0, FastMath.HALF_PI, 0);
        box(ship, "Inset azure porthole", v(.005f, .019f, .019f), v(x, y, z), p.enamel);
    }

    private static Geometry ring(Node parent, String name, Vector3f position, float tube, float radius, Material material) {
        Geometry geometry = new Geometry(name, new Torus(6, 16, tube, radius));
        geometry.setMaterial(material);
        geometry.setLocalTranslation(position);
        parent.attachChild(geometry);
        return geometry;
    }

    private static Geometry prism(Node parent, String name, float[][] outline, float[] heights, float[] scales, Material material) {
        Geometry geometry = new Geometry(name, beveledPrism(outline, heights, scales));
        geometry.setMaterial(material);
        parent.attachChild(geometry);
        return geometry;
    }

    private static Vector3f v(float x, float y, float z) { return new Vector3f(x, y, z); }

    private record Paint(Material hull, Material gold, Material deck, Material enamel, Material jewel, Material cloth) {}
}
