package com.pockyl.handson_delight.neoforge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.xkmc.youkaishomecoming.content.pot.cooking.core.CookingBlockEntity;
import dev.xkmc.youkaishomecoming.content.pot.cooking.soup.SoupHolder;
import dev.xkmc.youkaishomecoming.util.FluidRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.handson_delight.client.YoukaiPotFlights;

import java.util.ArrayList;
import java.util.List;

/**
 * Youkai's Feasts' pots draw their soup and what floats in it themselves. Where the station's scene is drawn, the soup is ours
 * (its own soup textures flicker from frame to frame): the scene's liquid in the soup's colour, and its own box is left out. What
 * the station shows coming down from the cook is in the pot already, but floats only once it is in: its place in the ring stays
 * free until then. What floats is drawn two thirds of its own size, so that a pot holds a dozen things without them covering its
 * soup, and the ring turns as the stockpot's ladle stirs it.
 */
@Pseudo
@Mixin(targets = "dev.xkmc.youkaishomecoming.content.pot.cooking.core.CookingRenderUtil", remap = false)
abstract class YoukaiPotSoupMixin {
    @Unique
    private static final float HANDSON$SMALLER = 2.0F / 3.0F;
    /** The pot being drawn: the renderer runs on the render thread only. */
    @Unique
    private static BlockPos handson$drawing = BlockPos.ZERO;

    /** The soup's box, left out where the scene draws ours (both layers, the one fading in and the one fading out). */
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Ldev/xkmc/youkaishomecoming/util/FluidRenderer;renderFluidBox"
            + "(Lnet/minecraft/resources/ResourceLocation;FFFFFFLnet/minecraft/client/renderer/MultiBufferSource;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;IZI)V"))
    private static void handson$oursInstead(ResourceLocation texture, float x0, float y0, float z0, float x1, float y1, float z1,
            MultiBufferSource buffers, PoseStack pose, int light, boolean bottom, int color, Level level, ItemRenderer renderer,
            CookingBlockEntity pot) {
        if (!DiegesisClient.isSceneDrawn(((BlockEntity) (Object) pot).getBlockPos())) {
            FluidRenderer.renderFluidBox(texture, x0, y0, z0, x1, y1, z1, buffers, pose, light, bottom, color);
        }
    }

    @Redirect(method = "render", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
            target = "Ldev/xkmc/youkaishomecoming/content/pot/cooking/soup/SoupHolder;floatingItems:Ljava/util/List;"))
    private static List<SoupHolder.ItemEntry> handson$notYetIn(SoupHolder soup, Level level, ItemRenderer renderer,
            CookingBlockEntity pot) {
        List<SoupHolder.ItemEntry> floating = soup.floatingItems;
        handson$drawing = ((BlockEntity) (Object) pot).getBlockPos();
        int flying = YoukaiPotFlights.flying(handson$drawing);
        if (flying <= 0 || floating.isEmpty()) {
            return floating;
        }
        List<SoupHolder.ItemEntry> drawn = new ArrayList<>(floating);
        for (int i = drawn.size() - 1; i >= 0 && flying > 0; i--) {
            if (!drawn.get(i).stack().isEmpty()) {
                drawn.set(i, new SoupHolder.ItemEntry(ItemStack.EMPTY, 0));
                flying--;
            }
        }
        return drawn;
    }

    /** Where in the ring a thing floats, turned on by the stirring. */
    @ModifyArg(method = "render", at = @At(value = "INVOKE", ordinal = 0, remap = true,
            target = "Lcom/mojang/math/Axis;rotationDegrees(F)Lorg/joml/Quaternionf;"))
    private static float handson$stirred(float angle) {
        return angle + YoukaiPotFlights.swirl(handson$drawing);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", remap = true,
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
    private static void handson$smaller(PoseStack pose, float x, float y, float z) {
        pose.scale(x * HANDSON$SMALLER, y * HANDSON$SMALLER, z * HANDSON$SMALLER);
    }
}
