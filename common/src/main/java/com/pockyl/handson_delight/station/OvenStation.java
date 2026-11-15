package com.pockyl.handson_delight.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.state.ControlSpec;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotRules;
import com.pockyl.diegesis.api.state.StationSchema;
import com.pockyl.diegesis.api.station.StationType;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.platform.Platform;

import java.util.function.Supplier;

/**
 * Cultural Delights' oven as a station: its grated door swings down, the baking tray slides out onto it, what bakes is laid on the
 * tray in the oven's 3 x 3 grid; the tray slides back, the door shuts and it bakes over the heat under it, as the oven always does.
 * The station only shows it and does by hand what its menu did.
 */
public final class OvenStation {
    public static final ResourceLocation ID = HandsOnDelight.id("oven");

    public static final String INGREDIENTS = "ingredients";
    /** What is baked waits in the oven's output slot. */
    public static final String SERVED = "served";
    public static final String PROGRESS = "progress";
    /**
     * Whether the door is open (1) or shut (0). The cook opens and shuts it with a click on it ({@link #ACTION_DOOR}); things go in
     * and come out only with it open, and the oven bakes only with it shut, as an oven keeps its heat.
     */
    public static final String DOOR = "door";
    public static final String ACTION_DOOR = "door";
    public static final String OPEN = "open";
    /** A custom action: a recipe picked in the book; its ingredients go in from the inventory. Data: {@link #DISH}, {@link #ALL}. */
    public static final String ACTION_FILL = "fill";
    public static final String DISH = "dish";
    public static final String ALL = "all";
    /**
     * A custom action: the stack in an inventory slot ({@link #FROM}) is laid on the tray, in the first place that has the same or
     * nothing: the item the cook came with, or one shift-clicked in the inventory.
     */
    public static final String ACTION_LAY = "lay";
    public static final String FROM = "from";
    /** What the logic tells the clients in the station's data: how long baking still takes, in ticks. */
    public static final String LEFT = "left";
    public static final SlotRef SERVED_SLOT = SlotRef.of(SERVED, 0);
    public static final Supplier<Block> OVEN = () -> BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(KitchenOven.MOD_ID, "oven"));

    public static final StationSchema SCHEMA = StationSchema.builder()
            .slots(INGREDIENTS, KitchenOven.INGREDIENTS, SlotRules.DEFAULT.kept())
            .slots(SERVED, 1, SlotRules.DEFAULT.kept().accepts(stack -> false))
            .control(PROGRESS, ControlSpec.stepped(0.0F, 1.0F, 100, 0.0F).locked())
            .control(DOOR, ControlSpec.stepped(0.0F, 1.0F, 1, 0.0F).locked())
            .build();

    /**
     * The oven's block is hidden while somebody works at it: the scene shows it with its door open. While nobody does, the block is the
     * oven and the scene shows only what lies on its tray: its block entity sends its content to the clients near it ({@code
     * OvenMixin}), and each of them reads it with its own copy of the logic.
     */
    public static final StationType TYPE = Diegesis.station(ID)
            .block(OVEN)
            .schema(SCHEMA)
            .logic(OvenLogic::new)
            // Worked from its door: the scene stands as the oven faces, whichever side the cook comes from.
            .orientation(StationType.Orientation.BLOCK)
            .openWhen(OvenStation::opens)
            .hidesBlock()
            .idleLogic()
            .build();

    private OvenStation() {
    }

    public static void register() {
        if (Platform.INSTANCE.isLoaded(KitchenOven.MOD_ID)) {
            Diegesis.register(TYPE);
        }
    }

    /** Sneaking keeps the oven's own menu. */
    private static boolean opens(Player player, BlockPos pos, BlockState state) {
        return !player.isSecondaryUseActive() && KitchenOven.of(player.level().getBlockEntity(pos)) != null;
    }
}
