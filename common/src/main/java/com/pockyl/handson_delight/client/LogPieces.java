package com.pockyl.handson_delight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import com.pockyl.handson_delight.station.ChoppingBlockStation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A log standing on a stump, drawn as the wedges its cuts make, as a pie is cut: each wedge has the log's own faces, bark on the
 * outside, its end grain on top and underneath, and split wood where the axe went through (the stripped log's side, else its
 * planks). The whole log is one wedge with no cut. Drawn on the block sheet with the game's own lighting of blocks, so a log and
 * its pieces look the same.
 */
final class LogPieces {
    /** The textures of a log's faces, from its models: end grain, bark, split wood. */
    record Faces(TextureAtlasSprite end, TextureAtlasSprite bark, TextureAtlasSprite split) {
    }

    /** One wedge: its outline seen from above, scene pixels from the log's middle, counter-clockwise from its first cut. */
    record Piece(float[] xs, float[] zs, boolean firstCut, boolean lastCut, float middle) {
        /** How far the wedge reaches along {@code angle} from the log's middle. */
        float reach(float angle) {
            float most = 0.0F;
            for (int i = 0; i < xs.length; i++) {
                most = Math.max(most, xs[i] * Mth.cos(angle) + zs[i] * Mth.sin(angle));
            }
            return most;
        }
    }

    private LogPieces() {
    }

    /** {@code log}'s faces: the stripped log's side for the split wood, else its planks' side, else its bark. */
    @SuppressWarnings("deprecation")
    static Faces faces(Level level, ItemStack log) {
        BlockState state = log.getItem() instanceof BlockItem item ? item.getBlock().defaultBlockState() : Blocks.OAK_LOG.defaultBlockState();
        TextureAtlasSprite end = sprite(state, Direction.UP);
        TextureAtlasSprite bark = sprite(state, Direction.NORTH);
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        Block stripped = BuiltInRegistries.BLOCK.get(id.withPath("stripped_" + id.getPath()));
        TextureAtlasSprite split;
        if (stripped != Blocks.AIR && !id.getPath().startsWith("stripped_")) {
            split = sprite(stripped.defaultBlockState(), Direction.NORTH);
        } else {
            ItemStack planks = ChoppingBlockStation.planks(level, log);
            split = planks.getItem() instanceof BlockItem item ? sprite(item.getBlock().defaultBlockState(), Direction.NORTH) : bark;
        }
        if (id.getPath().startsWith("stripped_")) {
            // A stripped log is split wood all through.
            split = bark;
        }
        return new Faces(end, bark, split);
    }

    private static TextureAtlasSprite sprite(BlockState state, Direction side) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        List<BakedQuad> quads = model.getQuads(state, side, RandomSource.create(42L));
        return quads.isEmpty() ? model.getParticleIcon() : quads.get(0).getSprite();
    }

    /**
     * The wedges a square log of half width {@code half} falls into along cuts through its middle at {@code angles} (radians from
     * straight across, as the board's marks lie). No cut: the whole log.
     */
    static List<Piece> pieces(float half, float[] angles) {
        List<Piece> pieces = new ArrayList<>();
        if (angles.length == 0) {
            pieces.add(new Piece(new float[] {-half, half, half, -half}, new float[] {-half, -half, half, half}, false, false, 0.0F));
            return pieces;
        }
        float[] rays = new float[angles.length * 2];
        for (int i = 0; i < angles.length; i++) {
            rays[i * 2] = Mth.wrapDegrees(angles[i] * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
            rays[i * 2 + 1] = Mth.wrapDegrees(angles[i] * Mth.RAD_TO_DEG + 180.0F) * Mth.DEG_TO_RAD;
        }
        Arrays.sort(rays);
        // Two cuts along one line make one cut.
        int kept = 1;
        for (int i = 1; i < rays.length; i++) {
            if (rays[i] - rays[kept - 1] > 1.0e-3F) {
                rays[kept++] = rays[i];
            }
        }
        if (kept > 1 && rays[0] + Mth.TWO_PI - rays[kept - 1] < 1.0e-3F) {
            kept--;
        }
        rays = Arrays.copyOf(rays, kept);
        float[] corners = {-3.0F * Mth.PI / 4.0F, -Mth.PI / 4.0F, Mth.PI / 4.0F, 3.0F * Mth.PI / 4.0F};
        for (int i = 0; i < rays.length; i++) {
            float from = rays[i];
            float to = i + 1 < rays.length ? rays[i + 1] : rays[0] + Mth.TWO_PI;
            List<float[]> outline = new ArrayList<>();
            outline.add(new float[] {0.0F, 0.0F});
            outline.add(edge(half, from));
            for (int turn = 0; turn < 2; turn++) {
                for (float corner : corners) {
                    float at = corner + turn * Mth.TWO_PI;
                    if (at > from && at < to) {
                        outline.add(new float[] {half * Mth.sign(Mth.cos(at)), half * Mth.sign(Mth.sin(at))});
                    }
                }
            }
            outline.add(edge(half, to));
            float[] xs = new float[outline.size()];
            float[] zs = new float[outline.size()];
            for (int p = 0; p < outline.size(); p++) {
                xs[p] = outline.get(p)[0];
                zs[p] = outline.get(p)[1];
            }
            pieces.add(new Piece(xs, zs, true, true, (from + to) / 2.0F));
        }
        return pieces;
    }

    /** Where a ray from the middle at {@code angle} leaves the square. */
    private static float[] edge(float half, float angle) {
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        float reach = half / Math.max(Math.abs(cos), Math.abs(sin));
        return new float[] {cos * reach, sin * reach};
    }

    /**
     * {@code piece} of a log {@code height} high, standing on y 0: moved by {@code dx, dz}, then turned by {@code yaw} radians about
     * the vertical (from the scene's frame into the piece's own) and moved by {@code -footZ} along its own z.
     */
    static void draw(PoseStack pose, MultiBufferSource buffers, int light, Faces faces, Piece piece, float height, float dx, float dz,
            float yaw, float footZ) {
        VertexConsumer out = buffers.getBuffer(Sheets.solidBlockSheet());
        PoseStack.Pose at = pose.last();
        int count = piece.xs().length;
        Vector3f[] top = new Vector3f[count];
        Vector3f[] bottom = new Vector3f[count];
        float cos = Mth.cos(yaw);
        float sin = Mth.sin(yaw);
        float half = 0.0F;
        for (int i = 0; i < count; i++) {
            half = Math.max(half, Math.max(Math.abs(piece.xs()[i]), Math.abs(piece.zs()[i])));
        }
        for (int i = 0; i < count; i++) {
            top[i] = place(piece.xs()[i] + dx, height, piece.zs()[i] + dz, cos, sin, footZ);
            bottom[i] = place(piece.xs()[i] + dx, 0.0F, piece.zs()[i] + dz, cos, sin, footZ);
        }
        Vector3f up = turn(0.0F, 1.0F, 0.0F, cos, sin);
        Vector3f down = turn(0.0F, -1.0F, 0.0F, cos, sin);
        // End grain over the top and under the foot, laid as the block's top lies: a fan from the first point.
        for (int i = 1; i + 1 < count; i++) {
            int[] tri = {0, i, i + 1};
            float[][] uv = new float[3][];
            for (int k = 0; k < 3; k++) {
                uv[k] = new float[] {(piece.xs()[tri[k]] + half) / (2.0F * half), (piece.zs()[tri[k]] + half) / (2.0F * half)};
            }
            quad(out, at, light, faces.end(), up, top[tri[0]], top[tri[1]], top[tri[2]], top[tri[2]], uv[0], uv[1], uv[2], uv[2]);
            quad(out, at, light, faces.end(), down, bottom[tri[0]], bottom[tri[1]], bottom[tri[2]], bottom[tri[2]], uv[0], uv[1], uv[2],
                    uv[2]);
        }
        for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;
            float ax = piece.xs()[i];
            float az = piece.zs()[i];
            float bx = piece.xs()[j];
            float bz = piece.zs()[j];
            float length = Mth.sqrt((bx - ax) * (bx - ax) + (bz - az) * (bz - az));
            if (length < 1.0e-4F) {
                continue;
            }
            // Outward: the outline runs counter-clockwise seen from above (x right, z down the screen), so outside is to its right.
            Vector3f normal = turn((bz - az) / length, 0.0F, -(bx - ax) / length, cos, sin);
            boolean cut = i == 0 && piece.firstCut() || j == 0 && piece.lastCut();
            TextureAtlasSprite sprite = cut ? faces.split() : faces.bark();
            float u0;
            float u1;
            if (cut) {
                // Split wood from the middle out.
                float far = half * Mth.SQRT_OF_TWO;
                u0 = Mth.sqrt(ax * ax + az * az) / far;
                u1 = Mth.sqrt(bx * bx + bz * bz) / far;
            } else {
                // Bark along the side it lies on, as the block's side shows it.
                boolean alongX = Math.abs(bz - az) < Math.abs(bx - ax);
                u0 = ((alongX ? ax : az) + half) / (2.0F * half);
                u1 = ((alongX ? bx : bz) + half) / (2.0F * half);
            }
            quad(out, at, light, sprite, normal, top[i], top[j], bottom[j], bottom[i], new float[] {u0, 0.0F}, new float[] {u1, 0.0F},
                    new float[] {u1, 1.0F}, new float[] {u0, 1.0F});
        }
    }

    private static Vector3f place(float x, float y, float z, float cos, float sin, float footZ) {
        return turn(x, y, z, cos, sin).sub(0.0F, 0.0F, footZ);
    }

    /** Turned by the yaw whose cosine and sine are given, the vertical the axis. */
    private static Vector3f turn(float x, float y, float z, float cos, float sin) {
        return new Vector3f(cos * x - sin * z, y, sin * x + cos * z);
    }

    /** A face; its corners are put in the order that faces {@code normal}, whichever way they came. */
    private static void quad(VertexConsumer out, PoseStack.Pose at, int light, TextureAtlasSprite sprite, Vector3f normal, Vector3f a,
            Vector3f b, Vector3f c, Vector3f d, float[] ua, float[] ub, float[] uc, float[] ud) {
        Vector3f facing = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
        if (facing.lengthSquared() < 1.0e-8F) {
            facing = new Vector3f(c).sub(a).cross(new Vector3f(d).sub(a));
        }
        if (facing.dot(normal) < 0.0F) {
            vertex(out, at, light, sprite, normal, d, ud);
            vertex(out, at, light, sprite, normal, c, uc);
            vertex(out, at, light, sprite, normal, b, ub);
            vertex(out, at, light, sprite, normal, a, ua);
        } else {
            vertex(out, at, light, sprite, normal, a, ua);
            vertex(out, at, light, sprite, normal, b, ub);
            vertex(out, at, light, sprite, normal, c, uc);
            vertex(out, at, light, sprite, normal, d, ud);
        }
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose at, int light, TextureAtlasSprite sprite, Vector3f normal, Vector3f p,
            float[] uv) {
        out.addVertex(at, p.x, p.y, p.z).setColor(-1).setUv(sprite.getU(Mth.clamp(uv[0], 0.0F, 1.0F)), sprite.getV(Mth.clamp(uv[1], 0.0F, 1.0F)))
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(at, normal.x, normal.y, normal.z);
    }
}
