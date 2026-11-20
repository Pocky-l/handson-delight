package com.pockyl.handson_delight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.handson_delight.station.KitchenOven;
import com.pockyl.handson_delight.station.OvenLogic;
import com.pockyl.handson_delight.station.OvenStation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What an oven shows while somebody works at it ({@link OvenLook} for what differs between ovens): its door open, the tray slid out
 * with what bakes laid on its places, the fire inside glowing. Shut, it bakes; open, what bakes browns and rises before the cook's
 * eyes where the oven bakes each place alone. Done, a puff of steam leaves the oven, a chime rings and the badge swells; what was baked
 * waits on the open door, or lies on the tray as a dish. An oven that burns fuel burns it at the back of its floor: fuel let go over
 * the rack or at its controls goes there, and an empty hand at the controls is offered the inventory's fuel; tools stand on top. While nobody works at it
 * the block is the oven again; the station hides the block while it is used and until the door has shut behind the last one to leave.
 */
public final class OvenBehavior implements StationBehavior {
    private static final int PLACES = 9;
    /** The badge over the oven: its plate, the bar, the flame and the icon. */
    private static final String[] BADGE_PARTS = {"badge", "dial_track", "dial", "flame", "flame_lit", "preview"};
    private static final Identifier BADGE_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "block/white_concrete");
    /** The tray starts out once the door is this far open. */
    private static final float TRAY_AFTER = 0.7F;
    /**
     * An oven that bakes sets: things of one kind lie piled on each other, each a little higher and turned a little further, the piles
     * spread over the tray. A pie crust lies in the middle as big as a dish, and its filling is laid on it in layers. Pixels, degrees.
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
    /** The dish of a set: its block, at most this big, else as an item. */
    private static final float ITEM_PIXELS = 8.0F;
    private static final float DISH_ITEM = 4.4F;
    private static final float DISH_BLOCK = 7.0F;
    private static final float DISH_BLOCK_SCALE = 0.7F;
    /**
     * On the oven's burners: cookware as its block, at most this big and no wider than a burner's place (7 px apart, pixels); any other
     * thing lying flat this big.
     */
    private static final float TOOL_BLOCK_SCALE = 0.5F;
    private static final float TOOL_PLACE = 6.5F;
    private static final float TOOL_ITEM = 3.5F;
    /** The bakeware standing in an open-mouthed oven: its block at most this big and this wide (pixels), else the item lying flat. */
    private static final float BAKEWARE_SCALE = 0.5F;
    private static final float BAKEWARE_WIDE = 7.5F;
    private static final float BAKEWARE_ITEM = 7.0F;
    /**
     * An oven that bakes sets takes its tray as one place, not as nine: something for it carried over the tray marks the whole tray
     * (this colour, reaching this far beyond the outer places' middles, pixels); an empty hand outlines the thing a click takes. Where
     * no bakeware stands, the one carried over the oven, or the one what lies there waits for, shows faintly where it would stand.
     */
    private static final int TRAY_MARK = 0x50FFFFFF;
    private static final float TRAY_MARK_EDGE = 1.4F;
    private static final int BAKEWARE_GHOST = 0x78FFFFFF;
    /** Baking, what lies there browns towards a crust's colour and rises a little, the most by the time it is done. */
    private static final int BROWNED = 0xFFB07A48;
    private static final float BROWNING = 0.7F;
    private static final float RISING = 0.12F;
    /** Several things laid at once come from the player one after the other ({@link FromPlayer}): seconds, seconds, pixels, pixels. */
    private static final float ARRIVE_STAGGER = 0.12F;
    private static final float ARRIVE_TIME = 0.4F;
    private static final float ARRIVE_FROM = 9.0F;
    private static final float ARRIVE_ARC = 3.0F;
    /** The heat inside: how much of the glow's own see-through ({@link OvenLook#glowAlpha}) it flickers away. */
    private static final float GLOW_FLICKER = 0.25F;
    /** Smoke while it bakes shut, puffs a second; shimmer out of the open mouth while it is hot. */
    private static final float SMOKE_RATE = 1.4F;
    private static final float SHIMMER_RATE = 2.5F;
    private static final Vector3f SMOKE_RISE = new Vector3f(0.0F, 4.5F, -0.5F);
    private static final Vector3f STEAM_OUT = new Vector3f(0.0F, 5.0F, -3.0F);
    /** Fuel offered to an empty hand at the fire: how see-through. */
    private static final int OFFERED = 0x90FFFFFF;
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

    private final OvenLook look;
    private final RecipeBook book = new RecipeBook(new OvenBook());
    private final UsableCache usable = new UsableCache();
    private List<KitchenOven.Baking> recipes = List.of();
    private final ItemStack[] shown = new ItemStack[PLACES];
    /** Where each thing lies, pixels from its place, how big and turned how far; as laid out last. */
    private final Vector3f[] laidAt = new Vector3f[PLACES];
    private final float[] laidSize = new float[PLACES];
    private final float[] laidTurn = new float[PLACES];
    private final float[] browned = new float[PLACES];
    private final Item[] drawnFood = new Item[PLACES];
    private final Map<String, Item> drawnItems = new HashMap<>();
    private final Map<String, Identifier> drawnModels = new HashMap<>();
    /** Seconds until each thing sets off from the player, NaN: not coming. */
    private final float[] arriveIn = new float[PLACES];
    private float time;
    /** How far the door is open, 0 shut to 1; how far the tray is out. */
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
    /** What bakes or waits: the badge's icon. */
    private ItemStack dish = ItemStack.EMPTY;
    private int servings;
    private boolean ready;
    private boolean sawBaking;
    private int servedBefore = -1;
    private float doneAt = -10.0F;
    private boolean baking;
    private ItemStack offered = ItemStack.EMPTY;
    /** The bakeware what lies there waits for, while other bakeware or none stands in the oven: the badge blinks it. */
    private ItemStack wanted = ItemStack.EMPTY;
    /** The bakeware shown faintly where none stands (empty: none), and what the bakeware node shows now; faint or not. */
    private ItemStack ghost = ItemStack.EMPTY;
    private Item bakewareDrawn;
    private boolean bakewareFaint;
    /** The place whose thing is outlined for an empty hand, -1 none; whether the bakeware is. */
    private int outlined = -1;
    private boolean panOutlined;

    public OvenBehavior(StationView view, OvenLook look) {
        this.look = look;
        Arrays.fill(shown, ItemStack.EMPTY);
        Arrays.fill(arriveIn, Float.NaN);
        Arrays.fill(browned, -1.0F);
        for (int i = 0; i < laidAt.length; i++) {
            laidAt[i] = new Vector3f();
            laidSize[i] = 1.0F;
        }
    }

    public static OvenBehavior cfb(StationView view) {
        return new OvenBehavior(view, OvenLook.CFB);
    }

    public static OvenBehavior cultural(StationView view) {
        return new OvenBehavior(view, OvenLook.CULTURAL);
    }

    public static OvenBehavior extra(StationView view) {
        return new OvenBehavior(view, OvenLook.EXTRA);
    }

    private static KitchenOven oven(StationView view) {
        return KitchenOven.of(view.level().getBlockEntity(view.pos()));
    }

    private static boolean eachPlace(StationView view) {
        KitchenOven oven = oven(view);
        return oven == null || oven.bakesEachPlace();
    }

    /** The bakeware standing in the oven, empty for none or for an oven without it. */
    private static ItemStack bakeware(StationView view) {
        return view.state().schema().slotGroups().containsKey(OvenStation.BAKEWARE) ? view.state().getItem(OvenStation.BAKEWARE_SLOT)
                : ItemStack.EMPTY;
    }

    private static boolean isBakeware(KitchenOven oven, ItemStack stack) {
        return oven != null && oven.usesBakeware() && oven.isBakeware(stack);
    }

    private static int outputs(StationView view) {
        return view.state().getItems(OvenStation.SERVED).length;
    }

    /** The node a served slot is drawn at: {@code served} for one, {@code served_N} for several. */
    private static String served(StationView view, int index) {
        return outputs(view) == 1 ? "served" : "served_" + index;
    }

    private static String dishNode(StationView view, int index) {
        return outputs(view) == 1 ? "dish" : "dish_" + index;
    }

    @Override
    public void onSceneReady(StationView view) {
        drawnDial = "";
        shownDish = null;
        Arrays.fill(drawnFood, null);
        Arrays.fill(browned, -1.0F);
        drawnItems.clear();
        drawnModels.clear();
        ghost = ItemStack.EMPTY;
        bakewareDrawn = null;
        bakewareFaint = false;
        outlined = -1;
        panOutlined = false;
        view.node("tray_mark").ifPresent(mark -> {
            // Flat over the tray, as wide as the places reach and a little more; the columns lie evenly about the middle.
            float wide = Math.abs(look.columnX()[0]) + TRAY_MARK_EDGE;
            float back = look.rowZ()[0] + TRAY_MARK_EDGE;
            float front = look.rowZ()[2] - TRAY_MARK_EDGE;
            MeshShape shape = MeshShape.builder()
                    .corner(-wide, 0.0F, back, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F)
                    .corner(wide, 0.0F, back, 1.0F, 1.0F, 0.0F, 1.0F, 0.0F)
                    .corner(wide, 0.0F, front, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F)
                    .corner(-wide, 0.0F, front, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F)
                    .build();
            mark.setShape(shape, BADGE_TEXTURE, true);
            mark.setTint(TRAY_MARK);
            mark.setVisible(false);
        });
        recipes = recipesOf(view);
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

    private static List<KitchenOven.Baking> recipesOf(StationView view) {
        KitchenOven oven = oven(view);
        return oven == null ? List.of() : oven.recipes(view.level());
    }

    @Override
    public void onOpen(StationView view) {
        recipes = recipesOf(view);
        usable.clear();
        view.setInventoryFilter(stack -> usable.test(stack, it -> usable(view, it)));
        book.opened(view);
        servedBefore = servedCount(view);
        // What the cook came with is what they meant to bake or burn: a right click with food lays it on the tray, never slower.
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && view.openedByUse() && player.inventoryMenu.getCarried().isEmpty()) {
            ItemStack held = player.getMainHandItem();
            KitchenOven oven = oven(view);
            if (oven != null && (oven.takes(held) || isBakeware(oven, held))) {
                lay(view, player.getInventory().getSelectedSlot(), false);
            } else if (oven != null && oven.burnsFuel() && oven.isFuel(held)) {
                fuel(view, player.getInventory().getSelectedSlot());
            }
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

    /** What the inventory at the oven shows: what bakes on it, and its fuel. */
    private boolean usable(StationView view, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        KitchenOven oven = oven(view);
        if (oven == null) {
            return false;
        }
        if (oven.burnsFuel() && oven.isFuel(stack)) {
            return true;
        }
        if (oven.bakesEachPlace()) {
            return oven.takes(stack);
        }
        if (isBakeware(oven, stack)) {
            return true;
        }
        return recipes.stream().anyMatch(recipe -> recipe.needed().stream().anyMatch(ingredient -> ingredient.test(stack)));
    }

    /** A shift click in the inventory lays the whole stack on the tray, or feeds the fire with it. */
    @Override
    public boolean onInventoryShiftClick(StationView view, int slot, ItemStack stack) {
        KitchenOven oven = oven(view);
        if (oven == null || !usable(view, stack)) {
            return false;
        }
        if (oven.burnsFuel() && oven.isFuel(stack) && !oven.takes(stack)) {
            fuel(view, slot);
        } else {
            lay(view, slot, false);
        }
        return true;
    }

    private void lay(StationView view, int slot, boolean all) {
        CompoundTag how = new CompoundTag();
        how.putInt(OvenStation.FROM, slot);
        how.putBoolean(OvenStation.ALL, all);
        view.sendAction(OvenStation.ACTION_LAY, how);
        openForIt(view);
    }

    private void fuel(StationView view, int slot) {
        CompoundTag how = new CompoundTag();
        if (slot >= 0) {
            how.putInt(OvenStation.FROM, slot);
        }
        view.sendAction(OvenStation.ACTION_FUEL, how);
    }

    /** The door opens for what the cook lays: always where the oven bakes a set (it holds while open), else only where nothing bakes. */
    private void openForIt(StationView view) {
        KitchenOven oven = oven(view);
        boolean holds = oven != null && !oven.bakesEachPlace();
        if (!isDoorOpen(view) && (holds || view.state().getControl(OvenStation.PROGRESS) <= 0.0F)) {
            askDoor(view, true);
        }
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (!(event instanceof SceneEvent.Click click)) {
            return;
        }
        if (isDoor(click.node()) && click.button() == 0) {
            askDoor(view, !isDoorOpen(view));
        } else if (click.node().equals("book_open")) {
            book.click(view, click.button());
        } else if (click.node().equals("panel") && click.button() == 0 && !offered.isEmpty()) {
            CompoundTag how = new CompoundTag();
            how.putBoolean(OvenStation.FROM_INVENTORY, true);
            view.sendAction(OvenStation.ACTION_FUEL, how);
        }
    }

    private boolean isDoor(String node) {
        return node.equals("door") || node.startsWith("door_");
    }

    /** The wheel over the recipe book goes through its ingredients or its list. */
    @Override
    public boolean onScroll(StationView view, String node, double amount) {
        return "book_open".equals(node) && book.scroll(amount);
    }

    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if ("book_open".equals(node)) {
            book.click(view, button);
            return true;
        }
        KitchenOven oven = oven(view);
        boolean reachable = isDoorOpen(view) && tray >= 1.0F;
        boolean forOven = oven != null && (oven.takes(stack) || isBakeware(oven, stack));
        if (isDoor(node) && !(reachable && forOven)) {
            askDoor(view, !isDoorOpen(view));
            return true;
        }
        if (oven == null) {
            return false;
        }
        boolean place = node.startsWith("place_");
        if (isBakeware(oven, stack) && (place || isDoor(node) || "rack_zone".equals(node) || "mouth".equals(node)
                || "bakeware_slot".equals(node))) {
            // Bakeware let go over the oven stands in it, one (Shift: the stack, bowls a recipe uses up); other bakeware comes back.
            CompoundTag how = new CompoundTag();
            how.putBoolean(OvenStation.ALL, shift);
            view.sendAction(OvenStation.ACTION_LAY, how);
            return true;
        }
        if ((isDoor(node) || "bakeware_slot".equals(node)) && oven.takes(stack)) {
            // Food let go over the mouth while the bakeware is out goes into it; a right click lays one, as into a crafting grid.
            CompoundTag how = new CompoundTag();
            how.putBoolean(OvenStation.ONE, button == 1);
            how.putBoolean(OvenStation.ALL, shift);
            view.sendAction(OvenStation.ACTION_LAY, how);
            return true;
        }
        boolean intoOven = place || "rack_zone".equals(node) || "panel".equals(node);
        if (intoOven && oven.burnsFuel() && oven.isFuel(stack) && !oven.takes(stack)) {
            // Fuel let go at the controls or over the rack goes onto the fire at the back.
            fuel(view, -1);
            return true;
        }
        if ((place || "rack_zone".equals(node)) && oven.takes(stack)) {
            // Let go over the rack: on every free place, or with a right click one on the place under the cursor (the free place
            // nearest it); Shift brings the inventory's alike things after it.
            CompoundTag how = new CompoundTag();
            if (button == 1) {
                int under = place ? Integer.parseInt(node.substring("place_".length())) : -1;
                boolean free = under >= 0 && view.state().getItem(SlotRef.of(OvenStation.INGREDIENTS, under)).isEmpty();
                how.putBoolean(OvenStation.ONE, true);
                how.putInt(OvenStation.PLACE, free ? under : nearestFree(view));
            }
            how.putBoolean(OvenStation.ALL, shift);
            view.sendAction(OvenStation.ACTION_LAY, how);
            return true;
        }
        return false;
    }

    /** The free place nearest the cursor on the tray; -1 for none. */
    private int nearestFree(StationView view) {
        ItemStack[] items = view.state().getItems(OvenStation.INGREDIENTS);
        int best = -1;
        float nearest = Float.MAX_VALUE;
        for (int place = 0; place < items.length && place < PLACES; place++) {
            if (!items[place].isEmpty()) {
                continue;
            }
            Optional<Vector3f> at = view.node(placeNode(place)).map(node -> node.sceneMatrix().transformPosition(new Vector3f()));
            Optional<Vector3f> cursor = at.flatMap(point -> view.cursorOn(point.y));
            float distance = at.isPresent() && cursor.isPresent() ? cursor.get().distanceSquared(at.get()) : place;
            if (distance < nearest) {
                nearest = distance;
                best = place;
            }
        }
        return best;
    }

    private static String placeNode(int index) {
        return "place_" + index;
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
        for (int i = 0; i < PLACES && i < items.length; i++) {
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
                Optional<Vector3f> from = FromPlayer.point(view, look.onTray() + ARRIVE_FROM, k, arrivals.size());
                if (from.isPresent()) {
                    arriveIn[index] = k * ARRIVE_STAGGER;
                    Vector3f start = offsetOf(view, index, from.get());
                    view.node(placeNode(index)).ifPresent(node -> {
                        node.setVisible(false);
                        node.moveTo(start.x, start.y, start.z, 0.0F);
                    });
                }
            }
        } else {
            arrivals.forEach(index -> view.playSound(SoundEvents.CHAIN_PLACE, placeNode(index), 0.25F, 1.5F + 0.04F * index));
        }
        badgeState(view);
    }

    /** The scene point {@code at} as an offset of a place's node from its place: the tray's own frame, the place taken off. */
    private Vector3f offsetOf(StationView view, int index, Vector3f at) {
        Optional<NodeView> tray = view.node("tray");
        Vector3f local = tray.map(node -> new Matrix4f(node.sceneMatrix()).invert().transformPosition(new Vector3f(at)))
                .orElse(new Vector3f(at).sub(8.0F, 0.0F, 8.0F));
        return local.sub(look.columnX()[index % 3], look.onTray(), look.rowZ()[index / 3]);
    }

    /**
     * Where each thing lies. An oven that bakes each place alone: each on its place. One that bakes sets: things of one kind piled on
     * each other, the piles spread over the tray; with a pie crust among them, the crust in the middle as big as the dish and
     * everything else laid on it in layers.
     */
    private void layOut(StationView view) {
        boolean each = eachPlace(view);
        int crust = -1;
        List<Item> kinds = new ArrayList<>();
        for (int i = 0; i < shown.length && !each; i++) {
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
            Vector3f home = new Vector3f(look.columnX()[i % 3], 0.0F, look.rowZ()[i / 3]);
            Vector3f at = new Vector3f();
            float size = 1.0F;
            float turn = 0.0F;
            if (shown[i].isEmpty() || each) {
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
                        : new float[] {look.columnX()[kind % 3], look.rowZ()[kind / 3]};
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
                view.node(placeNode(i)).ifPresent(node -> {
                    node.moveTo(to.x, to.y, to.z, LAYOUT_TIME);
                    node.scaleTo(scale, LAYOUT_TIME);
                    node.rotateTo(0.0F, angle, 0.0F, LAYOUT_TIME);
                });
                browned[i] = -1.0F;
            }
            food(view, i);
        }
        for (int i = 0; i < outputs(view); i++) {
            servedDish(view, i);
        }
        tools(view);
        fuelBed(view);
        bakewareStanding(view);
    }

    /**
     * The bakeware standing in an open-mouthed oven, under what bakes: its own block (a sheet, a pie dish, a loaf pan) sized to the
     * oven, else the item lying flat (a muffin tin, a bowl). Where none stands, the {@link #ghost} faintly in its place.
     */
    private void bakewareStanding(StationView view) {
        ItemStack standing = bakeware(view);
        boolean faint = standing.isEmpty() && !ghost.isEmpty();
        ItemStack stack = faint ? ghost : standing;
        Item item = stack.isEmpty() ? null : stack.getItem();
        if (item == bakewareDrawn && faint == bakewareFaint) {
            return;
        }
        bakewareDrawn = item;
        bakewareFaint = faint;
        view.node("bakeware_model").ifPresent(node -> {
            node.setVisible(item != null);
            if (item == null) {
                return;
            }
            int tint = faint ? BAKEWARE_GHOST : 0xFFFFFFFF;
            float extent = blockExtent(item);
            if (extent > 0.0F) {
                node.setVisual(Visual.ofBlock(((BlockItem) item).getBlock().defaultBlockState()).withTint(tint));
                node.rotateTo(0.0F, 0.0F, 0.0F, 0.0F);
                node.scaleTo(Math.min(BAKEWARE_SCALE, BAKEWARE_WIDE / extent), 0.0F);
                node.moveTo(0.0F, 0.0F, 0.0F, 0.0F);
            } else {
                node.setVisual(Visual.ofItem(item).withAnchor(ICON_MIDDLE).withTint(tint));
                node.rotateTo(-90.0F, 0.0F, 0.0F, 0.0F);
                node.scaleTo(BAKEWARE_ITEM / ITEM_PIXELS, 0.0F);
                node.moveTo(0.0F, 0.1F, 0.0F, 0.0F);
            }
        });
    }

    /**
     * An oven of sets shows its tray as one place ({@link #TRAY_MARK}): marked whole while the cook carries something for it over it,
     * the thing a click takes outlined for an empty hand (the bakeware where nothing lies on it), and the bakeware it lacks faint where
     * it would stand.
     */
    private void trayHints(StationView view, boolean reachable) {
        KitchenOven oven = oven(view);
        boolean sets = reachable && oven != null && !oven.bakesEachPlace();
        LocalPlayer player = Minecraft.getInstance().player;
        ItemStack carried = player == null ? ItemStack.EMPTY : player.inventoryMenu.getCarried();
        boolean over = sets && overTray(view);
        boolean bakewareCarried = isBakeware(oven, carried);
        boolean forTray = !carried.isEmpty() && oven != null && (oven.takes(carried) || bakewareCarried);
        view.node("tray_mark").ifPresent(mark -> mark.setVisible(over && forTray));
        ItemStack faint = ItemStack.EMPTY;
        if (sets && oven.usesBakeware()) {
            faint = over && bakewareCarried ? carried : wanted;
        }
        if (faint.getItem() != ghost.getItem()) {
            ghost = faint.isEmpty() ? ItemStack.EMPTY : faint.copyWithCount(1);
            bakewareStanding(view);
        }
        int under = -1;
        for (int i = 0; sets && carried.isEmpty() && i < PLACES; i++) {
            boolean hovered = view.node(placeNode(i)).map(NodeView::isHovered).orElse(false);
            if (hovered && !shown[i].isEmpty()) {
                under = i;
            }
        }
        boolean panUnder = sets && carried.isEmpty() && !bakeware(view).isEmpty()
                && view.node("bakeware_slot").map(NodeView::isHovered).orElse(false);
        if (panUnder != panOutlined) {
            panOutlined = panUnder;
            view.node("bakeware_model").ifPresent(node -> node.setOutlined(panUnder));
        }
        if (under != outlined) {
            if (outlined >= 0) {
                view.node("food_" + outlined).ifPresent(node -> node.setOutlined(false));
            }
            if (under >= 0) {
                view.node("food_" + under).ifPresent(node -> node.setOutlined(true));
            }
            outlined = under;
        }
    }

    /** Whether the cursor is over the tray: on a place, over the rack, the bakeware's rim or the mouth over it. */
    private static boolean overTray(StationView view) {
        for (String id : new String[] {"rack_zone", "bakeware_slot", "door_mouth", "mouth"}) {
            if (view.node(id).map(NodeView::isHovered).orElse(false)) {
                return true;
            }
        }
        for (int i = 0; i < PLACES; i++) {
            if (view.node(placeNode(i)).map(NodeView::isHovered).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    /** The thing on place {@code index}, drawn by its food node: an item lying flat, tinted as it browns. */
    private void food(StationView view, int index) {
        Item item = shown[index].isEmpty() ? null : shown[index].getItem();
        if (item == drawnFood[index]) {
            return;
        }
        drawnFood[index] = item;
        browned[index] = -1.0F;
        view.node("food_" + index).ifPresent(node -> {
            node.setVisible(item != null);
            if (item != null) {
                node.setVisual(Visual.ofItem(item).withAnchor(ICON_MIDDLE));
            }
        });
    }

    /**
     * What was baked: one dish lies on the tray, a dish that is a block (a pie, a cake) as its own block model sized to the tray; things
     * baked one by one wait in their slots on the open door, each as the item.
     */
    private void servedDish(StationView view, int index) {
        String node = dishNode(view, index);
        ItemStack stack = view.state().getItem(SlotRef.of(OvenStation.SERVED, index));
        Item item = stack.isEmpty() ? null : stack.getItem();
        if (drawnItems.containsKey(node) && drawnItems.get(node) == item) {
            return;
        }
        drawnItems.put(node, item);
        boolean single = outputs(view) == 1;
        view.node(node).ifPresent(dish -> {
            dish.setVisible(item != null);
            if (item == null) {
                return;
            }
            float extent = single ? blockExtent(item) : 0.0F;
            if (extent > 0.0F) {
                BlockState state = ((BlockItem) item).getBlock().defaultBlockState();
                dish.setVisual(Visual.ofBlock(state));
                dish.rotateTo(0.0F, 0.0F, 0.0F, 0.0F);
                dish.scaleTo(Math.min(DISH_BLOCK_SCALE, DISH_BLOCK / extent), 0.0F);
                dish.moveTo(0.0F, 0.0F, 0.0F, 0.0F);
            } else if (single) {
                dish.setVisual(Visual.ofItem(item).withAnchor(ICON_MIDDLE));
                dish.rotateTo(-90.0F, 0.0F, 0.0F, 0.0F);
                dish.scaleTo(DISH_ITEM / ITEM_PIXELS, 0.0F);
                dish.moveTo(0.0F, 0.15F, 0.0F, 0.0F);
            } else {
                dish.setVisual(Visual.ofItem(item).withAnchor(ICON_MIDDLE));
            }
        });
    }

    /**
     * What stands on the oven's burners, as on a stove: cookware that is a block (a skillet,
     * a pot) as its block, upright and no wider than a burner's place; anything else lying flat on the burner. In the world as at the
     * station: the oven's own renderer, which stands them tilted, leaves them to the scene ({@code CfbOvenToolsMixin}).
     */
    private void tools(StationView view) {
        ItemStack[] tools = view.state().schema().slotGroups().containsKey(OvenStation.TOOLS)
                ? view.state().getItems(OvenStation.TOOLS) : new ItemStack[0];
        for (int i = 0; i < tools.length; i++) {
            String nodeId = "tool_item_" + i;
            Item item = tools[i].isEmpty() ? null : tools[i].getItem();
            if (drawnItems.containsKey(nodeId) && drawnItems.get(nodeId) == item) {
                continue;
            }
            drawnItems.put(nodeId, item);
            view.node(nodeId).ifPresent(node -> {
                node.setVisible(item != null);
                if (item == null) {
                    return;
                }
                float extent = blockExtent(item);
                if (extent > 0.0F) {
                    node.setVisual(Visual.ofBlock(((BlockItem) item).getBlock().defaultBlockState()));
                    node.rotateTo(0.0F, 0.0F, 0.0F, 0.0F);
                    node.scaleTo(Math.min(TOOL_BLOCK_SCALE, TOOL_PLACE / extent), 0.0F);
                    node.moveTo(0.0F, 0.0F, 0.0F, 0.0F);
                } else {
                    node.setVisual(Visual.ofItem(item).withAnchor(ICON_MIDDLE));
                    node.rotateTo(-90.0F, 0.0F, 0.0F, 0.0F);
                    node.scaleTo(TOOL_ITEM / ITEM_PIXELS, 0.0F);
                    node.moveTo(0.0F, 0.1F, 0.0F, 0.0F);
                }
            });
        }
    }

    /** The fuel lying at the back of the oven's floor, where it burns. */
    private void fuelBed(StationView view) {
        if (view.state().schema().slotGroups().containsKey(OvenStation.FUEL)) {
            showItem(view, "fuel_item", view.state().getItem(OvenStation.FUEL_SLOT));
        }
    }

    private void showItem(StationView view, String nodeId, ItemStack stack) {
        Item item = stack.isEmpty() ? null : stack.getItem();
        if (drawnItems.containsKey(nodeId) && drawnItems.get(nodeId) == item) {
            return;
        }
        drawnItems.put(nodeId, item);
        view.node(nodeId).ifPresent(node -> {
            node.setVisible(item != null);
            if (item != null) {
                node.setVisual(Visual.ofItem(item).withAnchor(ICON_MIDDLE));
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

    private int servedCount(StationView view) {
        return Arrays.stream(view.state().getItems(OvenStation.SERVED)).mapToInt(ItemStack::getCount).sum();
    }

    /** What the badge says: what bakes or waits, how many, whether it was just done. */
    private void badgeState(StationView view) {
        StationState state = view.state();
        float progress = state.getControl(OvenStation.PROGRESS);
        ItemStack[] onTray = state.getItems(OvenStation.INGREDIENTS);
        Optional<KitchenOven.Baking> making = Optional.empty();
        if (eachPlace(view)) {
            // What bakes: the thing on the place furthest on of those still baking.
            byte[] baked = state.data().getByteArray(OvenStation.BAKED).orElse(new byte[0]);
            int best = -1;
            for (int i = 0; i < onTray.length; i++) {
                int done = i < baked.length ? baked[i] : 0;
                if (!onTray[i].isEmpty() && done < 100 && KitchenOven.bakingOf(recipes, onTray[i]).isPresent()
                        && (best < 0 || done > (best < baked.length ? baked[best] : 0))) {
                    best = i;
                }
            }
            if (best >= 0) {
                making = KitchenOven.bakingOf(recipes, onTray[best]);
            }
        } else {
            making = KitchenOven.matching(recipes, onTray, bakeware(view));
        }
        Item wants = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(state.data().getStringOr(OvenStation.WANTS, "")));
        wanted = making.isEmpty() && state.data().contains(OvenStation.WANTS) && wants != Items.AIR ? new ItemStack(wants) : ItemStack.EMPTY;
        ItemStack[] served = state.getItems(OvenStation.SERVED);
        ItemStack waiting = Arrays.stream(served).filter(stack -> !stack.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
        int count = servedCount(view);
        baking = making.isPresent();
        servings = 0;
        ready = false;
        if (making.isPresent()) {
            dish = making.get().result();
        } else if (!wanted.isEmpty()) {
            dish = wanted;
        } else if (!waiting.isEmpty()) {
            dish = waiting;
            servings = count;
            ready = true;
        } else {
            dish = ItemStack.EMPTY;
        }
        if (progress > 0.0F) {
            sawBaking = true;
        }
        if (servedBefore >= 0 && count > servedBefore && sawBaking) {
            sawBaking = progress > 0.0F;
            done(view);
        }
        servedBefore = count;
    }

    /** Something is done: a puff of steam out of the oven and, at the station, the badge swells with a chime. */
    private void done(StationView view) {
        doneAt = time;
        view.spawnParticles(DiegesisParticles.STEAM, look.mouth(), STEAM_OUT, 1.6F, 10, 0xB0FFFFFF);
        if (view.isInUse()) {
            view.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), "badge", 0.35F, 1.26F);
            view.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), "badge", 0.3F, 1.5F);
            view.spawnParticles(DiegesisParticles.GLINT, served(view, 0), 6, 0xFFFFE6A0);
        }
    }

    @Override
    public void frame(StationView view, float dt) {
        time += dt;
        boolean inUse = view.isInUse();
        boolean open = inUse && isDoorOpen(view);
        swing(view, dt, open);
        boolean whole = view.isBlockHidden();
        boolean hot = view.state().data().getBooleanOr(OvenStation.HOT, false);
        models(view, hot, door < 0.05F);
        // What stands on the burners is read every frame: in the world nobody's change of the oven reaches the scene as a change of
        // its state (its renderer's own data comes with the oven's updates).
        tools(view);
        // The oven's own parts exactly while the block is left out of the world: the two swap in one frame.
        for (String part : look.blockParts()) {
            view.node(part).ifPresent(node -> node.setVisible(whole));
        }
        boolean reachable = open && tray >= 1.0F;
        // On a tray of sets an empty hand reaches through the rack and the empty places to the bakeware under them: it is taken
        // anywhere on it where nothing lies.
        LocalPlayer player = Minecraft.getInstance().player;
        boolean throughToPan = !eachPlace(view) && (player == null || player.inventoryMenu.getCarried().isEmpty());
        for (int i = 0; i < PLACES; i++) {
            arrive(view, i, dt);
            boolean enabled = reachable && !(throughToPan && shown[i].isEmpty());
            view.node(placeNode(i)).ifPresent(node -> node.setEnabled(enabled));
        }
        for (int i = 0; i < outputs(view); i++) {
            // An empty one is no place to click: the door under it is.
            boolean holds = !view.state().getItem(SlotRef.of(OvenStation.SERVED, i)).isEmpty();
            view.node(served(view, i)).ifPresent(node -> node.setEnabled(reachable && holds));
        }
        view.node("rack_zone").ifPresent(node -> node.setEnabled(reachable && !throughToPan));
        view.node("bakeware_slot").ifPresent(node -> node.setEnabled(reachable));
        view.node("mouth").ifPresent(node -> node.setEnabled(reachable));
        view.node("panel").ifPresent(node -> node.setEnabled(reachable));
        trayHints(view, inUse && reachable);
        brown(view, open);
        heat(view, dt, hot, open, baking && !open);
        offerFuel(view, inUse && reachable);
        book.frame(view, dt);
        float appear = RecipeBook.appear(view);
        badge(view, inUse && RecipeBook.isWorkedAt(view) && appear > RecipeBook.SPECK, open);
        float swell = badgeSwell();
        for (String part : BADGE_PARTS) {
            view.node(part).ifPresent(node -> node.scaleTo(appear * swell, 0.0F));
        }
    }

    /** The models that follow the block: its colour, its lit front, the door lit through its glass while it burns shut. */
    private void models(StationView view, boolean hot, boolean doorShut) {
        Map<String, Identifier> models = look.models().of(view.level().getBlockState(view.pos()), hot, doorShut);
        models.forEach((nodeId, model) -> {
            if (!model.equals(drawnModels.get(nodeId))) {
                drawnModels.put(nodeId, model);
                view.node(nodeId).ifPresent(node -> node.setVisual(Visual.ofModel(model).withAnchor(anchorOf(nodeId))));
            }
        });
    }

    /** The scene's door and its handle turn on the door's hinge: their models are drawn from there. */
    private Vector3f anchorOf(String nodeId) {
        return nodeId.startsWith("door") ? new Vector3f(8.0F, 0.0F, 0.0F) : new Vector3f(8.0F, 0.0F, 8.0F);
    }

    /** The door opens and the tray comes out after it; shutting, the tray goes in first and the door after it. */
    private void swing(StationView view, float dt, boolean open) {
        float before = door;
        if (open) {
            door = Math.min(1.0F, door + dt / look.doorTime());
            if (door >= TRAY_AFTER) {
                tray = Math.min(1.0F, tray + dt / look.trayTime());
            }
        } else {
            tray = Math.max(0.0F, tray - dt / look.trayTime());
            if (tray <= 0.0F) {
                door = Math.max(0.0F, door - dt / look.doorTime());
            }
        }
        String hinge = switch (look.door()) {
            case DROP -> "door";
            case LEAVES -> "door_left";
            case NONE -> "tray";
        };
        // An open mouth has no door to sound: the bakeware or the food on the ledge does, and an empty ledge moves in silence.
        boolean sounds = look.door() != OvenLook.Door.NONE || !bakeware(view).isEmpty()
                || Arrays.stream(shown).anyMatch(stack -> stack != null && !stack.isEmpty());
        if (open && !wasOpening && before < 1.0F && sounds) {
            view.playSound(look.openSound(), hinge, 0.5F, 0.9F + view.level().getRandom().nextFloat() * 0.1F);
        }
        float closesAt = look.door() == OvenLook.Door.DROP ? 0.5F : 0.0F;
        if (before > closesAt && door <= closesAt && !open && sounds) {
            view.playSound(look.closeSound(), hinge, 0.5F, 0.9F + view.level().getRandom().nextFloat() * 0.1F);
        }
        if (look.door() == OvenLook.Door.LEAVES && before < 1.0F && door >= 1.0F) {
            // The leaves come to rest on their hinges with a small knock of iron.
            view.playSound(SoundEvents.CHAIN_HIT, hinge, 0.3F, 1.3F);
        }
        wasOpening = open;
        if (look.door() == OvenLook.Door.NONE) {
            // An open mouth: only the bakeware moves.
        } else if (look.door() == OvenLook.Door.DROP) {
            // Eased as its own animator eases it: fast at first, slowing as it comes down.
            float eased = 1.0F - (1.0F - door) * (1.0F - door) * (1.0F - door);
            float angle = look.doorOpen() * eased;
            view.node("door").ifPresent(node -> node.rotateTo(-angle, 0.0F, 0.0F, 0.0F));
        } else {
            float angle = look.doorOpen() * KitchenMotions.smooth(door);
            view.node("door_left").ifPresent(node -> node.rotateTo(0.0F, angle, 0.0F, 0.0F));
            view.node("door_right").ifPresent(node -> node.rotateTo(0.0F, -angle, 0.0F, 0.0F));
        }
        float out = look.trayOut() * KitchenMotions.smooth(tray);
        view.node("tray").ifPresent(node -> node.moveTo(0.0F, 0.0F, -out, 0.0F));
        // What waits on the open door shows once it is down.
        boolean down = door >= 0.95F;
        for (int i = 0; i < outputs(view) && outputs(view) > 1; i++) {
            ItemStack stack = view.state().getItem(SlotRef.of(OvenStation.SERVED, i));
            view.node(dishNode(view, i)).ifPresent(node -> node.setVisible(down && !stack.isEmpty()));
        }
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
        view.node(placeNode(index)).ifPresent(node -> {
            node.setVisible(true);
            Vector3f to = laidAt[index];
            node.hopTo(to.x, to.y, to.z, ARRIVE_ARC, ARRIVE_TIME);
        });
        view.playSound(SoundEvents.CHAIN_PLACE, placeNode(index), 0.2F, 1.5F + 0.04F * index);
    }

    /**
     * What bakes browns and rises as it bakes: each place by its own progress where the oven bakes each alone, all of the tray by the
     * set's where it bakes sets (only seen shut through its door, so held while the door is open).
     */
    private void brown(StationView view, boolean open) {
        boolean each = eachPlace(view);
        byte[] baked = view.state().data().getByteArray(OvenStation.BAKED).orElse(new byte[0]);
        float set = baking && !open ? view.state().getControl(OvenStation.PROGRESS) : 0.0F;
        for (int i = 0; i < PLACES; i++) {
            float progress = each ? (i < baked.length ? baked[i] / 100.0F : 0.0F) : set;
            if (shown[i].isEmpty() || each && KitchenOven.bakingOf(recipes, shown[i]).isEmpty()) {
                progress = 0.0F;
            }
            float k = KitchenMotions.smooth(Mth.clamp(progress, 0.0F, 1.0F));
            if (Math.abs(k - browned[i]) < 0.01F) {
                continue;
            }
            browned[i] = k;
            int color = blend(0xFFFFFFFF, BROWNED, BROWNING * k);
            float size = laidSize[i] * (1.0F + RISING * k);
            view.node("food_" + i).ifPresent(node -> node.setTint(color));
            view.node(placeNode(i)).ifPresent(node -> node.scaleTo(size, 0.0F));
        }
    }

    /**
     * The heat: its glow inside, flickering; out of the open mouth the hot air shimmers; while it bakes shut, smoke rises from it.
     */
    private void heat(StationView view, float dt, boolean hot, boolean open, boolean bakingShut) {
        boolean shownHere = view.isBlockHidden();
        view.node("glow").ifPresent(node -> {
            node.setVisible(hot);
            float flicker = 0.5F + 0.3F * Mth.sin(time * 7.3F) + 0.2F * Mth.sin(time * 13.1F + 1.0F);
            node.setTint((look.glowAlpha() - Math.round(look.glowAlpha() * GLOW_FLICKER * flicker)) << 24 | look.glowColor());
        });
        if (!hot || !shownHere) {
            return;
        }
        if (open && door >= 1.0F) {
            shimmer += dt * SHIMMER_RATE;
            if (shimmer >= 1.0F) {
                view.spawnParticles(HandsOnDelightClient.HEAT, look.mouth(), new Vector3f(0.0F, 3.0F, -0.5F), 2.5F, (int) shimmer, 0x48FFFFFF);
                shimmer -= (int) shimmer;
            }
        }
        if (bakingShut) {
            smoke += dt * SMOKE_RATE;
            if (smoke >= 1.0F) {
                view.spawnParticles(HandsOnDelightClient.PAN_SMOKE, look.smokeFrom(), SMOKE_RISE, 1.2F, (int) smoke, 0x58C8C6C2);
                smoke -= (int) smoke;
            }
        }
    }

    /**
     * With an empty hand at the oven's controls, the inventory's fuel shows see-through in front of them: a click puts it onto the
     * fire. Nothing while the fire has all it holds.
     */
    private void offerFuel(StationView view, boolean reachable) {
        offered = ItemStack.EMPTY;
        NodeView node = view.node("offer").orElse(null);
        LocalPlayer player = Minecraft.getInstance().player;
        KitchenOven oven = oven(view);
        boolean hovered = view.node("panel").map(NodeView::isHovered).orElse(false);
        if (node == null || player == null || oven == null || !oven.burnsFuel() || !reachable || !view.isFocused() || !hovered
                || !player.inventoryMenu.getCarried().isEmpty()) {
            if (node != null) {
                node.setVisible(false);
            }
            return;
        }
        int slot = OvenLogic.fuelSlot(oven, player.getInventory());
        node.setVisible(slot >= 0);
        if (slot < 0) {
            return;
        }
        offered = player.getInventory().getItem(slot);
        if (drawnItems.get("offer") != offered.getItem()) {
            drawnItems.put("offer", offered.getItem());
            node.setVisual(Visual.ofItem(offered.getItem()).withAnchor(ICON_MIDDLE));
            node.setTint(OFFERED);
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
     * The badge while something bakes or waits: what it makes, whether it is hot, how far it has baked and the time left, or how many
     * are ready. The flame is lit over the fire, grey and blinking while what lies there waits for heat.
     */
    private void badge(StationView view, boolean cookHere, boolean open) {
        boolean shownHere = cookHere && !dish.isEmpty();
        view.node("badge").ifPresent(plate -> plate.setVisible(shownHere));
        float progress = view.state().getControl(OvenStation.PROGRESS);
        int left = view.state().data().getIntOr(OvenStation.LEFT, 0);
        boolean hot = view.state().data().getBooleanOr(OvenStation.HOT, false);
        boolean cold = view.state().data().getBooleanOr(OvenStation.COLD, false);
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
        boolean waiting = cold || !eachPlace(view) && open && progress > 0.0F;
        view.node("dial").ifPresent(node -> node.setTint(waiting ? WAITING_COLOR : DIAL_COLOR));
        view.node("dial_track").ifPresent(node -> node.setVisible(shownHere));
        view.node("dial").ifPresent(node -> node.setVisible(shownHere));
        view.node("flame").ifPresent(node -> node.setVisible(shownHere && baking && !hot && Mth.sin(time * 6.0F) > 0.0F));
        view.node("flame_lit").ifPresent(node -> node.setVisible(shownHere && baking && hot));
        preview(view, shownHere, seconds);
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
    private void preview(StationView view, boolean shownHere, int seconds) {
        view.node("preview").ifPresent(node -> {
            node.setVisible(shownHere);
            if (!shownHere) {
                return;
            }
            if (shownDish != dish.getItem()) {
                shownDish = dish.getItem();
                node.setVisual(Visual.ofItem(shownDish).withAnchor(ICON_MIDDLE));
            }
            // The bakeware what lies there waits for blinks, as the flame does without heat.
            node.setVisible(wanted.isEmpty() || Mth.sin(time * 6.0F) > 0.0F);
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

    static boolean shiftDown() {
        return net.minecraft.client.Minecraft.getInstance().hasShiftDown();
    }
}
