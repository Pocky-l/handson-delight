package com.pockyl.handson_delight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.MeshShape;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.station.ChillerLogic;
import com.pockyl.handson_delight.station.ChillerStation;
import com.pockyl.handson_delight.station.KitchenChiller;

import java.util.List;
import java.util.Optional;

/**
 * What the chiller shows: an ice box whose three doors swing open one after another for the cook and shut behind the last one. In
 * the cold chamber the container stands on the zinc shelf with what sets in it, the liquid in it in its own colour, turning to the
 * colour of what it sets into; what has set hops up onto the wire rack. In the chest the piece of ice melting now shrinks before
 * the ice that waits; its water drips into the pan below, which slides out and is tipped over when the cook empties it. While the
 * ice melts, rime grows on the zinc and cold mist rolls out of the chamber, and out at the door edges of a shut box.
 */
public final class ChillerBehavior implements StationBehavior {
    private static final String[] BADGE_PARTS = {"badge", "dial_track", "dial", "cold", "pans", "preview"};
    private static final String[] DOORS = {"door_cold", "door_ice", "door_pan"};
    /** What the cook reaches only once the doors are open. */
    private static final String[] BEHIND_DOORS = {"container_slot", "place_0", "place_1", "place_2", "place_3", "served", "chamber",
            "chamber_floor", "ice", "chest", "chest_back", "pan_zone", "pan_back"};
    private static final ResourceLocation BADGE_TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "block/white_concrete");
    private static final Vector3f ICON_MIDDLE = new Vector3f(8.0F, 8.0F, 8.0F);
    private static final float ITEM_PIXELS = 8.0F;
    /** Upright things lean this far back to face the camera, degrees. */
    private static final float LEAN = 16.0F;

    /** Each door swings this long, the next one starting this much later; how far they open, degrees. */
    private static final float DOOR_TIME = 0.45F;
    private static final float DOOR_GAP = 0.18F;
    private static final float DOORS_OPEN = DOOR_TIME + 2.0F * DOOR_GAP;
    private static final float COLD_OPEN = 112.0F;
    private static final float SMALL_OPEN = -165.0F;

    /** The cold shelf's top, where the container stands, and the places of what sets, scene pixels. */
    private static final float SHELF = 6.0F;
    private static final float CONTAINER_X = 4.5F;
    private static final float CONTAINER_Z = 5.6F;
    private static final float CONTAINER_LIFT = 0.03F;
    /** The places of what sets: in a container that is a block, and round one that stands at the shelf's near right (a bowl, sticks). */
    private static final float[][] PLACES = {{5.5F, 4.4F}, {3.1F, 4.4F}, {5.5F, 6.9F}, {3.1F, 6.9F}};
    private static final float[][] PLACES_ROUND = {{6.1F, 4.2F}, {6.1F, 6.8F}, {3.3F, 8.0F}, {5.8F, 9.4F}};
    private static final float PLACE_Y = 6.7F;
    /** A container that is a block (a mold, a tray, a pan) is drawn this big at most, and this wide. */
    private static final float VESSEL_SCALE = 0.47F;
    private static final float VESSEL_WIDE = 6.6F;
    /** Our wooden bowl for what sets in a bowl: how big, and where it stands from the shelf's middle. */
    private static final ResourceLocation BOWL_MODEL = HandsOnDelight.id("diegesis/chiller/bowl");
    private static final float BOWL_SCALE = 0.52F;
    private static final float BOWL_X = -1.4F;
    private static final float BOWL_Z = -1.0F;
    /** Sticks and bottles stand this high in a row at the shelf's near right, this far apart; what is neither lies flat there. */
    private static final float STANDING = 3.0F;
    private static final float STANDING_X = -2.9F;
    private static final float STANDING_Z = -1.6F;
    private static final float STANDING_APART = 1.45F;
    private static final float STICK_TURN = -45.0F;
    private static final float STANDING_SUNK = 0.25F;
    private static final String[] CONTAINERS = {"container_model", "container_more", "container_third", "container_fourth"};
    private static final float THING = 2.4F;
    private static final float ARRIVE_ARC = 3.0F;
    private static final float ARRIVE_TIME = 0.35F;
    private static final float ARRIVE_APART = 0.09F;
    /** What has set lies on the rack, this big and this far in; how long it takes to hop there. */
    private static final float RACK = 12.0F;
    private static final float DISH = 3.6F;
    private static final float DISH_Z = 3.4F;
    private static final float DISH_LIFT = 0.15F;
    /** Several of what has set: from how many on a second and a third piece lie half on the first. */
    private static final int DISH_MORE = 2;
    private static final int DISH_MOST = 4;
    private static final float HOP_TIME = 0.45F;
    private static final float SETTLED = 0.4F;
    private static final float HAND_SIZE = 3.6F;
    private static final float DIP_SIZE = 3.2F;

    /**
     * The ice in the chest: a block of it is drawn this big, so that the piece that waits and the one melting stand side by side at
     * the front; the melting piece shrinks down to this share of it.
     */
    private static final float ICE_SIZE = 2.8F;
    private static final float ICE_LEAST = 0.3F;
    /** The pan slides out this far and tips this much, and how long each step takes. */
    private static final float PAN_OUT = -9.5F;
    private static final float PAN_TIP = -52.0F;
    private static final float PAN_SLIDE = 0.35F;
    private static final float PAN_TURN = 0.3F;
    private static final float PAN_HOLD = 0.35F;
    private static final int WATER_COLOR = 0xD05A8FE8;
    /** The cold mist: its colour, and how many puffs a second roll out of the open box and seep out of the shut one. */
    private static final int MIST_COLOR = 0x70FFFFFF;
    private static final float MIST_OPEN = 3.2F;
    private static final float MIST_SHUT = 1.6F;
    /** The rime: seconds to grow whole and to go, its opacity, the share of the growth its first layer takes and its second starts at. */
    private static final float FROST_GROWS = 9.0F;
    private static final float FROST_GOES = 14.0F;
    private static final int FROST_MOST = 235;
    private static final float RIME_EARLY = 0.4F;
    private static final float RIME_LATE = 0.3F;

    private static final int PLATE_COLOR = 0x8C1E140C;
    private static final int DIAL_COLOR = 0xFFFFCB6B;
    private static final int WAITING_COLOR = 0xFFA8A49C;
    private static final int TRACK_COLOR = 0x50FFF2DC;
    private static final int COLD_LIT = 0xFF8FD8FF;
    private static final int COLD_OUT = 0xFF8C8C8C;
    private static final int PAN_COLOR = 0xFF6FA8F0;
    private static final int PAN_FULL = 0xFFFF5A4A;
    private static final int BAR_LEFT = -7;
    private static final int BAR_TOP = 10;
    private static final int BAR_LENGTH = 14;
    private static final int TEXT_TOP = 13;
    private static final String[] COLON = {".", "#", ".", "#", "."};
    private static final String[] FLAKE = {"#.#", ".#.", "#.#"};
    private static final float DONE_SWELL = 0.25F;
    private static final float DONE_TIME = 0.5F;
    /** What is lacking shows in the badge's icon for this long out of every such while, seconds. */
    private static final float LACK_SHOWN = 0.8F;
    private static final float LACK_EVERY = 2.0F;

    private final RecipeBook book = new RecipeBook(new ChillerBook());
    private final UsableCache usable = new UsableCache();
    private float time;
    private float doors;
    /** The container drawn on the shelf, how many of it, how it holds the liquid and how big it is drawn. */
    private Item drawnContainer;
    private int drawnContainerCount = -1;
    private Holds holds = Holds.SHELF;
    private float vesselScale = VESSEL_SCALE;
    /** Whether the places of what sets lie round a container that stands at the shelf's near right. */
    private boolean round;
    private final Item[] drawnThings = new Item[KitchenChiller.PLACES];
    private final int[] lastCounts = new int[KitchenChiller.PLACES];
    /** Seconds until the thing that came into a place starts its flight from the cook; whether nothing lay there before. */
    private final float[] arriveIn = new float[KitchenChiller.PLACES];
    private final boolean[] arrivesNew = new boolean[KitchenChiller.PLACES];
    private float level;
    private float heldLevel;
    /** Seconds left in which the liquid's level follows the tank at once: what has set has just left it. */
    private float settled;
    private int shownColor = 0xFFFFFF;
    private Fluid colorOf;
    private int fluidColor;
    private String resultOf = "";
    private int resultColor;
    private KitchenMotions.Pour pour;
    private KitchenMotions.Dip dip;
    private int lastAmount = -1;
    private int lastServed = -1;
    private int lastIce = -1;
    private int lastTipped = -1;
    private int lastPan = -1;
    private Item drawnDish;
    private float hopping = -1.0F;
    private boolean hopsFirst;
    private Item drawnIce;
    private Item drawnMelting;
    private float meltShown = 1.0F;
    private float meltingScale = 1.0F;
    private float tipping = -1.0F;
    private float frostShown;
    private float mist;
    private float drops;
    private float glints;
    private float doneAt = -10.0F;
    private String drawnDial = "";
    private int drawnPans = -1;
    private Item shownIcon;

    public ChillerBehavior(StationView view) {
    }

    /**
     * How the container on the shelf holds the liquid: the tank node that shows it and, in the container's own pixels, where the
     * liquid's floor and its brim lie. A mold's brim is under its rim and its middle bar, so that the bar parts the two halves.
     */
    private enum Holds {
        /** Nothing holds it (no container, or sticks and bottles standing in it): it pools on the shelf, behind the lip. */
        SHELF("pool", 0.02F, 0.8F),
        MOLD("mold", 1.05F, 1.65F),
        TRAY("mold_tray", 1.05F, 1.65F),
        PAN("mold_pan", 1.05F, 3.4F),
        TIN("mold_tin", 0.1F, 1.6F),
        BOWL("mold_bowl", 1.05F, 2.75F);

        private final String node;
        private final float floor;
        private final float brim;

        Holds(String node, float floor, float brim) {
            this.node = node;
            this.floor = floor;
            this.brim = brim;
        }

        /** How {@code item} holds a liquid: Extra Delight's pans by their kind, a bowl as ours, any other block as a mold. */
        static Holds of(Item item) {
            if (item == Items.BOWL) {
                return BOWL;
            }
            if (blockExtent(item) <= 0.0F) {
                return SHELF;
            }
            String path = BuiltInRegistries.ITEM.getKey(item).getPath();
            return path.contains("muffin") ? TIN : path.contains("square_pan") ? PAN : path.contains("tray") ? TRAY : MOLD;
        }
    }

    private static KitchenChiller.Access access() {
        return KitchenChiller.access();
    }

    private static Fluid fluid(StationView view) {
        String id = view.state().data().getString(ChillerStation.FLUID);
        ResourceLocation key = id.isEmpty() ? null : ResourceLocation.tryParse(id);
        return key == null ? Fluids.EMPTY : BuiltInRegistries.FLUID.get(key);
    }

    /** The recipe the logic says is being set up. */
    private static Optional<KitchenChiller.Setting> wanted(StationView view) {
        String id = view.state().data().getString(ChillerStation.MAKING);
        if (id.isEmpty() || view.level() == null) {
            return Optional.empty();
        }
        return KitchenChiller.recipes(view.level()).stream().filter(recipe -> recipe.id().toString().equals(id)).findFirst();
    }

    @Override
    public void onSceneReady(StationView view) {
        drawnDial = "";
        drawnPans = -1;
        drawnContainer = null;
        drawnContainerCount = -1;
        drawnDish = null;
        drawnIce = null;
        drawnMelting = null;
        shownIcon = null;
        colorOf = null;
        java.util.Arrays.fill(drawnThings, null);
        view.node("dial").ifPresent(dial -> dial.setTint(DIAL_COLOR));
        view.node("dial_track").ifPresent(track -> track.setTint(TRACK_COLOR));
        view.node("badge").ifPresent(plate -> {
            plate.setTint(PLATE_COLOR);
            MeshShape.Builder shape = MeshShape.builder();
            PotBehavior.pixels(shape, -10, -16, 20, 35);
            PotBehavior.pixels(shape, -9, -17, 18, 1);
            PotBehavior.pixels(shape, -9, 19, 18, 1);
            plate.setShape(shape.build(), BADGE_TEXTURE, true);
        });
        view.node("cold").ifPresent(cold -> {
            MeshShape.Builder shape = MeshShape.builder();
            glyph(shape, FLAKE, -8, -14);
            cold.setShape(shape.build(), BADGE_TEXTURE, true);
        });
        for (String part : BADGE_PARTS) {
            view.node(part).ifPresent(node -> node.setOnTop(true));
        }
        view.node("book_open").ifPresent(node -> node.setPainter(book));
        StationState state = view.state();
        ItemStack[] places = state.getItems(ChillerStation.INGREDIENTS);
        for (int place = 0; place < KitchenChiller.PLACES; place++) {
            lastCounts[place] = place < places.length ? places[place].getCount() : 0;
            arriveIn[place] = 0.0F;
        }
        lastAmount = state.data().getInt(ChillerStation.AMOUNT);
        lastServed = state.getItem(ChillerStation.SERVED_SLOT).getCount();
        lastIce = state.getItem(ChillerStation.ICE_SLOT).getCount();
        lastTipped = state.data().getInt(ChillerStation.TIPPED);
        lastPan = state.data().getInt(ChillerStation.PAN);
        level = state.getTank(ChillerStation.LIQUID).amount() / 100.0F;
    }

    @Override
    public void onOpen(StationView view) {
        usable.clear();
        view.setInventoryFilter(stack -> usable.test(stack, ChillerBehavior::usable));
        book.opened(view);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && view.openedByUse() && player.inventoryMenu.getCarried().isEmpty() && usable(player.getMainHandItem())) {
            put(view, player.getInventory().selected, player.getMainHandItem(), !isLiquid(player.getMainHandItem()));
        }
    }

    @Override
    public void onClose(StationView view) {
        book.shut(view);
    }

    /** The doors are still swinging shut behind the last cook: the scene stands in for the block until they are. */
    @Override
    public boolean holdsBlock(StationView view) {
        return !view.isInUse() && doors > 0.0F;
    }

    /** What the chiller can use: ice, a container, a liquid, a thing some recipe sets, and the empty bottle or bucket that draws. */
    private static boolean usable(ItemStack stack) {
        KitchenChiller.Access access = access();
        Minecraft minecraft = Minecraft.getInstance();
        if (stack.isEmpty() || access == null || minecraft.level == null) {
            return false;
        }
        if (stack.is(Items.GLASS_BOTTLE) || stack.is(Items.BUCKET) || access.chillOf(stack) > 0 || access.liquidOf(stack).isPresent()) {
            return true;
        }
        List<KitchenChiller.Setting> recipes = KitchenChiller.recipes(minecraft.level);
        return KitchenChiller.isContainer(recipes, stack) || KitchenChiller.isThing(recipes, stack);
    }

    private static boolean isLiquid(ItemStack stack) {
        KitchenChiller.Access access = access();
        return access != null && access.liquidOf(stack).isPresent();
    }

    /**
     * The stack in inventory slot {@code slot} (-1: the cursor) goes where it belongs. An empty container that will draw the liquid
     * back is seen to dip into it at once.
     */
    private void put(StationView view, int slot, ItemStack stack, boolean all) {
        draws(view, stack).ifPresent(full -> dipInto(view, stack, full));
        CompoundTag how = new CompoundTag();
        how.putInt(ChillerStation.FROM, slot);
        how.putBoolean(ChillerStation.ALL, all);
        view.sendAction(ChillerStation.ACTION_PUT, how);
    }

    /** What {@code empty} comes up as when it draws the tank's liquid, as the logic will decide it; nothing when it will not. */
    private Optional<ItemStack> draws(StationView view, ItemStack empty) {
        KitchenChiller.Access access = access();
        int amount = view.state().data().getInt(ChillerStation.AMOUNT);
        Fluid fluid = fluid(view);
        if (access == null || amount <= 0 || fluid == Fluids.EMPTY || access.liquidOf(empty).isPresent() || access.chillOf(empty) > 0) {
            return Optional.empty();
        }
        StationState state = view.state();
        ItemStack rack = state.getItem(ChillerStation.CONTAINER_SLOT);
        List<KitchenChiller.Setting> recipes = KitchenChiller.recipes(view.level());
        if (KitchenChiller.isContainer(recipes, empty) && !ItemStack.isSameItem(rack, empty)
                && KitchenChiller.setsIn(recipes, state.getItems(ChillerStation.INGREDIENTS), fluid, amount, empty)) {
            return Optional.empty();
        }
        for (ItemStack full : access.containersOf(fluid)) {
            boolean bucket = full.is(fluid.getBucket());
            if (bucket ? empty.is(Items.BUCKET) && amount >= KitchenChiller.BUCKET
                    : amount >= KitchenChiller.BOTTLE && (full.getItem().getCraftingRemainingItem() == empty.getItem()
                            || full.is(Items.POTION) && empty.is(Items.GLASS_BOTTLE))) {
                return Optional.of(full);
            }
        }
        return Optional.empty();
    }

    private void dipInto(StationView view, ItemStack empty, ItemStack full) {
        Optional<NodeView> node = view.node("dipper");
        if (dip != null || node.isEmpty()) {
            return;
        }
        Vector3f over = liquidAt(surface() + 2.6F);
        Vector3f away = FromPlayer.point(view, SHELF + 8.0F, 0, 1).orElse(new Vector3f(8.0F, 14.0F, -10.0F));
        KitchenMotions.showItem(node.get(), empty.getItem(), DIP_SIZE, 0xFFFFFFFF);
        node.get().moveTo(over.x, over.y, over.z, 0.0F);
        node.get().rotateTo(0.0F, 0.0F, 0.0F, 0.0F);
        dip = new KitchenMotions.Dip(node.get(), full.getItem(), over, new Vector3f(), away, this::surface,
                view.node(holds.node).orElse(null), DIP_SIZE / ITEM_PIXELS, DIP_SIZE, 0xFF000000 | shownColor,
                () -> view.playSound(SoundEvents.BOTTLE_FILL, "container_slot", 0.45F, 0.9F));
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        StationState state = view.state();
        CompoundTag data = state.data();
        ItemStack[] places = state.getItems(ChillerStation.INGREDIENTS);
        int coming = 0;
        for (int place = 0; place < KitchenChiller.PLACES; place++) {
            int count = place < places.length ? places[place].getCount() : 0;
            if (count > lastCounts[place] && view.isFocused()) {
                arrivesNew[place] = lastCounts[place] == 0;
                arriveIn[place] = 0.02F + ARRIVE_APART * coming++;
            }
            lastCounts[place] = count;
        }
        ItemStack served = state.getItem(ChillerStation.SERVED_SLOT);
        if (lastServed >= 0 && served.getCount() > lastServed) {
            set(view, served.getItem(), lastServed == 0);
        }
        lastServed = served.getCount();
        int amount = data.getInt(ChillerStation.AMOUNT);
        if (lastAmount >= 0 && amount > lastAmount && view.isFocused()) {
            poured(view, amount - lastAmount);
        }
        lastAmount = amount;
        int ice = state.getItem(ChillerStation.ICE_SLOT).getCount();
        if (lastIce >= 0 && ice > lastIce) {
            view.playSound(SoundEvents.GLASS_PLACE, "ice", 0.3F, 1.5F);
        }
        lastIce = ice;
        int tipped = data.getInt(ChillerStation.TIPPED);
        int pan = data.getInt(ChillerStation.PAN);
        if (lastTipped >= 0 && tipped != lastTipped) {
            tip(view);
        } else if (lastPan >= 0 && pan <= lastPan - KitchenChiller.BOTTLE) {
            // A bucket or a bottle was filled from it.
            view.playSound(lastPan - pan >= KitchenChiller.BUCKET ? SoundEvents.BUCKET_FILL : SoundEvents.BOTTLE_FILL, "pan", 0.45F, 1.0F);
            view.node("melt").ifPresent(melt -> melt.ripple(0.0F, 0.0F, 0.3F));
        }
        lastTipped = tipped;
        lastPan = pan;
    }

    /** More liquid in the tank while the cook watches: it is poured from its container, a bucket for a bucketful, else a bottle. */
    private void poured(StationView view, int added) {
        KitchenChiller.Access access = access();
        Fluid fluid = fluid(view);
        if (access == null || fluid == Fluids.EMPTY) {
            return;
        }
        List<ItemStack> containers = access.containersOf(fluid);
        boolean bucketful = added >= KitchenChiller.BUCKET;
        Optional<ItemStack> container = containers.stream().filter(stack -> stack.is(fluid.getBucket()) == bucketful).findFirst()
                .or(() -> containers.stream().findFirst());
        Optional<NodeView> hand = view.node("hand_liquid");
        Optional<Vector3f> from = FromPlayer.point(view, SHELF + 8.0F, 0, 1);
        if (container.isEmpty() || hand.isEmpty() || from.isEmpty()) {
            return;
        }
        if (pour != null) {
            pour.stop();
        }
        heldLevel = level;
        pour = new KitchenMotions.Pour(hand.get(), "pour_chiller", container.get(), from.get(),
                liquidAt(0.0F), this::surface, holds.node, HAND_SIZE, -1.0F, 0.0F)
                .lipAtLeast(SHELF + 2.4F).colored(0xFF000000 | color(fluid));
    }

    /** Something has set: a knock on its container, it hops up turning over once to lie on the rack, and the badge swells with a chime. */
    private void set(StationView view, Item item, boolean first) {
        doneAt = time;
        settled = SETTLED;
        view.playSound(SoundEvents.COPPER_HIT, "container_slot", 0.35F, 1.4F);
        if (view.isFocused()) {
            view.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), "badge", 0.35F, 1.26F);
            view.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), "badge", 0.3F, 1.5F);
        }
        view.node("hop").ifPresent(node -> {
            KitchenMotions.showItem(node, item, DISH, 0xFFFFFFFF);
            Vector3f from = liquidAt(SHELF + 1.4F);
            node.moveTo(from.x, from.y, from.z, 0.0F);
            node.rotateTo(-LEAN, 0.0F, 0.0F, 0.0F);
            node.hopTo(CONTAINER_X, RACK + DISH_LIFT, DISH_Z, 1.6F, HOP_TIME);
            node.rotateTo(-450.0F, 0.0F, 0.0F, HOP_TIME);
        });
        hopping = 0.0F;
        hopsFirst = first;
    }

    /** The pan was tipped out: it slides out of the box, goes over its front edge with a splash, and slides back. */
    private void tip(StationView view) {
        tipping = 0.0F;
        view.node("melt").ifPresent(melt -> melt.holdLevel(PAN_SLIDE + PAN_TURN * 0.5F, 0.2F));
        view.node("pan").ifPresent(node -> node.moveTo(0.0F, 0.0F, PAN_OUT, PAN_SLIDE));
        view.playSound(SoundEvents.CHAIN_PLACE, "pan", 0.3F, 0.8F);
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (!(event instanceof SceneEvent.Click click)) {
            return;
        }
        String node = click.node();
        if (node.equals("book_open")) {
            book.click(view, click.button());
        } else if (click.button() == 0 && node.startsWith("pan_")) {
            // An empty hand: a bucket from the inventory takes a full pan, else the pan is tipped out.
            panAction(view, -2);
        } else if (click.button() == 0 && node.startsWith("chest")) {
            // An empty hand is offered the ice the cook carries.
            int slot = iceSlot();
            LocalPlayer player = Minecraft.getInstance().player;
            if (slot >= 0 && player != null) {
                put(view, slot, player.getInventory().getItem(slot), true);
            }
        }
    }

    private static int iceSlot() {
        LocalPlayer player = Minecraft.getInstance().player;
        KitchenChiller.Access access = access();
        if (player == null || access == null) {
            return -1;
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (access.chillOf(player.getInventory().getItem(slot)) > 0) {
                return slot;
            }
        }
        return -1;
    }

    private void panAction(StationView view, int from) {
        CompoundTag how = new CompoundTag();
        how.putInt(ChillerStation.FROM, from);
        view.sendAction(ChillerStation.ACTION_PAN, how);
    }

    /** Keys go to the book first: its search while it is typed, its page keys. */
    @Override
    public boolean onKey(StationView view, int keyCode, int scanCode, int modifiers) {
        return book.key(view, keyCode, modifiers);
    }

    @Override
    public boolean onChar(StationView view, char character, int modifiers) {
        return book.typed(view, character);
    }

    @Override
    public boolean onScroll(StationView view, String node, double amount) {
        return "book_open".equals(node) && book.scroll(amount);
    }

    @Override
    public boolean onInventoryShiftClick(StationView view, int slot, ItemStack stack) {
        if (!usable(stack)) {
            return false;
        }
        put(view, slot, stack, !isLiquid(stack));
        return true;
    }

    /**
     * A click with something on the cursor. At the pan a bucket or bottle is filled from it. A thing clicked onto a place lies
     * there, as the cook points. Anywhere else what the chiller can use goes where it belongs: a liquid one container at a time
     * (shift: all that fit), anything else the stack, or one with the right button.
     */
    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if ("book_open".equals(node)) {
            book.click(view, button);
            return true;
        }
        if (node.startsWith("pan_")) {
            if (view.state().data().getInt(ChillerStation.PAN) < KitchenChiller.BOTTLE) {
                view.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), "badge", 0.3F, 0.7F);
            } else {
                panAction(view, -1);
            }
            return true;
        }
        if (!usable(stack)) {
            return false;
        }
        if (node.startsWith("place_") && view.level() != null && KitchenChiller.isThing(KitchenChiller.recipes(view.level()), stack)) {
            return false;
        }
        put(view, -1, stack, isLiquid(stack) ? shift : button == 0 || shift);
        return true;
    }

    /** Where the liquid stands now, scene pixels: in the container that holds it, or on the shelf. */
    private float surface() {
        float over = holds.floor + (holds.brim - holds.floor) * level;
        return holds == Holds.SHELF ? SHELF + over : SHELF + CONTAINER_LIFT + vesselScale * over;
    }

    /** The middle of the liquid at height {@code y}: over the bowl that holds it, else over the middle of the shelf. */
    private Vector3f liquidAt(float y) {
        return holds == Holds.BOWL ? new Vector3f(CONTAINER_X + BOWL_X, y, CONTAINER_Z + BOWL_Z)
                : new Vector3f(CONTAINER_X, y, CONTAINER_Z - 0.5F);
    }

    @Override
    public void frame(StationView view, float dt) {
        time += dt;
        boolean inUse = view.isInUse();
        boolean whole = view.isBlockHidden();
        StationState state = view.state();
        int chill = state.data().getInt(ChillerStation.CHILL);
        float progress = state.getControl(ChillerStation.PROGRESS);
        doors(view, dt, inUse, whole);
        boolean open = inUse && doors >= DOORS_OPEN;
        container(view, whole);
        things(view, dt, whole);
        liquid(view, dt, whole, progress);
        ice(view, dt, whole, chill);
        pan(view, dt, whole);
        dish(view, dt, whole);
        frost(view, dt, whole, chill > 0);
        effects(view, dt, whole, chill > 0);
        if (pour != null && !pour.frame(view, dt)) {
            pour = null;
        }
        if (dip != null && !dip.frame(view, dt)) {
            dip = null;
        }
        for (String id : BEHIND_DOORS) {
            view.node(id).ifPresent(node -> node.setEnabled(open));
        }
        book.frame(view, dt);
        float appear = RecipeBook.appear(view);
        badge(view, inUse && RecipeBook.isWorkedAt(view) && appear > RecipeBook.SPECK, progress, chill);
        float since = time - doneAt;
        float swell = since >= 0.0F && since < DONE_TIME ? 1.0F + DONE_SWELL * Mth.sin(Mth.PI * since / DONE_TIME) : 1.0F;
        for (String part : BADGE_PARTS) {
            view.node(part).ifPresent(node -> node.scaleTo(appear * swell, 0.0F));
        }
    }

    /** The doors open one after another while somebody works here, and shut one after another behind the last cook. */
    private void doors(StationView view, float dt, boolean inUse, boolean whole) {
        float before = doors;
        doors = Mth.clamp(doors + (inUse ? dt : -dt), 0.0F, DOORS_OPEN);
        for (int door = 0; door < DOORS.length; door++) {
            float start = door * DOOR_GAP;
            float angle = (door == 0 ? COLD_OPEN : SMALL_OPEN) * KitchenMotions.smooth(Mth.clamp((doors - start) / DOOR_TIME, 0.0F, 1.0F));
            String id = DOORS[door];
            view.node(id).ifPresent(node -> {
                node.setVisible(whole);
                node.rotateTo(0.0F, angle, 0.0F, 0.0F);
            });
            if (before <= start && doors > start) {
                view.playSound(SoundEvents.WOODEN_TRAPDOOR_OPEN, id, 0.3F, 1.0F + 0.12F * door);
            } else if (before > start && doors <= start) {
                view.playSound(SoundEvents.WOODEN_TRAPDOOR_CLOSE, id, 0.3F, 1.0F + 0.12F * door);
            }
        }
        view.node("body").ifPresent(node -> node.setVisible(whole));
    }

    /**
     * The container on the shelf. A mold, a tray or a pan is its own block, sized to the shelf, and a bowl our wooden one: the
     * liquid stands in them. Sticks and bottles stand in a row at the shelf's near right, in the liquid that pools on the shelf;
     * anything else (a pie crust) lies flat there.
     */
    private void container(StationView view, boolean whole) {
        ItemStack rack = view.state().getItem(ChillerStation.CONTAINER_SLOT);
        Item item = rack.isEmpty() ? null : rack.getItem();
        int count = Math.min(CONTAINERS.length, rack.getCount());
        if (item != drawnContainer || count != drawnContainerCount) {
            drawnContainer = item;
            drawnContainerCount = count;
            holds = item == null ? Holds.SHELF : Holds.of(item);
            round = item != null && (holds == Holds.SHELF || holds == Holds.BOWL);
            if (holds == Holds.BOWL) {
                vesselScale = BOWL_SCALE;
                view.node(CONTAINERS[0]).ifPresent(node -> {
                    node.setVisual(Visual.ofModel(BOWL_MODEL).asLitAsBlock());
                    node.rotateTo(0.0F, 0.0F, 0.0F, 0.0F);
                    node.scaleTo(BOWL_SCALE, 0.0F);
                    node.moveTo(BOWL_X, CONTAINER_LIFT, BOWL_Z, 0.0F);
                });
            } else if (holds != Holds.SHELF) {
                vesselScale = Math.min(VESSEL_SCALE, VESSEL_WIDE / blockExtent(item));
                view.node(CONTAINERS[0]).ifPresent(node -> {
                    node.setVisual(Visual.ofBlock(((BlockItem) item).getBlock().defaultBlockState()));
                    node.rotateTo(0.0F, 0.0F, 0.0F, 0.0F);
                    node.scaleTo(vesselScale, 0.0F);
                    node.moveTo(0.0F, CONTAINER_LIFT, 0.0F, 0.0F);
                });
            } else if (item != null) {
                boolean stands = item == Items.STICK || item == Items.GLASS_BOTTLE;
                for (int index = 0; index < CONTAINERS.length; index++) {
                    float x = STANDING_X + STANDING_APART * index * (item == Items.STICK ? 1.0F : 1.3F);
                    // Every other one stands a little further back, as things put down by hand do.
                    float z = STANDING_Z + (index % 2 == 0 ? 0.0F : 0.5F);
                    view.node(CONTAINERS[index]).ifPresent(node -> {
                        if (stands) {
                            stand(node, item, x, z, item == Items.STICK ? STICK_TURN : 0.0F);
                        } else {
                            lay(node, item, x + 1.2F, 0.08F, z, 0.0F);
                            node.scaleTo(STANDING / ITEM_PIXELS, 0.0F);
                        }
                    });
                }
            }
            // The places of what sets move out of the way of a container that stands at the shelf's near right.
            for (int place = 0; place < KitchenChiller.PLACES; place++) {
                float x = places()[place][0] - PLACES[place][0];
                float z = places()[place][1] - PLACES[place][1];
                view.node("place_" + place).ifPresent(node -> node.moveTo(x, 0.0F, z, 0.0F));
            }
        }
        boolean rows = holds == Holds.SHELF && (item == Items.STICK || item == Items.GLASS_BOTTLE);
        for (int index = 0; index < CONTAINERS.length; index++) {
            boolean shown = whole && item != null && (index == 0 || rows && index < drawnContainerCount);
            view.node(CONTAINERS[index]).ifPresent(node -> node.setVisible(shown));
        }
    }

    /** Where the places of what sets lie now. */
    private float[][] places() {
        return round ? PLACES_ROUND : PLACES;
    }

    /**
     * Stands a stick or a bottle on the shelf, leant back to the camera, its foot in the liquid there; {@code turn} sets an icon
     * that lies on its diagonal upright.
     */
    private static void stand(NodeView node, Item item, float x, float z, float turn) {
        node.setVisual(Visual.ofItem(item).withAnchor(ICON_MIDDLE));
        node.rotateTo(-LEAN, 0.0F, turn, 0.0F);
        node.scaleTo(STANDING / ITEM_PIXELS, 0.0F);
        node.moveTo(x, STANDING / 2.0F - STANDING_SUNK, z, 0.0F);
    }

    /** How wide the block of {@code item} is, pixels; 0 for what is no block with a model and a shape. */
    private static float blockExtent(Item item) {
        if (!(item instanceof BlockItem blockItem)) {
            return 0.0F;
        }
        BlockState state = blockItem.getBlock().defaultBlockState();
        if (state.getRenderShape() != RenderShape.MODEL) {
            return 0.0F;
        }
        VoxelShape shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        if (shape.isEmpty()) {
            return 0.0F;
        }
        AABB bounds = shape.bounds();
        return (float) Math.max(bounds.getXsize(), bounds.getZsize()) * 16.0F;
    }

    /**
     * What sets lies in its places, a second one of a stack half on the first. What comes in while the cook watches flies in from
     * the cook, one after another.
     */
    private void things(StationView view, float dt, boolean whole) {
        ItemStack[] places = view.state().getItems(ChillerStation.INGREDIENTS);
        for (int place = 0; place < KitchenChiller.PLACES; place++) {
            ItemStack stack = place < places.length ? places[place] : ItemStack.EMPTY;
            Item item = stack.isEmpty() ? null : stack.getItem();
            int index = place;
            if (item != drawnThings[place]) {
                drawnThings[place] = item;
                if (item != null) {
                    view.node("thing_" + place).ifPresent(node -> lay(node, item, 0.0F, 0.05F, 0.0F, 20.0F * index));
                    view.node("pile_" + place).ifPresent(node -> lay(node, item, 0.45F, 0.27F, -0.35F, 20.0F * index + 35.0F));
                }
            }
            boolean waits = arriveIn[place] > 0.0F;
            if (waits) {
                arriveIn[place] -= dt;
                if (arriveIn[place] <= 0.0F && item != null) {
                    arrive(view, place);
                    waits = false;
                }
            }
            boolean shown = whole && item != null && !(waits && arrivesNew[place]);
            view.node("thing_" + place).ifPresent(node -> node.setVisible(shown));
            view.node("pile_" + place).ifPresent(node -> node.setVisible(shown && stack.getCount() > 1));
        }
    }

    private static void lay(NodeView node, Item item, float x, float y, float z, float turn) {
        node.setVisual(Visual.ofItem(item).withAnchor(ICON_MIDDLE));
        node.rotateTo(-90.0F, 0.0F, turn, 0.0F);
        node.scaleTo(THING / ITEM_PIXELS, 0.0F);
        node.moveTo(x, y, z, 0.0F);
    }

    /** The thing that came into {@code place} hops there from the cook. */
    private void arrive(StationView view, int place) {
        Optional<Vector3f> from = FromPlayer.point(view, SHELF + 8.0F, place, KitchenChiller.PLACES);
        Optional<NodeView> thing = view.node("thing_" + place);
        if (from.isEmpty() || thing.isEmpty()) {
            return;
        }
        thing.get().moveTo(from.get().x - places()[place][0], from.get().y - PLACE_Y, from.get().z - places()[place][1], 0.0F);
        thing.get().hopTo(0.0F, 0.05F, 0.0F, ARRIVE_ARC, ARRIVE_TIME);
        view.playSound(SoundEvents.COPPER_STEP, "container_slot", 0.25F, 1.5F);
    }

    /**
     * The liquid, in the container that holds it or on the shelf: as full as one setting takes, in the fluid's own colour, turning
     * to the colour of what it sets into and paling with the frost as it sets.
     */
    private void liquid(StationView view, float dt, boolean whole, float progress) {
        float wanted = view.state().getTank(ChillerStation.LIQUID).amount() / 100.0F;
        // What is being poured has not run in yet: the level waits for the stream.
        float target = pour != null && !pour.hasFlowed() ? Math.min(wanted, heldLevel) : wanted;
        // What has just set is no liquid any more: it went out with the piece that hopped onto the rack, at once.
        settled = Math.max(0.0F, settled - dt);
        level = settled > 0.0F ? target : Mth.lerp(Math.min(1.0F, dt * 4.0F), level, target);
        Fluid fluid = fluid(view);
        if (fluid != colorOf) {
            colorOf = fluid;
            // The last of a liquid keeps its colour while it goes.
            if (fluid != Fluids.EMPTY) {
                fluidColor = color(fluid);
            }
        }
        String making = view.state().data().getString(ChillerStation.MAKING);
        if (!making.equals(resultOf)) {
            resultOf = making;
            resultColor = wanted(view).map(recipe -> FoodColors.of(recipe.result()) & 0xFFFFFF).orElse(fluidColor);
        }
        float setting = KitchenMotions.smooth(progress);
        int mixed = KitchenLooks.mix(0xFF000000 | fluidColor, 0xFF000000 | resultColor, 0.7F * setting);
        int color = KitchenLooks.mix(mixed, 0xFFFFFFFF, 0.12F * setting) & 0xFFFFFF;
        shownColor = color;
        float shown = target;
        for (Holds kind : Holds.values()) {
            view.node(kind.node).ifPresent(tank -> {
                tank.setLiquidColor(0xFF000000 | color);
                tank.showLiquid(ChillerLogic.SYRUP_LIQUID, kind == holds ? shown : 0.0F);
                tank.setVisible(whole && kind == holds);
            });
        }
    }

    /** A fluid's colour: its tint, or for an untinted one (Extra Delight's syrups) the colour of its own texture. */
    private static int color(Fluid fluid) {
        if (fluid == Fluids.EMPTY) {
            return 0xF4F0E8;
        }
        int tint = ClientPlatform.INSTANCE.fluidColor(fluid);
        if (tint != 0) {
            return tint & 0xFFFFFF;
        }
        ResourceLocation texture = ClientPlatform.INSTANCE.fluidTexture(fluid);
        int drawn = texture == null ? 0 : FoodColors.ofTexture(texture);
        if (drawn != 0) {
            return drawn & 0xFFFFFF;
        }
        KitchenChiller.Access access = access();
        List<ItemStack> containers = access == null ? List.of() : access.containersOf(fluid);
        return containers.isEmpty() ? 0xF4F0E8 : KitchenLooks.liquidColor(containers.get(0)) & 0xFFFFFF;
    }

    /** The ice that waits stands at the front of the chest; the piece melting now lies beside it and shrinks as its cold goes. */
    private void ice(StationView view, float dt, boolean whole, int chill) {
        ItemStack waiting = view.state().getItem(ChillerStation.ICE_SLOT);
        Item item = waiting.isEmpty() ? null : waiting.getItem();
        if (item != drawnIce) {
            drawnIce = item;
            if (item != null) {
                view.node("ice_waiting").ifPresent(node -> piece(node, item));
                view.node("ice_more").ifPresent(node -> {
                    piece(node, item);
                    if (blockExtent(item) > 0.0F) {
                        // A second block lies on the first, turned a little.
                        node.moveTo(0.1F, ICE_SIZE, -0.1F, 0.0F);
                        node.rotateTo(0.0F, 14.0F, 0.0F, 0.0F);
                    } else {
                        node.moveTo(0.9F, ICE_SIZE * 0.4F, 0.9F, 0.0F);
                    }
                });
            }
        }
        view.node("ice_waiting").ifPresent(node -> node.setVisible(whole && item != null));
        view.node("ice_more").ifPresent(node -> node.setVisible(whole && item != null && waiting.getCount() > 1));
        Item melting = chill > 0 ? meltingIce(view.state().data().getInt(ChillerStation.CHILL_TOTAL), item) : null;
        if (melting != drawnMelting) {
            drawnMelting = melting;
            meltShown = 1.0F;
            if (melting != null) {
                view.node("ice_melting").ifPresent(node -> meltingScale = piece(node, melting));
            }
        }
        meltShown = Mth.lerp(Math.min(1.0F, dt * 3.0F), meltShown, chill / (float) ChillerLogic.CHILL_STEPS);
        view.node("ice_melting").ifPresent(node -> {
            node.setVisible(whole && melting != null);
            node.scaleTo(meltingScale * (ICE_LEAST + (1.0F - ICE_LEAST) * meltShown), 0.0F);
        });
    }

    /** Shows a piece of ice at {@code node}: a block of it as a small block, anything else as its item standing; its scale. */
    private static float piece(NodeView node, Item item) {
        if (blockExtent(item) > 0.0F) {
            node.setVisual(Visual.ofBlock(((BlockItem) item).getBlock().defaultBlockState()));
            node.rotateTo(0.0F, 0.0F, 0.0F, 0.0F);
            node.moveTo(0.0F, 0.0F, 0.0F, 0.0F);
            node.scaleTo(ICE_SIZE / 16.0F, 0.0F);
            return ICE_SIZE / 16.0F;
        }
        node.setVisual(Visual.ofItem(item).withAnchor(ICON_MIDDLE));
        node.rotateTo(-LEAN, 0.0F, 0.0F, 0.0F);
        node.moveTo(0.0F, ICE_SIZE * 0.4F, 0.0F, 0.0F);
        node.scaleTo(ICE_SIZE * 0.8F / ITEM_PIXELS, 0.0F);
        return ICE_SIZE * 0.8F / ITEM_PIXELS;
    }

    /** The kind of ice whose piece lasts {@code ticks}: the one melting now; else the kind that waits, else plain ice. */
    private static Item meltingIce(int ticks, Item waiting) {
        KitchenChiller.Access access = access();
        if (access != null) {
            for (ItemStack ice : access.ices()) {
                if (access.chillOf(ice) == ticks) {
                    return ice.getItem();
                }
            }
        }
        return waiting != null ? waiting : Items.ICE;
    }

    /** The drip pan, and while it is tipped out its way out of the box, over its front edge and back. */
    private void pan(StationView view, float dt, boolean whole) {
        view.node("pan").ifPresent(node -> node.setVisible(whole));
        view.node("melt").ifPresent(node -> node.setVisible(whole));
        if (tipping < 0.0F) {
            return;
        }
        float before = tipping;
        tipping += dt;
        float over = PAN_SLIDE;
        float back = over + PAN_TURN + PAN_HOLD;
        float in = back + PAN_TURN;
        Optional<NodeView> pan = view.node("pan");
        if (before < over && tipping >= over) {
            pan.ifPresent(node -> node.rotateTo(PAN_TIP, 0.0F, 0.0F, PAN_TURN));
            view.playSound(SoundEvents.BUCKET_EMPTY, "pan", 0.4F, 1.1F);
            view.spawnParticles(DiegesisParticles.SPLASH, new Vector3f(12.0F, 1.4F, -6.5F), new Vector3f(0.0F, -1.0F, -2.0F), 1.0F, 12,
                    WATER_COLOR);
            view.spawnParticles(DiegesisParticles.DROPLET, new Vector3f(12.0F, 1.8F, -6.0F), new Vector3f(0.0F, -2.0F, -1.5F), 1.2F, 8,
                    WATER_COLOR);
        }
        if (before < back && tipping >= back) {
            pan.ifPresent(node -> node.rotateTo(0.0F, 0.0F, 0.0F, PAN_TURN));
        }
        if (before < in && tipping >= in) {
            pan.ifPresent(node -> node.moveTo(0.0F, 0.0F, 0.0F, PAN_SLIDE));
            tipping = -1.0F;
        }
    }

    /** What has set lies on the rack, where the cook sees it under the box's top; a new one is first seen hopping there. */
    private void dish(StationView view, float dt, boolean whole) {
        ItemStack served = view.state().getItem(ChillerStation.SERVED_SLOT);
        Item item = served.isEmpty() ? null : served.getItem();
        if (item != drawnDish) {
            drawnDish = item;
            if (item != null) {
                view.node("dish").ifPresent(node -> flat(node, item, 0.0F, DISH_LIFT, 0.0F, 0.0F));
                view.node("dish_more").ifPresent(node -> flat(node, item, -1.6F, DISH_LIFT + 0.14F, 0.5F, 28.0F));
                view.node("dish_most").ifPresent(node -> flat(node, item, 1.5F, DISH_LIFT + 0.28F, 0.8F, -22.0F));
            }
        }
        if (hopping >= 0.0F) {
            hopping += dt;
            if (hopping >= HOP_TIME) {
                hopping = -1.0F;
                view.node("hop").ifPresent(node -> node.setVisible(false));
            }
        }
        boolean hidden = hopping >= 0.0F && hopsFirst;
        boolean lies = whole && item != null && !hidden;
        view.node("dish").ifPresent(node -> node.setVisible(lies));
        view.node("dish_more").ifPresent(node -> node.setVisible(lies && served.getCount() >= DISH_MORE));
        view.node("dish_most").ifPresent(node -> node.setVisible(lies && served.getCount() >= DISH_MOST));
        view.node("hop").ifPresent(node -> node.setVisible(whole && hopping >= 0.0F));
    }

    /** Lays a piece of what has set flat on the rack, turned by {@code turn}. */
    private static void flat(NodeView node, Item item, float x, float y, float z, float turn) {
        node.setVisual(Visual.ofItem(item).withAnchor(ICON_MIDDLE));
        node.rotateTo(-90.0F, 0.0F, turn, 0.0F);
        node.scaleTo(DISH / ITEM_PIXELS, 0.0F);
        node.moveTo(x, y, z, 0.0F);
    }

    /**
     * Rime grows on the zinc while the ice melts, first along the walls' feet and in their corners, then higher; it goes more
     * slowly once the ice is gone.
     */
    private void frost(StationView view, float dt, boolean whole, boolean cold) {
        frostShown = Mth.clamp(frostShown + (cold ? dt / FROST_GROWS : -dt / FROST_GOES), 0.0F, 1.0F);
        rime(view, "rime_early", whole, frostShown / RIME_EARLY);
        rime(view, "rime_late", whole, (frostShown - RIME_LATE) / (1.0F - RIME_LATE));
    }

    private static void rime(StationView view, String id, boolean whole, float grown) {
        int alpha = Math.round(FROST_MOST * KitchenMotions.smooth(Mth.clamp(grown, 0.0F, 1.0F)));
        view.node(id).ifPresent(node -> {
            node.setTint(alpha << 24 | 0xFFFFFF);
            node.setVisible(whole && alpha > 3);
        });
    }

    /**
     * While the ice melts. With the doors open cold mist rolls out of the chamber and down its front, melt water drips from the ice
     * shelf into the pan, and the rime glints now and then. With the doors shut the mist seeps out at their edges and a drop falls
     * under the box now and then.
     */
    private void effects(StationView view, float dt, boolean whole, boolean cold) {
        if (!cold || view.level() == null) {
            return;
        }
        RandomSource random = view.level().random;
        boolean open = whole && doors >= DOORS_OPEN;
        if (open || !whole) {
            mist += dt * (open ? MIST_OPEN : MIST_SHUT);
        }
        while (mist >= 1.0F) {
            mist -= 1.0F;
            if (open && random.nextInt(4) == 0) {
                // A wisp off the ice.
                view.spawnParticles(HandsOnDelightClient.MIST, new Vector3f(9.6F + random.nextFloat() * 4.8F,
                        9.6F + random.nextFloat() * 2.0F, 1.4F),
                        new Vector3f(0.0F, -0.3F, -1.2F), 0.25F, 1, MIST_COLOR);
            } else if (open) {
                // Out of the cold chamber's mouth: it rolls over the lip and sinks down the front of the box.
                view.spawnParticles(HandsOnDelightClient.MIST, new Vector3f(1.6F + random.nextFloat() * 5.8F,
                        7.2F + random.nextFloat() * 6.5F, 1.4F),
                        new Vector3f(0.0F, -0.4F, -1.8F), 0.3F, 1, MIST_COLOR);
            } else {
                float edge = switch (random.nextInt(3)) {
                    case 0 -> 1.2F;
                    case 1 -> 8.5F;
                    default -> 14.8F;
                };
                view.spawnParticles(HandsOnDelightClient.MIST, new Vector3f(edge, 2.5F + random.nextFloat() * 12.0F, -0.3F),
                        new Vector3f(0.0F, -0.3F, -0.6F), 0.2F, 1, MIST_COLOR);
            }
        }
        drops += dt * (open ? 1.4F : whole ? 0.0F : 0.4F);
        if (drops >= 1.0F) {
            drops -= 1.0F;
            if (open) {
                float x = 10.6F + random.nextFloat() * 2.8F;
                float z = 4.0F + random.nextFloat() * 7.0F;
                view.spawnParticles(DiegesisParticles.DROPLET, new Vector3f(x, 7.9F, z), new Vector3f(0.0F, -2.0F, 0.0F), 0.0F, 1,
                        WATER_COLOR);
                view.node("melt").ifPresent(melt -> melt.ripple(x - 12.0F, z - 8.0F, 0.12F));
            } else {
                view.spawnParticles(DiegesisParticles.DROPLET, new Vector3f(2.0F + random.nextFloat() * 12.0F, 0.95F,
                        2.0F + random.nextFloat() * 12.0F),
                        new Vector3f(0.0F, -1.0F, 0.0F), 0.0F, 1, WATER_COLOR);
            }
        }
        if (open) {
            glints += dt * 0.45F * frostShown;
            if (glints >= 1.0F) {
                glints -= 1.0F;
                // On the rime the cook sees: the rack's far wires.
                view.spawnParticles(DiegesisParticles.GLINT, new Vector3f(1.6F + random.nextFloat() * 5.8F,
                        RACK + 0.3F, 7.0F + random.nextFloat() * 7.0F),
                        new Vector3f(), 0.1F, 1, 0xD0E8F6FF);
            }
        }
    }

    /**
     * The badge while something is set up: what it sets into, how far, the seconds left (grey while it waits for cold), a snowflake
     * lit while ice melts and blinking while what is set up waits for ice, and the drip pan in three cells, red and blinking once it
     * is full and stops the cold. What is still missing (the container, the liquid, ice) shows in the icon now and then.
     */
    private void badge(StationView view, boolean cookHere, float progress, int chill) {
        StationState state = view.state();
        CompoundTag data = state.data();
        Optional<KitchenChiller.Setting> recipe = wanted(view);
        boolean sets = data.getBoolean(ChillerStation.SETS);
        boolean cold = chill > 0;
        boolean full = data.getInt(ChillerStation.PAN) >= KitchenChiller.PAN;
        ItemStack dish = recipe.map(KitchenChiller.Setting::result).orElse(ItemStack.EMPTY);
        boolean shown = cookHere && !dish.isEmpty();
        ItemStack lacking = shown ? lacking(view, recipe.get(), sets, cold) : ItemStack.EMPTY;
        boolean blink = Mth.sin(time * 6.0F) > 0.0F;
        boolean running = sets && cold && !full;
        view.node("badge").ifPresent(plate -> plate.setVisible(shown));
        int left = data.getInt(ChillerStation.LEFT);
        int seconds = sets && left > 0 ? Math.max(1, Mth.ceil(left / 20.0F)) : 0;
        int bar = Math.round(progress * BAR_LENGTH);
        String text = seconds >= 60 ? seconds / 60 + ":" + String.format("%02d", seconds % 60) : seconds > 0 ? Integer.toString(seconds)
                : "";
        String dial = bar + "/" + text;
        if (!dial.equals(drawnDial)) {
            drawnDial = dial;
            MeshShape.Builder track = MeshShape.builder();
            PotBehavior.pixels(track, BAR_LEFT, BAR_TOP, BAR_LENGTH, 1);
            view.node("dial_track").ifPresent(node -> node.setShape(track.build(), BADGE_TEXTURE, true));
            MeshShape.Builder lit = MeshShape.builder();
            if (bar > 0) {
                PotBehavior.pixels(lit, BAR_LEFT, BAR_TOP, bar, 1);
            }
            int width = -1;
            for (char c : text.toCharArray()) {
                width += (c == ':' ? 1 : 3) + 1;
            }
            int x = -width / 2 - width % 2;
            for (char c : text.toCharArray()) {
                String[] glyph = c == ':' ? COLON : PotBehavior.DIGITS[c - '0'];
                glyph(lit, glyph, x, TEXT_TOP);
                x += glyph[0].length() + 1;
            }
            view.node("dial").ifPresent(node -> node.setShape(bar > 0 || !text.isEmpty() ? lit.build() : null, BADGE_TEXTURE, true));
        }
        view.node("dial").ifPresent(node -> {
            node.setTint(running ? DIAL_COLOR : WAITING_COLOR);
            node.setVisible(shown);
        });
        view.node("dial_track").ifPresent(node -> node.setVisible(shown));
        view.node("cold").ifPresent(node -> {
            node.setTint(cold ? COLD_LIT : COLD_OUT);
            node.setVisible(shown && (cold || !sets || blink));
        });
        int thirds = full ? 3 : Math.min(2, data.getInt(ChillerStation.PAN) * 3 / KitchenChiller.PAN);
        if (thirds != drawnPans) {
            drawnPans = thirds;
            MeshShape.Builder cells = MeshShape.builder();
            for (int cell = 0; cell < 3; cell++) {
                // A filled cell stands three pixels high, an empty one is its floor.
                PotBehavior.pixels(cells, 1 + cell * 3, cell < thirds ? -14 : -12, 2, cell < thirds ? 3 : 1);
            }
            view.node("pans").ifPresent(node -> node.setShape(cells.build(), BADGE_TEXTURE, true));
        }
        view.node("pans").ifPresent(node -> {
            node.setTint(full ? PAN_FULL : PAN_COLOR);
            node.setVisible(shown && (!full || blink));
        });
        boolean showsLack = !lacking.isEmpty() && time % LACK_EVERY < LACK_SHOWN;
        ItemStack icon = showsLack ? lacking : dish;
        view.node("preview").ifPresent(node -> {
            node.setVisible(shown);
            if (!shown) {
                return;
            }
            if (shownIcon != icon.getItem()) {
                shownIcon = icon.getItem();
                node.setVisual(Visual.ofItem(shownIcon).withAnchor(ICON_MIDDLE));
            }
            Component name = dish.getHoverName();
            node.setTooltip(full ? Component.translatable("handson_delight.chiller.pan_full", name)
                    : !lacking.isEmpty() ? Component.translatable("handson_delight.chiller.lacks", name, lacking.getHoverName())
                    : seconds > 0 ? Component.translatable("handson_delight.pot.cooking", name, seconds) : name);
        });
    }

    /** What {@code recipe} still lacks to set: its container, then its liquid, then ice. Nothing when all is there. */
    private static ItemStack lacking(StationView view, KitchenChiller.Setting recipe, boolean sets, boolean cold) {
        StationState state = view.state();
        if (!sets) {
            if (!recipe.container().isEmpty() && !recipe.stands(state.getItem(ChillerStation.CONTAINER_SLOT))) {
                return recipe.container();
            }
            KitchenChiller.Access access = access();
            if (recipe.wantsLiquid() && access != null && !recipe.takes(fluid(view), state.data().getInt(ChillerStation.AMOUNT))) {
                List<ItemStack> containers = access.containersOf(recipe.fluid());
                return containers.isEmpty() ? ItemStack.EMPTY : containers.get(0);
            }
            return ItemStack.EMPTY;
        }
        return cold || !state.getItem(ChillerStation.ICE_SLOT).isEmpty() ? ItemStack.EMPTY : new ItemStack(Items.ICE);
    }

    private static void glyph(MeshShape.Builder shape, String[] rows, int left, int top) {
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < rows[row].length(); column++) {
                if (rows[row].charAt(column) == '#') {
                    PotBehavior.pixels(shape, left + column, top + row, 1, 1);
                }
            }
        }
    }
}
