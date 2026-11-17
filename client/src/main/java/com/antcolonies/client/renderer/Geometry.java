package com.antcolonies.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.texture.OverlayTexture;

import org.joml.Vector3f;

/**
 * Quads and boxes for custom geometry, in pixels (1/16 block), with texture coordinates in texels of a square texture.
 * A hot path (thousands of quads a frame): it makes no objects per vertex or box.
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

    /** A quad with a normalized texture coordinate per corner, wound to face along {@code n} without mirroring the sprite. */
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

    public static int shade(int color, float factor) {
        int a = color >>> 24;
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) ((color & 0xFF) * factor));
        return a << 24 | r << 16 | g << 8 | b;
    }

    /** A species color lifted toward white until it reads against the tunnels' dark soil; light colors are left alone. */
    static int visibleOnDark(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int gr = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        float luma = 0.299F * r + 0.587F * gr + 0.114F * b;
        float min = 105;
        if (luma >= min) {
            return rgb & 0xFFFFFF;
        }
        // Scale first (keeps the hue: navy becomes a clear blue, not grey), then blend toward white for what is left.
        float scale = Math.min(2.6F, min / Math.max(luma, 1));
        r = Math.min(255, (int) (r * scale));
        gr = Math.min(255, (int) (gr * scale));
        b = Math.min(255, (int) (b * scale));
        luma = 0.299F * r + 0.587F * gr + 0.114F * b;
        float t = Math.max(0, (min - luma) / (255 - luma));
        r = (int) (r + (255 - r) * t);
        gr = (int) (gr + (255 - gr) * t);
        b = (int) (b + (255 - b) * t);
        return r << 16 | gr << 8 | b;
    }
}
