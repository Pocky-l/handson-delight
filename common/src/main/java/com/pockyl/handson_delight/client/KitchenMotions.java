package com.pockyl.handson_delight.client;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.scene.Visual;

import java.util.Optional;
import java.util.function.DoubleSupplier;

/**
 * What a cook's hands do with food the same way at every station, played on nodes that stand at the scene's origin with no turn
 * of their own, so that their moves are scene pixels: a container is brought over, tipped and poured out in a stream; an egg is
 * knocked on a rim and its inside drops; sugar is shaken out; a container is dipped into a soup and comes up full. Each motion
 * is advanced by the station's frame and says when it is over.
 */
final class KitchenMotions {
    private static final Vector3f ITEM_MIDDLE = new Vector3f(8.0F, 8.0F, 8.0F);
    /**
     * Upright things lean back by this much, degrees, so that the camera above the station sees their faces and not their edges.
     * With a camera that looks along +z (a station's {@code yaw} 0) a turn about +z tips the top to the screen's right; a station
     * whose camera looks along -z gives 180 and everything turns with it.
     */
    private static final float LEAN = 16.0F;
    /** An item visual at scale 1 is drawn this many pixels high: the item's own "fixed" display halves it. */
    private static final float ITEM_DRAWN = 8.0F;

    private KitchenMotions() {
    }

    /** One motion in progress. */
    abstract static class Motion {
        protected float age;
        /** How the station's camera is turned: 0 when it looks along +z, 180 along -z. */
        protected float yaw;

        /** Advances the motion; false once it is over and its nodes are hidden. */
        abstract boolean frame(StationView view, float dt);

        /** Stops it where it is and hides what it showed: the station shows something else now. */
        abstract void stop();
    }

    /** Where a container has its rim, as a part of its size from its middle, on the side it tips to. */
    private static final float LIP_X = -0.3F;
    private static final float LIP_Y = 0.36F;

    /**
     * Where point {@code (x, y)} of an upright item (parts of its size from the middle) is, tipped by {@code tilt}, leant back and
     * turned to face the camera by {@code yaw}.
     */
    static Vector3f tipped(float x, float y, float size, float tilt, float yaw) {
        Vector3f point = new Vector3f(x * size, y * size, 0.0F);
        point.rotateZ(tilt * Mth.DEG_TO_RAD);
        point.rotateX(LEAN * Mth.DEG_TO_RAD);
        point.rotateY(yaw * Mth.DEG_TO_RAD);
        return point;
    }

    /** Shows {@code item} upright on {@code node}, {@code size} pixels high. */
    static void showItem(NodeView node, Item item, float size, int tint) {
        node.setShape(null, null);
        node.setVisual(Visual.ofItem(item).withAnchor(ITEM_MIDDLE));
        node.setTint(tint);
        node.scaleTo(size / ITEM_DRAWN, 0.0F);
        node.setVisible(true);
    }

    static float smooth(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** Fast at first, settling at the end. */
    static float out(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return 1.0F - (1.0F - t) * (1.0F - t);
    }

    /** The container a liquid comes in, or nothing when it comes in none. */
    static Optional<Item> containerOf(ItemStack stack) {
        Item container = stack.getItem().getCraftingRemainingItem();
        return container == null || container == Items.AIR ? Optional.empty() : Optional.of(container);
    }

    /**
     * A liquid poured out of its container: it is brought from {@code from} over the place where the stream lands, cocked back a
     * little, tipped over, poured in a stream that thins as it runs out, set upright again empty and taken away.
     */
    static final class Pour extends Motion {
        private static final float ARRIVE = 0.3F;
        private static final float TIP = 0.2F;
        private static final float BUCKET_FLOW = 0.8F;
        private static final float BOTTLE_FLOW = 0.55F;
        private static final float BACK = 0.2F;
        private static final float AWAY = 0.3F;
        private static final float ARC = 2.0F;
        /** The hand cocks the container back before tipping it, and tips it further as it empties, degrees. */
        private static final float COCK = -12.0F;
        private static final float EMPTYING = 14.0F;
        /** How high over the landing the rim is while it pours, and how far out from the rim a full stream lands, pixels. */
        private static final float OVER = 3.2F;
        private static final float THROW = 1.3F;
        /** It comes from the hand this much smaller: it grows as it comes nearer, it does not jump out at the camera. */
        private static final float FROM_SCALE = 0.5F;

        private final NodeView node;
        private final String key;
        private final ItemStack full;
        private final Vector3f from;
        private final Vector3f middle;
        private final DoubleSupplier landY;
        private final String liquidNode;
        private final float size;
        private final float tilt;
        private final float side;
        private final float lipX;
        private final float flow;
        private final float thick;
        private final int color;
        private Runnable onFlow = () -> {
        };
        private boolean flowing;
        private boolean emptied;

        /**
         * @param node    the node that shows the container
         * @param key     tells this stream from others at the station
         * @param full    the liquid in its container
         * @param from    where the container comes from, scene pixels
         * @param onto    where the stream lands (x, z; y is {@code landY}), scene pixels
         * @param liquid  the node whose liquid it pours into, or null
         * @param size    how high it is drawn, pixels
         * @param side    1 tips it to the right of the screen, -1 to the left
         */
        Pour(NodeView node, String key, ItemStack full, Vector3f from, Vector3f onto, DoubleSupplier landY, String liquid, float size,
                float side, float yaw) {
            this.yaw = yaw;
            this.node = node;
            this.key = key;
            this.full = full.copy();
            this.from = new Vector3f(from);
            this.landY = landY;
            this.liquidNode = liquid;
            this.size = size;
            this.side = side;
            this.lipX = side * LIP_X;
            boolean bucket = isBucket(full);
            // A bucket goes over slowly and runs thick; a bottle tips nearly upside down and runs thin.
            this.tilt = side * (bucket ? 105.0F : 125.0F);
            this.flow = bucket ? BUCKET_FLOW : BOTTLE_FLOW;
            this.thick = bucket ? 1.0F : 0.45F;
            this.color = KitchenLooks.liquidColor(full);
            Vector3f lip = tipped(lipX, LIP_Y, size, tilt, yaw);
            Vector3f out = outward(THROW);
            this.middle = new Vector3f(onto.x - out.x - lip.x, (float) landY.getAsDouble() + OVER - lip.y, onto.z - out.z - lip.z);
            showItem(node, full.getItem(), size, 0xFFFFFFFF);
            node.scaleTo(size / ITEM_DRAWN * FROM_SCALE, 0.0F);
            place(this.from, 0.0F);
        }

        /**
         * Holds the container so high that its lip is at least at {@code y} as it pours, scene pixels: over a deep vessel's rim rather
         * than down by the liquid in it. The stream falls the rest of the way.
         */
        Pour lipAtLeast(float y) {
            Vector3f lip = tipped(lipX, LIP_Y, size, tilt, yaw);
            middle.y = Math.max(middle.y, y - lip.y);
            return this;
        }

        /** Runs once, when the liquid starts to come out. */
        Pour onFlow(Runnable run) {
            onFlow = run;
            return this;
        }

        /** Whether the liquid has started to run: what it pours into takes its colour from then on. */
        boolean hasFlowed() {
            return flowing;
        }

        /** Seconds from the start until the liquid is all out. */
        float pouredBy() {
            return ARRIVE + TIP + flow;
        }

        /** Seconds the liquid runs. */
        float flowTime() {
            return flow;
        }

        /** Seconds from the start until {@code full} is all poured out. */
        static float pouredBy(ItemStack full) {
            return ARRIVE + TIP + (isBucket(full) ? BUCKET_FLOW : BOTTLE_FLOW);
        }

        private static boolean isBucket(ItemStack full) {
            return containerOf(full).map(item -> item == Items.BUCKET).orElse(false);
        }

        @Override
        boolean frame(StationView view, float dt) {
            age += dt;
            float t = age;
            if (t < ARRIVE) {
                float k = smooth(t / ARRIVE);
                Vector3f at = new Vector3f(from).lerp(middle, k).add(0.0F, ARC * Mth.sin(k * Mth.PI), 0.0F);
                place(at, side * COCK * k);
                node.scaleTo(size / ITEM_DRAWN * Mth.lerp(k, FROM_SCALE, 1.0F), 0.0F);
                return true;
            }
            t -= ARRIVE;
            if (t < TIP) {
                place(middle, Mth.lerp(smooth(t / TIP), side * COCK, tilt));
                return true;
            }
            t -= TIP;
            if (t < flow) {
                if (!flowing) {
                    flowing = true;
                    view.playSound(containerOf(full).map(item -> item == Items.BUCKET).orElse(false) ? SoundEvents.BUCKET_EMPTY
                            : SoundEvents.BOTTLE_EMPTY, null, 0.45F, 1.0F);
                    onFlow.run();
                }
                float k = t / flow;
                // A hand is never quite still, and the container goes over further as it empties.
                float angle = tilt + side * EMPTYING * k + 2.0F * Mth.sin(age * 11.0F);
                place(middle, angle);
                float strength = thick * smooth(t / 0.08F) * (1.0F - 0.75F * smooth((k - 0.55F) / 0.45F));
                Vector3f lip = tipped(lipX, LIP_Y, size, angle, yaw).add(middle);
                // It leaves the rim with some speed and lands out from it, nearer as it runs thin.
                Vector3f out = outward(THROW * (0.4F + 0.6F * strength / thick));
                view.pourStream(key, lip, new Vector3f(lip.x + out.x, (float) landY.getAsDouble(), lip.z + out.z), color, strength, liquidNode);
                return true;
            }
            t -= flow;
            if (!emptied) {
                emptied = true;
                Vector3f lip = tipped(lipX, LIP_Y, size, tilt + side * EMPTYING, yaw).add(middle);
                view.spawnParticles(DiegesisParticles.DROPLET, lip, new Vector3f(0.0F, -6.0F, 0.0F), 0.05F, 2, color);
                // What goes away is the empty container.
                containerOf(full).ifPresent(container -> showItem(node, container, size, 0xFFFFFFFF));
            }
            if (t < BACK) {
                place(new Vector3f(middle).add(0.0F, smooth(t / BACK), 0.0F), Mth.lerp(smooth(t / BACK), tilt + side * EMPTYING, 0.0F));
                return true;
            }
            t -= BACK;
            if (t < AWAY) {
                float k = smooth(t / AWAY);
                Vector3f lifted = new Vector3f(middle).add(0.0F, 1.0F, 0.0F);
                Vector3f gone = new Vector3f(from).add(0.0F, 3.0F, 0.0F);
                place(lifted.lerp(gone, k), 0.0F);
                node.scaleTo(size / ITEM_DRAWN * (1.0F - k), 0.0F);
                return true;
            }
            stop();
            return false;
        }

        /** {@code amount} pixels the way the rim points, level. */
        private Vector3f outward(float amount) {
            return new Vector3f(-side * amount, 0.0F, 0.0F).rotateY(yaw * Mth.DEG_TO_RAD);
        }

        private void place(Vector3f at, float angle) {
            node.moveTo(at.x, at.y, at.z, 0.0F);
            node.rotateTo(LEAN, yaw, angle, 0.0F);
        }

        @Override
        void stop() {
            node.setVisible(false);
        }
    }

    /**
     * Something poured dry (sugar, cocoa, bone meal): brought over, turned over and shaken, its grains falling in a thin stream and
     * making small rings where they land, while it empties and is gone.
     */
    static final class Shake extends Motion {
        private static final float ARRIVE = 0.3F;
        private static final float TURN = 0.18F;
        private static final float SHAKE = 0.6F;
        private static final float GONE = 0.15F;
        private static final float TILT = 140.0F;
        private static final float OVER = 3.5F;
        private static final float GRAINS = 70.0F;
        private static final float RINGS = 0.1F;

        private final NodeView node;
        private final ItemStack stack;
        private final Vector3f from;
        private final Vector3f middle;
        private final DoubleSupplier landY;
        private final NodeView liquid;
        private final float size;
        private final float tilt;
        private final int color;
        private float grains;
        private float rings;
        private boolean started;

        /** {@code side}: 1 turns it over to the right of the screen, -1 to the left. */
        Shake(NodeView node, ItemStack stack, Vector3f from, Vector3f onto, DoubleSupplier landY, NodeView liquid, float size, float side,
                float yaw) {
            this.yaw = yaw;
            this.node = node;
            this.stack = stack.copy();
            this.from = new Vector3f(from);
            this.landY = landY;
            this.liquid = liquid;
            this.size = size;
            this.tilt = side * TILT;
            this.color = 0xFF000000 | FoodColors.main(stack);
            Vector3f mouth = tipped(0.0F, LIP_Y, size, tilt, yaw);
            this.middle = new Vector3f(onto.x - mouth.x, (float) landY.getAsDouble() + OVER - mouth.y, onto.z - mouth.z);
            showItem(node, stack.getItem(), size, 0xFFFFFFFF);
            place(this.from, 0.0F, 0.5F);
        }

        float doneBy() {
            return ARRIVE + TURN + SHAKE + GONE;
        }

        @Override
        boolean frame(StationView view, float dt) {
            age += dt;
            float t = age;
            if (t < ARRIVE) {
                float k = smooth(t / ARRIVE);
                place(new Vector3f(from).lerp(middle, k).add(0.0F, 2.0F * Mth.sin(k * Mth.PI), 0.0F), -10.0F * Math.signum(tilt) * k,
                        Mth.lerp(k, 0.5F, 1.0F));
                return true;
            }
            t -= ARRIVE;
            if (t < TURN) {
                place(middle, Mth.lerp(smooth(t / TURN), -10.0F * Math.signum(tilt), tilt), 1.0F);
                return true;
            }
            t -= TURN;
            if (t < SHAKE) {
                if (!started) {
                    started = true;
                    view.playSound(SoundEvents.BRUSH_SAND, null, 0.25F, 1.4F);
                }
                float k = t / SHAKE;
                // Shaken in short jerks from the wrist.
                float jerk = Mth.sin(t * 50.0F);
                place(new Vector3f(middle).add(0.15F * jerk, 0.25F * Math.abs(jerk), 0.0F), tilt + 9.0F * jerk, 1.0F - 0.45F * k);
                Vector3f mouth = tipped(0.0F, LIP_Y, size * (1.0F - 0.45F * k), tilt, yaw).add(middle);
                grains += dt * GRAINS * (1.0F - 0.6F * k);
                if (grains >= 1.0F) {
                    view.spawnParticles(HandsOnDelightClient.GRAIN, mouth, new Vector3f(0.0F, -3.0F, 0.0F), 0.35F, (int) grains, color);
                    grains -= (int) grains;
                }
                rings -= dt;
                if (rings <= 0.0F && liquid != null) {
                    rings = RINGS;
                    liquid.ripple(mouth.x + (float) (Math.random() - 0.5), mouth.z + (float) (Math.random() - 0.5), 0.05F);
                }
                return true;
            }
            t -= SHAKE;
            if (t < GONE) {
                float k = smooth(t / GONE);
                place(new Vector3f(middle).add(0.0F, k, 0.0F), Mth.lerp(k, tilt, tilt * 0.7F), 0.55F * (1.0F - k));
                return true;
            }
            stop();
            return false;
        }

        private void place(Vector3f at, float angle, float scale) {
            node.moveTo(at.x, at.y, at.z, 0.0F);
            node.rotateTo(LEAN, yaw, angle, 0.0F);
            node.scaleTo(size / ITEM_DRAWN * scale, 0.0F);
        }

        @Override
        void stop() {
            node.setVisible(false);
        }
    }

    /**
     * An egg: brought down onto a rim, knocked on it, crack and bits of shell; carried over the food, it opens, the shell is gone
     * and its raw inside drops, lands with a splat, then lies there or sinks in a soup.
     */
    static final class Crack extends Motion {
        private static final float ARRIVE = 0.3F;
        private static final float KNOCK_DOWN = 0.06F;
        private static final float KNOCK_UP = 0.1F;
        private static final float OVER_TIME = 0.14F;
        private static final float OPEN = 0.1F;
        private static final float FALL = 0.2F;
        private static final float SINK = 0.5F;
        private static final float KNOCK_DEPTH = 0.7F;

        private final NodeView egg;
        private final NodeView inside;
        private final Vector3f from;
        private final Vector3f rim;
        private final Vector3f over;
        private final DoubleSupplier landY;
        private final NodeView liquid;
        private final float size;
        private final float insideSize;
        private final boolean sinks;
        private Runnable onLanded = () -> {
        };
        private boolean knocked;
        private boolean opened;
        private boolean landed;
        private float insideTurn;

        /**
         * @param rim    where the egg's lowest point knocks, scene pixels
         * @param onto   where its inside lands (x, z; y is {@code landY})
         * @param sinks  the inside sinks into a soup after landing; else it stays until {@link #stop}
         */
        Crack(NodeView egg, NodeView inside, ItemStack stack, Vector3f from, Vector3f rim, Vector3f onto, DoubleSupplier landY, NodeView liquid,
                float size, float insideSize, boolean sinks, float yaw) {
            this.yaw = yaw;
            this.egg = egg;
            this.inside = inside;
            this.from = new Vector3f(from);
            this.size = size;
            this.insideSize = insideSize;
            this.rim = new Vector3f(rim).add(0.0F, size * 0.42F, 0.0F);
            this.over = new Vector3f(onto.x, (float) landY.getAsDouble() + 2.5F, onto.z);
            this.landY = landY;
            this.liquid = liquid;
            this.sinks = sinks;
            showItem(egg, stack.getItem(), size, 0xFFFFFFFF);
            egg.scaleTo(size / ITEM_DRAWN * 0.5F, 0.0F);
            placeEgg(this.from, 0.0F);
            inside.setVisible(false);
            ItemStack opened = KitchenLooks.opened(stack);
            ItemVoxels.icon(opened).ifPresent(icon -> {
                inside.setVisual(null);
                inside.setShape(ItemVoxels.build(icon, insideSize, java.util.List.of(), 0.0F), icon.sprite());
            });
            inside.setTint(KitchenLooks.RAW_EGG);
        }

        /** Runs once, when the inside lands. */
        Crack onLanded(Runnable run) {
            onLanded = run;
            return this;
        }

        /** Seconds from the start until the inside lands. */
        static float landsBy() {
            return ARRIVE + KNOCK_DOWN + KNOCK_UP + OVER_TIME + OPEN + FALL;
        }

        /** The inside lies turned by {@code degrees} about the upright, as the station lays it. */
        Crack insideTurn(float degrees) {
            insideTurn = degrees;
            return this;
        }

        @Override
        boolean frame(StationView view, float dt) {
            age += dt;
            float t = age;
            // It comes to rest just over the rim, comes down on it hard and bounces off.
            Vector3f above = new Vector3f(rim).add(0.0F, 0.6F, 0.0F);
            if (t < ARRIVE) {
                float k = smooth(t / ARRIVE);
                placeEgg(new Vector3f(from).lerp(above, k).add(0.0F, 1.5F * Mth.sin(k * Mth.PI), 0.0F), -20.0F * k);
                egg.scaleTo(size / ITEM_DRAWN * Mth.lerp(k, 0.5F, 1.0F), 0.0F);
                return true;
            }
            t -= ARRIVE;
            if (t < KNOCK_DOWN) {
                float k = t / KNOCK_DOWN;
                placeEgg(new Vector3f(above).sub(0.0F, (0.6F + KNOCK_DEPTH * 0.3F) * k * k, 0.0F), -20.0F);
                return true;
            }
            t -= KNOCK_DOWN;
            if (!knocked) {
                knocked = true;
                KitchenLooks.crack(view, null, new Vector3f(rim).sub(0.0F, size * 0.42F, 0.0F));
                egg.shake(0.15F, 0.12F);
            }
            if (t < KNOCK_UP) {
                placeEgg(new Vector3f(rim).add(0.0F, out(t / KNOCK_UP) * 1.2F - KNOCK_DEPTH * 0.3F, 0.0F), -20.0F);
                return true;
            }
            t -= KNOCK_UP;
            if (t < OVER_TIME) {
                Vector3f start = new Vector3f(rim).add(0.0F, 1.2F - KNOCK_DEPTH * 0.3F, 0.0F);
                placeEgg(start.lerp(over, smooth(t / OVER_TIME)), Mth.lerp(smooth(t / OVER_TIME), -20.0F, 0.0F));
                return true;
            }
            t -= OVER_TIME;
            if (!opened) {
                opened = true;
                // Two halves pulled apart: the shell is gone to the sides, and what was inside starts to fall.
                view.spawnParticles(DiegesisParticles.DUST, over, new Vector3f(0.0F, 1.0F, 0.0F), 0.8F, 4, 0xFFEAD9B8);
                inside.moveTo(over.x, over.y, over.z, 0.0F);
                inside.rotateTo(0.0F, insideTurn, 0.0F, 0.0F);
                inside.scaleTo(0.5F, 0.0F);
                inside.setVisible(true);
            }
            if (t < OPEN) {
                egg.scaleTo(size / ITEM_DRAWN * (1.0F - t / OPEN), 0.0F);
                float k = t / OPEN;
                inside.moveTo(over.x, over.y - 0.3F * k, over.z, 0.0F);
                inside.scaleTo(0.5F + 0.2F * k, 0.0F);
                return true;
            }
            egg.setVisible(false);
            t -= OPEN;
            float surface = (float) landY.getAsDouble();
            if (t < FALL) {
                float k = t / FALL;
                // It falls as things fall, faster and faster, and flattens as it goes.
                inside.moveTo(over.x, Mth.lerp(k * k, over.y - 0.3F, surface), over.z, 0.0F);
                inside.scaleTo(0.7F + 0.3F * k, 0.0F);
                return true;
            }
            t -= FALL;
            if (!landed) {
                landed = true;
                inside.moveTo(over.x, surface, over.z, 0.0F);
                inside.scaleTo(1.0F, 0.0F);
                inside.shake(0.12F, 0.15F);
                if (liquid != null) {
                    liquid.ripple(over.x, over.z, 0.35F);
                    view.spawnParticles(DiegesisParticles.SPLASH, new Vector3f(over.x, surface, over.z), new Vector3f(0.0F, 6.0F, 0.0F), 0.5F, 3,
                            0xFFF4F0E4);
                }
                onLanded.run();
            }
            if (!sinks) {
                // It lies where the station shows it lying from now on.
                stop();
                return false;
            }
            if (t < SINK) {
                float k = smooth(t / SINK);
                inside.moveTo(over.x, surface - 1.2F * k, over.z, 0.0F);
                inside.scaleTo(1.0F - k, 0.0F);
                return true;
            }
            stop();
            return false;
        }

        private void placeEgg(Vector3f at, float angle) {
            egg.moveTo(at.x, at.y, at.z, 0.0F);
            egg.rotateTo(LEAN, yaw, angle, 0.0F);
        }

        @Override
        void stop() {
            egg.setVisible(false);
            inside.setVisible(false);
        }
    }

    /**
     * A container dipped into a soup where it hangs: down under the surface with a ring and a splash, up full with drops running
     * off it, and away to the cook.
     */
    static final class Dip extends Motion {
        private static final float DOWN = 0.14F;
        private static final float UP = 0.26F;
        private static final float HOLD = 0.08F;
        private static final float AWAY = 0.3F;
        private static final float DRIPS = 14.0F;

        private final NodeView node;
        private final Item filled;
        private final Vector3f at;
        private final Vector3f origin;
        private final Vector3f away;
        private final DoubleSupplier surface;
        private final NodeView liquid;
        private final float scale;
        private final float half;
        private final int drops;
        private final Runnable onDipped;
        private boolean dipped;
        private float drip;

        /**
         * @param node     the node that hangs over the soup now, at {@code at} (scene pixels); its moves are from {@code origin}, its
         *                 own place in the scene, and {@code scale} is what it is scaled by now; {@code size} its height in pixels
         * @param filled   what comes up
         * @param away     where it goes, scene pixels
         * @param drops    the colour of what runs off it
         * @param onDipped runs once, when it is under
         */
        Dip(NodeView node, Item filled, Vector3f at, Vector3f origin, Vector3f away, DoubleSupplier surface, NodeView liquid, float scale,
                float size, int drops, Runnable onDipped) {
            this.node = node;
            this.filled = filled;
            this.at = new Vector3f(at);
            this.origin = new Vector3f(origin);
            this.away = new Vector3f(away);
            this.surface = surface;
            this.liquid = liquid;
            this.scale = scale;
            this.half = size * 0.3F;
            this.drops = drops;
            this.onDipped = onDipped;
        }

        @Override
        boolean frame(StationView view, float dt) {
            age += dt;
            float t = age;
            float level = (float) surface.getAsDouble();
            float under = level - 0.4F;
            if (t < DOWN) {
                float k = t / DOWN;
                move(at.x, Mth.lerp(k * k, at.y, under), at.z);
                node.rotateTo(25.0F * k, 0.0F, 0.0F, 0.0F);
                return true;
            }
            t -= DOWN;
            if (!dipped) {
                dipped = true;
                if (liquid != null) {
                    liquid.ripple(at.x, at.z, 0.45F);
                }
                view.spawnParticles(DiegesisParticles.SPLASH, new Vector3f(at.x, level, at.z), new Vector3f(0.0F, 6.0F, 0.0F), 0.6F, 4, drops);
                node.setVisual(Visual.ofItem(filled).withAnchor(ITEM_MIDDLE));
                node.setTint(0xFFFFFFFF);
                onDipped.run();
            }
            float top = level + 3.0F;
            if (t < UP + HOLD) {
                float k = out(Math.min(1.0F, t / UP));
                move(at.x, Mth.lerp(k, under, top), at.z);
                node.rotateTo(25.0F * (1.0F - k), 0.0F, 0.0F, 0.0F);
                dripping(view, dt, new Vector3f(at.x, Mth.lerp(k, under, top) - half, at.z));
                return true;
            }
            t -= UP + HOLD;
            if (t < AWAY) {
                float k = smooth(t / AWAY);
                Vector3f where = new Vector3f(at.x, top, at.z).lerp(away, k);
                move(where.x, where.y, where.z);
                node.scaleTo(scale * (1.0F - k), 0.0F);
                if (k < 0.4F) {
                    dripping(view, dt, new Vector3f(where).sub(0.0F, half, 0.0F));
                }
                return true;
            }
            stop();
            return false;
        }

        private void move(float x, float y, float z) {
            node.moveTo(x - origin.x, y - origin.y, z - origin.z, 0.0F);
        }

        private void dripping(StationView view, float dt, Vector3f from) {
            drip += dt * DRIPS;
            if (drip >= 1.0F) {
                view.spawnParticles(DiegesisParticles.DROPLET, from, new Vector3f(0.0F, -2.0F, 0.0F), 0.6F, (int) drip, drops);
                drip -= (int) drip;
            }
        }

        @Override
        void stop() {
            node.setVisible(false);
            node.rotateTo(0.0F, 0.0F, 0.0F, 0.0F);
            node.scaleTo(scale, 0.0F);
        }
    }
}
