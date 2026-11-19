package com.pockyl.handson_delight.client;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.handson_delight.KitchenTags;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleSupplier;

/**
 * What lies in a vat, as a vat's scene shows it: dry, things of one kind in a pile, the piles spread over the floor; afloat, each
 * kind goes slowly round on the current, leaving a wake. What comes in flies from the player one by one: a liquid is poured from its
 * container, a powder shaken out, anything else hops in. Each place is a slot node {@code item_N} (its click place, which moves with
 * what it holds) with an item node {@code food_N} under it and a hand {@code hand_N} that pours or shakes. Shared by the vats of
 * Cultural Delights and Extra Delight.
 */
final class VatContents {
    /** Dry, things of one kind lie in a pile, each a little higher and turned further, the piles spread over the floor. */
    private static final float PILE_STEP = 0.3F;
    private static final float PILE_TURN = 38.0F;
    private static final float[][][] PILES = {
            {{0.0F, 0.0F}},
            {{2.6F, 0.0F}, {-2.6F, 0.0F}},
            {{2.8F, 1.6F}, {-2.8F, 1.6F}, {0.0F, -2.6F}},
            {{2.6F, 2.4F}, {-2.6F, 2.4F}, {2.6F, -2.4F}, {-2.6F, -2.4F}},
            {{3.2F, 2.8F}, {-3.2F, 2.8F}, {3.2F, -2.6F}, {-3.2F, -2.6F}, {0.0F, 0.1F}},
            {{3.4F, 2.8F}, {0.0F, 3.2F}, {-3.4F, 2.8F}, {3.4F, -2.6F}, {0.0F, -2.9F}, {-3.4F, -2.6F}}};
    /** The piles lie a little towards the back, clear of the near wall in the camera's view. */
    static final float PILE_BACK = 1.6F;
    /** Afloat, each kind goes round a circle about the middle, pixels from it, bobbing; settling, how fast a thing goes to its place. */
    private static final float FLOAT_RADIUS = 3.0F;
    private static final float FLOAT_BOB = 0.15F;
    private static final float SETTLE = 6.0F;
    private static final float WAKE_EVERY = 0.12F;
    /** Things coming in: how far above the vat they set off from the player, the arc of their hop, seconds in the air, between them. */
    static final float ARRIVE_FROM = 10.0F;
    private static final float ARRIVE_ARC = 3.0F;
    private static final float ARRIVE_TIME = 0.4F;
    private static final float ARRIVE_STAGGER = 0.14F;
    /** Liquids are poured and powders shaken a little to the right of the middle, the hand coming from the cook's side. */
    static final float POUR_X = 6.6F;
    static final float POUR_Z = 7.4F;
    private static final float POUR_SPREAD = 1.6F;
    static final float HAND_SIZE = 3.4F;
    private static final org.joml.Vector3fc ICON_MIDDLE = new Vector3f(8.0F, 8.0F, 8.0F);

    private final int slots;
    /** The inside's floor and its rim, pixels. */
    private final float floor;
    private final float rim;
    /** Where the liquid's surface stands now, pixels. */
    private final DoubleSupplier surface;
    private final ItemStack[] shown;
    /** Where each thing lies dry, its node's pixels from the middle of the floor, and how far turned; where it is now. */
    private final Vector3f[] laidAt;
    private final float[] laidTurn;
    /** Afloat: which kind each thing is of, how many kinds, its layer in its kind's pile; each kind's share of the circle as it eases. */
    private final int[] kindOf;
    private final int[] layerOf;
    private int kinds;
    private final float[] share;
    private final Vector3f[] at;
    private final Item[] drawnFood;
    /** Seconds until each thing sets off from the player (NaN: not coming), and its seconds in the air once it has. */
    private final float[] arriveIn;
    private final float[] flying;
    private final Vector3f[] arriveFrom;
    private final KitchenMotions.Motion[] going;
    private float wakeClock;

    VatContents(int slots, float floor, float rim, DoubleSupplier surface) {
        this.slots = slots;
        this.floor = floor;
        this.rim = rim;
        this.surface = surface;
        shown = new ItemStack[slots];
        laidAt = new Vector3f[slots];
        laidTurn = new float[slots];
        kindOf = new int[slots];
        layerOf = new int[slots];
        share = new float[slots];
        at = new Vector3f[slots];
        drawnFood = new Item[slots];
        arriveIn = new float[slots];
        flying = new float[slots];
        arriveFrom = new Vector3f[slots];
        going = new KitchenMotions.Motion[slots];
        Arrays.fill(shown, ItemStack.EMPTY);
        Arrays.fill(arriveIn, Float.NaN);
        Arrays.fill(flying, Float.NaN);
        for (int i = 0; i < slots; i++) {
            laidAt[i] = new Vector3f();
            at[i] = new Vector3f();
            arriveFrom[i] = new Vector3f();
        }
    }

    static String itemNode(int index) {
        return "item_" + index;
    }

    /** The scene's models are new: every food node is drawn again. */
    void sceneReady() {
        Arrays.fill(drawnFood, null);
    }

    ItemStack shown(int index) {
        return shown[index];
    }

    ItemStack[] shown() {
        return shown;
    }

    /** Whether {@code stack} goes in as a liquid, poured out of its container: it is the vat's liquid, nothing that floats. */
    static boolean isPoured(ItemStack stack) {
        return KitchenTags.is(stack, KitchenTags.POURED);
    }

    /** Whether {@code stack} is shaken in and dissolves: salt, sugar, powders. */
    static boolean dissolves(ItemStack stack) {
        return KitchenTags.is(stack, KitchenTags.DISSOLVES);
    }

    static boolean isSeen(ItemStack stack) {
        return !stack.isEmpty() && !isPoured(stack) && !dissolves(stack);
    }

    /**
     * What lies in the places now. Only more in the vat is an arrival: things shared out over the places come from where they lay,
     * not from the player. New arrivals fly from the player one after another.
     */
    void update(StationView view, ItemStack[] items) {
        boolean more = Arrays.stream(items).mapToInt(ItemStack::getCount).sum() > Arrays.stream(shown).mapToInt(ItemStack::getCount).sum();
        List<Integer> arrivals = new ArrayList<>();
        for (int i = 0; i < slots && i < items.length; i++) {
            if (!items[i].isEmpty() && (shown[i].isEmpty() || !ItemStack.isSameItem(items[i], shown[i])) && view.isFocused() && more) {
                arrivals.add(i);
            }
            if (items[i].isEmpty()) {
                arriveIn[i] = Float.NaN;
                flying[i] = Float.NaN;
                if (going[i] != null) {
                    going[i].stop();
                    going[i] = null;
                }
            }
            shown[i] = items[i].copy();
        }
        layOut();
        for (int k = 0; k < arrivals.size(); k++) {
            int index = arrivals.get(k);
            Optional<Vector3f> from = FromPlayer.point(view, rim + ARRIVE_FROM, k, arrivals.size());
            if (from.isPresent()) {
                arriveIn[index] = k * ARRIVE_STAGGER;
                arriveFrom[index].set(from.get());
                view.node(itemNode(index)).ifPresent(node -> node.setVisible(false));
            }
        }
    }

    /**
     * Where each thing lies: dry, things of one kind in a pile, the piles spread over the floor; afloat, each kind's pile goes round on
     * the current (worked out every frame). Poured and dissolved things are the liquid: their click places lie on it near the cook.
     */
    private void layOut() {
        List<Item> seen = new ArrayList<>();
        for (ItemStack stack : shown) {
            if (isSeen(stack) && !seen.contains(stack.getItem())) {
                seen.add(stack.getItem());
            }
        }
        kinds = seen.size();
        int[] layers = new int[Math.max(1, kinds)];
        int hidden = 0;
        for (int i = 0; i < slots; i++) {
            if (!isSeen(shown[i])) {
                laidAt[i].set(-2.0F + 2.0F * hidden++, 0.0F, -2.5F);
                laidTurn[i] = 0.0F;
                kindOf[i] = -1;
                continue;
            }
            int kind = seen.indexOf(shown[i].getItem());
            float[] place = PILES[Math.min(kinds, PILES.length) - 1][Math.min(kind, PILES.length - 1)];
            int layer = layers[kind]++;
            kindOf[i] = kind;
            layerOf[i] = layer;
            laidAt[i].set(place[0], PILE_STEP * layer, place[1] + PILE_BACK);
            laidTurn[i] = PILE_TURN * layer + 25.0F * kind;
        }
    }

    /**
     * A frame: each thing is drawn, comes in, goes where it belongs and can be clicked once it is there and the vat is open; what
     * goes round on the liquid leaves a wake. {@code level} is how high the liquid stands (0 to 1), {@code turned} the current's angle.
     */
    void frame(StationView view, float dt, float time, float turned, float level, int liquidColor, boolean inside, boolean wet,
            boolean moving, boolean reachable) {
        for (int i = 0; i < slots; i++) {
            food(view, i);
            arrive(view, i, dt, level, liquidColor);
            place(view, i, dt, time, turned, level, inside, wet);
            boolean enabled = reachable && Float.isNaN(arriveIn[i]) && Float.isNaN(flying[i]);
            view.node(itemNode(i)).ifPresent(node -> node.setEnabled(enabled));
            if (going[i] != null && !going[i].frame(view, dt)) {
                going[i] = null;
            }
        }
        wakes(view, dt, moving);
    }

    /** The thing in place {@code index}, drawn by its food node: an item lying flat; nothing for what is poured or dissolved. */
    private void food(StationView view, int index) {
        Item item = isSeen(shown[index]) ? shown[index].getItem() : null;
        if (item == drawnFood[index]) {
            return;
        }
        drawnFood[index] = item;
        view.node("food_" + index).ifPresent(node -> {
            node.setVisible(item != null);
            if (item != null) {
                node.setVisual(Visual.ofItem(item).withAnchor(ICON_MIDDLE));
            }
        });
    }

    /**
     * A thing on its way from the player waits its turn hidden; then a liquid is poured from its container, a powder shaken out, and
     * anything else hops in from where it set off.
     */
    private void arrive(StationView view, int index, float dt, float level, int liquidColor) {
        if (!Float.isNaN(arriveIn[index])) {
            arriveIn[index] -= dt;
            if (arriveIn[index] > 0.0F) {
                return;
            }
            arriveIn[index] = Float.NaN;
            ItemStack stack = shown[index];
            NodeView hand = view.node("hand_" + index).orElse(null);
            NodeView brew = view.node("brew").orElse(null);
            Vector3f onto = new Vector3f(POUR_X, 0.0F, POUR_Z + POUR_SPREAD * (index % 3 - 1));
            if (hand != null && isPoured(stack)) {
                if (brew != null) {
                    brew.holdLevel(KitchenMotions.Pour.pouredBy(stack) - 0.5F, 0.6F);
                }
                going[index] = new KitchenMotions.Pour(hand, "pour_" + index, stack, arriveFrom[index], onto, surface, "brew", HAND_SIZE,
                        -1.0F, 0.0F).lipAtLeast(rim + 1.0F);
            } else if (hand != null && dissolves(stack)) {
                going[index] = new KitchenMotions.Shake(hand, stack, arriveFrom[index], onto, surface, brew, HAND_SIZE * 0.8F, -1.0F, 0.0F);
            } else {
                flying[index] = 0.0F;
                Vector3f start = new Vector3f(arriveFrom[index]).sub(8.0F, floor, 8.0F);
                at[index].set(start);
                view.node(itemNode(index)).ifPresent(node -> {
                    node.moveTo(start.x, start.y, start.z, 0.0F);
                    node.setVisible(true);
                });
            }
            return;
        }
        if (Float.isNaN(flying[index])) {
            return;
        }
        float before = flying[index];
        flying[index] += dt;
        if (before < ARRIVE_TIME && flying[index] >= ARRIVE_TIME) {
            flying[index] = Float.NaN;
            boolean wet = level > 0.02F;
            Vector3f where = new Vector3f(at[index]).add(8.0F, floor, 8.0F);
            if (wet) {
                view.node("brew").ifPresent(brew -> brew.ripple(where.x, where.z, 0.45F));
                view.spawnParticles(DiegesisParticles.SPLASH, where, new Vector3f(0.0F, 5.0F, 0.0F), 0.6F, 4, 0xFF000000 | liquidColor);
                view.playSound(SoundEvents.GENERIC_SPLASH, itemNode(index), 0.15F, 1.6F);
            } else {
                view.playSound(SoundEvents.STONE_HIT, itemNode(index), 0.25F, 1.5F);
            }
        }
    }

    /** Where thing {@code index} goes now, its node's pixels from the middle of the floor: its pile, on the floor or afloat on the current. */
    private Vector3f home(int index, boolean wet, float time, float turned) {
        float top = (float) surface.getAsDouble();
        if (!wet || kindOf[index] < 0) {
            Vector3f dry = new Vector3f(laidAt[index]);
            if (wet) {
                dry.y = top - floor + 0.1F;
            }
            return dry;
        }
        share[kindOf[index]] = Mth.lerp(0.08F, share[kindOf[index]], kindOf[index] * Mth.TWO_PI / Math.max(1, kinds));
        float angle = turned + share[kindOf[index]];
        float radius = kinds == 1 ? FLOAT_RADIUS * 0.7F : FLOAT_RADIUS;
        float bob = FLOAT_BOB * Mth.sin(time * 2.1F + index * 1.7F);
        return new Vector3f(Mth.cos(angle) * radius, top - floor + 0.1F + PILE_STEP * layerOf[index] + bob,
                Mth.sin(angle) * radius + PILE_BACK);
    }

    /**
     * Each thing goes where it belongs: a hop from the player while it comes in, then its pile on the floor, or round on the current
     * afloat, rising and falling with the liquid.
     */
    private void place(StationView view, int index, float dt, float time, float turned, float level, boolean inside, boolean wet) {
        Optional<NodeView> node = view.node(itemNode(index));
        if (node.isEmpty() || !Float.isNaN(arriveIn[index])) {
            return;
        }
        Vector3f to = home(index, wet && level > 0.02F, time, turned);
        if (!Float.isNaN(flying[index])) {
            float k = Math.min(1.0F, flying[index] / ARRIVE_TIME);
            Vector3f from = new Vector3f(arriveFrom[index]).sub(8.0F, floor, 8.0F);
            at[index].set(from).lerp(to, KitchenMotions.smooth(k)).add(0.0F, ARRIVE_ARC * Mth.sin(Mth.PI * k), 0.0F);
        } else {
            at[index].lerp(to, Math.min(1.0F, dt * SETTLE));
        }
        node.get().moveTo(at[index].x, at[index].y, at[index].z, 0.0F);
        // Afloat, a thing turns slowly with the current it rides on.
        float spin = wet && kindOf[index] >= 0 ? turned * Mth.RAD_TO_DEG * 0.6F : 0.0F;
        node.get().rotateTo(0.0F, laidTurn[index] + spin, 0.0F, 0.0F);
        boolean coming = going[index] != null;
        node.get().setVisible(inside && !coming || !Float.isNaN(flying[index]));
    }

    /** What goes round on the liquid leaves a trail behind it. */
    private void wakes(StationView view, float dt, boolean moving) {
        wakeClock -= dt;
        if (!moving || wakeClock > 0.0F) {
            return;
        }
        wakeClock = WAKE_EVERY;
        view.node("brew").ifPresent(brew -> {
            boolean[] done = new boolean[slots];
            for (int i = 0; i < slots; i++) {
                int kind = kindOf[i];
                if (kind < 0 || shown[i].isEmpty() || done[kind] || !Float.isNaN(flying[i])) {
                    continue;
                }
                done[kind] = true;
                brew.leaveWake(at[i].x + 8.0F, at[i].z + 8.0F, 1.6F, 0.6F);
            }
        });
    }
}
