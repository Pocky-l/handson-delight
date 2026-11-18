package com.pockyl.handson_delight.neoforge;

import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import com.pockyl.handson_delight.compat.OptionalMixins;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applies the mixins into addons' own blocks only where they fit. The addon must be there under its own mod id: Youkai's Homecoming
 * ships classes of the same names as Youkai's Feasts, older and different. And its version must have what the mixin reaches into:
 * Youkai's Feasts before 1.0.4 lacks the drinks its kettle and fermentation tank stations pour, Sable Trees before 0.5.0 the cut on
 * its stump. A mixin that does not fit stops the game at start, or the station at its first use; left out, the addon's block keeps
 * its own use. Asked before any class loads, from the bytecode alone.
 */
public final class AddonMixins implements IMixinConfigPlugin {
    private static final String PACKAGE = "com.pockyl.handson_delight.neoforge.mixin.";
    private static final String YOUKAI = "youkaisfeasts";
    private static final String EXTRA_DELIGHT = "extradelight";
    /** Youkai's Feasts' drinks as fluids, which its kettle and fermentation tank stations pour and tap: from 1.0.4 on. */
    private static final String YOUKAI_DRINKS = "dev.xkmc.youkaishomecoming.content.item.fluid.IYHFluidItem";
    private static final String SABLE_TREES = "sabletrees";
    /** Sable Trees' cut of Farmer's Delight's recipes on its stump, with the count of food cut there: from 0.5.0 on. */
    private static final String SABLE_TREES_CUT = "com.snissac.sabletrees.block.ChoppingBlock$CuttingBoard";

    /** What a mixin needs besides its target: the mod, and another class of the mod (null for none). */
    private record Needs(String modId, String otherClass) {
        static Needs mod(String modId) {
            return new Needs(modId, null);
        }
    }

    /** The mixins into addons whose versions differ. The others (Miner's Delight, Dungeons Delight, ...) need their target only. */
    private static final Map<String, Needs> NEEDS = Map.ofEntries(
            Map.entry("YoukaiKettleMixin", new Needs(YOUKAI, YOUKAI_DRINKS)),
            Map.entry("YoukaiTankMixin", new Needs(YOUKAI, YOUKAI_DRINKS)),
            Map.entry("YoukaiPotMixin", Needs.mod(YOUKAI)),
            Map.entry("YoukaiTimedAccess", Needs.mod(YOUKAI)),
            Map.entry("YoukaiKettleRecipeAccess", Needs.mod(YOUKAI)),
            Map.entry("YoukaiFermentRecipeAccess", Needs.mod(YOUKAI)),
            Map.entry("YoukaiCuisineBoardMixin", Needs.mod(YOUKAI)),
            Map.entry("YoukaiSteamerMixin", Needs.mod(YOUKAI)),
            Map.entry("YoukaiPotSoupMixin", Needs.mod(YOUKAI)),
            Map.entry("YoukaiHintsMixin", Needs.mod(YOUKAI)),
            Map.entry("MixingBowlMixin", Needs.mod(EXTRA_DELIGHT)),
            Map.entry("MeltingPotMixin", Needs.mod(EXTRA_DELIGHT)),
            Map.entry("ChoppingBlockEntityMixin", new Needs(SABLE_TREES, SABLE_TREES_CUT)),
            Map.entry("ChoppingBlockAccess", new Needs(SABLE_TREES, SABLE_TREES_CUT)),
            Map.entry("StumpCutMixin", new Needs(SABLE_TREES, SABLE_TREES_CUT)),
            Map.entry("StumpSplitMixin", new Needs(SABLE_TREES, SABLE_TREES_CUT)),
            Map.entry("StumpContentMixin", new Needs(SABLE_TREES, SABLE_TREES_CUT)));

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        Needs needs = NEEDS.get(mixinClassName.substring(PACKAGE.length()));
        if (needs != null && FMLLoader.getLoadingModList().getModFileById(needs.modId()) == null) {
            return false;
        }
        if (OptionalMixins.classNode(targetClassName) == null) {
            return false;
        }
        if (needs != null && needs.otherClass() != null && OptionalMixins.classNode(needs.otherClass()) == null) {
            LoggerFactory.getLogger(AddonMixins.class).warn("This version of {} is older than its station needs: {} is left out",
                    needs.modId(), mixinClassName.substring(PACKAGE.length()));
            return false;
        }
        return true;
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
