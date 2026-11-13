package com.pockyl.handson_delight.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;

import com.pockyl.handson_delight.client.ClientPlatform;

/** Vanilla's way: the model's GUI transform, the same model. */
public final class FabricClientPlatform implements ClientPlatform {
    @Override
    public BakedModel guiModel(BakedModel model, PoseStack poseStack) {
        model.getTransforms().getTransform(ItemDisplayContext.GUI).apply(false, poseStack);
        return model;
    }
}
