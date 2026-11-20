package com.pockyl.handson_delight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import com.pockyl.handson_delight.RecipeParts;
import com.pockyl.handson_delight.station.BowlLogic;
import com.pockyl.handson_delight.station.BowlStation;
import com.pockyl.handson_delight.station.KitchenBowl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * What the mixing bowl shows while somebody works at it: what was dropped in lying on its floor, or on what was poured in, liquids
 * standing in it in their colour, eggs cracked over its rim, and the cook's own spoon or whisk standing in it. Taken in hand it
 * stirs as the mouse goes round; what lies in the bowl goes round with it, shrinks into the mixture and the mixture takes the
 * colour of what it becomes. Done, the cook's container dips in and comes up full, or what was made is lifted out, and goes to them.
 * While nobody works at it the bowl is the block, and Extra Delight's own renderer draws what lies in it.
 */
public final class BowlBehavior implements StationBehavior {
    private static final int SLOTS = KitchenBowl.PLACES;
    private static final int HANDS = 4;
    private static final String[] BADGE_PARTS = {"badge", "dial_track", "dial", "preview"};
    private static final Identifier BADGE_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "block/white_concrete");
    private static final Identifier EGG_YOLK = Identifier.fromNamespaceAndPath(KitchenBowl.MOD_ID, "egg_yolk");
    /** The inside, pixels: the floor, how deep the scene's liquid box is, the rim. */
    private static final float FLOOR = 1.0F;
    private static final float DEPTH = 4.5F;
    private static final float RIM = 6.0F;
    /** Things of a kind lie together; the kinds round the middle this far out, the next of a kind a little higher and turned. */
    private static final float KINDS_RADIUS = 2.4F;
    private static final float PILE_STEP = 0.25F;
    private static final float PILE_TURN = 38.0F;
    private static final float FOOD_SCALE = 0.5F;
    /** Mixed in, things shrink to this part of their size and sink this far into the mixture. */
    private static final float MIXED_SIZE = 0.45F;
    private static final float MIXED_SINK = 0.8F;
    private static final float SETTLE = 6.0F;
    /** Things coming in: how far above the bowl they set off from the player, the arc of their hop, seconds in the air, between them. */
    private static final float ARRIVE_FROM = 10.0F;
    private static final float ARRIVE_ARC = 3.0F;
    private static final float ARRIVE_TIME = 0.4F;
    private static final float ARRIVE_STAGGER = 0.14F;
    /** Liquids are poured a little to the right of the middle, the hand coming from the cook's side; one after the other. */
    private static final float POUR_X = 6.2F;
    private static final float POUR_Z = 7.2F;
    private static final float HAND_SIZE = 3.4F;
    private static final float POUR_STAGGER = 0.45F;
    /** Where an egg is knocked: on the right rim. */
    private static final Vector3f RIM_AT = new Vector3f(13.5F, RIM, 8.0F);
    /** The spoon: how far its end goes from the middle, where it rests (right and back), how big it is drawn. */
    private static final float REACH = 3.6F;
    private static final float REST_X = 2.4F;
    private static final float REST_Z = 2.0F;
    private static final float SPOON_SCALE = 0.5F;
    /** A flat spoon is drawn as its icon: its bowl at the icon's upper right, so a quarter and a half turn puts it down. */
    private static final float FLAT_TURN = 135.0F;
    private static final float FLAT_END = 1.3F;
    /** A modelled utensil (the whisk) stands on its handle: turned over, its wires go down. */
    private static final float MODEL_TURN = 180.0F;
    private static final float MODEL_END = 1.0F;
    /** How the handle leans: at rest on the rim, held a little out from the middle. */
    private static final Vector3f REST_LEAN = new Vector3f(0.5F, 1.0F, 0.4F);
    private static final float HELD_LEAN = 0.35F;
    /** A press on the spoon shorter than this is a click: it stays in the hand, stirring as the mouse goes, until the next click. */
    private static final float SPOON_CLICK = 0.3F;
    /** The spoon's end is told to the server this often while it moves, seconds. */
    private static final float SEND_INTERVAL = 0.06F;
    /** How see-through the utensil offered from the inventory stands. */
    private static final int OFFERED = 0x8CFFFFFF;
    /** What lies in the bowl goes round with the spoon and slows down after it, its turning halved in this many seconds. */
    private static final float SWIRL_HALF_LIFE = 0.6F;
    private static final float SWIRL_TAKES = 0.7F;
    private static final float STIR_VOLUME = 0.55F;
    /** How much the food colours the mixture before it is stirred in, and how far the mixture turns to the result's colour. */
    private static final float FOOD_TINT = 0.25F;
    private static final float TURNING = 0.85F;
    private static final int WATER = 0xFF3F76E4;
    /** A liquid that comes in no bottle Extra Delight knows: a pale cream. */
    private static final int UNKNOWN_LIQUID = 0xFFE6DCC4;
    private static final int BATTER_ALPHA = 0xF0;
    /** The badge: a plate, the bar and the sets under the icon. */
    private static final int PLATE_COLOR = 0x8C1E140C;
    private static final int DIAL_COLOR = 0xFFFFCB6B;
    private static final int TRACK_COLOR = 0x50FFF2DC;
    private static final int BAR_LEFT = -7;
    private static final int BAR_TOP = 10;
    private static final int BAR_LENGTH = 14;
    private static final int TEXT_TOP = 13;
    private static final String[] TIMES = {"...", "#.#", ".#.", "#.#", "..."};
    private static final float DONE_SWELL = 0.35F;
    private static final float DONE_TIME = 0.6F;
    /** What is made: a container dips in this far right of the middle; what has none is lifted out and goes to the cook. */
    private static final Vector3f DIP_AT = new Vector3f(8.6F, 0.0F, 7.6F);
    private static final float DIP_SIZE = 3.6F;
    private static final float LIFT_TIME = 0.35F;
    private static final float AWAY_TIME = 0.3F;
    private static final float YOLK_TIME = 0.4F;
    private static final Vector3f ICON_MIDDLE = new Vector3f(8.0F, 8.0F, 8.0F);
    private static final float ITEM_PIXELS = 8.0F;

    private final RecipeBook book = new RecipeBook(new BowlBook());
    private final UsableCache usable = new UsableCache();
    private List<KitchenBowl.Mixing> recipes = List.of();
    private final ItemStack[] shown = new ItemStack[SLOTS];
    private final Item[] drawnFood = new Item[SLOTS];
    private final int[] kindOf = new int[SLOTS];
    private final int[] layerOf = new int[SLOTS];
    private int kinds;
    private final Vector3f[] at = new Vector3f[SLOTS];
    /** Seconds until each thing sets off from the player (NaN: not coming), and its seconds in the air once it has. */
    private final float[] arriveIn = new float[SLOTS];
    private final float[] flying = new float[SLOTS];
    private final Vector3f[] arriveFrom = new Vector3f[SLOTS];
    private final KitchenMotions.Motion[] cracking = new KitchenMotions.Motion[SLOTS];
    /** Pours and cracks waiting their turn, and those going on in the hands. */
    private final Deque<ItemStack> toPour = new ArrayDeque<>();
    private final KitchenMotions.Motion[] hands = new KitchenMotions.Motion[HANDS];
    private int nextHand;
    private float pourClock;
    private KitchenMotions.Dip dip;
    private float time;
    private float level;
    private int liquidColor = WATER;
    private boolean wet;
    private int foodColor = 0xFFE8DCC0;
    private int shownColor;
    private float swirlAngle;
    private float swirl;
    /** The spoon: its end, pixels from the middle of the floor; who holds it; the utensil drawn. */
    private float tipX = REST_X;
    private float tipZ = REST_Z;
    private float tipAngle = Float.NaN;
    private boolean stroking;
    private boolean heldByClick;
    private float strokeStart;
    private float sendClock;
    private Item drawnUtensil;
    private boolean flatUtensil;
    private final Quaternionf lean = new Quaternionf();
    private String drawnDial = "";
    private Item shownIcon;
    private Optional<KitchenBowl.Mixing> making = Optional.empty();
    private KitchenBowl.Mixing lastMaking;
    private int pouredSeen = -1;
    private int madeSeen = -1;
    private int stirredSeen;
    private float doneAt = -10.0F;
    /** What was made of a recipe without a container, lifted out of the bowl: seconds since it started (NaN: none). */
    private float lifting = Float.NaN;
    private Vector3f liftAway = new Vector3f();
    /** The yolk of an egg cracked for its white, on its way to the cook: seconds (NaN: none). */
    private float yolkFlying = Float.NaN;
    private final Vector3f yolkStart = new Vector3f();
    private final Vector3f yolkAway = new Vector3f();

    public BowlBehavior(StationView view) {
        Arrays.fill(shown, ItemStack.EMPTY);
        Arrays.fill(arriveIn, Float.NaN);
        Arrays.fill(flying, Float.NaN);
        for (int i = 0; i < SLOTS; i++) {
            at[i] = new Vector3f();
            arriveFrom[i] = new Vector3f();
        }
    }

    @Override
    public void onSceneReady(StationView view) {
        drawnDial = "";
        shownIcon = null;
        drawnUtensil = null;
        shownColor = 0;
        Arrays.fill(drawnFood, null);
        recipes = KitchenBowl.recipes(view.level());
        view.node("dial").ifPresent(dial -> dial.setTint(DIAL_COLOR));
        view.node("dial_track").ifPresent(track -> track.setTint(TRACK_COLOR));
        view.node("badge").ifPresent(plate -> {
            plate.setTint(PLATE_COLOR);
            MeshShape.Builder plateShape = MeshShape.builder();
            PotBehavior.pixels(plateShape, -10, -12, 20, 31);
            PotBehavior.pixels(plateShape, -9, -13, 18, 1);
            PotBehavior.pixels(plateShape, -9, 19, 18, 1);
            plate.setShape(plateShape.build(), BADGE_TEXTURE, true);
        });
        for (String part : BADGE_PARTS) {
            view.node(part).ifPresent(node -> node.setOnTop(true));
        }
        view.node("book_open").ifPresent(node -> node.setPainter(book));
        view.node("batter").ifPresent(batter -> {
            batter.setWakeLook(2.5F, 0.5F);
            batter.setFloatingWake(0.0F);
        });
        StationState state = view.state();
        pouredSeen = state.data().getIntOr(BowlStation.POURED_COUNT, 0);
        madeSeen = state.data().getIntOr(BowlStation.MADE_COUNT, 0);
        stirredSeen = state.data().getIntOr(BowlStation.STIRRED, 0);
        refresh(view);
    }

    @Override
    public void onOpen(StationView view) {
        recipes = KitchenBowl.recipes(view.level());
        usable.clear();
        view.setInventoryFilter(stack -> usable.test(stack, it -> usable(view, it)));
        book.opened(view);
        // What the cook came with is what they meant for the bowl, as with a right click on it: an egg is cracked for its white, as the
        // bowl's own click cracks it; a liquid poured, a bottle filled, food put in. A spoon stands in the bowl, ready.
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && view.openedByUse() && player.inventoryMenu.getCarried().isEmpty()) {
            ItemStack held = player.getMainHandItem();
            int selected = player.getInventory().getSelectedSlot();
            KitchenBowl bowl = bowl(view);
            if (bowl != null && !held.isEmpty()) {
                BowlLogic.Kind kind = BowlLogic.kind(recipes, bowl, held);
                if (kind == BowlLogic.Kind.FOOD && KitchenLooks.isCracked(held)) {
                    put(view, selected, true, true);
                } else if (kind == BowlLogic.Kind.LIQUID) {
                    put(view, selected, true, false);
                } else if (kind == BowlLogic.Kind.FOOD) {
                    put(view, selected, false, false);
                } else if (kind == BowlLogic.Kind.CONTAINER && bowl.handson$draw(held, true) != null) {
                    draw(view, selected, false);
                }
            }
        }
        refresh(view);
    }

    @Override
    public void onClose(StationView view) {
        stroking = false;
        heldByClick = false;
        book.shut(view);
        refresh(view);
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        refresh(view);
    }

    private static KitchenBowl bowl(StationView view) {
        return KitchenBowl.of(view.level().getBlockEntity(view.pos()));
    }

    /** What the inventory at the bowl shows: food and liquids its recipes take, their utensils, and what draws a liquid back out. */
    private boolean usable(StationView view, ItemStack stack) {
        KitchenBowl bowl = bowl(view);
        if (stack.isEmpty() || bowl == null) {
            return false;
        }
        return switch (BowlLogic.kind(recipes, bowl, stack)) {
            case FOOD, UTENSIL -> true;
            case LIQUID -> {
                KitchenBowl.Liquid liquid = bowl.handson$liquidOf(stack);
                yield recipes.stream().anyMatch(recipe -> recipe.liquids().stream().anyMatch(need -> need.takes(liquid.fluid())));
            }
            case CONTAINER -> bowl.handson$draw(stack, true) != null;
            case NONE -> false;
        };
    }

    /** A shift click in the inventory: food or a liquid goes in, all of the stack; empty bottles are filled with what is in the bowl. */
    @Override
    public boolean onInventoryShiftClick(StationView view, int slot, ItemStack stack) {
        KitchenBowl bowl = bowl(view);
        if (bowl == null || !usable(view, stack)) {
            return false;
        }
        switch (BowlLogic.kind(recipes, bowl, stack)) {
            case FOOD, LIQUID -> put(view, slot, false, false);
            case CONTAINER -> draw(view, slot, true);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void put(StationView view, int from, boolean one, boolean separate) {
        CompoundTag how = new CompoundTag();
        how.putInt(BowlStation.FROM, from);
        how.putBoolean(BowlStation.ONE, one);
        how.putBoolean(BowlStation.SEPARATE, separate);
        view.sendAction(BowlStation.ACTION_PUT, how);
    }

    private void draw(StationView view, int from, boolean all) {
        LocalPlayer player = Minecraft.getInstance().player;
        KitchenBowl bowl = bowl(view);
        ItemStack empty = player == null ? ItemStack.EMPTY : from < 0 ? player.inventoryMenu.getCarried() : player.getInventory().getItem(from);
        ItemStack full = bowl == null || empty.isEmpty() ? null : bowl.handson$draw(empty, true);
        if (full == null) {
            return;
        }
        if (dip == null) {
            dipFrom(view, empty.getItem(), full.getItem(), () -> view.playSound(SoundEvents.BOTTLE_FILL, "batter", 0.45F, 1.0F));
        }
        CompoundTag how = new CompoundTag();
        how.putInt(BowlStation.FROM, from);
        how.putBoolean(BowlStation.ALL, all);
        view.sendAction(BowlStation.ACTION_DRAW, how);
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Stroke stroke && stroke.node().equals("spoon")) {
            switch (stroke.phase()) {
                case START -> {
                    stroking = true;
                    if (!heldByClick) {
                        strokeStart = time;
                    }
                }
                case END -> {
                    stroking = false;
                    if (heldByClick) {
                        // The next click puts the spoon down.
                        heldByClick = false;
                    } else if (time - strokeStart < SPOON_CLICK) {
                        // A click takes the spoon in hand: it stirs as the mouse goes, with no button to hold.
                        heldByClick = true;
                        view.holdStroke();
                    }
                }
                default -> {
                }
            }
        } else if (event instanceof SceneEvent.Click click && click.node().equals("book_open")) {
            book.click(view, click.button());
        }
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

    /**
     * A stack on the cursor clicked over the bowl: a liquid is poured (one container, Shift all of them), an empty container draws a
     * liquid back out, an egg clicked with the right button is cracked for its white as the bowl's own click cracks it; a spoon goes
     * back to the inventory and stands in the bowl, ready to be taken by its handle. Food falls in as into any station.
     */
    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if ("book_open".equals(node)) {
            book.click(view, button);
            return true;
        }
        KitchenBowl bowl = bowl(view);
        if (bowl == null || "preview".equals(node)) {
            return false;
        }
        switch (BowlLogic.kind(recipes, bowl, stack)) {
            case LIQUID -> {
                put(view, -1, !shift, false);
                return true;
            }
            case UTENSIL -> {
                view.putAwayCarried();
                return true;
            }
            case CONTAINER -> {
                if (bowl.handson$draw(stack, true) == null) {
                    return false;
                }
                draw(view, -1, shift);
                return true;
            }
            case FOOD -> {
                if (button == 1 && KitchenLooks.isCracked(stack)) {
                    put(view, -1, true, true);
                    return true;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    private void refresh(StationView view) {
        StationState state = view.state();
        ItemStack[] items = state.getItems(BowlStation.INGREDIENTS);
        boolean more = Arrays.stream(items).mapToInt(ItemStack::getCount).sum() > Arrays.stream(shown).mapToInt(ItemStack::getCount).sum();
        List<Integer> arrivals = new ArrayList<>();
        for (int i = 0; i < SLOTS && i < items.length; i++) {
            if (!items[i].isEmpty() && (shown[i].isEmpty() || !ItemStack.isSameItem(items[i], shown[i])) && view.isFocused() && more) {
                arrivals.add(i);
            }
            if (items[i].isEmpty()) {
                arriveIn[i] = Float.NaN;
                flying[i] = Float.NaN;
                for (int k = 0; k < HANDS; k++) {
                    if (cracking[i] != null && hands[k] == cracking[i]) {
                        hands[k].stop();
                        free(k);
                    }
                }
                cracking[i] = null;
            }
            shown[i] = items[i].copy();
        }
        layOut();
        for (int k = 0; k < arrivals.size(); k++) {
            int index = arrivals.get(k);
            Optional<Vector3f> from = FromPlayer.point(view, RIM + ARRIVE_FROM, k, arrivals.size());
            if (from.isPresent()) {
                arriveIn[index] = k * ARRIVE_STAGGER;
                arriveFrom[index].set(from.get());
                view.node("item_" + index).ifPresent(node -> node.setVisible(false));
            }
        }
        CompoundTag data = state.data();
        String id = data.getStringOr(BowlStation.MAKING, "");
        Identifier key = id.isEmpty() ? null : Identifier.tryParse(id);
        making = key == null ? Optional.empty() : recipes.stream().filter(recipe -> recipe.id().equals(key)).findFirst();
        making.ifPresent(recipe -> lastMaking = recipe);
        readLiquids(view);
        int poured = data.getIntOr(BowlStation.POURED_COUNT, 0);
        if (pouredSeen >= 0 && poured > pouredSeen) {
            for (Tag entry : data.getListOrEmpty(BowlStation.POURED)) {
                Item item = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(entry.asString().orElse("")));
                if (item != Items.AIR) {
                    toPour.add(new ItemStack(item));
                }
            }
        }
        pouredSeen = poured;
        int made = data.getIntOr(BowlStation.MADE_COUNT, 0);
        if (madeSeen >= 0 && made > madeSeen) {
            done(view, data);
        }
        madeSeen = made;
        int stirred = data.getIntOr(BowlStation.STIRRED, 0);
        if (stirred > stirredSeen) {
            stirred(view);
        }
        stirredSeen = stirred;
    }

    /** The bowl's liquids as its block entity has them on this client, and their colour. */
    private void readLiquids(StationView view) {
        KitchenBowl bowl = bowl(view);
        List<KitchenBowl.Liquid> liquids = bowl == null ? List.of() : bowl.handson$liquids();
        float r = 0;
        float g = 0;
        float b = 0;
        float weight = 0;
        for (KitchenBowl.Liquid liquid : liquids) {
            ItemStack bottle = KitchenBowl.shown(liquid.fluid(), KitchenBowl.BOTTLE);
            int color = liquid.fluid().equals(Identifier.withDefaultNamespace("water")) ? WATER
                    : bottle.isEmpty() ? UNKNOWN_LIQUID : KitchenLooks.liquidColor(bottle);
            float w = liquid.amount();
            r += (color >> 16 & 0xFF) * w;
            g += (color >> 8 & 0xFF) * w;
            b += (color & 0xFF) * w;
            weight += w;
        }
        boolean wasWet = wet;
        wet = weight > 0;
        if (wet != wasWet) {
            usable.clear();
        }
        if (wet) {
            liquidColor = 0xFF000000 | Math.round(r / weight) << 16 | Math.round(g / weight) << 8 | Math.round(b / weight);
        }
        float fr = 0;
        float fg = 0;
        float fb = 0;
        int count = 0;
        for (ItemStack stack : shown) {
            if (stack.isEmpty()) {
                continue;
            }
            int color = KitchenLooks.isCracked(stack) ? 0xFFF2DC9A : 0xFF000000 | FoodColors.main(stack);
            fr += color >> 16 & 0xFF;
            fg += color >> 8 & 0xFF;
            fb += color & 0xFF;
            count++;
        }
        if (count > 0) {
            foodColor = 0xFF000000 | Math.round(fr / count) << 16 | Math.round(fg / count) << 8 | Math.round(fb / count);
        }
    }

    /** Things of one kind lie together, the kinds spread round the middle; each next one of a kind a little higher and turned. */
    private void layOut() {
        List<Item> seen = new ArrayList<>();
        for (ItemStack stack : shown) {
            if (!stack.isEmpty() && !seen.contains(stack.getItem())) {
                seen.add(stack.getItem());
            }
        }
        kinds = seen.size();
        int[] layers = new int[Math.max(1, kinds)];
        for (int i = 0; i < SLOTS; i++) {
            if (shown[i].isEmpty()) {
                kindOf[i] = -1;
                continue;
            }
            int kind = seen.indexOf(shown[i].getItem());
            kindOf[i] = kind;
            layerOf[i] = layers[kind]++;
        }
    }

    private float surface() {
        return FLOOR + DEPTH * level;
    }

    /** A whole circle more is stirred: the mixture laps at the spoon, and is heard. */
    private void stirred(StationView view) {
        float x = 8.0F + tipX;
        float z = 8.0F + tipZ;
        view.node("batter").ifPresent(batter -> batter.ripple(x, z, 0.3F));
        if (level > 0.02F) {
            view.spawnParticles(DiegesisParticles.SPLASH, new Vector3f(x, surface(), z), new Vector3f(0.0F, 3.0F, 0.0F), 0.4F, 2, shownColor);
            view.playSound(PotSounds.SCOOP, "batter", 0.18F, 1.4F + view.level().getRandom().nextFloat() * 0.2F);
        } else {
            view.playSound(SoundEvents.SAND_STEP, "batter", 0.12F, 1.6F);
        }
    }

    /** Something was made: at the station the badge swells with a chime; a container dips in for it, or it is lifted out. */
    private void done(StationView view, CompoundTag data) {
        doneAt = time;
        Item made = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(data.getStringOr(BowlStation.MADE, "")));
        view.spawnParticles(DiegesisParticles.GLINT, new Vector3f(8.0F, surface() + 0.5F, 8.0F), new Vector3f(0.0F, 2.0F, 0.0F), 3.0F, 6,
                0xFFFFE6A0);
        if (view.isInUse()) {
            view.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), "badge", 0.35F, 1.26F);
            view.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), "badge", 0.3F, 1.5F);
        }
        if (made == Items.AIR || !view.isFocused()) {
            return;
        }
        KitchenBowl.Mixing recipe = lastMaking;
        if (recipe != null && recipe.hasContainer()) {
            dipFrom(view, recipe.container().getItem(), made, () -> view.playSound(PotSounds.SCOOP, "batter", 0.45F, 1.1F));
        } else {
            view.node("dipper").ifPresent(node -> {
                KitchenMotions.showItem(node, made, DIP_SIZE, 0xFFFFFFFF);
                node.moveTo(DIP_AT.x, surface() - 0.5F, DIP_AT.z, 0.0F);
            });
            lifting = 0.0F;
            liftAway = FromPlayer.point(view, RIM + ARRIVE_FROM, 0, 1).orElse(new Vector3f(8.0F, RIM + 8.0F, 0.0F));
            view.playSound(PotSounds.PLOP, "batter", 0.4F, 1.2F);
        }
    }

    /** An empty container hangs over the bowl, dips in and comes up as {@code full}, and goes to the cook at the bottom of the screen. */
    private void dipFrom(StationView view, Item empty, Item full, Runnable onDipped) {
        Optional<NodeView> node = view.node("dipper");
        if (node.isEmpty()) {
            return;
        }
        lifting = Float.NaN;
        Vector3f over = new Vector3f(DIP_AT.x, surface() + 3.0F, DIP_AT.z);
        Vector3f away = FromPlayer.point(view, RIM + ARRIVE_FROM, 0, 1).orElse(new Vector3f(8.0F, RIM + 8.0F, 0.0F));
        KitchenMotions.showItem(node.get(), empty, DIP_SIZE, 0xFFFFFFFF);
        node.get().moveTo(over.x, over.y, over.z, 0.0F);
        node.get().rotateTo(0.0F, 0.0F, 0.0F, 0.0F);
        float scale = DIP_SIZE / ITEM_PIXELS;
        dip = new KitchenMotions.Dip(node.get(), full, over, new Vector3f(), away, this::surface, view.node("batter").orElse(null), scale, DIP_SIZE,
                0xFF000000 | shownColor, onDipped);
    }

    @Override
    public void frame(StationView view, float dt) {
        time += dt;
        boolean inUse = view.isInUse();
        float progress = view.state().getControl(BowlStation.PROGRESS);
        batter(view, dt, progress);
        spoon(view, dt);
        swirl *= (float) Math.pow(0.5F, dt / SWIRL_HALF_LIFE);
        swirlAngle += swirl * dt;
        view.node("batter").ifPresent(batter -> batter.setCurrent(swirl));
        for (int i = 0; i < SLOTS; i++) {
            food(view, i);
            arrive(view, i, dt);
            place(view, i, dt, progress);
        }
        pours(view, dt);
        if (dip != null && !dip.frame(view, dt)) {
            dip = null;
        }
        lift(view, dt);
        yolk(view, dt);
        view.node("mouth").ifPresent(node -> node.setEnabled(inUse));
        book.frame(view, dt);
        float appear = RecipeBook.appear(view);
        badge(view, inUse && RecipeBook.isWorkedAt(view) && appear > RecipeBook.SPECK, progress);
        float swell = badgeSwell();
        for (String part : BADGE_PARTS) {
            view.node(part).ifPresent(node -> node.scaleTo(appear * swell, 0.0F));
        }
    }

    /**
     * The mixture: as high as what was poured and what is stirred into it, in the colour of its liquids with a little of the food's,
     * turning to the result's as it is stirred. Thick: it hardly moves by itself.
     */
    private void batter(StationView view, float dt, float progress) {
        Optional<NodeView> node = view.node("batter");
        if (node.isEmpty()) {
            return;
        }
        float wanted = view.state().getTank(BowlStation.BATTER).amount() / 100.0F;
        level = Mth.lerp(Math.min(1.0F, dt * 3.0F), level, wanted);
        int base = wet ? KitchenLooks.mix(liquidColor, foodColor, FOOD_TINT + (1.0F - FOOD_TINT) * progress * 0.5F) : foodColor;
        int target = making.map(recipe -> KitchenLooks.mix(base, 0xFF000000 | FoodColors.main(recipe.result()), TURNING * KitchenMotions.smooth(progress)))
                .orElse(base);
        target = BATTER_ALPHA << 24 | target & 0xFFFFFF;
        if (target != shownColor) {
            shownColor = target;
            node.get().setLiquidColor(target);
        }
        node.get().setMottle(0.15F + 0.25F * progress);
        node.get().setVisible(view.isInUse());
    }

    /**
     * The spoon: the cook's own utensil for what lies in the bowl, see-through while it is only offered from their inventory. Held, its
     * end follows the hand round the bowl; for everybody else it follows what the server was told. Let go, it goes back to lean on the
     * rim.
     */
    private void spoon(StationView view, float dt) {
        NodeView spoon = view.node("spoon").orElse(null);
        NodeView item = view.node("spoon_item").orElse(null);
        if (spoon == null || item == null) {
            return;
        }
        StationState state = view.state();
        boolean othersStir = state.getControl(BowlStation.STIRRING) > 0.5F && !stroking;
        int from = utensilSlot(view);
        ItemStack utensil = utensilAt(from);
        if (utensil.isEmpty() && (othersStir || stroking) && making.isPresent()) {
            ItemStack[] any = RecipeParts.items(making.get().utensil());
            utensil = any.length > 0 ? any[0] : ItemStack.EMPTY;
        }
        boolean shownNow = view.isInUse() && making.isPresent() && !utensil.isEmpty();
        spoon.setVisible(shownNow);
        spoon.setEnabled(shownNow && view.isFocused() && from > -2);
        if (!shownNow) {
            stroking = stroking && view.isFocused();
            return;
        }
        if (utensil.getItem() != drawnUtensil) {
            drawnUtensil = utensil.getItem();
            // Lit as a block in the GUI: a 3D model, not a flat icon.
            flatUtensil = !com.pockyl.diegesis.impl.client.render.SceneDraw.itemState(utensil, ItemDisplayContext.GUI, view.level(), 0).usesBlockLight();
            item.setVisual(Visual.ofItem(drawnUtensil).withAnchor(ICON_MIDDLE));
            item.scaleTo(SPOON_SCALE, 0.0F);
            item.rotateTo(0.0F, 0.0F, flatUtensil ? FLAT_TURN : MODEL_TURN, 0.0F);
            item.moveTo(0.0F, flatUtensil ? FLAT_END : MODEL_END, 0.0F, 0.0F);
        }
        boolean hand = stroking && view.isFocused();
        item.setTint(hand || othersStir ? 0xFFFFFFFF : OFFERED);
        float wantX = REST_X;
        float wantZ = REST_Z;
        if (hand) {
            Optional<Vector3f> cursor = view.cursorOn(surface());
            if (cursor.isPresent()) {
                float x = cursor.get().x - 8.0F;
                float z = cursor.get().z - 8.0F;
                float distance = (float) Math.hypot(x, z);
                float shorter = distance > REACH ? REACH / distance : 1.0F;
                wantX = x * shorter;
                wantZ = z * shorter;
            } else {
                wantX = tipX;
                wantZ = tipZ;
            }
        } else if (othersStir) {
            wantX = state.getControl(BowlStation.SPOON_X) * REACH;
            wantZ = state.getControl(BowlStation.SPOON_Z) * REACH;
        }
        float follow = 1.0F - (float) Math.exp(-dt * (hand ? 20.0F : othersStir ? 9.0F : 5.0F));
        float stepX = (wantX - tipX) * follow;
        float stepZ = (wantZ - tipZ) * follow;
        tipX += stepX;
        tipZ += stepZ;
        float moved = (float) Math.hypot(stepX, stepZ);
        boolean stirring = hand || othersStir;
        if (stirring && dt > 0.0F && Math.hypot(tipX, tipZ) > 0.8F) {
            float angle = (float) Mth.atan2(tipZ, tipX);
            if (!Float.isNaN(tipAngle)) {
                float turn = Mth.wrapDegrees((angle - tipAngle) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
                float speed = turn / dt;
                // What lies in the bowl takes up the spoon's turning slowly, and never faster than it.
                if (Math.abs(speed * SWIRL_TAKES) > Math.abs(swirl) || Math.signum(speed) != Math.signum(swirl)) {
                    swirl = Mth.lerp(Math.min(1.0F, dt * 3.0F), swirl, speed * SWIRL_TAKES);
                }
            }
            tipAngle = angle;
        } else {
            tipAngle = Float.NaN;
        }
        if (stirring && moved > 0.005F) {
            float bx = tipX + 8.0F;
            float bz = tipZ + 8.0F;
            view.node("batter").ifPresent(batter -> batter.leaveWake(bx, bz));
        }
        view.loopSound("stir", PotSounds.STIR, Math.min(1.0F, Math.abs(swirl) / 6.0F) * STIR_VOLUME * (level > 0.02F ? 1.0F : 0.5F),
                level > 0.02F ? 1.15F : 1.45F);
        if (hand) {
            sendClock -= dt;
            if (sendClock <= 0.0F && moved > 0.002F) {
                sendClock = SEND_INTERVAL;
                CompoundTag where = new CompoundTag();
                where.putFloat(BowlStation.X, tipX / REACH);
                where.putFloat(BowlStation.Z, tipZ / REACH);
                where.putInt(BowlStation.FROM, from);
                view.sendAction(BowlStation.ACTION_STIR, where);
            }
        }
        float tipY = level > 0.02F ? surface() - 0.6F : FLOOR + 0.5F;
        spoon.moveTo(tipX, tipY - FLOOR, tipZ, 0.0F);
        Vector3f leanTo = stirring ? new Vector3f(tipX / REACH * HELD_LEAN, 1.0F, tipZ / REACH * HELD_LEAN) : new Vector3f(REST_LEAN);
        lean.rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), leanTo.normalize());
        Vector3f angles = lean.getEulerAnglesYXZ(new Vector3f()).mul(Mth.RAD_TO_DEG);
        spoon.rotateTo(angles.x, angles.y, angles.z, 0.0F);
    }

    /** Where the local cook's utensil for what the bowl makes is: -1 the cursor, an inventory slot, or -2 when they have none. */
    private int utensilSlot(StationView view) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !view.isFocused() || making.isEmpty()) {
            return -2;
        }
        var wanted = making.get().utensil();
        if (wanted.test(player.inventoryMenu.getCarried())) {
            return -1;
        }
        Inventory inventory = player.getInventory();
        if (wanted.test(inventory.getSelectedItem())) {
            return inventory.getSelectedSlot();
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (wanted.test(inventory.getItem(slot))) {
                return slot;
            }
        }
        return -2;
    }

    private static ItemStack utensilAt(int from) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || from < -1) {
            return ItemStack.EMPTY;
        }
        return from == -1 ? player.inventoryMenu.getCarried() : player.getInventory().getItem(from);
    }

    /** The thing in place {@code index}, drawn by its food node lying flat; an egg as its raw inside, cracked into the bowl. */
    private void food(StationView view, int index) {
        ItemStack stack = shown[index];
        Item item = stack.isEmpty() ? null : stack.getItem();
        if (item == drawnFood[index]) {
            return;
        }
        drawnFood[index] = item;
        view.node("food_" + index).ifPresent(node -> {
            node.setVisible(item != null);
            if (item == null) {
                return;
            }
            boolean egg = KitchenLooks.isCracked(stack);
            node.setVisual(Visual.ofItem(egg ? KitchenLooks.opened(stack).getItem() : item).withAnchor(ICON_MIDDLE));
            node.setTint(egg ? KitchenLooks.RAW_EGG : 0xFFFFFFFF);
        });
    }

    /** A thing on its way from the player waits its turn hidden; then an egg is cracked over the rim, anything else hops in. */
    private void arrive(StationView view, int index, float dt) {
        if (!Float.isNaN(arriveIn[index])) {
            arriveIn[index] -= dt;
            if (arriveIn[index] > 0.0F) {
                return;
            }
            arriveIn[index] = Float.NaN;
            ItemStack stack = shown[index];
            if (KitchenLooks.isCracked(stack) && cracking[index] == null) {
                int hand = nextHand();
                NodeView egg = view.node("hand_" + hand).orElse(null);
                NodeView inside = view.node("spill_" + hand).orElse(null);
                if (egg != null && inside != null) {
                    Vector3f onto = home(index, 0.0F).add(8.0F, FLOOR, 8.0F);
                    cracking[index] = new KitchenMotions.Crack(egg, inside, stack, arriveFrom[index], RIM_AT, onto, this::surface,
                            view.node("batter").orElse(null), 3.0F, 3.0F, false, 0.0F);
                    hands[hand] = cracking[index];
                    return;
                }
            }
            flying[index] = 0.0F;
            Vector3f start = new Vector3f(arriveFrom[index]).sub(8.0F, FLOOR, 8.0F);
            at[index].set(start);
            view.node("item_" + index).ifPresent(node -> {
                node.moveTo(start.x, start.y, start.z, 0.0F);
                node.setVisible(true);
            });
            return;
        }
        if (Float.isNaN(flying[index])) {
            return;
        }
        float before = flying[index];
        flying[index] += dt;
        if (before < ARRIVE_TIME && flying[index] >= ARRIVE_TIME) {
            flying[index] = Float.NaN;
            Vector3f where = new Vector3f(at[index]).add(8.0F, FLOOR, 8.0F);
            if (level > 0.02F) {
                view.node("batter").ifPresent(batter -> batter.ripple(where.x, where.z, 0.45F));
                view.spawnParticles(DiegesisParticles.SPLASH, where, new Vector3f(0.0F, 5.0F, 0.0F), 0.6F, 4, 0xFF000000 | shownColor);
                view.playSound(PotSounds.PLOP, "item_" + index, 0.25F, 1.5F);
            } else {
                view.playSound(SoundEvents.WOOD_HIT, "item_" + index, 0.2F, 1.6F);
            }
        }
    }

    /** Hand {@code k} is free again; an egg it cracked into a place lies there now. */
    private void free(int k) {
        for (int i = 0; i < SLOTS; i++) {
            if (cracking[i] == hands[k]) {
                cracking[i] = null;
            }
        }
        hands[k] = null;
    }

    private int nextHand() {
        for (int k = 0; k < HANDS; k++) {
            int hand = (nextHand + k) % HANDS;
            if (hands[hand] == null) {
                nextHand = (hand + 1) % HANDS;
                return hand;
            }
        }
        int hand = nextHand;
        hands[hand].stop();
        free(hand);
        nextHand = (hand + 1) % HANDS;
        return hand;
    }

    /** Where thing {@code index} lies, its node's pixels from the middle of the floor: its kind's place round the middle, gone round. */
    private Vector3f home(int index, float progress) {
        int kind = Math.max(0, kindOf[index]);
        float angle = swirlAngle + kind * Mth.TWO_PI / Math.max(1, kinds) + 0.6F;
        float radius = kinds <= 1 ? 0.6F : KINDS_RADIUS * (1.0F - 0.35F * progress);
        float y = level > 0.02F ? surface() - FLOOR + 0.05F : 0.05F;
        y += PILE_STEP * layerOf[index] - MIXED_SINK * progress;
        return new Vector3f(Mth.cos(angle) * radius, Math.max(0.05F, y), Mth.sin(angle) * radius);
    }

    /** Each thing goes where it belongs: a hop from the player while it comes in, then its place, going round with the mixture. */
    private void place(StationView view, int index, float dt, float progress) {
        Optional<NodeView> node = view.node("item_" + index);
        if (node.isEmpty() || !Float.isNaN(arriveIn[index]) || shown[index].isEmpty()) {
            return;
        }
        Vector3f to = home(index, progress);
        if (!Float.isNaN(flying[index])) {
            float k = Math.min(1.0F, flying[index] / ARRIVE_TIME);
            Vector3f from = new Vector3f(arriveFrom[index]).sub(8.0F, FLOOR, 8.0F);
            at[index].set(from).lerp(to, KitchenMotions.smooth(k)).add(0.0F, ARRIVE_ARC * Mth.sin(Mth.PI * k), 0.0F);
        } else {
            at[index].lerp(to, Math.min(1.0F, dt * SETTLE));
        }
        node.get().moveTo(at[index].x, at[index].y, at[index].z, 0.0F);
        node.get().rotateTo(0.0F, PILE_TURN * layerOf[index] + 25.0F * kindOf[index] + swirlAngle * Mth.RAD_TO_DEG * 0.6F, 0.0F, 0.0F);
        boolean coming = cracking[index] != null;
        node.get().setVisible(view.isInUse() && !coming || !Float.isNaN(flying[index]));
        view.node("food_" + index).ifPresent(food -> food.scaleTo(FOOD_SCALE * Mth.lerp(KitchenMotions.smooth(progress), 1.0F, MIXED_SIZE), 0.0F));
    }

    /** What was poured or cracked in comes one after the other: a liquid poured from its container, an egg cracked for its white. */
    private void pours(StationView view, float dt) {
        for (int k = 0; k < HANDS; k++) {
            if (hands[k] != null && !hands[k].frame(view, dt)) {
                free(k);
            }
        }
        pourClock -= dt;
        if (toPour.isEmpty() || pourClock > 0.0F) {
            return;
        }
        ItemStack stack = toPour.poll();
        pourClock = POUR_STAGGER;
        int hand = nextHand();
        NodeView node = view.node("hand_" + hand).orElse(null);
        if (node == null) {
            return;
        }
        Vector3f from = FromPlayer.point(view, RIM + ARRIVE_FROM, 0, 1).orElse(new Vector3f(8.0F, RIM + ARRIVE_FROM, 0.0F));
        if (KitchenLooks.isCracked(stack)) {
            NodeView inside = view.node("spill_" + hand).orElse(null);
            if (inside == null) {
                return;
            }
            Vector3f onto = new Vector3f(POUR_X + 1.0F, 0.0F, POUR_Z);
            hands[hand] = new KitchenMotions.Crack(node, inside, stack, from, RIM_AT, onto, this::surface, view.node("batter").orElse(null), 3.0F,
                    3.0F, true, 0.0F).onLanded(() -> yolkFrom(view, onto));
            return;
        }
        view.node("batter").ifPresent(batter -> batter.holdLevel(KitchenMotions.Pour.pouredBy(stack) - 0.5F, 0.6F));
        hands[hand] = new KitchenMotions.Pour(node, "pour_" + hand, stack, from, new Vector3f(POUR_X, 0.0F, POUR_Z), this::surface, "batter",
                HAND_SIZE, -1.0F, 0.0F).lipAtLeast(RIM + 1.0F);
    }

    /** The yolk of an egg cracked for its white leaves the bowl for the cook. */
    private void yolkFrom(StationView view, Vector3f onto) {
        Item yolk = BuiltInRegistries.ITEM.getValue(EGG_YOLK);
        if (yolk == Items.AIR || !view.isFocused()) {
            return;
        }
        view.node("yolk").ifPresent(node -> {
            KitchenMotions.showItem(node, yolk, 2.6F, 0xFFFFFFFF);
            node.moveTo(onto.x, surface() + 0.6F, onto.z, 0.0F);
        });
        yolkAway.set(FromPlayer.point(view, RIM + ARRIVE_FROM, 0, 1).orElse(new Vector3f(8.0F, RIM + 8.0F, 0.0F)));
        yolkStart.set(onto.x, surface() + 0.6F, onto.z);
        yolkFlying = 0.0F;
    }

    private void yolk(StationView view, float dt) {
        if (Float.isNaN(yolkFlying)) {
            return;
        }
        yolkFlying += dt;
        float k = KitchenMotions.smooth(yolkFlying / YOLK_TIME);
        Vector3f where = new Vector3f(yolkStart).lerp(yolkAway, k).add(0.0F, 2.5F * Mth.sin(Mth.PI * k), 0.0F);
        view.node("yolk").ifPresent(node -> {
            node.moveTo(where.x, where.y, where.z, 0.0F);
            node.scaleTo(2.6F / ITEM_PIXELS * (1.0F - 0.6F * k), 0.0F);
            if (yolkFlying >= YOLK_TIME) {
                node.setVisible(false);
            }
        });
        if (yolkFlying >= YOLK_TIME) {
            yolkFlying = Float.NaN;
        }
    }

    /** What was made without a container comes up out of the mixture, dripping, and goes to the cook. */
    private void lift(StationView view, float dt) {
        if (Float.isNaN(lifting)) {
            return;
        }
        lifting += dt;
        NodeView node = view.node("dipper").orElse(null);
        if (node == null) {
            lifting = Float.NaN;
            return;
        }
        float top = surface() + 3.0F;
        if (lifting < LIFT_TIME) {
            float k = KitchenMotions.out(lifting / LIFT_TIME);
            node.moveTo(DIP_AT.x, Mth.lerp(k, surface() - 0.5F, top), DIP_AT.z, 0.0F);
            if (view.level().getRandom().nextFloat() < dt * 14.0F) {
                view.spawnParticles(DiegesisParticles.DROPLET, new Vector3f(DIP_AT.x, Mth.lerp(k, surface(), top) - 1.0F, DIP_AT.z),
                        new Vector3f(0.0F, -2.0F, 0.0F), 0.6F, 1, 0xFF000000 | shownColor);
            }
            return;
        }
        float k = KitchenMotions.smooth((lifting - LIFT_TIME) / AWAY_TIME);
        Vector3f where = new Vector3f(DIP_AT.x, top, DIP_AT.z).lerp(liftAway, k);
        node.moveTo(where.x, where.y, where.z, 0.0F);
        node.scaleTo(DIP_SIZE / ITEM_PIXELS * (1.0F - k), 0.0F);
        if (k >= 1.0F) {
            node.setVisible(false);
            lifting = Float.NaN;
        }
    }

    private float badgeSwell() {
        float since = time - doneAt;
        return since >= 0.0F && since < DONE_TIME ? 1.0F + DONE_SWELL * Mth.sin(Mth.PI * since / DONE_TIME) : 1.0F;
    }

    /**
     * The badge while the bowl makes something: what it makes, a bar for how far it is stirred and how many sets. It shows what the
     * cook lacks instead, blinking: the utensil, or the container the stirred bowlful waits for.
     */
    private void badge(StationView view, boolean cookHere, float progress) {
        boolean shownNow = cookHere && making.isPresent();
        view.node("badge").ifPresent(plate -> plate.setVisible(shownNow));
        view.node("dial_track").ifPresent(node -> node.setVisible(shownNow));
        view.node("dial").ifPresent(node -> node.setVisible(shownNow));
        CompoundTag data = view.state().data();
        int sets = data.getIntOr(BowlStation.SETS, 0);
        int bar = Math.round(progress * BAR_LENGTH);
        String text = sets > 1 ? "x" + Math.min(sets, 99) : "";
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
        Item asked = null;
        String wants = data.getStringOr(BowlStation.WANTS, "");
        if (!wants.isEmpty()) {
            Item container = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(wants));
            asked = container == Items.AIR ? null : container;
        } else if (making.isPresent() && utensilSlot(view) < -1) {
            ItemStack[] utensils = RecipeParts.items(making.get().utensil());
            asked = utensils.length > 0 ? utensils[0].getItem() : null;
        }
        Item ask = asked;
        view.node("preview").ifPresent(node -> {
            node.setVisible(shownNow && (ask == null || Mth.sin(time * 9.0F) > -0.3F));
            if (!shownNow) {
                return;
            }
            Item icon = ask != null ? ask : making.get().result().getItem();
            if (shownIcon != icon) {
                shownIcon = icon;
                node.setVisual(Visual.ofItem(icon).withAnchor(ICON_MIDDLE));
            }
            node.setTooltip(ask != null ? new ItemStack(ask).getHoverName() : making.get().result().getHoverName());
        });
    }

    private static void text(MeshShape.Builder shape, String text) {
        int width = -1;
        for (char c : text.toCharArray()) {
            width += 4;
        }
        int x = -width / 2 - width % 2;
        for (char c : text.toCharArray()) {
            String[] glyph = c == 'x' ? TIMES : PotBehavior.DIGITS[c - '0'];
            for (int row = 0; row < glyph.length; row++) {
                for (int column = 0; column < glyph[row].length(); column++) {
                    if (glyph[row].charAt(column) == '#') {
                        PotBehavior.pixels(shape, x + column, TEXT_TOP + row, 1, 1);
                    }
                }
            }
            x += glyph[0].length() + 1;
        }
    }
}
