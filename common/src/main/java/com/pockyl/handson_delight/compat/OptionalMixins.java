package com.pockyl.handson_delight.compat;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Applies the mixins into other mods' classes only where that mod is installed. Mixin looks a target class up before it skips a
 * missing one, and logs an error for each it cannot find; this asks first, quietly, so a game without KubeJS or the addon stoves
 * starts with a clean log. Every other mixin applies as always.
 */
public final class OptionalMixins implements IMixinConfigPlugin {
    private static final String PACKAGE = "com.pockyl.handson_delight.mixin.";
    private static final Set<String> OPTIONAL = Set.of(PACKAGE + "KubeJsRecipesMixin", PACKAGE + "AddonStoveSmokeMixin");

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !OPTIONAL.contains(mixinClassName) || isPresent(targetClassName);
    }

    private static boolean isPresent(String className) {
        try {
            // The lookup Mixin makes for a target itself (ModLauncher serves no untransformed bytecode), minus its log line.
            MixinService.getService().getBytecodeProvider().getClassNode(className.replace('.', '/'));
            return true;
        } catch (ClassNotFoundException | IOException e) {
            return false;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
