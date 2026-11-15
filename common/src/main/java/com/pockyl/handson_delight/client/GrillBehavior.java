package com.pockyl.handson_delight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.station.GrillStation;
import com.pockyl.handson_delight.station.KitchenGrill;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Barbeque's Delight's grill on the client: two skewers lying front to back over the fire, each built from its icon. A click on one
 * whose lower side is done turns it over (a hop and a half turn, the seared side up); before that it only twitches. The other button,
 * or a click on one that is done, takes it. A seasoning on the cursor clicked on a skewer is shaken over it, and its colour stays on
 * the skewer: specks of a powder, a zigzag of a sauce. Heat shimmers over a lit grill, a skewer that cooks steams, one that waits to
 * be turned smokes round its edge, and one left on after it is done darkens and smokes harder until it burns.
 */
public final class GrillBehavior implements StationBehavior {
    /** How big a skewer is drawn: its icon this many pixels across, its diagonal running front to back. */
    private static final float SIZE = 8.5F;
    /** The grate's height: Barbeque's grill has its grate at y 15. */
    private static final float TOP = 15.05F;
    /** How far from the middle each place lies, across the grill: Barbeque's own places are a fifth of a block off it. */
    private static final float APART = 3.2F;
    /** Turned so that the icon's diagonal, from its bottom left to its top right, runs away from the cook. */
    private static final float ALONG = 45.0F;
    private static final int BURNING = 0xFF4E3B2E;
    private static final float FLIGHT = 0.32F;
    private static final float RISE = 3.0F;
    private static final float LAND_TIME = 0.2F;
    private static final float LAND_FROM = 4.0F;
    private static final float PIECE_STAGGER = 0.14F;
    private static final float PLAYER_LAND_TIME = 0.32F;
    private static final float AWAY_TIME = 0.3F;
    private static final float SHIMMER_RATE = 1.6F;
    private static final float VAPOUR_RATE = 0.9F;
    private static final float SMOKE_RATE = 2.5F;
    private static final float BURN_SMOKE_RATE = 6.0F;
    private static final float SIZZLE_TIME = 1.6F;
    private static final float WAITING_SIZZLE_TIME = 1.1F;
    private static final float HAND_SIZE = 3.6F;
    /** How thick a seasoning lies on a skewer's top, pixels. */
    private static final float FILM_THICK = 0.12F;
    /** Of a skewer's pixels, how many a powder speckles. */
    private static final float SPECKLED = 0.22F;
    /** Of a skewer's pixels, how many a sauce covers. */
    private static final float GLAZED = 0.6F;
    private static final int GHOSTS = 3;

    private final GrillBook pages = new GrillBook();
    private final RecipeBook book = new RecipeBook(pages);
    private final Map<Item, Boolean> usable = new HashMap<>();
    private final RandomSource random = RandomSource.create();
    private final ItemStack[] shown = new ItemStack[KitchenGrill.PLACES];
    private final String[] shownLook = new String[KitchenGrill.PLACES];
    private final float[] turnedAt = new float[KitchenGrill.PLACES];
    /** Until when the skewer keeps the look it had before its turn: it is in the air, its other side comes up halfway. */
    private final float[] heldUntil = new float[KitchenGrill.PLACES];
    private final boolean[] heldTurned = new boolean[KitchenGrill.PLACES];
    /** Until when a seasoning is still on its way: its colour shows on the skewer once it was shaken out. */
    private final float[] seasonedFrom = new float[KitchenGrill.PLACES];
    private final KitchenMotions.Motion[] going = new KitchenMotions.Motion[KitchenGrill.PLACES];
    private final List<Step> steps = new ArrayList<>();
    private boolean quiet = true;
    private float clock;
    private float shimmer;
    private float vapour;
    private float smoke;
    private float sizzle;
    private int ghost;

    private record Step(float at, Runnable run) {
    }

    public GrillBehavior(StationView view) {
        Arrays.fill(shown, ItemStack.EMPTY);
        Arrays.fill(turnedAt, -10.0F);
    }

    @Override
    public void onSceneReady(StationView view) {
        Arrays.fill(shown, ItemStack.EMPTY);
        Arrays.fill(shownLook, null);
        steps.clear();
        quiet = true;
        view.node("book_open").ifPresent(node -> node.setPainter(book));
        refresh(view);
    }

    @Override
    public void onOpen(StationView view) {
        book.opened(view);
        usable.clear();
        view.setInventoryFilter(stack -> usable.computeIfAbsent(stack.getItem(),
                item -> KitchenGrill.grills(view.level(), stack) || KitchenGrill.isSeasoning(stack)));
        // What the cook came with is meant for the grill: a skewer goes on, a seasoning over the first skewer that takes it.
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && view.openedByUse() && player.inventoryMenu.getCarried().isEmpty()) {
            onInventoryShiftClick(view, player.getInventory().selected, player.getMainHandItem());
        }
    }

    @Override
    public void onClose(StationView view) {
        book.shut(view);
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        refresh(view);
    }

    @Override
    public void tick(StationView view) {
        refresh(view);
        for (int i = 0; i < GHOSTS; i++) {
            view.node("lift_" + i).filter(node -> node.isVisible() && !node.isMoving()).ifPresent(node -> node.setVisible(false));
        }
    }

    /** The skewers as the state says; what came on lands, several at once from the player one after the other. */
    private void refresh(StationView view) {
        StationState state = view.state();
        int arriving = 0;
        for (int place = 0; place < KitchenGrill.PLACES; place++) {
            arriving += shown[place].isEmpty() && !state.getItem(GrillStation.place(place)).isEmpty() ? 1 : 0;
        }
        boolean together = arriving > 1 && !quiet;
        int landing = 0;
        boolean lit = state.data().getBoolean(GrillStation.LIT);
        for (int place = 0; place < KitchenGrill.PLACES; place++) {
            ItemStack now = state.getItem(GrillStation.place(place));
            String look = lookKey(view, place, now);
            if (Objects.equals(look, shownLook[place]) && ItemStack.isSameItemSameComponents(now, shown[place])) {
                continue;
            }
            ItemStack was = shown[place];
            boolean arrives = was.isEmpty() && !now.isEmpty();
            shown[place] = now.copy();
            shownLook[place] = look;
            int index = place;
            Optional<NodeView> node = view.node("piece_" + place);
            node.ifPresent(it -> draw(view, it, index));
            if (!quiet && !arrives && !was.isEmpty() && !now.isEmpty() && !was.is(now.getItem())) {
                changed(view, place, now);
            }
            if (!arrives || quiet || node.isEmpty()) {
                continue;
            }
            float delay = together ? landing * PIECE_STAGGER : 0.0F;
            Vector3f from = together ? FromPlayer.offset(view, LAND_FROM, landing, arriving) : null;
            landing++;
            node.get().setVisible(delay <= 0.0F);
            Runnable lands = () -> {
                if (shown[index].isEmpty()) {
                    return;
                }
                node.get().setVisible(true);
                land(node.get(), from, index);
                if (lit) {
                    after(from == null ? LAND_TIME : PLAYER_LAND_TIME, () -> hitsTheGrill(view, index));
                }
            };
            if (delay > 0.0F) {
                after(delay, lands);
            } else {
                lands.run();
            }
        }
        quiet = false;
    }

    /** What the skewer on {@code place} shows, as a key: drawn again only when it changes. */
    private String lookKey(StationView view, int place, ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        CompoundTag data = view.state().data();
        boolean turned = clock < heldUntil[place] ? heldTurned[place] : data.getBoolean(GrillStation.TURNED + place);
        int burn = Mth.floor(data.getFloat(GrillStation.BURN + place) * 10.0F);
        boolean seasoned = clock >= seasonedFrom[place] && KitchenGrill.isSeasoned(stack);
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "/" + turned + "/" + burn + "/" + seasoned;
    }

    /**
     * Draws the skewer on {@code place}: raw until it is turned, then its seared side up (the grilled look), darker the nearer it is
     * to burning; its seasoning over it once it was shaken out.
     */
    private void draw(StationView view, NodeView node, int place) {
        ItemStack stack = shown[place];
        node.setVisible(!stack.isEmpty());
        Optional<NodeView> dressing = view.node("dressing_" + place);
        if (stack.isEmpty()) {
            dressing.ifPresent(it -> it.setVisible(false));
            return;
        }
        CompoundTag data = view.state().data();
        boolean turned = clock < heldUntil[place] ? heldTurned[place] : data.getBoolean(GrillStation.TURNED + place);
        ItemStack look = turned ? grilled(view, stack) : stack;
        Optional<ItemVoxels.Icon> icon = ItemVoxels.icon(look);
        if (icon.isPresent()) {
            node.setVisual(null);
            node.setShape(ItemVoxels.build(icon.get(), SIZE, List.of(), 0.0F), icon.get().sprite());
        } else {
            node.setShape(null, null);
            node.setVisual(Visual.ofItem(look.getItem()).withAnchor(new Vector3f(8.0F, 8.0F, 8.0F)));
        }
        float burn = KitchenGrill.isBurnt(stack) ? 0.0F : data.getFloat(GrillStation.BURN + place);
        node.setTint(KitchenLooks.mix(0xFFFFFFFF, BURNING, KitchenMotions.smooth(Mth.clamp(burn, 0.0F, 1.0F)) * 0.8F));
        if (!node.isMoving()) {
            node.moveTo(0.0F, 0.0F, 0.0F, 0.0F);
            node.rotateTo(0.0F, yaw(place), 0.0F, 0.0F);
        }
        dressing.ifPresent(it -> dress(it, icon, stack, clock >= seasonedFrom[place]));
    }

    /**
     * The seasoning on a skewer, lying on its top: a powder as specks over its food, a sauce as a zigzag across it, both in the colour
     * of what is in the seasoning's bowl.
     */
    private static void dress(NodeView node, Optional<ItemVoxels.Icon> skewer, ItemStack stack, boolean arrived) {
        Optional<ItemStack> seasoning = KitchenGrill.seasoning(stack);
        Optional<ItemVoxels.Icon> full = seasoning.flatMap(ItemVoxels::icon);
        if (!arrived || full.isEmpty() || skewer.isEmpty() || KitchenGrill.isBurnt(stack)) {
            node.setVisible(false);
            return;
        }
        // Barbeque's seasonings are drawn in the same bowl or dish: what differs from another seasoning of the same kind is what is
        // in it. A powder shows its lightest grains, a sauce its own colour.
        boolean sauce = isSauce(seasoning.get());
        Optional<ItemVoxels.Icon> other = ItemVoxels.icon(new ItemStack(otherOfItsKind(seasoning.get(), sauce)));
        int[] at = sauce ? ItemVoxels.contents(full.get(), other) : ItemVoxels.lightest(full.get(), other);
        node.setVisible(true);
        node.setTint(0xFFFFFFFF);
        long seed = skewer.get().sprite().hashCode();
        node.setShape(ItemVoxels.specks(skewer.get(), SIZE, FILM_THICK, full.get(), at[0], at[1], seed, sauce ? GLAZED : SPECKLED, 3),
                full.get().sprite());
    }

    /** Another seasoning drawn in the same bowl or dish as {@code seasoning}: cumin or chili powder, barbeque or buffalo sauce. */
    private static Item otherOfItsKind(ItemStack seasoning, boolean sauce) {
        String path = BuiltInRegistries.ITEM.getKey(seasoning.getItem()).getPath();
        String other = sauce ? path.equals("barbeque_sauce") ? "buffalo_sauce" : "barbeque_sauce"
                : path.equals("cumin_powder") ? "chili_powder" : "cumin_powder";
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(KitchenGrill.MOD_ID, other));
    }

    /** Barbeque's Delight's sauces (honey mustard, buffalo, barbeque): dabbed on, not shaken. */
    static boolean isSauce(ItemStack seasoning) {
        return BuiltInRegistries.ITEM.getKey(seasoning.getItem()).getPath().endsWith("_sauce");
    }

    /** What the grill makes of {@code raw}: the look of its seared side; burnt and grilled skewers look as they are. */
    private static ItemStack grilled(StationView view, ItemStack raw) {
        return KitchenGrill.recipe(view.level(), raw).map(KitchenGrill.Grilling::result).filter(result -> !result.isEmpty())
                .orElse(raw);
    }

    /** Each skewer a little turned off straight, never both the same way: laid by hand. */
    private static float yaw(int place) {
        return ALONG + (place == 0 ? -4.0F : 5.0F);
    }

    /** Where the skewer on {@code place} lies, scene pixels: place 0 on the cook's left, as Barbeque's grill has it. */
    static Vector3f placeOf(int place) {
        return new Vector3f(place == 0 ? 8.0F + APART : 8.0F - APART, TOP, 8.0F);
    }

    /** Comes down onto its place from a little above, or with {@code from} over from the player, with a hop. */
    private void land(NodeView node, Vector3f from, int place) {
        node.rotateTo(0.0F, yaw(place), 0.0F, 0.0F);
        if (from == null) {
            node.moveTo(0.0F, LAND_FROM, 0.0F, 0.0F);
            node.hopTo(0.0F, 0.0F, 0.0F, 0.8F, LAND_TIME);
        } else {
            node.moveTo(from.x, LAND_FROM, from.z, 0.0F);
            node.hopTo(0.0F, 0.0F, 0.0F, 2.0F, PLAYER_LAND_TIME);
        }
    }

    /** A skewer became something else while it lay there: grilled through, or burnt. */
    private void changed(StationView view, int place, ItemStack now) {
        if (KitchenGrill.isBurnt(now)) {
            view.spawnParticles(DiegesisParticles.SMOKE, onPiece(place), new Vector3f(0.0F, 3.0F, 0.0F), 1.8F, 8, 0xC0302824);
            view.playSound(SoundEvents.FIRE_EXTINGUISH, "grill", 0.35F, 1.4F);
            return;
        }
        view.spawnParticles(DiegesisParticles.STEAM, onPiece(place), new Vector3f(0.0F, 3.0F, 0.0F), 1.5F, 3, 0x60FFFFFF);
        view.spawnParticles(DiegesisParticles.GLINT, onPiece(place), new Vector3f(0.0F, 2.0F, 0.0F), 1.5F, 3, 0xFFFFE9A0);
        if (view.isFocused()) {
            view.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), "grill", 0.3F, 1.26F);
        }
    }

    @Override
    public void frame(StationView view, float dt) {
        clock += dt;
        if (!steps.isEmpty()) {
            List<Step> due = steps.stream().filter(step -> step.at() <= clock).toList();
            steps.removeAll(due);
            due.forEach(step -> step.run().run());
        }
        for (int place = 0; place < KitchenGrill.PLACES; place++) {
            if (going[place] != null && !going[place].frame(view, dt)) {
                going[place] = null;
            }
        }
        heat(view, dt);
        book.frame(view, dt);
    }

    /**
     * The grill at work. Lit, the air over it shimmers; a skewer that cooks steams a little and sizzles quietly; one whose lower side
     * is done smokes round its edge and sizzles sharper, wanting to be turned; one left on after its time smokes the harder the
     * nearer it is to burning.
     */
    private void heat(StationView view, float dt) {
        CompoundTag data = view.state().data();
        if (!data.getBoolean(GrillStation.LIT)) {
            sizzle = 0.0F;
            return;
        }
        shimmer += dt * SHIMMER_RATE;
        while (shimmer >= 1.0F) {
            shimmer -= 1.0F;
            view.spawnParticles(HandsOnDelightClient.HEAT, new Vector3f(8.0F + randomly(5.5F), TOP + 2.0F + random.nextFloat() * 2.0F,
                    8.0F + randomly(5.5F)), new Vector3f(0.0F, 3.0F, 0.0F), 0.3F, 1, 0);
        }
        List<Integer> cooking = new ArrayList<>();
        List<Integer> waiting = new ArrayList<>();
        float burning = 0.0F;
        for (int place = 0; place < KitchenGrill.PLACES; place++) {
            if (shown[place].isEmpty() || KitchenGrill.isBurnt(shown[place]) || clock - turnedAt[place] < FLIGHT) {
                continue;
            }
            float burn = data.getFloat(GrillStation.BURN + place);
            if (burn > 0.0F) {
                burning += burn;
                waiting.add(place);
            } else if (data.getBoolean(GrillStation.WAITS + place)) {
                waiting.add(place);
            } else if (!data.getBoolean(GrillStation.TURNED + place) || data.getFloat(GrillStation.LOWER + place) < 1.0F) {
                cooking.add(place);
            }
        }
        vapour += dt * VAPOUR_RATE * cooking.size();
        while (vapour >= 1.0F && !cooking.isEmpty()) {
            vapour -= 1.0F;
            view.spawnParticles(DiegesisParticles.STEAM, onPiece(cooking.get(random.nextInt(cooking.size()))),
                    new Vector3f(randomly(0.4F), 2.2F, randomly(0.4F)), 0.4F, 1, 0x40FFFFFF);
        }
        smoke += dt * (SMOKE_RATE * waiting.size() + BURN_SMOKE_RATE * burning);
        while (smoke >= 1.0F && !waiting.isEmpty()) {
            smoke -= 1.0F;
            int place = waiting.get(random.nextInt(waiting.size()));
            float burn = data.getFloat(GrillStation.BURN + place);
            view.spawnParticles(HandsOnDelightClient.PAN_SMOKE, roundPiece(place), new Vector3f(randomly(0.5F), 4.0F, randomly(0.5F)),
                    0.3F, 1, burn > 0.4F ? 0xA0605650 : 0);
        }
        if (cooking.isEmpty() && waiting.isEmpty()) {
            sizzle = 0.0F;
            return;
        }
        sizzle -= dt;
        if (sizzle <= 0.0F) {
            boolean wants = !waiting.isEmpty();
            sizzle = wants ? WAITING_SIZZLE_TIME : SIZZLE_TIME;
            view.playSound(FarmersDelightContent.SKILLET_SIZZLE, "grill", wants ? 0.3F : 0.15F,
                    (wants ? 1.15F : 1.0F) + random.nextFloat() * 0.1F);
        }
    }

    /** A skewer landing on the hot grate: it hisses and steams. */
    private void hitsTheGrill(StationView view, int place) {
        view.spawnParticles(DiegesisParticles.STEAM, onPiece(place), new Vector3f(0.0F, 3.0F, 0.0F), 1.2F, 2, 0x50FFFFFF);
        view.playSound(FarmersDelightContent.SKILLET_ADD_FOOD, "grill", 0.45F, 1.05F + random.nextFloat() * 0.1F);
    }

    /** A point on the skewer on {@code place}: along its length, front to back. */
    private Vector3f onPiece(int place) {
        return placeOf(place).add(randomly(0.8F), 0.6F, randomly(SIZE * 0.5F));
    }

    /** Just beside the skewer, on the grate: where the smoke of a side that waits curls up from. */
    private Vector3f roundPiece(int place) {
        float side = random.nextBoolean() ? 1.0F : -1.0F;
        return placeOf(place).add(side * (1.3F + 0.4F * random.nextFloat()), 0.15F, randomly(SIZE * 0.5F));
    }

    private float randomly(float most) {
        return (random.nextFloat() * 2.0F - 1.0F) * most;
    }

    private void after(float seconds, Runnable run) {
        steps.add(new Step(clock + seconds, run));
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (!(event instanceof SceneEvent.Click click)) {
            return;
        }
        if ("book_open".equals(click.node())) {
            book.click(view);
            return;
        }
        int place = placeOf(click.node());
        if (place < 0 || shown[place].isEmpty()) {
            return;
        }
        CompoundTag data = view.state().data();
        CompoundTag at = new CompoundTag();
        at.putInt(GrillStation.PLACE, place);
        boolean done = KitchenGrill.isBurnt(shown[place]) || KitchenGrill.recipe(view.level(), shown[place]).isEmpty();
        // A skewer that smokes, waiting, is what the cook clicks: it turns. The other button takes it, raw or half done; done, either.
        if (click.button() == 1 || done) {
            view.sendAction(GrillStation.ACTION_TAKE, at);
        } else if (data.getBoolean(GrillStation.WAITS + place)) {
            if (clock - turnedAt[place] > FLIGHT + 0.05F) {
                view.sendAction(GrillStation.ACTION_TURN, at);
                turned(view, place);
            }
        } else {
            twitch(view, place);
        }
    }

    private static int placeOf(String node) {
        if (node == null || !node.startsWith("piece_")) {
            return -1;
        }
        try {
            int place = Integer.parseInt(node.substring("piece_".length()));
            return place >= 0 && place < KitchenGrill.PLACES ? place : -1;
        } catch (NumberFormatException notAPiece) {
            return -1;
        }
    }

    /** One skewer over, as everybody sees it: up and half round, then, edge on, the seared side's look, the rest of the way and down. */
    private void turned(StationView view, int place) {
        turnedAt[place] = clock;
        heldTurned[place] = false;
        heldUntil[place] = clock + FLIGHT / 2.0F;
        view.node("piece_" + place).ifPresent(node -> {
            float yaw = yaw(place);
            node.hopTo(0.0F, 0.0F, 0.0F, RISE, FLIGHT);
            node.rotateTo(0.0F, yaw, 90.0F, FLIGHT / 2.0F);
            after(FLIGHT / 2.0F, () -> {
                heldUntil[place] = 0.0F;
                heldTurned[place] = true;
                shownLook[place] = null;
                refresh(view);
                node.rotateTo(0.0F, yaw, -90.0F, 0.0F);
                node.rotateTo(0.0F, yaw, 0.0F, FLIGHT / 2.0F);
            });
        });
        view.node("dressing_" + place).ifPresent(node -> node.setVisible(false));
        view.playSound(SoundEvents.LANTERN_STEP, "grill", 0.25F, 1.3F);
        after(FLIGHT, () -> {
            view.spawnParticles(DiegesisParticles.STEAM, onPiece(place), new Vector3f(0.0F, 3.0F, 0.0F), 0.8F, 2, 0x50FFFFFF);
            shownLook[place] = null;
            refresh(view);
        });
    }

    /** Not ready to turn: it lifts a hair and drops back, its lower side still sticks. */
    private void twitch(StationView view, int place) {
        view.node("piece_" + place).filter(node -> !node.isMoving()).ifPresent(node -> node.hopTo(0.0F, 0.0F, 0.0F, 0.6F, 0.14F));
        view.playSound(SoundEvents.LANTERN_HIT, "grill", 0.2F, 1.5F);
    }

    /**
     * A skewer let go over the grill goes on: one onto every free place, or with the right button one onto the place under the cursor.
     * A seasoning on the cursor goes over the skewer it is let go on. The book takes its click with a stack in the hand.
     */
    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if ("book_open".equals(node)) {
            book.click(view);
            return true;
        }
        CompoundTag how = new CompoundTag();
        if (KitchenGrill.isSeasoning(stack)) {
            int place = placeOf(node);
            how.putInt(GrillStation.PLACE, place >= 0 ? place : nearest(view, false));
            view.sendAction(GrillStation.ACTION_SEASON, how);
            return true;
        }
        if (button == 1) {
            how.putBoolean(GrillStation.ONE, true);
            how.putInt(GrillStation.PLACE, nearest(view, true));
        }
        view.sendAction(GrillStation.ACTION_PLACE, how);
        return true;
    }

    /** The place nearest the cursor on the grill, a free one or one with a skewer; -1 for none. */
    private int nearest(StationView view, boolean free) {
        Optional<Vector3f> cursor = view.cursorOn(TOP);
        int best = -1;
        float nearest = Float.MAX_VALUE;
        for (int place = 0; place < KitchenGrill.PLACES; place++) {
            if (view.state().getItem(GrillStation.place(place)).isEmpty() != free) {
                continue;
            }
            Vector3f at = placeOf(place);
            float distance = cursor.map(point -> point.distanceSquared(at)).orElse((float) place);
            if (distance < nearest) {
                nearest = distance;
                best = place;
            }
        }
        return best;
    }

    /** The first free place, for a skewer laid from the book; -1 for none. */
    static int firstFree(StationView view) {
        for (int place = 0; place < KitchenGrill.PLACES; place++) {
            if (view.state().getItem(GrillStation.place(place)).isEmpty()) {
                return place;
            }
        }
        return -1;
    }

    /** Shift + click in the inventory: a skewer goes onto the grill, a seasoning over the first skewer that takes it. */
    @Override
    public boolean onInventoryShiftClick(StationView view, int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        CompoundTag how = new CompoundTag();
        how.putInt(GrillStation.FROM, slot);
        if (KitchenGrill.isSeasoning(stack)) {
            view.sendAction(GrillStation.ACTION_SEASON, how);
            return true;
        }
        if (!KitchenGrill.grills(view.level(), stack)) {
            return false;
        }
        view.sendAction(GrillStation.ACTION_PLACE, how);
        return true;
    }

    @Override
    public void onEffect(StationView view, ResourceLocation effect, String node, CompoundTag data) {
        int place = data.getInt(GrillStation.PLACE);
        boolean known = place >= 0 && place < KitchenGrill.PLACES;
        if (GrillStation.EFFECT_TURNED.equals(effect)) {
            // The cook's own turn is on show already.
            if (known && (!view.isFocused() || clock - turnedAt[place] > 0.6F)) {
                turned(view, place);
            }
        } else if (GrillStation.EFFECT_UNTURNED.equals(effect)) {
            if (known) {
                heldUntil[place] = 0.0F;
                shownLook[place] = null;
                refresh(view);
                twitch(view, place);
            }
        } else if (GrillStation.EFFECT_PLACED.equals(effect)) {
            view.playSound(SoundEvents.LANTERN_PLACE, "grill", 0.4F, 1.1F);
        } else if (GrillStation.EFFECT_REFUSED.equals(effect)) {
            ItemStack refused = ItemStack.parseOptional(view.level().registryAccess(), data.getCompound(GrillStation.STACK));
            ghost(view, refused).ifPresent(ghost -> {
                ghost.moveTo(0.0F, LAND_FROM + 2.0F, 0.0F, 0.0F);
                ghost.moveTo(0.0F, 1.5F, 0.0F, 0.09F);
                after(0.09F, () -> ghost.hopTo(0.0F, LAND_FROM + 2.0F, -6.0F, 1.2F, 0.22F));
                after(0.31F, () -> ghost.setVisible(false));
            });
            view.playSound(SoundEvents.LANTERN_HIT, "grill", 0.4F, 1.3F);
        } else if (GrillStation.EFFECT_TAKEN.equals(effect)) {
            taken(view, place, ItemStack.parseOptional(view.level().registryAccess(), data.getCompound(GrillStation.STACK)));
        } else if (GrillStation.EFFECT_SEASONED.equals(effect) && known) {
            seasoned(view, place, ItemStack.parseOptional(view.level().registryAccess(), data.getCompound(GrillStation.STACK)));
        }
    }

    /**
     * A seasoning over the skewer on {@code place}: brought from the cook and shaken out over it, a powder's grains or a sauce's drops
     * falling along it; its colour shows on the skewer once it is out.
     */
    private void seasoned(StationView view, int place, ItemStack seasoning) {
        Optional<NodeView> hand = view.node("hand_" + place);
        if (seasoning.isEmpty() || hand.isEmpty()) {
            return;
        }
        if (going[place] != null) {
            going[place].stop();
        }
        Vector3f from = FromPlayer.point(view, TOP + 6.0F, 0, 1).orElse(placeOf(place).add(0.0F, 8.0F, -10.0F));
        Vector3f onto = placeOf(place);
        KitchenMotions.Shake shake = new KitchenMotions.Shake(hand.get(), seasoning, from, onto, () -> TOP + 0.6F, null, HAND_SIZE,
                place == 0 ? 1.0F : -1.0F, 0.0F);
        going[place] = shake;
        seasonedFrom[place] = clock + shake.doneBy() * 0.7F;
        shownLook[place] = null;
        after(shake.doneBy() * 0.7F, () -> {
            shownLook[place] = null;
            refresh(view);
            if (isSauce(seasoning)) {
                view.playSound(SoundEvents.HONEY_DRINK, "grill", 0.3F, 1.2F);
            }
        });
        refresh(view);
    }

    /** A skewer leaves the grill for the cook's inventory: it lifts and melts away where it was, the drawer shows it arriving. */
    private void taken(StationView view, int place, ItemStack stack) {
        if (place < 0 || place >= KitchenGrill.PLACES || stack.isEmpty()) {
            return;
        }
        Vector3f at = placeOf(place).sub(8.0F, TOP, 8.0F);
        ghost(view, stack).ifPresent(ghost -> {
            ghost.moveTo(at.x, 0.0F, at.z, 0.0F);
            ghost.rotateTo(0.0F, yaw(place), 0.0F, 0.0F);
            ghost.hopTo(at.x, 2.5F, at.z, 1.5F, AWAY_TIME);
            ghost.scaleTo(0.0F, AWAY_TIME);
            after(AWAY_TIME, () -> ghost.setVisible(false));
        });
        // The real one goes at once; the state that says so may come a moment later.
        view.node("piece_" + place).ifPresent(node -> node.setVisible(false));
        view.node("dressing_" + place).ifPresent(node -> node.setVisible(false));
        shown[place] = ItemStack.EMPTY;
        shownLook[place] = null;
        view.playSound(SoundEvents.ITEM_PICKUP, "grill", 0.3F, 1.5F);
    }

    private Optional<NodeView> ghost(StationView view, ItemStack stack) {
        ghost = (ghost + 1) % GHOSTS;
        Optional<NodeView> node = view.node("lift_" + ghost);
        node.ifPresent(it -> {
            Optional<ItemVoxels.Icon> icon = ItemVoxels.icon(stack);
            if (icon.isPresent()) {
                it.setVisual(null);
                it.setShape(ItemVoxels.build(icon.get(), SIZE, List.of(), 0.0F), icon.get().sprite());
            } else {
                it.setShape(null, null);
                it.setVisual(Visual.ofItem(stack.getItem()).withAnchor(new Vector3f(8.0F, 8.0F, 8.0F)));
            }
            it.setTint(0xFFFFFFFF);
            it.rotateTo(0.0F, 0.0F, 0.0F, 0.0F);
            it.scaleTo(1.0F, 0.0F);
            it.moveTo(0.0F, 0.0F, 0.0F, 0.0F);
            it.setVisible(true);
        });
        return node;
    }
}
