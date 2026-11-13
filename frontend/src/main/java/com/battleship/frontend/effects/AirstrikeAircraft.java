package com.battleship.frontend.effects;

import com.battleship.frontend.graphics.Materials;
import com.battleship.shared.game.Side;
import com.jme3.material.Material;
import com.jme3.math.Vector3f;
import com.jme3.math.FastMath;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.*;
import com.jme3.scene.shape.Sphere;
import static com.battleship.frontend.graphics.ProceduralMeshes.*;

final class AirstrikeAircraft {
    private AirstrikeAircraft() { }

    static Node create(Materials materials,Side side) {
        boolean radiant=side==Side.WHITE;
        Material hull=materials.metal(Materials.linear(radiant?0xE6E1CE:0x303348),115);
        Material trim=materials.metal(Materials.linear(radiant?0xD5AD62:0xAD97BE),135);
        Material deck=materials.metal(Materials.linear(radiant?0x213C52:0x241F36),85);
        Material canopy=materials.metal(Materials.linear(radiant?0x407E9B:0x745A9B),150);
        Material beacon=materials.glow(Materials.linear(radiant?0xB4E1EF:0xC4A8E1),.7f);
        BatchNode aircraft=new BatchNode("Ornate twin-engine bomber");
        Geometry fuselage=new Geometry("Rounded bomber fuselage",new Sphere(12,20,.18f));
        fuselage.setMaterial(hull); fuselage.setLocalScale(1,.82f,6.9f);
        fuselage.setLocalTranslation(0,.02f,.08f); aircraft.attachChild(fuselage);
        for(int sign:new int[]{-1,1}) {
            float[][] wing={{sign*.13f,-.42f},{sign*1.26f,-.78f},{sign*1.13f,-.10f},{sign*.15f,.42f}};
            if(sign<0) wing=new float[][]{wing[3],wing[2],wing[1],wing[0]};
            prism(aircraft,"Swept enamel gull wing",wing,new float[]{-.04f,.015f,.05f},new float[]{.98f,1,.97f},deck);
            float[][] tip={{sign*1.05f,-.71f},{sign*1.26f,-.78f},{sign*1.13f,-.10f},{sign*1.02f,-.05f}};
            if(sign<0) tip=new float[][]{tip[3],tip[2],tip[1],tip[0]};
            // Keep the cap fan inside its own off-center panel.
            float tipX=sign*1.12f,tipZ=-.39f;
            for(float[] point:tip) { point[0]-=tipX; point[1]-=tipZ; }
            Geometry panel=new Geometry("Ivory wingtip panel",beveledPrism(tip,new float[]{.052f,.062f},new float[]{1,1}));
            panel.setMaterial(hull); panel.setLocalTranslation(tipX,0,tipZ); aircraft.attachChild(panel);
            rod(aircraft,"Wing gilded leading edge",new Vector3f(sign*.17f,.05f,.42f),new Vector3f(sign*1.13f,.05f,-.10f),.018f,trim);
            rod(aircraft,"Wing engraved trailing edge",new Vector3f(sign*.17f,.035f,-.42f),new Vector3f(sign*1.26f,.035f,-.78f),.009f,deck);
            for(float station:new float[]{.4f,.7f,1f}) rod(aircraft,"Gold wing rib",
                    new Vector3f(sign*station,.056f,.52f-.55f*station),
                    new Vector3f(sign*station,.056f,-.38f-.30f*station),.006f,trim);
            Geometry nacelle=new Geometry("Twin engine nacelle",new Sphere(10,14,.14f));
            nacelle.setMaterial(deck); nacelle.setLocalScale(1,.9f,2.7f);
            nacelle.setLocalTranslation(sign*.62f,0,.07f); aircraft.attachChild(nacelle);
            rod(aircraft,"Engine brass collar",new Vector3f(sign*.62f,0,.32f),new Vector3f(sign*.62f,0,.38f),.145f,trim);
            for(int blade=0;blade<3;blade++) {
                float angle=blade*com.jme3.math.FastMath.TWO_PI/3;
                rod(aircraft,"Propeller blade",new Vector3f(sign*.62f,0,.42f),
                        new Vector3f(sign*.62f+com.jme3.math.FastMath.cos(angle)*.27f,
                                com.jme3.math.FastMath.sin(angle)*.27f,.42f),.018f,trim);
            }
            box(aircraft,"Bomber tailplane",new Vector3f(.28f,.026f,.14f),new Vector3f(sign*.22f,.10f,-.94f),hull);
            float[][] fin={{-.15f,-.19f},{.17f,-.13f},{.12f,.06f},{-.15f,.19f}};
            Geometry stabilizer=new Geometry("Swept vertical stabilizer",beveledPrism(fin,new float[]{-.024f,.024f},new float[]{1,1}));
            stabilizer.setMaterial(deck); stabilizer.rotate(0,0,FastMath.HALF_PI);
            stabilizer.setLocalTranslation(sign*.28f,.25f,-.99f); aircraft.attachChild(stabilizer);
            rod(aircraft,"Tail fin gilding",new Vector3f(sign*.28f,.42f,-1.12f),new Vector3f(sign*.28f,.37f,-.93f),.014f,trim);
            box(aircraft,"Bomb bay door",new Vector3f(.075f,.014f,.28f),new Vector3f(sign*.095f,-.155f,.04f),deck);
            gem(aircraft,new Vector3f(sign*1.13f,.075f,-.11f),new Vector3f(.035f,.025f,.055f),beacon);
        }
        Geometry cockpit=new Geometry("Faceted blue cockpit",new Sphere(8,12,.15f));
        cockpit.setMaterial(canopy); cockpit.setLocalScale(.82f,.7f,2.2f);
        cockpit.setLocalTranslation(0,.16f,.52f); aircraft.attachChild(cockpit);
        rod(aircraft,"Cockpit gold spine",new Vector3f(0,.25f,.28f),new Vector3f(0,.25f,.75f),.012f,trim);
        gem(aircraft,new Vector3f(0,.16f,-.36f),new Vector3f(.065f,.035f,.12f),trim);
        aircraft.batch(); aircraft.updateGeometricState();
        Node baked=new Node("Ornate twin-engine bomber");
        for(Spatial child:aircraft.getChildren()) if(child instanceof Geometry batch && batch.isIgnoreTransform()) {
            Geometry geometry=new Geometry("Bomber plated airframe",batch.getMesh());
            geometry.setMaterial(batch.getMaterial()); baked.attachChild(geometry);
        }
        baked.setShadowMode(RenderQueue.ShadowMode.Off);
        return baked;
    }

    private static void prism(Node parent,String name,float[][] outline,float[] heights,float[] scales,Material material) {
        Geometry geometry=new Geometry(name,beveledPrism(outline,heights,scales));
        geometry.setMaterial(material); parent.attachChild(geometry);
    }
}
