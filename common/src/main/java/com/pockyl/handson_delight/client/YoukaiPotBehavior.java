package com.pockyl.handson_delight.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.mixin.SpriteContentsAccessor;
import com.pockyl.handson_delight.station.KitchenYoukaiPot;
import com.pockyl.handson_delight.station.YoukaiPotStation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A Youkai's Feasts pot on the client. The pot's own renderer draws the soup and what floats in it; the station shows how things get
 * there: food comes down from the cook into the soup one piece after another with a splash (it floats once it is in), soy sauce
 * and cream are poured, the cook stirs the stockpot with its ladle as the cooking pot's spoon and what floats goes round, what comes
 * back out lifts from the soup to the cook. The badge over the
 * pot shows what cooks, whether it is heated and the time left; done, a chime and steam, and the dish stands where the pot stood (one
 * that is no block comes up to the cook).
 */
public final class YoukaiPotBehavior implements StationBehavior {
    /** The pots: where their soup's surface is, and how far from the middle food lands in it, pixels. */
    public enum Size {
        SMALL(3.0F, 2.0F),
        SHORT(5.0F, 3.0F),
        STOCKPOT(14.0F, 3.5F);

        final float soup;
        final float spread;

        Size(float soup, float spread) {
            this.soup = soup;
            this.spread = spread;
        }
    }

    private static final int FLIERS = 4;
    private static final String[] BADGE_PARTS = {"badge", "dial_track", "dial", "flame", "flame_lit", "preview"};
    @SuppressWarnings("removal")
    private static final ResourceLocation BADGE_TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "block/white_concrete");
    /** What comes in flies this long from the cook, the next one starting this much later; a pour holds the next back. */
    private static final float FLY_TIME = 0.32F;
    private static final float NEXT = 0.16F;
    /** As big as what floats (its own 0.375 of a block, drawn at two thirds of that). */
    private static final float FLY_SIZE = 3.4F;
    private static final float FLY_FROM = 7.0F;
    private static final float POUR_SIZE = 4.6F;
    private static final float POUR_OVER_RIM = 2.5F;
    private static final float AWAY = 0.35F;
    /**
     * The stockpot's ladle stirs as the cooking pot's spoon does (same pace, lean, homing, swirl, trail and sound; {@link PotBehavior}):
     * taken by its handle (held, or a click takes it in hand until the next), its end follows the hand in the soup and the handle
     * stands up; let go, it stays a while, then eases back to its rest, where the block has it. The soup and what floats go round
     * after it and calm down. Everybody near sees it through the station's controls.
     */
    private static final float SPOON_CLICK = 0.3F;
    /** The end of the ladle's bowl at rest, off the middle as its model has it, how high, and how far the hand takes it. */
    private static final float REST_X = -1.25F;
    private static final float SPOON_END = 2.75F;
    private static final float SPOON_REACH = 3.0F;
    /** How the ladle leans at rest (its model's), and in the hand at the edge of its reach, degrees. */
    private static final float REST_LEAN = 22.5F;
    private static final float STIR_LEAN = 7.0F;
    private static final float SPOON_HOME_EASE = 0.6F;
    private static final float SPOON_HOME_RATE = 1.8F;
    private static final float SPOON_HOME_GRIP = 2.0F;
    private static final float HAND_MEMORY = 2.5F;
    private static final float SEND_INTERVAL = 0.12F;
    private static final float SEND_TRAVEL = 0.2F;
    private static final float RIPPLE_INTERVAL = 0.09F;
    private static final float RIPPLE_STRENGTH = 0.17F;
    private static final float SWIRL_HALF_LIFE = 0.9F;
    private static final float SWIRL_SPEED = 1.5F;
    private static final float CALM_DRIFT = 0.05F;
    private static final float STIR_VOLUME = 0.7F;
    /** What floats in the pot is drawn by its own renderer: its ring turns with our soup, the other way round in its terms. */
    private static final float FLOATERS_TURN = -1.0F;
    private static final int SPLASH_COLOR = 0xC0B8D4E8;
    /** The badge, as the kettle's: plate, a flame over the icon of what cooks, the bar and the seconds left under it. */
    private static final int PLATE_COLOR = 0x8C1E140C;
    private static final int DIAL_COLOR = 0xFFFFCB6B;
    private static final int TRACK_COLOR = 0x50FFF2DC;
    private static final int FLAME_COLOR = 0xFFFF9A3C;
    private static final int FLAME_TOP = -15;
    private static final int TEXT_TOP = 13;
    private static final int BAR_LEFT = -7;
    private static final int BAR_TOP = 10;
    private static final int BAR_LENGTH = 14;
    private static final String[] COLON = {".", "#", ".", "#", "."};
    private static final float DONE_SWELL = 0.35F;
    private static final float DONE_TIME = 0.6F;
    private static final Vector3f ICON_MIDDLE = new Vector3f(8.0F, 8.0F, 8.0F);
    /**
     * The soup is the scene's own liquid, shown by code, as in the cooking pot (the pot's own soup textures flicker from frame to frame).
     * Water is the biome's, half darkened in iron, as in the kettle; a soup base (soy sauce, miso, cream, the soul soup) the colour
     * of its own texture. As the pot cooks one into the next, the colour moves across. It lies calm; heated it barely stirs, cooking
     * it bubbles a little at the walls.
     */
    public static final ResourceLocation SOUP_LIQUID = HandsOnDelight.id("youkai_soup");
    private static final String WATER = "water";
    private static final int WATER_ALPHA = 0x96;
    private static final int WATER_IN_IRON = 0x1E1E24;
    private static final int SOUP_ALPHA = 0xD8;
    private static final float WATER_MOTTLE = 0.1F;
    private static final float SOUP_MOTTLE = 0.2F;
    private static final Map<ResourceLocation, Integer> SOUP_COLOURS = new HashMap<>();
    /** The model the game falls back on when the soup colours were read: a new one after every resource reload. */
    private static BakedModel soupColoursReadWith;

    private final Size size;
    private final RecipeBook book;
    private final UsableCache usable = new UsableCache();
    private final List<Step> steps = new ArrayList<>();
    private KitchenMotions.Motion hand;
    private float clock;
    private float busyUntil;
    private int flier;
    private List<ItemStack> seen = List.of();
    private boolean stroking;
    private boolean heldByClick;
    private float strokeStart;
    private float tipX = REST_X;
    private float tipZ;
    private float grip;
    private float homing;
    private float swirl;
    private float swirlSign = 1.0F;
    private float turned;
    private float handLeft;
    private float sendClock;
    private float sendTravel;
    private float rippleClock;
    private final Quaternionf lean = new Quaternionf();
    private final Vector3f restAxis = new Vector3f();
    private final Vector3f leanAxis = new Vector3f();
    private final Vector3f poseAngles = new Vector3f();
    private final Vector3f spoonThrough = new Vector3f();
    private final Matrix4f potInverse = new Matrix4f();
    private float shownTurn = Float.NaN;
    private String drawnDial = "";
    private Item shownDish;
    private float doneAt = -10.0F;

    private record Step(float at, Runnable run) {
    }

    public YoukaiPotBehavior(StationView view, Size size) {
        this.size = size;
        this.book = new RecipeBook(new YoukaiPotBook(view.type().id()));
    }

    static KitchenYoukaiPot pot(StationView view) {
        return KitchenYoukaiPot.of(view.level().getBlockEntity(view.pos()));
    }

    @Override
    public void onSceneReady(StationView view) {
        steps.clear();
        busyUntil = 0.0F;
        stroking = false;
        heldByClick = false;
        shownTurn = Float.NaN;
        drawnDial = "";
        shownDish = null;
        if (hand != null) {
            hand.stop();
            hand = null;
        }
        view.node("book_open").ifPresent(node -> node.setPainter(book));
        view.node("dial").ifPresent(dial -> dial.setTint(DIAL_COLOR));
        view.node("dial_track").ifPresent(track -> track.setTint(TRACK_COLOR));
        view.node("flame").ifPresent(flame -> flame.setTint(TRACK_COLOR));
        view.node("flame_lit").ifPresent(flame -> flame.setTint(FLAME_COLOR));
        view.node("badge").ifPresent(plate -> {
            plate.setTint(PLATE_COLOR);
            MeshShape.Builder shape = MeshShape.builder();
            PotBehavior.pixels(shape, -10, -17, 20, 36);
            PotBehavior.pixels(shape, -9, -18, 18, 1);
            PotBehavior.pixels(shape, -9, 19, 18, 1);
            plate.setShape(shape.build(), BADGE_TEXTURE, true);
        });
        MeshShape.Builder flame = MeshShape.builder();
        glyph(flame, PotBehavior.FLAME, -2, FLAME_TOP);
        MeshShape flameShape = flame.build();
        view.node("flame").ifPresent(node -> node.setShape(flameShape, BADGE_TEXTURE, true));
        view.node("flame_lit").ifPresent(node -> node.setShape(flameShape, BADGE_TEXTURE, true));
        for (String part : BADGE_PARTS) {
            view.node(part).ifPresent(node -> node.setOnTop(true));
        }
        turnToBlock(view);
        view.node("broth").ifPresent(node -> node.showLiquid(SOUP_LIQUID, 1.0F));
    }

    @Override
    public void onOpen(StationView view) {
        usable.clear();
        book.opened(view);
        // What the pot takes now is lit in the drawer, the rest dim: its own hint of what may go in next. An empty pot takes water.
        view.setInventoryFilter(stack -> {
            KitchenYoukaiPot now = pot(view);
            return now == null ? YoukaiPotStation.isWater(stack) : usable.test(stack, now::handson$takes);
        });
        KitchenYoukaiPot pot = pot(view);
        if (pot == null) {
            return;
        }
        // What the cook came with is meant for the pot: one of it goes in, as the pot's own click puts one in.
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && view.openedByUse() && player.inventoryMenu.getCarried().isEmpty()
                && pot.handson$takes(player.getMainHandItem())) {
            CompoundTag how = new CompoundTag();
            how.putInt(YoukaiPotStation.FROM, player.getInventory().selected);
            view.sendAction(YoukaiPotStation.ACTION_PUT, how);
        }
    }

    @Override
    public void onClose(StationView view) {
        book.shut(view);
        YoukaiPotFlights.clear(view.pos());
    }

    @Override
    public void tick(StationView view) {
        KitchenYoukaiPot pot = pot(view);
        if (pot == null) {
            return;
        }
        List<ItemStack> in = pot.handson$contents();
        if (!sameStacks(in, seen)) {
            // Whatever went in or came out, what the pot takes next has changed.
            seen = in;
            usable.clear();
        }
        turnToBlock(view);
    }

    @Override
    public void frame(StationView view, float dt) {
        clock += dt;
        if (!steps.isEmpty()) {
            List<Step> due = steps.stream().filter(step -> step.at() <= clock).toList();
            steps.removeAll(due);
            due.forEach(step -> step.run().run());
        }
        if (hand != null && !hand.frame(view, dt)) {
            hand = null;
        }
        // The scene's copy of the stockpot stands in only while the block is hidden; passers-by see the block itself.
        boolean copy = view.isBlockHidden();
        view.node("body").ifPresent(node -> node.setVisible(copy));
        // The ladle is taken only once there is something to stir.
        boolean filled = pot(view) != null;
        view.node("ladle").ifPresent(node -> {
            node.setVisible(copy);
            node.setEnabled(filled);
        });
        if (!filled) {
            stroking = false;
            heldByClick = false;
        }
        moveSpoon(view, dt);
        soup(view);
        book.frame(view, dt);
        float appear = RecipeBook.appear(view);
        badge(view, view.isInUse() && RecipeBook.isWorkedAt(view) && appear > RecipeBook.SPECK);
        float swell = swell();
        for (String part : BADGE_PARTS) {
            view.node(part).ifPresent(node -> node.scaleTo(appear * swell, 0.0F));
        }
    }

    private void after(float seconds, Runnable run) {
        steps.add(new Step(clock + seconds, run));
    }

    private float start() {
        return Math.max(0.0F, busyUntil - clock);
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Stroke stroke && "ladle".equals(stroke.node())) {
            stroke(view, stroke.phase());
            return;
        }
        if (!(event instanceof SceneEvent.Click click)) {
            return;
        }
        if ("book_open".equals(click.node())) {
            book.click(view, click.button());
            return;
        }
        // A right click over the soup takes the last thing back out.
        if (click.button() == 1 && ("soup".equals(click.node()) || "mouth".equals(click.node()))) {
            view.sendAction(YoukaiPotStation.ACTION_BACK, new CompoundTag());
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

    /** The wheel over the recipe book goes through its ingredients or its list. */
    @Override
    public boolean onScroll(StationView view, String node, double amount) {
        return "book_open".equals(node) && book.scroll(amount);
    }

    /** A stack let go over the pot: one of it goes in, with shift as many as the pot takes. */
    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if ("book_open".equals(node)) {
            book.click(view, button);
            return true;
        }
        if (pot(view) == null && !YoukaiPotStation.isWater(stack)) {
            return false;
        }
        CompoundTag how = new CompoundTag();
        how.putBoolean(YoukaiPotStation.ALL, shift);
        view.sendAction(YoukaiPotStation.ACTION_PUT, how);
        return true;
    }

    /** Shift + click in the inventory: as many of that stack go in as the pot takes. */
    @Override
    public boolean onInventoryShiftClick(StationView view, int slot, ItemStack stack) {
        KitchenYoukaiPot pot = pot(view);
        boolean water = pot == null && YoukaiPotStation.isWater(stack);
        if (!water && (stack.isEmpty() || pot == null || !pot.handson$takes(stack) && !YoukaiPotStation.isIngredient(pot, stack))) {
            return false;
        }
        CompoundTag how = new CompoundTag();
        how.putInt(YoukaiPotStation.FROM, slot);
        how.putBoolean(YoukaiPotStation.ALL, true);
        view.sendAction(YoukaiPotStation.ACTION_PUT, how);
        return true;
    }

    @Override
    public void onEffect(StationView view, ResourceLocation effect, String node, CompoundTag data) {
        if (YoukaiPotStation.EFFECT_IN.equals(effect)) {
            in(view, data.getList(YoukaiPotStation.STACKS, Tag.TAG_COMPOUND));
        } else if (YoukaiPotStation.EFFECT_BACK.equals(effect)) {
            back(view, ItemStack.parseOptional(view.level().registryAccess(), data.getCompound(YoukaiPotStation.STACK)));
        } else if (YoukaiPotStation.EFFECT_REFUSED.equals(effect)) {
            refused(view, ItemStack.parseOptional(view.level().registryAccess(), data.getCompound(YoukaiPotStation.STACK)));
        } else if (YoukaiPotStation.EFFECT_DONE.equals(effect)) {
            done(view, ItemStack.parseOptional(view.level().registryAccess(), data.getCompound(YoukaiPotStation.STACK)), data.getBoolean(YoukaiPotStation.SERVED));
        }
    }

    /** Things went in: each comes down from the cook into the soup, or is poured, one after the other. */
    private void in(StationView view, ListTag went) {
        float at = start();
        int count = went.size();
        float lands = at;
        for (int i = 0; i < count; i++) {
            ItemStack stack = ItemStack.parseOptional(view.level().registryAccess(), went.getCompound(i));
            int index = i;
            float begins = at;
            if (KitchenLooks.isPoured(stack) && view.node("hand_0").isPresent()) {
                // A pour holds the hand: the one before must be in, the next waits for the bottle to go.
                begins = Math.max(begins, lands);
                float poured = KitchenMotions.Pour.pouredBy(stack);
                float from = begins;
                after(from, () -> pour(view, stack));
                at = begins + poured + 0.3F;
                lands = at;
            } else {
                after(begins, () -> flyIn(view, stack, index, count));
                at = begins + NEXT;
                lands = Math.max(lands, begins + FLY_TIME);
            }
        }
        YoukaiPotFlights.coming(view.pos(), count, lands + 1.0F);
        busyUntil = clock + Math.max(at, lands);
    }

    /** Food comes down from the cook into the soup with a splash and floats there once it is in. */
    private void flyIn(StationView view, ItemStack stack, int index, int count) {
        Optional<NodeView> found = view.node("fly_" + flier);
        flier = (flier + 1) % FLIERS;
        if (found.isEmpty()) {
            landed(view, 0.0F, 0.0F);
            return;
        }
        NodeView node = found.get();
        RandomSource random = view.level().getRandom();
        float angle = random.nextFloat() * Mth.TWO_PI;
        float distance = size.spread * (0.4F + 0.6F * random.nextFloat());
        float x = Mth.cos(angle) * distance;
        float z = Mth.sin(angle) * distance;
        show(node, stack, FLY_SIZE);
        Vector3f from = FromPlayer.offset(view, size.soup + FLY_FROM, index, count);
        node.moveTo(from.x, FLY_FROM, from.z, 0.0F);
        node.rotateTo(-55.0F, random.nextFloat() * 360.0F, 0.0F, 0.0F);
        node.scaleTo(0.6F, 0.0F);
        node.setVisible(true);
        node.hopTo(x, -1.0F, z, 2.5F, FLY_TIME);
        node.scaleTo(1.0F, FLY_TIME * 0.7F);
        after(FLY_TIME, () -> {
            node.setVisible(false);
            landed(view, x, z);
        });
    }

    /** Something is in the soup: it splashes. */
    private void landed(StationView view, float x, float z) {
        YoukaiPotFlights.landed(view.pos());
        view.node("broth").ifPresent(node -> node.ripple(8.0F + x, 8.0F + z, 0.6F));
        view.spawnParticles(DiegesisParticles.SPLASH, new Vector3f(8.0F + x, size.soup, 8.0F + z), new Vector3f(0.0F, 6.0F, 0.0F),
                0.6F, 4, SPLASH_COLOR);
        view.playSound(SoundEvents.GENERIC_SPLASH, "soup", 0.18F, 1.5F + view.level().getRandom().nextFloat() * 0.3F);
    }

    /** Soy sauce or cream is poured over the soup from the cook's side; it is in once it runs. */
    private void pour(StationView view, ItemStack stack) {
        NodeView node = view.node("hand_0").orElse(null);
        if (node == null) {
            landed(view, 0.0F, 0.0F);
            return;
        }
        if (hand != null) {
            hand.stop();
        }
        float rim = size.soup + POUR_OVER_RIM;
        Vector3f from = FromPlayer.offset(view, rim + 4.0F, 0, 1).add(8.0F, rim + 4.0F, 8.0F);
        KitchenMotions.Pour pour = new KitchenMotions.Pour(node, "youkai_pot_pour", stack, from, new Vector3f(8.0F, size.soup, 8.0F),
                () -> size.soup, null, POUR_SIZE, -1.0F, 0.0F).lipAtLeast(rim + 1.0F);
        pour.onFlow(() -> YoukaiPotFlights.landed(view.pos()));
        hand = pour;
    }

    /** The last thing came back out: it lifts out of the soup and goes to the cook. */
    private void back(StationView view, ItemStack stack) {
        float at = start();
        after(at, () -> leave(view, stack));
        busyUntil = clock + at + AWAY;
    }

    private void leave(StationView view, ItemStack stack) {
        view.node("fly_" + flier).ifPresent(node -> {
            show(node, stack, FLY_SIZE);
            node.moveTo(0.0F, -0.5F, 0.0F, 0.0F);
            node.rotateTo(-55.0F, 0.0F, 0.0F, 0.0F);
            node.scaleTo(0.9F, 0.0F);
            node.setVisible(true);
            Vector3f away = FromPlayer.offset(view, size.soup + FLY_FROM, 0, 1);
            node.hopTo(away.x, FLY_FROM, away.z, 3.0F, AWAY);
            node.scaleTo(0.3F, AWAY);
            after(AWAY, () -> node.setVisible(false));
        });
        flier = (flier + 1) % FLIERS;
        view.spawnParticles(DiegesisParticles.DROPLET, new Vector3f(8.0F, size.soup + 0.5F, 8.0F), new Vector3f(0.0F, 4.0F, 0.0F), 0.8F,
                3, SPLASH_COLOR);
        view.playSound(SoundEvents.ITEM_PICKUP, "soup", 0.3F, 1.4F);
    }

    /** What the pot would not take comes down to the rim and bounces back to the cook. */
    private void refused(StationView view, ItemStack stack) {
        view.node("fly_" + flier).ifPresent(node -> {
            show(node, stack, FLY_SIZE);
            Vector3f from = FromPlayer.offset(view, size.soup + FLY_FROM, 0, 1);
            node.moveTo(from.x, FLY_FROM, from.z, 0.0F);
            node.rotateTo(-55.0F, 0.0F, 0.0F, 0.0F);
            node.scaleTo(0.7F, 0.0F);
            node.setVisible(true);
            node.hopTo(from.x * 0.25F, 2.0F, from.z * 0.25F, 1.5F, 0.16F);
            after(0.16F, () -> {
                node.hopTo(from.x, FLY_FROM, from.z, 1.5F, 0.22F);
                node.scaleTo(0.4F, 0.22F);
                view.playSound(SoundEvents.WOOD_HIT, "soup", 0.4F, 0.8F);
            });
            after(0.38F, () -> node.setVisible(false));
        });
        flier = (flier + 1) % FLIERS;
    }

    /**
     * The pot has finished: a chime and a puff of steam. The dish stands where the pot stood, which closes the station; one that is
     * no block comes up out of the soup to the cook.
     */
    private void done(StationView view, ItemStack dish, boolean served) {
        doneAt = clock;
        // Played by the game's own sound manager: a feast's pot closes the station at once, the scene's sounds with it.
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.26F, 0.35F));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.5F, 0.3F));
        view.spawnParticles(DiegesisParticles.STEAM, new Vector3f(8.0F, size.soup + 1.0F, 8.0F), new Vector3f(0.0F, 5.0F, 0.0F), 2.0F, 6,
                0xA0FFFFFF);
        if (served && !dish.isEmpty()) {
            leave(view, dish);
        }
    }

    /** The ladle taken by its handle: held while the button is, or a click takes it in hand until the next click. */
    private void stroke(StationView view, SceneEvent.Stroke.Phase phase) {
        if (pot(view) == null && phase == SceneEvent.Stroke.Phase.START) {
            // Nothing to stir in an empty pot: the ladle stays leaning where the block has it.
            return;
        }
        switch (phase) {
            case START -> {
                stroking = true;
                if (!heldByClick) {
                    strokeStart = clock;
                }
            }
            case END -> {
                stroking = false;
                if (heldByClick) {
                    heldByClick = false;
                } else if (clock - strokeStart < SPOON_CLICK) {
                    heldByClick = true;
                    view.holdStroke();
                }
            }
            default -> {
            }
        }
    }

    /** The ladle's end follows the hand round the pot; for everybody else it follows what the server was told. */
    private void moveSpoon(StationView view, float dt) {
        NodeView spoon = view.node("ladle").orElse(null);
        NodeView pot = view.node("body").orElse(null);
        if (spoon == null || pot == null) {
            return;
        }
        boolean stirred = view.state().getControl(YoukaiPotStation.STIRRING) > 0.5F;
        float wantX = REST_X;
        float wantZ = 0.0F;
        boolean hand = stroking && view.isFocused();
        if (hand) {
            Optional<Vector3f> cursor = view.cursorOn(size.soup);
            if (cursor.isPresent()) {
                Vector3f inPot = potInverse.set(pot.sceneMatrix()).invert().transformPosition(cursor.get());
                float distance = (float) Math.hypot(inPot.x, inPot.z);
                float shorter = distance > SPOON_REACH ? SPOON_REACH / distance : 1.0F;
                wantX = inPot.x * shorter;
                wantZ = inPot.z * shorter;
            }
            handLeft = HAND_MEMORY;
        } else if (stirred && handLeft > 0.0F) {
            // Let go, it stands where the hand left it: what the server last heard lies a little behind.
            wantX = tipX;
            wantZ = tipZ;
        } else if (stirred) {
            wantX = view.state().getControl(YoukaiPotStation.SPOON_X) * SPOON_REACH;
            wantZ = view.state().getControl(YoukaiPotStation.SPOON_Z) * SPOON_REACH;
        }
        handLeft -= dt;
        // Nobody holds it any more: it sets off softly and takes its time, the rest of its moves answer a hand.
        boolean goingHome = !hand && !stirred;
        homing = goingHome ? Math.min(1.0F, homing + dt / SPOON_HOME_EASE) : 0.0F;
        grip = Mth.lerp(Math.min(1.0F, dt * (goingHome ? SPOON_HOME_GRIP * homing : 7.0F)), grip, goingHome ? 0.0F : 1.0F);
        float follow = 1.0F - (float) Math.exp(-dt * (hand ? 18.0F : goingHome ? SPOON_HOME_RATE * homing : 9.0F));
        float stepX = (wantX - tipX) * follow;
        float stepZ = (wantZ - tipZ) * follow;
        // Round which way the hand goes: the sign of the turn about the middle.
        float across = tipX * stepZ - tipZ * stepX;
        tipX += stepX;
        tipZ += stepZ;
        float moved = (float) Math.hypot(stepX, stepZ);
        if (grip > 0.5F && dt > 0.0F) {
            // Soup takes up a stir slowly: what floats speeds up and turns round smoothly, whatever the hand does from frame to frame.
            if (moved > 0.02F && Math.abs(across) > 1.0e-3F) {
                swirlSign = Mth.lerp(Math.min(1.0F, dt * 1.5F), swirlSign, Math.signum(across));
            }
            float wanted = Math.min(1.0F, moved / dt / 7.0F);
            if (wanted > swirl) {
                swirl = Mth.lerp(Math.min(1.0F, dt * 2.5F), swirl, wanted);
            }
            stirEffects(view, pot, moved, dt, hand);
        }
        swirl *= (float) Math.pow(0.5F, dt / SWIRL_HALF_LIFE);
        pose(spoon, pot);
        // The soup goes round with the stir, and what floats on it.
        float current = (CALM_DRIFT + swirl * SWIRL_SPEED) * swirlSign;
        turned += dt * current;
        view.node("broth").ifPresent(broth -> broth.setCurrent(current));
        YoukaiPotFlights.stirred(view.pos(), FLOATERS_TURN * turned * Mth.RAD_TO_DEG);
        view.loopSound("stir", PotSounds.STIR, swirl * STIR_VOLUME, 0.9F + 0.15F * swirl);
    }

    /** What a moving ladle does: a trail where it goes through the soup, rings from its end, and word to the server. */
    private void stirEffects(StationView view, NodeView pot, float moved, float dt, boolean hand) {
        if (moved > 0.005F) {
            view.node("broth").ifPresent(broth -> broth.leaveWake(spoonThrough.x, spoonThrough.z));
        }
        rippleClock -= dt;
        if (moved > 0.01F && rippleClock <= 0.0F) {
            rippleClock = RIPPLE_INTERVAL;
            Vector3f at = pot.sceneMatrix().transformPosition(new Vector3f(tipX, 0.0F, tipZ));
            view.node("broth").ifPresent(broth -> broth.ripple(at.x, at.z, RIPPLE_STRENGTH));
        }
        if (hand) {
            sendClock -= dt;
            sendTravel += moved;
            if (sendClock <= 0.0F && sendTravel >= SEND_TRAVEL) {
                sendClock = SEND_INTERVAL;
                sendTravel = 0.0F;
                CompoundTag where = new CompoundTag();
                where.putFloat(YoukaiPotStation.STIR_X, tipX / SPOON_REACH);
                where.putFloat(YoukaiPotStation.STIR_Z, tipZ / SPOON_REACH);
                view.sendAction(YoukaiPotStation.ACTION_STIR, where);
            }
        }
    }

    /** Leans the ladle from its rest towards the hand's upright, the end of its bowl staying where the hand has it. */
    private void pose(NodeView spoon, NodeView pot) {
        // The lean grows with the distance from the middle, so it goes through the middle without a jump.
        float rest = (float) Math.tan(REST_LEAN * Mth.DEG_TO_RAD);
        float held = (float) Math.tan(STIR_LEAN * Mth.DEG_TO_RAD) / SPOON_REACH;
        float leanX = Mth.lerp(grip, rest, tipX * held);
        float leanZ = Mth.lerp(grip, 0.0F, tipZ * held);
        // The shortest way from how the model leans by itself: the handle tips, it never twists about itself.
        Quaternionf turn = lean.rotationTo(restAxis.set(rest, 1.0F, 0.0F).normalize(), leanAxis.set(leanX, 1.0F, leanZ).normalize());
        Vector3f angles = turn.getEulerAnglesYXZ(poseAngles).mul(Mth.RAD_TO_DEG);
        spoon.moveTo(tipX, SPOON_END, tipZ, 0.0F);
        spoon.rotateTo(angles.x, angles.y, angles.z, 0.0F);
        // Where the handle goes through the surface, in the scene, for its trail.
        float height = size.soup - SPOON_END;
        pot.sceneMatrix().transformPosition(spoonThrough.set(tipX + leanX * height, 0.0F, tipZ + leanZ * height));
    }

    /** The soup in the scene's liquid: its colour, how uneven, how restless. */
    private void soup(StationView view) {
        KitchenYoukaiPot pot = pot(view);
        NodeView node = view.node("broth").orElse(null);
        if (node == null) {
            return;
        }
        // An empty pot shows its bare inside until water is poured in.
        node.setVisible(pot != null);
        if (pot == null) {
            return;
        }
        KitchenYoukaiPot.Soup soup = pot.handson$soup();
        int colour = soupColour(view, soup.now());
        if (soup.toNext() > 0.0F) {
            colour = blend(colour, soupColour(view, soup.next()), KitchenMotions.smooth(soup.toNext()));
        }
        node.setLiquidColor(colour);
        node.setMottle(WATER.equals(soup.now().getPath()) ? WATER_MOTTLE : SOUP_MOTTLE);
        boolean heated = pot.handson$isHeated();
        boolean cooking = heated && pot.handson$cooking() != null;
        // A soup that shakes looks wrong: a slight stir over heat, a little more while it cooks.
        node.setAgitation(cooking ? 0.06F : heated ? 0.02F : 0.0F);
        node.setEdgeBoil(cooking ? 0.12F : 0.0F);
    }

    /** The colour of a soup base: water as the biome's, half darkened in iron; any other its texture's. */
    private static int soupColour(StationView view, ResourceLocation soup) {
        if (WATER.equals(soup.getPath())) {
            return WATER_ALPHA << 24 | blend(BiomeColors.getAverageWaterColor(view.level(), view.pos()), WATER_IN_IRON, 0.5F) & 0xFFFFFF;
        }
        BakedModel missing = Minecraft.getInstance().getModelManager().getMissingModel();
        if (missing != soupColoursReadWith) {
            // Resources were reloaded: a resource pack may paint the soups otherwise now.
            soupColoursReadWith = missing;
            SOUP_COLOURS.clear();
        }
        return SOUP_COLOURS.computeIfAbsent(soup, id -> SOUP_ALPHA << 24 | textureColour(id.withPrefix("block/bowl/soup/")));
    }

    /** The average colour of a texture of the block atlas, its first frame, as much as each pixel shows. */
    private static int textureColour(ResourceLocation texture) {
        SpriteContents contents = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture).contents();
        NativeImage image = ((SpriteContentsAccessor) contents).handson$originalImage();
        long red = 0;
        long green = 0;
        long blue = 0;
        long weight = 0;
        for (int y = 0; y < contents.height(); y++) {
            for (int x = 0; x < contents.width(); x++) {
                int abgr = image.getPixelRGBA(x, y);
                int alpha = abgr >>> 24;
                red += (long) (abgr & 0xFF) * alpha;
                green += (long) (abgr >> 8 & 0xFF) * alpha;
                blue += (long) (abgr >> 16 & 0xFF) * alpha;
                weight += alpha;
            }
        }
        return weight == 0 ? 0x806040 : (int) (red / weight) << 16 | (int) (green / weight) << 8 | (int) (blue / weight);
    }

    private static int blend(int from, int to, float amount) {
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            result |= Math.round(Mth.lerp(amount, from >>> shift & 0xFF, to >>> shift & 0xFF)) << shift;
        }
        return result;
    }

    /** The stockpot's copy stands as the block faces, whichever way the scene turned to the cook. */
    private void turnToBlock(StationView view) {
        BlockState state = view.level().getBlockState(view.pos());
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return;
        }
        Direction block = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        // Its block state turns its model as the facing turns from north (Registrate's horizontal blocks).
        float turn = view.facing().toYRot() - block.toYRot() + 180.0F;
        if (turn != shownTurn) {
            shownTurn = turn;
            view.node("body").ifPresent(node -> node.rotateTo(0.0F, turn, 0.0F, 0.0F));
        }
    }

    private float swell() {
        float since = clock - doneAt;
        return since >= 0.0F && since < DONE_TIME ? 1.0F + DONE_SWELL * Mth.sin(Mth.PI * since / DONE_TIME) : 1.0F;
    }

    /** The badge while the pot cooks: what it makes, whether it is heated, how far it has cooked and the seconds left. */
    private void badge(StationView view, boolean here) {
        KitchenYoukaiPot pot = pot(view);
        ItemStack dish = pot == null ? ItemStack.EMPTY : cooking(pot);
        boolean shown = here && !dish.isEmpty();
        view.node("badge").ifPresent(plate -> plate.setVisible(shown));
        int total = pot == null ? 0 : pot.handson$cookTime();
        int cooked = pot == null ? 0 : pot.handson$cooked();
        boolean heated = pot != null && pot.handson$isHeated();
        int seconds = shown && total > 0 ? Math.max(1, Mth.ceil((total - cooked) / 20.0F)) : 0;
        int bar = shown && total > 0 ? Math.round((float) cooked / total * BAR_LENGTH) : 0;
        String text = seconds >= 60 ? seconds / 60 + ":" + String.format("%02d", seconds % 60) : seconds > 0 ? Integer.toString(seconds) : "";
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
        // The flame: lit over heat; grey and blinking while the pot waits for heat (it cools back meanwhile).
        view.node("flame").ifPresent(node -> node.setVisible(shown && !heated && Mth.sin(clock * 6.0F) > 0.0F));
        view.node("flame_lit").ifPresent(node -> node.setVisible(shown && heated));
        view.node("preview").ifPresent(node -> {
            node.setVisible(shown);
            if (!shown) {
                return;
            }
            if (shownDish != dish.getItem()) {
                shownDish = dish.getItem();
                node.setVisual(Visual.ofItem(shownDish).withAnchor(ICON_MIDDLE));
            }
            node.setTooltip(seconds > 0 ? Component.translatable("handson_delight.pot.cooking", dish.getHoverName(), seconds)
                    : dish.getHoverName());
        });
    }

    /** The dish the pot is cooking now, or nothing. */
    private static ItemStack cooking(KitchenYoukaiPot pot) {
        ResourceLocation id = pot.handson$cooking();
        if (id == null) {
            return ItemStack.EMPTY;
        }
        return pot.handson$dishes().stream().filter(dish -> dish.id().equals(id)).findFirst().map(KitchenYoukaiPot.Dish::result)
                .orElse(ItemStack.EMPTY);
    }

    /** A line of digits and colons centred under the bar, a pixel between characters. */
    static void text(MeshShape.Builder shape, String text) {
        int width = -1;
        for (char c : text.toCharArray()) {
            width += (c == ':' ? 1 : 3) + 1;
        }
        int x = -width / 2 - width % 2;
        for (char c : text.toCharArray()) {
            String[] glyph = c == ':' ? COLON : PotBehavior.DIGITS[c - '0'];
            glyph(shape, glyph, x, TEXT_TOP);
            x += glyph[0].length() + 1;
        }
    }

    static void glyph(MeshShape.Builder shape, String[] rows, int left, int top) {
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < rows[row].length(); column++) {
                if (rows[row].charAt(column) == '#') {
                    PotBehavior.pixels(shape, left + column, top + row, 1, 1);
                }
            }
        }
    }

    /** Shows {@code stack} on {@code node}, {@code size} pixels across, built from its icon where it has one. */
    static void show(NodeView node, ItemStack stack, float size) {
        Optional<ItemVoxels.Icon> icon = ItemVoxels.icon(stack);
        if (icon.isPresent()) {
            node.setVisual(null);
            node.setShape(ItemVoxels.build(icon.get(), size, List.of(), 0.0F), icon.get().sprite());
        } else {
            node.setShape(null, null);
            node.setVisual(Visual.ofItem(stack.getItem()).withAnchor(new Vector3f(8.0F, 8.0F, 8.0F)));
        }
        node.setTint(0xFFFFFFFF);
    }

    private static boolean sameStacks(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.matches(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }
}
