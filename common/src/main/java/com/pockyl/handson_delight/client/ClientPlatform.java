package com.pockyl.handson_delight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.resources.Identifier;

import com.pockyl.handson_delight.platform.Platform;

/**
 * What differs between the loaders on the client only, kept apart from {@link Platform}, which a dedicated server loads too. Each
 * loader project provides one implementation, listed in its {@code META-INF/services}.
 */
public interface ClientPlatform {
    ClientPlatform INSTANCE = Platform.load(ClientPlatform.class);

    /**
     * A model registered by its plain id (another mod's additional model), or null where the loader has no such lookup: vanilla
     * looks models up by {@code ModelResourceLocation} only.
     */
    default BlockStateModel model(Identifier id) {
        return null;
    }

    /** The colour a fluid is drawn in, ARGB, as its own client look tints it; 0 where the loader cannot tell. */
    default int fluidColor(net.minecraft.world.level.material.Fluid fluid) {
        return 0;
    }

    /** The block atlas texture a still pool of the fluid is drawn with, or null where the loader cannot tell. */
    default Identifier fluidTexture(net.minecraft.world.level.material.Fluid fluid) {
        return null;
    }
}
