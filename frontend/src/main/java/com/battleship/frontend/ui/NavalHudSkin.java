package com.battleship.frontend.ui;

import com.battleship.frontend.graphics.Materials;
import com.jme3.asset.AssetManager;
import com.jme3.font.*;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.*;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.*;
import com.jme3.scene.shape.Quad;
import com.jme3.util.BufferUtils;
import java.util.*;

/** Screen-space drawing only; no game state or input handling. */
public final class NavalHudSkin {
    public static final int BLUE = 0x104E79, ORANGE = 0xF77936, GOLD = 0xD4BA71, WHITE = 0xF5F4E9;
    private final AssetManager assets;
    private final Materials materials;
    private final BitmapFont font;
    private final Node parent;
    private final float z;
    public NavalHudSkin(AssetManager assets, Node parent, float z) {
        this.assets = assets; this.parent = parent; this.z = z;
        materials = new Materials(assets); font = UiTypography.hud(assets);
    }
    public Geometry rect(float x, float y, float w, float h, int color, float alpha) {
        ColorRGBA tint = Materials.linear(color); tint.a = alpha;
        Geometry g = new Geometry("Naval HUD panel", new Quad(w, h));
        g.setMaterial(materials.flat(tint)); g.setLocalTranslation(x, y, z); return attach(g);
    }
    public void line(float x, float y, float ex, float ey, int color, float thickness) {
        float dx = ex-x, dy=ey-y;
        Geometry g = rect(x, y, FastMath.sqrt(dx*dx+dy*dy), thickness, color, 1);
        g.rotate(0,0,FastMath.atan2(dy,dx)); g.move(0,0,2.2f);
    }
    public void panel(float x, float y, float w, float h, int color) {
        rect(x,y,w,h,color,.91f);
        line(x,y,x+w,y,GOLD,1); line(x,y+h,x+w,y+h,GOLD,1);
        line(x,y,x,y+h,GOLD,1); line(x+w,y,x+w,y+h,GOLD,1);
    }
    public void hazePennant(float x,float bottom,float width,float shoulder,float peak,float alpha) {
        ColorRGBA tint=new ColorRGBA(1,1,1,alpha);
        Mesh mesh=new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position,3,new float[]{x,bottom,0,x+width,bottom,0,
                x+width,shoulder,0,x+width/2,shoulder+peak,0,x,shoulder,0});
        mesh.setBuffer(VertexBuffer.Type.Index,3,new int[]{0,1,2,0,2,3,0,3,4}); mesh.updateBound();
        Geometry g=new Geometry("Fleet translucent backing",mesh); g.setMaterial(materials.flat(tint));
        g.setLocalTranslation(0,0,z); attach(g);
    }
    public void banner(float x, float bottom, float width, float top) {
        float point = Math.min(width*.46f,72);
        float[] vertices={x+width/2,bottom,0, x+width,bottom+point,0, x+width,top,0,
                x,top,0,x,bottom+point,0};
        Mesh mesh=new Mesh(); mesh.setBuffer(VertexBuffer.Type.Position,3,vertices);
        mesh.setBuffer(VertexBuffer.Type.Index,3,new int[]{0,1,2,0,2,3,0,3,4}); mesh.updateBound();
        Geometry g=new Geometry("Captain pennant",mesh); g.setMaterial(materials.flat(Materials.linear(0x073453)));
        g.setLocalTranslation(0,0,z); attach(g);
        rect(x+3,bottom+point,width-6,top-bottom-point,BLUE,.78f);
        line(x,bottom+point,x,top,GOLD,1.3f); line(x+width,bottom+point,x+width,top,GOLD,1.3f);
        line(x,bottom+point,x+width/2,bottom,GOLD,1.3f); line(x+width/2,bottom,x+width,bottom+point,GOLD,1.3f);
    }
    public BitmapText text(String value, float size, float x, float y, float width, float height, int color) {
        BitmapText t=new BitmapText(font); t.setText(UiTypography.normalize(value)); t.setSize(size);
        t.setColor(Materials.rgb(color)); t.setLocalTranslation(0,0,z+3); parent.attachChild(t);
        UiTypography.centerIn(t,x,y,width,height); return t;
    }
    public Geometry portrait(String path,float cx,float cy,float radius) {
        float diameter=radius*2.12f;
        Geometry g=new Geometry("Commander portrait",new Quad(diameter,diameter));
        Material material=materials.flat(ColorRGBA.White); material.setTexture("ColorMap",assets.loadTexture(path));
        g.setMaterial(material); g.setLocalTranslation(cx-diameter/2,cy-diameter/2,z+1); return attach(g);
    }
    public Geometry arc(String name,float cx,float cy,float radius,float width,float start,float sweep,int color) {
        Geometry g=new Geometry(name,arcMesh(radius,width,start,sweep,false));
        g.setMaterial(materials.flat(Materials.linear(color))); g.setLocalTranslation(cx,cy,z+2); return attach(g);
    }
    public static Mesh arcMesh(float radius,float width,float start,float sweep,boolean segmented) {
        var points=new ArrayList<Vector3f>(); var indices=new ArrayList<Integer>();
        int count=segmented?30:80;
        float step=(segmented?FastMath.PI:FastMath.TWO_PI)/count;
        for(int i=0;i<count && i*step<sweep-.00001f;i++) {
            float a=start+i*step, b=start+Math.min(sweep,(i+(segmented?.86f:1))*step);
            if(b<=a) continue;
            int n=points.size();
            for(float[] v:new float[][]{{radius-width/2,a},{radius+width/2,a},{radius+width/2,b},{radius-width/2,b}})
                points.add(new Vector3f(FastMath.cos(v[1])*v[0],FastMath.sin(v[1])*v[0],0));
            for(int k:new int[]{0,1,2,0,2,3}) indices.add(n+k);
        }
        Mesh mesh=new Mesh(); mesh.setBuffer(VertexBuffer.Type.Position,3,BufferUtils.createFloatBuffer(points.toArray(Vector3f[]::new)));
        mesh.setBuffer(VertexBuffer.Type.Index,3,indices.stream().mapToInt(Integer::intValue).toArray()); mesh.updateBound(); return mesh;
    }
    public void gear(float cx,float cy,float radius) {
        arc("Settings medallion",cx,cy,radius/2,radius,0,FastMath.TWO_PI,BLUE);
        arc("Settings rim",cx,cy,radius,2,0,FastMath.TWO_PI,GOLD);
        arc("Gear center",cx,cy,radius*.4f,3,0,FastMath.TWO_PI,WHITE);
        for(int i=0;i<8;i++) {
            float a=FastMath.TWO_PI*i/8;
            line(cx+FastMath.cos(a)*radius*.4f,cy+FastMath.sin(a)*radius*.4f,
                 cx+FastMath.cos(a)*radius*.62f,cy+FastMath.sin(a)*radius*.62f,WHITE,5);
        }
    }
    public void moveArrows(float cx,float cy) {
        for(int i=0;i<4;i++) {
            float a=FastMath.HALF_PI*i, dx=FastMath.cos(a),dy=FastMath.sin(a);
            float x=cx+dx*25,y=cy+dy*25;
            line(cx+dx*9,cy+dy*9,x,y,WHITE,3);
            line(x,y,x-dx*8-dy*6,y-dy*8+dx*6,WHITE,3);
            line(x,y,x-dx*8+dy*6,y-dy*8-dx*6,WHITE,3);
        }
    }
    public void refreshIcon(float cx,float cy) {
        for(int i=0;i<2;i++) {
            float start=.2f+i*FastMath.PI, end=start+FastMath.PI*.72f, radius=16;
            arc("Random arrows",cx,cy,radius,4,start,FastMath.PI*.72f,WHITE);
            float x=cx+FastMath.cos(end)*radius,y=cy+FastMath.sin(end)*radius;
            line(x,y,x+FastMath.sin(end)*9+FastMath.cos(end)*4,y-FastMath.cos(end)*9+FastMath.sin(end)*4,WHITE,4);
        }
    }
    private Geometry attach(Geometry g) {
        g.getMaterial().getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        g.getMaterial().getAdditionalRenderState().setDepthWrite(false);
        g.setQueueBucket(Bucket.Gui); parent.attachChild(g); return g;
    }
}
