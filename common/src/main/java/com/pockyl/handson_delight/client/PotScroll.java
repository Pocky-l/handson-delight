package com.pockyl.handson_delight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
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
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.handson_delight.station.CookingPotStation;
import com.pockyl.handson_delight.station.ScrollPot;

/**
 * The alchemist pot's spell scroll as the scene shows it: it hangs open over the back rim, swaying and glowing in its school's colour,
 * and its school shows on the pot: fire licks the base, ice frosts the rim, lightning sparks over the liquid, holy light rises from it,
 * gluttony boils it harder. A dish that waits for a school blinks a scroll at the badge. A scroll on the cursor clicked anywhere on the
 * pot hangs; one in the hand that opened the pot hangs at once.
 */
final class PotScroll {
    /** Seconds the scroll takes to rise out of the pot and open, and how far below its place it starts, pixels. */
    private static final float HANG_TIME = 0.45F;
    private static final float HANG_FROM = 4.0F;
    /** How far it sways (degrees) and bobs (pixels), and how fast. */
    private static final float SWAY = 12.0F;
    private static final float BOB = 0.25F;
    /** Its glow: glints a second, and a pale ring of them when it hangs. */
    private static final float GLOW_RATE = 1.6F;
    /**
     * The rim's height and how far its middle lies from the pot's middle; where a fire scroll's flames come out from under the pot:
     * between the base (5.5 px out) and the body's edge (6 px out), low, rising slowly enough to stay under the body's bottom.
     */
    private static final float RIM = 9.0F;
    private static final float RIM_HALF = 4.6F;
    private static final float FLAME_HALF = 5.6F;
    private static final float FLAME_SPREAD = 0.7F;
    private static final float FLAME_FROM = 0.2F;
    private static final Vector3f FLAME_UP = new Vector3f(0.0F, 1.6F, 0.0F);
    private static final int FROST = 0xFFDFF6FF;
    private static final int SPARK = 0xFFB4ECFF;
    private static final int HOLY = 0xFFFFE07A;
    private static final Vector3f SLOW_UP = new Vector3f(0.0F, 1.2F, 0.0F);
    private static final Vector3f STILL = new Vector3f();
    /** How much harder a gluttony scroll makes the pot boil. */
    private static final float GLUTTONY_BOIL = 1.7F;

    private ItemStack shown = ItemStack.EMPTY;
    private ResourceLocation school;
    private int color = 0xFFFFFFFF;
    private float hangAge = Float.NaN;
    private float time;
    private float glow;
    private float effect;
    private float sparkWait;
    private Item badgeItem;
    /** The school {@link #badgeItem} was picked for: another dish waits for another school's scroll. */
    private ResourceLocation badgeSchool;

    /** What hangs there when the scene is first seen hangs already. */
    void ready(StationView view) {
        // The badge is drawn over everything for the cook: the scroll blinking on it too.
        view.node("badge_scroll").ifPresent(node -> node.setOnTop(true));
        shown = view.state().getItem(CookingPotStation.SCROLL_SLOT).copy();
        read(view);
        hangAge = Float.NaN;
        // A rebuilt scene has a fresh badge node without the scroll's visual.
        badgeItem = null;
        badgeSchool = null;
    }

    private void read(StationView view) {
        ScrollPot pot = ScrollPot.of(view.level().getBlockEntity(view.pos()));
        school = pot == null || shown.isEmpty() ? null : pot.handson$schoolOf(shown);
        color = 0xFF000000 | (pot == null || school == null ? 0xFFFFFF : pot.handson$colorOf(school));
    }

    /** The slot changed: a new scroll rises and opens, with a rustle and a ring of its colour. */
    void refresh(StationView view, boolean quiet) {
        ItemStack now = view.state().getItem(CookingPotStation.SCROLL_SLOT);
        if (ItemStack.matches(now, shown)) {
            return;
        }
        shown = now.copy();
        read(view);
        if (!shown.isEmpty() && !quiet) {
            hangAge = 0.0F;
            view.playSound(SoundEvents.BOOK_PAGE_TURN, "scroll", 0.8F, 0.9F);
            view.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, "scroll", 0.25F, 1.4F);
        }
    }

    /** Whether the pot boils harder than a fire makes it: a gluttony scroll. */
    float boil() {
        return ScrollPot.GLUTTONY.equals(school) ? GLUTTONY_BOIL : 1.0F;
    }

    /** A scroll on the cursor clicked on the pot hangs over it; true when it was one. */
    boolean click(StationView view, ItemStack stack) {
        ScrollPot pot = ScrollPot.of(view.level().getBlockEntity(view.pos()));
        if (pot == null || !pot.handson$isScroll(stack)) {
            return false;
        }
        view.sendAction(CookingPotStation.ACTION_HANG, new CompoundTag());
        return true;
    }

    /** The scroll in the hand that opened the pot is meant for it. */
    void opened(StationView view) {
        LocalPlayer player = Minecraft.getInstance().player;
        ScrollPot pot = ScrollPot.of(view.level().getBlockEntity(view.pos()));
        if (player != null && pot != null && view.openedByUse() && player.inventoryMenu.getCarried().isEmpty()
                && pot.handson$isScroll(player.getMainHandItem())) {
            CompoundTag how = new CompoundTag();
            how.putBoolean(CookingPotStation.FROM_HAND, true);
            view.sendAction(CookingPotStation.ACTION_HANG, how);
        }
    }

    /** The scroll a dish waits for blinks at the badge's corner while the badge shows. */
    void badge(StationView view, boolean shown, ResourceLocation wanted) {
        view.node("badge_scroll").ifPresent(node -> {
            boolean on = shown && wanted != null && Mth.sin(time * 6.0F) > 0.0F;
            node.setVisible(on);
            ScrollPot pot = ScrollPot.of(view.level().getBlockEntity(view.pos()));
            if (on && pot != null && (badgeItem == null || !wanted.equals(badgeSchool))) {
                badgeSchool = wanted;
                badgeItem = null;
                pot.handson$scrollsOf(wanted).stream().findFirst().ifPresent(scroll -> {
                    badgeItem = scroll.getItem();
                    node.setVisual(Visual.ofItem(badgeItem).withAnchor(new Vector3f(8.0F, 8.0F, 8.0F)));
                });
            }
        });
    }

    /** Every frame: the scroll sways and glows, and its school shows on the pot. {@code surface}: the liquid's height, pixels. */
    void frame(StationView view, float dt, boolean heated, boolean cooking, float surface) {
        time += dt;
        NodeView node = view.node("scroll").orElse(null);
        if (node == null) {
            return;
        }
        float rise = 0.0F;
        float open = 1.0F;
        if (!Float.isNaN(hangAge)) {
            hangAge += dt;
            float t = Math.min(1.0F, hangAge / HANG_TIME);
            float eased = t * t * (3.0F - 2.0F * t);
            rise = (1.0F - eased) * -HANG_FROM;
            open = Mth.lerp(eased, 0.2F, 1.0F);
            if (t >= 1.0F) {
                hangAge = Float.NaN;
                ring(view, node);
            }
        }
        node.moveTo(0.0F, rise + Mth.sin(time * 1.3F) * BOB, 0.0F, 0.0F);
        node.rotateTo(0.0F, Mth.sin(time * 0.7F) * SWAY, 0.0F, 0.0F);
        node.scaleTo(open, 0.0F);
        if (shown.isEmpty() || school == null) {
            return;
        }
        RandomSource random = view.level().getRandom();
        glow += dt * GLOW_RATE;
        if (glow >= 1.0F) {
            glow -= 1.0F;
            view.spawnParticles(DiegesisParticles.GLINT, node.scenePosition(), SLOW_UP, 1.6F, 1, color);
        }
        school(view, dt, heated, cooking, surface, random);
    }

    /** A pale ring of the school's colour round the scroll once it is open. */
    private void ring(StationView view, NodeView node) {
        view.spawnParticles(DiegesisParticles.GLINT, node.scenePosition(), SLOW_UP, 2.2F, 6, color);
    }

    private void school(StationView view, float dt, boolean heated, boolean cooking, float surface, RandomSource random) {
        if (ScrollPot.FIRE.equals(school)) {
            // Its fire burns under the pot as a campfire would: flames come out from under the bottom all round the body's edge and
            // lick up no higher than the base.
            effect += dt * (cooking ? 80.0F : 55.0F);
            while (effect >= 1.0F) {
                effect -= 1.0F;
                Vector3f at = edge(random, FLAME_HALF + random.nextFloat() * FLAME_SPREAD, FLAME_FROM + random.nextFloat() * 0.4F);
                view.spawnParticles(HandsOnDelightClient.SCROLL_FLAME, at, FLAME_UP, 0.15F, 1, 0);
            }
        } else if (ScrollPot.ICE.equals(school)) {
            // Frost settles on the rim: flakes drift off it and glitter there.
            effect += dt * 4.0F;
            while (effect >= 1.0F) {
                effect -= 1.0F;
                Vector3f at = edge(random, RIM_HALF, RIM + 0.15F);
                if (random.nextBoolean()) {
                    world(view, ParticleTypes.SNOWFLAKE, at, 0.0);
                } else {
                    view.spawnParticles(DiegesisParticles.GLINT, at, STILL, 0.1F, 2, FROST);
                }
            }
        } else if (ScrollPot.LIGHTNING.equals(school)) {
            // Now and then a crackle of sparks jumps over the liquid.
            sparkWait -= dt;
            if (sparkWait <= 0.0F) {
                sparkWait = 0.5F + random.nextFloat() * 1.1F;
                Vector3f at = new Vector3f(8.0F + (random.nextFloat() - 0.5F) * 6.0F, surface + 0.3F, 8.0F + (random.nextFloat() - 0.5F) * 6.0F);
                view.spawnParticles(DiegesisParticles.SPARK, at, new Vector3f(0.0F, 3.0F, 0.0F), 0.8F, 3 + random.nextInt(3), SPARK);
                view.playSound(SoundEvents.REDSTONE_TORCH_BURNOUT, "broth", 0.08F, 1.8F + random.nextFloat() * 0.3F);
            }
        } else if (ScrollPot.HOLY.equals(school)) {
            // Golden motes rise from the liquid.
            effect += dt * 3.0F;
            while (effect >= 1.0F) {
                effect -= 1.0F;
                Vector3f at = new Vector3f(8.0F + (random.nextFloat() - 0.5F) * 6.5F, surface + 0.2F, 8.0F + (random.nextFloat() - 0.5F) * 6.5F);
                view.spawnParticles(DiegesisParticles.GLINT, at, new Vector3f(0.0F, 2.0F, 0.0F), 0.2F, 1, HOLY);
            }
        } else if (!ScrollPot.GLUTTONY.equals(school) && heated) {
            // Any other school tints the steam over a hot pot with its colour.
            effect += dt * 1.5F;
            while (effect >= 1.0F) {
                effect -= 1.0F;
                Vector3f at = new Vector3f(8.0F + (random.nextFloat() - 0.5F) * 6.0F, surface + 0.3F, 8.0F + (random.nextFloat() - 0.5F) * 6.0F);
                view.spawnParticles(DiegesisParticles.GLINT, at, SLOW_UP, 0.3F, 1, color);
            }
        }
    }

    /**
     * One of the game's own particles at {@code at}, scene pixels round the pot's middle: those are drawn by the world, for everybody,
     * at the game's size. The scene faces the player, the ring round the pot is the same from every side.
     */
    private static void world(StationView view, net.minecraft.core.particles.ParticleOptions particle, Vector3f at, double rise) {
        view.level().addParticle(particle, view.pos().getX() + at.x / 16.0, view.pos().getY() + at.y / 16.0, view.pos().getZ() + at.z / 16.0,
                0.0, rise, 0.0);
    }

    /** A point on a square ring {@code half} pixels from the pot's middle, at {@code y}. */
    private static Vector3f edge(RandomSource random, float half, float y) {
        float along = (random.nextFloat() * 2.0F - 1.0F) * half;
        float side = random.nextBoolean() ? half : -half;
        return random.nextBoolean() ? new Vector3f(8.0F + along, y, 8.0F + side) : new Vector3f(8.0F + side, y, 8.0F + along);
    }
}
