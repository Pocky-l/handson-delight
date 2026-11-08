package com.pockyl.handson_delight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.pockyl.diegesis.api.client.NodePainter;
import com.pockyl.handson_delight.HandsOnDelight;

/**
 * The fat a frying piece lets out: a thin amber puddle on the pan around each piece, growing in while it fries and drying off once
 * the pan is empty or cold. Painted at the node {@code oil}, which rides on the pan, so it tilts with a toss.
 */
final class PanOil implements NodePainter {
    /** A block-atlas sprite: the scene keeps everything see-through on the block sheet, in one sorted batch. */
    private static final ResourceLocation SPRITE = HandsOnDelight.id("block/oil_film");
    /** A puddle reaches this far past its piece's own size, times. */
    private static final float SPREAD = 1.3F;
    /** Seconds the fat takes to run out to its full puddle, and to dry off. */
    private static final float GROW = 2.5F;
    private static final float DRY = 1.5F;
    /** How much the puddle's light breathes, as hot oil shimmers. */
    private static final float SHIMMER = 0.12F;

    private float[][] places = new float[0][];
    private float size;
    private float amount;
    private float clock;

    /** The pieces now on the pan, pan pixels from its middle, and the size they are drawn at. */
    void lay(float[][] places, float size) {
        this.places = places;
        this.size = size;
    }

    /** Runs out while {@code frying}, dries off otherwise. */
    void frame(float dt, boolean frying) {
        clock += dt;
        amount = Mth.clamp(amount + (frying ? dt / GROW : -dt / DRY), 0.0F, 1.0F);
    }

    @Override
    public void paint(PoseStack poseStack, MultiBufferSource buffers, int light) {
        if (amount <= 0.0F || places.length == 0) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(Sheets.translucentCullBlockSheet());
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(SPRITE);
        // Grows from the middle of each piece, so the fat seems to run out from under it.
        float grown = Mth.sqrt(amount);
        for (int i = 0; i < places.length; i++) {
            float half = size * SPREAD * 0.5F * (0.55F + 0.45F * grown);
            float shimmer = 1.0F - SHIMMER + SHIMMER * Mth.sin(clock * 2.6F + i * 1.9F);
            int alpha = Mth.clamp(Math.round(255.0F * amount * shimmer), 0, 255);
            poseStack.pushPose();
            poseStack.translate(places[i][0], 0.0F, places[i][1]);
            // Quarter turns only: pixels turned by any other angle break the grid they share with the pan.
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F * i));
            quad(consumer, poseStack.last().pose(), poseStack.last().normal(), sprite, half, alpha, light);
            poseStack.popPose();
        }
    }

    private static void quad(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, TextureAtlasSprite sprite, float half, int alpha,
            int light) {
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        vertex(consumer, pose, normal, -half, -half, u0, v0, alpha, light);
        vertex(consumer, pose, normal, -half, half, u0, v1, alpha, light);
        vertex(consumer, pose, normal, half, half, u1, v1, alpha, light);
        vertex(consumer, pose, normal, half, -half, u1, v0, alpha, light);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, float x, float z, float u, float v, int alpha,
            int light) {
        consumer.vertex(pose, x, 0.0F, z).color(255, 255, 255, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
    }
}
