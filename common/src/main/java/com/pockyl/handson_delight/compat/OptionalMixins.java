package com.pockyl.handson_delight.compat;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Applies the mixins into other mods' classes only where that mod is installed. Mixin looks a target class up before it skips a
 * missing one, and logs an error for each it cannot find; this asks first, quietly, so a game without the addon stoves or the keg
 * starts with a clean log. Every other mixin applies as always. Chopper's Delight's board mixin also needs the member it reaches
 * into, which versions before 1.2.0 (NeoForge) and 1.1.0 (Fabric) lack: left out there, their board keeps its own use. The NeoForge
 * mixins into addons' own stations ask more ({@code AddonMixins}).
 */
public final class OptionalMixins implements IMixinConfigPlugin {
    private static final String PACKAGE = "com.pockyl.handson_delight.mixin.";
    private static final Set<String> OPTIONAL = Set.of(PACKAGE + "AddonStoveSmokeMixin", PACKAGE + "KegMixin", PACKAGE + "OvenMixin",
            PACKAGE + "VatMixin", PACKAGE + "VatUprightMixin", PACKAGE + "VatSparksMixin", PACKAGE + "GrillSmokeMixin",
            PACKAGE + "GrillSizzleMixin", PACKAGE + "DecoratedBoardMixin", PACKAGE + "DecoratedBoardContentMixin");
    private static final String CHOPPERS_BOARD = PACKAGE + "DecoratedBoardMixin";
    /** {@code clearContent} of Chopper's board, as the jar names it: Mojang's name (NeoForge, Fabric's dev runs) or Fabric's. */
    private static final Set<String> CLEAR_CONTENT = Set.of("clearContent", "method_5448");

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!OPTIONAL.contains(mixinClassName)) {
            return true;
        }
        ClassNode target = classNode(targetClassName);
        if (target == null) {
            return false;
        }
        if (CHOPPERS_BOARD.equals(mixinClassName) && target.methods.stream().noneMatch(method -> CLEAR_CONTENT.contains(method.name))) {
            LoggerFactory.getLogger(OptionalMixins.class).warn("This version of choppersdelight is older than its station needs: "
                    + "DecoratedBoardMixin is left out");
            return false;
        }
        return true;
    }

    /** The class's bytecode as its jar has it, without loading it; null when no mod has it. */
    public static ClassNode classNode(String className) {
        try {
            // The lookup Mixin makes for a target itself (ModLauncher serves no untransformed bytecode), minus its log line.
            return MixinService.getService().getBytecodeProvider().getClassNode(className.replace('.', '/'));
        } catch (ClassNotFoundException | IOException e) {
            return null;
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
