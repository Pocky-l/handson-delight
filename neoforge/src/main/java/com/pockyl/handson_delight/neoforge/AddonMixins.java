package com.pockyl.handson_delight.neoforge;

import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
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
 * its stump. Extra Delight's chiller finishes a recipe only from 2.7.2 on: before that it keeps its own menu, as a station whose
 * block never makes anything would only mislead. A mixin that does not fit stops the game at start, or the station at its first
 * use; left out, the addon's block keeps its own use. Asked before any class loads, from the bytecode alone.
 */
public final class AddonMixins implements IMixinConfigPlugin {
    private static final String PACKAGE = "com.pockyl.handson_delight.neoforge.mixin.";
    private static final String YOUKAI = "youkaisfeasts";
    private static final String EXTRA_DELIGHT = "extradelight";
    private static final String HEARTH = "hearthandharvest";
    /** Hearth and Harvest's keg, with its two tanks, and its cask sealed by hand, and aging only so: both from 1.4.0 on. */
    private static final String HEARTH_KEG = "alabaster.hearthandharvest.common.block.entity.KegBlockEntity";
    private static final String HEARTH_CASK = "alabaster.hearthandharvest.common.item.CaskBlockItem";
    /** Youkai's Feasts' drinks as fluids, which its kettle and fermentation tank stations pour and tap: from 1.0.4 on. */
    private static final String YOUKAI_DRINKS = "dev.xkmc.youkaishomecoming.content.item.fluid.IYHFluidItem";
    private static final String SABLE_TREES = "sabletrees";
    /** Sable Trees' cut of Farmer's Delight's recipes on its stump, with the count of food cut there: from 0.5.0 on. */
    private static final String SABLE_TREES_CUT = "com.snissac.sabletrees.block.ChoppingBlock$CuttingBoard";

    /** The first Extra Delight whose chiller puts what it made into its output. */
    private static final String EXTRA_DELIGHT_CHILLER = "2.7.2";

    /** What a mixin needs besides its target: the mod, another class of the mod (null for none), its oldest version (null for any). */
    private record Needs(String modId, String otherClass, String since) {
        Needs(String modId, String otherClass) {
            this(modId, otherClass, null);
        }

        static Needs mod(String modId) {
            return new Needs(modId, null, null);
        }

        static Needs since(String modId, String version) {
            return new Needs(modId, null, version);
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
            Map.entry("ChillerMixin", Needs.since(EXTRA_DELIGHT, EXTRA_DELIGHT_CHILLER)),
            Map.entry("EdOvenMixin", Needs.mod(EXTRA_DELIGHT)),
            Map.entry("ChoppingBlockEntityMixin", new Needs(SABLE_TREES, SABLE_TREES_CUT)),
            Map.entry("ChoppingBlockAccess", new Needs(SABLE_TREES, SABLE_TREES_CUT)),
            Map.entry("StumpCutMixin", new Needs(SABLE_TREES, SABLE_TREES_CUT)),
            Map.entry("StumpSplitMixin", new Needs(SABLE_TREES, SABLE_TREES_CUT)),
            Map.entry("StumpContentMixin", new Needs(SABLE_TREES, SABLE_TREES_CUT)),
            Map.entry("HearthKegMixin", new Needs(HEARTH, HEARTH_KEG)),
            Map.entry("HearthKegAccess", new Needs(HEARTH, HEARTH_KEG)),
            Map.entry("HearthCaskMixin", new Needs(HEARTH, HEARTH_CASK)),
            Map.entry("HearthCaskAccess", new Needs(HEARTH, HEARTH_CASK)));

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
        ModFileInfo mod = needs == null ? null : FMLLoader.getLoadingModList().getModFileById(needs.modId());
        if (needs != null && mod == null) {
            return false;
        }
        if (needs != null && needs.since() != null && mod.getMods().stream().filter(info -> info.getModId().equals(needs.modId()))
                .anyMatch(info -> info.getVersion().compareTo(new DefaultArtifactVersion(needs.since())) < 0)) {
            LoggerFactory.getLogger(AddonMixins.class).warn("This version of {} is older than its station needs ({}): {} is left out",
                    needs.modId(), needs.since(), mixinClassName.substring(PACKAGE.length()));
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
