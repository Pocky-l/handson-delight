package com.pockyl.handson_delight.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.ControlSpec;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.state.TankSpec;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.platform.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Youkai's Feasts' steamer as a station: the tower of its pot and racks opens from any of its blocks. The lid comes off and is set
 * on the counter, the top rack is open; a click on the side of a rack lower down lifts the racks over it off together and sets them
 * aside, so that its places are open. Food goes onto the place it is let go over, a rack in the hand goes on top, the lid goes back
 * on, water is poured into the pot; what lies there is taken off with a click, everything done on the open rack with shift. The steamer steams as
 * before, through its own code: the station shows it and does by hand what its clicks did.
 */
public final class SteamerStation {
    public static final ResourceLocation ID = HandsOnDelight.id("youkai_steamer");

    /** Where a stack let go over the open rack is caught: one slot that never keeps anything. */
    public static final String HAND = "hand";
    /** The scene's water in the pot, shown by code: the steamer keeps only whether there is any. */
    public static final String WATER = "water";
    /**
     * The open rack, counted from the pot up: the racks over it are lifted off and set aside, for everybody who sees the tower.
     * {@link #TOP} and anything past the top rack: the top rack, nothing lifted.
     */
    public static final String OPEN = "open";
    public static final int TOP = SteamerTower.MOST_RACKS;

    /**
     * Custom actions. {@code put}: one of the stack on the cursor, or in inventory slot {@link #FROM}, onto {@link #PLACE} of
     * {@link #RACK}, or the rack's first free place without a place; with {@link #ALL} on every free place of the tower, the open
     * rack first.
     */
    public static final String ACTION_PUT = "put";
    /** {@code take}: what lies on {@link #PLACE} of {@link #RACK} goes to the cook; with {@link #ALL} everything done on the tower. */
    public static final String ACTION_TAKE = "take";
    /** {@code back}: the last thing the cook laid comes back off. */
    public static final String ACTION_BACK = "back";
    /** {@code open}: {@link #RACK} is opened, the racks over it lifted off; the top rack puts them back. */
    public static final String ACTION_OPEN = "open";
    /** {@code rack}: a rack, on the cursor or in slot {@link #FROM}, goes onto the top of the tower. */
    public static final String ACTION_RACK = "rack";
    /** {@code lid}: the lid, on the cursor or in slot {@link #FROM}, goes onto the tower. */
    public static final String ACTION_LID = "lid";
    /** {@code water}: a water bucket, on the cursor or in slot {@link #FROM}, is poured into the pot. */
    public static final String ACTION_WATER = "water";
    /** {@code fill}: what {@link #DISH} steams from goes from the inventory onto the open rack; with {@link #ALL} over the tower. */
    public static final String ACTION_FILL = "fill";
    public static final String RACK = "rack";
    public static final String PLACE = "place";
    public static final String FROM = "from";
    public static final String ALL = "all";
    public static final String DISH = "dish";

    /**
     * Things went onto racks: {@link #LAID}, each with its {@link #RACK}, {@link #PLACE} and {@link #STACK}; {@link #BOOK} when the
     * book laid them.
     */
    public static final ResourceLocation EFFECT_IN = HandsOnDelight.id("steamer_in");
    /** Things came off to the cook: {@link #LAID} as for {@link #EFFECT_IN}. */
    public static final ResourceLocation EFFECT_TAKEN = HandsOnDelight.id("steamer_taken");
    /** What the steamer would not take: {@link #STACK}. */
    public static final ResourceLocation EFFECT_REFUSED = HandsOnDelight.id("steamer_refused");
    /** A rack went on top: {@link #RACK} is the new one. */
    public static final ResourceLocation EFFECT_RACK = HandsOnDelight.id("steamer_rack");
    /** The lid went on. */
    public static final ResourceLocation EFFECT_LID = HandsOnDelight.id("steamer_lid");
    /** Water went into the pot. */
    public static final ResourceLocation EFFECT_WATER = HandsOnDelight.id("steamer_water");
    public static final String LAID = "laid";
    public static final String STACK = "stack";
    public static final String BOOK = "book";

    public static final StationSchema SCHEMA = StationSchema.builder()
            .slots(HAND, 1, SlotRules.DEFAULT)
            .tank(WATER, TankSpec.of(100))
            .control(OPEN, ControlSpec.stepped(0.0F, TOP, TOP, TOP).locked())
            .build();

    public static final Supplier<Block> POT = () -> SteamerTower.block("steamer_pot");
    public static final Supplier<Block> RACK_BLOCK = () -> SteamerTower.block("steamer_rack");
    public static final Supplier<Block> LID = () -> SteamerTower.block("steamer_lid");

    /**
     * The tower is the pot's station: a rack block or the lid opens it at the pot under them. While somebody works there the scene
     * stands in for the whole tower, so that its racks and lid can come off.
     */
    public static final StationType TYPE = Diegesis.station(ID)
            .block(POT)
            .block(RACK_BLOCK)
            .block(LID)
            .anchor(SteamerTower::potUnder)
            .hidesBlocks(SteamerTower::above)
            .schema(SCHEMA)
            .logic(SteamerLogic::new)
            .orientation(StationType.Orientation.PLAYER)
            .openWhen(SteamerStation::opens)
            .build();

    /** A steaming recipe: what goes on a rack, what it becomes, and the ticks it takes with the steam of a covered tower. */
    public record Steaming(ResourceLocation id, Ingredient ingredient, ItemStack result, int ticks) {
    }

    private SteamerStation() {
    }

    public static void register() {
        if (Platform.INSTANCE.isLoaded(KitchenSteamer.MOD_ID)) {
            Diegesis.register(TYPE);
        }
    }

    /**
     * At the pot (a rack or the lid stands for the pot under them; racks on anything else are no station), with an empty hand,
     * something the steamer takes, a rack, the lid, a water bucket or a kitchen thing. Sneaking keeps the steamer's own use (it
     * takes a rack off with what is on it).
     */
    public static boolean opens(Player player, BlockPos pos, BlockState state) {
        SteamerTower tower = SteamerTower.isPot(state) ? SteamerTower.of(player.level(), pos) : null;
        if (tower == null || player.isSecondaryUseActive()) {
            return false;
        }
        ItemStack held = player.getMainHandItem();
        return held.isEmpty() || tower.potBlock().handson$takes(held) || isRack(held) || isLid(held) || held.is(Items.WATER_BUCKET)
                || StoveStation.isKitchenThing(player.level(), held);
    }

    public static boolean isRack(ItemStack stack) {
        return !stack.isEmpty() && stack.is(RACK_BLOCK.get().asItem());
    }

    public static boolean isLid(ItemStack stack) {
        return !stack.isEmpty() && stack.is(LID.get().asItem());
    }

    /**
     * A rack held with Shift on the pot, a block of racks or the lid of a tower goes onto the tower's top, flush, as a plain click
     * does without the station. A plain click opens the station here, so Shift is the way round it; Youkai's own click passes on
     * Shift, and the rack item then placed a block of its own over the pot, its one rack drawn 8 px over the pot's top (Discord,
     * 2026-11-26). A rack on anything else, or a full tower, keeps the rack's own placing. True when the rack went on; on the client
     * when it would, so the placing is stopped there too.
     */
    public static boolean stacksOnShift(Player player, Level level, BlockPos pos, ItemStack held) {
        if (!player.isSecondaryUseActive() || !isRack(held)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!SteamerTower.isPot(state) && !SteamerTower.isRack(state) && !SteamerTower.isLid(state)) {
            return false;
        }
        SteamerTower tower = SteamerTower.of(level, SteamerTower.potUnder(level, pos, state));
        if (tower == null || !tower.hasRoom() || !tower.top().handson$addRack()) {
            return false;
        }
        if (!level.isClientSide() && !player.getAbilities().instabuild) {
            held.shrink(1);
        }
        return true;
    }

    /** The steamer's recipes, in the recipe manager's order. */
    @SuppressWarnings({"unchecked", "rawtypes", "removal"})
    public static List<Steaming> recipes(Level level) {
        List<Steaming> found = new ArrayList<>();
        Optional<RecipeType<?>> type = BuiltInRegistries.RECIPE_TYPE.getOptional(ResourceLocation.fromNamespaceAndPath(KitchenSteamer.MOD_ID, "steaming"));
        if (type.isEmpty()) {
            return found;
        }
        for (RecipeHolder<?> holder : (List<RecipeHolder<?>>) (List) level.getRecipeManager().getAllRecipesFor((RecipeType) type.get())) {
            if (holder.value() instanceof AbstractCookingRecipe cooking && !cooking.getIngredients().isEmpty()) {
                ItemStack result = cooking.getResultItem(level.registryAccess()).copy();
                if (!result.isEmpty()) {
                    found.add(new Steaming(holder.id(), cooking.getIngredients().get(0), result, cooking.getCookingTime()));
                }
            }
        }
        return found;
    }
}
