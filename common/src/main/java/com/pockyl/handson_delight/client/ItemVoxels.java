package com.pockyl.handson_delight.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.MeshShape;
import com.pockyl.handson_delight.mixin.SpriteContentsAccessor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An item lying flat, built from its icon's pixels as one pixel thick voxels, so that a knife can cut it where it came down: the
 * pixels on either side of a cut move apart, along a line at any angle. The icon's top lies away from the viewer, its left on the viewer's left (+x).
 */
final class ItemVoxels {
    private static final Map<Item, Optional<Icon>> ICONS = new HashMap<>();
    /** The model the game falls back on when the icons were read: a new one after every resource reload. */
    private static BakedModel readWith;

    private ItemVoxels() {
    }

    /** The pixels of an item's flat icon: the sprite of the block atlas they come from, and which of them are solid. */
    record Icon(ResourceLocation sprite, int width, int height, boolean[] solid) {
        boolean solid(int column, int row) {
            return column >= 0 && row >= 0 && column < width && row < height && solid[row * width + column];
        }

        /** The first and last columns and rows with a solid pixel: {left, right, top, bottom}. */
        int[] bounds() {
            int[] bounds = {width, -1, height, -1};
            for (int row = 0; row < height; row++) {
                for (int column = 0; column < width; column++) {
                    if (solid(column, row)) {
                        bounds[0] = Math.min(bounds[0], column);
                        bounds[1] = Math.max(bounds[1], column);
                        bounds[2] = Math.min(bounds[2], row);
                        bounds[3] = Math.max(bounds[3], row);
                    }
                }
            }
            return bounds;
        }
    }

    /** The icon of an item drawn flat; empty for one drawn as a block, which is not cut into rows. */
    static Optional<Icon> icon(ItemStack stack) {
        BakedModel missing = Minecraft.getInstance().getModelManager().getMissingModel();
        if (missing != readWith) {
            // Resources were reloaded: the icons read so far may show other textures now.
            readWith = missing;
            ICONS.clear();
        }
        return ICONS.computeIfAbsent(stack.getItem(), item -> read(stack));
    }

    private static Optional<Icon> read(ItemStack stack) {
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(stack, null, null, 0);
        if (model.isGui3d()) {
            return Optional.empty();
        }
        SpriteContents contents = model.getParticleIcon().contents();
        NativeImage image = ((SpriteContentsAccessor) contents).handson$originalImage();
        int width = contents.width();
        int height = contents.height();
        boolean[] solid = new boolean[width * height];
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                solid[row * width + column] = (image.getPixelRGBA(column, row) >>> 24) >= 128;
            }
        }
        return Optional.of(new Icon(contents.name(), width, height, solid));
    }

    /**
     * A cut across an item lying flat: the line through {@code x, z} (node pixels from the item's middle) at {@code angle} radians
     * from straight across (along x). Pixels are on one side or the other by their middles: the cut goes in steps, as pixels do.
     */
    record Cut(float x, float z, float angle) {
        /** The line's normal, towards +z when the cut runs straight across. */
        float normalX() {
            return -Mth.sin(angle);
        }

        float normalZ() {
            return Mth.cos(angle);
        }

        /** How far {@code px, pz} lies from the line, on the normal's side positive. */
        float distance(float px, float pz) {
            return normalX() * (px - x) + normalZ() * (pz - z);
        }
    }

    /**
     * The icon {@code size} pixels across at its longest side, cut along {@code cuts}, the pieces {@code gap} pixels apart: each one
     * moves away from every cut it lies beside.
     */
    static MeshShape build(Icon icon, float size, List<Cut> cuts, float gap) {
        int width = icon.width();
        int height = icon.height();
        float pixel = size / Math.max(width, height);
        int[] piece = new int[width * height];
        float[] shiftX = new float[width * height];
        float[] shiftZ = new float[width * height];
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                int index = row * width + column;
                float middleX = (width / 2.0F - column - 0.5F) * pixel;
                float middleZ = (height / 2.0F - row - 0.5F) * pixel;
                for (int i = 0; i < cuts.size(); i++) {
                    Cut cut = cuts.get(i);
                    boolean beyond = cut.distance(middleX, middleZ) > 0.0F;
                    float half = (beyond ? gap : -gap) / 2.0F;
                    piece[index] |= beyond ? 1 << i : 0;
                    shiftX[index] += cut.normalX() * half;
                    shiftZ[index] += cut.normalZ() * half;
                }
            }
        }
        MeshShape.Builder shape = MeshShape.builder();
        for (int row = 0; row < height; row++) {
            int start = -1;
            for (int column = 0; column <= width; column++) {
                boolean solid = icon.solid(column, row);
                int index = row * width + column;
                if (start >= 0 && !(solid && piece[index] == piece[row * width + start])) {
                    int first = row * width + start;
                    top(shape, icon, pixel, start, column, row, shiftX[first], shiftZ[first]);
                    start = -1;
                }
                if (!solid) {
                    continue;
                }
                if (start < 0) {
                    start = column;
                }
                int own = piece[index];
                float left = (width / 2.0F - column) * pixel + shiftX[index];
                float right = left - pixel;
                float far = (height / 2.0F - row) * pixel + shiftZ[index];
                float near = far - pixel;
                float u = (column + 0.5F) / width;
                float v = (row + 0.5F) / height;
                if (!joined(icon, piece, column - 1, row, own)) {
                    side(shape, new Vector3f(left, 0, near), new Vector3f(left, pixel, near), new Vector3f(left, pixel, far),
                            new Vector3f(left, 0, far), 1, 0, 0, u, v);
                }
                if (!joined(icon, piece, column + 1, row, own)) {
                    side(shape, new Vector3f(right, 0, near), new Vector3f(right, pixel, near), new Vector3f(right, pixel, far),
                            new Vector3f(right, 0, far), -1, 0, 0, u, v);
                }
                if (!joined(icon, piece, column, row - 1, own)) {
                    side(shape, new Vector3f(left, 0, far), new Vector3f(left, pixel, far), new Vector3f(right, pixel, far),
                            new Vector3f(right, 0, far), 0, 0, 1, u, v);
                }
                if (!joined(icon, piece, column, row + 1, own)) {
                    side(shape, new Vector3f(left, 0, near), new Vector3f(left, pixel, near), new Vector3f(right, pixel, near),
                            new Vector3f(right, 0, near), 0, 0, -1, u, v);
                }
            }
        }
        return shape.build();
    }

    /** Whether the pixel is solid and in the same piece: no face between the two then. */
    private static boolean joined(Icon icon, int[] piece, int column, int row, int with) {
        return icon.solid(column, row) && piece[row * icon.width() + column] == with;
    }

    /** The top of a run of pixels of one piece in a row, the icon mapped straight onto it, moved with its piece. */
    private static void top(MeshShape.Builder shape, Icon icon, float pixel, int from, int to, int row, float shiftX, float shiftZ) {
        float west = (icon.width() / 2.0F - to) * pixel + shiftX;
        float east = (icon.width() / 2.0F - from) * pixel + shiftX;
        float far = (icon.height() / 2.0F - row) * pixel + shiftZ;
        float near = far - pixel;
        float u0 = (float) from / icon.width();
        float u1 = (float) to / icon.width();
        float vFar = (float) row / icon.height();
        float vNear = (float) (row + 1) / icon.height();
        shape.corner(west, pixel, near, u1, vNear, 0, 1, 0)
                .corner(west, pixel, far, u1, vFar, 0, 1, 0)
                .corner(east, pixel, far, u0, vFar, 0, 1, 0)
                .corner(east, pixel, near, u0, vNear, 0, 1, 0);
    }

    /** A side face in one pixel's colour, turned to face out along its normal whichever way the corners were given. */
    private static void side(MeshShape.Builder shape, Vector3f a, Vector3f b, Vector3f c, Vector3f d, float nx, float ny, float nz,
            float u, float v) {
        Vector3f normal = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(b));
        Vector3f[] corners = normal.dot(nx, ny, nz) >= 0 ? new Vector3f[] {a, b, c, d} : new Vector3f[] {d, c, b, a};
        for (Vector3f corner : corners) {
            shape.corner(corner.x, corner.y, corner.z, u, v, nx, ny, nz);
        }
    }
}
