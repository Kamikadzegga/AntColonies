package com.antcolonies.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.texture.OverlayTexture;

import org.joml.Vector3f;

/**
 * Quads and boxes for custom geometry, in pixels (1/16 block), with texture coordinates in texels of a square texture.
 * <p>
 * Thousands of quads a frame go through here when many Terrariums are in view, so it writes each vertex in one call (the
 * buffer's fast path), works out a quad's normal once rather than per corner, and makes no objects per vertex or box.
 */
public final class Geometry {
    private final PoseStack.Pose pose;
    private final VertexConsumer buffer;
    private final float textureSize;
    public int light;
    private final Vector3f scratch = new Vector3f();
    private float nx;
    private float ny;
    private float nz;

    public Geometry(PoseStack.Pose pose, VertexConsumer buffer, float textureSize, int light) {
        this.pose = pose;
        this.buffer = buffer;
        this.textureSize = textureSize;
        this.light = light;
    }

    /** The normal for the next vertices, turned by the pose. */
    private void normal(float x, float y, float z) {
        pose.transformNormal(x, y, z, scratch);
        nx = scratch.x;
        ny = scratch.y;
        nz = scratch.z;
    }

    /** One vertex at a point in pixels, with normalized texture coordinates and the current normal. */
    private void vertex(float x, float y, float z, int color, float u, float v) {
        pose.pose().transformPosition(x / 16, y / 16, z / 16, scratch);
        buffer.addVertex(scratch.x, scratch.y, scratch.z, color, u, v, OverlayTexture.NO_OVERLAY, light, nx, ny, nz);
    }

    /** A quad through four corners (counter-clockwise seen from the side it faces), textured with a texel rectangle. */
    public void quad(float[] a, float[] b, float[] c, float[] d, int color, float u0, float v0, float u1, float v1, float nx, float ny, float nz) {
        quad(a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2], color, u0, v0, u1, v1, nx, ny, nz);
    }

    private void quad(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
            int color, float u0, float v0, float u1, float v1, float nx, float ny, float nz) {
        normal(nx, ny, nz);
        float s = textureSize;
        vertex(ax, ay, az, color, u0 / s, v1 / s);
        vertex(bx, by, bz, color, u1 / s, v1 / s);
        vertex(cx, cy, cz, color, u1 / s, v0 / s);
        vertex(dx, dy, dz, color, u0 / s, v0 / s);
    }

    /**
     * A quad with its own normalized texture coordinate per corner, wound to face along {@code n}: the corners and
     * their coordinates stay paired, so a sprite is never mirrored by the winding.
     */
    public void quadUv(float[][] p, float[][] uv, int color, float[] n) {
        float abx = p[1][0] - p[0][0];
        float aby = p[1][1] - p[0][1];
        float abz = p[1][2] - p[0][2];
        float acx = p[2][0] - p[0][0];
        float acy = p[2][1] - p[0][1];
        float acz = p[2][2] - p[0][2];
        float cross = (aby * acz - abz * acy) * n[0] + (abz * acx - abx * acz) * n[1] + (abx * acy - aby * acx) * n[2];
        normal(n[0], n[1], n[2]);
        for (int k = 0; k < 4; k++) {
            int i = cross >= 0 ? k : 3 - k;
            vertex(p[i][0], p[i][1], p[i][2], color, uv[i][0], uv[i][1]);
        }
    }

    /** An axis-aligned box from (x0, y0, z0) to (x1, y1, z1), every face the same texture rectangle. */
    public void box(float x0, float y0, float z0, float x1, float y1, float z1, int color, float u0, float v0, float u1, float v1) {
        // Down and up.
        quad(x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, shade(color, 0.5F), u0, v0, u1, v1, 0, -1, 0);
        quad(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, color, u0, v0, u1, v1, 0, 1, 0);
        // North and south.
        int northSouth = shade(color, 0.8F);
        quad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, northSouth, u0, v0, u1, v1, 0, 0, -1);
        quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, northSouth, u0, v0, u1, v1, 0, 0, 1);
        // West and east.
        int westEast = shade(color, 0.6F);
        quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, westEast, u0, v0, u1, v1, -1, 0, 0);
        quad(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, westEast, u0, v0, u1, v1, 1, 0, 0);
    }

    /** The color with its RGB scaled (alpha kept). */
    public static int shade(int color, float factor) {
        int a = color >>> 24;
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) ((color & 0xFF) * factor));
        return a << 24 | r << 16 | g << 8 | b;
    }
}
