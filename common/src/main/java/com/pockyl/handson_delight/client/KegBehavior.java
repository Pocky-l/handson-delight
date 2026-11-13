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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.MeshShape;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.handson_delight.station.KegLogic;
import com.pockyl.handson_delight.station.KegStation;
import com.pockyl.handson_delight.station.KitchenKeg;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * What the keg shows while somebody works at it: the lid swung open, the liquid at its level, looking like the drink it is, with
 * what ferments floating on it, the tap in front. While nobody does, the keg is the block itself, shut: the station hides the block
 * while it is used and until the lid has fallen shut behind the last one to leave.
 */
public final class KegBehavior implements StationBehavior {
    /**
     * The inside of a keg, in pixels: where the liquid starts and how high it stands when the keg is full, three quarters up the
     * inside under the lid; the lower lip of the tap's hole, where a drink leaves it, in the scene before the keg is turned to its
     * facing; the middle of the liquid in its own node; the opening the lid sits in, in the model's pixels.
     */
    public record Shape(float floor, float full, Vector3f tap, float middleX, float middleZ, float rimX0, float rimX1, float rimZ0,
            float rimZ1) {
        /**
         * The lying keg: walls 1 px thick, the whole top between them a hatch; the tap's hole low in the middle of its front, x 7..9,
         * y 4..6 (Brewin' and Chewin's texture, rows 11..13 on a face from y 2).
         */
        public static final Shape LYING = new Shape(3.0F, 12.0F, new Vector3f(8.0F, 4.2F, 0.0F), 0.0F, 0.0F, 2.0F, 14.0F, 1.0F, 15.0F);
        /** The standing keg: staves 1 px thick, the whole head a lid; the tap's hole near the bottom of its front, x 7..9, y 1..3. */
        public static final Shape STANDING = new Shape(1.0F, 11.5F, new Vector3f(8.0F, 1.2F, 1.0F), 8.0F, 8.0F, 2.0F, 14.0F, 2.0F, 14.0F);
    }

    private static final String[] ITEM_NODES = {"item_0", "item_1", "item_2", "item_3"};
    /** The badge over the keg: its plate, the bar and the thermometer. */
    private static final String[] BADGE_PARTS = {"badge", "dial_track", "dial", "thermo", "thermo_now", "preview"};
    private static final String[] SHOWN_IN_USE = {"keg", "lid", "drink", "tap", "mouth", "served"};
    @SuppressWarnings("removal")
    private static final ResourceLocation BADGE_TEXTURE = new ResourceLocation("minecraft", "block/white_concrete");
    /**
     * The lid is taken off and set down on the floor on the cook's right, leaning on the keg's side, and put back when the last
     * one goes: lifted clear of the rim, carried over in an arc while it is turned on its edge, set down. Seconds each way; where
     * it stands, pixels from its place on the keg (the keg's own frame), and how far it leans, degrees.
     */
    private static final float LID_MOVE = 0.42F;
    private static final Vector3f LID_LIFTED = new Vector3f(0.0F, 3.0F, 0.0F);
    private static final Vector3f LID_SWUNG = new Vector3f(-8.5F, 3.5F, 0.0F);
    private static final Vector3f LID_LEANING = new Vector3f(-9.75F, -9.75F, 0.0F);
    private static final float LID_TURN = 68.0F;
    /** How hard a brew nobody watches seems to work, and how often this client looks whether it does, seconds. */
    private static final float IDLE_VIGOUR = 0.6F;
    private static final float UNWATCHED_CHECK = 4.0F;
    /** In a keg without liquid, what is put in lies on slats across its top, as meat set to dry: their top, pixels. */
    private static final float RACK_TOP = 14.0F;
    /** Rings from where a thing lands on the liquid, and the small ones of things bobbing on it every so many seconds. */
    private static final float LANDING_RINGS = 0.6F;
    private static final float BOB_RINGS = 0.12F;
    private static final float BOB_RING_EVERY = 1.6F;
    private static final float BOB_RING_SPREAD = 1.8F;
    /** Where things float, pixels from the middle; how they bob. */
    private static final float FLOAT_RADIUS = 3.2F;
    private static final Vector3f RISE = new Vector3f(0.0F, 3.0F, 0.0F);
    private static final Vector3f DRIFT_UP = new Vector3f(0.0F, 1.6F, 0.0F);
    /**
     * Water in a keg: clear, but dark, as water stands in wood, the biome's own colour only half of it; a little uneven, so that it
     * does not read as a painted surface.
     */
    private static final int WATER_ALPHA = 0x8C;
    private static final int WATER_IN_WOOD = 0x1E2B30;
    private static final float WATER_MOTTLE = 0.1F;
    private static final int DRINK_ALPHA = 0xF0;
    /** Bubbles a second while it ferments, on top of the drink's own fizz; how hard it seethes along the staves then. */
    private static final float FERMENT_BUBBLES = 1.5F;
    private static final float FERMENT_SEETHE = 0.2F;
    /** A brew's yeast head: how much of the surface it covers at its height, and when in the brew it has collapsed. */
    private static final float KRAUSEN = 0.9F;
    private static final float KRAUSEN_GONE = 0.85F;
    /**
     * Several things put in at once (a recipe picked in the book, a stack shared out) jump in one after the other from the player at
     * the bottom of the screen ({@link FromPlayer}): seconds between them, seconds in the air, how high they start over the liquid
     * and how high the arc goes, pixels.
     */
    private static final float ARRIVE_STAGGER = 0.16F;
    private static final float ARRIVE_TIME = 0.4F;
    private static final float ARRIVE_FROM = 9.0F;
    private static final float ARRIVE_ARC = 4.0F;
    /** Where a bucket or bottle poured at the opening is drawn from and how high, pixels. */
    private static final float POUR_FROM_ABOVE = 9.0F;
    private static final float POUR_SIZE = 4.2F;
    /** The top of the keg's walls, and how far over it the lip of what is poured is held, pixels. */
    private static final float RIM = 16.0F;
    private static final float POUR_OVER_RIM = 1.5F;
    /**
     * The badge, drawn in the pixels of the icon of what ferments (16 of them, centred), as the pot's: a dark plate with cut corners,
     * five cells of warmth over the icon (the keg's own lit and taller, the ones the recipe takes marked under them), the bar of how
     * far it has fermented under it and the time left under the bar, minutes and seconds counting down. Bar and time go grey while
     * the lid is off and the brew waits. Done, it shows what is ready and how many servings, and swells with a chime as it gets
     * there.
     */
    private static final int PLATE_COLOR = 0x8C1E140C;
    private static final int DIAL_COLOR = 0xFFFFCB6B;
    private static final int WAITING_COLOR = 0xFFA8A49C;
    private static final int TRACK_COLOR = 0x50FFF2DC;
    private static final int CELL_LEFT = -7;
    private static final int CELL_STEP = 3;
    private static final int CELL_TOP = -14;
    private static final int FITS_TOP = -10;
    private static final int TEXT_TOP = 13;
    private static final String[] COLON = {".", "#", ".", "#", "."};
    private static final String[] TIMES = {"...", "#.#", ".#.", "#.#", "..."};
    /** Done: how much the badge swells and for how long, seconds; how it breathes while what is ready waits. */
    private static final float DONE_SWELL = 0.35F;
    private static final float DONE_TIME = 0.6F;
    private static final float READY_BREATH = 0.04F;
    /** A drink drawn at the tap, millibuckets: what a serving is counted in. */
    private static final int SERVING = 250;
    private static final Vector3f ICON_MIDDLE = new Vector3f(8.0F, 8.0F, 8.0F);
    /** The thermometer's colours, cold to hot, and the dim cells. */
    private static final int[] WARMTH_COLORS = {0xFF6FB8FF, 0xFFB5E3FF, 0xFFFFF4D8, 0xFFFFB45A, 0xFFFF6A3C};
    private static final int BAR_LEFT = -7;
    private static final int BAR_TOP = 10;
    private static final int BAR_LENGTH = 14;
    /** A container from the inventory offered under the tap to an empty hand: how see-through, how high it is drawn, pixels. */
    private static final int OFFERED = 0x8CFFFFFF;
    private static final float OFFER_SIZE = 4.5F;
    /** How fast what floats follows the level, per second: it sinks with a drink drawn as the stream runs. */
    private static final float LEVEL_FOLLOW = 1.6F;

    private final Shape shape;
    private final RecipeBook book = new RecipeBook(new KegBook());
    private final UsableCache usable = new UsableCache();
    private final ItemStack[] shown = new ItemStack[ITEM_NODES.length];
    /** Things just landed on the liquid, to splash where they lie once they are placed there. */
    private final boolean[] splashAt = new boolean[ITEM_NODES.length];
    /** Seconds until each floating thing sends its next small ring. */
    private final float[] ringIn = {0.7F, 1.6F, 2.4F, 1.1F};
    /** Seconds since each thing set off from the player (below 0 while it waits its turn, NaN once in), and where it set off. */
    private final float[] arriveAge = new float[ITEM_NODES.length];
    private final Vector3f[] arriveFrom = new Vector3f[ITEM_NODES.length];
    private float time;
    /** How far the lid is on its way off, 0 on the keg to 1 leaning on its side. */
    private float lid;
    private boolean wasOpen;
    /** The lid as the cook last clicked it, shown at once while the keg answers; null once the keg agrees. */
    private Boolean lidAsked;
    private float lidAskedAt;
    private float level;
    private float bubbles;
    private float rising;
    private float shownTurn = Float.NaN;
    private String drawnDial = "";
    private int drawnWarmth = -1;
    /** What the keg makes of what is in it, or what waits in its output: the badge's icon. */
    private ItemStack dish = ItemStack.EMPTY;
    private Item shownDish;
    /** Servings in the keg: its liquid in quarters of a bucket, or what waits in its output; 0 while something ferments. */
    private int servings;
    /** What is there was brewed here and waits to be drawn or taken: the badge breathes. */
    private boolean ready;
    private boolean sawFermenting;
    private float doneAt = -10.0F;
    private int liquidColor;
    /** The drink in the keg as it looks, and the one it ferments into, when known. */
    private KegLiquids.Drink drink;
    private KegLiquids.Drink brewing;
    private ItemStack offered = ItemStack.EMPTY;
    private Item offeredItem;
    private final KegDraughts draughts = new KegDraughts();
    private final KegFerment ferment = new KegFerment();
    private NodeView kegNode;
    private float unwatchedCheck;
    private boolean unwatchedWorks;
    private KitchenMotions.Pour pour;

    public KegBehavior(StationView view, Shape shape) {
        this.shape = shape;
        Arrays.fill(shown, ItemStack.EMPTY);
        Arrays.fill(arriveAge, Float.NaN);
    }

    public static KegBehavior lying(StationView view) {
        return new KegBehavior(view, Shape.LYING);
    }

    public static KegBehavior standing(StationView view) {
        return new KegBehavior(view, Shape.STANDING);
    }

    private KitchenKeg keg(StationView view) {
        return KitchenKeg.of(view.level().getBlockEntity(view.pos()));
    }

    @Override
    public void onSceneReady(StationView view) {
        drawnDial = "";
        drawnWarmth = -1;
        shownDish = null;
        shownTurn = Float.NaN;
        if (pour != null) {
            pour.stop();
            pour = null;
        }
        view.node("dial").ifPresent(dial -> dial.setTint(DIAL_COLOR));
        view.node("dial_track").ifPresent(track -> track.setTint(TRACK_COLOR));
        view.node("thermo").ifPresent(track -> track.setTint(TRACK_COLOR));
        view.node("badge").ifPresent(plate -> {
            plate.setTint(PLATE_COLOR);
            MeshShape.Builder plateShape = MeshShape.builder();
            // Taller than the pot's: the time runs under the bar.
            PotBehavior.pixels(plateShape, -10, -16, 20, 35);
            PotBehavior.pixels(plateShape, -9, -17, 18, 1);
            PotBehavior.pixels(plateShape, -9, 19, 18, 1);
            plate.setShape(plateShape.build(), BADGE_TEXTURE, true);
        });
        // The badge stays in sight over the keg, its lid and whatever stands beside it, as the pot's does.
        for (String part : BADGE_PARTS) {
            view.node(part).ifPresent(node -> node.setOnTop(true));
        }
        view.node("book_open").ifPresent(node -> node.setPainter(book));
        view.node("offer").ifPresent(node -> node.setOnTop(true));
        draughts.ready(view);
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
        draughts.clear(view);
        refresh(view);
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        refresh(view);
    }

    /** The lid is still falling shut after the last one left: the scene keeps standing in for the block until it lies on the rim. */
    @Override
    public boolean holdsBlock(StationView view) {
        return !view.isInUse() && isLidUp();
    }

    private boolean isLidUp() {
        return lid > 0.0F;
    }

    /** What the inventory at the keg shows: what ferments in it, and what pours into it or is filled from it. */
    private boolean usable(StationView view, ItemStack stack) {
        KitchenKeg keg = keg(view);
        if (keg == null) {
            return false;
        }
        if (keg.handson$isVessel(stack)) {
            return true;
        }
        return keg.handson$recipes().stream().anyMatch(recipe -> recipe.ingredients().stream().anyMatch(ingredient -> ingredient.test(stack)));
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Click click && click.node().equals("lid") && click.button() == 0) {
            toggleLid(view);
        } else if (event instanceof SceneEvent.Click click && click.node().equals("book_open")) {
            book.click(view);
        } else if (event instanceof SceneEvent.Click click && click.node().equals("tap") && click.button() == 0 && !offered.isEmpty()) {
            boolean all = Screen.hasShiftDown();
            draw(view, offered, false, all);
            CompoundTag how = new CompoundTag();
            how.putString(KegStation.AT, KegStation.TAP);
            how.putBoolean(KegStation.FROM_INVENTORY, true);
            how.putBoolean(KegStation.ALL, all);
            view.sendAction(KegStation.ACTION_USE, how);
        }
    }

    /** A container clicked on the tap fills; a bucket or a drink clicked on the opening pours in; the book takes its clicks. */
    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if ("book_open".equals(node)) {
            book.click(view);
            return true;
        }
        if ("lid".equals(node)) {
            toggleLid(view);
            return true;
        }
        KitchenKeg keg = keg(view);
        if (keg == null) {
            return false;
        }
        boolean tap = "tap".equals(node) && keg.handson$canTap(stack);
        boolean pour = ("mouth".equals(node) || "drink".equals(node)) && keg.handson$canPour(stack);
        if (!tap && !pour) {
            return false;
        }
        if (tap) {
            draw(view, stack, true, shift);
        }
        CompoundTag how = new CompoundTag();
        how.putString(KegStation.AT, tap ? KegStation.TAP : KegStation.MOUTH);
        how.putBoolean(KegStation.ALL, shift);
        view.sendAction(KegStation.ACTION_USE, how);
        return true;
    }

    /** Whether the lid is off: as the cook clicked it, until the keg says so too. */
    private boolean isLidOff(StationView view) {
        boolean off = view.state().getControl(KegStation.LID) > 0.5F;
        if (lidAsked != null && (lidAsked == off || time - lidAskedAt > 1.0F)) {
            lidAsked = null;
        }
        return lidAsked != null ? lidAsked : off;
    }

    /** A click on the lid takes it off, or puts it back on. */
    private void toggleLid(StationView view) {
        boolean open = !isLidOff(view);
        lidAsked = open;
        lidAskedAt = time;
        CompoundTag data = new CompoundTag();
        data.putBoolean(KegStation.OPEN, open);
        view.sendAction(KegStation.ACTION_LID, data);
    }

    /** The cook draws a drink into {@code vessel}: its mug comes to the tap at the click, before the keg answers. */
    private void draw(StationView view, ItemStack vessel, boolean fromHand, boolean all) {
        KitchenKeg keg = keg(view);
        if (keg == null || keg.handson$fluid() == Fluids.EMPTY) {
            return;
        }
        Fluid fluid = keg.handson$fluid();
        int color = colourOf(view, keg, fluid);
        draughts.draw(view, vessel, BuiltInRegistries.FLUID.getKey(fluid), color, KegLiquids.of(fluid).orElse(null), fromHand, all);
    }

    @Override
    public void onEffect(StationView view, ResourceLocation effect, String node, CompoundTag data) {
        Fluid fluid = fluidOf(data.getString(KegStation.FLUID));
        int color = 0xFF000000 | colourOf(view, keg(view), fluid) & 0xFFFFFF;
        if (KegStation.EFFECT_POURED.equals(effect)) {
            poured(view, data, color);
        } else if (KegStation.EFFECT_TAPPED.equals(effect) && fluid != Fluids.EMPTY) {
            Item vessel = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(data.getString(KegStation.VESSEL)));
            draughts.tapped(view, new ItemStack(vessel), BuiltInRegistries.FLUID.getKey(fluid), colourOf(view, keg(view), fluid),
                    KegLiquids.of(fluid).orElse(null), data.getInt(KegStation.AMOUNT));
        }
    }

    /**
     * Something was poured in at the opening: the bucket or bottle is tipped over it and its liquid runs in a stream, the level
     * waiting for it. Without a container to show, it only splashes.
     */
    private void poured(StationView view, CompoundTag data, int color) {
        Item vessel = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(data.getString(KegStation.VESSEL)));
        NodeView hand = view.node("pour_hand").orElse(null);
        if (hand == null || vessel == null || vessel == Items.AIR || !view.isFocused()) {
            view.node("drink").ifPresent(drink -> drink.ripple(shape.middleX(), shape.middleZ(), 0.6F));
            view.spawnOnLiquid(DiegesisParticles.SPLASH, "drink", 6, color, RISE);
            view.playSound(SoundEvents.BUCKET_EMPTY, "drink", 0.7F, 1.0F);
            return;
        }
        if (pour != null) {
            pour.stop();
        }
        float surface = surfaceY();
        Vector3f onto = new Vector3f(8.5F, surface, 8.5F);
        Vector3f from = view.cursorOn(shape.full() + POUR_FROM_ABOVE).orElse(new Vector3f(8.0F, shape.full() + POUR_FROM_ABOVE, 4.0F));
        // Tipped over the rim of the opening, not down in the keg: the stream falls to the liquid.
        pour = new KitchenMotions.Pour(hand, "keg_pour", new ItemStack(vessel), from, onto, this::surfaceY, "drink", POUR_SIZE, -1.0F, 0.0F)
                .lipAtLeast(RIM + POUR_OVER_RIM);
        float before = pour.pouredBy() - pour.flowTime();
        float flow = pour.flowTime();
        view.node("drink").ifPresent(drink -> drink.holdLevel(before, flow));
    }

    private static Fluid fluidOf(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null || id.isEmpty() ? Fluids.EMPTY : BuiltInRegistries.FLUID.get(key);
    }

    /** Where a drink leaves the tap's hole in the scene, turned with the keg, just out of the stave. */
    private Vector3f tapPoint(StationView view) {
        Vector3f hole = new Vector3f(shape.tap()).sub(8.0F, 0.0F, 8.0F).add(0.0F, 0.0F, -0.3F);
        return view.node("keg").map(keg -> keg.sceneMatrix().transformPosition(hole)).orElse(new Vector3f(shape.tap()));
    }

    /**
     * Whether the keg works while nobody is at it: this client is not told how far a brew is, but it knows what lies in the keg, the
     * recipes and the blocks round it, so it tells for itself whether a recipe is in and the keg warm or cool enough for it. Looked at
     * again as often as the keg itself feels its warmth.
     */
    private boolean worksUnwatched(StationView view, float dt) {
        unwatchedCheck -= dt;
        if (unwatchedCheck > 0.0F) {
            return unwatchedWorks;
        }
        unwatchedCheck = UNWATCHED_CHECK;
        KitchenKeg keg = keg(view);
        unwatchedWorks = false;
        if (keg != null && view.state().getItem(KegStation.SERVED_SLOT).isEmpty()) {
            Optional<KitchenKeg.Fermenting> recipe = making(view, keg);
            if (recipe.isPresent()) {
                keg.handson$feelTemperature();
                unwatchedWorks = KegLogic.fitsWarmth(keg.handson$data().get(2), recipe.get().temperature());
            }
        }
        return unwatchedWorks;
    }

    /** A point of the keg in its model's pixels, in the scene: turned with the keg. */
    private Vector3f kegPoint(float x, float y, float z) {
        Vector3f local = new Vector3f(x - 8.0F, y, z - 8.0F);
        return inScene(local);
    }

    private Vector3f inScene(Vector3f local) {
        return kegNode == null ? local.add(8.0F, 0.0F, 8.0F) : kegNode.sceneMatrix().transformPosition(local);
    }

    /** The colour of foam on what the keg holds or brews. */
    private int foamColor() {
        if (brewing != null && brewing.foamColor() != 0) {
            return brewing.foamColor();
        }
        return drink != null && drink.foamColor() != 0 ? drink.foamColor() : KegLiquids.HEAD;
    }

    /** The way the tap faces in the scene, level. */
    private static Vector3f tapFacing(StationView view) {
        return view.node("keg").map(keg -> keg.sceneMatrix().transformDirection(new Vector3f(0.0F, 0.0F, -1.0F)))
                .map(way -> way.set(way.x, 0.0F, way.z).normalize()).orElse(new Vector3f(0.0F, 0.0F, -1.0F));
    }

    private float surfaceY() {
        return shape.floor() + (shape.full() - shape.floor()) * level;
    }

    private void refresh(StationView view) {
        StationState state = view.state();
        ItemStack[] items = state.getItems(KegStation.INGREDIENTS);
        List<Integer> arrivals = new java.util.ArrayList<>();
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
            float height = surfaceY() + ARRIVE_FROM;
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

    /** Something lands in the keg: a splash on the liquid and a plop, or a knock on the slats of a dry keg. */
    private void splashIn(StationView view, int index) {
        if (view.state().getTank(KegStation.DRINK).amount() == 0) {
            // On the dry slats: a soft knock on wood.
            view.playSound(SoundEvents.WOOD_HIT, ITEM_NODES[index], 0.3F, 1.2F + 0.05F * index);
            return;
        }
        // Splashes and drops of the liquid itself where it lies on it (in the frame it is placed there), and rings across it.
        splashAt[index] = true;
        view.playSound(PotSounds.PLOP, ITEM_NODES[index], 0.45F, 0.95F + 0.04F * index);
    }

    /**
     * The liquid's colour and nature: water as the biome's, a drink as {@link KegLiquids} knows it, anything else as the drink poured
     * from it. While it ferments, the colour moves from the liquid it was to the drink it becomes.
     */
    private void colour(StationView view) {
        KitchenKeg keg = keg(view);
        if (keg == null) {
            return;
        }
        Fluid fluid = keg.handson$fluid();
        drink = KegLiquids.of(fluid).orElse(null);
        int base = colourOf(view, keg, fluid);
        float progress = view.state().getControl(KegStation.PROGRESS);
        Optional<KitchenKeg.Fermenting> making = making(view, keg);
        brewing = making.flatMap(recipe -> KegLiquids.of(recipe.resultFluid())).orElse(null);
        ItemStack served = view.state().getItem(KegStation.SERVED_SLOT);
        dish = making.map(recipe -> recipe.resultFluid() != Fluids.EMPTY ? keg.handson$drinkOf(recipe.resultFluid()) : recipe.resultItem())
                .filter(stack -> !stack.isEmpty()).orElse(ItemStack.EMPTY);
        boolean wasReady = ready;
        servings = 0;
        ready = false;
        if (dish.isEmpty() && !served.isEmpty()) {
            dish = served;
            servings = served.getCount();
            ready = true;
        } else if (dish.isEmpty() && fluid != Fluids.EMPTY && keg.handson$amount() > 0) {
            // Whatever the keg holds, as it is drawn or poured, and how many servings of it.
            dish = keg.handson$drinkOf(fluid);
            servings = keg.handson$amount() / SERVING;
            ready = keg.handson$recipes().stream().anyMatch(recipe -> recipe.resultFluid() != Fluids.EMPTY && recipe.resultFluid().isSame(fluid));
        }
        if (progress > 0.0F) {
            sawFermenting = true;
        } else if (ready && !wasReady && sawFermenting) {
            sawFermenting = false;
            done(view);
        }
        int colour = base;
        if (making.isPresent() && progress > 0.0F) {
            KitchenKeg.Fermenting recipe = making.get();
            int result = recipe.resultFluid() != Fluids.EMPTY ? colourOf(view, keg, recipe.resultFluid())
                    : 0xFF000000 | FoodColors.main(recipe.resultItem());
            // What steeps in it colours it early, as a wort does: it is the drink's colour by a third of the way.
            colour = blend(base, result, KitchenMotions.smooth(progress * 3.0F));
        }
        liquidColor = colour & 0xFFFFFF;
        int shownColour = colour;
        view.node("drink").ifPresent(node -> {
            node.setLiquidColor(shownColour);
            node.setMottle(drink != null ? drink.mottle() : keg.handson$fluid().isSame(Fluids.WATER) ? WATER_MOTTLE : 0.0F);
        });
    }

    private static int colourOf(StationView view, KitchenKeg keg, Fluid fluid) {
        if (fluid == Fluids.EMPTY) {
            return WATER_ALPHA << 24;
        }
        if (fluid.isSame(Fluids.WATER)) {
            return WATER_ALPHA << 24 | blend(BiomeColors.getAverageWaterColor(view.level(), view.pos()), WATER_IN_WOOD, 0.5F) & 0xFFFFFF;
        }
        Optional<KegLiquids.Drink> known = KegLiquids.of(fluid);
        if (known.isPresent()) {
            return known.get().color();
        }
        ItemStack drink = keg == null ? ItemStack.EMPTY : keg.handson$drinkOf(fluid);
        return DRINK_ALPHA << 24 | (drink.isEmpty() ? 0xB08850 : KitchenLooks.liquidColor(drink) & 0xFFFFFF);
    }

    /** The recipe what lies in the keg makes: its ingredients there, its base the liquid in it. */
    private static Optional<KitchenKeg.Fermenting> making(StationView view, KitchenKeg keg) {
        List<ItemStack> items = Arrays.stream(view.state().getItems(KegStation.INGREDIENTS)).filter(stack -> !stack.isEmpty()).toList();
        if (items.isEmpty()) {
            return Optional.empty();
        }
        Fluid fluid = keg.handson$fluid();
        return keg.handson$recipes().stream().filter(recipe -> recipe.base().isSame(fluid) || recipe.base() == Fluids.EMPTY && fluid == Fluids.EMPTY)
                .filter(recipe -> matches(recipe, items)).findFirst();
    }

    private static boolean matches(KitchenKeg.Fermenting recipe, List<ItemStack> items) {
        List<net.minecraft.world.item.crafting.Ingredient> wanted = recipe.ingredients().stream().filter(ingredient -> !ingredient.isEmpty()).toList();
        if (wanted.size() != items.size()) {
            return false;
        }
        boolean[] used = new boolean[items.size()];
        for (var ingredient : wanted) {
            boolean found = false;
            for (int i = 0; i < items.size() && !found; i++) {
                if (!used[i] && ingredient.test(items.get(i))) {
                    used[i] = true;
                    found = true;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private void tooltip(StationView view) {
        KitchenKeg keg = keg(view);
        view.node("drink").ifPresent(node -> {
            if (keg == null || keg.handson$fluid() == Fluids.EMPTY) {
                node.setTooltip(Component.empty());
                return;
            }
            ItemStack drink = keg.handson$drinkOf(keg.handson$fluid());
            Component name = drink.isEmpty() ? Component.literal(BuiltInRegistries.FLUID.getKey(keg.handson$fluid()).getPath())
                    : drink.getHoverName();
            int left = view.state().data().getInt(KegStation.LEFT);
            node.setTooltip(left > 0 && view.state().getControl(KegStation.PROGRESS) > 0.0F
                    ? Component.translatable("handson_delight.keg.fermenting", name, Math.max(1, Math.round(left / 1200.0F)))
                    : Component.translatable("handson_delight.keg.holds", name, keg.handson$amount()));
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
        kegNode = view.node("keg").orElse(null);
        boolean inUse = view.isInUse();
        // The keg comes shut, as it stands; the cook takes the lid off.
        boolean lidOff = inUse && isLidOff(view);
        if (lidOff && !wasOpen) {
            view.playSound(SoundEvents.BARREL_OPEN, "lid", 0.45F, 1.05F + view.level().random.nextFloat() * 0.1F);
            if (view.state().getControl(KegStation.PROGRESS) > 0.0F || ready) {
                ferment.opened(view, this::kegPoint, shape, view.state().getTank(KegStation.DRINK).amount() == 0, foamColor());
            }
        }
        wasOpen = lidOff;
        swingLid(view, dt, lidOff);
        // Nothing goes in through the lid.
        view.node("mouth").ifPresent(node -> node.setEnabled(lidOff && lid >= 1.0F));
        // The scene's keg exactly while the block's own is left out of the world: the two swap in one frame.
        boolean open = view.isBlockHidden();
        for (String id : SHOWN_IN_USE) {
            view.node(id).ifPresent(node -> node.setVisible(open));
        }
        turnToBlock(view);
        level = Mth.lerp(Math.min(1.0F, dt * LEVEL_FOLLOW), level, view.state().getTank(KegStation.DRINK).amount() / 100.0F);
        boolean dry = view.state().getTank(KegStation.DRINK).amount() == 0 && level <= 0.02F;
        boolean onRack = dry && (Arrays.stream(shown).anyMatch(stack -> !stack.isEmpty())
                || !view.state().getItem(KegStation.SERVED_SLOT).isEmpty());
        view.node("rack").ifPresent(node -> node.setVisible(open && onRack));
        float surface = dry ? RACK_TOP : surfaceY();
        floaters(view, dt, open, open && level > 0.02F, surface);
        view.node("served").ifPresent(node -> node.moveTo(0.0F, surface + 0.25F, 0.0F, 0.0F));
        book.frame(view, dt);
        float progress = view.state().getControl(KegStation.PROGRESS);
        boolean fermenting = progress > 0.0F;
        liquidLife(view, dt, open, fermenting, progress);
        // Under its lid a working brew breathes, most at its height, not at all while it is too cold or too warm to work.
        float vigour;
        if (inUse) {
            int now = Math.round(view.state().getControl(KegStation.TEMPERATURE));
            int wanted = Math.round(view.state().getControl(KegStation.WANTED));
            boolean works = fermenting && !lidOff && (wanted == 0 || KegLogic.fitsWarmth(now, wanted));
            vigour = works ? 0.35F + 0.65F * Mth.sin(Mth.PI * Math.min(1.0F, progress / 0.85F)) : 0.0F;
        } else {
            vigour = worksUnwatched(view, dt) ? IDLE_VIGOUR : 0.0F;
        }
        ferment.frame(view, dt, this::kegPoint, shape, vigour, view.state().getTank(KegStation.DRINK).amount() == 0, view.isBlockHidden(),
                tapPoint(view), foamColor());
        if (pour != null && !pour.frame(view, dt)) {
            pour = null;
        }
        draughts.frame(view, dt, tapPoint(view), tapFacing(view), Float.isNaN(shownTurn) ? 0.0F : shownTurn,
                view.node("drink").orElse(null));
        offerContainer(view, inUse);
        // The badge comes with the camera as the book does, growing as it flies in and going as it flies back.
        float appear = RecipeBook.appear(view);
        badge(view, inUse && RecipeBook.isWorkedAt(view) && appear > RecipeBook.SPECK, lidOff);
        float swell = badgeSwell();
        for (String part : BADGE_PARTS) {
            view.node(part).ifPresent(node -> node.scaleTo(appear * swell, 0.0F));
        }
    }

    /**
     * The lid is taken off and set down leaning on the keg's side when the cook clicks it, and put back on with another click or
     * when the last one goes; it knocks as it is set down either way.
     */
    private void swingLid(StationView view, float dt, boolean off) {
        float before = lid;
        lid = Mth.clamp(lid + (off ? dt : -dt) / LID_MOVE, 0.0F, 1.0F);
        if (before < 1.0F && lid >= 1.0F) {
            view.playSound(SoundEvents.WOOD_PLACE, "lid", 0.35F, 1.1F + view.level().random.nextFloat() * 0.1F);
        } else if (before > 0.0F && lid <= 0.0F) {
            view.playSound(SoundEvents.BARREL_CLOSE, "lid", 0.4F, 1.0F + view.level().random.nextFloat() * 0.1F);
        }
        float k = KitchenMotions.smooth(lid);
        float knock = lid <= 0.0F ? ferment.bump() : 0.0F;
        // Up off the rim, over and down: a curve through two points, so that it never cuts through the keg's edge.
        float u = 1.0F - k;
        Vector3f at = new Vector3f(LID_LIFTED).mul(3.0F * u * u * k).add(new Vector3f(LID_SWUNG).mul(3.0F * u * k * k))
                .add(new Vector3f(LID_LEANING).mul(k * k * k));
        // It is turned on its edge once it is clear of the rim.
        float turn = LID_TURN * KitchenMotions.smooth((lid - 0.15F) / 0.7F);
        view.node("lid").ifPresent(node -> {
            node.moveTo(at.x, at.y + knock, at.z, 0.0F);
            node.rotateTo(0.0F, 0.0F, turn, 0.0F);
        });
    }

    /**
     * The liquid lives as the drink does: its own foam, fizz and what rises off it; while a brew ferments, a yeast head grows on it,
     * peaks and collapses brown before the brew is done, and what ferments sends up bubbles.
     */
    private void liquidLife(StationView view, float dt, boolean open, boolean fermenting, float progress) {
        float foam = drink == null ? 0.0F : drink.foam();
        int foamColor = drink == null ? 0 : drink.foamColor();
        if (fermenting && brewing != null && brewing.brewed()) {
            float head = KRAUSEN * Mth.sin(Mth.PI * Math.min(1.0F, progress / KRAUSEN_GONE));
            if (head > foam) {
                foam = head;
                foamColor = blend(KegLiquids.HEAD, KegLiquids.SPENT_HEAD, KitchenMotions.smooth((progress - 0.4F) / 0.6F));
            }
        }
        float shownFoam = open && level > 0.02F ? foam : 0.0F;
        int shownFoamColor = foamColor;
        boolean yeastHead = fermenting && brewing != null && brewing.brewed();
        view.node("drink").ifPresent(node -> {
            node.setFoam(shownFoam, shownFoamColor);
            node.setAgitation(fermenting ? 0.04F : 0.01F);
            node.setEdgeBoil(fermenting && !yeastHead ? FERMENT_SEETHE : 0.0F);
        });
        if (!open || level <= 0.02F) {
            return;
        }
        float fizz = (drink == null ? 0.0F : drink.fizz()) + (fermenting ? FERMENT_BUBBLES : 0.0F);
        bubbles += dt * fizz;
        if (bubbles >= 1.0F) {
            view.spawnInLiquid(DiegesisParticles.BUBBLE, "drink", (int) bubbles, 0xE0FFFFFF, RISE);
            bubbles -= (int) bubbles;
        }
        if (drink != null && drink.rising() != null) {
            rising += dt * drink.risingRate();
            if (rising >= 1.0F) {
                view.spawnOnLiquid(drink.rising(), "drink", (int) rising, drink.risingColor(), DRIFT_UP);
                rising -= (int) rising;
            }
        }
    }

    /** What lies in the keg floats on the liquid, a little apart, bobbing; with no liquid it lies on the floor. */
    private void floaters(StationView view, float dt, boolean open, boolean afloat, float surface) {
        int present = 0;
        for (ItemStack stack : shown) {
            present += stack.isEmpty() ? 0 : 1;
        }
        int rank = 0;
        for (int i = 0; i < ITEM_NODES.length; i++) {
            int index = i;
            boolean there = !shown[i].isEmpty() && open;
            float place = rank * Mth.TWO_PI / Math.max(1, present) + time * (afloat ? 0.15F : 0.0F);
            if (!shown[i].isEmpty()) {
                rank++;
            }
            float bob = afloat ? Mth.sin(time * 0.9F + index * 1.3F) * 0.07F : 0.0F;
            float y = (afloat || surface >= RACK_TOP ? surface : shape.floor()) + 0.25F + bob;
            Vector3f at = new Vector3f(Mth.cos(place) * FLOAT_RADIUS, y, Mth.sin(place) * FLOAT_RADIUS);
            boolean waiting = arrive(view, i, dt, at);
            if (splashAt[i] && !waiting) {
                splashAt[i] = false;
                int color = 0xFF000000 | liquidColor;
                Vector3f where = new Vector3f(at).add(8.0F, 0.0F, 8.0F);
                view.spawnParticles(DiegesisParticles.SPLASH, where, new Vector3f(0.0F, 9.0F, 0.0F), 1.4F, 7, color);
                view.spawnParticles(DiegesisParticles.DROPLET, where, new Vector3f(0.0F, 14.0F, 0.0F), 2.2F, 4, color);
                ringsAt(view, where, LANDING_RINGS);
            } else if (afloat && there && !waiting) {
                // Bobbing on it, each now and then sends a small ring out.
                ringIn[i] -= dt;
                if (ringIn[i] <= 0.0F) {
                    ringIn[i] = BOB_RING_EVERY + view.level().random.nextFloat() * BOB_RING_SPREAD;
                    ringsAt(view, new Vector3f(at).add(8.0F, 0.0F, 8.0F), BOB_RINGS);
                }
            }
            view.node(ITEM_NODES[i]).ifPresent(node -> {
                node.setEnabled(there && !waiting);
                node.moveTo(at.x, at.y, at.z, 0.0F);
                node.rotateTo(Mth.sin(time * 0.5F + index) * 3.0F, -place * Mth.RAD_TO_DEG, 0.0F, 0.0F);
            });
        }
    }

    /** Rings across the liquid from a point of the scene (pixels), as strong as {@code strength}. */
    private static void ringsAt(StationView view, Vector3f where, float strength) {
        view.node("drink").ifPresent(drink -> {
            Vector3f local = new org.joml.Matrix4f(drink.sceneMatrix()).invert().transformPosition(new Vector3f(where));
            drink.ripple(local.x, local.z, strength);
        });
    }

    /**
     * A thing on its way in from the player: moves {@code at} (offset from the item's node, pixels) along its arc from where it set off
     * to its place on the liquid, and splashes as it lands. True while it still waits for its turn.
     */
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

    /** The keg's parts stand as the block faces, whichever way the scene turned to the player. */
    private void turnToBlock(StationView view) {
        BlockState state = view.level().getBlockState(view.pos());
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return;
        }
        Direction block = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        float turn = view.facing().toYRot() - block.toYRot();
        if (turn != shownTurn) {
            shownTurn = turn;
            view.node("keg").ifPresent(node -> node.rotateTo(0.0F, turn, 0.0F, 0.0F));
        }
    }

    /** With an empty hand at the tap, a container from the inventory hangs see-through under it: a click fills it. */
    private void offerContainer(StationView view, boolean inUse) {
        offered = ItemStack.EMPTY;
        NodeView node = view.node("offer").orElse(null);
        LocalPlayer player = Minecraft.getInstance().player;
        KitchenKeg keg = keg(view);
        boolean hovered = view.node("tap").map(NodeView::isHovered).orElse(false);
        if (node == null || player == null || keg == null || !inUse || !view.isFocused() || !hovered
                || !player.inventoryMenu.getCarried().isEmpty() || draughts.busy()) {
            if (node != null) {
                node.setVisible(false);
            }
            return;
        }
        int slot = KegLogic.containerSlot(keg, player.getInventory());
        node.setVisible(slot >= 0);
        if (slot < 0) {
            return;
        }
        offered = player.getInventory().getItem(slot);
        if (offeredItem != offered.getItem()) {
            offeredItem = offered.getItem();
            node.setVisual(Visual.ofItem(offeredItem).withAnchor(new Vector3f(8.0F, 8.0F, 8.0F)));
            node.setTint(OFFERED);
            node.scaleTo(OFFER_SIZE / 16.0F / 0.35F, 0.0F);
        }
        // Where the mug will be held, under the tap.
        Vector3f under = KegDraughts.heldMiddle(tapPoint(view), tapFacing(view));
        node.moveTo(under.x - 8.0F, under.y, under.z - 8.0F, 0.0F);
    }

    /** Something is done: the badge swells with a chime, and a few glints rise from the keg. */
    private void done(StationView view) {
        doneAt = time;
        view.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(), "badge", 0.35F, 1.26F);
        view.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(), "badge", 0.3F, 1.5F);
        view.playSound(PotSounds.BURST, "lid", 0.45F, 1.0F);
        view.spawnParticles(DiegesisParticles.GLINT, "lid", 8, 0xFFFFE6A0);
    }

    /** How much bigger the badge is drawn now: a swell as something gets done, a slow breath while it waits to be taken. */
    float badgeSwell() {
        float since = time - doneAt;
        if (since >= 0.0F && since < DONE_TIME) {
            return 1.0F + DONE_SWELL * Mth.sin(Mth.PI * since / DONE_TIME);
        }
        return ready ? 1.0F + READY_BREATH * (0.5F - 0.5F * Mth.cos(time * 2.4F)) : 1.0F;
    }

    /**
     * The badge while something ferments or is ready: what it makes, how warm the keg is against what the recipe takes, how far it
     * has fermented and the time left, or how many servings are ready.
     */
    private void badge(StationView view, boolean cookHere, boolean lidOff) {
        boolean shown = cookHere && !dish.isEmpty();
        view.node("badge").ifPresent(plate -> plate.setVisible(shown));
        float progress = view.state().getControl(KegStation.PROGRESS);
        int left = view.state().data().getInt(KegStation.LEFT);
        int seconds = progress > 0.0F && left > 0 ? Math.max(1, Mth.ceil(left / 20.0F)) : 0;
        int bar = ready ? BAR_LENGTH : Math.round(progress * BAR_LENGTH);
        String text = seconds >= 60 ? seconds / 60 + ":" + String.format("%02d", seconds % 60) : seconds > 0 ? Integer.toString(seconds)
                : servings > 0 ? "x" + Math.min(servings, 99) : "";
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
        boolean waiting = lidOff && progress > 0.0F;
        view.node("dial").ifPresent(node -> node.setTint(waiting ? WAITING_COLOR : DIAL_COLOR));
        int now = Mth.clamp(Math.round(view.state().getControl(KegStation.TEMPERATURE)), 1, 5);
        int wanted = Math.round(view.state().getControl(KegStation.WANTED));
        int key = now * 10 + wanted;
        if (key != drawnWarmth) {
            drawnWarmth = key;
            // Dim cells cold to hot, a mark under each warmth the recipe takes; the keg's own cell lit and taller.
            MeshShape.Builder cells = MeshShape.builder();
            for (int warmth = 1; warmth <= WARMTH_COLORS.length; warmth++) {
                PotBehavior.pixels(cells, CELL_LEFT + (warmth - 1) * CELL_STEP, CELL_TOP, 2, 3);
                if (wanted > 0 && KegLogic.fitsWarmth(warmth, wanted)) {
                    PotBehavior.pixels(cells, CELL_LEFT + (warmth - 1) * CELL_STEP, FITS_TOP, 2, 1);
                }
            }
            view.node("thermo").ifPresent(node -> node.setShape(cells.build(), BADGE_TEXTURE, true));
            MeshShape.Builder cell = MeshShape.builder();
            PotBehavior.pixels(cell, CELL_LEFT + (now - 1) * CELL_STEP, CELL_TOP - 1, 2, 5);
            view.node("thermo_now").ifPresent(node -> {
                node.setShape(cell.build(), BADGE_TEXTURE, true);
                node.setTint(WARMTH_COLORS[now - 1]);
            });
        }
        view.node("dial_track").ifPresent(node -> node.setVisible(shown));
        view.node("dial").ifPresent(node -> node.setVisible(shown));
        view.node("thermo").ifPresent(node -> node.setVisible(shown));
        // The keg's own cell blinks while it is too cold or too warm for what is in it.
        boolean fits = wanted == 0 || KegLogic.fitsWarmth(now, wanted);
        view.node("thermo_now").ifPresent(node -> node.setVisible(shown && (fits || Mth.sin(time * 6.0F) > 0.0F)));
        preview(view, shown, seconds);
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

    /** The icon of what ferments or waits, with its name and the minutes left on hover. */
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
            node.setTooltip(minutes > 0 ? Component.translatable("handson_delight.keg.fermenting", dish.getHoverName(), minutes)
                    : dish.getHoverName());
        });
    }

}
