package com.battleship.frontend.scene.environment;

import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.texture.Texture;
import com.jme3.texture.image.ColorSpace;
import java.io.IOException;

final class CastleImpostor {
    private record Tile(Vector4f crop, float aspect) {}
    private final Node root;
    private final Material[] materials = new Material[5];

    CastleImpostor(AssetManager assets, String name, String faction, Vector3f position,
                   float yaw, float height) {
        root = new Node(name);
        root.setLocalTranslation(position);
        root.setLocalRotation(new Quaternion().fromAngles(0, yaw, 0));
        String path = "textures/castles/architecture/" + faction + "-parts.png";
        Texture texture = assets.loadTexture(path);
        texture.getImage().setColorSpace(ColorSpace.sRGB);
        texture.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        texture.setMagFilter(Texture.MagFilter.Bilinear);
        texture.setWrap(Texture.WrapMode.EdgeClamp);
        Tile[] tiles = readTiles(assets, path);
        // Complete buildings occupy separate depth tiers; no view-dependent registration or rotation.
        layer(assets, texture, tiles[0], 0, "RearSpireLayer", 0, -0.24f*height, height, false);
        layer(assets, texture, tiles[2], 1, "LeftWingLayer", -0.38f*height, 0.07f*height, 0.58f*height, false);
        layer(assets, texture, tiles[2], 2, "RightWingLayer", 0.38f*height, 0.07f*height, 0.58f*height, true);
        layer(assets, texture, tiles[1], 3, "MainFacadeLayer", 0, 0, 0.76f*height, false);
        layer(assets, texture, tiles[3], 4, "FrontGateLayer", 0, 0.20f*height, 0.46f*height, false);
    }

    Node root() { return root; }

    void setOpacity(float value) {
        for (Material material : materials) material.setFloat("Opacity", value);
    }

    private void layer(AssetManager assets, Texture texture, Tile tile, int index, String name,
                       float x, float depth, float height, boolean mirror) {
        Material material = new Material(assets, "materials/CastleArtwork.j3md");
        material.setTexture("ColorMap", texture);
        material.setVector4("Crop", tile.crop());
        material.setFloat("Opacity", 1);
        material.setFloat("Brightness", 0.98f);
        RenderState state = material.getAdditionalRenderState();
        state.setBlendMode(RenderState.BlendMode.Alpha);
        state.setDepthWrite(true);
        state.setFaceCullMode(RenderState.FaceCullMode.Back);
        materials[index] = material;
        float width = height * tile.aspect();
        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, new float[] {
                -width/2,0,0, width/2,0,0, width/2,height,0, -width/2,height,0});
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, mirror
                ? new float[] {1,0, 0,0, 0,1, 1,1} : new float[] {0,0, 1,0, 1,1, 0,1});
        mesh.setBuffer(VertexBuffer.Type.Index, 3, new int[] {0,1,2, 0,2,3});
        mesh.updateBound();
        Geometry image = new Geometry(root.getName() + " " + name, mesh);
        image.setMaterial(material);
        image.setQueueBucket(RenderQueue.Bucket.Opaque);
        image.setShadowMode(RenderQueue.ShadowMode.Off);
        Node layer = new Node(name);
        layer.setLocalTranslation(x, 0, depth);
        layer.attachChild(image);
        root.attachChild(layer);
    }

    private static Tile[] readTiles(AssetManager assets, String path) {
        try (var stream = assets.locateAsset(new AssetKey<>(path)).openStream()) {
            var image = javax.imageio.ImageIO.read(stream);
            int width=image.getWidth(), height=image.getHeight();
            int[] pixels=image.getRGB(0,0,width,height,null,0,width);
            // Locate transparent gutters rather than cutting off masonry at an assumed half-image.
            int row=divider(pixels,width,height,0,height,true);
            int upper=divider(pixels,width,height,0,row,false);
            int lower=divider(pixels,width,height,row,height,false);
            int[][] regions={{0,0,upper,row},{upper,0,width,row},
                    {0,row,lower,height},{lower,row,width,height}};
            Tile[] tiles=new Tile[4];
            for (int i=0;i<regions.length;i++) {
                int[] region=regions[i];
                int minX=region[2], maxX=region[0], minY=region[3], maxY=region[1];
                for(int y=region[1];y<region[3];y++)
                    for(int x=region[0];x<region[2];x++) {
                        if((pixels[y*width+x]>>>24)<=32) continue;
                        minX=Math.min(minX,x); maxX=Math.max(maxX,x);
                        minY=Math.min(minY,y); maxY=Math.max(maxY,y);
                    }
                minX=Math.max(region[0],minX-3); maxX=Math.min(region[2]-1,maxX+3);
                minY=Math.max(region[1],minY-3); maxY=Math.min(region[3]-1,maxY+3);
                float w=maxX-minX+1, h=maxY-minY+1;
                tiles[i]=new Tile(new Vector4f(minX/(float)width,(height-maxY-1f)/height,w/width,h/height),w/h);
            }
            return tiles;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load castle components: "+path,exception);
        }
    }

    private static int divider(int[] pixels, int width, int height, int top, int bottom, boolean rows) {
        int size=rows?height:width, best=size/2, score=Integer.MAX_VALUE;
        for(int at=(int)(size*0.40f);at<(int)(size*0.62f);at++) {
            int count=0;
            if(rows) {
                for(int x=0;x<width;x++) if((pixels[at*width+x]>>>24)>64) count++;
            } else {
                for(int y=top;y<bottom;y++) if((pixels[y*width+at]>>>24)>64) count++;
            }
            if(count<score || count==score && Math.abs(at-size/2)<Math.abs(best-size/2)) {
                best=at; score=count;
            }
        }
        return best;
    }
}
