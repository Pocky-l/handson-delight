package com.pockyl.handson_delight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
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
}
