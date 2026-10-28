package com.battleship.frontend.ui;

import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;

public final class UiShapes {
    private UiShapes() { }

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
