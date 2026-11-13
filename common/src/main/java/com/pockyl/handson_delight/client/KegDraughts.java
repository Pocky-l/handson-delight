package com.pockyl.handson_delight.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.handson_delight.HandsOnDelight;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Drinks drawn at the keg's tap, one mug at a time: the mug comes from the player and is held under the tap leaning in, the drink
 * runs into it and rises while it is set upright, its head grows, the last drops fall from the tap, and the full mug goes back to
 * the player. The cook's own mug comes at the click, before the keg answers; the drinks other players draw come with the keg's
 * word. Several drawn at once come one after the other, quicker.
 */
final class KegDraughts {
    private static final String NODE = "mug";
    private static final String STREAM = "keg_tap";
    /** Brewin' and Chewin's tankard: its own model, the drink seen rising in it. Other containers are shown as their items. */
    private static final ResourceLocation TANKARD = ResourceLocation.tryBuild("brewinandchewin", "tankard");
    private static final SoundEvent POUR = SoundEvent.createVariableRangeEvent(HandsOnDelight.id("keg.pour"));
    private static final SoundEvent POUR_QUICK = SoundEvent.createVariableRangeEvent(HandsOnDelight.id("keg.pour_quick"));
    /** The mug's size in the scene (its model is 7 px high, its inside from 1 to 7) and how full it is drawn. */
    private static final float SCALE = 0.75F;
    private static final float HEIGHT = 7.0F * SCALE;
    private static final float FULL = 0.86F;
    /** An item that is not a tankard is drawn this many pixels high; items are 16 px. */
    private static final float ITEM_SIZE = 4.5F;
    /** Where it is held: its middle this far out from the tap's wall, its rim this far under the tap, pixels. */
    private static final float OUT = 2.1F;
    private static final float UNDER = 0.8F;
    /** How far it leans its rim in to the tap as the drink starts, degrees: it is set upright as it fills. */
    private static final float LEAN = 16.0F;
    /** How small it is where it comes from and where it goes, how high it swings on the way, pixels. */
    private static final float FAR_SCALE = 0.55F;
    private static final float ARC = 3.0F;
    /** Bubbles a second rising in a drink that fizzes, while it runs in and just after. */
    private static final float RUN_BUBBLES = 9.0F;
    /** A mug the cook brought waits this long for the keg's word; without it, it goes back empty. */
    private static final float ANSWER = 0.8F;
    /** A drink drawn: about this many millibuckets for each container filled. */
    private static final int DRINK = 250;

    /** Seconds: coming, running, the last drops, going. */
    private record Pace(float come, float run, float drip, float go) {
        static final Pace ONE = new Pace(0.22F, 0.95F, 0.3F, 0.32F);
        static final Pace QUICK = new Pace(0.16F, 0.55F, 0.12F, 0.24F);
    }

    /** One mug on its way: what it is, what is drawn into it, whose it is and where it goes. */
    private static final class Mug {
        private final ItemStack vessel;
        private final ResourceLocation liquid;
        /** ARGB, the alpha how clear the drink is. */
        private final int color;
        private final KegLiquids.Drink look;
        private final boolean own;
        private final boolean fromHand;
        /** How many containers the cook held: when every one is filled, the last drink stays in the hand. */
        private final int held;
        private boolean toHand;
        private Pace pace;
        private float age;
        /** Seconds since the click, counted on while it is held for the keg's answer. */
        private float waited;
        private boolean answered;
        private boolean dry;
        private boolean poured;
        private boolean spilled;
        private float bubbles;
        private Vector3f from;

        private Mug(ItemStack vessel, ResourceLocation liquid, int color, KegLiquids.Drink look, boolean own, boolean fromHand, boolean toHand,
                Pace pace) {
            this.vessel = vessel.copyWithCount(1);
            this.held = vessel.getCount();
            this.liquid = liquid;
            this.color = color;
            this.look = look;
            this.own = own;
            this.fromHand = fromHand;
            this.toHand = toHand;
            this.pace = pace;
        }

        private Mug next() {
            Mug mug = new Mug(vessel.copyWithCount(held), liquid, color, look, own, fromHand, false, Pace.QUICK);
            mug.answered = true;
            return mug;
        }

        private boolean isTankard() {
            return TANKARD.equals(BuiltInRegistries.ITEM.getKey(vessel.getItem()));
        }
    }

    private final Deque<Mug> waiting = new ArrayDeque<>();
    private Mug current;
    private Visual tankard;
    private Item shownItem;
    private boolean handHidden;

    /** The scene is new: the mug's own model is what a tankard shows. */
    void ready(StationView view) {
        view.node(NODE).ifPresent(node -> {
            tankard = node.visual().orElse(null);
            node.setVisible(false);
        });
        shownItem = null;
    }

    /** The middle of a mug held under the tap, scene pixels: where a container offered to an empty hand hangs. */
    static Vector3f heldMiddle(Vector3fc spout, Vector3fc outward) {
        return new Vector3f(spout).add(outward.x() * OUT, -UNDER - HEIGHT / 2.0F, outward.z() * OUT);
    }

    /** Whether a mug is at the tap or on its way. */
    boolean busy() {
        return current != null || !waiting.isEmpty();
    }

    /**
     * The cook draws a drink into {@code vessel} (with {@code all}, as many as there are): the mug comes at once, the keg answers
     * after. It comes from the hand ({@code fromHand}) or from the inventory; the drink goes back to the hand when the hand held only
     * that mug, else into the inventory.
     */
    void draw(StationView view, ItemStack vessel, ResourceLocation liquid, int color, KegLiquids.Drink look, boolean fromHand, boolean all) {
        Pace pace = all && vessel.getCount() > 1 ? Pace.QUICK : Pace.ONE;
        waiting.add(new Mug(vessel, liquid, color, look, true, fromHand, fromHand && vessel.getCount() == 1, pace));
        if (fromHand && view.isFocused() && !handHidden) {
            // At the click, before the mug sets off: the cursor's stack would cover it.
            view.setCarriedHidden(true);
            handHidden = true;
        }
    }

    /**
     * The keg drew {@code millibuckets} into {@code vessel}: the cook's own mug that waits for it goes on, with more after it when
     * several were filled; a drink somebody else drew comes now.
     */
    void tapped(StationView view, ItemStack vessel, ResourceLocation liquid, int color, KegLiquids.Drink look, int millibuckets) {
        int drinks = Math.max(1, Math.round((float) millibuckets / DRINK));
        Mug own = unanswered();
        if (own != null) {
            own.answered = true;
            if (drinks > 1 && own.pace == Pace.ONE && own.age <= own.pace.come()) {
                own.pace = Pace.QUICK;
            }
            // The others follow the one they belong to, before anything the cook clicked after it.
            List<Mug> order = new ArrayList<>(waiting);
            int at = own == current ? 0 : order.indexOf(own) + 1;
            Mug last = own;
            for (int i = 1; i < drinks; i++) {
                last = own.next();
                order.add(at++, last);
            }
            if (own.fromHand && own.held <= drinks) {
                own.toHand = false;
                last.toHand = true;
            }
            waiting.clear();
            waiting.addAll(order);
            return;
        }
        for (int i = 0; i < drinks; i++) {
            Mug mug = new Mug(vessel, liquid, color, look, false, false, false, drinks > 1 ? Pace.QUICK : Pace.ONE);
            mug.answered = true;
            waiting.add(mug);
        }
    }

    private Mug unanswered() {
        if (current != null && current.own && !current.answered && !current.dry) {
            return current;
        }
        for (Mug mug : waiting) {
            if (mug.own && !mug.answered) {
                return mug;
            }
        }
        return null;
    }

    /** The cook left: what is on its way is put away at once. */
    void clear(StationView view) {
        waiting.clear();
        current = null;
        hide(view);
    }

    /**
     * Moves the mug at the tap.
     *
     * @param spout   where the drink leaves the tap, scene pixels
     * @param outward the way the tap faces, level, unit
     * @param yaw     the keg's turn, degrees: the mug's handle stays to the cook's right
     * @param drink   the keg's liquid node, which the drink leaves
     */
    void frame(StationView view, float dt, Vector3fc spout, Vector3fc outward, float yaw, NodeView drink) {
        NodeView node = view.node(NODE).orElse(null);
        if (node == null) {
            waiting.clear();
            current = null;
            return;
        }
        if (current == null) {
            current = waiting.poll();
            if (current == null) {
                hide(view);
                return;
            }
            start(view, node, current, spout, outward, drink);
        }
        Mug mug = current;
        mug.age += dt;
        mug.waited += dt;
        Pace pace = mug.pace;
        if (mug.own && !mug.answered && !mug.dry && mug.waited > ANSWER) {
            mug.dry = true;
        }
        if (!mug.answered && !mug.dry && mug.age > pace.come()) {
            // Held under the tap until the keg answers.
            mug.age = pace.come();
        }
        float run = mug.dry ? 0.0F : pace.run();
        float drip = mug.dry ? 0.0F : pace.drip();
        float t = mug.age;
        Vector3f rim = new Vector3f(spout).add(outward.x() * OUT, -UNDER, outward.z() * OUT);
        Vector3f held = new Vector3f(rim).sub(0.0F, HEIGHT, 0.0F);
        float lean;
        float fraction;
        float size = 1.0F;
        Vector3f at;
        if (t < pace.come()) {
            float k = KitchenMotions.out(t / pace.come());
            at = new Vector3f(mug.from).lerp(held, k);
            at.y += ARC * 0.5F * Mth.sin(k * Mth.PI);
            size = Mth.lerp(k, FAR_SCALE, 1.0F);
            lean = LEAN * k;
            fraction = 0.0F;
        } else if (t < pace.come() + run) {
            float k = (t - pace.come()) / run;
            if (!mug.poured) {
                mug.poured = true;
                view.playSound(pace == Pace.QUICK ? POUR_QUICK : POUR, NODE, 0.7F, 0.96F + view.level().random.nextFloat() * 0.08F);
            }
            // Fast while the mug is empty, slower as the drink nears the rim; the mug is set upright as it fills.
            fraction = FULL * (1.0F - (float) Math.pow(1.0F - k, 1.7F));
            lean = LEAN * (1.0F - KitchenMotions.smooth(k * 1.35F));
            at = held;
            stream(view, spout, outward, held, lean, fraction, mug.color, k);
            if (!mug.spilled && k > 0.92F && mug.isTankard() && headOf(mug.look) >= 0.5F) {
                // A full head spills over the rim.
                mug.spilled = true;
                view.spawnParticles(DiegesisParticles.BUBBLE_POP, new Vector3f(held).add(0.0F, HEIGHT + 0.2F, 0.0F),
                        new Vector3f(0.0F, 0.6F, 0.0F), 1.1F, 5, mug.look.foamColor() != 0 ? mug.look.foamColor() : KegLiquids.HEAD);
                view.spawnParticles(DiegesisParticles.DROPLET, new Vector3f(held).add(0.0F, HEIGHT, 0.0F).sub(outward.x() * 1.6F, 0.0F,
                        outward.z() * 1.6F), new Vector3f(0.0F, -4.0F, 0.0F), 0.6F, 2, KegLiquids.HEAD);
            }
            // Bubbles while it runs in, none once it is nearly full: they would be left hanging where the mug was.
            fizz(view, mug, dt, k < 0.7F ? 1.0F : 0.0F);
        } else if (t < pace.come() + run + drip) {
            float k = (t - pace.come() - run) / drip;
            fraction = mug.dry ? 0.0F : FULL;
            lean = 0.0F;
            at = held;
            drops(view, node, spout, outward, mug, k, dt / drip);
        } else if (t < pace.come() + run + drip + pace.go()) {
            float k = KitchenMotions.smooth((t - pace.come() - run - drip) / pace.go());
            Vector3f to = destination(view, mug, held, outward);
            at = new Vector3f(held).lerp(to, k);
            at.y += ARC * Mth.sin(k * Mth.PI);
            size = Mth.lerp(k, 1.0F, FAR_SCALE);
            lean = -6.0F * Mth.sin(k * Mth.PI);
            fraction = mug.dry ? 0.0F : FULL;
        } else {
            finish(view, node, mug);
            return;
        }
        place(node, mug, at, lean, yaw, outward, size);
        if (mug.isTankard()) {
            node.showLiquid(mug.liquid, fraction);
            node.setLiquidColor(mug.color);
            float head = headOf(mug.look);
            float k = t < pace.come() ? 0.0F : Math.min(1.0F, (t - pace.come()) / Math.max(0.01F, run));
            node.setFoam(mug.dry ? 0.0F : head * KitchenMotions.smooth(k * 1.25F - 0.1F), mug.look == null ? KegLiquids.HEAD : mug.look.foamColor());
        }
    }

    private void start(StationView view, NodeView node, Mug mug, Vector3fc spout, Vector3fc outward, NodeView drink) {
        Vector3f rim = new Vector3f(spout).add(outward.x() * OUT, -UNDER, outward.z() * OUT);
        float height = rim.y + 3.0F;
        if (mug.own && view.isFocused()) {
            mug.from = (mug.fromHand ? view.cursorOn(height) : FromPlayer.point(view, height, 0, 1)).orElse(null);
        }
        if (mug.from == null) {
            // Somebody standing in front of the keg.
            mug.from = new Vector3f(rim).add(outward.x() * 7.0F, 4.0F, outward.z() * 7.0F);
        }
        // What the cook holds stays out of sight while their mugs are filled, not to cover the tap.
        if (mug.own && mug.fromHand && view.isFocused() && !handHidden) {
            view.setCarriedHidden(true);
            handHidden = true;
        }
        if (mug.isTankard()) {
            if (tankard != null) {
                node.setVisual(tankard);
            }
            shownItem = null;
            // A new mug starts empty, not where the last one was.
            node.showLiquid(null, 0.0F);
            node.showLiquid(mug.liquid, 0.0F);
            node.setFoam(0.0F, KegLiquids.HEAD);
        } else if (shownItem != mug.vessel.getItem()) {
            shownItem = mug.vessel.getItem();
            node.setVisual(Visual.ofItem(shownItem).withAnchor(new Vector3f(8.0F, 0.0F, 8.0F)));
            node.showLiquid(null, 0.0F);
        }
        node.setOnTop(true);
        node.setVisible(true);
        // The keg's own level waits for the stream, then sinks as it runs, over all the mugs that follow this one.
        if (drink != null && !mug.dry) {
            float over = mug.pace.run();
            for (Mug next : waiting) {
                over += next.pace.come() + next.pace.run() + next.pace.drip() + next.pace.go();
            }
            drink.holdLevel(mug.pace.come(), over);
        }
    }

    /** The mug's place and pose: {@code at} is its bottom; leaning, it turns round its rim, which stays under the tap. */
    private void place(NodeView node, Mug mug, Vector3f at, float lean, float yaw, Vector3fc outward, float size) {
        float radians = lean * Mth.DEG_TO_RAD;
        float tall = HEIGHT * size;
        Vector3f bottom = new Vector3f(at).add(0.0F, tall * (1.0F - Mth.cos(radians)), 0.0F)
                .add(outward.x() * tall * Mth.sin(radians), 0.0F, outward.z() * tall * Mth.sin(radians));
        node.moveTo(bottom.x - 8.0F, bottom.y, bottom.z - 8.0F, 0.0F);
        node.rotateTo(lean, yaw, 0.0F, 0.0F);
        float scale = mug.isTankard() ? size : size * ITEM_SIZE / 16.0F / SCALE;
        node.scaleTo(scale, 0.0F);
    }

    /** The drink running from the tap into the mug: it starts at once, runs full, and thins just before the mug is full. */
    private static void stream(StationView view, Vector3fc spout, Vector3fc outward, Vector3f held, float lean, float fraction, int color, float k) {
        float flow = Math.min(1.0F, k / 0.06F) * (k > 0.85F ? Math.max(0.2F, (1.0F - k) / 0.15F) : 1.0F);
        // It lands on the drink near the side of the mug under the tap, where a cellarman lets it run down the wall.
        float surface = held.y + (1.0F + 5.9F * fraction) * SCALE;
        Vector3f to = new Vector3f(held.x - outward.x() * 0.6F, surface, held.z - outward.z() * 0.6F);
        view.pourStream(STREAM, new Vector3f(spout), to, 0xFF000000 | color, flow * (0.55F + 0.45F * (1.0F - lean / LEAN)), NODE);
    }

    /** Bubbles rising in a drink that fizzes. */
    private static void fizz(StationView view, Mug mug, float dt, float strength) {
        if (mug.look == null || mug.look.fizz() <= 0.0F || !mug.isTankard()) {
            return;
        }
        mug.bubbles += dt * RUN_BUBBLES * strength * Math.min(1.0F, mug.look.fizz());
        if (mug.bubbles >= 1.0F) {
            view.spawnInLiquid(DiegesisParticles.BUBBLE, NODE, (int) mug.bubbles, 0xE0FFFFFF, new Vector3f(0.0F, 2.5F, 0.0F));
            mug.bubbles -= (int) mug.bubbles;
        }
    }

    /** The last two drops fall from the tap into the full mug, each with a ring. */
    private static void drops(StationView view, NodeView node, Vector3fc spout, Vector3fc outward, Mug mug, float k, float step) {
        if (mug.dry) {
            return;
        }
        for (float when : new float[] {0.12F, 0.55F}) {
            if (k >= when && k - step < when) {
                view.spawnParticles(DiegesisParticles.DROPLET, new Vector3f(spout).add(outward.x() * 0.2F, -0.2F, outward.z() * 0.2F),
                        new Vector3f(0.0F, -12.0F, 0.0F), 0.0F, 1, 0xFF000000 | mug.color);
            }
            float lands = when + 0.3F;
            if (k >= lands && k - step < lands) {
                node.ripple(0.0F, 0.0F, 0.12F);
            }
        }
    }

    /** Where the full mug goes: to the cursor it came from, to the inventory at the bottom of the screen, or back to whoever drew it. */
    private static Vector3f destination(StationView view, Mug mug, Vector3f held, Vector3fc outward) {
        float height = held.y + HEIGHT + 3.0F;
        if (mug.own && view.isFocused()) {
            Vector3f to = (mug.toHand ? view.cursorOn(height) : FromPlayer.point(view, height, 0, 1)).orElse(null);
            if (to != null) {
                return to;
            }
        }
        return new Vector3f(mug.from);
    }

    private void finish(StationView view, NodeView node, Mug mug) {
        current = null;
        node.setVisible(false);
        node.showLiquid(null, 0.0F);
        if (handHidden && waiting.stream().noneMatch(next -> next.own && next.fromHand)) {
            view.setCarriedHidden(false);
            handHidden = false;
        }
    }

    private void hide(StationView view) {
        view.node(NODE).ifPresent(node -> {
            node.setVisible(false);
            node.showLiquid(null, 0.0F);
        });
        if (handHidden) {
            view.setCarriedHidden(false);
            handHidden = false;
        }
    }

    /** How much of the mug's top a drink's head covers once it is full: a beer's whole top, a liqueur's none. */
    private static float headOf(KegLiquids.Drink look) {
        if (look == null) {
            return 0.0F;
        }
        return look.foam() >= 0.3F ? 1.0F : Math.min(1.0F, look.foam() * 2.5F);
    }
}
