package com.pockyl.handson_delight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
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
import com.pockyl.handson_delight.station.KettleLogic;
import com.pockyl.handson_delight.station.KettleStation;
import com.pockyl.handson_delight.station.KitchenKettle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * What the kettle shows while somebody works at it: its lid off on the stove beside it or on, the drink inside seen through the
 * opening, the leaves steeping in it and giving it their colour, steam from the spout while it is hot. A cup is filled at the spout:
 * the kettle is lifted by its handle and tipped over it. Done, it whistles with a jet of steam, also to whoever passes by. Farmer's
 * Respite's kettle and Youkai's Feasts' are drawn alike; Youkai's Feasts' boils its water before it brews: the water warms and
 * comes to the boil, and boiled with nothing to brew, the kettle whistles.
 */
public final class KettleBehavior implements StationBehavior {
    private static final String[] ITEM_NODES = {"item_0", "item_1", "item_2", "item_3"};
    /** The badge over the kettle: its plate, the bar, the flame and the icon. */
    private static final String[] BADGE_PARTS = {"badge", "dial_track", "dial", "flame", "flame_lit", "preview"};
    private static final String[] SHOWN_IN_USE = {"kettle", "pot", "lid", "drink", "spout", "mouth"};
    @SuppressWarnings("removal")
    private static final ResourceLocation BADGE_TEXTURE = ResourceLocation.withDefaultNamespace("block/white_concrete");
    /** Farmer's Respite's own whistle for its kettle; this mod's for a kettle whose mod has none (Youkai's Feasts'). */
    private static final SoundEvent WHISTLE = SoundEvent.createVariableRangeEvent(ResourceLocation.tryBuild(KettleStation.MOD_ID, "block.kettle.whistle"));
    private static final SoundEvent OWN_WHISTLE = SoundEvent.createVariableRangeEvent(HandsOnDelight.id("kettle.whistle"));

    /**
     * The kettle's inside, in pixels of its block: the floor, where the drink stands when the kettle is full, the opening's top. The
     * front bottom edge it tips round is 5 px in front of its middle.
     */
    private static final float FLOOR = 1.0F;
    private static final float FULL = 5.5F;
    private static final float TOP = 7.0F;
    private static final float PIVOT = -5.0F;
    /** The kettle's middle, which it is turned, tipped and made smaller about while it is in the hand. */
    private static final Vector3f MIDDLE = new Vector3f(0.0F, 3.5F, 0.0F);
    /** Where a drink leaves the spout, its lip, from the kettle's middle (its own frame, the spout to -z). */
    private static final Vector3f LIP = new Vector3f(0.0F, 6.2F, -7.9F);
    /**
     * Pouring, as a cartoon: the kettle hops off the stove into the cook's hand, up and out towards them (pixels), shrinks to a size
     * that sits in a hand, leans back a little as it is picked up and is tipped over the cup (degrees); set down, it bounces.
     */
    private static final float LIFT = 7.0F;
    /** How far the kettle is brought towards the cook in the hand, pixels, level: it comes closer to be poured. */
    private static final float HELD_CLOSER = 12.0F;
    /** The way from the kettle to the scene's camera, level (kettle.json). */
    private static final Vector3f TO_CAMERA = new Vector3f(-1.0F, 0.0F, -1.0F).normalize();
    private static final float HOP = 1.5F;
    private static final float IN_HAND = 0.6F;
    private static final float TILT = 55.0F;
    private static final float LEAN_BACK = 10.0F;
    private static final float BOUNCE = 0.08F;
    private static final float BOUNCE_TIME = 0.25F;
    /**
     * Picked up, the kettle is turned side on to the cook, as a kettle is held in the hand: its spout to the right of the screen, over the stove, so that
     * it is tipped in profile. The way to the right of the scene's camera (kettle.json: from (-16.52, 31.09, -20.52) to (8, 4, 4)), level.
     */
    private static final Vector3f POUR_WAY = new Vector3f(-1.0F, 0.0F, 1.0F).normalize();
    /**
     * The lid: lifted clear of the rim, slid out under the handle to the cook's right and set down on its edge, leaning on the kettle's
     * side, its bottom edge on the stove (its own origin is the middle of its underside). Seconds each way; points in the kettle's frame.
     */
    private static final float LID_MOVE = 0.4F;
    private static final Vector3f LID_ON = new Vector3f(0.0F, 7.0F, 0.0F);
    private static final Vector3f LID_LIFTED = new Vector3f(0.0F, 8.4F, 0.0F);
    private static final Vector3f LID_OUT = new Vector3f(-8.5F, 8.4F, 0.0F);
    private static final Vector3f LID_LEANING = new Vector3f(-5.55F, 1.94F, 0.0F);
    private static final float LID_TURN = 75.0F;
    /** Where the things steeping lie under the opening, pixels from the middle (two in Farmer's Respite's kettle, four in Youkai's). */
    private static final float[][] STEEP_AT = {{-0.9F, 0.5F}, {0.8F, -0.6F}, {0.7F, 1.0F}, {-0.7F, -1.0F}};
    /** How dark what steeps gets by the end, 0 to 1 of its own colour taken off. */
    private static final float STEEPED = 0.45F;
    /** How soon the drink takes the colour of what it becomes: by this share of the brew it is the new drink's colour. */
    private static final float COLOUR_BY = 0.75F;
    /**
     * Water in a copper kettle: clear but dark, the biome's own colour only half of it; a little uneven.
     */
    private static final int WATER_ALPHA = 0x96;
    private static final int WATER_IN_COPPER = 0x2A1A12;
    private static final float WATER_MOTTLE = 0.1F;
    private static final int DRINK_ALPHA = 0xD8;
    /** Steam a second from the spout or the opening of a hot kettle, more as a brew goes on; bubbles a second in it. */
    private static final float STEAM_HOT = 0.25F;
    private static final float STEAM_BREWING = 1.6F;
    /** A drink left on the fire boils on for as long as it stands there: a steady plume from the spout, puffs a second. */
    private static final float STEAM_BOILING = 7.0F;
    private static final float BUBBLES_HOT = 1.0F;
    private static final float BUBBLES_BREWING = 5.0F;
    private static final int STEAM_COLOR = 0x96FFFFFF;
    private static final Vector3f STEAM_OUT = new Vector3f(0.0F, 4.0F, 0.0F);
    /** The whistle when a brew is done: its jet of steam, seconds, and how much. */
    private static final float JET_TIME = 2.2F;
    private static final float JET_RATE = 45.0F;
    /** How loud the whistle is: a kettle in the next room, not a train. */
    private static final float WHISTLE_VOLUME = 0.12F;
    /** Things put in at once (a recipe picked in the book) jump in one after the other from the player: as at the keg. */
    private static final float ARRIVE_STAGGER = 0.16F;
    private static final float ARRIVE_TIME = 0.4F;
    private static final float ARRIVE_FROM = 9.0F;
    private static final float ARRIVE_ARC = 4.0F;
    /** Where a bucket poured at the opening is drawn from and how high, pixels; how far over the opening its lip is held. */
    private static final float POUR_FROM_ABOVE = 9.0F;
    private static final float POUR_SIZE = 3.4F;
    private static final float POUR_OVER_TOP = 2.0F;
    /**
     * The badge, as the keg's: a dark plate with cut corners, a flame over the icon of what brews (lit while something heats the
     * kettle, grey and blinking while it waits for heat), the bar of how far it has brewed under it and the minutes and seconds left
     * under the bar. With a drink in the kettle, how many cups of it there are. Done, it swells with a chime.
     */
    private static final int PLATE_COLOR = 0x8C1E140C;
    private static final int DIAL_COLOR = 0xFFFFCB6B;
    private static final int TRACK_COLOR = 0x50FFF2DC;
    private static final int FLAME_COLOR = 0xFFFF9A3C;
    private static final int FLAME_TOP = -15;
    private static final int TEXT_TOP = 13;
    private static final String[] COLON = {".", "#", ".", "#", "."};
    private static final String[] TIMES = {"...", "#.#", ".#.", "#.#", "..."};
    private static final float DONE_SWELL = 0.35F;
    private static final float DONE_TIME = 0.6F;
    private static final float READY_BREATH = 0.04F;
    private static final int SERVING = 250;
    private static final Vector3f ICON_MIDDLE = new Vector3f(8.0F, 8.0F, 8.0F);
    private static final int BAR_LEFT = -7;
    private static final int BAR_TOP = 10;
    private static final int BAR_LENGTH = 14;
    /** A cup from the inventory offered under the spout to an empty hand: how see-through. */
    private static final int OFFERED = 0x8CFFFFFF;
    private static final float LEVEL_FOLLOW = 1.6F;

    private final RecipeBook book = new RecipeBook(new KettleBook());
    private final UsableCache usable = new UsableCache();
    private final KettleCups cups = new KettleCups();
    private final ItemStack[] shown = new ItemStack[ITEM_NODES.length];
    private final float[] arriveAge = new float[ITEM_NODES.length];
    private final Vector3f[] arriveFrom = new Vector3f[ITEM_NODES.length];
    private final boolean[] splashAt = new boolean[ITEM_NODES.length];
    private float time;
    /** How far the lid is on its way off, 0 on the kettle to 1 leaning on its side. */
    private float lid;
    private boolean wasOpen;
    private boolean sawLid;
    /** The lid as the cook last clicked it, shown at once while the kettle answers; null once the kettle agrees. */
    private Boolean lidAsked;
    private float lidAskedAt;
    private float level;
    private float bubbles;
    private float steam;
    private float jet;
    private float shownTurn = Float.NaN;
    private String drawnDial = "";
    private int drawnFlame = -1;
    /** What the kettle brews, or the drink it holds: the badge's icon. */
    private ItemStack dish = ItemStack.EMPTY;
    private Item shownDish;
    private int servings;
    private boolean ready;
    private float doneAt = -10.0F;
    /** How far the water had come to the boil at the last look, for a kettle that boils it first; NaN before the first. */
    private float lastBoil = Float.NaN;
    /** The liquid in the kettle at the last look and what it was brewing into: when the one becomes the other, it is done. */
    private Fluid lastFluid;
    private Fluid becoming = Fluids.EMPTY;
    private int liquidColor;
    private KegLiquids.Drink drink;
    private ItemStack offered = ItemStack.EMPTY;
    private Item offeredItem;
    private NodeView kettleNode;
    private KitchenMotions.Pour pour;
    /** When the kettle last came back down onto the stove, for its bounce; how far it was picked up the frame before. */
    private float landedAt = -10.0F;
    private float lastHold;

    public KettleBehavior(StationView view) {
        Arrays.fill(shown, ItemStack.EMPTY);
        Arrays.fill(arriveAge, Float.NaN);
    }

    private static KitchenKettle kettle(StationView view) {
        return KitchenKettle.of(view.level().getBlockEntity(view.pos()));
    }

    @Override
    public void onSceneReady(StationView view) {
        drawnDial = "";
        drawnFlame = -1;
        shownDish = null;
        shownTurn = Float.NaN;
        if (pour != null) {
            pour.stop();
            pour = null;
        }
        view.node("dial").ifPresent(dial -> dial.setTint(DIAL_COLOR));
        view.node("dial_track").ifPresent(track -> track.setTint(TRACK_COLOR));
        view.node("flame").ifPresent(track -> track.setTint(TRACK_COLOR));
        view.node("flame_lit").ifPresent(flame -> flame.setTint(FLAME_COLOR));
        view.node("badge").ifPresent(plate -> {
            plate.setTint(PLATE_COLOR);
            MeshShape.Builder plateShape = MeshShape.builder();
            PotBehavior.pixels(plateShape, -10, -17, 20, 36);
            PotBehavior.pixels(plateShape, -9, -18, 18, 1);
            PotBehavior.pixels(plateShape, -9, 19, 18, 1);
            plate.setShape(plateShape.build(), BADGE_TEXTURE, true);
        });
        MeshShape.Builder flame = MeshShape.builder();
        glyph(flame, PotBehavior.FLAME, -2, FLAME_TOP);
        MeshShape flameShape = flame.build();
        view.node("flame").ifPresent(node -> node.setShape(flameShape, BADGE_TEXTURE, true));
        view.node("flame_lit").ifPresent(node -> node.setShape(flameShape, BADGE_TEXTURE, true));
        for (String part : BADGE_PARTS) {
            view.node(part).ifPresent(node -> node.setOnTop(true));
        }
        view.node("book_open").ifPresent(node -> node.setPainter(book));
        view.node("offer").ifPresent(node -> node.setOnTop(true));
        cups.ready(view);
        lid = isLidOff(view) ? 1.0F : 0.0F;
        wasOpen = lid > 0.0F;
        lastFluid = null;
        lastBoil = Float.NaN;
        refresh(view);
    }

    @Override
    public void onOpen(StationView view) {
        usable.clear();
        view.setInventoryFilter(stack -> usable.test(stack, it -> usable(view, it)));
        book.opened(view);
        refresh(view);
    }

    @Override
    public void onClose(StationView view) {
        book.shut(view);
        cups.clear(view);
        refresh(view);
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        refresh(view);
    }

    /** The lid still on its way, or a cup still being filled, after the last one left: the scene keeps standing in for the block. */
    @Override
    public boolean holdsBlock(StationView view) {
        return !view.isInUse() && (lid > 0.0F && lid < 1.0F || cups.hold() > 0.0F);
    }

    /** What the inventory at the kettle shows: what steeps in it, and what pours into it or is filled from it. */
    private boolean usable(StationView view, ItemStack stack) {
        KitchenKettle kettle = kettle(view);
        if (kettle == null) {
            return false;
        }
        if (kettle.handson$isVessel(stack)) {
            return true;
        }
        return kettle.handson$recipes().stream().anyMatch(recipe -> recipe.ingredients().stream().anyMatch(ingredient -> ingredient.test(stack)));
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Click click && click.node().equals("lid") && click.button() == 0) {
            toggleLid(view);
        } else if (event instanceof SceneEvent.Click click && click.node().equals("book_open")) {
            book.click(view, click.button());
        } else if (event instanceof SceneEvent.Click click && click.node().equals("spout") && click.button() == 0 && !offered.isEmpty()) {
            boolean all = Screen.hasShiftDown();
            pourOut(view, offered, false, all);
            CompoundTag how = new CompoundTag();
            how.putString(KettleStation.AT, KettleStation.SPOUT);
            how.putBoolean(KettleStation.FROM_INVENTORY, true);
            how.putBoolean(KettleStation.ALL, all);
            view.sendAction(KettleStation.ACTION_USE, how);
        }
    }

    /** The wheel over the recipe book goes through its ingredients or its list. */
    @Override
    public boolean onScroll(StationView view, String node, double amount) {
        return "book_open".equals(node) && book.scroll(amount);
    }

    /** A cup clicked on the spout fills; a bucket or a drink clicked on the opening pours in; the book and the lid take their clicks. */
    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if ("book_open".equals(node)) {
            book.click(view, button);
            return true;
        }
        if ("lid".equals(node)) {
            toggleLid(view);
            return true;
        }
        KitchenKettle kettle = kettle(view);
        if (kettle == null) {
            return false;
        }
        boolean spout = "spout".equals(node) && kettle.handson$canServe(stack);
        boolean in = ("mouth".equals(node) || "drink".equals(node)) && kettle.handson$canPour(stack);
        if (!spout && !in) {
            return false;
        }
        if (spout) {
            pourOut(view, stack, true, shift);
        }
        CompoundTag how = new CompoundTag();
        how.putString(KettleStation.AT, spout ? KettleStation.SPOUT : KettleStation.MOUTH);
        how.putBoolean(KettleStation.ALL, shift);
        view.sendAction(KettleStation.ACTION_USE, how);
        return true;
    }

    /**
     * Whether the lid is off: the kettle block's own state (or the station's, for a kettle whose block has none), or as the cook
     * clicked it until the kettle says so too.
     */
    private boolean isLidOff(StationView view) {
        KitchenKettle kettle = kettle(view);
        // A lid that is the station's alone is on whenever nobody works at the kettle, whatever this client last heard.
        boolean off = kettle == null || kettle.handson$lidInBlock() ? KettleStation.isOpen(view.level().getBlockState(view.pos()))
                : view.isInUse() && view.state().getControl(KettleStation.LID) > 0.5F;
        if (lidAsked != null && (lidAsked == off || time - lidAskedAt > 1.0F)) {
            lidAsked = null;
        }
        return lidAsked != null ? lidAsked : off;
    }

    private void toggleLid(StationView view) {
        if (cups.busy()) {
            return;
        }
        boolean open = !isLidOff(view);
        lidAsked = open;
        lidAskedAt = time;
        CompoundTag data = new CompoundTag();
        data.putBoolean(KettleStation.OPEN, open);
        view.sendAction(KettleStation.ACTION_LID, data);
    }

    /** The cook pours a drink into {@code vessel}: the cup comes to the spout at the click, before the kettle answers. */
    private void pourOut(StationView view, ItemStack vessel, boolean fromHand, boolean all) {
        KitchenKettle kettle = kettle(view);
        if (kettle == null || kettle.handson$fluid() == Fluids.EMPTY) {
            return;
        }
        Fluid fluid = kettle.handson$fluid();
        cups.pour(view, vessel, filled(kettle, vessel, fluid), BuiltInRegistries.FLUID.getKey(fluid), colourOf(view, kettle, fluid), fromHand,
                all);
    }

    /** What {@code vessel} becomes filled with {@code fluid}: the drink of the kettle's pouring, or the vessel itself when unknown. */
    private static ItemStack filled(KitchenKettle kettle, ItemStack vessel, Fluid fluid) {
        if (vessel.is(Items.BUCKET) && fluid.getBucket() != Items.AIR) {
            return new ItemStack(fluid.getBucket());
        }
        ItemStack drink = kettle.handson$drinkOf(fluid);
        return drink.is(Items.AIR) ? vessel : drink;
    }

    @Override
    public void onEffect(StationView view, ResourceLocation effect, String node, CompoundTag data) {
        Fluid fluid = fluidOf(data.getString(KettleStation.FLUID));
        KitchenKettle kettle = kettle(view);
        int color = 0xFF000000 | colourOf(view, kettle, fluid) & 0xFFFFFF;
        Item vessel = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(data.getString(KettleStation.VESSEL)));
        if (KettleStation.EFFECT_POURED.equals(effect)) {
            poured(view, vessel, color);
        } else if (KettleStation.EFFECT_SERVED.equals(effect) && fluid != Fluids.EMPTY && kettle != null) {
            ItemStack cup = new ItemStack(vessel);
            cups.served(cup, filled(kettle, cup, fluid), BuiltInRegistries.FLUID.getKey(fluid), colourOf(view, kettle, fluid),
                    data.getInt(KettleStation.AMOUNT));
        }
    }

    /** Something was poured in at the opening: the bucket is tipped over it and its liquid runs in, the level waiting for it. */
    private void poured(StationView view, Item vessel, int color) {
        NodeView hand = view.node("pour_hand").orElse(null);
        Vector3f opening = kettlePoint(0.0F, FULL, 0.0F);
        if (hand == null || vessel == null || vessel == Items.AIR || !view.isFocused()) {
            view.spawnOnLiquid(DiegesisParticles.SPLASH, "drink", 4, color, new Vector3f(0.0F, 3.0F, 0.0F));
            view.playSound(SoundEvents.BUCKET_EMPTY, "drink", 0.6F, 1.1F);
            return;
        }
        if (pour != null) {
            pour.stop();
        }
        Vector3f from = view.cursorOn(TOP + POUR_FROM_ABOVE).orElse(new Vector3f(opening.x, TOP + POUR_FROM_ABOVE, opening.z - 4.0F));
        pour = new KitchenMotions.Pour(hand, "kettle_pour", new ItemStack(vessel), from, opening, this::surfaceY, "drink", POUR_SIZE, -1.0F,
                0.0F).lipAtLeast(TOP + POUR_OVER_TOP);
        float before = pour.pouredBy() - pour.flowTime();
        float flow = pour.flowTime();
        view.node("drink").ifPresent(drink -> drink.holdLevel(before, flow));
    }

    private static Fluid fluidOf(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null || id.isEmpty() ? Fluids.EMPTY : BuiltInRegistries.FLUID.get(key);
    }

    private float surfaceY() {
        return FLOOR + (FULL - FLOOR) * level;
    }

    /** A point in the kettle's own frame (pixels from its middle, the spout to -z), in the scene: turned with the kettle. */
    private Vector3f kettlePoint(float x, float y, float z) {
        Vector3f local = new Vector3f(x, y, z);
        return kettleNode == null ? local.add(8.0F, 0.0F, 8.0F) : kettleNode.sceneMatrix().transformPosition(local);
    }

    /** Where the kettle is carried picked up {@code hold} of the way, from where it stands on the stove, in its own frame. */
    private Vector3f heldOffset(float hold) {
        Vector3f closer = new Vector3f(TO_CAMERA).mul(HELD_CLOSER * hold);
        if (kettleNode != null) {
            new Matrix4f(kettleNode.sceneMatrix()).invert().transformDirection(closer);
        }
        return closer.add(0.0F, LIFT * hold + HOP * Mth.sin(Mth.PI * hold), 0.0F);
    }

    /** How far the kettle leans, degrees about its middle: back as it is picked up, forward over the cup. */
    private static float lean(float hold, float tip) {
        return -TILT * tip + LEAN_BACK * Mth.sin(Mth.PI * hold) * (1.0F - tip);
    }

    /** How big the kettle is drawn: smaller in the hand, with a bounce as it lands on the stove. */
    private float scaleOf(float hold) {
        float bounce = time - landedAt < BOUNCE_TIME ? BOUNCE * Mth.sin(Mth.PI * (time - landedAt) / BOUNCE_TIME) : 0.0F;
        return Mth.lerp(hold, 1.0F, IN_HAND) * (1.0F + bounce);
    }

    /**
     * How far the kettle is turned about its middle, degrees, to stand side on to the cook ({@link #POUR_WAY}) when it is picked up
     * {@code hold} of the way; the shorter way round.
     */
    private float turnTo(float hold) {
        if (kettleNode == null || hold <= 0.0F) {
            return 0.0F;
        }
        Vector3f way = new Matrix4f(kettleNode.sceneMatrix()).invert().transformDirection(new Vector3f(POUR_WAY));
        return (float) Mth.atan2(-way.x, -way.z) * Mth.RAD_TO_DEG * hold;
    }

    /** The kettle picked up {@code hold} and tipped {@code tip} of the way: its own transform in the kettle's frame. */
    private Matrix4f tipped(float hold, float tip) {
        Vector3f off = heldOffset(hold).add(MIDDLE);
        return new Matrix4f().translate(off).rotateY(turnTo(hold) * Mth.DEG_TO_RAD).rotateX(lean(hold, tip) * Mth.DEG_TO_RAD)
                .scale(scaleOf(hold)).translate(-MIDDLE.x, -MIDDLE.y, -MIDDLE.z);
    }

    /** Where the drink leaves the spout with the kettle picked up and tipped so far, in the scene. */
    private Vector3f lipAt(float hold, float tip) {
        Vector3f local = tipped(hold, tip).transformPosition(new Vector3f(LIP));
        return kettlePoint(local.x, local.y, local.z);
    }

    /** The way the spout faces in the scene, level. */
    private Vector3f spoutFacing() {
        if (kettleNode == null) {
            return new Vector3f(0.0F, 0.0F, -1.0F);
        }
        Vector3f way = kettleNode.sceneMatrix().transformDirection(new Vector3f(0.0F, 0.0F, -1.0F));
        return way.set(way.x, 0.0F, way.z).normalize();
    }

    private void refresh(StationView view) {
        StationState state = view.state();
        ItemStack[] items = state.getItems(KettleStation.INGREDIENTS);
        List<Integer> arrivals = new ArrayList<>();
        for (int i = 0; i < ITEM_NODES.length && i < items.length; i++) {
            if (!items[i].isEmpty() && shown[i].isEmpty() && view.isFocused()) {
                arrivals.add(i);
            }
            if (items[i].isEmpty()) {
                arriveAge[i] = Float.NaN;
            }
            shown[i] = items[i].copy();
        }
        if (arrivals.size() > 1) {
            float height = TOP + ARRIVE_FROM;
            for (int k = 0; k < arrivals.size(); k++) {
                int index = arrivals.get(k);
                arriveAge[index] = -k * ARRIVE_STAGGER;
                arriveFrom[index] = FromPlayer.point(view, height, k, arrivals.size()).orElse(new Vector3f(8.0F, height, 8.0F));
            }
        } else {
            arrivals.forEach(index -> splashIn(view, index));
        }
        colour(view);
        tooltip(view);
    }

    /** Something drops in: a plop and a splash at the opening, or a tap on the dry copper floor. */
    private void splashIn(StationView view, int index) {
        KitchenKettle kettle = kettle(view);
        if (kettle == null || kettle.handson$amount() == 0) {
            view.playSound(SoundEvents.LANTERN_HIT, ITEM_NODES[index], 0.25F, 1.6F);
            return;
        }
        splashAt[index] = true;
        view.playSound(PotSounds.PLOP, ITEM_NODES[index], 0.35F, 1.15F + 0.05F * index);
    }

    /**
     * The liquid's colour: water as the biome's, darkened in copper; a drink as {@link KegLiquids} knows it or in its own fluid's
     * colour. While it brews, the colour moves from the liquid it was to the drink it becomes, as the leaves give it theirs.
     */
    private void colour(StationView view) {
        KitchenKettle kettle = kettle(view);
        if (kettle == null) {
            return;
        }
        Fluid fluid = fluidIn(view);
        drink = KegLiquids.of(fluid).orElse(null);
        Optional<KitchenKettle.Brewing> making = making(view, kettle, fluid);
        becoming = making.map(KitchenKettle.Brewing::result).orElse(Fluids.EMPTY);
        int colour = colourOf(view, kettle, fluid);
        float progress = view.state().getControl(KettleStation.PROGRESS);
        if (making.isPresent() && progress > 0.0F) {
            colour = blend(colour, colourOf(view, kettle, making.get().result()), KitchenMotions.smooth(progress / COLOUR_BY));
        }
        liquidColor = colour & 0xFFFFFF;
        int shownColour = colour;
        view.node("drink").ifPresent(node -> {
            node.setLiquidColor(shownColour);
            node.setMottle(drink != null ? drink.mottle() : fluid.isSame(Fluids.WATER) ? WATER_MOTTLE : 0.0F);
        });
        boolean wasReady = ready;
        servings = 0;
        ready = false;
        dish = making.map(recipe -> kettle.handson$drinkOf(recipe.result())).orElse(ItemStack.EMPTY);
        int amount = view.state().getTank(KettleStation.DRINK).amount() * kettle.handson$capacity() / 100;
        if (dish.isEmpty() && fluid != Fluids.EMPTY && amount > 0
                && kettle.handson$recipes().stream().anyMatch(recipe -> recipe.result().isSame(fluid))) {
            // A brewed drink waits to be poured; plain water or milk shows no badge.
            dish = kettle.handson$drinkOf(fluid);
            servings = Math.max(1, Math.round((float) amount / SERVING));
            ready = true;
        }
        if (ready && !wasReady && lastFluid != null && lastFluid != fluid) {
            done(view);
        }
        lastFluid = fluid;
        // Water boiled in a kettle that boils it first, with nothing to brew yet: it whistles, as a kettle on the stove does.
        float boil = kettle.handson$boilsFirst() && fluid.isSame(Fluids.WATER) ? view.state().getControl(KettleStation.BOIL) : Float.NaN;
        if (!Float.isNaN(lastBoil) && lastBoil < 1.0F && boil >= 1.0F && making.isEmpty()) {
            boiled(view);
        }
        lastBoil = boil;
    }

    private static int colourOf(StationView view, KitchenKettle kettle, Fluid fluid) {
        if (fluid == Fluids.EMPTY) {
            return WATER_ALPHA << 24;
        }
        if (fluid.isSame(Fluids.WATER)) {
            return WATER_ALPHA << 24 | blend(BiomeColors.getAverageWaterColor(view.level(), view.pos()), WATER_IN_COPPER, 0.5F) & 0xFFFFFF;
        }
        Optional<KegLiquids.Drink> known = KegLiquids.of(fluid);
        if (known.isPresent()) {
            return known.get().color();
        }
        int tint = kettle == null ? 0 : kettle.handson$tint(fluid);
        if (tint != 0 && (tint & 0xFFFFFF) != 0xFFFFFF) {
            return DRINK_ALPHA << 24 | tint & 0xFFFFFF;
        }
        ItemStack drink = kettle == null ? ItemStack.EMPTY : kettle.handson$drinkOf(fluid);
        return DRINK_ALPHA << 24 | (drink.isEmpty() ? 0xB08850 : KitchenLooks.liquidColor(drink) & 0xFFFFFF);
    }

    /** The liquid in the kettle as the station tells it: the block's own data reaches this client later. */
    private static Fluid fluidIn(StationView view) {
        return view.state().getTank(KettleStation.DRINK).amount() == 0 ? Fluids.EMPTY : fluidOf(view.state().data().getString(KettleStation.FLUID));
    }

    /** The recipe what lies in the kettle makes: its ingredients there, its base the liquid in it. */
    private static Optional<KitchenKettle.Brewing> making(StationView view, KitchenKettle kettle, Fluid fluid) {
        List<ItemStack> items = Arrays.stream(view.state().getItems(KettleStation.INGREDIENTS)).filter(stack -> !stack.isEmpty()).toList();
        if (items.isEmpty() || fluid == Fluids.EMPTY) {
            return Optional.empty();
        }
        return kettle.handson$recipes().stream().filter(recipe -> recipe.base().isSame(fluid)).filter(recipe -> KettleLogic.matches(recipe, items)).findFirst();
    }

    private void tooltip(StationView view) {
        KitchenKettle kettle = kettle(view);
        view.node("drink").ifPresent(node -> {
            if (kettle == null || kettle.handson$fluid() == Fluids.EMPTY) {
                node.setTooltip(Component.empty());
                return;
            }
            ItemStack drink = kettle.handson$drinkOf(kettle.handson$fluid());
            Component name = drink.isEmpty() ? Component.literal(BuiltInRegistries.FLUID.getKey(kettle.handson$fluid()).getPath())
                    : drink.getHoverName();
            node.setTooltip(Component.translatable("handson_delight.keg.holds", name, kettle.handson$amount()));
        });
    }

    private static int blend(int from, int to, float amount) {
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            result |= Math.round(Mth.lerp(amount, from >>> shift & 0xFF, to >>> shift & 0xFF)) << shift;
        }
        return result;
    }

    @Override
    public void frame(StationView view, float dt) {
        time += dt;
        kettleNode = view.node("kettle").orElse(null);
        boolean inUse = view.isInUse();
        boolean lidOff = isLidOff(view);
        boolean shownHere = view.isBlockHidden();
        if (lidOff != wasOpen && sawLid && shownHere) {
            // The lid lifted off the rim, or about to go back on.
            view.playSound(SoundEvents.LANTERN_HIT, "lid", 0.35F, lidOff ? 1.5F : 1.3F);
        }
        sawLid = true;
        wasOpen = lidOff;
        for (String id : SHOWN_IN_USE) {
            view.node(id).ifPresent(node -> node.setVisible(shownHere));
        }
        support(view, shownHere);
        turnToBlock(view);
        float tip = cups.tip();
        float hold = cups.hold();
        if (hold > 0.0F && lastHold <= 0.0F) {
            view.playSound(SoundEvents.LANTERN_HIT, "spout", 0.3F, 1.45F);
        } else if (hold <= 0.0F && lastHold > 0.0F) {
            landedAt = time;
            view.playSound(SoundEvents.LANTERN_PLACE, "spout", 0.35F, 1.3F);
        }
        lastHold = hold;
        float scale = scaleOf(hold);
        float leaning = lean(hold, tip);
        float turning = turnTo(hold);
        // The node is the kettle's front bottom edge: where the transform about its middle takes that edge, less where it stands.
        Vector3f edge = tipped(hold, tip).transformPosition(new Vector3f(0.0F, 0.0F, PIVOT));
        Vector3f off = edge.sub(0.0F, 0.0F, PIVOT);
        view.node("pot").ifPresent(pot -> {
            pot.moveTo(off.x, off.y, off.z, 0.0F);
            pot.rotateTo(leaning, turning, 0.0F, 0.0F);
            pot.scaleTo(scale, 0.0F);
        });
        moveLid(view, dt, lidOff, hold, tip);
        // Nothing goes in through the lid.
        view.node("mouth").ifPresent(node -> node.setEnabled(lidOff && lid >= 1.0F && hold <= 0.0F));
        level = Mth.lerp(Math.min(1.0F, dt * LEVEL_FOLLOW), level, view.state().getTank(KettleStation.DRINK).amount() / 100.0F);
        float progress = view.state().getControl(KettleStation.PROGRESS);
        steeping(view, dt, shownHere && hold <= 0.0F, progress);
        book.frame(view, dt);
        heat(view, dt, shownHere, lidOff, inUse, progress);
        if (pour != null && !pour.frame(view, dt)) {
            pour = null;
        }
        cups.frame(view, dt, lipAt(1.0F, 1.0F), lipAt(hold, tip), POUR_WAY, view.node("drink").orElse(null));
        offerCup(view, inUse);
        float appear = RecipeBook.appear(view);
        badge(view, inUse && RecipeBook.isWorkedAt(view) && appear > RecipeBook.SPECK);
        float swell = badgeSwell();
        for (String part : BADGE_PARTS) {
            view.node(part).ifPresent(node -> node.scaleTo(appear * swell, 0.0F));
        }
    }

    /** The tray of a kettle on a stove, the hanger of one hung up: as the block stands. */
    private static void support(StationView view, boolean shown) {
        String standsOn = support(view.level().getBlockState(view.pos()));
        view.node("tray").ifPresent(node -> node.setVisible(shown && standsOn.equals("tray")));
        view.node("hanger").ifPresent(node -> node.setVisible(shown && standsOn.equals("handle")));
    }

    /** What the kettle stands on or hangs from: Farmer's Delight's {@code support} of its block, "none", "tray" or "handle". */
    private static String support(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals("support")) {
                return state.getValue(property).toString().toLowerCase(java.util.Locale.ROOT);
            }
        }
        return "none";
    }

    /** The kettle stands as the block faces, whichever way the scene turned to the player. */
    private void turnToBlock(StationView view) {
        BlockState state = view.level().getBlockState(view.pos());
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return;
        }
        Direction block = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        KitchenKettle kettle = kettle(view);
        if (kettle != null && kettle.handson$turnsOnTray() && "tray".equals(support(state))) {
            // On its tray its mod draws the kettle a quarter turned to the right: the world's block and the scene's agree.
            block = block.getClockWise();
        }
        float turn = view.facing().toYRot() - block.toYRot();
        if (turn != shownTurn) {
            shownTurn = turn;
            view.node("kettle").ifPresent(node -> node.rotateTo(0.0F, turn, 0.0F, 0.0F));
        }
    }

    /**
     * The lid is lifted off the rim, slid out under the handle and set down on its edge leaning on the kettle's side when the cook
     * clicks it, and put back the same way. It rides on the kettle while it is on; off, it stays on the stove when the kettle is tipped.
     */
    private void moveLid(StationView view, float dt, boolean off, float hold, float tip) {
        float before = lid;
        lid = Mth.clamp(lid + (off ? dt : -dt) / LID_MOVE, 0.0F, 1.0F);
        if (before < 1.0F && lid >= 1.0F || before > 0.0F && lid <= 0.0F) {
            view.playSound(SoundEvents.LANTERN_PLACE, "lid", 0.4F, 1.25F + view.level().random.nextFloat() * 0.1F);
        }
        NodeView node = view.node("lid").orElse(null);
        if (node == null) {
            return;
        }
        if (lid <= 0.0F) {
            node.moveTo(0.0F, 0.0F, 0.0F, 0.0F);
            node.rotateTo(0.0F, 0.0F, 0.0F, 0.0F);
            node.scaleTo(1.0F, 0.0F);
            return;
        }
        float k = KitchenMotions.smooth(lid);
        Matrix4f kettle = tipped(hold, tip);
        Vector3f start = kettle.transformPosition(new Vector3f(LID_ON));
        // Up off the rim, out under the handle and down: a curve through two points, so that it never cuts through the kettle.
        float u = 1.0F - k;
        Vector3f at = new Vector3f(start).mul(u * u * u).add(new Vector3f(LID_LIFTED).mul(3.0F * u * u * k))
                .add(new Vector3f(LID_OUT).mul(3.0F * u * k * k)).add(new Vector3f(LID_LEANING).mul(k * k * k));
        float turn = LID_TURN * KitchenMotions.smooth((lid - 0.35F) / 0.6F);
        // In the tipped kettle's own frame, which the lid is a child of, from where it sits on the kettle.
        Vector3f local = new Matrix4f(kettle).invert().transformPosition(new Vector3f(at)).sub(LID_ON);
        Quaternionf rotation = new Quaternionf().rotationX(-lean(hold, tip) * Mth.DEG_TO_RAD).rotateY(-turnTo(hold) * Mth.DEG_TO_RAD)
                .rotateZ(turn * Mth.DEG_TO_RAD);
        Vector3f euler = rotation.getEulerAnglesYXZ(new Vector3f());
        node.moveTo(local.x, local.y, local.z, 0.0F);
        node.rotateTo(euler.x * Mth.RAD_TO_DEG, euler.y * Mth.RAD_TO_DEG, euler.z * Mth.RAD_TO_DEG, 0.0F);
        // Off, it keeps its own size on the stove while the kettle is small in the hand.
        node.scaleTo(1.0F / scaleOf(hold), 0.0F);
    }

    /**
     * What steeps lies under the opening: afloat as it goes in, it soaks, sinks to the bottom and darkens as the brew goes on. Things
     * put in together come from the player one after the other.
     */
    private void steeping(StationView view, float dt, boolean shownHere, float progress) {
        boolean wet = view.state().getTank(KettleStation.DRINK).amount() > 0;
        float sunk = wet ? KitchenMotions.smooth(progress * 1.6F) : 1.0F;
        int tint = 0xFF000000 | 0x10101 * Math.round(255.0F * (1.0F - STEEPED * KitchenMotions.smooth(progress)));
        for (int i = 0; i < ITEM_NODES.length; i++) {
            int index = i;
            boolean there = shownHere && !shown[i].isEmpty();
            float bob = wet && sunk < 1.0F ? Mth.sin(time * 1.1F + i * 1.7F) * 0.08F * (1.0F - sunk) : 0.0F;
            float y = Mth.lerp(sunk, surfaceY() - 0.6F, FLOOR + 0.2F) + bob;
            float spin = time * (wet ? 8.0F : 0.0F) * (1.0F - sunk) + i * 140.0F;
            Vector3f at = kettlePoint(STEEP_AT[i][0], y, STEEP_AT[i][1]).sub(8.0F, 0.0F, 8.0F);
            boolean waiting = arrive(view, i, dt, at);
            if (splashAt[i] && !waiting) {
                splashAt[i] = false;
                int color = 0xFF000000 | liquidColor;
                Vector3f where = kettlePoint(0.0F, surfaceY(), 0.0F);
                view.spawnParticles(DiegesisParticles.SPLASH, where, new Vector3f(0.0F, 7.0F, 0.0F), 0.8F, 4, color);
                view.node("drink").ifPresent(drink -> drink.ripple(0.0F, 5.0F, 0.4F));
            }
            view.node(ITEM_NODES[i]).ifPresent(node -> {
                node.setEnabled(there && !waiting);
                node.moveTo(at.x, at.y, at.z, 0.0F);
                node.rotateTo(80.0F * sunk, spin, 0.0F, 0.0F);
                node.setTint(tint);
            });
        }
    }

    /** A thing on its way in from the player, as at the keg: true while it still waits for its turn. */
    private boolean arrive(StationView view, int index, float dt, Vector3f at) {
        if (Float.isNaN(arriveAge[index])) {
            return false;
        }
        arriveAge[index] += dt;
        if (arriveAge[index] < 0.0F) {
            return true;
        }
        float k = arriveAge[index] / ARRIVE_TIME;
        if (k >= 1.0F) {
            arriveAge[index] = Float.NaN;
            splashIn(view, index);
            return false;
        }
        Vector3f from = new Vector3f(arriveFrom[index]).sub(8.0F, 0.0F, 8.0F);
        float eased = KitchenMotions.smooth(k);
        at.set(Mth.lerp(eased, from.x, at.x), Mth.lerp(k, from.y, at.y) + ARRIVE_ARC * Mth.sin(k * Mth.PI), Mth.lerp(eased, from.z, at.z));
        return false;
    }

    /**
     * A kettle over heat: its drink simmers, steam rises from the spout (from the opening with the lid off), more as a brew nears
     * done; the simmer is heard while it is shown. Seen by everybody near it, whether anybody works at it or not.
     */
    private void heat(StationView view, float dt, boolean shownHere, boolean lidOff, boolean inUse, float progress) {
        KitchenKettle kettle = kettle(view);
        boolean heated = kettle != null && kettle.handson$isHeated();
        boolean wet = kettle != null && kettle.handson$amount() > 0;
        boolean brewing = heated && becoming != Fluids.EMPTY;
        // Water still coming to the boil in a kettle that boils it first: it warms, simmering more and more.
        float boil = view.state().getControl(KettleStation.BOIL);
        boolean warming = heated && wet && boil < 1.0F;
        // Nobody at it: this client does not know how far a brew is, only that one goes on. A drink left on the fire keeps boiling.
        float strength = !heated || !wet ? 0.0F : warming ? 0.1F + 0.5F * boil : brewing ? inUse ? 0.35F + 0.65F * progress : 0.6F : 1.0F;
        view.node("drink").ifPresent(node -> {
            node.setAgitation(heated && wet ? 0.02F + 0.06F * strength : 0.005F);
            node.setEdgeBoil(heated && wet ? 0.1F + 0.4F * strength : 0.0F);
        });
        // While it pours, the stream is what is watched: no puffs round the cup.
        boolean onFire = heated && wet && cups.hold() <= 0.0F;
        // Brewing, it comes to the boil as the brew goes on; a drink done, or water, boils on.
        boolean boiling = onFire && !brewing && !warming;
        float hot = !onFire ? 0.0F : boiling ? STEAM_BOILING : STEAM_HOT + STEAM_BREWING * strength;
        steam += dt * hot;
        if (steam >= 1.0F) {
            int puffs = (int) steam;
            steam -= puffs;
            if (lidOff && shownHere) {
                view.spawnOnLiquid(DiegesisParticles.STEAM, "drink", puffs, STEAM_COLOR, STEAM_OUT);
            } else if (lidOff) {
                // The block itself, its lid off: the steam rises from the opening.
                view.spawnParticles(DiegesisParticles.STEAM, kettlePoint(0.0F, TOP, 0.0F), STEAM_OUT, 0.6F, puffs, STEAM_COLOR);
            } else {
                Vector3f lip = lipAt(cups.hold(), cups.tip());
                float push = boiling ? 5.0F : 2.0F;
                view.spawnParticles(DiegesisParticles.STEAM, lip, new Vector3f(spoutFacing()).mul(push).add(0.0F, 3.5F, 0.0F), 0.3F, puffs,
                        boiling ? 0xB0FFFFFF : STEAM_COLOR);
            }
        }
        if (shownHere && wet && heated && cups.hold() <= 0.0F) {
            bubbles += dt * (BUBBLES_HOT + BUBBLES_BREWING * strength);
            if (bubbles >= 1.0F) {
                view.spawnInLiquid(DiegesisParticles.BUBBLE, "drink", (int) bubbles, 0xE0FFFFFF, new Vector3f(0.0F, 3.0F, 0.0F));
                bubbles -= (int) bubbles;
            }
        }
        // Heard as long as there is something in it on the fire, at the station or passing by; not while it is in the hand.
        view.loopSound("simmer", PotSounds.BODY, onFire ? 0.15F + 0.35F * strength : 0.0F, 1.35F);
        if (cups.hold() > 0.0F) {
            // Picked up, it is off the fire: the whistle stops.
            jet = 0.0F;
        }
        view.loopSound("whistle", kettle != null && kettle.handson$lidInBlock() ? WHISTLE : OWN_WHISTLE,
                jet > 0.0F ? WHISTLE_VOLUME * Math.min(1.0F, jet / 0.5F) : 0.0F, 1.0F);
        if (jet > 0.0F) {
            jet -= dt;
            Vector3f lip = lipAt(cups.hold(), cups.tip());
            int puffs = Math.max(1, Math.round(dt * JET_RATE * Math.min(1.0F, jet / JET_TIME * 2.0F)));
            view.spawnParticles(DiegesisParticles.STEAM, lip, new Vector3f(spoutFacing()).mul(14.0F).add(0.0F, 5.0F, 0.0F), 0.5F, puffs, 0xD0FFFFFF);
        }
    }

    /** With an empty hand at the spout, a cup from the inventory hangs see-through under it: a click fills it. */
    private void offerCup(StationView view, boolean inUse) {
        offered = ItemStack.EMPTY;
        NodeView node = view.node("offer").orElse(null);
        LocalPlayer player = Minecraft.getInstance().player;
        KitchenKettle kettle = kettle(view);
        boolean hovered = view.node("spout").map(NodeView::isHovered).orElse(false);
        if (node == null || player == null || kettle == null || !inUse || !view.isFocused() || !hovered
                || !player.inventoryMenu.getCarried().isEmpty() || cups.busy()) {
            if (node != null) {
                node.setVisible(false);
            }
            return;
        }
        int slot = KettleLogic.containerSlot(kettle, player.getInventory());
        node.setVisible(slot >= 0);
        if (slot < 0) {
            return;
        }
        offered = player.getInventory().getItem(slot);
        if (offeredItem != offered.getItem()) {
            offeredItem = offered.getItem();
            node.setVisual(Visual.ofItem(offeredItem).withAnchor(new Vector3f(8.0F, 0.0F, 8.0F)));
            node.setTint(OFFERED);
            node.scaleTo(KettleCups.HEIGHT / 16.0F / 0.35F, 0.0F);
        }
        // Where the cup will be held, in front of the spout.
        Vector3f lip = lipAt(1.0F, 1.0F);
        Vector3f out = POUR_WAY;
        node.moveTo(lip.x + out.x * 0.6F - 8.0F, lip.y - 1.6F - KettleCups.HEIGHT, lip.z + out.z * 0.6F - 8.0F, 0.0F);
    }

    /**
     * A brew is done: with the lid on, the kettle whistles and a jet of steam shoots from the spout, as Farmer's Respite's own whistle
     * does, for whoever is near; with it off, the steam only rises. At the station the badge swells with a chime too.
     */
    private void done(StationView view) {
        KitchenKettle kettle = kettle(view);
        if (kettle == null || !kettle.handson$isHeated()) {
            return;
        }
        doneAt = time;
        if (!isLidOff(view)) {
            jet = JET_TIME;
        } else {
            view.spawnOnLiquid(DiegesisParticles.STEAM, "drink", 8, 0xA0FFFFFF, STEAM_OUT);
        }
        if (view.isInUse()) {
            view.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), "badge", 0.35F, 1.26F);
            view.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), "badge", 0.3F, 1.5F);
        }
    }

    /** Plain water has come to the boil, the lid on: the whistle and its jet, for whoever is near; with it off, the steam only rises. */
    private void boiled(StationView view) {
        KitchenKettle kettle = kettle(view);
        if (kettle == null || !kettle.handson$isHeated()) {
            return;
        }
        if (!isLidOff(view)) {
            jet = JET_TIME;
        } else {
            view.spawnOnLiquid(DiegesisParticles.STEAM, "drink", 6, 0xA0FFFFFF, STEAM_OUT);
        }
    }

    float badgeSwell() {
        float since = time - doneAt;
        if (since >= 0.0F && since < DONE_TIME) {
            return 1.0F + DONE_SWELL * Mth.sin(Mth.PI * since / DONE_TIME);
        }
        return ready ? 1.0F + READY_BREATH * (0.5F - 0.5F * Mth.cos(time * 2.4F)) : 1.0F;
    }

    /**
     * The badge while something brews or a drink waits: what it makes, whether it is heated, how far it has brewed and the time left,
     * or how many cups there are.
     */
    private void badge(StationView view, boolean cookHere) {
        boolean shown = cookHere && !dish.isEmpty();
        view.node("badge").ifPresent(plate -> plate.setVisible(shown));
        float progress = view.state().getControl(KettleStation.PROGRESS);
        int left = view.state().data().getInt(KettleStation.LEFT);
        boolean heated = view.state().getControl(KettleStation.HEATED) > 0.5F;
        boolean brewing = becoming != Fluids.EMPTY;
        float boil = view.state().getControl(KettleStation.BOIL);
        int seconds = brewing && (progress > 0.0F || boil < 1.0F) && left > 0 ? Math.max(1, Mth.ceil(left / 20.0F)) : 0;
        int bar = brewing ? Math.round(brewed(view, progress, boil) * BAR_LENGTH) : ready ? BAR_LENGTH : 0;
        String text = seconds >= 60 ? seconds / 60 + ":" + String.format("%02d", seconds % 60) : seconds > 0 ? Integer.toString(seconds)
                : !brewing && servings > 0 ? "x" + Math.min(servings, 99) : "";
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
            text(lit, text);
            view.node("dial").ifPresent(node -> node.setShape(bar > 0 || !text.isEmpty() ? lit.build() : null, BADGE_TEXTURE, true));
        }
        view.node("dial_track").ifPresent(node -> node.setVisible(shown));
        view.node("dial").ifPresent(node -> node.setVisible(shown));
        // The flame: lit over a fire; grey and blinking while a brew waits for heat; none for a drink that only waits to be poured.
        view.node("flame").ifPresent(node -> node.setVisible(shown && brewing && !heated && Mth.sin(time * 6.0F) > 0.0F));
        view.node("flame_lit").ifPresent(node -> node.setVisible(shown && brewing && heated));
        preview(view, shown, seconds);
    }

    /**
     * How far along the brew is, 0 to 1, as the badge's bar shows it: in a kettle that boils its water first, the boiling is the bar's
     * first part, as long as it takes against the brew.
     */
    private float brewed(StationView view, float progress, float boil) {
        KitchenKettle kettle = kettle(view);
        if (kettle == null || !kettle.handson$boilsFirst()) {
            return progress;
        }
        Optional<KitchenKettle.Brewing> making = making(view, kettle, fluidIn(view));
        if (making.isEmpty()) {
            return progress;
        }
        float boiling = (float) making.get().baseAmount() / (making.get().baseAmount() + Math.max(1, making.get().ticks()));
        return boil < 1.0F ? boiling * boil : boiling + (1.0F - boiling) * progress;
    }

    /** A line of digits, colons and "x" centred under the bar, a pixel between characters. */
    private static void text(MeshShape.Builder shape, String text) {
        int width = -1;
        for (char c : text.toCharArray()) {
            width += (c == ':' ? 1 : 3) + 1;
        }
        int x = -width / 2 - width % 2;
        for (char c : text.toCharArray()) {
            String[] glyph = c == ':' ? COLON : c == 'x' ? TIMES : PotBehavior.DIGITS[c - '0'];
            glyph(shape, glyph, x, TEXT_TOP);
            x += glyph[0].length() + 1;
        }
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

    /** The icon of what brews or waits, with its name and the minutes left on hover. */
    private void preview(StationView view, boolean shown, int seconds) {
        int minutes = seconds > 0 ? Math.max(1, Mth.ceil(seconds / 60.0F)) : 0;
        view.node("preview").ifPresent(node -> {
            node.setVisible(shown);
            if (!shown) {
                return;
            }
            if (shownDish != dish.getItem()) {
                shownDish = dish.getItem();
                node.setVisual(Visual.ofItem(shownDish).withAnchor(ICON_MIDDLE));
            }
            node.setTooltip(seconds >= 120 ? Component.translatable("handson_delight.keg.fermenting", dish.getHoverName(), minutes)
                    : seconds > 0 ? Component.translatable("handson_delight.pot.cooking", dish.getHoverName(), seconds) : dish.getHoverName());
        });
    }
}
