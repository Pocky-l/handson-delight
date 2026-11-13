package com.pockyl.handson_delight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.BakedModel;

import com.pockyl.handson_delight.platform.Platform;

/**
 * What differs between the loaders on the client only, kept apart from {@link Platform}, which a dedicated server loads too. Each
 * loader project provides one implementation, listed in its {@code META-INF/services}.
 */
public interface ClientPlatform {
    ClientPlatform INSTANCE = Platform.load(ClientPlatform.class);

    /**
     * Applies the GUI transform of {@code model} to {@code poseStack} and gives the model to draw after it, as
     * {@code ItemDisplayContext.NONE}: on NeoForge a model can give another one for the GUI.
     */
    BakedModel guiModel(BakedModel model, PoseStack poseStack);
}
