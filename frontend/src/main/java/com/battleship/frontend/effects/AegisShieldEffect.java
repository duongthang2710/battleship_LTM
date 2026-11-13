package com.battleship.frontend.effects;

import com.battleship.frontend.graphics.Materials;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.*;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.*;
import com.jme3.util.BufferUtils;
import java.util.ArrayList;
import java.util.List;
import static com.battleship.frontend.graphics.ProceduralMeshes.*;

final class AegisShieldEffect {
    private final Node template;

    AegisShieldEffect(Materials materials) {
        Material light=materials.glow(Materials.linear(0x83CFF0),.8f);
        Material gold=materials.metal(Materials.linear(0xD8BC7C),95);
        BatchNode lattice=new BatchNode("Aegis engraved lattice");
        float[] radii={.46f,.37f,.19f};
        float[] heights={0,.20f,.32f};
        List<Vector3f> vertices=new ArrayList<>();
        List<Integer> indices=new ArrayList<>();
        for(int level=0;level<radii.length;level++) {
            for(int corner=0;corner<6;corner++) {
                Vector3f point=point(corner,radii[level],heights[level]);
                vertices.add(point);
                rod(lattice,"Aegis hexagonal rail",point,point(corner+1,radii[level],heights[level]),.007f,
                        level==0?gold:light);
                if(level>0) rod(lattice,"Aegis curved meridian",point(corner,radii[level-1],heights[level-1]),point,.007f,light);
            }
        }
        vertices.add(new Vector3f(0,.38f,0));
        for(int corner=0;corner<6;corner++) {
            int next=(corner+1)%6;
            for(int level=0;level<2;level++) {
                int lower=level*6,upper=lower+6;
                indices.addAll(List.of(lower+corner,upper+corner,lower+next,lower+next,upper+corner,upper+next));
            }
            indices.addAll(List.of(12+corner,18,12+next));
            rod(lattice,"Aegis crown spoke",point(corner,.19f,.32f),new Vector3f(0,.38f,0),.006f,light);
            gem(lattice,point(corner,.46f,0),new Vector3f(.025f,.035f,.025f),light);
        }
        lattice.batch(); lattice.updateGeometricState();
        template=new Node("Elevated Aegis shield");
        for(Spatial child:lattice.getChildren()) if(child instanceof Geometry batch && batch.isIgnoreTransform()) {
            Geometry baked=new Geometry("Aegis lattice",batch.getMesh());
            baked.setMaterial(batch.getMaterial()); template.attachChild(baked);
        }
        Mesh mesh=new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position,3,BufferUtils.createFloatBuffer(vertices.toArray(Vector3f[]::new)));
        mesh.setBuffer(VertexBuffer.Type.Index,3,indices.stream().mapToInt(Integer::intValue).toArray()); mesh.updateBound();
        Geometry canopy=new Geometry("Aegis translucent canopy",mesh);
        ColorRGBA tint=Materials.linear(0x65BDEA); tint.a=.065f;
        Material glass=materials.flat(tint);
        glass.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        glass.getAdditionalRenderState().setDepthWrite(false);
        glass.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        canopy.setMaterial(glass); canopy.setQueueBucket(RenderQueue.Bucket.Transparent);
        template.attachChild(canopy); template.setShadowMode(RenderQueue.ShadowMode.Off);
    }

    Node create() { return template.clone(false); }

    private static Vector3f point(int corner,float radius,float height) {
        float angle=corner*FastMath.TWO_PI/6;
        return new Vector3f(FastMath.cos(angle)*radius,height,FastMath.sin(angle)*radius);
    }
}
