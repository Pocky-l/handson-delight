package com.pockyl.handson_delight.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

import com.pockyl.handson_delight.client.ClientPlatform;

/** NeoForge's baked models choose their GUI model themselves (separate transforms) while they apply the transform. */
public final class NeoForgeClientPlatform implements ClientPlatform {
    @Override
    public BakedModel guiModel(BakedModel model, PoseStack poseStack) {
        return model.applyTransform(ItemDisplayContext.GUI, poseStack, false);
    }

    /** A model another mod registered by its plain id, as NeoForge keeps such models: standalone. */
    @Override
    public BakedModel model(ResourceLocation id) {
        ModelManager models = Minecraft.getInstance().getModelManager();
        BakedModel model = models.getModel(ModelResourceLocation.standalone(id));
        return model == models.getMissingModel() ? null : model;
    }
}
