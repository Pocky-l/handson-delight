package com.pockyl.handson_delight.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_delight.station.KitchenOven;
import com.pockyl.handson_delight.station.OvenStation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An oven's book: what bakes of what, over what heat and for how long, in tabs by kind where it has more than one (furnace food,
 * campfire dishes, dishes in bakeware). The heat shows as the oven's usual fire: coal for an oven that burns fuel, Farmer's Delight's
 * stove for one that stands on it; the time in seconds. A pick fills the tray from the inventory.
 */
final class OvenBook implements RecipeBook.Source {
    private static final ResourceLocation STOVE = ResourceLocation.fromNamespaceAndPath("farmersdelight", "stove");
    /** The tabs' names and icons, by kind. */
    private static final Map<String, ItemStack> ICONS = new LinkedHashMap<>();

    static {
        ICONS.put(OvenStation.KIND_FOOD, new ItemStack(Items.BREAD));
        ICONS.put(OvenStation.KIND_CAMPFIRE, new ItemStack(Items.CAMPFIRE));
        ICONS.put(OvenStation.KIND_BAKEWARE, new ItemStack(Items.PUMPKIN_PIE));
    }

    private static KitchenOven oven(StationView view) {
        return KitchenOven.of(view.level().getBlockEntity(view.pos()));
    }

    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        KitchenOven oven = oven(view);
        List<RecipeBook.Page> pages = new ArrayList<>();
        if (oven == null) {
            return pages;
        }
        ItemStack heat = oven.burnsFuel() ? new ItemStack(Items.COAL)
                : BuiltInRegistries.ITEM.getOptional(STOVE).map(ItemStack::new).orElse(new ItemStack(Items.CAMPFIRE));
        for (KitchenOven.Baking recipe : oven.recipes(view.level())) {
            if (recipe.needed().isEmpty() || recipe.needed().size() > oven.places()) {
                continue;
            }
            // The bakeware it bakes in first, where it bakes in some: the cook brings it along.
            List<ItemStack> foot = recipe.bakeware().isEmpty() ? List.of(heat, new ItemStack(Items.CLOCK))
                    : List.of(recipe.bakeware().copy(), heat, new ItemStack(Items.CLOCK));
            Component seconds = Component.translatable("handson_delight.book.seconds", Math.max(1, Math.round(recipe.ticks() / 20.0F)));
            pages.add(new RecipeBook.Page(recipe.id(), recipe.result(), recipe.needed(), foot, seconds, false, recipe.kind()));
        }
        return pages;
    }

    /** One tab a kind of recipe the oven has, when it has more than one kind. */
    @Override
    public List<RecipeBook.Tab> tabs(StationView view) {
        KitchenOven oven = oven(view);
        if (oven == null) {
            return List.of();
        }
        List<String> kinds = oven.recipes(view.level()).stream().map(KitchenOven.Baking::kind).distinct().toList();
        if (kinds.size() < 2) {
            return List.of();
        }
        List<RecipeBook.Tab> tabs = new ArrayList<>();
        tabs.add(new RecipeBook.Tab(null, new ItemStack(Items.COMPASS), Component.translatable("handson_delight.book.tab.all")));
        if (oven.usesBakeware()) {
            // A tab a bakeware, the one with the most recipes first: its own item and name.
            Map<String, Long> counts = new LinkedHashMap<>();
            oven.recipes(view.level()).forEach(recipe -> counts.merge(recipe.kind(), 1L, Long::sum));
            counts.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).forEach(entry -> {
                ItemStack icon = BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(entry.getKey())).map(ItemStack::new)
                        .orElse(ItemStack.EMPTY);
                if (!icon.isEmpty()) {
                    tabs.add(new RecipeBook.Tab(entry.getKey(), icon, icon.getHoverName()));
                }
            });
            return tabs;
        }
        for (String kind : ICONS.keySet()) {
            if (kinds.contains(kind)) {
                tabs.add(new RecipeBook.Tab(kind, ICONS.get(kind), Component.translatable("handson_delight.book.tab.oven_" + kind)));
            }
        }
        return tabs;
    }

    @Override
    public String memory() {
        return "oven";
    }

    /** A recipe picked in the book goes in from the inventory: one, or as many as the tray takes with Shift. */
    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        CompoundTag fill = new CompoundTag();
        fill.putString(OvenStation.DISH, page.key().toString());
        fill.putBoolean(OvenStation.ALL, Screen.hasShiftDown());
        view.sendAction(OvenStation.ACTION_FILL, fill);
    }

    @Override
    public List<ItemStack> inStation(StationView view) {
        return List.of(view.state().getItems(OvenStation.INGREDIENTS));
    }

    /** The book opens at what lies on the tray. */
    @Override
    public Object current(StationView view) {
        KitchenOven oven = oven(view);
        if (oven == null) {
            return null;
        }
        List<KitchenOven.Baking> recipes = oven.recipes(view.level());
        ItemStack[] onTray = view.state().getItems(OvenStation.INGREDIENTS);
        if (oven.bakesEachPlace()) {
            for (ItemStack stack : onTray) {
                var baking = KitchenOven.bakingOf(recipes, stack);
                if (baking.isPresent()) {
                    return baking.get().id();
                }
            }
            return null;
        }
        ItemStack bakeware = view.state().schema().slotGroups().containsKey(OvenStation.BAKEWARE)
                ? view.state().getItem(OvenStation.BAKEWARE_SLOT) : ItemStack.EMPTY;
        return KitchenOven.matching(recipes, onTray, bakeware).map(KitchenOven.Baking::id).orElse(null);
    }

    /** An oven that bakes each place alone takes any of its recipes beside what lies there: a page picked stays open. */
    @Override
    public boolean fits(StationView view, Object key) {
        KitchenOven oven = oven(view);
        return oven != null && oven.bakesEachPlace() || RecipeBook.Source.super.fits(view, key);
    }
}
