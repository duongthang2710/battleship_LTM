package com.battleship.frontend.graphics;

import com.jme3.material.Material;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Cylinder;
import com.jme3.util.BufferUtils;
import java.util.ArrayList;
import java.util.List;

public final class ProceduralMeshes {
    private ProceduralMeshes() {}

    public static Mesh beveledPrism(float[][] outline, float[] heights, float[] scales) {
        List<Vector3f> vertices = new ArrayList<>();
        List<Vector3f> normals = new ArrayList<>();
        int count = outline.length;
        for (int level = 0; level < heights.length - 1; level++) {
            for (int i = 0; i < count; i++) {
                int next = (i + 1) % count;
                Vector3f a = point(outline[i], heights[level], scales[level]);
                Vector3f b = point(outline[i], heights[level + 1], scales[level + 1]);
                Vector3f c = point(outline[next], heights[level + 1], scales[level + 1]);
                Vector3f d = point(outline[next], heights[level], scales[level]);
                triangle(vertices, normals, a, b, c);
                triangle(vertices, normals, a, c, d);
            }
        }
        int top = heights.length - 1;
        for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;
            triangle(vertices, normals, new Vector3f(0, heights[top], 0),
                    point(outline[next], heights[top], scales[top]), point(outline[i], heights[top], scales[top]));
            triangle(vertices, normals, new Vector3f(0, heights[0], 0),
                    point(outline[i], heights[0], scales[0]), point(outline[next], heights[0], scales[0]));
        }
        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(vertices.toArray(Vector3f[]::new)));
        mesh.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(normals.toArray(Vector3f[]::new)));
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, new float[vertices.size() * 2]);
        mesh.updateBound();
        return mesh;
    }

    public static float[][] clippedSquare(float halfSize, float corner) {
        float a = halfSize, b = halfSize - corner;
        return new float[][] {{-b, -a}, {b, -a}, {a, -b}, {a, b},
                {b, a}, {-b, a}, {-a, b}, {-a, -b}};
    }

    public static Geometry box(Node parent, String name, Vector3f size, Vector3f position, Material material) {
        Geometry geometry = new Geometry(name, new Box(size.x, size.y, size.z));
        geometry.setMaterial(material);
        geometry.setLocalTranslation(position);
        parent.attachChild(geometry);
        return geometry;
    }

    public static Geometry rod(Node parent, String name, Vector3f start, Vector3f end,
                               float radius, Material material) {
        Vector3f direction = end.subtract(start);
        Geometry geometry = new Geometry(name, new Cylinder(2, 8, radius, direction.length(), true));
        geometry.setMaterial(material);
        geometry.setLocalTranslation(start.add(end).multLocal(0.5f));
        Vector3f up = Math.abs(direction.normalize().y) > 0.98f ? Vector3f.UNIT_X : Vector3f.UNIT_Y;
        geometry.setLocalRotation(new Quaternion().lookAt(direction, up));
        parent.attachChild(geometry);
        return geometry;
    }

    public static Geometry gem(Node parent, Vector3f position, Vector3f size, Material material) {
        float[][] outline = {{-1, 0}, {0, -1}, {1, 0}, {0, 1}};
        Geometry gem = new Geometry("Faceted crystal", beveledPrism(outline,
                new float[] {-1, -0.2f, 0.25f, 1}, new float[] {0, 0.8f, 0.8f, 0}));
        gem.setMaterial(material);
        gem.setLocalScale(size);
        gem.setLocalTranslation(position);
        parent.attachChild(gem);
        return gem;
    }

    public static Mesh curvedSail(float width, float height) {
        int columns = 8, rows = 10, stride = columns + 1;
        float[] positions = new float[stride * (rows + 1) * 3];
        float[] normals = new float[positions.length];
        int[] indices = new int[columns * rows * 6];
        for (int y = 0; y <= rows; y++) {
            for (int x = 0; x <= columns; x++) {
                float u = (float) x / columns, v = (float) y / rows;
                int i = (y * stride + x) * 3;
                positions[i] = (u - 0.5f) * width * (1 - 0.32f * v);
                positions[i + 1] = v * height;
                positions[i + 2] = 0.26f * (float) Math.sin(u * Math.PI) * (float) Math.sin(v * Math.PI);
                Vector3f normal = new Vector3f(-0.26f * (float) Math.PI / width
                        * (float) Math.cos(u * Math.PI) * (float) Math.sin(v * Math.PI),
                        -0.26f * (float) Math.PI / height * (float) Math.sin(u * Math.PI)
                                * (float) Math.cos(v * Math.PI), 1).normalizeLocal();
                normals[i] = normal.x; normals[i + 1] = normal.y; normals[i + 2] = normal.z;
            }
        }
        int i = 0;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                int a = y * stride + x, b = a + 1, c = a + stride, d = c + 1;
                indices[i++] = a; indices[i++] = b; indices[i++] = c;
                indices[i++] = b; indices[i++] = d; indices[i++] = c;
            }
        }
        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, positions);
        mesh.setBuffer(VertexBuffer.Type.Normal, 3, normals);
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, new float[stride * (rows + 1) * 2]);
        mesh.setBuffer(VertexBuffer.Type.Index, 3, indices);
        mesh.updateBound();
        return mesh;
    }

    private static Vector3f point(float[] point, float y, float scale) {
        return new Vector3f(point[0] * scale, y, point[1] * scale);
    }

    private static void triangle(List<Vector3f> vertices, List<Vector3f> normals,
                                 Vector3f a, Vector3f b, Vector3f c) {
        Vector3f normal = b.subtract(a).cross(c.subtract(a)).normalizeLocal();
        vertices.add(a); vertices.add(b); vertices.add(c);
        normals.add(normal); normals.add(normal); normals.add(normal);
    }
}
