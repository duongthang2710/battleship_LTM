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
        float centerRow = (float) shape.stream().mapToInt(Coordinate::row).average().orElse(0);
        float centerColumn = (float) shape.stream().mapToInt(Coordinate::column).average().orElse(0);
        Coordinate core = shape.stream().min(Comparator.<Coordinate>comparingInt(c -> -degree(c, footprint))
                .thenComparingDouble(c -> Math.pow(c.row()-centerRow,2)+Math.pow(c.column()-centerColumn,2))).orElseThrow();
        for (Coordinate cell : shape) {
            float x = cell.row(), z = -cell.column();
            float[][] outline = outline(cell, footprint);
            prism(built, "Rounded connected naval hull", outline, new float[] {.015f,.105f,.28f,.32f}, new float[] {.62f,.90f,1,1}, hull,x,z);
            prism(built, "Continuous gilded gunwale", outline, new float[] {.285f,.315f}, new float[] {1.015f,1.015f}, gold,x,z);
            prism(built, "Recessed azure deck", outline, new float[] {.321f,.335f}, new float[] {.91f,.91f}, deck,x,z);
            for (int i=0;i<outline.length;i++) {
                float[] a=outline[i], b=outline[(i+1)%outline.length];
                if (connectionEdge(a,b,cell,footprint)) continue;
                Vector3f from=v(x+a[0]*.94f,.39f,z+a[1]*.94f), to=v(x+b[0]*.94f,.39f,z+b[1]*.94f);
                rod(built,"Curved brass guardrail",from,to,.009f,gold);
                if (i%4==0) rod(built,"Guardrail support",from.add(0,-.065f,0),from,.007f,gold);
            }
            Node equipment=new Node("Naval deck module"); equipment.setLocalTranslation(x,0,z);
            equipment.rotate(0,heading(cell,footprint),0); built.attachChild(equipment);
            for (int side:new int[]{-1,1}) {
                box(equipment,"Inset side armor",v(.013f,.045f,.13f),v(side*.265f,.205f,0),gold);
                for(int port=-1;port<=1;port++) gem(equipment,v(side*.28f,.205f,port*.09f),v(.014f,.020f,.020f),glass);
            }
            if (cell.equals(core)) continue;
            switch(type) {
                case CARRIER -> {
                    box(equipment,"Carrier flight deck",v(.18f,.022f,.30f),v(0,.365f,0),deck);
                    for(int side:new int[]{-1,1}) box(equipment,"Gilded landing guide",v(.009f,.004f,.28f),v(side*.14f,.39f,0),gold);
                    box(equipment,"Aircraft wings",v(.16f,.018f,.038f),v(0,.43f,-.03f),hull);
                    prism(equipment,"Aircraft fuselage",new float[][]{{0,-.23f},{.035f,-.13f},{.04f,.13f},{0,.26f},{-.04f,.13f},{-.035f,-.13f}},new float[]{.40f,.48f},new float[]{1,.50f},hull,0,0);
                    box(equipment,"Aircraft tailplane",v(.085f,.012f,.023f),v(0,.445f,-.17f),gold);
                }
                case BATTLESHIP -> turret(equipment,0,0,.17f,3,hull,gold);
                case CRUISER -> turret(equipment,0,0,.145f,2,hull,gold);
                case DESTROYER -> turret(equipment,0,0,.12f,1,hull,gold);
                case SUBMARINE -> {
                    Geometry pressure=new Geometry("Rounded pressure hull",new Sphere(8,12,1)); pressure.setMaterial(hull);
                    pressure.setLocalScale(.205f,.14f,.31f); pressure.setLocalTranslation(0,.405f,0); equipment.attachChild(pressure);
                    for(float station:new float[]{-.17f,.17f}) rod(equipment,"Pressure hull brass rib",v(-.18f,.445f,station),v(.18f,.445f,station),.012f,gold);
                    box(equipment,"Submarine hatch",v(.08f,.012f,.09f),v(0,.55f,0),gold);
                }
            }
        }
        Node bridge=new Node("Fleet command section"); bridge.setLocalTranslation(core.row(),0,-core.column());
        bridge.rotate(0,heading(core,footprint),0); built.attachChild(bridge);
        prism(bridge,"Bridge gold plinth",clippedSquare(.205f,.06f),new float[]{.335f,.385f},new float[]{1,1},gold,0,0);
        prism(bridge,"Sculpted command tower",clippedSquare(.175f,.055f),new float[]{.385f,.59f,.66f},new float[]{1,.86f,.70f},hull,0,0);
        for(int side:new int[]{-1,1}) box(bridge,"Bridge panoramic windows",v(.012f,.035f,.105f),v(side*.15f,.56f,0),glass);
        box(bridge,"Forward bridge glass",v(.11f,.035f,.01f),v(0,.56f,.145f),glass);
        if(type==ShipType.SUBMARINE) {
            rod(bridge,"Periscope",v(0,.66f,0),v(0,.96f,0),.018f,gold);
            rod(bridge,"Periscope lens",v(0,.96f,0),v(0,.96f,.10f),.018f,glass);
        } else {
            float height=type==ShipType.BATTLESHIP?1.16f:1.04f;
            rod(bridge,"Celestial signal mast",v(0,.63f,-.08f),v(0,height,-.08f),.015f,gold);
            rod(bridge,"Signal crossyard",v(-.22f,height-.14f,-.08f),v(.22f,height-.14f,-.08f),.011f,gold);
            Geometry pennant=new Geometry("Fleet silk pennant",curvedSail(.32f,.27f)); pennant.setMaterial(hull);
            pennant.setLocalTranslation(0,.70f,-.075f); bridge.attachChild(pennant);
            gem(bridge,v(0,height+.035f,-.08f),v(.036f,.075f,.036f),glow);
            for(int side:new int[]{-1,1}) box(bridge,"Ventilated engine casing",v(.05f,.045f,.065f),v(side*.10f,.38f,-.205f),gold);
        }
        gem(bridge,v(0,.69f,.10f),v(.04f,.06f,.04f),glow);
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
    private static final int[][] DIRECTIONS={{-1,0},{1,0},{0,-1},{0,1}};
    private static float heading(Coordinate cell,Set<Coordinate> shape) {
        for(int[] d:DIRECTIONS) if(shape.contains(cell.offset(d[0],d[1]))) {
            float sign=degree(cell,shape)==1?-1:1;
            return FastMath.atan2(sign*d[0],-sign*d[1]);
        }
        return 0;
    }
    private static float[][] outline(Coordinate cell,Set<Coordinate> shape) {
        if(degree(cell,shape)==1) {
            float[][] bow={{-.28f,-.5f},{.28f,-.5f},{.29f,.02f},{.22f,.25f},{.10f,.42f},{0,.49f},{-.10f,.42f},{-.22f,.25f},{-.29f,.02f}};
            float yaw=heading(cell,shape), sin=FastMath.sin(yaw), cos=FastMath.cos(yaw);
            return Arrays.stream(bow).map(p -> new float[]{p[0]*cos+p[1]*sin,-p[0]*sin+p[1]*cos}).toArray(float[][]::new);
        }
        // Radial union of a rounded junction and narrow arms: bends/T/crosses keep water between branches.
        var angles=new TreeSet<Double>();
        for(int i=0;i<24;i++) angles.add(i*Math.PI*2/24);
        double corner=Math.atan2(.28,.5);
        for(int[] d:DIRECTIONS) if(shape.contains(cell.offset(d[0],d[1]))) {
            double a=Math.atan2(-d[1],d[0]);
            angles.add((a-corner+Math.PI*2)%(Math.PI*2)); angles.add((a+corner+Math.PI*2)%(Math.PI*2));
        }
        var points=new ArrayList<float[]>();
        for(double angle:angles) {
            double x=Math.cos(angle),z=Math.sin(angle),radius=.30;
            for(int[] d:DIRECTIONS) if(shape.contains(cell.offset(d[0],d[1]))) {
                double along=x*d[0]-z*d[1], across=Math.abs(x*d[1]+z*d[0]);
                if(along>1e-6) radius=Math.max(radius,Math.min(.5/along,across<1e-6?100:.28/across));
            }
            points.add(new float[]{(float)(x*radius),(float)(z*radius)});
        }
        return points.toArray(float[][]::new);
    }
    private static boolean connectionEdge(float[] a,float[] b,Coordinate cell,Set<Coordinate> shape) {
        for(int[] d:DIRECTIONS) if(shape.contains(cell.offset(d[0],d[1]))
                && Math.abs(a[0]*d[0]-a[1]*d[1]-.5f)<.001f && Math.abs(b[0]*d[0]-b[1]*d[1]-.5f)<.001f) return true;
        return false;
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
