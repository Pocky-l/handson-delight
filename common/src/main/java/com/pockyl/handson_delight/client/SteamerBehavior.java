package com.pockyl.handson_delight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.MeshShape;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.handson_delight.station.KitchenSteamer;
import com.pockyl.handson_delight.station.SteamerStation;
import com.pockyl.handson_delight.station.SteamerTower;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Youkai's Feasts' steamer on the client: the tower of its pot and racks, drawn by the scene while somebody works at it. The lid
 * comes off with a puff of steam and is set on the counter at the cook's right; the top rack is open, its four places as the
 * steamer has them. Any other rack, on the tower under the open one or on the stack aside, is outlined under the cursor and opens
 * when clicked: the racks over it stand aside on the lid as one stack in the tower's order, so the rack just over the open one is
 * at its foot. The wheel opens the next rack up or down. The camera goes down and up with the open rack. Food on the cursor goes onto the place under it, which
 * shows it see-through first; a rack in the hand goes on top, the lid on, water into the pot. A click on a thing takes it, shift
 * takes everything done on the open rack, the other button takes back what was laid last. Steam rises through the open rack, a thing done glints;
 * the badge tells what steams on the open rack, whether the pot has water and heat, and the seconds to the next thing done.
 */
public final class SteamerBehavior implements StationBehavior {
    private static final int RACKS = SteamerTower.MOST_RACKS;
    private static final int PLACES = KitchenSteamer.PLACES;
    /** The pot's height and a rack's, pixels: rack {@code k} stands from {@code 8 + 4k}. */
    private static final float POT_TOP = 8.0F;
    private static final float RACK_HEIGHT = 4.0F;
    /** The rack the scene's camera, badge and book are placed for: the pot's second, its top at 16 px. */
    private static final int CAMERA_RACK = 1;
    /** A thing on a place: this many pixels across (the steamer's own 0.3125 of a block), a quarter off the middle, just over the floor. */
    private static final float ITEM_SIZE = 5.0F;
    private static final float QUARTER = 2.0F;
    /**
     * The racks over the open one: one stack, in the tower's order, set down one block to the cook's right on the lid that went
     * there first (on its handle) or on the counter. The rack just over the open one lies lowest, always in the shot.
     */
    private static final float ASIDE = -16.0F;
    private static final float ON_LID = 2.0F;
    /** Lid and racks aside stand this much nearer the cook than the tower, clear of the badge over them. */
    private static final float ASIDE_FRONT = -3.5F;
    private static final float LIFT_UP = 3.0F;
    /**
     * Racks going to the stack or back pass this much nearer the cook than it: they come in under the racks already there (which
     * rise to let them in) and go out from under them, never through them.
     */
    private static final float LANE = 15.0F;
    /** Every move of the racks, so that racks moving together arrive together. */
    private static final float RACK_TIME = 0.42F;
    /** Of a move, the part in which the racks on the stack make room (first) or close up (last). */
    private static final float MAKE_ROOM = 0.55F;
    private static final byte PATH_DIRECT = 0;
    private static final byte PATH_OUT = 1;
    private static final byte PATH_IN = 2;
    private static final byte PATH_RISE = 3;
    private static final byte PATH_SETTLE = 4;
    /** The lid: up off the top, over to the counter one block to the cook's right, and down onto it. */
    private static final float LID_ASIDE = -16.0F;
    private static final float LID_UP = 4.0F;
    private static final float LID_TIME = 0.4F;
    /** What comes on from the cook: flight, the next one later, and from how high over the rack. */
    private static final float FLY_TIME = 0.3F;
    private static final float NEXT = 0.12F;
    private static final float FLY_FROM = 7.0F;
    private static final float AWAY_TIME = 0.3F;
    /** How long a thing just taken stays gone while the steamer still shows it: the effect comes before the block's own update. */
    private static final float LEAVING_TIME = 1.0F;
    /** A new rack comes down onto the tower from this high. */
    private static final float RACK_DROP = 6.0F;
    private static final float RACK_DROP_TIME = 0.25F;
    private static final int LIFTS = 4;
    private static final int OPEN_WALLS = 4;
    private static final int PREVIEW_TINT = 0x80FFFFFF;
    private static final float STEAM_RATE = 2.2F;
    private static final int STEAM_COLOR = 0x50FFFFFF;
    private static final int GLINT_COLOR = 0xFFFFE9A0;
    private static final int SPLASH_COLOR = 0xC0B8D4E8;
    private static final int WATER_ALPHA = 0x96;
    private static final int WATER_IN_IRON = 0x1E1E24;
    /** The badge, as the pots': plate, a flame and a drop over the icon of what steams, the bar and the seconds left under it. */
    private static final String[] BADGE_PARTS = {"badge", "dial_track", "dial", "flame", "flame_lit", "drop", "drop_lit", "preview"};
    @SuppressWarnings("removal")
    private static final ResourceLocation BADGE_TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "block/white_concrete");
    private static final int PLATE_COLOR = 0x8C1E140C;
    private static final int DIAL_COLOR = 0xFFFFCB6B;
    private static final int TRACK_COLOR = 0x50FFF2DC;
    private static final int FLAME_COLOR = 0xFFFF9A3C;
    private static final int DROP_COLOR = 0xFF6FB4F0;
    private static final int GLYPH_TOP = -15;
    private static final int BAR_LEFT = -7;
    private static final int BAR_TOP = 10;
    private static final int BAR_LENGTH = 14;
    private static final String[] DROP = {"..#..", ".###.", "#####", "#####", ".###."};
    private static final Vector3f ICON_MIDDLE = new Vector3f(8.0F, 8.0F, 8.0F);
    /** The steam a thing gets at the least over a boiling pot: what rises, with no lid sending it back down. */
    private static final float MIN_HEAT = 0.2F;

    private final RecipeBook book = new RecipeBook(new SteamerBook());
    private final UsableCache usable = new UsableCache();
    private final RandomSource random = RandomSource.create();
    private final ItemStack[][] shown = new ItemStack[RACKS][PLACES];
    private final boolean[][] previewing = new boolean[RACKS][PLACES];
    /** What each place gave the cook a moment ago, and until when. */
    private final ItemStack[][] leaving = new ItemStack[RACKS][PLACES];
    private final float[][] leavingUntil = new float[RACKS][PLACES];
    /** Until when the thing on a place is still on its way there: the code leaves its position alone meanwhile. */
    private final float[][] flyingUntil = new float[RACKS][PLACES];
    private final float[] droppedAt = new float[RACKS];
    /**
     * Each rack's move: where it is, where it came from and goes, offsets from its place on the tower in pixels; how far along, 0
     * to 1, and by which way.
     */
    private final Vector3f[] rackAt = new Vector3f[RACKS];
    private final Vector3f[] rackFrom = new Vector3f[RACKS];
    private final Vector3f[] rackTo = new Vector3f[RACKS];
    private final float[] rackGone = new float[RACKS];
    private final byte[] rackPath = new byte[RACKS];
    private final List<Step> steps = new ArrayList<>();
    private float clock;
    private float busyUntil;
    private float lidOff;
    private float shownTurn = Float.NaN;
    private boolean quiet = true;
    private float steam;
    private int ghost;
    private String drawnDial = "";
    /** What the badge shows: the thing nearest done, and what it becomes. */
    private Item badgeFor;
    private ItemStack badgeIcon = ItemStack.EMPTY;

    private record Step(float at, Runnable run) {
    }

    public SteamerBehavior(StationView view) {
        clear();
    }

    private void clear() {
        for (ItemStack[] rack : shown) {
            Arrays.fill(rack, ItemStack.EMPTY);
        }
        for (ItemStack[] rack : leaving) {
            Arrays.fill(rack, ItemStack.EMPTY);
        }
        for (float[] rack : flyingUntil) {
            Arrays.fill(rack, 0.0F);
        }
        Arrays.fill(droppedAt, -10.0F);
        for (int rack = 0; rack < RACKS; rack++) {
            rackAt[rack] = new Vector3f();
            rackFrom[rack] = new Vector3f();
            rackTo[rack] = new Vector3f();
            rackGone[rack] = 1.0F;
            rackPath[rack] = PATH_DIRECT;
        }
        steps.clear();
        shownTurn = Float.NaN;
        quiet = true;
        drawnDial = "";
        badgeFor = null;
        badgeIcon = ItemStack.EMPTY;
    }

    static SteamerTower tower(StationView view) {
        return SteamerTower.of(view.level(), view.pos());
    }

    private static float rackY(int rack) {
        return POT_TOP + RACK_HEIGHT * rack;
    }

    /** The open rack: the one the station has open, the top one otherwise; -1 while the pot has none. */
    static int openRack(StationView view, SteamerTower tower) {
        int open = Math.round(view.state().getControl(SteamerStation.OPEN));
        return open < 0 || open >= tower.racks() ? tower.racks() - 1 : open;
    }

    @Override
    public void onSceneReady(StationView view) {
        clear();
        view.node("book_open").ifPresent(node -> node.setPainter(book));
        view.node("dial").ifPresent(dial -> dial.setTint(DIAL_COLOR));
        view.node("dial_track").ifPresent(track -> track.setTint(TRACK_COLOR));
        view.node("flame").ifPresent(node -> node.setTint(TRACK_COLOR));
        view.node("flame_lit").ifPresent(node -> node.setTint(FLAME_COLOR));
        view.node("drop").ifPresent(node -> node.setTint(TRACK_COLOR));
        view.node("drop_lit").ifPresent(node -> node.setTint(DROP_COLOR));
        view.node("badge").ifPresent(plate -> {
            plate.setTint(PLATE_COLOR);
            MeshShape.Builder shape = MeshShape.builder();
            PotBehavior.pixels(shape, -10, -17, 20, 36);
            PotBehavior.pixels(shape, -9, -18, 18, 1);
            PotBehavior.pixels(shape, -9, 19, 18, 1);
            plate.setShape(shape.build(), BADGE_TEXTURE, true);
        });
        MeshShape.Builder flame = MeshShape.builder();
        YoukaiPotBehavior.glyph(flame, PotBehavior.FLAME, -7, GLYPH_TOP);
        MeshShape flameShape = flame.build();
        view.node("flame").ifPresent(node -> node.setShape(flameShape, BADGE_TEXTURE, true));
        view.node("flame_lit").ifPresent(node -> node.setShape(flameShape, BADGE_TEXTURE, true));
        MeshShape.Builder drop = MeshShape.builder();
        YoukaiPotBehavior.glyph(drop, DROP, 2, GLYPH_TOP);
        MeshShape dropShape = drop.build();
        view.node("drop").ifPresent(node -> node.setShape(dropShape, BADGE_TEXTURE, true));
        view.node("drop_lit").ifPresent(node -> node.setShape(dropShape, BADGE_TEXTURE, true));
        for (String part : BADGE_PARTS) {
            view.node(part).ifPresent(node -> node.setOnTop(true));
        }
        view.node("water").ifPresent(node -> node.showLiquid(YoukaiPotBehavior.SOUP_LIQUID, 1.0F));
        SteamerTower tower = tower(view);
        // Opened from afar, straight to the shot of the open rack: the racks over it are still on, the top one is open.
        if (tower != null) {
            view.moveCamera(0.0F, cameraLift(tower.racks() - 1), 0.0F);
        }
    }

    @Override
    public void onOpen(StationView view) {
        book.opened(view);
        usable.clear();
        SteamerTower tower = tower(view);
        if (tower == null) {
            return;
        }
        KitchenSteamer pot = tower.potBlock();
        // What the steamer takes is lit in the drawer, with racks, the lid and water.
        view.setInventoryFilter(stack -> usable.test(stack, it -> pot.handson$takes(it) || SteamerStation.isRack(it)
                || SteamerStation.isLid(it) || it.is(Items.WATER_BUCKET)));
        // What the cook came with is meant for the steamer: food onto the open rack, a rack on top, the lid on, water in.
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && view.openedByUse() && player.inventoryMenu.getCarried().isEmpty()) {
            ItemStack held = player.getMainHandItem();
            CompoundTag how = new CompoundTag();
            how.putInt(SteamerStation.FROM, player.getInventory().selected);
            String action = action(pot, held);
            if (action != null) {
                view.sendAction(action, how);
            }
        }
    }

    /** What a stack does at the steamer: goes on a rack, on top as a rack, on as the lid, into the pot as water; null for nothing. */
    private static String action(KitchenSteamer pot, ItemStack stack) {
        if (SteamerStation.isRack(stack)) {
            return SteamerStation.ACTION_RACK;
        }
        if (SteamerStation.isLid(stack)) {
            return SteamerStation.ACTION_LID;
        }
        if (stack.is(Items.WATER_BUCKET)) {
            return SteamerStation.ACTION_WATER;
        }
        return pot.handson$takes(stack) ? SteamerStation.ACTION_PUT : null;
    }

    @Override
    public void onClose(StationView view) {
        book.shut(view);
    }

    /** The racks and the lid still on their way back after the last one left: the scene keeps standing in for the tower. */
    @Override
    public boolean holdsBlock(StationView view) {
        return !view.isInUse() && (anyAside() || lidOff > 0.0F);
    }

    @Override
    public void tick(StationView view) {
        for (int i = 0; i < LIFTS; i++) {
            view.node("lift_" + i).filter(node -> node.isVisible() && !node.isMoving()).ifPresent(node -> node.setVisible(false));
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
        SteamerTower tower = tower(view);
        if (tower == null) {
            return;
        }
        int count = tower.racks();
        boolean working = view.isInUse();
        int open = working ? openRack(view, tower) : count - 1;
        turn(view);
        racks(view, tower, dt, open, working);
        lid(view, tower, dt, working);
        places(view, tower, open);
        water(view, tower);
        steam(view, tower, dt, open);
        float shot = cameraLift(open);
        view.moveCamera(0.0F, shot, 0.0F);
        view.node("desk").ifPresent(node -> node.moveTo(0.0F, shot, 0.0F, 0.0F));
        book.frame(view, dt);
        float appear = RecipeBook.appear(view);
        badge(view, tower, open, working && RecipeBook.isWorkedAt(view) && appear > RecipeBook.SPECK);
        for (String part : BADGE_PARTS) {
            view.node(part).ifPresent(node -> node.scaleTo(appear, 0.0F));
        }
        quiet = false;
    }

    /** How far the camera, badge and book stand from where the scene has them: as far as the open rack is from its second rack. */
    private static float cameraLift(int open) {
        return RACK_HEIGHT * (open - CAMERA_RACK);
    }

    /** The scene's turn into the world's frame (what the steamer's places are counted in), degrees. */
    private static float worldTurn(StationView view) {
        return view.facing().toYRot() + 180.0F;
    }

    /** Pot and lid stand as their blocks face, whichever way the scene turned to the cook; the racks are square. */
    private void turn(StationView view) {
        BlockState state = view.level().getBlockState(view.pos());
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return;
        }
        Direction block = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        // Its block states turn its models as the facing turns from south (Registrate's horizontal blocks).
        float turn = worldTurn(view) - block.toYRot();
        if (turn != shownTurn) {
            shownTurn = turn;
            view.node("pot").ifPresent(node -> node.rotateTo(0.0F, turn, 0.0F, 0.0F));
            view.node("lid").ifPresent(node -> node.rotateTo(0.0F, turn, 0.0F, 0.0F));
            for (int rack = 0; rack < RACKS; rack++) {
                for (int place = 0; place < PLACES; place++) {
                    shown[rack][place] = ItemStack.EMPTY;
                }
            }
        }
    }

    /**
     * The racks the tower has. Those over the open one stand aside as one stack in the tower's order, on the lid to the cook's
     * right; when another rack opens, the racks that change sides go under that stack or out from under it, and the stack makes
     * room or closes up. Any rack but the open one is outlined under the cursor: a click opens that very rack. A new rack comes
     * down onto the top.
     */
    private void racks(StationView view, SteamerTower tower, float dt, int open, boolean working) {
        int count = tower.racks();
        float base = tower.isCapped() ? ON_LID : 0.0F;
        boolean landed = false;
        boolean lifted = false;
        for (int rack = 0; rack < RACKS; rack++) {
            Optional<NodeView> node = view.node("rack_" + rack);
            if (node.isEmpty()) {
                continue;
            }
            boolean there = rack < count;
            node.get().setVisible(there);
            if (!there) {
                rackAt[rack].zero();
                rackTo[rack].zero();
                rackGone[rack] = 1.0F;
                continue;
            }
            Vector3f target = working && rack > open
                    ? new Vector3f(ASIDE, base + RACK_HEIGHT * (rack - open - 1) - rackY(rack), ASIDE_FRONT) : new Vector3f();
            if (target.distanceSquared(rackTo[rack]) > 1.0E-4F) {
                lifted |= setOff(rack, target);
            }
            if (rackGone[rack] < 1.0F) {
                rackGone[rack] = Math.min(1.0F, rackGone[rack] + dt / RACK_TIME);
                rackAt[rack].set(along(rack, rackGone[rack]));
                landed |= rackGone[rack] >= 1.0F && rackPath[rack] != PATH_RISE && rackPath[rack] != PATH_SETTLE;
            }
            Vector3f at = rackAt[rack];
            float drop = clock - droppedAt[rack] < RACK_DROP_TIME ? RACK_DROP * (1.0F - KitchenMotions.smooth((clock - droppedAt[rack])
                    / RACK_DROP_TIME)) : 0.0F;
            node.get().moveTo(at.x, at.y + drop, at.z, 0.0F);
            // The open rack's places take the clicks; every other rack opens when clicked, and only while the racks stand still.
            boolean pickable = working && rack != open && !moving();
            node.get().setEnabled(pickable);
            // Only its own band: never its whole shape through the racks over it.
            node.get().setOutlineSeenOnly(true);
            node.get().setOutlined(pickable && node.get().isHovered());
        }
        // The open rack's walls stop the cursor, which would go through them to the rack under it; they open nothing.
        boolean walls = working && open >= 0 && open < count && !moving();
        for (int wall = 0; wall < OPEN_WALLS; wall++) {
            float y = walls ? RACK_HEIGHT * open : 0.0F;
            view.node("open_wall_" + wall).ifPresent(node -> {
                node.setEnabled(walls);
                node.moveTo(0.0F, y, 0.0F, 0.0F);
            });
        }
        if (lifted) {
            view.playSound(SoundEvents.BAMBOO_HIT, "pot", 0.45F, 1.2F);
        }
        if (landed) {
            view.playSound(SoundEvents.BAMBOO_PLACE, "pot", 0.45F, 1.0F);
        }
        // The pot is no rack: it opens nothing, so it is never outlined.
        view.node("pot").ifPresent(node -> node.setOutlined(false));
    }

    /** Sends a rack from where it is now to {@code target}; true when it leaves the tower or the stack (a lift worth a sound). */
    private boolean setOff(int rack, Vector3f target) {
        Vector3f now = rackAt[rack];
        boolean onTower = rackGone[rack] >= 1.0F && now.lengthSquared() < 1.0E-4F;
        boolean onStack = rackGone[rack] >= 1.0F && Math.abs(now.x - ASIDE) < 1.0E-2F;
        boolean toStack = Math.abs(target.x - ASIDE) < 1.0E-2F;
        byte path;
        if (onTower && toStack) {
            path = PATH_OUT;
        } else if (onStack && !toStack) {
            path = PATH_IN;
        } else if (onStack) {
            // Staying on the stack: up first to let racks in under it, down last once the racks under it are out.
            path = target.y > now.y ? PATH_RISE : PATH_SETTLE;
        } else {
            path = PATH_DIRECT;
        }
        rackFrom[rack].set(now);
        rackTo[rack].set(target);
        rackGone[rack] = 0.0F;
        rackPath[rack] = path;
        return path == PATH_OUT || path == PATH_IN;
    }

    /**
     * Where a rack is {@code k} of its move along. To the stack: up off the tower and towards the cook, over to the stack and down
     * to its foot, then in under it. Back: out from under it towards the cook, over and up to the tower, then in onto it. Along
     * the stack: straight up or down, in the part of the move that lets the others pass.
     */
    private Vector3f along(int rack, float k) {
        Vector3f from = rackFrom[rack];
        Vector3f to = rackTo[rack];
        return switch (rackPath[rack]) {
            case PATH_OUT -> polyline(KitchenMotions.smooth(k), from, new Vector3f(from.x, from.y + LIFT_UP, to.z - LANE),
                    new Vector3f(to.x, to.y, to.z - LANE), to);
            case PATH_IN -> polyline(KitchenMotions.smooth(k), from, new Vector3f(from.x, from.y, from.z - LANE),
                    new Vector3f(to.x, to.y + LIFT_UP, to.z - LANE), new Vector3f(to.x, to.y + LIFT_UP, to.z), to);
            case PATH_RISE -> new Vector3f(from).lerp(to, KitchenMotions.smooth(k / MAKE_ROOM));
            case PATH_SETTLE -> new Vector3f(from).lerp(to, KitchenMotions.smooth((k - (1.0F - MAKE_ROOM)) / MAKE_ROOM));
            default -> new Vector3f(from).lerp(to, KitchenMotions.smooth(k));
        };
    }

    /** The point {@code k} of the way along the straight legs through {@code points}, by length. */
    private static Vector3f polyline(float k, Vector3f... points) {
        float total = 0.0F;
        for (int i = 1; i < points.length; i++) {
            total += points[i].distance(points[i - 1]);
        }
        float left = k * total;
        for (int i = 1; i < points.length; i++) {
            float leg = points[i].distance(points[i - 1]);
            if (left <= leg && leg > 0.0F) {
                return new Vector3f(points[i - 1]).lerp(points[i], left / leg);
            }
            left -= leg;
        }
        return new Vector3f(points[points.length - 1]);
    }

    /** Whether a rack is off the tower or on its way. */
    private boolean anyAside() {
        for (int rack = 0; rack < RACKS; rack++) {
            if (rackGone[rack] < 1.0F || rackAt[rack].lengthSquared() > 1.0E-4F) {
                return true;
            }
        }
        return false;
    }

    /** Whether a rack is on its way to the stack aside or back. */
    private boolean moving() {
        for (float gone : rackGone) {
            if (gone < 1.0F) {
                return true;
            }
        }
        return false;
    }

    /** Up {@code up} pixels, over {@code x} and down {@code y} from where it was, {@code k} of the way: a curve that clears the rest. */
    private static Vector3f curve(float k, float up, float x, float y) {
        float u = 1.0F - k;
        Vector3f p1 = new Vector3f(0.0F, up, 0.0F);
        Vector3f p2 = new Vector3f(x, Math.max(up, y + up), ASIDE_FRONT);
        Vector3f p3 = new Vector3f(x, y, ASIDE_FRONT);
        return p1.mul(3.0F * u * u * k).add(p2.mul(3.0F * u * k * k)).add(p3.mul(k * k * k));
    }

    /** The lid, on the top rack while nobody works at the tower; off to the counter on the cook's right while somebody does. */
    private void lid(StationView view, SteamerTower tower, float dt, boolean working) {
        boolean capped = tower.isCapped();
        NodeView node = view.node("lid").orElse(null);
        if (node == null) {
            return;
        }
        float before = lidOff;
        // Racks still stand on it aside: it goes back once they are on the tower.
        lidOff = Mth.clamp(lidOff + ((working || anyAside()) && capped ? dt : -dt) / LID_TIME, 0.0F, 1.0F);
        if (before <= 0.0F && lidOff > 0.0F) {
            // Off the top: the steam under it comes out at once.
            view.playSound(SoundEvents.LANTERN_HIT, "pot", 0.3F, 1.5F);
            puff(view, tower);
        } else if (before > 0.0F && lidOff <= 0.0F || before < 1.0F && lidOff >= 1.0F) {
            view.playSound(SoundEvents.WOOD_PLACE, "pot", 0.4F, 1.3F);
        }
        node.setVisible(capped);
        float top = rackY(tower.racks());
        float k = KitchenMotions.smooth(lidOff);
        Vector3f at = curve(k, LID_UP, LID_ASIDE, -top);
        node.moveTo(at.x, top - 8.0F + at.y, at.z, 0.0F);
        node.setEnabled(false);
    }

    /** A puff of the steam held under the lid, out over the open rack. */
    private void puff(StationView view, SteamerTower tower) {
        if (!tower.potBlock().handson$isHeated() || !tower.potBlock().handson$hasWater()) {
            return;
        }
        Vector3f over = new Vector3f(8.0F, rackY(tower.racks()) + 0.5F, 8.0F);
        view.spawnParticles(DiegesisParticles.STEAM, over, new Vector3f(0.0F, 4.0F, 0.0F), 3.0F, 8, 0x90FFFFFF);
    }

    /**
     * What lies on each place, where the steamer has it (its quarters of the block, counted in the world's frame), turned as it lays
     * them; a feast block filling its rack. Over a free place of the open rack, the stack on the cursor shows see-through.
     */
    private void places(StationView view, SteamerTower tower, int open) {
        LocalPlayer player = Minecraft.getInstance().player;
        ItemStack carried = player == null || !view.isFocused() ? ItemStack.EMPTY : player.inventoryMenu.getCarried();
        KitchenSteamer pot = tower.potBlock();
        float world = worldTurn(view);
        boolean anyGhost = false;
        for (int rack = 0; rack < tower.racks() && rack < RACKS; rack++) {
            ItemStack first = there(tower, rack, 0);
            float clearance = pot.handson$clearance(first);
            feast(view, rack, clearance >= 0.0F ? first : ItemStack.EMPTY, clearance, world);
            for (int place = 0; place < PLACES; place++) {
                NodeView node = view.node("place_" + rack + "_" + place).orElse(null);
                if (node == null) {
                    continue;
                }
                ItemStack now = clearance >= 0.0F ? ItemStack.EMPTY : there(tower, rack, place);
                boolean ghost = rack == open && now.isEmpty() && clearance < 0.0F && !carried.isEmpty() && node.isHovered()
                        && pot.handson$takes(carried);
                ItemStack look = ghost ? carried : now;
                anyGhost |= ghost;
                // Its places take clicks once the racks over it are off (or none were), not while they move.
                node.setEnabled(rack == open && !moving());
                if (clock < flyingUntil[rack][place]) {
                    continue;
                }
                // Each place on its own quarter of the rack, empty or not, so that the cursor finds the one it is over.
                float yaw = world - 90.0F * place;
                Vector3f at = placeAt(yaw);
                node.moveTo(at.x, 0.0F, at.z, 0.0F);
                if (ItemStack.isSameItemSameComponents(look, shown[rack][place]) && ghost == previewing[rack][place]) {
                    continue;
                }
                ItemStack was = shown[rack][place];
                boolean wasGhost = previewing[rack][place];
                shown[rack][place] = look.copy();
                previewing[rack][place] = ghost;
                // An empty place stays, without a look, so that the cursor finds it.
                node.setVisible(true);
                if (look.isEmpty()) {
                    node.setShape(null, null);
                    node.setVisual(null);
                    continue;
                }
                if (ghost) {
                    // See-through, as the steamer's renderer draws a thing: the item laid flat.
                    node.setShape(null, null);
                    node.setVisual(Visual.ofItem(look.getItem()).withAnchor(ICON_MIDDLE).withTint(PREVIEW_TINT));
                    node.rotateTo(90.0F, yaw, 0.0F, 0.0F);
                    node.scaleTo(ITEM_SIZE / 16.0F, 0.0F);
                } else {
                    YoukaiPotBehavior.show(node, look, ITEM_SIZE);
                    node.rotateTo(0.0F, yaw, 0.0F, 0.0F);
                    node.scaleTo(1.0F, 0.0F);
                }
                // Steamed through: the thing became its result where it lies.
                if (!quiet && !ghost && !wasGhost && !was.isEmpty() && !was.is(look.getItem())) {
                    done(view, rack, place);
                }
            }
        }
        for (int rack = tower.racks(); rack < RACKS; rack++) {
            Arrays.fill(shown[rack], ItemStack.EMPTY);
        }
        // Over a free place the stack shows where it goes, see-through: the one on the cursor would cover it.
        view.setCarriedHidden(anyGhost);
    }

    /** What lies on {@code place} of {@code rack}; nothing while the steamer still has the thing just taken, which would come back. */
    private ItemStack there(SteamerTower tower, int rack, int place) {
        ItemStack now = tower.item(rack, place);
        if (!leaving[rack][place].isEmpty()) {
            if (clock < leavingUntil[rack][place] && ItemStack.isSameItemSameComponents(now, leaving[rack][place])) {
                return ItemStack.EMPTY;
            }
            leaving[rack][place] = ItemStack.EMPTY;
        }
        return now;
    }

    /** Where a thing lies on its place, from the middle of the rack, for a place turned {@code yaw} degrees. */
    private static Vector3f placeAt(float yaw) {
        return new Quaternionf().rotationY(yaw * Mth.DEG_TO_RAD).transform(new Vector3f(-QUARTER, 0.0F, -QUARTER));
    }

    /** A feast block fills its rack, as small as leaves it its room round it, as the steamer draws it. */
    private void feast(StationView view, int rack, ItemStack stack, float clearance, float world) {
        NodeView node = view.node("feast_" + rack).orElse(null);
        if (node == null) {
            return;
        }
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)) {
            node.setVisible(false);
            return;
        }
        node.setVisible(true);
        BlockState state = item.getBlock().defaultBlockState();
        if (node.visual().flatMap(Visual::block).filter(state::equals).isEmpty()) {
            node.setVisual(Visual.ofBlock(state));
        }
        node.scaleTo(8.0F / Math.max(1.0F, 16.0F - clearance * 2.0F), 0.0F);
        float own = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                .toYRot() : 0.0F;
        node.rotateTo(0.0F, world - own, 0.0F, 0.0F);
    }

    /** A thing is done where it lies: a glint and a little steam, and a soft chime for the cook. */
    private void done(StationView view, int rack, int place) {
        Vector3f at = placeAt(worldTurn(view) - 90.0F * place).add(8.0F, rackY(rack) + 2.0F, 8.0F);
        view.spawnParticles(DiegesisParticles.GLINT, at, new Vector3f(0.0F, 2.0F, 0.0F), 1.2F, 3, GLINT_COLOR);
        view.spawnParticles(DiegesisParticles.STEAM, at, new Vector3f(0.0F, 3.0F, 0.0F), 1.0F, 2, STEAM_COLOR);
        if (view.isFocused()) {
            view.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), "pot", 0.25F, 1.4F);
        }
    }

    /** The water in the pot, as the kettle's: the biome's water, half darkened in iron. */
    private void water(StationView view, SteamerTower tower) {
        view.node("water").ifPresent(node -> {
            boolean wet = tower.potBlock().handson$hasWater();
            node.setVisible(wet);
            if (wet) {
                int color = KitchenLooks.mix(BiomeColors.getAverageWaterColor(view.level(), view.pos()), WATER_IN_IRON, 0.5F);
                node.setLiquidColor(WATER_ALPHA << 24 | color & 0xFFFFFF);
                node.setAgitation(tower.potBlock().handson$isHeated() ? 0.08F : 0.0F);
            }
        });
    }

    /** Steam rises through the open rack while the pot boils under the tower. */
    private void steam(StationView view, SteamerTower tower, float dt, int open) {
        KitchenSteamer pot = tower.potBlock();
        if (!pot.handson$isHeated() || !pot.handson$hasWater() || lidOff <= 0.0F && tower.isCapped()) {
            return;
        }
        steam += dt * STEAM_RATE;
        while (steam >= 1.0F) {
            steam -= 1.0F;
            float top = open < 0 ? POT_TOP : rackY(open) + RACK_HEIGHT;
            Vector3f at = new Vector3f(8.0F + randomly(4.5F), top - 1.0F, 8.0F + randomly(4.5F));
            view.spawnParticles(DiegesisParticles.STEAM, at, new Vector3f(randomly(0.3F), 3.0F, randomly(0.3F)), 0.4F, 1, STEAM_COLOR);
        }
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
        String node = click.node();
        if ("book_open".equals(node)) {
            book.click(view, click.button());
            return;
        }
        SteamerTower tower = tower(view);
        if (tower == null || node == null) {
            return;
        }
        int rack = rackOf(node);
        if (node.startsWith("rack_")) {
            // A rack on the tower or on the stack aside: that very rack opens.
            openAt(view, tower, rack);
            return;
        }
        int place = placeOf(node);
        if (rack < 0 || place < 0) {
            return;
        }
        if (click.button() == 1) {
            view.sendAction(SteamerStation.ACTION_BACK, new CompoundTag());
            return;
        }
        CompoundTag how = new CompoundTag();
        how.putInt(SteamerStation.RACK, rack);
        how.putInt(SteamerStation.PLACE, place);
        how.putBoolean(SteamerStation.ALL, Screen.hasShiftDown());
        view.sendAction(SteamerStation.ACTION_TAKE, how);
    }

    private static int rackOf(String node) {
        if (!node.startsWith("rack_") && !node.startsWith("place_")) {
            return -1;
        }
        String[] parts = node.split("_");
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException notOne) {
            return -1;
        }
    }

    private static int placeOf(String node) {
        if (!node.startsWith("place_")) {
            return -1;
        }
        String[] parts = node.split("_");
        try {
            return parts.length == 3 ? Integer.parseInt(parts[2]) : -1;
        } catch (NumberFormatException notOne) {
            return -1;
        }
    }

    /**
     * The wheel goes down and up the tower: down opens the rack under the open one (the racks over it lifted aside), up the one over
     * it, up to the top rack with nothing lifted. Over the open recipe book it goes through the book instead.
     */
    @Override
    public boolean onScroll(StationView view, String node, double amount) {
        if ("book_open".equals(node) && book.scroll(amount)) {
            return true;
        }
        SteamerTower tower = tower(view);
        if (tower == null || tower.racks() < 2 || amount == 0.0) {
            return tower != null;
        }
        openAt(view, tower, openRack(view, tower) + (amount > 0.0 ? 1 : -1));
        return true;
    }

    /** Opens rack {@code rack} (clamped to the tower): the racks over it go onto the stack aside, the ones under it come back. */
    private static void openAt(StationView view, SteamerTower tower, int rack) {
        int next = Mth.clamp(rack, 0, tower.racks() - 1);
        if (next == openRack(view, tower)) {
            return;
        }
        CompoundTag how = new CompoundTag();
        how.putInt(SteamerStation.RACK, next >= tower.racks() - 1 ? SteamerStation.TOP : next);
        view.sendAction(SteamerStation.ACTION_OPEN, how);
    }

    /**
     * A stack let go over the tower: food onto the place under it (with shift onto every free place of the open rack), or onto the open rack's first
     * free place when it was let go elsewhere; a rack on top, the lid on, water into the
     * pot. The book takes its click.
     */
    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if ("book_open".equals(node)) {
            book.click(view, button);
            return true;
        }
        SteamerTower tower = tower(view);
        if (tower == null) {
            return false;
        }
        String action = action(tower.potBlock(), stack);
        if (node != null && node.startsWith("rack_") && (action == null || action.equals(SteamerStation.ACTION_PUT))) {
            // Food goes onto the open rack: a click on another rack opens that one first, the food stays on the cursor.
            openAt(view, tower, rackOf(node));
            return true;
        }
        if (action == null) {
            CompoundTag how = new CompoundTag();
            view.sendAction(SteamerStation.ACTION_PUT, how);
            return true;
        }
        CompoundTag how = new CompoundTag();
        if (action.equals(SteamerStation.ACTION_PUT)) {
            int rack = node == null ? -1 : rackOf(node);
            int place = node == null ? -1 : placeOf(node);
            int open = openRack(view, tower);
            // On a place of the open rack, there; anywhere else, the open rack's first free place.
            how.putInt(SteamerStation.RACK, place >= 0 ? rack : open);
            how.putInt(SteamerStation.PLACE, place >= 0 && rack == open ? place : -1);
            how.putBoolean(SteamerStation.ALL, shift);
        }
        view.sendAction(action, how);
        return true;
    }

    /** Shift + click in the inventory: food onto every free place of the open rack, a rack on top, the lid on, water into the pot. */
    @Override
    public boolean onInventoryShiftClick(StationView view, int slot, ItemStack stack) {
        SteamerTower tower = tower(view);
        String action = stack.isEmpty() || tower == null ? null : action(tower.potBlock(), stack);
        if (action == null) {
            return false;
        }
        CompoundTag how = new CompoundTag();
        how.putInt(SteamerStation.FROM, slot);
        how.putBoolean(SteamerStation.ALL, true);
        view.sendAction(action, how);
        return true;
    }

    @Override
    public void onEffect(StationView view, ResourceLocation effect, String node, CompoundTag data) {
        if (SteamerStation.EFFECT_IN.equals(effect)) {
            in(view, data.getList(SteamerStation.LAID, Tag.TAG_COMPOUND));
        } else if (SteamerStation.EFFECT_TAKEN.equals(effect)) {
            taken(view, data.getList(SteamerStation.LAID, Tag.TAG_COMPOUND));
        } else if (SteamerStation.EFFECT_REFUSED.equals(effect)) {
            refused(view, ItemStack.parseOptional(view.level().registryAccess(), data.getCompound(SteamerStation.STACK)));
        } else if (SteamerStation.EFFECT_RACK.equals(effect)) {
            int rack = data.getInt(SteamerStation.RACK);
            if (rack >= 0 && rack < RACKS) {
                droppedAt[rack] = clock;
            }
            view.playSound(SoundEvents.BAMBOO_PLACE, "pot", 0.6F, 1.0F);
        } else if (SteamerStation.EFFECT_LID.equals(effect)) {
            view.playSound(SoundEvents.WOOD_PLACE, "pot", 0.5F, 1.2F);
        } else if (SteamerStation.EFFECT_WATER.equals(effect)) {
            view.spawnParticles(DiegesisParticles.SPLASH, new Vector3f(8.0F, 6.0F, 8.0F), new Vector3f(0.0F, 6.0F, 0.0F), 1.5F, 6,
                    SPLASH_COLOR);
            view.playSound(SoundEvents.BUCKET_EMPTY, "pot", 0.6F, 1.0F);
        }
    }

    /** Things came on: each comes down from the cook onto its place, one after the other. */
    private void in(StationView view, ListTag went) {
        float at = Math.max(0.0F, busyUntil - clock);
        int count = went.size();
        float world = worldTurn(view);
        for (int i = 0; i < count; i++) {
            CompoundTag one = went.getCompound(i);
            int rack = one.getInt(SteamerStation.RACK);
            int place = one.getInt(SteamerStation.PLACE);
            ItemStack stack = ItemStack.parseOptional(view.level().registryAccess(), one.getCompound(SteamerStation.STACK));
            if (rack < 0 || rack >= RACKS || place < 0 || place >= PLACES) {
                continue;
            }
            int index = i;
            float begins = at + i * NEXT;
            flyingUntil[rack][place] = clock + begins + FLY_TIME;
            view.node("place_" + rack + "_" + place).ifPresent(node -> {
                node.setVisible(false);
                after(begins, () -> {
                    float yaw = world - 90.0F * place;
                    Vector3f rest = placeAt(yaw);
                    Vector3f from = FromPlayer.offset(view, rackY(rack) + FLY_FROM, index, count);
                    YoukaiPotBehavior.show(node, stack, ITEM_SIZE);
                    node.setTint(0xFFFFFFFF);
                    node.rotateTo(0.0F, yaw, 0.0F, 0.0F);
                    // Its own size again: the place may have shown the stack see-through, as the item laid flat.
                    node.scaleTo(1.0F, 0.0F);
                    node.moveTo(rest.x + from.x, FLY_FROM, rest.z + from.z, 0.0F);
                    node.setVisible(true);
                    node.hopTo(rest.x, 0.0F, rest.z, 2.0F, FLY_TIME);
                    shown[rack][place] = stack.copy();
                    previewing[rack][place] = false;
                    after(FLY_TIME, () -> view.playSound(SoundEvents.BAMBOO_HIT, "pot", 0.25F, 1.6F
                            + random.nextFloat() * 0.2F));
                });
            });
        }
        busyUntil = clock + at + count * NEXT + FLY_TIME;
    }

    /** Things went to the cook: each lifts off its place and goes, the drawer shows it coming. */
    private void taken(StationView view, ListTag went) {
        float world = worldTurn(view);
        for (int i = 0; i < went.size(); i++) {
            CompoundTag one = went.getCompound(i);
            int rack = one.getInt(SteamerStation.RACK);
            int place = one.getInt(SteamerStation.PLACE);
            ItemStack stack = ItemStack.parseOptional(view.level().registryAccess(), one.getCompound(SteamerStation.STACK));
            if (rack < 0 || rack >= RACKS || place < 0 || place >= PLACES) {
                continue;
            }
            Vector3f at = placeAt(world - 90.0F * place).add(0.0F, rackY(rack) + 1.2F - 8.0F, 0.0F);
            int delay = i;
            after(delay * 0.05F, () -> ghost(view, stack).ifPresent(ghost -> {
                Vector3f away = FromPlayer.offset(view, rackY(rack) + FLY_FROM, 0, 1);
                ghost.moveTo(at.x, at.y, at.z, 0.0F);
                ghost.rotateTo(0.0F, world - 90.0F * place, 0.0F, 0.0F);
                ghost.hopTo(at.x + away.x, at.y + FLY_FROM, at.z + away.z, 1.5F, AWAY_TIME);
                ghost.scaleTo(0.3F, AWAY_TIME);
            }));
            view.node("place_" + rack + "_" + place).ifPresent(node -> node.setVisible(false));
            shown[rack][place] = ItemStack.EMPTY;
            leaving[rack][place] = stack.copy();
            leavingUntil[rack][place] = clock + LEAVING_TIME;
        }
        view.playSound(SoundEvents.ITEM_PICKUP, "pot", 0.3F, 1.5F);
    }

    /** What the steamer would not take comes down to the open rack and bounces back to the cook. */
    private void refused(StationView view, ItemStack stack) {
        SteamerTower tower = tower(view);
        if (stack.isEmpty() || tower == null) {
            return;
        }
        float top = rackY(Math.max(0, openRack(view, tower))) + RACK_HEIGHT - 8.0F;
        ghost(view, stack).ifPresent(ghost -> {
            Vector3f from = FromPlayer.offset(view, top + 8.0F + FLY_FROM, 0, 1);
            ghost.moveTo(from.x, top + FLY_FROM, from.z, 0.0F);
            ghost.hopTo(from.x * 0.25F, top + 1.0F, from.z * 0.25F, 1.5F, 0.16F);
            after(0.16F, () -> {
                ghost.hopTo(from.x, top + FLY_FROM, from.z, 1.5F, 0.22F);
                ghost.scaleTo(0.4F, 0.22F);
            });
        });
        view.playSound(SoundEvents.BAMBOO_HIT, "pot", 0.4F, 0.8F);
    }

    private Optional<NodeView> ghost(StationView view, ItemStack stack) {
        ghost = (ghost + 1) % LIFTS;
        Optional<NodeView> node = view.node("lift_" + ghost);
        node.ifPresent(it -> {
            YoukaiPotBehavior.show(it, stack, ITEM_SIZE);
            it.rotateTo(0.0F, 0.0F, 0.0F, 0.0F);
            it.scaleTo(1.0F, 0.0F);
            it.moveTo(0.0F, 0.0F, 0.0F, 0.0F);
            it.setVisible(true);
        });
        return node;
    }

    /**
     * The badge while something steams on the open rack: what is nearest done, a flame lit over heat (grey, blinking, without), a
     * drop lit with water in the pot (blinking without), the bar of how far it is and the seconds left at the steam it gets now.
     */
    private void badge(StationView view, SteamerTower tower, int open, boolean here) {
        int bestRack = -1;
        int bestPlace = -1;
        float bestLeft = Float.MAX_VALUE;
        for (int place = 0; place < PLACES && open >= 0; place++) {
            float steamed = tower.steamed(open, place);
            if (steamed < 0.0F) {
                continue;
            }
            float left = (1.0F - steamed) * tower.steamTime(open, place);
            if (left < bestLeft) {
                bestLeft = left;
                bestRack = open;
                bestPlace = place;
            }
        }
        boolean shown = here && bestRack >= 0;
        view.node("badge").ifPresent(plate -> plate.setVisible(shown));
        KitchenSteamer pot = tower.potBlock();
        boolean heated = pot.handson$isHeated();
        boolean wet = pot.handson$hasWater();
        // The steam it gets now; just set over the fire the tower has none yet, and it will get at least what rises from the pot.
        float perTick = heated && wet ? Math.max(tower.heat(Math.max(0, open)), MIN_HEAT) : 0.0F;
        int seconds = shown && perTick > 0.0F ? Math.max(1, Mth.ceil(bestLeft / perTick / 20.0F)) : 0;
        float steamed = shown ? tower.steamed(bestRack, bestPlace) : 0.0F;
        int bar = shown ? Math.round(steamed * BAR_LENGTH) : 0;
        String text = seconds >= 60 ? seconds / 60 + ":" + String.format("%02d", seconds % 60)
                : seconds > 0 ? Integer.toString(seconds) : "";
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
            YoukaiPotBehavior.text(lit, text);
            view.node("dial").ifPresent(node -> node.setShape(bar > 0 || !text.isEmpty() ? lit.build() : null, BADGE_TEXTURE, true));
        }
        boolean blink = Mth.sin(clock * 6.0F) > 0.0F;
        view.node("dial_track").ifPresent(node -> node.setVisible(shown));
        view.node("dial").ifPresent(node -> node.setVisible(shown));
        view.node("flame").ifPresent(node -> node.setVisible(shown && !heated && blink));
        view.node("flame_lit").ifPresent(node -> node.setVisible(shown && heated));
        view.node("drop").ifPresent(node -> node.setVisible(shown && !wet && blink));
        view.node("drop_lit").ifPresent(node -> node.setVisible(shown && wet));
        ItemStack thing = shown ? tower.item(bestRack, bestPlace) : ItemStack.EMPTY;
        view.node("preview").ifPresent(node -> {
            node.setVisible(shown);
            if (!shown) {
                return;
            }
            if (badgeFor != thing.getItem()) {
                badgeFor = thing.getItem();
                badgeIcon = SteamerStation.recipes(view.level()).stream().filter(recipe -> recipe.ingredient().test(thing))
                        .map(SteamerStation.Steaming::result).findFirst().orElse(thing);
                node.setVisual(Visual.ofItem(badgeIcon.getItem()).withAnchor(ICON_MIDDLE));
            }
            node.setTooltip(seconds > 0 ? Component.translatable("handson_delight.pot.cooking", badgeIcon.getHoverName(), seconds)
                    : badgeIcon.getHoverName());
        });
    }
}
