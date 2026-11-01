package com.battleship.frontend.ui;

import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;

public final class UiShapes {
    private UiShapes() { }
    public static Mesh polygonFrame(float[] xy,float thickness) {
        int n=xy.length/2;
        float[] vertices=new float[n*6]; int[] indices=new int[n*6];
        for(int i=0;i<n;i++) {
            int prev=(i+n-1)%n,next=(i+1)%n;
            float ax=xy[i*2]-xy[prev*2],ay=xy[i*2+1]-xy[prev*2+1];
            float bx=xy[next*2]-xy[i*2],by=xy[next*2+1]-xy[i*2+1];
            float al=(float)Math.hypot(ax,ay),bl=(float)Math.hypot(bx,by);
            float nx=-ay/al,ny=ax/al,mx=-by/bl,my=bx/bl;
            float inset=thickness/Math.max(.05f,1+nx*mx+ny*my);
            vertices[i*3]=xy[i*2];vertices[i*3+1]=xy[i*2+1];
            vertices[(n+i)*3]=xy[i*2]+(nx+mx)*inset;
            vertices[(n+i)*3+1]=xy[i*2+1]+(ny+my)*inset;
            int k=i*6; int[] face={i,next,n+i,next,n+next,n+i};
            System.arraycopy(face,0,indices,k,6);
        }
        Mesh mesh=new Mesh(); mesh.setBuffer(VertexBuffer.Type.Position,3,vertices);
        mesh.setBuffer(VertexBuffer.Type.Index,3,indices);mesh.updateBound();mesh.setStatic();return mesh;
    }
    public static void frame(com.jme3.scene.Node parent,float x,float y,float width,float height,float cut,float thickness,
                             com.jme3.material.Material material,float z) {
        float c=Math.min(cut,Math.min(width,height)/2);
        for(var child:parent.getChildren()) {
            if(child instanceof com.jme3.scene.Geometry g && g.getMesh() instanceof com.jme3.scene.shape.Quad q
                    && Math.abs(g.getLocalTranslation().x-x)<.01f && Math.abs(g.getLocalTranslation().y-y)<.01f
                    && Math.abs(q.getWidth()-width)<.01f && Math.abs(q.getHeight()-height)<.01f)
                g.setMesh(chamfered(width,height,c));
        }
        var g=new com.jme3.scene.Geometry("Joined UI frame",polygonFrame(new float[]{c,0,width-c,0,width,c,width,height-c,
                width-c,height,c,height,0,height-c,0,c},thickness));
        material.getAdditionalRenderState().setBlendMode(com.jme3.material.RenderState.BlendMode.Alpha);
        material.getAdditionalRenderState().setDepthWrite(false);g.setMaterial(material);
        g.setLocalTranslation(x,y,z);g.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Gui);parent.attachChild(g);
    }

    public static Mesh chamfered(float width, float height, float cut) {
        float c = Math.min(cut, Math.min(width, height) / 2);
        float[] points = {width / 2, height / 2, 0, c, 0, 0, width - c, 0, 0,
                width, c, 0, width, height - c, 0, width - c, height, 0,
                c, height, 0, 0, height - c, 0, 0, c, 0};
        int[] indices = new int[24];
        for (int i = 0; i < 8; i++) { indices[i * 3] = 0; indices[i * 3 + 1] = i + 1; indices[i * 3 + 2] = (i + 1) % 8 + 1; }
        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(points));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices));
        mesh.updateBound(); mesh.setStatic();
        return mesh;
    }
}
