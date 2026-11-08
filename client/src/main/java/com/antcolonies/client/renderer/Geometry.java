package com.antcolonies.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Quads and boxes for custom geometry, in pixels (1/16 block), with texture coordinates in texels of a square texture.
 */
public final class Geometry {
    private final PoseStack.Pose pose;
    private final VertexConsumer buffer;
    private final float textureSize;
    public int light;

    public Geometry(PoseStack.Pose pose, VertexConsumer buffer, float textureSize, int light) {
        this.pose = pose;
        this.buffer = buffer;
        this.textureSize = textureSize;
        this.light = light;
    }

    private void vertex(float x, float y, float z, int color, float u, float v, float nx, float ny, float nz) {
        buffer.addVertex(pose, x / 16, y / 16, z / 16).setColor(color).setUv(u / textureSize, v / textureSize)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }

    /** A quad through four corners (counter-clockwise seen from the side it faces), textured with a texel rectangle. */
    public void quad(float[] a, float[] b, float[] c, float[] d, int color, float u0, float v0, float u1, float v1, float nx, float ny, float nz) {
        vertex(a[0], a[1], a[2], color, u0, v1, nx, ny, nz);
        vertex(b[0], b[1], b[2], color, u1, v1, nx, ny, nz);
        vertex(c[0], c[1], c[2], color, u1, v0, nx, ny, nz);
        vertex(d[0], d[1], d[2], color, u0, v0, nx, ny, nz);
    }

    /**
     * A quad with its own normalized texture coordinate per corner, wound to face along {@code n}: the corners and
     * their coordinates stay paired, so a sprite is never mirrored by the winding.
     */
    public void quadUv(float[][] p, float[][] uv, int color, float[] n) {
        float[] ab = {p[1][0] - p[0][0], p[1][1] - p[0][1], p[1][2] - p[0][2]};
        float[] ac = {p[2][0] - p[0][0], p[2][1] - p[0][1], p[2][2] - p[0][2]};
        float cross = (ab[1] * ac[2] - ab[2] * ac[1]) * n[0] + (ab[2] * ac[0] - ab[0] * ac[2]) * n[1] + (ab[0] * ac[1] - ab[1] * ac[0]) * n[2];
        for (int k = 0; k < 4; k++) {
            int i = cross >= 0 ? k : 3 - k;
            buffer.addVertex(pose, p[i][0] / 16, p[i][1] / 16, p[i][2] / 16).setColor(color).setUv(uv[i][0], uv[i][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, n[0], n[1], n[2]);
        }
    }

    /** An axis-aligned box from (x0, y0, z0) to (x1, y1, z1), every face the same texture rectangle. */
    public void box(float x0, float y0, float z0, float x1, float y1, float z1, int color, float u0, float v0, float u1, float v1) {
        // Down and up.
        quad(new float[] {x0, y0, z1}, new float[] {x0, y0, z0}, new float[] {x1, y0, z0}, new float[] {x1, y0, z1}, shade(color, 0.5F), u0, v0, u1, v1, 0, -1, 0);
        quad(new float[] {x0, y1, z0}, new float[] {x0, y1, z1}, new float[] {x1, y1, z1}, new float[] {x1, y1, z0}, color, u0, v0, u1, v1, 0, 1, 0);
        // North and south.
        quad(new float[] {x1, y0, z0}, new float[] {x0, y0, z0}, new float[] {x0, y1, z0}, new float[] {x1, y1, z0}, shade(color, 0.8F), u0, v0, u1, v1, 0, 0, -1);
        quad(new float[] {x0, y0, z1}, new float[] {x1, y0, z1}, new float[] {x1, y1, z1}, new float[] {x0, y1, z1}, shade(color, 0.8F), u0, v0, u1, v1, 0, 0, 1);
        // West and east.
        quad(new float[] {x0, y0, z0}, new float[] {x0, y0, z1}, new float[] {x0, y1, z1}, new float[] {x0, y1, z0}, shade(color, 0.6F), u0, v0, u1, v1, -1, 0, 0);
        quad(new float[] {x1, y0, z1}, new float[] {x1, y0, z0}, new float[] {x1, y1, z0}, new float[] {x1, y1, z1}, shade(color, 0.6F), u0, v0, u1, v1, 1, 0, 0);
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
