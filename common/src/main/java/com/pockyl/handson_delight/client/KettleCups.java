package com.pockyl.handson_delight.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 * Drinks poured from the kettle's spout, one cup at a time, as a cartoon pours them: the cook picks the kettle up (it hops off the
 * stove and gets smaller in the hand) while the cup comes from the player, tips it over the cup, a thin stream runs in, the kettle is
 * righted with a last drop, and the cup, now the drink, goes back to the player as the kettle is set down again. The cook's own cup comes at the click, before the kettle answers; drinks other
 * players pour come with the kettle's word. Several at once come one after the other, quicker.
 */
final class KettleCups {
    private static final String NODE = "cup";
    private static final String STREAM = "kettle_spout";
    /** The mug is 6 px high in its model; how full it is drawn. */
    private static final float MUG_HEIGHT = 6.0F;
    private static final float FULL = 0.85F;
    private static final SoundEvent POUR = SoundEvent.createVariableRangeEvent(HandsOnDelight.id("keg.pour"));
    private static final SoundEvent POUR_QUICK = SoundEvent.createVariableRangeEvent(HandsOnDelight.id("keg.pour_quick"));
    /** How high a cup is drawn, pixels (items are 16 at scale 1), and how far under the spout's lip its rim is held. */
    static final float HEIGHT = 3.6F;
    private static final float UNDER = 1.6F;
    /** How far it leans its rim in to the stream, degrees, and how small it is where it comes from and goes to. */
    private static final float LEAN = 10.0F;
    private static final float FAR_SCALE = 0.55F;
    private static final float ARC = 3.0F;
    /** A cup the cook brought waits this long for the kettle's word; without it, it goes back empty. */
    private static final float ANSWER = 0.8F;
    /** Seconds the kettle takes to be set down when no cup follows. */
    private static final float SET_DOWN = 0.22F;
    /** A drink poured: about this many millibuckets for each cup filled. */
    private static final int SERVING = 250;

    /** Seconds: coming, the kettle lifted and tipped, running, set down with the last drop, going. */
    private record Pace(float come, float tip, float run, float settle, float go) {
        static final Pace ONE = new Pace(0.26F, 0.16F, 0.8F, 0.16F, 0.3F);
        static final Pace QUICK = new Pace(0.16F, 0.1F, 0.45F, 0.1F, 0.2F);
    }

    /** One cup on its way: what it is, what it becomes, whose it is and where it goes. */
    private static final class Cup {
        private final ItemStack vessel;
        private final ItemStack drink;
        private final ResourceLocation liquid;
        /** ARGB, the alpha how clear the drink is. */
        private final int color;
        private final boolean own;
        private final boolean fromHand;
        private final int held;
        private boolean toHand;
        private Pace pace;
        private float age;
        private float waited;
        private boolean answered;
        private boolean dry;
        private boolean poured;
        private boolean filled;
        private Vector3f from;

        private Cup(ItemStack vessel, ItemStack drink, ResourceLocation liquid, int color, boolean own, boolean fromHand, boolean toHand,
                Pace pace) {
            this.vessel = vessel.copyWithCount(1);
            this.held = vessel.getCount();
            this.drink = drink.copyWithCount(1);
            this.liquid = liquid;
            this.color = color;
            this.own = own;
            this.fromHand = fromHand;
            this.toHand = toHand;
            this.pace = pace;
        }

        private Cup next() {
            Cup cup = new Cup(vessel.copyWithCount(held), drink, liquid, color, own, fromHand, false, Pace.QUICK);
            cup.answered = true;
            return cup;
        }

        /** A bottle is shown as a glass mug, the drink rising in it, as the kettle's drinks look; anything else as itself. */
        private boolean isMug() {
            return vessel.is(Items.GLASS_BOTTLE);
        }
    }

    private final Deque<Cup> waiting = new ArrayDeque<>();
    private Cup current;
    private Visual mug;
    private Item shownItem;
    private boolean handHidden;
    /** How far the kettle is tipped now, 0 standing to 1 pouring, and how far it is picked up, 0 on the stove to 1 in the hand. */
    private float tip;
    private float hold;

    /** The scene is new: the cup's own model is what a bottle shows. */
    void ready(StationView view) {
        view.node(NODE).ifPresent(node -> {
            mug = node.visual().orElse(null);
            node.setVisible(false);
        });
        shownItem = null;
    }

    /** Whether a cup is at the spout or on its way. */
    boolean busy() {
        return current != null || !waiting.isEmpty();
    }

    /** How far the kettle is tipped this frame, 0 to 1. */
    float tip() {
        return tip;
    }

    /** How far the kettle is picked up this frame, 0 on the stove to 1 in the cook's hand. */
    float hold() {
        return hold;
    }

    /**
     * The cook pours a drink into {@code vessel} (with {@code all}, as many as there are): the cup comes at once, the kettle answers
     * after. It comes from the hand ({@code fromHand}) or from the inventory; the drink goes back to the hand when the hand held only
     * that cup, else into the inventory.
     */
    void pour(StationView view, ItemStack vessel, ItemStack drink, ResourceLocation liquid, int color, boolean fromHand, boolean all) {
        Pace pace = all && vessel.getCount() > 1 ? Pace.QUICK : Pace.ONE;
        waiting.add(new Cup(vessel, drink, liquid, color, true, fromHand, fromHand && vessel.getCount() == 1, pace));
        if (fromHand && view.isFocused() && !handHidden) {
            view.setCarriedHidden(true);
            handHidden = true;
        }
    }

    /**
     * The kettle poured {@code millibuckets} into {@code vessel}: the cook's own cup that waits for it goes on, with more after it when
     * several were filled; a drink somebody else poured comes now.
     */
    void served(ItemStack vessel, ItemStack drink, ResourceLocation liquid, int color, int millibuckets) {
        int drinks = Math.max(1, Math.round((float) millibuckets / SERVING));
        Cup own = unanswered();
        if (own != null) {
            own.answered = true;
            if (drinks > 1 && own.pace == Pace.ONE && own.age <= own.pace.come()) {
                own.pace = Pace.QUICK;
            }
            List<Cup> order = new ArrayList<>(waiting);
            int at = own == current ? 0 : order.indexOf(own) + 1;
            Cup last = own;
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
            Cup cup = new Cup(vessel, drink, liquid, color, false, false, false, drinks > 1 ? Pace.QUICK : Pace.ONE);
            cup.answered = true;
            waiting.add(cup);
        }
    }

    private Cup unanswered() {
        if (current != null && current.own && !current.answered && !current.dry) {
            return current;
        }
        for (Cup cup : waiting) {
            if (cup.own && !cup.answered) {
                return cup;
            }
        }
        return null;
    }

    /** The cook left: what is on its way is put away at once, the kettle set down. */
    void clear(StationView view) {
        waiting.clear();
        current = null;
        tip = 0.0F;
        hold = 0.0F;
        hide(view);
    }

    /**
     * Moves the cup and tells how far the kettle is tipped.
     *
     * @param lip     where the drink leaves the spout with the kettle fully tipped, scene pixels
     * @param running where it leaves the spout this frame
     * @param outward the way the spout faces, level, unit
     * @param drink   the kettle's liquid node, whose level sinks as the drink runs
     */
    void frame(StationView view, float dt, Vector3fc lip, Vector3fc running, Vector3fc outward, NodeView drink) {
        NodeView node = view.node(NODE).orElse(null);
        if (node == null) {
            waiting.clear();
            current = null;
            tip = 0.0F;
            hold = 0.0F;
            return;
        }
        if (current == null) {
            current = waiting.poll();
            if (current == null) {
                tip = 0.0F;
                hold = Math.max(0.0F, hold - dt / SET_DOWN);
                hide(view);
                return;
            }
            start(view, node, current, lip, drink);
        }
        Cup cup = current;
        cup.age += dt;
        cup.waited += dt;
        Pace pace = cup.pace;
        if (cup.own && !cup.answered && !cup.dry && cup.waited > ANSWER) {
            cup.dry = true;
        }
        if (!cup.answered && !cup.dry && cup.age > pace.come()) {
            // Held at the spout until the kettle answers.
            cup.age = pace.come();
        }
        float tipTime = cup.dry ? 0.0F : pace.tip();
        float run = cup.dry ? 0.0F : pace.run();
        float settle = cup.dry ? 0.0F : pace.settle();
        float t = cup.age;
        Vector3f rim = new Vector3f(lip).add(outward.x() * 0.6F, -UNDER, outward.z() * 0.6F);
        Vector3f held = new Vector3f(rim).sub(0.0F, HEIGHT, 0.0F);
        float lean = 0.0F;
        float size = 1.0F;
        float fraction = 0.0F;
        Vector3f at;
        tip = 0.0F;
        if (t < pace.come()) {
            float k = KitchenMotions.out(t / pace.come());
            // The kettle is picked up as the cup comes, or stays in the hand from the cup before.
            hold = Math.max(hold, k);
            at = new Vector3f(cup.from).lerp(held, k);
            at.y += ARC * 0.5F * Mth.sin(k * Mth.PI);
            size = Mth.lerp(k, FAR_SCALE, 1.0F);
            lean = LEAN * k;
        } else if (t < pace.come() + tipTime) {
            // Tipped over the cup.
            hold = 1.0F;
            tip = KitchenMotions.smooth((t - pace.come()) / tipTime);
            at = held;
            lean = LEAN;
        } else if (t < pace.come() + tipTime + run) {
            float k = (t - pace.come() - tipTime) / run;
            hold = 1.0F;
            tip = 1.0F;
            at = held;
            lean = LEAN * (1.0F - KitchenMotions.smooth(k * 1.3F));
            if (!cup.poured) {
                cup.poured = true;
                view.playSound(SoundEvents.BOTTLE_FILL, NODE, 0.55F, 1.05F + view.level().random.nextFloat() * 0.1F);
                view.playSound(pace == Pace.QUICK ? POUR_QUICK : POUR, NODE, 0.35F, 1.35F + view.level().random.nextFloat() * 0.1F);
            }
            stream(view, running, held, cup.color, k);
            // Fast while the mug is empty, slower as the drink nears the rim.
            fraction = FULL * (1.0F - (float) Math.pow(1.0F - k, 1.6F));
            if (!cup.filled && k > 0.82F) {
                // Full: what is in the hand is the drink now, hot.
                cup.filled = true;
                if (cup.isMug()) {
                    view.spawnParticles(DiegesisParticles.STEAM, new Vector3f(held).add(0.0F, HEIGHT, 0.0F), new Vector3f(0.0F, 3.0F, 0.0F),
                            0.4F, 2, 0x90FFFFFF);
                } else {
                    showItem(node, cup.drink.isEmpty() ? cup.vessel : cup.drink);
                }
            }
        } else if (t < pace.come() + tipTime + run + settle) {
            float k = (t - pace.come() - tipTime - run) / settle;
            hold = 1.0F;
            tip = 1.0F - KitchenMotions.smooth(k);
            fraction = cup.dry ? 0.0F : FULL;
            at = held;
            if (!cup.dry && k < 0.5F && k + dt / settle >= 0.5F) {
                view.spawnParticles(DiegesisParticles.DROPLET, new Vector3f(running), new Vector3f(0.0F, -10.0F, 0.0F), 0.0F, 1,
                        0xFF000000 | cup.color);
            }
        } else if (t < pace.come() + tipTime + run + settle + pace.go()) {
            float k = KitchenMotions.smooth((t - pace.come() - tipTime - run - settle) / pace.go());
            // Set down as the cup goes, unless another cup comes after it.
            hold = waiting.isEmpty() ? 1.0F - k : 1.0F;
            fraction = cup.dry ? 0.0F : FULL;
            Vector3f to = destination(view, cup, held);
            at = new Vector3f(held).lerp(to, k);
            at.y += ARC * Mth.sin(k * Mth.PI);
            size = Mth.lerp(k, 1.0F, FAR_SCALE);
            lean = -5.0F * Mth.sin(k * Mth.PI);
        } else {
            finish(view, node);
            return;
        }
        node.moveTo(at.x - 8.0F, at.y, at.z - 8.0F, 0.0F);
        // Held at the spout it is drawn where it is, behind the kettle nearer the cook; flying in and away, over everything, so
        // that the stove and the kettle never cut it.
        node.setOnTop(t < pace.come() || t >= pace.come() + tipTime + run + settle);
        if (cup.isMug()) {
            // Its handle away from the kettle, its rim leaning in to the stream.
            node.rotateTo(0.0F, (float) Mth.atan2(-outward.z(), outward.x()) * Mth.RAD_TO_DEG, lean, 0.0F);
            node.scaleTo(size * HEIGHT / MUG_HEIGHT, 0.0F);
            node.showLiquid(cup.liquid, fraction);
            node.setLiquidColor(cup.color);
        } else {
            // Its face to the cook, as an item is held up.
            node.rotateTo(lean, 180.0F, 0.0F, 0.0F);
            node.scaleTo(size * HEIGHT / 16.0F, 0.0F);
        }
    }

    private void start(StationView view, NodeView node, Cup cup, Vector3fc lip, NodeView drink) {
        float height = lip.y() + 3.0F;
        if (cup.own && view.isFocused()) {
            cup.from = (cup.fromHand ? view.cursorOn(height) : FromPlayer.point(view, height, 0, 1)).orElse(null);
        }
        if (cup.from == null) {
            cup.from = new Vector3f(lip).add(0.0F, 3.0F, -7.0F);
        }
        if (cup.own && cup.fromHand && view.isFocused() && !handHidden) {
            view.setCarriedHidden(true);
            handHidden = true;
        }
        if (cup.isMug()) {
            if (mug != null) {
                node.setVisual(mug);
            }
            shownItem = null;
            // A new mug starts empty, not where the last one was.
            node.showLiquid(null, 0.0F);
            node.showLiquid(cup.liquid, 0.0F);
        } else {
            node.showLiquid(null, 0.0F);
            showItem(node, cup.vessel);
        }
        node.setOnTop(true);
        node.setVisible(true);
        // The kettle's own level waits for the stream, then sinks as it runs, over all the cups that follow this one.
        if (drink != null && !cup.dry) {
            float over = cup.pace.run();
            for (Cup next : waiting) {
                over += next.pace.come() + next.pace.tip() + next.pace.run() + next.pace.settle() + next.pace.go();
            }
            drink.holdLevel(cup.pace.come() + cup.pace.tip(), over);
        }
    }

    private void showItem(NodeView node, ItemStack stack) {
        if (shownItem != stack.getItem()) {
            shownItem = stack.getItem();
            node.setVisual(Visual.ofItem(shownItem).withAnchor(new Vector3f(8.0F, 0.0F, 8.0F)));
        }
    }

    /** The drink running from the spout into the cup: thin, it starts at once and thins just before the cup is full. */
    private static void stream(StationView view, Vector3fc spout, Vector3f held, int color, float k) {
        float flow = Math.min(1.0F, k / 0.08F) * (k > 0.8F ? Math.max(0.15F, (1.0F - k) / 0.2F) : 1.0F);
        Vector3f to = new Vector3f(held).add(0.0F, HEIGHT * (0.3F + 0.5F * k), 0.0F);
        view.pourStream(STREAM, new Vector3f(spout), to, 0xFF000000 | color, 0.7F * flow, NODE);
    }

    /** Where the full cup goes: to the cursor it came from, to the inventory at the bottom of the screen, or back to whoever poured. */
    private static Vector3f destination(StationView view, Cup cup, Vector3f held) {
        float height = held.y + HEIGHT + 3.0F;
        if (cup.own && view.isFocused()) {
            Vector3f to = (cup.toHand ? view.cursorOn(height) : FromPlayer.point(view, height, 0, 1)).orElse(null);
            if (to != null) {
                return to;
            }
        }
        return new Vector3f(cup.from);
    }

    private void finish(StationView view, NodeView node) {
        current = null;
        node.setVisible(false);
        if (handHidden && waiting.stream().noneMatch(next -> next.own && next.fromHand)) {
            view.setCarriedHidden(false);
            handHidden = false;
        }
    }

    private void hide(StationView view) {
        view.node(NODE).ifPresent(node -> node.setVisible(false));
        if (handHidden) {
            view.setCarriedHidden(false);
            handHidden = false;
        }
    }
}
