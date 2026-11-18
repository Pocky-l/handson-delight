package com.pockyl.handson_delight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.handson_delight.station.BasinStation;
import com.pockyl.handson_delight.station.KitchenBasin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Barbeque's Delight's basin on the client: what lies in its two compartments as a few pieces each, more the more there is; a stick
 * from the cook's inventory ready on its front rim. Skewering, a stick lies over the basin and the pieces are pushed onto it one
 * after another, food and side by turns from the two compartments; the finished skewer goes to the cook.
 */
public final class BasinBehavior implements StationBehavior {
    private static final float FLOOR = 1.02F;
    private static final float PIECE_SIZE = 3.4F;
    private static final float STICK_SIZE = 9.0F;
    private static final float BEAD_SIZE = 3.4F;
    /** The skewer lies front to back over the divider, this high: the icon's diagonal turned to run away from the cook. */
    private static final float OVER = 6.0F;
    private static final float ALONG = 45.0F;
    /** Where each piece in a compartment lies, from its middle: x, z, and how far up it lies on the ones under it. */
    private static final float[][] PILE = {{0.0F, 1.0F, 0.0F}, {0.4F, 4.2F, 0.0F}, {-0.3F, -2.2F, 0.0F}, {-0.6F, 2.6F, 0.3F},
            {0.5F, -0.6F, 0.3F}, {0.0F, 1.6F, 0.6F}};
    private static final int[] SHOWN_FROM = {1, 2, 4, 8, 16, 32};
    private static final int BEADS = 4;
    private static final float STICK_IN = 0.25F;
    private static final float BEAD_EVERY = 0.3F;
    private static final float BEAD_HOP = 0.2F;
    /** How long a piece takes pushed from the stick's point down to its place. */
    private static final float PUSH = 0.16F;
    /** Along the stick from its middle: its point, the place of the first piece by the handle, and how far apart pieces sit. */
    private static final float POINT = 5.2F;
    private static final float HANDLE = -3.0F;
    private static final float SPACING = 1.9F;
    /**
     * A piece on the stick stands across it, its face towards the cook and leant back a little to the camera above: a flat icon is
     * drawn from its face only, turned the other way it would show its edge.
     */
    private static final float ACROSS = -70.0F;
    private static final float AWAY_TIME = 0.35F;
    private static final float SHOWN = 0.45F;
    private static final int GHOSTS = 3;

    private final BasinBook pages = new BasinBook();
    private final RecipeBook book = new RecipeBook(pages);
    private final Map<Item, Boolean> usable = new HashMap<>();
    private final RandomSource random = RandomSource.create();
    private final ItemStack[] shown = new ItemStack[KitchenBasin.PLACES];
    private final int[] shownCount = new int[KitchenBasin.PLACES];
    private final List<Step> steps = new ArrayList<>();
    private ItemStack restingStick = ItemStack.EMPTY;
    private float clock;
    private int ghost;

    private record Step(float at, Runnable run) {
    }

    public BasinBehavior(StationView view) {
        Arrays.fill(shown, ItemStack.EMPTY);
    }

    @Override
    public void onSceneReady(StationView view) {
        Arrays.fill(shown, ItemStack.EMPTY);
        Arrays.fill(shownCount, -1);
        steps.clear();
        restingStick = ItemStack.EMPTY;
        view.node("book_open").ifPresent(node -> node.setPainter(book));
        refresh(view);
    }

    @Override
    public void onOpen(StationView view) {
        book.opened(view);
        usable.clear();
        view.setInventoryFilter(stack -> usable.computeIfAbsent(stack.getItem(),
                item -> KitchenBasin.isTool(view.level(), stack) || KitchenBasin.isFood(view.level(), stack)));
        // What the cook came with is meant for the basin: food goes in, a stick makes a skewer of what lies there.
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && view.openedByUse() && player.inventoryMenu.getCarried().isEmpty()) {
            ItemStack held = player.getMainHandItem();
            CompoundTag how = new CompoundTag();
            how.putInt(BasinStation.FROM, player.getInventory().selected);
            if (KitchenBasin.isTool(view.level(), held)) {
                view.sendAction(BasinStation.ACTION_SKEWER, how);
            } else if (KitchenBasin.isFood(view.level(), held)) {
                how.putBoolean(BasinStation.ALL, true);
                view.sendAction(BasinStation.ACTION_PUT, how);
            }
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
        restStick(view);
        for (int i = 0; i < GHOSTS; i++) {
            view.node("lift_" + i).filter(node -> node.isVisible() && !node.isMoving()).ifPresent(node -> node.setVisible(false));
        }
    }

    /** The compartments as the state says: a few pieces of what lies in each, more the more there is. */
    private void refresh(StationView view) {
        for (int place = 0; place < KitchenBasin.PLACES; place++) {
            ItemStack now = view.state().getItem(BasinStation.compartment(place));
            int count = piecesFor(now.getCount());
            if (ItemStack.isSameItemSameComponents(now, shown[place]) && count == shownCount[place]) {
                continue;
            }
            shown[place] = now.copy();
            shownCount[place] = count;
            for (int k = 0; k < PILE.length; k++) {
                int index = k;
                int at = place;
                view.node("food_" + place + "_" + k).ifPresent(node -> {
                    node.setVisible(index < count);
                    if (index < count) {
                        show(node, now, PIECE_SIZE);
                        node.moveTo(PILE[index][0], PILE[index][2], PILE[index][1], 0.0F);
                        node.rotateTo(0.0F, yaw(at, index), 0.0F, 0.0F);
                    }
                });
            }
        }
    }

    private static int piecesFor(int count) {
        int pieces = 0;
        for (int from : SHOWN_FROM) {
            pieces += count >= from ? 1 : 0;
        }
        return pieces;
    }

    /** Each piece lies a little turned its own way, never two alike. */
    private static float yaw(int place, int index) {
        return ((place * 31 + index * 7919) % 61 - 30) * 1.5F;
    }

    /** A stick from the cook's inventory lies on the front rim while there is one: the cook's tool, a click away. */
    private void restStick(StationView view) {
        if (!view.isFocused()) {
            view.node("stick").ifPresent(node -> node.setVisible(false));
            return;
        }
        ItemStack stick = ItemStack.EMPTY;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            Inventory inventory = player.getInventory();
            for (int slot = 0; slot < inventory.items.size() && stick.isEmpty(); slot++) {
                if (KitchenBasin.isTool(view.level(), inventory.items.get(slot))) {
                    stick = inventory.items.get(slot).copyWithCount(1);
                }
            }
        }
        if (ItemStack.isSameItem(stick, restingStick)) {
            return;
        }
        restingStick = stick;
        ItemStack rest = stick;
        view.node("stick").ifPresent(node -> {
            node.setVisible(!rest.isEmpty());
            if (!rest.isEmpty()) {
                show(node, rest, STICK_SIZE);
                // The stick's icon runs corner to corner: turned to lie along the rim.
                node.rotateTo(0.0F, -ALONG, 0.0F, 0.0F);
            }
        });
    }

    /** Shows {@code stack} on {@code node} lying flat, {@code size} pixels across, built from its icon where it has one. */
    private static void show(NodeView node, ItemStack stack, float size) {
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

    /** Where compartment {@code place} has its middle on its floor, scene pixels: 0 on the cook's left. */
    private static Vector3f middleOf(int place) {
        return new Vector3f(place == 0 ? 11.25F : 4.75F, FLOOR, 8.0F);
    }

    @Override
    public void frame(StationView view, float dt) {
        clock += dt;
        if (!steps.isEmpty()) {
            List<Step> due = steps.stream().filter(step -> step.at() <= clock).toList();
            steps.removeAll(due);
            due.forEach(step -> step.run().run());
        }
        book.frame(view, dt);
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
            book.click(view, click.button());
            return;
        }
        if ("stick".equals(click.node())) {
            CompoundTag how = new CompoundTag();
            how.putBoolean(BasinStation.ALL, Screen.hasShiftDown());
            view.sendAction(BasinStation.ACTION_SKEWER, how);
            return;
        }
        int place = compartmentOf(click.node());
        if (place >= 0 && !shown[place].isEmpty()) {
            // An empty hand takes back what lies there, as at the basin itself.
            CompoundTag at = new CompoundTag();
            at.putInt(BasinStation.PLACE, place);
            view.sendAction(BasinStation.ACTION_TAKE, at);
        }
    }

    private static int compartmentOf(String node) {
        if (node == null) {
            return -1;
        }
        if (node.startsWith("compartment_") || node.startsWith("food_")) {
            return node.charAt(node.indexOf('_') + 1) == '0' ? 0 : 1;
        }
        return -1;
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

    /**
     * A stick on the cursor clicked on the basin skewers what lies there (with shift as many as it can); food let go over a compartment
     * goes into it, the whole stack, or one with the right button.
     */
    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if ("book_open".equals(node)) {
            book.click(view, button);
            return true;
        }
        CompoundTag how = new CompoundTag();
        if (KitchenBasin.isTool(view.level(), stack)) {
            how.putBoolean(BasinStation.ALL, shift);
            view.sendAction(BasinStation.ACTION_SKEWER, how);
            return true;
        }
        int place = compartmentOf(node);
        if (place < 0) {
            place = view.cursorOn(4.0F).map(point -> point.x > 8.0F ? 0 : 1).orElse(-1);
        }
        how.putInt(BasinStation.PLACE, place);
        how.putBoolean(BasinStation.ALL, button == 0);
        view.sendAction(BasinStation.ACTION_PUT, how);
        return true;
    }

    /** Shift + click in the inventory: food goes into the compartment holding it or a free one, a stick skewers all it can. */
    @Override
    public boolean onInventoryShiftClick(StationView view, int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        CompoundTag how = new CompoundTag();
        how.putInt(BasinStation.FROM, slot);
        how.putBoolean(BasinStation.ALL, true);
        if (KitchenBasin.isTool(view.level(), stack)) {
            view.sendAction(BasinStation.ACTION_SKEWER, how);
            return true;
        }
        if (!KitchenBasin.isFood(view.level(), stack)) {
            return false;
        }
        view.sendAction(BasinStation.ACTION_PUT, how);
        return true;
    }

    @Override
    public void onEffect(StationView view, ResourceLocation effect, String node, CompoundTag data) {
        if (BasinStation.EFFECT_SKEWERED.equals(effect)) {
            skewered(view, data);
        } else if (BasinStation.EFFECT_PUT.equals(effect)) {
            view.playSound(SoundEvents.WOOD_HIT, "basin", 0.5F, 1.2F);
        } else if (BasinStation.EFFECT_TAKEN.equals(effect)) {
            view.playSound(SoundEvents.ITEM_PICKUP, "basin", 0.3F, 1.5F);
        } else if (BasinStation.EFFECT_REFUSED.equals(effect)) {
            ItemStack refused = ItemStack.parseOptional(view.level().registryAccess(), data.getCompound(BasinStation.STACK));
            ghost(view, refused).ifPresent(ghost -> {
                ghost.moveTo(0.0F, 4.0F, 0.0F, 0.0F);
                ghost.moveTo(0.0F, 1.0F, 0.0F, 0.09F);
                after(0.09F, () -> ghost.hopTo(0.0F, 4.0F, -6.0F, 1.2F, 0.22F));
                after(0.31F, () -> ghost.setVisible(false));
            });
            view.playSound(SoundEvents.WOOD_HIT, "basin", 0.4F, 0.8F);
        }
    }

    /**
     * A skewer made, as everybody sees it: a stick comes to lie over the basin, its point away from the cook. Each piece comes up from
     * its compartment, turns across the stick at its point and is pushed down it onto the ones before (food and side by turns), the
     * first going farthest, to the handle. The full skewer then goes to the cook as it is.
     */
    private void skewered(StationView view, CompoundTag data) {
        ItemStack result = ItemStack.parseOptional(view.level().registryAccess(), data.getCompound(BasinStation.STACK));
        ItemStack food = ItemStack.parseOptional(view.level().registryAccess(), data.getCompound(BasinStation.FOOD));
        ItemStack side = ItemStack.parseOptional(view.level().registryAccess(), data.getCompound(BasinStation.SIDE));
        int foodPlace = data.getInt(BasinStation.PLACE);
        int foodCount = data.getInt(BasinStation.FOOD_COUNT);
        int sideCount = side.isEmpty() ? 0 : data.getInt(BasinStation.SIDE_COUNT);
        if (result.isEmpty()) {
            return;
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0, f = 0, s = 0; i < BEADS && (f < foodCount || s < sideCount); i++) {
            boolean takeSide = s < sideCount && (f >= foodCount || i % 2 == 1);
            order.add(takeSide ? 1 : 0);
            if (takeSide) {
                s++;
            } else {
                f++;
            }
        }
        ItemStack stickStack = restingStick.isEmpty() ? new ItemStack(net.minecraft.world.item.Items.STICK) : restingStick;
        // The stick's middle line runs through the scene node's own point, so that the pieces, centred the same way, sit on it.
        Vector3f stickAt = turnedY(middle(stickStack, STICK_SIZE), ALONG).negate();
        List<Vector3f> placed = new ArrayList<>();
        placed.add(stickAt);
        Optional<NodeView> stick = view.node("skewer");
        stick.ifPresent(node -> {
            show(node, stickStack, STICK_SIZE);
            // Laid flat, the stick's icon runs from its handle at the bottom left to its point at the top right: turned, point away.
            node.rotateTo(0.0F, ALONG, 0.0F, 0.0F);
            node.moveTo(stickAt.x, stickAt.y - 1.9F, stickAt.z - 6.5F, 0.0F);
            node.scaleTo(1.0F, 0.0F);
            node.setVisible(true);
            node.hopTo(stickAt.x, stickAt.y, stickAt.z, 1.5F, STICK_IN);
        });
        view.playSound(SoundEvents.WOOD_PLACE, "basin", 0.35F, 1.4F);
        for (int i = 0; i < order.size(); i++) {
            int bead = i;
            boolean isSide = order.get(i) == 1;
            ItemStack piece = isSide ? side : food;
            int from = isSide ? 1 - foodPlace : foodPlace;
            // Stood across the stick, the piece is moved so that the middle of what its icon draws is on the stick's line.
            Vector3f centre = turnedX(middle(piece, BEAD_SIZE), ACROSS).negate();
            Vector3f rest = new Vector3f(centre).add(0.0F, 0.0F, HANDLE + bead * SPACING);
            placed.add(rest);
            after(STICK_IN + bead * BEAD_EVERY, () -> view.node("bead_" + bead).ifPresent(node -> {
                show(node, piece, BEAD_SIZE);
                Vector3f start = middleOf(from).sub(8.0F, OVER, 8.0F);
                node.moveTo(start.x, start.y, start.z, 0.0F);
                node.rotateTo(0.0F, yaw(from, bead), 0.0F, 0.0F);
                node.scaleTo(1.0F, 0.0F);
                node.setVisible(true);
                // Up to the point, turning to stand square across the stick.
                node.hopTo(centre.x, centre.y, centre.z + POINT, 2.0F, BEAD_HOP);
                node.rotateTo(ACROSS, 0.0F, 0.0F, BEAD_HOP);
                after(BEAD_HOP, () -> {
                    view.playSound(SoundEvents.HONEY_BLOCK_HIT, "basin", 0.45F, 1.6F + random.nextFloat() * 0.2F);
                    node.moveTo(rest.x, rest.y, rest.z, PUSH);
                });
                after(BEAD_HOP + PUSH, () -> view.playSound(SoundEvents.HONEY_BLOCK_STEP, "basin", 0.3F, 1.3F));
            }));
        }
        // Full, it rests a moment over the basin, for the cook to see what was made.
        float done = STICK_IN + Math.max(0, order.size() - 1) * BEAD_EVERY + BEAD_HOP + PUSH + SHOWN;
        after(done, () -> {
            Vector3f away = FromPlayer.offset(view, OVER, 0, 1);
            List<NodeView> parts = new ArrayList<>();
            stick.ifPresent(parts::add);
            for (int i = 0; i < order.size(); i++) {
                view.node("bead_" + i).ifPresent(parts::add);
            }
            for (int i = 0; i < parts.size() && i < placed.size(); i++) {
                // Every part goes the same way from where it is: the skewer leaves whole.
                Vector3f at = placed.get(i);
                parts.get(i).hopTo(at.x + away.x, at.y + 2.0F, at.z + away.z, 2.0F, AWAY_TIME);
                parts.get(i).scaleTo(0.0F, AWAY_TIME);
            }
            after(AWAY_TIME, () -> parts.forEach(part -> part.setVisible(false)));
            view.playSound(SoundEvents.BUNDLE_INSERT, "basin", 0.6F, 1.0F);
        });
    }

    /**
     * The middle of what {@code stack}'s icon draws, in its node's own space, as {@link #show} builds it {@code size} across: an icon
     * is laid out by its whole canvas, and what it draws seldom sits in its middle.
     */
    private static Vector3f middle(ItemStack stack, float size) {
        Optional<ItemVoxels.Icon> icon = ItemVoxels.icon(stack);
        if (icon.isEmpty()) {
            return new Vector3f();
        }
        ItemVoxels.Icon it = icon.get();
        int[] bounds = it.bounds();
        float pixel = it.pixel(size);
        return new Vector3f((it.width() / 2.0F - (bounds[0] + bounds[1]) / 2.0F - 0.5F) * pixel, pixel / 2.0F,
                (it.height() / 2.0F - (bounds[2] + bounds[3]) / 2.0F - 0.5F) * pixel);
    }

    /** {@code point} turned {@code degrees} about y, as a node's rotation turns it. */
    private static Vector3f turnedY(Vector3f point, float degrees) {
        float a = degrees * Mth.DEG_TO_RAD;
        return new Vector3f(point.x * Mth.cos(a) + point.z * Mth.sin(a), point.y, -point.x * Mth.sin(a) + point.z * Mth.cos(a));
    }

    /** {@code point} turned {@code degrees} about x, as a node's rotation turns it. */
    private static Vector3f turnedX(Vector3f point, float degrees) {
        float a = degrees * Mth.DEG_TO_RAD;
        return new Vector3f(point.x, point.y * Mth.cos(a) - point.z * Mth.sin(a), point.y * Mth.sin(a) + point.z * Mth.cos(a));
    }

    private Optional<NodeView> ghost(StationView view, ItemStack stack) {
        ghost = (ghost + 1) % GHOSTS;
        Optional<NodeView> node = view.node("lift_" + ghost);
        node.ifPresent(it -> {
            show(it, stack, PIECE_SIZE);
            it.rotateTo(0.0F, 0.0F, 0.0F, 0.0F);
            it.scaleTo(1.0F, 0.0F);
            it.moveTo(0.0F, 0.0F, 0.0F, 0.0F);
            it.setVisible(true);
        });
        return node;
    }
}
