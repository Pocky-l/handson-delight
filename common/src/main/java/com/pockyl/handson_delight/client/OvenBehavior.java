package com.pockyl.handson_delight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.MeshShape;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.handson_delight.station.KitchenOven;
import com.pockyl.handson_delight.station.OvenStation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * What the oven shows while somebody works at it: its grated door swung down, the baking tray slid out over it with what bakes laid
 * on it in the oven's grid, the heat under the oven glowing inside. Shut, what bakes is seen through the grate browning and rising,
 * and is a dish with a puff of steam when it is done. While nobody works at it the oven is the block itself, the same models, and the
 * scene draws only what lies on its tray, for everybody near: the station hides the block while it is used and until the door has
 * swung shut behind the last one to leave.
 */
public final class OvenBehavior implements StationBehavior {
    private static final String[] ITEM_NODES = {"item_0", "item_1", "item_2", "item_3", "item_4", "item_5", "item_6", "item_7", "item_8"};
    /** The badge over the oven: its plate, the bar, the flame and the icon. */
    private static final String[] BADGE_PARTS = {"badge", "dial_track", "dial", "flame", "flame_lit", "preview"};
    /** The parts the block's own model has too: drawn by the scene only while the block is hidden. */
    private static final String[] OVEN_PARTS = {"body", "door_left", "door_right", "tray_model"};
    private static final ResourceLocation BADGE_TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "block/white_concrete");
    private static final SoundEvent OPEN_SOUND = SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(KitchenOven.MOD_ID, "block.oven.open"));
    private static final SoundEvent CLOSE_SOUND = SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(KitchenOven.MOD_ID, "block.oven.close"));
    /**
     * The door's two leaves swing open on their hinges at its sides, as gates, then the tray slides out between them; shutting,
     * the tray goes in first. Seconds each; how far the leaves turn, degrees; how far out the tray comes, pixels.
     */
    private static final float DOOR_TIME = 0.4F;
    private static final float TRAY_TIME = 0.35F;
    private static final float DOOR_OPEN = 105.0F;
    private static final float TRAY_OUT = 7.0F;
    /** The tray starts out once the leaves are this far open. */
    private static final float TRAY_AFTER = 0.7F;
    /** The grid's places on the tray, its own pixels from its middle: column 0 on the cook's left, row 0 at the back. */
    private static final float[] COLUMN_X = {2.67F, 0.0F, -2.67F};
    private static final float[] ROW_Z = {1.67F, -1.0F, -3.67F};
    private static final float ON_TRAY = 2.0F;
    /**
     * Things of one kind lie piled on each other, each a little higher and turned a little further, the piles spread over the tray.
     * A pie crust lies in the middle as big as a dish, and its filling is laid on it in layers. Pixels and degrees.
     */
    private static final float PILE_STEP = 0.3F;
    private static final float PILE_TURN = 38.0F;
    private static final float[][][] PILES = {
            {{0.0F, -1.0F}},
            {{1.7F, -1.0F}, {-1.7F, -1.0F}},
            {{2.2F, 0.4F}, {-2.2F, 0.4F}, {0.0F, -2.6F}},
            {{1.9F, 0.6F}, {-1.9F, 0.6F}, {1.9F, -2.6F}, {-1.9F, -2.6F}}};
    private static final float CRUST_SIZE = 1.9F;
    private static final float FILLING_LIFT = 0.35F;
    private static final float FILLING_SIZE = 1.1F;
    private static final float LAYOUT_TIME = 0.25F;
    /** What lies on the tray is drawn as an item lying flat; the dish as its block, at most this big, else as an item. */
    private static final float ITEM_PIXELS = 8.0F;
    private static final float DISH_ITEM = 4.4F;
    private static final float DISH_BLOCK = 7.0F;
    private static final float DISH_BLOCK_SCALE = 0.7F;
    /**
     * Baking, what lies there browns towards a crust's colour and rises a little, the most by the time it is done; a dough rises
     * more. Done, it is the dish.
     */
    private static final int BROWNED = 0xFFB07A48;
    private static final float BROWNING = 0.7F;
    private static final float RISING = 0.12F;
    /**
     * Several things laid at once (a recipe picked in the book) come from the player at the bottom of the screen one after the other
     * ({@link FromPlayer}): seconds between them, seconds in the air, how high they start and how high the arc goes, pixels.
     */
    private static final float ARRIVE_STAGGER = 0.12F;
    private static final float ARRIVE_TIME = 0.4F;
    private static final float ARRIVE_FROM = 9.0F;
    private static final float ARRIVE_ARC = 3.0F;
    /** The heat inside: how see-through the glow is at most and how much it flickers. */
    private static final int GLOW_ALPHA = 0xC0;
    private static final int GLOW_FLICKER = 0x30;
    /** Smoke out of the chimney while it bakes, puffs a second; shimmer out of the open mouth while it is hot. */
    private static final float SMOKE_RATE = 1.4F;
    private static final float SHIMMER_RATE = 2.5F;
    private static final Vector3f CHIMNEY = new Vector3f(8.0F, 16.3F, 8.0F);
    private static final Vector3f MOUTH = new Vector3f(8.0F, 10.5F, 0.5F);
    private static final Vector3f SMOKE_RISE = new Vector3f(0.0F, 4.5F, 0.0F);
    private static final Vector3f STEAM_OUT = new Vector3f(0.0F, 5.0F, -3.0F);
    /** The badge, as the kettle's: plate, flame over the icon, bar, time left; servings ready as "xN". */
    private static final int PLATE_COLOR = 0x8C1E140C;
    private static final int DIAL_COLOR = 0xFFFFCB6B;
    private static final int WAITING_COLOR = 0xFFA8A49C;
    private static final int TRACK_COLOR = 0x50FFF2DC;
    private static final int FLAME_COLOR = 0xFFFF9A3C;
    private static final int FLAME_TOP = -15;
    private static final int TEXT_TOP = 13;
    private static final String[] COLON = {".", "#", ".", "#", "."};
    private static final String[] TIMES = {"...", "#.#", ".#.", "#.#", "..."};
    private static final float DONE_SWELL = 0.35F;
    private static final float DONE_TIME = 0.6F;
    private static final float READY_BREATH = 0.04F;
    private static final Vector3f ICON_MIDDLE = new Vector3f(8.0F, 8.0F, 8.0F);
    private static final int BAR_LEFT = -7;
    private static final int BAR_TOP = 10;
    private static final int BAR_LENGTH = 14;

    private final RecipeBook book = new RecipeBook(new OvenBook());
    private final UsableCache usable = new UsableCache();
    private List<KitchenOven.Baking> recipes = List.of();
    private final ItemStack[] shown = new ItemStack[ITEM_NODES.length];
    /** Where each thing lies, its own node's pixels from its place in the grid, how big and turned how far; as laid out last. */
    private final Vector3f[] laidAt = new Vector3f[ITEM_NODES.length];
    private final float[] laidSize = new float[ITEM_NODES.length];
    private final float[] laidTurn = new float[ITEM_NODES.length];
    /** The items the food nodes and the dish show now. */
    private final Item[] drawnFood = new Item[ITEM_NODES.length];
    private Item drawnServed;
    /** Seconds until each thing sets off from the player, NaN: not coming. */
    private final float[] arriveIn = new float[ITEM_NODES.length];
    private float time;
    /** How far the door's leaves are open, 0 shut to 1; how far the tray is out. */
    private float door;
    private float tray;
    private boolean wasOpening;
    /** The door as the cook last clicked it, shown at once while the oven answers; null once it agrees. */
    private Boolean doorAsked;
    private float doorAskedAt;
    private float smoke;
    private float shimmer;
    private String drawnDial = "";
    private Item shownDish;
    /** What the oven makes of what lies on the tray, or what waits in its output: the badge's icon. */
    private ItemStack dish = ItemStack.EMPTY;
    private int servings;
    private boolean ready;
    private boolean sawBaking;
    private int servedBefore;
    private float doneAt = -10.0F;
    private boolean baking;
    private float tint = -1.0F;

    public OvenBehavior(StationView view) {
        Arrays.fill(shown, ItemStack.EMPTY);
        Arrays.fill(arriveIn, Float.NaN);
        for (int i = 0; i < laidAt.length; i++) {
            laidAt[i] = new Vector3f();
            laidSize[i] = 1.0F;
        }
    }

    @Override
    public void onSceneReady(StationView view) {
        drawnDial = "";
        shownDish = null;
        drawnServed = null;
        Arrays.fill(drawnFood, null);
        tint = -1.0F;
        recipes = KitchenOven.recipes(view.level());
        view.node("dial").ifPresent(dial -> dial.setTint(DIAL_COLOR));
        view.node("dial_track").ifPresent(track -> track.setTint(TRACK_COLOR));
        view.node("flame").ifPresent(flame -> flame.setTint(TRACK_COLOR));
        view.node("flame_lit").ifPresent(flame -> flame.setTint(FLAME_COLOR));
        view.node("badge").ifPresent(plate -> {
            plate.setTint(PLATE_COLOR);
            MeshShape.Builder plateShape = MeshShape.builder();
            PotBehavior.pixels(plateShape, -10, -16, 20, 35);
            PotBehavior.pixels(plateShape, -9, -17, 18, 1);
            PotBehavior.pixels(plateShape, -9, 19, 18, 1);
            plate.setShape(plateShape.build(), BADGE_TEXTURE, true);
        });
        MeshShape.Builder flame = MeshShape.builder();
        glyph(flame, PotBehavior.FLAME, -2, FLAME_TOP);
        MeshShape flameShape = flame.build();
        view.node("flame").ifPresent(node -> node.setShape(flameShape, BADGE_TEXTURE, true));
        view.node("flame_lit").ifPresent(node -> node.setShape(flameShape, BADGE_TEXTURE, true));
        for (String part : BADGE_PARTS) {
            view.node(part).ifPresent(node -> node.setOnTop(true));
        }
        view.node("book_open").ifPresent(node -> node.setPainter(book));
        refresh(view);
    }

    @Override
    public void onOpen(StationView view) {
        recipes = KitchenOven.recipes(view.level());
        usable.clear();
        view.setInventoryFilter(stack -> usable.test(stack, this::usable));
        book.opened(view);
        servedBefore = view.state().getItem(OvenStation.SERVED_SLOT).getCount();
        // What the cook came with is what they meant to bake: a right click with food lays it on the tray, never slower.
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && view.openedByUse() && player.inventoryMenu.getCarried().isEmpty() && usable(player.getMainHandItem())) {
            lay(view, player.getInventory().selected);
        }
        refresh(view);
    }

    @Override
    public void onClose(StationView view) {
        book.shut(view);
        refresh(view);
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        refresh(view);
    }

    /** The door is still swinging shut after the last one left: the scene stands in for the block until it is shut. */
    @Override
    public boolean holdsBlock(StationView view) {
        return !view.isInUse() && (door > 0.0F || tray > 0.0F);
    }

    /** What the inventory at the oven shows: what goes into any of its recipes. */
    private boolean usable(ItemStack stack) {
        return !stack.isEmpty() && recipes.stream().anyMatch(recipe -> recipe.needed().stream().anyMatch(ingredient -> ingredient.test(stack)));
    }

    /** A shift click in the inventory lays the whole stack on the tray. */
    @Override
    public boolean onInventoryShiftClick(StationView view, int slot, ItemStack stack) {
        if (!usable(stack)) {
            return false;
        }
        lay(view, slot);
        return true;
    }

    private void lay(StationView view, int slot) {
        CompoundTag how = new CompoundTag();
        how.putInt(OvenStation.FROM, slot);
        view.sendAction(OvenStation.ACTION_LAY, how);
        if (!isDoorOpen(view) && view.state().getControl(OvenStation.PROGRESS) <= 0.0F) {
            askDoor(view, true);
        }
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Click click && click.node().startsWith("door_") && click.button() == 0) {
            askDoor(view, !isDoorOpen(view));
        } else if (event instanceof SceneEvent.Click click && click.node().equals("book_open")) {
            book.click(view);
        }
    }

    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if ("book_open".equals(node)) {
            book.click(view);
            return true;
        }
        if (node.startsWith("door_")) {
            askDoor(view, !isDoorOpen(view));
            return true;
        }
        return false;
    }

    /** Whether the door is open: as the cook clicked it, until the oven says so too. */
    private boolean isDoorOpen(StationView view) {
        boolean open = view.state().getControl(OvenStation.DOOR) > 0.5F;
        if (doorAsked != null && (doorAsked == open || time - doorAskedAt > 1.0F)) {
            doorAsked = null;
        }
        return doorAsked != null ? doorAsked : open;
    }

    private void askDoor(StationView view, boolean open) {
        doorAsked = open;
        doorAskedAt = time;
        CompoundTag data = new CompoundTag();
        data.putBoolean(OvenStation.OPEN, open);
        view.sendAction(OvenStation.ACTION_DOOR, data);
    }

    private void refresh(StationView view) {
        StationState state = view.state();
        ItemStack[] items = state.getItems(OvenStation.INGREDIENTS);
        // Things shared out over the tray come from where they lay, not from the player: only more on the tray is an arrival.
        boolean more = Arrays.stream(items).mapToInt(ItemStack::getCount).sum() > Arrays.stream(shown).mapToInt(ItemStack::getCount).sum();
        List<Integer> arrivals = new ArrayList<>();
        for (int i = 0; i < ITEM_NODES.length && i < items.length; i++) {
            if (!items[i].isEmpty() && shown[i].isEmpty() && view.isFocused() && more) {
                arrivals.add(i);
            }
            if (items[i].isEmpty()) {
                arriveIn[i] = Float.NaN;
            }
            shown[i] = items[i].copy();
        }
        layOut(view);
        if (arrivals.size() > 1) {
            for (int k = 0; k < arrivals.size(); k++) {
                int index = arrivals.get(k);
                Optional<Vector3f> from = FromPlayer.point(view, ON_TRAY + ARRIVE_FROM, k, arrivals.size());
                if (from.isPresent()) {
                    arriveIn[index] = k * ARRIVE_STAGGER;
                    Vector3f start = offsetOf(view, index, from.get());
                    view.node(ITEM_NODES[index]).ifPresent(node -> {
                        node.setVisible(false);
                        node.moveTo(start.x, start.y, start.z, 0.0F);
                    });
                }
            }
        } else {
            arrivals.forEach(index -> view.playSound(SoundEvents.CHAIN_PLACE, ITEM_NODES[index], 0.25F, 1.5F + 0.04F * index));
        }
        badgeState(view);
    }

    /** The scene point {@code at} as an offset of the thing's node from its place: the tray's own frame, the slot's place taken off. */
    private static Vector3f offsetOf(StationView view, int index, Vector3f at) {
        Optional<NodeView> tray = view.node("tray");
        Vector3f local = tray.map(node -> new Matrix4f(node.sceneMatrix()).invert().transformPosition(new Vector3f(at)))
                .orElse(new Vector3f(at).sub(8.0F, 0.0F, 8.0F));
        return local.sub(COLUMN_X[index % 3], ON_TRAY, ROW_Z[index / 3]);
    }

    /**
     * Where each thing lies: things of one kind piled on each other, the piles spread over the tray; with a pie crust among them,
     * the crust in the middle as big as the dish and everything else laid on it in layers.
     */
    private void layOut(StationView view) {
        int crust = -1;
        List<Item> kinds = new ArrayList<>();
        for (int i = 0; i < shown.length; i++) {
            if (shown[i].isEmpty()) {
                continue;
            }
            if (crust < 0 && isCrust(shown[i])) {
                crust = i;
            } else if (!kinds.contains(shown[i].getItem())) {
                kinds.add(shown[i].getItem());
            }
        }
        int[] layers = new int[Math.max(1, kinds.size())];
        int filling = 0;
        for (int i = 0; i < shown.length; i++) {
            Vector3f home = new Vector3f(COLUMN_X[i % 3], 0.0F, ROW_Z[i / 3]);
            Vector3f at = new Vector3f();
            float size = 1.0F;
            float turn = 0.0F;
            if (shown[i].isEmpty()) {
                at.set(0.0F, 0.0F, 0.0F);
            } else if (crust >= 0 && i == crust) {
                at.set(-home.x, 0.0F, -1.0F - home.z);
                size = CRUST_SIZE;
            } else if (crust >= 0) {
                at.set(-home.x, FILLING_LIFT + PILE_STEP * filling, -1.0F - home.z);
                size = FILLING_SIZE;
                turn = 15.0F + PILE_TURN * filling++;
            } else {
                int kind = kinds.indexOf(shown[i].getItem());
                float[] place = kinds.size() <= PILES.length ? PILES[kinds.size() - 1][kind]
                        : new float[] {COLUMN_X[kind % 3], ROW_Z[kind / 3]};
                int layer = layers[kind]++;
                at.set(place[0] - home.x, PILE_STEP * layer, place[1] - home.z);
                turn = PILE_TURN * layer;
            }
            if (!at.equals(laidAt[i], 0.01F) || laidSize[i] != size || laidTurn[i] != turn) {
                laidAt[i].set(at);
                laidSize[i] = size;
                laidTurn[i] = turn;
                Vector3f to = at;
                float scale = size;
                float angle = turn;
                view.node(ITEM_NODES[i]).ifPresent(node -> {
                    node.moveTo(to.x, to.y, to.z, LAYOUT_TIME);
                    node.scaleTo(scale, LAYOUT_TIME);
                    node.rotateTo(0.0F, angle, 0.0F, LAYOUT_TIME);
                });
            }
            food(view, i);
        }
        served(view);
    }

    /** The thing in place {@code index}, drawn by its food node: an item lying flat, tinted as it browns. */
    private void food(StationView view, int index) {
        Item item = shown[index].isEmpty() ? null : shown[index].getItem();
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
     * What was baked lies on the tray: a dish that is a block (a pie, a cake) as its own block model, sized to the tray; anything else,
     * or a block with no model of its own, as the item lying flat.
     */
    private void served(StationView view) {
        ItemStack stack = view.state().getItem(OvenStation.SERVED_SLOT);
        Item item = stack.isEmpty() ? null : stack.getItem();
        if (item == drawnServed) {
            return;
        }
        drawnServed = item;
        view.node("dish").ifPresent(node -> {
            node.setVisible(item != null);
            if (item == null) {
                return;
            }
            float extent = blockExtent(item);
            if (extent > 0.0F) {
                BlockState state = ((BlockItem) item).getBlock().defaultBlockState();
                node.setVisual(Visual.ofBlock(state));
                node.rotateTo(0.0F, 0.0F, 0.0F, 0.0F);
                node.scaleTo(Math.min(DISH_BLOCK_SCALE, DISH_BLOCK / extent), 0.0F);
                node.moveTo(0.0F, 0.0F, 0.0F, 0.0F);
            } else {
                node.setVisual(Visual.ofItem(item).withAnchor(ICON_MIDDLE));
                node.rotateTo(-90.0F, 0.0F, 0.0F, 0.0F);
                node.scaleTo(DISH_ITEM / ITEM_PIXELS, 0.0F);
                node.moveTo(0.0F, 0.15F, 0.0F, 0.0F);
            }
        });
    }

    /** How wide the block of a block item lies, pixels, when it is drawn as a model of its own; 0 for anything else. */
    private static float blockExtent(Item item) {
        if (!(item instanceof BlockItem blockItem)) {
            return 0.0F;
        }
        BlockState state = blockItem.getBlock().defaultBlockState();
        if (state.getRenderShape() != RenderShape.MODEL) {
            return 0.0F;
        }
        VoxelShape shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        if (shape.isEmpty()) {
            return 0.0F;
        }
        AABB bounds = shape.bounds();
        return (float) Math.max(bounds.getXsize(), bounds.getZsize()) * 16.0F;
    }

    private static boolean isCrust(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("pie_crust");
    }

    /** What the badge says: what bakes or waits, how many, whether it was just done. */
    private void badgeState(StationView view) {
        StationState state = view.state();
        float progress = state.getControl(OvenStation.PROGRESS);
        ItemStack served = state.getItem(OvenStation.SERVED_SLOT);
        Optional<KitchenOven.Baking> making = KitchenOven.matching(recipes, state.getItems(OvenStation.INGREDIENTS));
        baking = making.isPresent();
        servings = 0;
        ready = false;
        if (making.isPresent()) {
            dish = making.get().result();
        } else if (!served.isEmpty()) {
            dish = served;
            servings = served.getCount();
            ready = true;
        } else {
            dish = ItemStack.EMPTY;
        }
        if (progress > 0.0F) {
            sawBaking = true;
        }
        if (served.getCount() > servedBefore && sawBaking) {
            sawBaking = progress > 0.0F;
            done(view);
        }
        servedBefore = served.getCount();
    }

    /** Something is done: a puff of steam out of the oven and, at the station, the badge swells with a chime. */
    private void done(StationView view) {
        doneAt = time;
        view.spawnParticles(DiegesisParticles.STEAM, MOUTH, STEAM_OUT, 1.6F, 10, 0xB0FFFFFF);
        if (view.isInUse()) {
            view.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), "badge", 0.35F, 1.26F);
            view.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), "badge", 0.3F, 1.5F);
            view.spawnParticles(DiegesisParticles.GLINT, "served", 6, 0xFFFFE6A0);
        }
    }

    @Override
    public void frame(StationView view, float dt) {
        time += dt;
        boolean inUse = view.isInUse();
        boolean open = inUse && isDoorOpen(view);
        swing(view, dt, open);
        // The oven's own parts exactly while the block is left out of the world: the two swap in one frame. What lies on the tray
        // is the scene's alone and always shows, in the block as at the station.
        boolean whole = view.isBlockHidden();
        for (String part : OVEN_PARTS) {
            view.node(part).ifPresent(node -> node.setVisible(whole));
        }
        boolean heated = KitchenOven.isHeated(view.level().getBlockState(view.pos()));
        boolean reachable = open && tray >= 1.0F;
        for (int i = 0; i < ITEM_NODES.length; i++) {
            arrive(view, i, dt);
            boolean enabled = reachable;
            view.node(ITEM_NODES[i]).ifPresent(node -> node.setEnabled(enabled));
        }
        view.node("served").ifPresent(node -> node.setEnabled(reachable));
        view.node("mouth").ifPresent(node -> node.setEnabled(reachable));
        float progress = view.state().getControl(OvenStation.PROGRESS);
        brown(view, baking && progress > 0.0F && !open ? progress : 0.0F);
        heat(view, dt, heated, open, baking && progress > 0.0F && !open);
        book.frame(view, dt);
        float appear = RecipeBook.appear(view);
        badge(view, inUse && RecipeBook.isWorkedAt(view) && appear > RecipeBook.SPECK, heated, open);
        float swell = badgeSwell();
        for (String part : BADGE_PARTS) {
            view.node(part).ifPresent(node -> node.scaleTo(appear * swell, 0.0F));
        }
    }

    /** The door goes down and the tray comes out after it; shutting, the tray goes in first and the door comes up after it. */
    private void swing(StationView view, float dt, boolean open) {
        float before = door;
        if (open) {
            door = Math.min(1.0F, door + dt / DOOR_TIME);
            if (door >= TRAY_AFTER) {
                tray = Math.min(1.0F, tray + dt / TRAY_TIME);
            }
        } else {
            tray = Math.max(0.0F, tray - dt / TRAY_TIME);
            if (tray <= 0.0F) {
                door = Math.max(0.0F, door - dt / DOOR_TIME);
            }
        }
        if (open && !wasOpening && before < 1.0F) {
            view.playSound(OPEN_SOUND, "door_left", 0.5F, 0.95F + view.level().random.nextFloat() * 0.1F);
        }
        if (before > 0.0F && door <= 0.0F) {
            view.playSound(CLOSE_SOUND, "door_left", 0.5F, 0.95F + view.level().random.nextFloat() * 0.1F);
        }
        if (before < 1.0F && door >= 1.0F) {
            // The leaves come to rest on their hinges with a small knock of iron.
            view.playSound(SoundEvents.CHAIN_HIT, "door_left", 0.3F, 1.3F);
        }
        wasOpening = open;
        float angle = DOOR_OPEN * KitchenMotions.smooth(door);
        // Outwards, towards the cook.
        view.node("door_left").ifPresent(node -> node.rotateTo(0.0F, angle, 0.0F, 0.0F));
        view.node("door_right").ifPresent(node -> node.rotateTo(0.0F, -angle, 0.0F, 0.0F));
        float out = TRAY_OUT * KitchenMotions.smooth(tray);
        view.node("tray").ifPresent(node -> node.moveTo(0.0F, 0.0F, -out, 0.0F));
    }

    /** A thing on its way in from the player waits its turn hidden, then hops from where it set off to where it lies. */
    private void arrive(StationView view, int index, float dt) {
        if (Float.isNaN(arriveIn[index])) {
            return;
        }
        arriveIn[index] -= dt;
        if (arriveIn[index] > 0.0F) {
            return;
        }
        arriveIn[index] = Float.NaN;
        view.node(ITEM_NODES[index]).ifPresent(node -> {
            node.setVisible(true);
            Vector3f to = laidAt[index];
            node.hopTo(to.x, to.y, to.z, ARRIVE_ARC, ARRIVE_TIME);
        });
        view.playSound(SoundEvents.CHAIN_PLACE, ITEM_NODES[index], 0.2F, 1.5F + 0.04F * index);
    }

    /** What bakes browns and rises as it bakes; anything else lies as it is. */
    private void brown(StationView view, float progress) {
        float k = KitchenMotions.smooth(progress);
        if (Math.abs(k - tint) < 0.01F) {
            return;
        }
        tint = k;
        int color = blend(0xFFFFFFFF, BROWNED, BROWNING * k);
        for (int i = 0; i < ITEM_NODES.length; i++) {
            float size = laidSize[i] * (1.0F + RISING * k);
            view.node("food_" + i).ifPresent(node -> node.setTint(color));
            view.node(ITEM_NODES[i]).ifPresent(node -> node.scaleTo(size, 0.0F));
        }
    }

    /**
     * The heat under the oven: its glow inside, flickering; out of the open mouth the hot air shimmers; while it bakes shut, smoke
     * rises from the chimney.
     */
    private void heat(StationView view, float dt, boolean heated, boolean open, boolean bakingShut) {
        boolean shown = view.isBlockHidden();
        // Drawn by the scene alone, in the world as at the station: the block's model has no glow, which could not flicker.
        view.node("glow").ifPresent(node -> {
            node.setVisible(heated);
            float flicker = 0.5F + 0.3F * Mth.sin(time * 7.3F) + 0.2F * Mth.sin(time * 13.1F + 1.0F);
            node.setTint((GLOW_ALPHA - Math.round(GLOW_FLICKER * flicker)) << 24 | 0xFFFFFF);
        });
        if (!heated) {
            return;
        }
        if (shown && open && door >= 1.0F) {
            shimmer += dt * SHIMMER_RATE;
            if (shimmer >= 1.0F) {
                view.spawnParticles(HandsOnDelightClient.HEAT, MOUTH, new Vector3f(0.0F, 3.0F, -0.5F), 2.5F, (int) shimmer, 0x48FFFFFF);
                shimmer -= (int) shimmer;
            }
        }
        if (bakingShut) {
            smoke += dt * SMOKE_RATE;
            if (smoke >= 1.0F) {
                view.spawnParticles(HandsOnDelightClient.PAN_SMOKE, CHIMNEY, SMOKE_RISE, 1.2F, (int) smoke, 0x58C8C6C2);
                smoke -= (int) smoke;
            }
        }
    }

    float badgeSwell() {
        float since = time - doneAt;
        if (since >= 0.0F && since < DONE_TIME) {
            return 1.0F + DONE_SWELL * Mth.sin(Mth.PI * since / DONE_TIME);
        }
        return ready ? 1.0F + READY_BREATH * (0.5F - 0.5F * Mth.cos(time * 2.4F)) : 1.0F;
    }

    /**
     * The badge while something bakes or waits: what it makes, whether it is heated, how far it has baked and the time left, or how
     * many are ready. Bar and time go grey while the door is open and baking waits.
     */
    private void badge(StationView view, boolean cookHere, boolean heated, boolean open) {
        boolean shown = cookHere && !dish.isEmpty();
        view.node("badge").ifPresent(plate -> plate.setVisible(shown));
        float progress = view.state().getControl(OvenStation.PROGRESS);
        int left = view.state().data().getInt(OvenStation.LEFT);
        int seconds = baking && progress > 0.0F && left > 0 ? Math.max(1, Mth.ceil(left / 20.0F)) : 0;
        int bar = baking ? Math.round(progress * BAR_LENGTH) : ready ? BAR_LENGTH : 0;
        String text = seconds >= 60 ? seconds / 60 + ":" + String.format("%02d", seconds % 60) : seconds > 0 ? Integer.toString(seconds)
                : !baking && servings > 0 ? "x" + Math.min(servings, 99) : "";
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
        boolean waiting = open && progress > 0.0F;
        view.node("dial").ifPresent(node -> node.setTint(waiting ? WAITING_COLOR : DIAL_COLOR));
        view.node("dial_track").ifPresent(node -> node.setVisible(shown));
        view.node("dial").ifPresent(node -> node.setVisible(shown));
        // The flame: lit over the heat; grey and blinking while what lies there waits for heat; none for what only waits to be taken.
        view.node("flame").ifPresent(node -> node.setVisible(shown && baking && !heated && Mth.sin(time * 6.0F) > 0.0F));
        view.node("flame_lit").ifPresent(node -> node.setVisible(shown && baking && heated));
        preview(view, shown, seconds);
    }

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

    /** The icon of what bakes or waits, with its name and the seconds left on hover. */
    private void preview(StationView view, boolean shown, int seconds) {
        view.node("preview").ifPresent(node -> {
            node.setVisible(shown);
            if (!shown) {
                return;
            }
            if (shownDish != dish.getItem()) {
                shownDish = dish.getItem();
                node.setVisual(Visual.ofItem(shownDish).withAnchor(ICON_MIDDLE));
            }
            node.setTooltip(seconds > 0 ? Component.translatable("handson_delight.pot.cooking", dish.getHoverName(), seconds) : dish.getHoverName());
        });
    }

    private static int blend(int from, int to, float amount) {
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            result |= Math.round(Mth.lerp(amount, from >>> shift & 0xFF, to >>> shift & 0xFF)) << shift;
        }
        return result;
    }
}
