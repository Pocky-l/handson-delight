package com.pockyl.handson_delight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.MeshShape;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.station.CuisineBoardStation;
import com.pockyl.handson_delight.station.CuisineLogic;
import com.pockyl.handson_delight.station.Cutting;
import com.pockyl.handson_delight.station.KitchenCuisine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Youkai's Feasts' cuisine board on the client. What lies on it is drawn with the board's own models, as its renderer draws them;
 * every step shows as it is done: food comes down from the cook onto the board, a sauce is poured out of its bottle, rice is
 * pressed into nigiri, a sheet is rolled up under the hands, a knife cuts the roll into its pieces, which go to the cook. Held over
 * the board, the food on the cursor shows see-through where it would go; food an empty hand would shape or roll rises under it.
 */
public final class CuisineBehavior implements StationBehavior {
    private static final int PARTS = 12;
    private static final int FLIERS = 4;
    private static final int PIECES = 6;
    private static final int GHOSTS = 3;
    /**
     * Moves are offsets from a node's own place: the parts and previews stand at the block's middle on the board (8, 0, 8) and
     * draw the models from the block's corner; the knife's turn stands at (8, 1, 8).
     */
    private static final Vector3f REST = new Vector3f();
    private static final float KNIFE_BASE = 1.0F;
    /** How see-through the next step stands over the board, and the knife offered from the inventory. */
    private static final int PREVIEW = 0x66FFFFFF;
    private static final int OFFERED = 0x8CFFFFFF;
    /** How far food rises under an empty hand that would shape or roll it, pixels, and how fast. */
    private static final float LIFT = 0.5F;
    private static final float LIFT_TIME = 0.1F;
    /**
     * The knife lies across the roll, edge down, its tip away from the cook, turned this much towards the cook's left so that the
     * camera sees its blade and not only its back; its handle is that far back from the middle of the icon.
     */
    private static final float KNIFE_SKEW = 20.0F;
    private static final float KNIFE_BACK = 2.0F;
    /** Where food lands on the board, how high, how big it flies in and how long it takes. */
    private static final float LAND_Y = 2.5F;
    private static final float FLY_SIZE = 5.0F;
    private static final float FLY_TIME = 0.3F;
    private static final float POP = 0.16F;
    private static final float GAP = 0.06F;
    private static final float POUR_SIZE = 5.0F;
    private static final float POUR_HEIGHT = 4.0F;
    /** The shaping press and the roll under the hands. */
    private static final float PRESS = 0.12F;
    /**
     * Rolling up: the roll comes over from the cook's edge of the sheet to the middle, turning over and over, while the sheet and
     * what lies on it draw in under it towards the far edge. The sheet's far edge (5 px from the middle, at its top 1 px up), the
     * roll's middle (3 px up, in the middle), where it starts (towards the cook) and how far it turns on the way, degrees.
     */
    private static final float ROLL_TIME = 0.55F;
    private static final float SHEET_HALF = 5.0F;
    private static final float SHEET_Y = 1.0F;
    private static final float ROLL_MIDDLE = 3.0F;
    private static final float ROLL_START = 3.5F;
    private static final float ROLL_TURNS = 330.0F;
    /**
     * Cutting, as at the board: the next cut is a dashed white line across the dish, the knife follows the cursor over the dish
     * this high over its top and stands right over the line within {@link #SNAP} of it; a click there cuts, off it the knife only
     * knocks. Lines already cut stay as thin dark lines.
     */
    private static final float HOVER = 2.4F;
    private static final float SNAP = 1.4F;
    private static final float CHOP = 0.16F;
    private static final float CHOP_DEPTH = 2.8F;
    private static final float MARK_WIDTH = 0.16F;
    private static final float DASH = 0.6F;
    private static final float DASH_GAP = 0.45F;
    private static final int MARK_COLOR = 0xA0FFFFFF;
    private static final int SCORE_COLOR = 0xC0281C12;
    private static final int SHADE_COLOR = 0x8C1A120C;
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("block/white_concrete");
    /** The box of a finished dish (x, y, z from and to) when its models cannot be read: a roll lying along x. */
    private static final float[] ROLL_BOX = {2.0F, 14.0F, 1.0F, 5.0F, 6.0F, 10.0F};
    private static final Direction[] SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST,
            Direction.EAST};
    private static final float PIECE_SIZE = 3.4F;
    private static final float AWAY = 0.35F;

    private final CuisineBook pages = new CuisineBook(this);
    private final RecipeBook book = new RecipeBook(pages);
    private final List<Step> steps = new ArrayList<>();
    /** What the scene draws as lying on the board: lags the board while a step is being shown. */
    private List<ItemStack> shown = List.of();
    private final List<ResourceLocation> shownModels = new ArrayList<>();
    private List<ResourceLocation> previewModels = List.of();
    private Object previewFor;
    private boolean lifted;
    private KitchenMotions.Motion hand;
    private float clock;
    /** Until when steps already told are being shown: what the board holds then is not drawn yet. */
    private float busyUntil;
    private int ghost;
    private boolean cutting;
    /** The box of what lies on the board, scene pixels, read from the models it was read for. */
    private float[] box = ROLL_BOX.clone();
    private Object measuredFor;
    private int drawnMarks;
    /** The knife the cook cuts with now (on the cursor, or offered from the inventory), and over which mark it stands, or -1. */
    private ItemStack knife = ItemStack.EMPTY;
    private boolean offered;
    private int overMark = -1;
    /** Seconds into the knife's stroke, NaN when it is not striking; the mark asked for and when, so one is not asked for twice. */
    private float chopAge = Float.NaN;
    private int askedMark = -1;
    private float askedAt;
    /**
     * A stack put on from the cursor: once the board has it, what is left on the cursor goes back into the inventory unless the
     * next step wants it, so the cursor does not cover the board. What lay there before, and when it was sent.
     */
    private List<ItemStack> putFrom;
    private float putAt;

    private record Step(float at, Runnable run) {
    }

    public CuisineBehavior(StationView view) {
    }

    static KitchenCuisine board(StationView view) {
        return KitchenCuisine.of(view.level().getBlockEntity(view.pos()));
    }

    /** What lies on the board now, as the scene shows it. */
    List<ItemStack> shown() {
        return shown;
    }

    @Override
    public void onSceneReady(StationView view) {
        steps.clear();
        busyUntil = 0.0F;
        shownModels.clear();
        previewModels = List.of();
        previewFor = null;
        cutting = false;
        if (hand != null) {
            hand.stop();
            hand = null;
        }
        view.node("book_open").ifPresent(node -> node.setPainter(book));
        KitchenCuisine board = board(view);
        showContents(view, board == null ? List.of() : board.handson$contents(), false);
    }

    @Override
    public void onOpen(StationView view) {
        book.opened(view);
        KitchenCuisine board = board(view);
        if (board == null) {
            return;
        }
        view.setInventoryFilter(stack -> board.handson$takes(stack) > 0 || CuisineBoardStation.isStep(board, stack)
                || Cutting.isTool(view.level(), stack));
        // What the cook came with is meant for the board: the next step goes on. A knife stays in its slot, offered over the dish.
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && view.openedByUse() && player.inventoryMenu.getCarried().isEmpty()) {
            ItemStack held = player.getMainHandItem();
            CompoundTag how = new CompoundTag();
            how.putInt(CuisineBoardStation.FROM, player.getInventory().selected);
            if (board.handson$takes(held) > 0) {
                view.sendAction(CuisineBoardStation.ACTION_PUT, how);
            }
        }
    }

    @Override
    public void onClose(StationView view) {
        book.shut(view);
    }

    @Override
    public void tick(StationView view) {
        KitchenCuisine board = board(view);
        if (board == null) {
            return;
        }
        putAway(view, board);
        if (clock >= busyUntil && steps.isEmpty() && !cutting && !sameStacks(board.handson$contents(), shown)) {
            // Changed by somebody's own click on the board, or a step not shown: drawn as it is, what came on popping up.
            showContents(view, board.handson$contents(), true);
        }
        measure();
        marks(view, board);
        preview(view, board);
        for (int i = 0; i < GHOSTS; i++) {
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
        if (hand != null && !hand.frame(view, dt)) {
            hand = null;
        }
        KitchenCuisine board = board(view);
        if (board != null) {
            knife(view, board, dt);
        }
        book.frame(view, dt);
    }

    /**
     * After a step from the cursor went on, the rest of the stack goes back into the inventory when the next step has no use for
     * it: rice for a nigiri, whose next step is the hand's. It stays for a step that takes it again (a second rice for a California
     * sheet). A dish picked in the book that what lies on the board is the start of decides which step is next.
     */
    private void putAway(StationView view, KitchenCuisine board) {
        if (putFrom == null) {
            return;
        }
        List<ItemStack> on = board.handson$contents();
        if (clock - putAt > 2.0F) {
            putFrom = null;
            return;
        }
        if (sameStacks(on, putFrom)) {
            return;
        }
        putFrom = null;
        LocalPlayer player = Minecraft.getInstance().player;
        ItemStack carried = player == null ? ItemStack.EMPTY : player.inventoryMenu.getCarried();
        if (!carried.isEmpty() && !wanted(view, board, on, carried)) {
            view.putAwayCarried();
        }
    }

    /** Whether the board's next step takes {@code stack}, for the dish picked in the book when the board is on the way to it. */
    private boolean wanted(StationView view, KitchenCuisine board, List<ItemStack> on, ItemStack stack) {
        if (board.handson$takes(stack) <= 0) {
            return false;
        }
        KitchenCuisine.Dish dish = CuisineBook.dish(view, book.pickedKey());
        if (dish == null || CuisineLogic.done(dish, on) < 0) {
            return true;
        }
        List<ItemStack> next = new ArrayList<>(on);
        next.add(stack.copyWithCount(1));
        return CuisineLogic.done(dish, next) >= 0;
    }

    private void after(float seconds, Runnable run) {
        steps.add(new Step(clock + seconds, run));
    }

    /** When the next thing told can start: after what is being shown. */
    private float start() {
        return Math.max(0.0F, busyUntil - clock);
    }

    /** Draws {@code contents} with the board's models; with {@code pop}, a part that was not there comes up out of the board. */
    private void showContents(StationView view, List<ItemStack> contents, boolean pop) {
        KitchenCuisine board = board(view);
        List<ResourceLocation> models = board == null ? null : board.handson$modelsOf(contents);
        if (models == null) {
            models = board == null ? List.of() : board.handson$models();
        }
        shown = copy(contents);
        float turn = turn(view);
        Set<ResourceLocation> before = new HashSet<>(shownModels);
        for (int k = 0; k < PARTS; k++) {
            ResourceLocation model = k < models.size() ? models.get(k) : null;
            boolean fresh = model != null && !before.contains(model);
            view.node("part_" + k).ifPresent(node -> {
                node.setVisible(model != null);
                if (model == null) {
                    return;
                }
                node.setVisual(Visual.ofModel(model));
                node.rotateTo(0.0F, turn, 0.0F, 0.0F);
                node.setTint(0xFFFFFFFF);
                if (pop && fresh) {
                    node.moveTo(REST.x, 1.2F, REST.z, 0.0F);
                    node.scaleTo(0.85F, 0.0F);
                    node.moveTo(REST.x, REST.y, REST.z, POP);
                    node.scaleTo(1.0F, POP);
                } else {
                    node.moveTo(REST.x, REST.y, REST.z, 0.0F);
                    node.scaleTo(1.0F, 0.0F);
                }
            });
        }
        shownModels.clear();
        shownModels.addAll(models.subList(0, Math.min(models.size(), PARTS)));
        previewFor = null;
        lifted = false;
    }

    /**
     * Held over the board, the parts the food on the cursor would add stand there see-through. With an empty hand over food the
     * hand would shape or roll, nothing turns see-through (food going see-through under the hand reads as wrong): it rises a little.
     */
    private void preview(StationView view, KitchenCuisine board) {
        LocalPlayer player = Minecraft.getInstance().player;
        // With a stack on the cursor the drop zone is what the cursor is over, with an empty hand the board's surface.
        boolean over = view.isFocused() && player != null && clock >= busyUntil && !cutting
                && (hovered(view, "surface") || hovered(view, "mouth"));
        ItemStack carried = player == null ? ItemStack.EMPTY : player.inventoryMenu.getCarried();
        lift(view, over && carried.isEmpty() && board.handson$byHand() && overDish(view).isPresent());
        List<ItemStack> next = null;
        if (over && !carried.isEmpty() && board.handson$takes(carried) > 0) {
            next = new ArrayList<>(shown);
            next.add(carried.copyWithCount(1));
        }
        Object key = next == null ? null : List.of(carried.getItem(), shown.size(), shownModels.hashCode());
        if (Objects.equals(key, previewFor)) {
            return;
        }
        previewFor = key;
        List<ResourceLocation> models = next == null ? null : board.handson$modelsOf(next);
        previewModels = models == null ? List.of() : models.stream().filter(model -> !shownModels.contains(model)).toList();
        float turn = turn(view);
        for (int k = 0; k < PARTS; k++) {
            ResourceLocation model = k < previewModels.size() ? previewModels.get(k) : null;
            view.node("preview_" + k).ifPresent(node -> {
                node.setVisible(model != null);
                if (model != null) {
                    node.setVisual(Visual.ofModel(model).asTranslucent());
                    node.setTint(PREVIEW);
                    node.rotateTo(0.0F, turn, 0.0F, 0.0F);
                    node.moveTo(REST.x, REST.y + 0.02F, REST.z, 0.0F);
                }
            });
        }
    }

    /**
     * The food a click of the empty hand would shape or roll rises a little while the cursor is on it, and settles when it leaves.
     * Once a step is being shown its own moves take the parts over.
     */
    private void lift(StationView view, boolean up) {
        if (up == lifted) {
            return;
        }
        lifted = up;
        if (!up && clock < busyUntil) {
            return;
        }
        visibleParts(view).forEach(part -> part.moveTo(REST.x, REST.y + (up ? LIFT : 0.0F), REST.z, LIFT_TIME));
    }

    private static boolean hovered(StationView view, String node) {
        return view.node(node).map(NodeView::isHovered).orElse(false);
    }

    /**
     * The knife the cook cuts a finished dish with: the one on the cursor, or with an empty hand the first in the inventory that
     * cuts it, offered see-through. Over the dish it follows the cursor along it and stands right over the next mark when near
     * it, the cursor's own stack hidden meanwhile; away from the dish an offered knife waits over the next mark, one on the cursor
     * is the cursor's. Struck, it comes down and up again.
     */
    private void knife(StationView view, KitchenCuisine board, float dt) {
        LocalPlayer player = Minecraft.getInstance().player;
        ItemStack finished = board.handson$finished();
        boolean ready = view.isFocused() && player != null && !finished.isEmpty() && !cutting && clock >= busyUntil;
        ItemStack carried = player == null ? ItemStack.EMPTY : player.inventoryMenu.getCarried();
        boolean striking = !Float.isNaN(chopAge);
        if (!striking) {
            knife = ItemStack.EMPTY;
            offered = false;
            if (ready && !carried.isEmpty() && CuisineBoardStation.cuts(view.level(), board, carried)) {
                knife = carried;
            } else if (ready && carried.isEmpty()) {
                int slot = Cutting.toolSlot(view.level(), finished, player.getInventory());
                knife = slot >= 0 ? player.getInventory().getItem(slot) : ItemStack.EMPTY;
                offered = !knife.isEmpty();
            }
        }
        Optional<Vector3f> over = knife.isEmpty() ? Optional.empty() : overDish(view);
        boolean drawn = !knife.isEmpty() && (over.isPresent() || offered);
        view.setCarriedHidden(drawn && !offered);
        Optional<NodeView> node = view.node("knife");
        node.ifPresent(it -> it.setVisible(drawn));
        if (!drawn || node.isEmpty()) {
            overMark = -1;
            return;
        }
        showKnife(view, node.get(), knife);
        node.get().setTint(offered && !striking ? OFFERED : 0xFFFFFFFF);
        int next = nextMark(view, finished);
        float markX = next >= 0 ? markX(view, finished, next) : (box[0] + box[1]) / 2.0F;
        float x = over.map(point -> Mth.clamp(point.x, box[0], box[1])).orElse(markX);
        if (!striking) {
            overMark = next >= 0 && Math.abs(x - markX) < SNAP ? next : -1;
        }
        if (overMark >= 0) {
            x = markX;
        }
        float y = box[3] + HOVER;
        if (striking) {
            chopAge += dt;
            float t = chopAge / CHOP;
            y -= CHOP_DEPTH * (t < 0.5F ? KitchenMotions.out(t * 2.0F) : 1.0F - KitchenMotions.smooth(t * 2.0F - 1.0F));
            if (chopAge >= CHOP) {
                chopAge = Float.NaN;
            }
        }
        Vector3f at = knifeAt(turn(view), x, y);
        view.node("knife_turn").ifPresent(turn -> turn.moveTo(at.x, at.y, at.z, 0.0F));
    }

    /** Where the cursor points on the dish's top, scene pixels, while it is over the dish. */
    private Optional<Vector3f> overDish(StationView view) {
        return view.cursorOn(box[3]).filter(point -> point.x > box[0] - 1.0F && point.x < box[1] + 1.0F
                && point.z > box[4] - 2.0F && point.z < box[5] + 2.0F);
    }

    /** How many marks the finished dish is cut at, as the board would cut it, and how many are cut. */
    private static int markCount(StationView view, ItemStack finished) {
        return finished.isEmpty() ? 0 : Cutting.plan(view.level(), finished).marks();
    }

    /** The next mark to cut, or -1 without a knife that cuts the dish lying there. */
    private int nextMark(StationView view, ItemStack finished) {
        if (knife.isEmpty()) {
            return -1;
        }
        int count = markCount(view, finished);
        int cuts = view.state().data().getInt(CuisineBoardStation.CUTS);
        return cuts < count ? cuts : -1;
    }

    /** Where mark {@code k} lies along the dish: square across it, evenly between its ends, as a roll is sliced. */
    private float markX(StationView view, ItemStack finished, int k) {
        return box[0] + (box[1] - box[0]) * (k + 1.0F) / (markCount(view, finished) + 1.0F);
    }

    /**
     * The marks on the finished dish: those cut are thin dark lines over its top, for everybody; the next one is a dashed white
     * line for a cook with a knife, as at the board.
     */
    private void marks(StationView view, KitchenCuisine board) {
        ItemStack finished = board.handson$finished();
        boolean drawn = !finished.isEmpty() && !cutting && clock >= busyUntil;
        int count = drawn ? markCount(view, finished) : 0;
        int cuts = drawn ? Math.min(view.state().data().getInt(CuisineBoardStation.CUTS), count) : 0;
        int next = drawn && !knife.isEmpty() && cuts < count ? cuts : -1;
        int key = Objects.hash(count, cuts, next, measuredFor, drawn);
        if (key == drawnMarks) {
            return;
        }
        drawnMarks = key;
        view.node("scored").ifPresent(node -> {
            node.setVisible(cuts > 0);
            if (cuts > 0) {
                MeshShape.Builder shape = MeshShape.builder();
                for (int k = 0; k < cuts; k++) {
                    line(shape, markX(view, finished, k), box[5] - box[4], 0.0F, MARK_WIDTH, 0.03F);
                }
                node.setShape(shape.build(), WHITE, false);
                node.setTint(SCORE_COLOR);
            }
        });
        // White dashes over a dark shade a little wider: they show on dark kelp and on white rice alike.
        for (String id : new String[] {"marks_shade", "marks"}) {
            boolean shade = id.equals("marks_shade");
            view.node(id).ifPresent(node -> {
                node.setVisible(next >= 0);
                if (next >= 0) {
                    MeshShape.Builder shape = MeshShape.builder();
                    line(shape, markX(view, finished, next), shade ? DASH + 0.2F : DASH, shade ? DASH_GAP - 0.2F : DASH_GAP,
                            shade ? MARK_WIDTH * 2.2F : MARK_WIDTH, shade ? 0.02F : 0.04F);
                    node.setShape(shape.build(), WHITE, !shade);
                    node.setTint(shade ? SHADE_COLOR : MARK_COLOR);
                }
            });
        }
    }

    /**
     * A line square across the dish at {@code x}, {@code width} either side of it, {@code lift} over its top, in dashes {@code dash}
     * long with {@code gap} between them, in the space of a node standing at the block's middle.
     */
    private void line(MeshShape.Builder shape, float x, float dash, float gap, float width, float lift) {
        float y = box[3] + lift;
        float left = x - 8.0F - width;
        float right = x - 8.0F + width;
        for (float z = box[4]; z < box[5] - 0.01F; z += dash + gap) {
            float from = z - 8.0F;
            float to = Math.min(box[5], z + dash) - 8.0F;
            shape.corner(right, y, from, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F)
                    .corner(left, y, from, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F)
                    .corner(left, y, to, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F)
                    .corner(right, y, to, 1.0F, 1.0F, 0.0F, 1.0F, 0.0F);
        }
    }

    /** The box of what lies on the board (x, y, z from and to, scene pixels), read from its models' faces once they change. */
    private void measure() {
        List<ResourceLocation> models = List.copyOf(shownModels);
        if (models.equals(measuredFor)) {
            return;
        }
        measuredFor = models;
        float[] found = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE};
        RandomSource random = RandomSource.create(42L);
        for (ResourceLocation id : models) {
            BakedModel model = ClientPlatform.INSTANCE.model(id);
            if (model == null) {
                continue;
            }
            for (Direction side : SIDES) {
                for (BakedQuad quad : model.getQuads(null, side, random)) {
                    int[] data = quad.getVertices();
                    int stride = data.length / 4;
                    for (int corner = 0; corner < 4; corner++) {
                        for (int axis = 0; axis < 3; axis++) {
                            float value = Float.intBitsToFloat(data[corner * stride + axis]) * 16.0F;
                            found[axis * 2] = Math.min(found[axis * 2], value);
                            found[axis * 2 + 1] = Math.max(found[axis * 2 + 1], value);
                        }
                    }
                }
            }
        }
        // The models are drawn unturned (turn 0), so their own space is the scene's.
        box = found[0] < found[1] ? found : ROLL_BOX.clone();
    }

    private static void showKnife(StationView view, NodeView node, ItemStack knife) {
        if (node.visual().flatMap(Visual::item).map(item -> item != knife.getItem()).orElse(true)) {
            node.setVisual(Visual.ofItem(knife.getItem()).withAnchor(new Vector3f(8.0F, 8.0F, 8.0F)));
        }
        float yaw = knifeYaw(turn(view));
        view.node("knife_turn").ifPresent(turn -> turn.rotateTo(0.0F, yaw, 0.0F, 0.0F));
    }

    /**
     * The yaw the board's models are drawn with: turned to the cook, rolls lying across in front of them, as the board shows
     * them to whoever placed it (its facing points away from them, its renderer turns its models by the facing). The world
     * shows them the same way while the scene is drawn.
     */
    static float turn(StationView view) {
        return 0.0F;
    }

    /** {@code (x, y, z)} of the models' own space, from the middle of the block, as an offset in the scene. */
    private static Vector3f toScene(float turn, float x, float y, float z) {
        return new Vector3f(x, y, z).rotateY(turn * Mth.DEG_TO_RAD);
    }

    /** Which way a roll runs in the scene: the models' own x. */
    private static Vector3f along(float turn) {
        return toScene(turn, 1.0F, 0.0F, 0.0F);
    }

    /** Where the knife's tip points: across the roll, away from the cook (or to the cook's left for a roll running away). */
    private static Vector3f knifeTip(float turn) {
        Vector3f d = along(turn);
        Vector3f tip = new Vector3f(-d.z, 0.0F, d.x);
        if (Math.abs(tip.z) > 0.001F ? tip.z < 0.0F : tip.x < 0.0F) {
            tip.negate();
        }
        return tip.rotateY(KNIFE_SKEW * Mth.DEG_TO_RAD);
    }

    /** The knife's yaw: its icon is laid with the tip to the scene's -x (its rotation in the scene file), edge down. */
    private static float knifeYaw(float turn) {
        Vector3f tip = knifeTip(turn);
        return (float) Math.toDegrees(Math.atan2(tip.z, -tip.x));
    }

    /** The offset of the knife's turn for a cut at {@code spot} along the roll (2..14), its middle {@code y} high. */
    private static Vector3f knifeAt(float turn, float spot, float y) {
        Vector3f d = along(turn);
        Vector3f tip = knifeTip(turn);
        return new Vector3f(d.x * (spot - 8.0F) - tip.x * KNIFE_BACK, y - KNIFE_BASE, d.z * (spot - 8.0F) - tip.z * KNIFE_BACK);
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (!(event instanceof SceneEvent.Click click)) {
            return;
        }
        if ("book_open".equals(click.node())) {
            book.click(view);
            return;
        }
        KitchenCuisine board = board(view);
        if (board == null || !"surface".equals(click.node()) && !"mouth".equals(click.node())) {
            return;
        }
        if (click.button() == 1) {
            // A right click takes the last step back, as the board takes back what lies on it.
            view.sendAction(CuisineBoardStation.ACTION_BACK, new CompoundTag());
            return;
        }
        if (board.handson$byHand()) {
            view.sendAction(CuisineBoardStation.ACTION_SHAPE, new CompoundTag());
        } else if (!board.handson$finished().isEmpty()) {
            // A knife offered over the dish cuts with a left click (over the dish; the rest of the board does nothing then),
            // as at the board; without one the dish goes to the cook.
            if (offered) {
                if (overDish(view).isPresent()) {
                    chop(view);
                }
            } else {
                view.sendAction(CuisineBoardStation.ACTION_TAKE, new CompoundTag());
            }
        }
    }

    /** The knife comes down: on the next mark it cuts there, off the marks it only knocks on the dish. */
    private void chop(StationView view) {
        if (!Float.isNaN(chopAge)) {
            return;
        }
        chopAge = 0.0F;
        if (overMark < 0) {
            view.playSound(SoundEvents.WOOD_HIT, "surface", 0.25F, 1.6F);
            return;
        }
        if (overMark == askedMark && clock - askedAt < 1.0F) {
            return;
        }
        askedMark = overMark;
        askedAt = clock;
        CompoundTag how = new CompoundTag();
        how.putInt(CuisineBoardStation.MARK, overMark);
        view.sendAction(CuisineBoardStation.ACTION_CUT, how);
    }

    /**
     * A stack let go over the board: a knife cuts the finished roll; food goes on as the next step, with shift as many steps of it
     * as the board takes in a row.
     */
    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if ("book_open".equals(node)) {
            book.click(view);
            return true;
        }
        KitchenCuisine board = board(view);
        if (board == null) {
            return false;
        }
        CompoundTag how = new CompoundTag();
        if (board.handson$takes(stack) <= 0 && CuisineBoardStation.cuts(view.level(), board, stack)) {
            if (overDish(view).isPresent()) {
                chop(view);
            }
            return true;
        }
        how.putBoolean(CuisineBoardStation.ALL, shift);
        view.sendAction(CuisineBoardStation.ACTION_PUT, how);
        putFrom = copy(board.handson$contents());
        putAt = clock;
        return true;
    }

    /**
     * Shift + click in the inventory: a knife cuts the finished roll at every mark left at once, food goes on as many steps as the
     * board takes of it.
     */
    @Override
    public boolean onInventoryShiftClick(StationView view, int slot, ItemStack stack) {
        KitchenCuisine board = board(view);
        if (stack.isEmpty() || board == null) {
            return false;
        }
        CompoundTag how = new CompoundTag();
        how.putInt(CuisineBoardStation.FROM, slot);
        if (board.handson$takes(stack) <= 0 && CuisineBoardStation.cuts(view.level(), board, stack)) {
            how.putBoolean(CuisineBoardStation.ALL, true);
            view.sendAction(CuisineBoardStation.ACTION_CUT, how);
            return true;
        }
        if (board.handson$takes(stack) <= 0 && !CuisineBoardStation.isStep(board, stack)) {
            return false;
        }
        how.putBoolean(CuisineBoardStation.ALL, true);
        view.sendAction(CuisineBoardStation.ACTION_PUT, how);
        return true;
    }

    @Override
    public void onEffect(StationView view, ResourceLocation effect, String node, CompoundTag data) {
        if (CuisineBoardStation.EFFECT_LAID.equals(effect)) {
            laid(view, data);
        } else if (CuisineBoardStation.EFFECT_TAKEN.equals(effect)) {
            taken(view, data);
        } else if (CuisineBoardStation.EFFECT_BACK.equals(effect)) {
            back(view, data);
        } else if (CuisineBoardStation.EFFECT_CHOPPED.equals(effect)) {
            view.playSound(FarmersDelightContent.KNIFE_CUT, "surface", 0.8F, 0.95F + view.level().getRandom().nextFloat() * 0.15F);
            visibleParts(view).forEach(part -> part.shake(0.2F, 0.1F));
        } else if (CuisineBoardStation.EFFECT_CUT.equals(effect)) {
            cut(view, data);
        } else if (CuisineBoardStation.EFFECT_REFUSED.equals(effect)) {
            putFrom = null;
            refused(view, ItemStack.parseOptional(view.level().registryAccess(), data.getCompound(CuisineBoardStation.STACK)));
        }
    }

    /** Steps went on, shown one after the other: each comes down from the cook, is poured, or is done by hand. */
    private void laid(StationView view, CompoundTag data) {
        ListTag told = data.getList(CuisineBoardStation.STEPS, Tag.TAG_COMPOUND);
        float at = start();
        for (int i = 0; i < told.size(); i++) {
            CompoundTag step = told.getCompound(i);
            ItemStack laid = ItemStack.parseOptional(view.level().registryAccess(), step.getCompound(CuisineBoardStation.STACK));
            List<ItemStack> afterIt = stacks(view, step.getList(CuisineBoardStation.AFTER, Tag.TAG_COMPOUND));
            int index = i;
            float begins = at;
            if (laid.isEmpty()) {
                after(begins, () -> byHand(view, afterIt));
                at += handTime(afterIt) + GAP;
            } else if (KitchenLooks.isPoured(laid) && view.node("hand_0").isPresent()) {
                // Only a liquid is poured: rice comes in a bowl too, but goes down as food.
                float poured = pourTime(view, laid);
                after(begins, () -> pour(view, laid, afterIt));
                at += poured + GAP;
            } else {
                after(begins, () -> flyIn(view, laid, afterIt, index));
                at += FLY_TIME + GAP;
            }
        }
        busyUntil = clock + at;
    }

    /** Food comes down from the cook (from above for the others) onto the board and becomes its part. */
    private void flyIn(StationView view, ItemStack laid, List<ItemStack> afterIt, int index) {
        view.node("fly_" + (index % FLIERS)).ifPresentOrElse(node -> {
            show(node, laid, FLY_SIZE);
            Vector3f from = FromPlayer.offset(view, LAND_Y + 6.0F, 0, 1);
            node.moveTo(from.x, LAND_Y + 6.0F, from.z, 0.0F);
            node.rotateTo(0.0F, (index * 37 % 60) - 30.0F, 0.0F, 0.0F);
            node.scaleTo(0.6F, 0.0F);
            node.setVisible(true);
            node.hopTo(0.0F, LAND_Y - 2.0F, 0.0F, 2.5F, FLY_TIME);
            node.scaleTo(1.0F, FLY_TIME * 0.7F);
            after(FLY_TIME, () -> {
                node.setVisible(false);
                showContents(view, afterIt, true);
                view.playSound(FarmersDelightContent.BOARD_PLACE, "surface", 0.5F, 1.1F + view.level().getRandom().nextFloat() * 0.2F);
            });
        }, () -> showContents(view, afterIt, true));
    }

    /** How long a pour holds the next step back: until the sauce is out and the bottle is on its way back. */
    private static float pourTime(StationView view, ItemStack laid) {
        return KitchenMotions.Pour.pouredBy(laid) + 0.3F;
    }

    /** A sauce is poured out of its bottle over the board, from the cook's side; the sauce shows as it runs. */
    private void pour(StationView view, ItemStack laid, List<ItemStack> afterIt) {
        NodeView node = view.node("hand_0").orElse(null);
        if (node == null) {
            showContents(view, afterIt, true);
            return;
        }
        if (hand != null) {
            hand.stop();
        }
        Vector3f from = FromPlayer.offset(view, LAND_Y + POUR_HEIGHT, 0, 1).add(8.0F, LAND_Y + POUR_HEIGHT, 8.0F);
        KitchenMotions.Pour pour = new KitchenMotions.Pour(node, "youkai_board_pour", laid, from, new Vector3f(8.0F, LAND_Y, 8.0F),
                () -> LAND_Y, null, POUR_SIZE, -1.0F, 0.0F);
        pour.onFlow(() -> showContents(view, afterIt, true));
        hand = pour;
    }

    private float handTime(List<ItemStack> afterIt) {
        return rolls(afterIt) ? ROLL_TIME + POP : PRESS * 2 + POP;
    }

    /** Whether a step by hand that leaves {@code afterIt} on the board rolled it up: a whole dish lies there then. */
    private static boolean rolls(List<ItemStack> afterIt) {
        return afterIt.size() == 1 && !afterIt.get(0).isEmpty();
    }

    /** A step done by hand: a sheet is rolled up; rice is shaped or a tube closed by pressing it, and it comes up as the new thing. */
    private void byHand(StationView view, List<ItemStack> afterIt) {
        List<NodeView> parts = visibleParts(view);
        if (rolls(afterIt)) {
            rollUp(view, parts, afterIt);
            return;
        }
        parts.forEach(part -> {
            part.scaleTo(0.86F, PRESS);
            part.moveTo(REST.x, -0.3F, REST.z, PRESS);
        });
        view.playSound(SoundEvents.WOOL_STEP, "surface", 0.45F, 0.8F);
        after(PRESS * 2, () -> {
            showContents(view, afterIt, true);
            view.playSound(FarmersDelightContent.BOARD_PLACE, "surface", 0.4F, 1.2F);
        });
    }

    /**
     * The sheet is rolled up away from the cook: the roll comes over from the cook's edge to the middle turning over and over, and
     * the sheet with what lies on it draws in under it towards the far edge; then the roll lies where the board draws it. A roll
     * running away from the cook is rolled from the cook's right.
     */
    private void rollUp(StationView view, List<NodeView> parts, List<ItemStack> afterIt) {
        float turn = turn(view);
        KitchenCuisine board = board(view);
        List<ResourceLocation> rolled = board == null ? null : board.handson$modelsOf(afterIt);
        Optional<NodeView> roll = view.node("roll");
        if (rolled == null || rolled.isEmpty() || roll.isEmpty()) {
            showContents(view, afterIt, true);
            return;
        }
        // The cook's side in the models' own space: the roll starts there.
        Vector3f near = new Vector3f(0.0F, 0.0F, -1.0F).rotateY(-turn * Mth.DEG_TO_RAD);
        float side = Math.abs(near.z) > 0.5F ? Math.signum(near.z) : -1.0F;
        float far = -side * SHEET_HALF;
        Vector3f pivot = toScene(turn, 0.0F, SHEET_Y, far);
        for (NodeView part : parts) {
            Optional<ResourceLocation> model = part.visual().flatMap(Visual::model);
            if (model.isEmpty()) {
                continue;
            }
            part.setVisual(Visual.ofModel(model.get()).withAnchor(new Vector3f(8.0F, SHEET_Y, 8.0F + far)));
            part.moveTo(pivot.x, pivot.y, pivot.z, 0.0F);
            part.scaleTo(0.2F, ROLL_TIME);
        }
        NodeView node = roll.get();
        node.setVisual(Visual.ofModel(rolled.get(0)).withAnchor(new Vector3f(8.0F, ROLL_MIDDLE, 8.0F)));
        Vector3f from = toScene(turn, 0.0F, ROLL_MIDDLE, side * ROLL_START);
        Vector3f to = toScene(turn, 0.0F, ROLL_MIDDLE, 0.0F);
        node.moveTo(from.x, from.y, from.z, 0.0F);
        // Turning about its own length the way it rolls: towards the middle, away from the cook.
        node.rotateTo(side * ROLL_TURNS, turn, 0.0F, 0.0F);
        node.scaleTo(0.55F, 0.0F);
        node.setVisible(true);
        node.moveTo(to.x, to.y, to.z, ROLL_TIME);
        node.rotateTo(0.0F, turn, 0.0F, ROLL_TIME);
        node.scaleTo(1.0F, ROLL_TIME);
        view.playSound(SoundEvents.WOOL_STEP, "surface", 0.4F, 1.0F);
        after(ROLL_TIME * 0.45F, () -> view.playSound(SoundEvents.WOOL_STEP, "surface", 0.35F, 1.15F));
        after(ROLL_TIME, () -> {
            node.setVisible(false);
            showContents(view, afterIt, false);
            view.playSound(FarmersDelightContent.BOARD_PLACE, "surface", 0.5F, 0.9F);
        });
    }

    private List<NodeView> visibleParts(StationView view) {
        List<NodeView> parts = new ArrayList<>();
        for (int k = 0; k < PARTS; k++) {
            view.node("part_" + k).filter(NodeView::isVisible).ifPresent(parts::add);
        }
        return parts;
    }

    /** The dish leaves the board for the cook; a batch shows the next dish lying there again after it. */
    private void taken(StationView view, CompoundTag data) {
        float at = start();
        boolean batch = data.contains(CuisineBoardStation.AFTER);
        List<ItemStack> afterIt = batch ? stacks(view, data.getList(CuisineBoardStation.AFTER, Tag.TAG_COMPOUND)) : List.of();
        after(at, () -> {
            leave(view, visibleParts(view));
            view.playSound(SoundEvents.ITEM_PICKUP, "surface", 0.35F, 1.4F);
            after(AWAY, () -> showContents(view, afterIt, batch));
        });
        busyUntil = clock + at + AWAY + GAP;
    }

    /**
     * The last step came off. When it only added parts, they lift and go to the cook. Most steps of its dishes draw the whole thing
     * anew (kelp with rice on it is an open sheet, a second rice a bigger mound, a filling a new tube): then what stays is drawn as
     * it lies now, in place, and only the food that came off goes to the cook, so the half-made roll never leaves the board.
     */
    private void back(StationView view, CompoundTag data) {
        float at = start();
        List<ItemStack> afterIt = stacks(view, data.getList(CuisineBoardStation.AFTER, Tag.TAG_COMPOUND));
        ItemStack off = ItemStack.parseOptional(view.level().registryAccess(), data.getCompound(CuisineBoardStation.STACK));
        after(at, () -> {
            KitchenCuisine board = board(view);
            List<ResourceLocation> left = board == null ? null : board.handson$modelsOf(afterIt);
            view.playSound(SoundEvents.ITEM_PICKUP, "surface", 0.3F, 1.5F);
            if (left == null || !shownModels.containsAll(left)) {
                showContents(view, afterIt, false);
                goesBack(view, off);
                return;
            }
            List<NodeView> going = new ArrayList<>();
            for (int k = 0; k < shownModels.size(); k++) {
                if (!left.contains(shownModels.get(k))) {
                    view.node("part_" + k).ifPresent(going::add);
                }
            }
            leave(view, going);
            after(AWAY, () -> showContents(view, afterIt, false));
        });
        busyUntil = clock + at + AWAY + GAP;
    }

    /** {@code stack} lifts off the board and goes to the cook, as it came down. */
    private void goesBack(StationView view, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        view.node("fly_0").ifPresent(node -> {
            show(node, stack, FLY_SIZE);
            node.moveTo(0.0F, LAND_Y - 2.0F, 0.0F, 0.0F);
            node.rotateTo(0.0F, 0.0F, 0.0F, 0.0F);
            node.scaleTo(1.0F, 0.0F);
            node.setVisible(true);
            Vector3f away = FromPlayer.offset(view, LAND_Y + 6.0F, 0, 1);
            node.hopTo(away.x, LAND_Y + 4.0F, away.z, 2.0F, AWAY);
            node.scaleTo(0.0F, AWAY);
            after(AWAY, () -> node.setVisible(false));
        });
    }

    /** {@code parts} go up and towards the cook, shrinking away. */
    private static void leave(StationView view, List<NodeView> parts) {
        Vector3f away = FromPlayer.offset(view, 6.0F, 0, 1);
        for (NodeView part : parts) {
            part.hopTo(away.x, 4.0F, away.z, 2.0F, AWAY);
            part.scaleTo(0.0F, AWAY);
        }
    }

    /**
     * The last mark is cut: the dish falls into its pieces where it lay, they part a little where the knife went through and go
     * to the cook one after the other.
     */
    private void cut(StationView view, CompoundTag data) {
        ListTag results = data.getList(CuisineBoardStation.RESULTS, Tag.TAG_COMPOUND);
        ItemStack piece = results.isEmpty() ? ItemStack.EMPTY : ItemStack.parseOptional(view.level().registryAccess(), results.getCompound(0));
        int pieces = Mth.clamp(piece.getCount(), 2, PIECES);
        view.playSound(FarmersDelightContent.KNIFE_CUT, "surface", 0.8F, 0.95F + view.level().getRandom().nextFloat() * 0.15F);
        cutting = true;
        float[] at = box.clone();
        // The knife comes down a moment before: the pieces part when it is through.
        float through = CHOP * 0.5F;
        after(through, () -> {
            visibleParts(view).forEach(part -> part.setVisible(false));
            view.node("scored").ifPresent(node -> node.setVisible(false));
            view.node("marks").ifPresent(node -> node.setVisible(false));
            shown = List.of();
            shownModels.clear();
            Vector3f away = FromPlayer.offset(view, 6.0F, 0, 1);
            float y = (at[2] + at[3]) / 2.0F - KNIFE_BASE;
            for (int k = 0; k < pieces; k++) {
                int index = k;
                float x = at[0] + (at[1] - at[0]) * (k + 0.5F) / pieces - 8.0F;
                view.node("piece_" + k).ifPresent(node -> {
                    show(node, piece, PIECE_SIZE);
                    node.moveTo(x, y, (at[4] + at[5]) / 2.0F - 8.0F, 0.0F);
                    node.rotateTo(0.0F, (index * 53 % 40) - 20.0F, 0.0F, 0.0F);
                    node.scaleTo(1.0F, 0.0F);
                    node.setVisible(true);
                    node.moveTo(x * 1.15F, y, (at[4] + at[5]) / 2.0F - 8.0F, 0.12F);
                    after(0.3F + index * 0.05F, () -> {
                        node.hopTo(away.x, y + 4.0F, away.z, 2.0F, AWAY);
                        node.scaleTo(0.0F, AWAY);
                    });
                    after(0.3F + index * 0.05F + AWAY, () -> node.setVisible(false));
                });
            }
            view.playSound(SoundEvents.ITEM_PICKUP, "surface", 0.35F, 1.3F);
        });
        float done = through + 0.3F + pieces * 0.05F + AWAY;
        after(done, () -> cutting = false);
        busyUntil = clock + done;
    }

    /** What the board would not take comes down from the hand and bounces back. */
    private void refused(StationView view, ItemStack stack) {
        ghost = (ghost + 1) % GHOSTS;
        view.node("lift_" + ghost).ifPresent(node -> {
            show(node, stack, FLY_SIZE * 0.8F);
            node.rotateTo(0.0F, 0.0F, 0.0F, 0.0F);
            node.scaleTo(1.0F, 0.0F);
            node.moveTo(0.0F, 4.0F, 0.0F, 0.0F);
            node.setVisible(true);
            node.moveTo(0.0F, 1.5F, 0.0F, 0.09F);
            after(0.09F, () -> node.hopTo(0.0F, 4.0F, -6.0F, 1.2F, 0.22F));
            after(0.31F, () -> node.setVisible(false));
        });
        view.playSound(SoundEvents.WOOD_HIT, "surface", 0.4F, 0.8F);
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

    private static List<ItemStack> stacks(StationView view, ListTag list) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            stacks.add(ItemStack.parseOptional(view.level().registryAccess(), list.getCompound(i)));
        }
        return stacks;
    }

    private static List<ItemStack> copy(List<ItemStack> stacks) {
        List<ItemStack> copy = new ArrayList<>();
        stacks.forEach(stack -> copy.add(stack.copy()));
        return copy;
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
