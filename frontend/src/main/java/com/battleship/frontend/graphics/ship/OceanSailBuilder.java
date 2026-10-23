package com.battleship.frontend.graphics.ship;

import com.jme3.material.Material;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import java.nio.FloatBuffer;

import static com.battleship.frontend.graphics.ProceduralMeshes.*;

public final class OceanSailBuilder {
    public void build(Node ship, boolean radiant, Material canvas, Material trim,
                      Material jewel, Material enamel) {
        float[] mastZ = {-1.40f, -0.05f, 1.21f};
        float[] tops = radiant ? new float[] {3.22f, 3.75f, 3.34f}
                : new float[] {3.48f, 4.03f, 3.60f};
        for (int i = 0; i < mastZ.length; i++) {
            float z = mastZ[i], top = tops[i], width = i == 1 ? 1.60f : 1.28f;
            rod(ship, "Gilded lower mast", new Vector3f(0, 0.44f, z),
                    new Vector3f(0, top - 0.54f, z), 0.030f, trim);
            rod(ship, "Tapered royal mast", new Vector3f(0, top - 0.54f, z),
                    new Vector3f(0, top + 0.06f, z), 0.015f, trim);
            gem(ship, new Vector3f(0, top + 0.12f, z),
                    new Vector3f(0.045f, radiant ? 0.13f : 0.19f, 0.045f), jewel);
            box(ship, "Lookout enamel collar", new Vector3f(0.10f, 0.026f, 0.10f),
                    new Vector3f(0, top - 0.46f, z), enamel);
            int tiers = i == 1 ? 3 : 2;
            float available = top - 0.92f, tierStep = available / tiers;
            for (int tier = 0; tier < tiers; tier++) {
                float h = tierStep - 0.13f, y = 0.73f + tier * tierStep;
                silk(ship, z, y, width * (1 - tier * 0.19f), h, radiant, canvas, trim, jewel);
            }
            banner(ship, z, top - 0.12f, radiant, canvas, trim);
            for (int side : new int[] {-1, 1}) {
                for (int line = -1; line <= 1; line++) {
                    rod(ship, "Royal mast shroud", new Vector3f(side * 0.48f, 0.47f, z + line * 0.24f),
                            new Vector3f(0, top - 0.48f, z), 0.0045f, trim);
                }
                for (int rung = 1; rung <= 6; rung++) {
                    float t = rung / 8f, x = side * 0.48f * (1 - t), y = 0.47f + (top - 0.95f) * t;
                    rod(ship, "Shroud woven ladder", new Vector3f(x, y, z - 0.24f * (1 - t)),
                            new Vector3f(x, y, z + 0.24f * (1 - t)), 0.003f, trim);
                }
            }
            if (i > 0) rod(ship, "Overhead mast stay", new Vector3f(0, tops[i - 1] - 0.05f, mastZ[i - 1]),
                    new Vector3f(0, top - 0.05f, z), 0.0045f, trim);
        }
        rod(ship, "Bowsprit forestay", new Vector3f(0, tops[2] - 0.20f, mastZ[2]),
                new Vector3f(0, 0.89f, 3.16f), 0.005f, trim);
        Mesh jib = new Mesh();
        jib.setBuffer(VertexBuffer.Type.Position, 3,
                new float[] {0, 0.98f, 2.91f, 0.09f, 2.54f, 1.31f, 0.13f, 1.04f, 1.50f});
        jib.setBuffer(VertexBuffer.Type.Normal, 3, triangleNormals(jib));
        jib.setBuffer(VertexBuffer.Type.TexCoord, 2, new float[] {0, 0, 1, 1, 1, 0});
        jib.updateBound();
        attach(ship, "Swept triangular fore-silk", jib, canvas);
        rod(ship, "Forward silk golden edge", new Vector3f(0, 0.98f, 2.91f),
                new Vector3f(0.09f, 2.54f, 1.31f), 0.011f, trim);
    }

    private void silk(Node ship, float z, float y, float w, float h, boolean radiant,
                      Material canvas, Material trim, Material jewel) {
        Node sail = new Node("Billowing celestial silk");
        sail.setLocalTranslation(0, y, z + 0.045f);
        attach(sail, "Cambered woven sail", cloth(w, h, radiant), canvas);
        rod(sail, "Gilded sail yard", new Vector3f(-w * 0.48f, h + 0.02f, 0),
                new Vector3f(w * 0.48f, h + 0.02f, 0), 0.018f, trim);
        curve(sail, w, h, radiant, 0, 0, 1, 0, 10, 0.012f, 0.007f, trim);
        curve(sail, w, h, radiant, 0, 1, 1, 1, 8, 0.011f, 0.007f, trim);
        for (int side = 0; side <= 1; side++) {
            curve(sail, w, h, radiant, side, 0, side, 1, 8, 0.010f, 0.007f, trim);
        }
        for (float seam : new float[] {0.25f, 0.75f}) {
            curve(sail, w, h, radiant, seam, 0.03f, seam, 0.98f, 6, 0.003f, 0.004f, trim);
        }
        for (int face : new int[] {-1, 1}) {
            float radius = Math.min(h * 0.23f, w * 0.21f), offset = face * 0.018f;
            for (int k = 0; k < 16; k++) {
                float a = k * FastMath.TWO_PI / 16, b = (k + 1) * FastMath.TWO_PI / 16;
                float r0 = radius * (k % 2 == 0 ? 1 : 0.33f);
                float r1 = radius * (k % 2 == 0 ? 0.33f : 1);
                curve(sail, w, h, radiant, 0.5f + FastMath.cos(a) * r0 / (w * 0.90f),
                        0.52f + FastMath.sin(a) * r0 / h, 0.5f + FastMath.cos(b) * r1 / (w * 0.90f),
                        0.52f + FastMath.sin(b) * r1 / h, 1, 0.006f, offset, trim);
            }
            Vector3f crest = point(0.5f, 0.52f, w, h, radiant);
            crest.z += offset;
            gem(sail, crest, new Vector3f(0.018f, 0.026f, 0.014f), jewel);
        }
        ship.attachChild(sail);
    }

    private Mesh cloth(float w, float h, boolean radiant) {
        int cols = 12, rows = 10, stride = cols + 1, count = stride * (rows + 1);
        float[] positions = new float[count * 3], normals = new float[count * 3], uv = new float[count * 2];
        int[] indices = new int[cols * rows * 6];
        for (int row = 0; row <= rows; row++) {
            for (int col = 0; col <= cols; col++) {
                float u = col / (float) cols, v = row / (float) rows;
                int index = row * stride + col;
                Vector3f p = point(u, v, w, h, radiant);
                // Finite differences include the scalloped edge and camber in the true normal.
                Vector3f du = point(Math.min(1, u + 0.001f), v, w, h, radiant)
                        .subtract(point(Math.max(0, u - 0.001f), v, w, h, radiant));
                Vector3f dv = point(u, Math.min(1, v + 0.001f), w, h, radiant)
                        .subtract(point(u, Math.max(0, v - 0.001f), w, h, radiant));
                Vector3f n = du.cross(dv).normalizeLocal();
                positions[index * 3] = p.x; positions[index * 3 + 1] = p.y; positions[index * 3 + 2] = p.z;
                normals[index * 3] = n.x; normals[index * 3 + 1] = n.y; normals[index * 3 + 2] = n.z;
                uv[index * 2] = u; uv[index * 2 + 1] = v;
            }
        }
        int index = 0;
        for (int row = 0; row < rows; row++) for (int col = 0; col < cols; col++) {
            int a = row * stride + col, b = a + 1, c = a + stride, d = c + 1;
            indices[index++] = a; indices[index++] = b; indices[index++] = c;
            indices[index++] = b; indices[index++] = d; indices[index++] = c;
        }
        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, positions);
        mesh.setBuffer(VertexBuffer.Type.Normal, 3, normals);
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, uv);
        mesh.setBuffer(VertexBuffer.Type.Index, 3, indices);
        mesh.updateBound();
        return mesh;
    }

    private Vector3f point(float u, float v, float w, float h, boolean radiant) {
        float scallop = radiant ? 0.075f * FastMath.sin(u * FastMath.PI)
                + 0.035f * FastMath.sin(u * FastMath.TWO_PI) * FastMath.sin(u * FastMath.TWO_PI)
                : 0.17f * Math.abs(2 * u - 1);
        float billow = FastMath.sin(u * FastMath.PI) * FastMath.sin(v * FastMath.PI);
        return new Vector3f((u - 0.5f) * w * (1 - 0.16f * v), h * v + scallop * (1 - v),
                w * 0.19f * billow + 0.012f * FastMath.sin(u * FastMath.TWO_PI * 3) * billow);
    }

    private void curve(Node parent, float w, float h, boolean radiant, float u0, float v0,
                       float u1, float v1, int segments, float radius, float offset, Material material) {
        Vector3f previous = point(u0, v0, w, h, radiant); previous.z += offset;
        for (int step = 1; step <= segments; step++) {
            float t = step / (float) segments;
            Vector3f next = point(u0 + (u1 - u0) * t, v0 + (v1 - v0) * t, w, h, radiant);
            next.z += offset;
            rod(parent, "Curved silk embroidery", previous, next, radius, material);
            previous = next;
        }
    }

    private void banner(Node ship, float z, float y, boolean radiant, Material canvas, Material trim) {
        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, new float[] {
                0, y, z, 0.50f, y - 0.03f, z + 0.04f, 0.31f, y - 0.15f, z + 0.06f,
                0, y, z, 0.31f, y - 0.15f, z + 0.06f, 0, y - (radiant ? 0.18f : 0.27f), z});
        mesh.setBuffer(VertexBuffer.Type.Normal, 3, triangleNormals(mesh));
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, new float[12]);
        mesh.updateBound();
        attach(ship, "Swallowtail silk pennant", mesh, canvas);
        rod(ship, "Pennant gold upper seam", new Vector3f(0, y, z),
                new Vector3f(0.50f, y - 0.03f, z + 0.04f), 0.006f, trim);
    }

    private void attach(Node parent, String name, Mesh mesh, Material material) {
        Geometry geometry = new Geometry(name, mesh);
        geometry.setMaterial(material);
        parent.attachChild(geometry);
    }

    private float[] triangleNormals(Mesh mesh) {
        FloatBuffer p = mesh.getFloatBuffer(VertexBuffer.Type.Position);
        float[] normals = new float[p.limit()];
        for (int i = 0; i < normals.length; i += 9) {
            Vector3f a = new Vector3f(p.get(i), p.get(i + 1), p.get(i + 2));
            Vector3f b = new Vector3f(p.get(i + 3), p.get(i + 4), p.get(i + 5));
            Vector3f c = new Vector3f(p.get(i + 6), p.get(i + 7), p.get(i + 8));
            Vector3f n = b.subtract(a).cross(c.subtract(a)).normalizeLocal();
            for (int v = 0; v < 3; v++) { normals[i + v * 3] = n.x; normals[i + v * 3 + 1] = n.y; normals[i + v * 3 + 2] = n.z; }
        }
        return normals;
    }
}
