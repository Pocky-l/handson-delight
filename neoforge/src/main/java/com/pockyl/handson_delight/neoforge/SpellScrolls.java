package com.pockyl.handson_delight.neoforge;

import io.redspace.ironsspellbooks.api.item.IScroll;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Iron's Spells' scrolls, as Farmer's Spell's alchemist pot reads them: the school of a scroll is that of its first spell. Loaded only
 * through the pot's mixin, so only where both mods are there.
 */
public final class SpellScrolls {
    private static final ResourceLocation SCROLL = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "scroll");
    /** A school Iron's Spells does not know any more: a violet. */
    private static final int UNKNOWN = 0xB070FF;

    private SpellScrolls() {
    }

    public static boolean isScroll(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof IScroll;
    }

    /** The school of the scroll's first spell, or null. */
    public static ResourceLocation schoolOf(ItemStack scroll) {
        if (!isScroll(scroll)) {
            return null;
        }
        ISpellContainer spells = ISpellContainer.get(scroll);
        SpellData spell = spells == null ? null : spells.getSpellAtIndex(0);
        SchoolType school = spell == null || spell.getSpell() == null ? null : spell.getSpell().getSchoolType();
        return school == null ? null : school.getId();
    }

    /** A scroll of every enabled spell of {@code school}, at its lowest level, in the order Iron's Spells registers them. */
    public static List<ItemStack> scrollsOf(ResourceLocation school) {
        SchoolType type = school == null ? null : SchoolRegistry.getSchool(school);
        Item item = BuiltInRegistries.ITEM.get(SCROLL);
        List<ItemStack> scrolls = new ArrayList<>();
        if (type == null || item == Items.AIR) {
            return scrolls;
        }
        // Read from the spells each time: Iron's Spells caches its list per school the first time it is asked, and a school is read
        // from its config, which a server builds only when a player joins.
        for (AbstractSpell spell : SpellRegistry.REGISTRY) {
            if (spell.isEnabled() && spell.getSchoolType() == type) {
                ItemStack scroll = new ItemStack(item);
                ISpellContainer.createScrollContainer(spell, spell.getMinLevel(), scroll);
                scrolls.add(scroll);
            }
        }
        return scrolls;
    }

    /** The colour Iron's Spells aims a school's spells in, 0xRRGGBB. */
    public static int colorOf(ResourceLocation school) {
        SchoolType type = school == null ? null : SchoolRegistry.getSchool(school);
        if (type == null) {
            return UNKNOWN;
        }
        Vector3f color = type.getTargetingColor();
        return Math.round(clamp01(color.x) * 255.0F) << 16 | Math.round(clamp01(color.y) * 255.0F) << 8 | Math.round(clamp01(color.z) * 255.0F);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    /** Any scroll of {@code school}, shown as one of each of its spells. */
    public static Ingredient ingredient(ResourceLocation school) {
        return new Ingredient(new SchoolScroll(school));
    }

    /**
     * Matches a scroll by its school. It lives only in the recipe book on a client and is never written or sent, so it has no
     * ingredient type of its own (the mod registers nothing).
     */
    private record SchoolScroll(ResourceLocation school) implements ICustomIngredient {
        @Override
        public boolean test(ItemStack stack) {
            return stack != null && school.equals(schoolOf(stack));
        }

        @Override
        public Stream<ItemStack> getItems() {
            return scrollsOf(school).stream();
        }

        @Override
        public boolean isSimple() {
            return false;
        }

        @Override
        public IngredientType<?> getType() {
            throw new UnsupportedOperationException("A scroll of a school is never written");
        }
    }
}
