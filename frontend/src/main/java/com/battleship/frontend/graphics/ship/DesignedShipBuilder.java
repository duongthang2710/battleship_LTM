package com.battleship.frontend.graphics.ship;

import com.battleship.frontend.graphics.Materials;
import com.battleship.shared.game.*;
import com.jme3.material.Material;
import com.jme3.math.*;
import com.jme3.scene.*;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.shape.*;
import java.util.*;
import static com.battleship.frontend.graphics.ProceduralMeshes.*;

/** One contiguous hull/deck assembled from the actual footprint; every catalog shape uses this path. */
public final class DesignedShipBuilder {
    private final Materials materials;
    private final Map<String, Node> templates = new HashMap<>();
    public DesignedShipBuilder(Materials materials) { this.materials = materials; }
    public Node create(ShipType type, List<Coordinate> shape, ShipFactory.Fleet fleet) {
        var normalized = ShipShapes.normalize(shape);
        String key = fleet + ":" + type + ":" + normalized;
        return templates.computeIfAbsent(key, ignored -> build(type, normalized, fleet)).clone(false);
    }
    private Node build(ShipType type, List<Coordinate> shape, ShipFactory.Fleet fleet) {
        boolean white = fleet == ShipFactory.Fleet.RADIANT;
        Material hull = materials.metal(Materials.linear(white ? 0xEEE7D6 : 0x171D2B), 105);
        Material gold = materials.metal(Materials.linear(white ? 0xE9BD68 : 0xB694CA), 135);
        Material deck = materials.metal(Materials.linear(white ? 0x163B55 : 0x2D2647), 80);
        Material glass = materials.metal(Materials.linear(white ? 0x5EAFC4 : 0x8B69AC), 95);
        Material glow = materials.glow(Materials.linear(white ? 0x86D9E7 : 0xBA8AE6), .7f);
        BatchNode built = new BatchNode(fleet + " designed " + type);
        Set<Coordinate> footprint = Set.copyOf(shape);
        Coordinate core = shape.stream().max(Comparator.comparingInt(c -> degree(c, footprint))).orElseThrow();
        for (Coordinate cell : shape) {
            float x = cell.row(), z = -cell.column();
            float[][] outline = outline(cell, footprint);
            prism(built, "Connected armored hull", outline, new float[] {.01f, .10f, .28f, .34f}, new float[] {.78f, .94f, 1, 1}, hull, x, z);
            prism(built, "Hull gilded waterline", outline, new float[] {.25f, .275f}, new float[] {1.005f, 1.005f}, gold, x, z);
            prism(built, "Inlaid naval deck", outline, new float[] {.341f, .365f}, new float[] {1, 1}, deck, x, z);
            for (int[] d : new int[][] {{-1,0},{1,0},{0,-1},{0,1}}) if (!footprint.contains(cell.offset(d[0], d[1]))) {
                Vector3f middle = v(x + d[0] * .43f, .395f, z - d[1] * .43f);
                Vector3f tangent = v(d[1] * .31f, 0, d[0] * .31f);
                rod(built, "Gilded perimeter rail", middle.subtract(tangent), middle.add(tangent), .012f, gold);
                for (float t : new float[] {-.75f, 0, .75f}) {
                    Vector3f p = middle.add(tangent.mult(t)); rod(built, "Rail stanchion", p.add(0, -.09f, 0), p, .009f, gold);
                    Vector3f port = v(x + d[0] * .443f, .205f, z - d[1] * .443f).add(tangent.mult(t));
                    gem(built, port, v(.027f, .032f, .027f), glass);
                }
            }
            if (!cell.equals(core)) {
                switch (type) {
                    case CARRIER -> {
                        box(built, "Landing runway", v(.09f, .008f, .27f), v(x, .377f, z), gold);
                        box(built, "Folded wing aircraft", v(.22f, .025f, .045f), v(x, .42f, z), hull);
                        prism(built, "Aircraft fuselage", new float[][] {{0,-.20f},{.055f,-.08f},{.035f,.20f},{0,.28f},{-.035f,.20f},{-.055f,-.08f}}, new float[] {.40f,.48f}, new float[] {1,.50f}, hull, x, z);
                    }
                    case BATTLESHIP -> turret(built, x, z, .18f, 3, hull, gold);
                    case CRUISER -> turret(built, x, z, .14f, 2, hull, gold);
                    case SUBMARINE -> {
                        prism(built, "Sealed pressure module", clippedSquare(.25f,.12f), new float[] {.365f,.43f,.57f,.61f}, new float[] {.8f,1,.75f,.3f}, hull, x, z);
                        rod(built, "Pressure module rib", v(x-.19f,.48f,z), v(x+.19f,.48f,z), .018f, gold);
                    }
                    case DESTROYER -> turret(built, x, z, .125f, 1, hull, gold);
                }
            }
        }
        float x = core.row(), z = -core.column();
        prism(built, "Gilded command plinth", clippedSquare(.29f,.09f), new float[] {.365f,.42f}, new float[] {1,1}, gold, x, z);
        prism(built, type == ShipType.SUBMARINE ? "Submarine command tower" : "Armored bridge", clippedSquare(.24f,.07f),
                new float[] {.42f,.67f,.73f}, new float[] {1,.84f,.60f}, hull, x, z);
        for (int side : new int[] {-1,1}) box(built, "Bridge azure windows", v(.012f,.055f,.14f), v(x+side*.21f,.60f,z), glass);
        if (type == ShipType.SUBMARINE) {
            rod(built, "Periscope", v(x,.7f,z), v(x,1.04f,z), .023f, gold);
            rod(built, "Periscope lens", v(x,1.04f,z), v(x,1.04f,z+.12f), .023f, glass);
        } else {
            float mast = type == ShipType.CARRIER ? 1.32f : type == ShipType.BATTLESHIP ? 1.60f : 1.42f;
            rod(built, "Royal mast", v(x,.70f,z), v(x,mast,z), .018f, gold);
            box(built, "Ceremonial crossyard", v(.28f,.013f,.018f), v(x,mast-.22f,z), gold);
            Geometry sail = new Geometry("Fleet pennant", curvedSail(.50f, .50f)); sail.setMaterial(hull);
            sail.setLocalTranslation(x,.76f,z+.025f); built.attachChild(sail);
            gem(built, v(x,mast+.04f,z), v(.045f,.12f,.045f), glow);
        }
        gem(built, v(x,.77f,z-.17f), v(.06f,.09f,.06f), glow);
        built.setShadowMode(ShadowMode.CastAndReceive); built.batch(); built.updateGeometricState();
        Node baked = new Node(built.getName()); baked.setShadowMode(ShadowMode.CastAndReceive);
        for (Spatial child : built.getChildren()) if (child instanceof Geometry batch && batch.isIgnoreTransform()) {
            Geometry geometry = new Geometry(batch.getName(), batch.getMesh()); geometry.setMaterial(batch.getMaterial());
            geometry.getMesh().setStatic(); baked.attachChild(geometry);
        }
        return baked;
    }
    private static int degree(Coordinate cell, Set<Coordinate> cells) {
        int count = 0; for (int[] d : new int[][] {{-1,0},{1,0},{0,-1},{0,1}}) if (cells.contains(cell.offset(d[0],d[1]))) count++; return count;
    }
    private static float[][] outline(Coordinate cell, Set<Coordinate> shape) {
        boolean left = shape.contains(cell.offset(-1,0)), right = shape.contains(cell.offset(1,0));
        boolean back = shape.contains(cell.offset(0,1)), front = shape.contains(cell.offset(0,-1));
        float l = left ? -.5f : -.44f, r = right ? .5f : .44f, b = back ? -.5f : -.44f, f = front ? .5f : .44f;
        var points = new ArrayList<float[]>();
        if (!left && !back) { points.add(new float[]{l+.10f,b}); points.add(new float[]{l,b+.10f}); } else points.add(new float[]{l,b});
        if (!left && !front) { points.add(new float[]{l,f-.10f}); points.add(new float[]{l+.10f,f}); } else points.add(new float[]{l,f});
        if (!right && !front) { points.add(new float[]{r-.10f,f}); points.add(new float[]{r,f-.10f}); } else points.add(new float[]{r,f});
        if (!right && !back) { points.add(new float[]{r,b+.10f}); points.add(new float[]{r-.10f,b}); } else points.add(new float[]{r,b});
        // ProceduralMeshes expects counter-clockwise points in XZ for outward normals.
        Collections.reverse(points);
        return points.toArray(float[][]::new);
    }
    private static void turret(Node n, float x, float z, float radius, int barrels, Material hull, Material gold) {
        prism(n, "Turret turntable", clippedSquare(radius+.025f,.05f), new float[] {.37f,.415f}, new float[] {1,1}, gold,x,z);
        prism(n, "Armored gun house", clippedSquare(radius,.04f), new float[] {.415f,.52f,.57f}, new float[] {1,1,.70f}, hull,x,z);
        for (int i=0;i<barrels;i++) { float bx=x+(i-(barrels-1)/2f)*.075f; rod(n,"Naval cannon",v(bx,.51f,z+.06f),v(bx,.51f,z+.34f),.018f,gold); }
    }
    private static void prism(Node n, String name, float[][] outline, float[] heights, float[] scales, Material material, float x, float z) {
        Geometry g = new Geometry(name, beveledPrism(outline, heights, scales)); g.setMaterial(material); g.setLocalTranslation(x,0,z); n.attachChild(g);
    }
    private static Vector3f v(float x,float y,float z) { return new Vector3f(x,y,z); }
}
