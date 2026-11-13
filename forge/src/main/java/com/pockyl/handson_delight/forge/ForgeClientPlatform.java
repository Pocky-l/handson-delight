package com.pockyl.handson_delight.forge;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;

import com.pockyl.handson_delight.client.ClientPlatform;

/** Forge's baked models choose their GUI model themselves (separate transforms) while they apply the transform. */
public final class ForgeClientPlatform implements ClientPlatform {
    @Override
    public BakedModel guiModel(BakedModel model, PoseStack poseStack) {
        return model.applyTransform(ItemDisplayContext.GUI, poseStack, false);
    }
}
