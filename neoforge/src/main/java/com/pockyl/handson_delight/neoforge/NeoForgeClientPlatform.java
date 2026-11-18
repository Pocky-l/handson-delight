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

    @Override
    public int fluidColor(net.minecraft.world.level.material.Fluid fluid) {
        int tint = net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid).getTintColor();
        // A fluid that is not tinted draws its texture as it is: its colour is then the texture's, which the caller reads itself.
        return tint == 0xFFFFFFFF ? 0 : tint;
    }

    @Override
    public ResourceLocation fluidTexture(net.minecraft.world.level.material.Fluid fluid) {
        return net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid).getStillTexture();
    }
}
