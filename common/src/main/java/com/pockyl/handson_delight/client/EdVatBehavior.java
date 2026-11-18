package com.pockyl.handson_delight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.MeshShape;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.station.EdVatLogic;
import com.pockyl.handson_delight.station.EdVatStation;
import com.pockyl.handson_delight.station.KitchenEdVat;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * What Extra Delight's vat shows while somebody works at it: the crock open, its lid lifted off and leant back on the far rim, the
 * liquid poured into it in its colour, what was dropped in floating round on it (lying on the floor while it is dry), the thing of a
 * stage shaken in. As it ages the liquid turns towards what it makes; done, that waits in the vat until it is drawn into its
 * containers. Its lid goes on and comes off as a motion: swung down onto the rim and up again, flown from the cook and back to them.
 * The badge shows what it makes, a pip for each stage, the thing and the lid the stage under way wants, the bar and the time left.
 */
public final class EdVatBehavior implements StationBehavior {
    /** The six places and the stage place. */
    private static final int SLOTS = KitchenEdVat.INGREDIENTS + 1;
    private static final String[] BADGE_PARTS = {"badge", "dial_track", "dial", "thermo", "thermo_now", "preview", "column"};
    /** The parts of the badge's column: children of {@code column}, they grow with it. */
    private static final String[] COLUMN_PARTS = {"cells", "want_item", "want_lid", "want_ok", "want_missing", "want_open"};
    /** The middle of Extra Delight's lid model, its own pixels: the lid icon turns about it. */
    private static final Vector3f LID_MIDDLE = new Vector3f(8.0F, 2.75F, 8.0F);
    private static final ResourceLocation BADGE_TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "block/white_concrete");
    /** The crock's models, one for each of Extra Delight's looks of it, in the order of its {@code style}. */
    private static final String[] STYLES = {"copper_block", "white_terracotta", "light_gray_terracotta", "gray_terracotta",
            "black_terracotta", "brown_terracotta", "red_terracotta", "orange_terracotta", "yellow_terracotta", "lime_terracotta",
            "green_terracotta", "cyan_terracotta", "light_blue_terracotta", "blue_terracotta", "magenta_terracotta", "pink_terracotta",
            "purple_terracotta"};
    /** The lid swings up about the far edge of the rim this far, degrees, in this many seconds, and settles with a little bounce. */
    private static final float LID_UP = 104.0F;
    private static final float LID_TIME = 0.45F;
    private static final float LID_BOUNCE = 4.0F;
    /** A lid flying from the cook or back to them: seconds, and the height of its arc. */
    private static final float LID_FLIGHT = 0.5F;
    private static final float LID_ARC = 6.0F;
    /**
     * Where the lid lies on the vat, scene pixels: the scene's place of the {@code lid} node, under its {@code hinge} at the far
     * edge. A flight moves it by its offset from there ({@code moveTo} moves a node from its own place).
     */
    private static final Vector3f LID_REST = new Vector3f(8.0F, 16.0F, 8.0F);
    /** A lid the cook could put on, offered over the vat, see-through. */
    private static final int OFFERED = 0x70FFFFFF;
    /** The inside: its floor, how deep it is to the rim, the rim, pixels (the scene's tank box). */
    private static final float FLOOR = 2.0F;
    private static final float DEPTH = 14.0F;
    private static final float RIM = 16.0F;
    private static final float CURRENT = 0.22F;
    /** A container drawing what waits in the vat, or the liquid back out, this high; dipped in on the right of the middle. */
    private static final float DIP_SIZE = 3.6F;
    private static final Vector3f DIP_AT = new Vector3f(6.0F, 0.0F, 8.6F);
    private static final float DISH_ITEM = 5.2F;
    private static final float ITEM_PIXELS = 8.0F;
    private static final Vector3f ICON_MIDDLE = new Vector3f(8.0F, 8.0F, 8.0F);
    /** How much darker a liquid looks in the crock than on its icon; how far it has turned to what it makes once it is done. */
    private static final float IN_SHADE = 0.85F;
    private static final float TURNING = 0.8F;
    private static final int WATER = 0xFF3F76E4;
    private static final float AGING_BUBBLES = 1.0F;
    private static final int BUBBLE_COLOR = 0x90FFFFFF;
    private static final Vector3f RISE = new Vector3f(0.0F, 3.5F, 0.0F);
    /** The badge, as the other vat's: a plate, stage pips over the icon, the bar and the time under it, what the stage wants beside it. */
    private static final int PLATE_COLOR = 0x8C1E140C;
    private static final int DIAL_COLOR = 0xFFFFCB6B;
    private static final int WAITING_COLOR = 0xFFA8A49C;
    private static final int TRACK_COLOR = 0x50FFF2DC;
    private static final int PIP_DONE = 0xFFFFCB6B;
    private static final int PIP_NOW = 0xFFFFF4D8;
    /** Stage dots over the icon: each this wide, this far apart (joined by a line), their top. */
    private static final int PIP_SIZE = 5;
    private static final int PIP_STEP = 9;
    private static final int PIP_TOP = -15;
    /** The column right of the icon for what the stage under way wants: its left edge, its places' size and their tops. */
    private static final int COLUMN_LEFT = 11;
    private static final int CELL = 11;
    private static final int ITEM_CELL_TOP = -13;
    private static final int LID_CELL_TOP = 0;
    /** The plate's right edge, narrow and with the column. */
    private static final int PLATE_RIGHT = 10;
    private static final int PLATE_WIDE_RIGHT = COLUMN_LEFT + CELL + 2;
    private static final int CELL_COLOR = 0x66000000;
    private static final int OK_COLOR = 0xFF8FD16A;
    private static final int MISSING_COLOR = 0xFFFFB347;
    private static final int OPEN_COLOR = 0xFFFF5A4A;
    private static final String[] TICK = {"....#", "...##", "#.##.", "###..", ".#..."};
    private static final int BAR_LEFT = -7;
    private static final int BAR_TOP = 10;
    private static final int BAR_LENGTH = 14;
    /** The lid of a stage that ages open, struck through: dimmed under the stroke. */
    private static final int LID_STRUCK = 0xFFB0A8A0;
    private static final float DONE_SWELL = 0.35F;
    private static final float DONE_TIME = 0.6F;
    private static final float READY_BREATH = 0.04F;
    private static final float NEED_TIME = 1.6F;
    private static final int DAY = 24000;

    private final RecipeBook book = new RecipeBook(new EdVatBook());
    private final UsableCache usable = new UsableCache();
    private final VatContents contents = new VatContents(SLOTS, FLOOR, RIM, this::surface);
    private List<KitchenEdVat.Aging> recipes = List.of();
    private float time;
    /** The lid as shown: how far up on its hinge (0 on the rim, 1 leant back), where it was last told to be. */
    private float lidUp;
    private int lidShown = -1;
    private boolean wasLifting;
    /** A lid flying from the cook ({@code 1}) or to them ({@code -1}), how far along, 0 to 1; 0 when none flies. */
    private int lidFlight;
    private float lidFlown;
    private Vector3f lidFrom = new Vector3f();
    private BlockState lidLook;
    private String bodyModel = "";
    private float level;
    private int shownColor;
    private float turned;
    private float bubbles;
    private int amountBefore = -1;
    /** What waits in the vat and how much liquid is in it, as the last state change told it. */
    private ItemStack lastWaiting = ItemStack.EMPTY;
    private int lastAmount;
    private KitchenMotions.Motion pour;
    private KitchenMotions.Dip dip;
    private String drawnDial = "";
    private String drawnPips = "";
    private String drawnColumn = "";
    private String drawnPlate = "";
    private Item shownIcon;
    private Item shownWant;
    private Item drawnDish;
    private ItemStack dish = ItemStack.EMPTY;
    private Optional<KitchenEdVat.Aging> making = Optional.empty();
    private int servings;
    private boolean ready;
    private boolean sawAging;
    private int doneBefore;
    private float doneAt = -10.0F;
    private float needUntil = -10.0F;
    private Item needed;

    public EdVatBehavior(StationView view) {
    }

    @Override
    public void onSceneReady(StationView view) {
        drawnDial = "";
        drawnPips = "";
        drawnColumn = "";
        drawnPlate = "";
        shownIcon = null;
        shownWant = null;
        drawnDish = null;
        shownColor = 0;
        bodyModel = "";
        lidLook = null;
        contents.sceneReady();
        recipes = KitchenEdVat.recipes(view.level());
        view.node("dial").ifPresent(dial -> dial.setTint(DIAL_COLOR));
        view.node("dial_track").ifPresent(track -> track.setTint(TRACK_COLOR));
        view.node("thermo").ifPresent(track -> track.setTint(TRACK_COLOR));
        view.node("badge").ifPresent(plate -> plate.setTint(PLATE_COLOR));
        view.node("cells").ifPresent(node -> node.setTint(CELL_COLOR));
        view.node("want_ok").ifPresent(node -> node.setTint(OK_COLOR));
        view.node("want_missing").ifPresent(node -> node.setTint(MISSING_COLOR));
        view.node("want_open").ifPresent(node -> node.setTint(OPEN_COLOR));
        for (String part : BADGE_PARTS) {
            view.node(part).ifPresent(node -> node.setOnTop(true));
        }
        for (String part : COLUMN_PARTS) {
            view.node(part).ifPresent(node -> node.setOnTop(true));
        }
        view.node("book_open").ifPresent(node -> node.setPainter(book));
        view.node("brew").ifPresent(brew -> {
            brew.setWakeLook(1.8F, 0.45F);
            brew.setFloatingWake(0.0F);
        });
        view.node("want_lid").ifPresent(node -> node.setVisual(Visual.ofBlock(KitchenEdVat.lid().defaultBlockState()).withAnchor(LID_MIDDLE)));
        lidShown = -1;
        refresh(view);
    }

    /**
     * The turn about the vertical that shows the crock as the block stands in the world, whichever side the scene is turned to:
     * Extra Delight turns its model by its {@code facing}, east unturned.
     */
    private static float bodyTurn(Direction scene, BlockState block) {
        Direction facing = block.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? block.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.EAST;
        return Mth.wrapDegrees(VatBehavior.lidTurn(scene) - (facing.toYRot() + 90.0F));
    }

    @Override
    public void onOpen(StationView view) {
        recipes = KitchenEdVat.recipes(view.level());
        usable.clear();
        view.setInventoryFilter(stack -> usable.test(stack, this::usable));
        book.opened(view);
        doneBefore = doneCount(view.state());
        // What the cook came with is what they meant to put in, draw into or put on: a right click with it does it.
        LocalPlayer player = Minecraft.getInstance().player;
        // Except the empty bucket or bottle left in the hand by pouring: while something ages in the liquid, coming back with it is
        // no wish to draw the liquid out and spoil the batch. A click with it at the station still does.
        ItemStack held = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        boolean drainsBatch = isEmptyContainer(held) && !KitchenEdVat.draws(recipes, waiting(view), held)
                && Arrays.stream(view.state().getItems(EdVatStation.INGREDIENTS)).anyMatch(stack -> !stack.isEmpty());
        if (player != null && view.openedByUse() && player.inventoryMenu.getCarried().isEmpty() && usable(held) && !drainsBatch) {
            use(view, player.getInventory().selected, held, false);
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

    /** The lid is still swinging or flying after the last one left: the scene stands in for the blocks until it is done. */
    @Override
    public boolean holdsBlock(StationView view) {
        return !view.isInUse() && (lidFlight != 0 || lidShown == EdVatStation.LID_ON && lidUp > 0.0F);
    }

    // What the cook uses.

    private KitchenEdVat.Access access() {
        return KitchenEdVat.access();
    }

    private boolean isLiquid(ItemStack stack) {
        return access() != null && access().liquidOf(stack).isPresent();
    }

    private boolean isEmptyContainer(ItemStack stack) {
        return stack.is(Items.BUCKET) || stack.is(Items.GLASS_BOTTLE);
    }

    /**
     * What the inventory at the vat shows: what goes into any recipe (its things, its liquids, the things its stages want), the lid,
     * the containers of what waits in it, and an empty bucket or bottle while there is liquid to draw back out.
     */
    private boolean usable(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (KitchenEdVat.isLid(stack)) {
            return true;
        }
        ItemStack waiting = lastWaiting;
        if (KitchenEdVat.isContainer(recipes, stack) || isEmptyContainer(stack)) {
            return KitchenEdVat.draws(recipes, waiting, stack) || waiting.isEmpty() && isEmptyContainer(stack) && lastAmount > 0;
        }
        if (isLiquid(stack)) {
            Fluid fluid = access().liquidOf(stack).get().fluid();
            return recipes.stream().anyMatch(recipe -> recipe.fluids().stream().anyMatch(wanted -> wanted.isSame(fluid)));
        }
        return recipes.stream().anyMatch(recipe -> recipe.isStageItem(stack)
                || recipe.ingredients().stream().anyMatch(ingredient -> ingredient.test(stack)));
    }

    /** A shift click in the inventory: the whole stack goes in, or draws as many servings as it holds containers. */
    @Override
    public boolean onInventoryShiftClick(StationView view, int slot, ItemStack stack) {
        if (!usable(stack)) {
            return false;
        }
        use(view, slot, stack, true);
        return true;
    }

    /** {@code stack}, in inventory slot {@code slot} (-1: on the cursor), is used on the vat. */
    private void use(StationView view, int slot, ItemStack stack, boolean all) {
        CompoundTag how = new CompoundTag();
        how.putInt(EdVatStation.FROM, slot);
        how.putBoolean(EdVatStation.ALL, all);
        if (KitchenEdVat.isLid(stack)) {
            view.sendAction(EdVatStation.ACTION_LID, how);
        } else if (KitchenEdVat.draws(recipes, waiting(view), stack)) {
            draw(view, all, slot);
        } else if (isEmptyContainer(stack) && !isLiquid(stack)) {
            scoop(view, slot, stack);
        } else if (!KitchenEdVat.isContainer(recipes, stack)) {
            view.sendAction(EdVatStation.ACTION_PUT, how);
        }
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Click click && click.node().equals("book_open")) {
            book.click(view, click.button());
        } else if (event instanceof SceneEvent.Click click && click.button() == 0) {
            clicked(view, click.node());
        }
    }

    @Override
    public boolean onScroll(StationView view, String node, double amount) {
        return "book_open".equals(node) && book.scroll(amount);
    }

    /** Whatever the cook carries, clicked over the vat or its lid, is used on it: the left button the whole stack, the right one. */
    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if ("book_open".equals(node)) {
            book.click(view, button);
            return true;
        }
        if ("mouth".equals(node) || "served".equals(node) || "lid".equals(node) || node.startsWith("item_")) {
            use(view, -1, stack, button == 0 || shift);
            return true;
        }
        return false;
    }

    private boolean clicked(StationView view, String node) {
        switch (node) {
            case "lid" -> {
                CompoundTag data = new CompoundTag();
                // An empty hand puts on a lid from the inventory when none is there.
                data.putInt(EdVatStation.FROM, -2);
                view.sendAction(EdVatStation.ACTION_LID, data);
            }
            case "served" -> draw(view, Screen.hasShiftDown(), -1);
            default -> {
                return false;
            }
        }
        return true;
    }

    private static ItemStack waiting(StationView view) {
        return view.state().getItem(EdVatStation.SERVINGS_SLOT);
    }

    /**
     * The cook takes what was made. What waits in the vat is drawn into the container they bring, on their cursor or from inventory
     * slot {@code from}: it is dipped in and comes up full. With nothing to draw it into, the badge shows the container it wants.
     * What lies in the output is taken as it lies.
     */
    private void draw(StationView view, boolean all, int from) {
        ItemStack drink = waiting(view);
        ItemStack served = view.state().getItem(EdVatStation.SERVED_SLOT);
        if (drink.isEmpty() && served.isEmpty()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (!drink.isEmpty()) {
            ItemStack brought = player == null ? ItemStack.EMPTY
                    : from < 0 ? player.inventoryMenu.getCarried() : player.getInventory().getItem(from);
            if (!KitchenEdVat.draws(recipes, drink, brought)) {
                needUntil = time + NEED_TIME;
                needed = KitchenEdVat.bottling(recipes, drink).stream().findFirst().map(recipe -> recipe.container().getItem())
                        .orElse(null);
                view.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), "badge", 0.3F, 0.7F);
                return;
            }
            if (dip == null) {
                dipFrom(view, brought.getItem(), drink.getItem());
            }
        } else {
            view.playSound(SoundEvents.ITEM_PICKUP, "served", 0.35F, 1.2F);
        }
        CompoundTag take = new CompoundTag();
        take.putBoolean(EdVatStation.ALL, all);
        take.putInt(EdVatStation.FROM, from);
        view.sendAction(EdVatStation.ACTION_TAKE, take);
    }

    /** An empty bucket or bottle draws the liquid back out: dipped in, it comes up full of it. */
    private void scoop(StationView view, int from, ItemStack empty) {
        Fluid fluid = fluid(view);
        if (fluid == Fluids.EMPTY || access() == null) {
            return;
        }
        Item full = empty.is(Items.BUCKET) ? fluid.getBucket()
                : access().containersOf(fluid).stream().filter(stack -> !stack.is(fluid.getBucket())).findFirst().map(ItemStack::getItem)
                .orElse(Items.AIR);
        if (full != Items.AIR && dip == null) {
            dipFrom(view, empty.getItem(), full);
        }
        CompoundTag data = new CompoundTag();
        data.putInt(EdVatStation.FROM, from);
        view.sendAction(EdVatStation.ACTION_SCOOP, data);
    }

    private void dipFrom(StationView view, Item empty, Item full) {
        Optional<NodeView> node = view.node("dipper");
        if (node.isEmpty()) {
            return;
        }
        Vector3f over = new Vector3f(DIP_AT.x, surface() + 3.0F, DIP_AT.z);
        Vector3f away = FromPlayer.point(view, RIM + VatContents.ARRIVE_FROM, 0, 1).orElse(new Vector3f(8.0F, RIM + 8.0F, 0.0F));
        KitchenMotions.showItem(node.get(), empty, DIP_SIZE, 0xFFFFFFFF);
        node.get().moveTo(over.x, over.y, over.z, 0.0F);
        node.get().rotateTo(0.0F, 0.0F, 0.0F, 0.0F);
        dip = new KitchenMotions.Dip(node.get(), full, over, new Vector3f(), away, this::surface, view.node("brew").orElse(null),
                DIP_SIZE / ITEM_PIXELS, DIP_SIZE, 0xFF000000 | shownColor,
                () -> view.playSound(SoundEvents.BOTTLE_FILL, "brew", 0.45F, 1.0F));
    }

    // What the vat shows.

    private static Fluid fluid(StationView view) {
        String id = view.state().data().getString(EdVatStation.FLUID);
        ResourceLocation key = id.isEmpty() ? null : ResourceLocation.tryParse(id);
        return key == null ? Fluids.EMPTY : BuiltInRegistries.FLUID.get(key);
    }

    private void refresh(StationView view) {
        StationState state = view.state();
        ItemStack waitingNow = waiting(view);
        if (!ItemStack.isSameItem(waitingNow, lastWaiting)) {
            usable.clear();
        }
        lastWaiting = waitingNow.copy();
        int amount = state.data().getInt(EdVatStation.AMOUNT);
        if ((amount > 0) != (lastAmount > 0)) {
            usable.clear();
        }
        lastAmount = amount;
        ItemStack[] places = state.getItems(EdVatStation.INGREDIENTS);
        ItemStack[] items = Arrays.copyOf(places, SLOTS);
        items[SLOTS - 1] = state.getItem(EdVatStation.STAGE_SLOT);
        contents.update(view, items);
        poured(view, amount);
        dish(view);
        badgeState(view, places);
    }

    /** More liquid in the vat while the cook watches: it is poured from its container, a bucket for a bucketful, else a bottle. */
    private void poured(StationView view, int amount) {
        int before = amountBefore;
        amountBefore = amount;
        if (before < 0 || amount <= before || !view.isFocused() || access() == null) {
            return;
        }
        Fluid fluid = fluid(view);
        List<ItemStack> containers = access().containersOf(fluid);
        boolean bucketful = amount - before >= 1000;
        Optional<ItemStack> container = containers.stream().filter(stack -> stack.is(fluid.getBucket()) == bucketful).findFirst()
                .or(() -> containers.stream().findFirst());
        Optional<NodeView> hand = view.node("hand_liquid");
        Optional<Vector3f> from = FromPlayer.point(view, RIM + VatContents.ARRIVE_FROM, 0, 1);
        if (container.isEmpty() || hand.isEmpty() || from.isEmpty()) {
            return;
        }
        if (pour != null) {
            pour.stop();
        }
        view.node("brew").ifPresent(brew -> brew.holdLevel(KitchenMotions.Pour.pouredBy(container.get()) - 0.5F, 0.6F));
        pour = new KitchenMotions.Pour(hand.get(), "pour_liquid", container.get(), from.get(),
                new Vector3f(VatContents.POUR_X, 0.0F, VatContents.POUR_Z), this::surface, "brew", VatContents.HAND_SIZE, -1.0F, 0.0F)
                .lipAtLeast(RIM + 1.0F);
    }

    /** What was made lying in the vat: in its output (made Extra Delight's way), or waiting as servings of solid food (not bottled). */
    private void dish(StationView view) {
        ItemStack served = view.state().getItem(EdVatStation.SERVED_SLOT);
        ItemStack waiting = waiting(view);
        Item item = !served.isEmpty() ? served.getItem() : !waiting.isEmpty() && !bottled(waiting) ? waiting.getItem() : null;
        if (item != drawnDish) {
            drawnDish = item;
            view.node("dish").ifPresent(node -> {
                node.setVisible(item != null);
                if (item != null) {
                    node.setVisual(Visual.ofItem(item).withAnchor(ICON_MIDDLE));
                    node.rotateTo(-90.0F, 0.0F, 0.0F, 0.0F);
                    node.scaleTo(DISH_ITEM / ITEM_PIXELS, 0.0F);
                }
            });
        }
        boolean any = item != null || !waiting.isEmpty();
        view.node("served").ifPresent(node -> node.setVisible(any));
    }

    /** Whether what waits in the vat is drawn into bottles: a sauce, a vinegar, a brine of pickles; it shows as the liquid alone. */
    private boolean bottled(ItemStack waiting) {
        return KitchenEdVat.bottling(recipes, waiting).stream().anyMatch(recipe -> recipe.container().is(Items.GLASS_BOTTLE));
    }

    private static int doneCount(StationState state) {
        return state.getItem(EdVatStation.SERVED_SLOT).getCount() + state.getItem(EdVatStation.SERVINGS_SLOT).getCount();
    }

    private void badgeState(StationView view, ItemStack[] places) {
        StationState state = view.state();
        float progress = state.getControl(EdVatStation.PROGRESS);
        ItemStack waiting = waiting(view);
        ItemStack served = state.getItem(EdVatStation.SERVED_SLOT);
        String id = state.data().getString(EdVatStation.MAKING);
        making = id.isEmpty() ? KitchenEdVat.matchingPlaces(recipes, places)
                : recipes.stream().filter(recipe -> recipe.id().toString().equals(id)).findFirst();
        servings = 0;
        ready = false;
        if (making.isPresent()) {
            dish = making.get().result();
        } else if (!waiting.isEmpty() || !served.isEmpty()) {
            dish = waiting.isEmpty() ? served : waiting;
            servings = dish.getCount();
            ready = true;
        } else {
            dish = ItemStack.EMPTY;
        }
        if (progress > 0.0F || state.data().getInt(EdVatStation.STAGE_NOW) > 0) {
            sawAging = true;
        }
        int done = doneCount(state);
        if (done > doneBefore && sawAging) {
            sawAging = false;
            done(view);
        }
        doneBefore = done;
    }

    private void done(StationView view) {
        doneAt = time;
        view.node("brew").ifPresent(brew -> brew.ripple(8.0F, 8.0F, 0.6F));
        if (view.isInUse()) {
            view.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), "badge", 0.35F, 1.26F);
            view.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), "badge", 0.3F, 1.5F);
            view.spawnOnLiquid(DiegesisParticles.GLINT, "brew", 6, 0xFFFFE6A0, new Vector3f(0.0F, 2.0F, 0.0F));
        }
    }

    @Override
    public void frame(StationView view, float dt) {
        time += dt;
        boolean inUse = view.isInUse();
        boolean whole = view.isBlockHidden();
        body(view, whole);
        lid(view, dt, whole);
        boolean inside = whole;
        boolean wet = view.state().getTank(EdVatStation.BREW).amount() > 0;
        liquid(view, dt, inside, wet);
        turned += dt * (wet ? CURRENT : 0.0F);
        view.node("brew").ifPresent(brew -> brew.setCurrent(wet ? CURRENT : 0.0F));
        boolean reachable = inUse && lidShown != EdVatStation.LID_ON && lidUp >= 1.0F || inUse && lidShown == EdVatStation.LID_NONE;
        contents.frame(view, dt, time, turned, level, shownColor, inside, wet, inside && wet, reachable);
        if (pour != null && !pour.frame(view, dt)) {
            pour = null;
        }
        if (dip != null && !dip.frame(view, dt)) {
            dip = null;
        }
        view.node("dish").ifPresent(node -> {
            node.setVisible(inside && drawnDish != null);
            node.moveTo(0.0F, surface() - FLOOR + 0.15F, VatContents.PILE_BACK, 0.0F);
        });
        view.node("served").ifPresent(node -> node.setEnabled(reachable));
        view.node("mouth").ifPresent(node -> node.setEnabled(inUse));
        book.frame(view, dt);
        float appear = RecipeBook.appear(view);
        badge(view, inUse && RecipeBook.isWorkedAt(view) && appear > RecipeBook.SPECK);
        float swell = badgeSwell();
        for (String part : BADGE_PARTS) {
            view.node(part).ifPresent(node -> node.scaleTo(appear * swell, 0.0F));
        }
    }

    /** The open crock, in the look of the block (its style), turned as the block stands; drawn only while the block is hidden. */
    private void body(StationView view, boolean whole) {
        BlockState block = view.level().getBlockState(view.pos());
        int style = 0;
        for (Property<?> property : block.getProperties()) {
            if (property.getName().equals("style") && block.getValue(property) instanceof Integer value) {
                style = Mth.clamp(value, 0, STYLES.length - 1);
            }
        }
        String model = style == 0 ? "body" : "body_" + STYLES[style];
        Optional<NodeView> body = view.node("body");
        if (body.isPresent() && !model.equals(bodyModel)) {
            bodyModel = model;
            body.get().setVisual(Visual.ofModel(HandsOnDelight.id("diegesis/ed_vat/" + model)).asLitAsBlock());
        }
        float turn = bodyTurn(view.facing(), block);
        body.ifPresent(node -> {
            node.rotateTo(0.0F, turn, 0.0F, 0.0F);
            node.setVisible(whole);
        });
    }

    /**
     * The lid: on the rim, leant back on its far edge, or nowhere, as the station says. Between on and leant back it swings about the
     * far edge; to and from nowhere it flies from the cook and back. A lid the stage wants and the cook carries is offered over the
     * vat, see-through: a click puts it on.
     */
    private void lid(StationView view, float dt, boolean whole) {
        int now = Math.round(view.state().getControl(EdVatStation.LID));
        Optional<NodeView> node = view.node("lid");
        if (node.isEmpty()) {
            return;
        }
        BlockState look = lidLook(view, now);
        if (look != null && look != lidLook) {
            lidLook = look;
            node.get().setVisual(Visual.ofBlock(look).asLitAsBlock());
            // The badge's lid is the lid of this vat, in its look.
            view.node("want_lid").ifPresent(icon -> icon.setVisual(Visual.ofBlock(look).withAnchor(LID_MIDDLE)));
        }
        if (lidShown < 0) {
            lidShown = now;
            lidUp = now == EdVatStation.LID_ASIDE ? 1.0F : 0.0F;
        }
        if (now != lidShown) {
            if (now == EdVatStation.LID_NONE || lidShown == EdVatStation.LID_NONE) {
                // Off to the cook, or from them onto the rim.
                lidFlight = now == EdVatStation.LID_NONE ? -1 : 1;
                lidFlown = 0.0F;
                lidFrom = FromPlayer.point(view, RIM + VatContents.ARRIVE_FROM, 0, 1).orElse(new Vector3f(8.0F, RIM + 10.0F, -4.0F));
                if (lidFlight > 0) {
                    lidUp = 0.0F;
                }
            }
            lidShown = now;
        }
        float hinge = swing(view, dt, lidShown == EdVatStation.LID_ASIDE);
        Vector3f at = new Vector3f();
        if (lidFlight != 0) {
            lidFlown = Math.min(1.0F, lidFlown + dt / LID_FLIGHT);
            float k = KitchenMotions.smooth(lidFlown);
            Vector3f from = lidFlight > 0 ? new Vector3f(lidFrom) : new Vector3f(LID_REST);
            Vector3f to = lidFlight > 0 ? new Vector3f(LID_REST) : new Vector3f(lidFrom);
            Vector3f path = from.lerp(to, k).add(0.0F, LID_ARC * Mth.sin(Mth.PI * k), 0.0F);
            at = path.sub(LID_REST);
            if (lidFlown >= 1.0F) {
                if (lidFlight > 0) {
                    view.playSound(SoundEvents.COPPER_PLACE, "lid", 0.5F, 1.15F);
                }
                lidFlight = 0;
            }
        }
        node.get().moveTo(at.x, at.y, at.z, 0.0F);
        Optional<NodeView> hingeNode = view.node("hinge");
        hingeNode.ifPresent(h -> h.rotateTo(lidFlight != 0 ? 0.0F : hinge, 0.0F, 0.0F, 0.0F));
        boolean offered = view.isInUse() && lidShown == EdVatStation.LID_NONE && lidFlight == 0 && wantsLid(view) && carriesLid();
        boolean seen = lidFlight != 0 || whole && lidShown != EdVatStation.LID_NONE || offered;
        node.get().setVisible(seen);
        node.get().setTint(offered ? OFFERED : 0xFFFFFFFF);
        node.get().setEnabled(view.isInUse() && (lidShown != EdVatStation.LID_NONE || offered) && lidFlight == 0);
    }

    /** The look of the lid: the block on the vat, the one set aside as its item says, or Extra Delight's first look. */
    private BlockState lidLook(StationView view, int now) {
        BlockState above = view.level().getBlockState(view.pos().above());
        if (KitchenEdVat.isLid(above)) {
            return above;
        }
        KitchenEdVat vat = KitchenEdVat.of(view.level().getBlockEntity(view.pos()));
        if (vat != null && !vat.handson$lidAside().isEmpty()) {
            return EdVatLogic.asBlock(vat.handson$lidAside());
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            int slot = EdVatLogic.lidSlot(player.getInventory());
            if (slot >= 0) {
                return EdVatLogic.asBlock(player.getInventory().getItem(slot));
            }
        }
        return lidLook != null ? lidLook : KitchenEdVat.lid().defaultBlockState();
    }

    private static boolean carriesLid() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && (EdVatLogic.lidSlot(player.getInventory()) >= 0 || KitchenEdVat.isLid(player.inventoryMenu.getCarried()));
    }

    /** Whether the stage under way wants the lid on. */
    private boolean wantsLid(StationView view) {
        int stage = view.state().data().getInt(EdVatStation.STAGE_NOW);
        return making.flatMap(recipe -> recipe.stage(stage)).map(KitchenEdVat.Stage::lid).orElse(false);
    }

    /** The lid swings up about the far edge, comes to rest leant back with a little bounce; going down, it lands with a knock. */
    private float swing(StationView view, float dt, boolean up) {
        float before = lidUp;
        lidUp = up ? Math.min(1.0F, lidUp + dt / LID_TIME) : Math.max(0.0F, lidUp - dt / LID_TIME);
        if (up && !wasLifting && before < 1.0F) {
            view.playSound(SoundEvents.COPPER_STEP, "lid", 0.5F, 1.2F);
        }
        if (before > 0.0F && lidUp <= 0.0F) {
            view.playSound(SoundEvents.COPPER_PLACE, "lid", 0.5F, 1.1F);
        }
        if (before < 1.0F && lidUp >= 1.0F) {
            view.playSound(SoundEvents.COPPER_HIT, "lid", 0.35F, 1.0F);
        }
        wasLifting = up;
        float k = up ? KitchenMotions.out(lidUp) : lidUp * lidUp;
        float bounce = up && lidUp > 0.75F ? LID_BOUNCE * Mth.sin((lidUp - 0.75F) / 0.25F * Mth.PI) : 0.0F;
        return LID_UP * k + bounce;
    }

    /**
     * The liquid: as high as what was poured or waits to be drawn, in its colour, turning towards what it makes as it ages through its
     * stages; what ages sends up a bubble now and then.
     */
    private void liquid(StationView view, float dt, boolean inside, boolean wet) {
        Optional<NodeView> brew = view.node("brew");
        if (brew.isEmpty()) {
            return;
        }
        NodeView node = brew.get();
        float wanted = view.state().getTank(EdVatStation.BREW).amount() / 100.0F;
        level = Mth.lerp(Math.min(1.0F, dt * 3.0F), level, wanted);
        int target = baseColor(view);
        int stages = Math.max(1, view.state().data().getInt(EdVatStation.STAGES));
        float progress = view.state().getControl(EdVatStation.PROGRESS);
        int stage = view.state().data().getInt(EdVatStation.STAGE_NOW);
        boolean aging = making.isPresent() && (progress > 0.0F || stage > 0);
        if (aging) {
            float along = Mth.clamp((stage + progress) / stages, 0.0F, 1.0F);
            target = KitchenLooks.mix(target, made(making.get().result()), TURNING * KitchenMotions.smooth(along));
        }
        if (target != shownColor) {
            shownColor = target;
            node.setLiquidColor(target);
        }
        node.setVisible(inside);
        node.setMottle(wet ? 0.02F : 0.0F);
        if (inside && wet && aging && progress > 0.0F) {
            bubbles += dt * AGING_BUBBLES;
            if (bubbles >= 1.0F) {
                view.spawnInLiquid(DiegesisParticles.BUBBLE, "brew", (int) bubbles, BUBBLE_COLOR, RISE);
                bubbles -= (int) bubbles;
            }
        }
    }

    /** The colour of the liquid in the vat, or of what waits in it: a sauce, a vinegar, a brine. */
    private int baseColor(StationView view) {
        ItemStack waiting = waiting(view);
        if (!waiting.isEmpty() && fluid(view) == Fluids.EMPTY) {
            return made(waiting);
        }
        Fluid fluid = fluid(view);
        if (fluid == Fluids.EMPTY) {
            return WATER;
        }
        if (fluid.isSame(Fluids.WATER)) {
            return WATER;
        }
        int color = access() == null ? WATER
                : access().containersOf(fluid).stream().findFirst().map(KitchenLooks::liquidColor).orElse(WATER);
        return 0xFF000000 | Math.round((color >> 16 & 0xFF) * IN_SHADE) << 16 | Math.round((color >> 8 & 0xFF) * IN_SHADE) << 8
                | Math.round((color & 0xFF) * IN_SHADE);
    }

    /** The colour of what the vat makes, as it shows in bulk. */
    private static int made(ItemStack result) {
        return VatBehavior.vivid(FoodColors.main(result));
    }

    private float surface() {
        return FLOOR + DEPTH * level;
    }

    private float badgeSwell() {
        float since = time - doneAt;
        if (since >= 0.0F && since < DONE_TIME) {
            return 1.0F + DONE_SWELL * Mth.sin(Mth.PI * since / DONE_TIME);
        }
        return ready ? 1.0F + READY_BREATH * (0.5F - 0.5F * Mth.cos(time * 2.4F)) : 1.0F;
    }

    /**
     * The badge while something ages or waits: what it makes; a dot for each stage on a line, those done filled, the one under way a
     * ring; the bar and the time left of that stage (days, or minutes and seconds under a day), grey while it waits; in a column right
     * of the icon the thing the stage wants and the lid, each ticked or framed blinking, the lid struck through for a stage that ages
     * open.
     */
    private void badge(StationView view, boolean cookHere) {
        boolean shown = cookHere && !dish.isEmpty();
        view.node("badge").ifPresent(plate -> plate.setVisible(shown));
        StationState state = view.state();
        float progress = state.getControl(EdVatStation.PROGRESS);
        int left = state.data().getInt(EdVatStation.LEFT);
        int stage = state.data().getInt(EdVatStation.STAGE_NOW);
        int stages = making.map(recipe -> recipe.stages().size()).orElse(0);
        boolean aging = making.isPresent();
        Optional<KitchenEdVat.Stage> now = making.flatMap(recipe -> recipe.stage(stage));
        boolean itemFits = now.map(wanted -> wanted.fits(state.getItem(EdVatStation.STAGE_SLOT))).orElse(true);
        int lidNow = Math.round(state.getControl(EdVatStation.LID));
        boolean lidFits = now.map(wanted -> wanted.lid() == (lidNow == EdVatStation.LID_ON)).orElse(true);
        boolean liquidFits = making.map(recipe -> recipe.takes(fluid(view), state.data().getInt(EdVatStation.AMOUNT))).orElse(true);
        boolean waiting = aging && (!itemFits || !lidFits || !liquidFits);
        int bar = aging ? Math.round(progress * BAR_LENGTH) : ready ? BAR_LENGTH : 0;
        String text = aging && left > 0 ? duration(left) : !aging && servings > 0 ? "x" + Math.min(servings, 99) : "";
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
            VatBehavior.text(lit, text);
            view.node("dial").ifPresent(node -> node.setShape(bar > 0 || !text.isEmpty() ? lit.build() : null, BADGE_TEXTURE, true));
        }
        view.node("dial").ifPresent(node -> node.setTint(waiting ? WAITING_COLOR : DIAL_COLOR));
        String pips = stages + "/" + stage;
        if (!pips.equals(drawnPips)) {
            drawnPips = pips;
            // A dot for each stage on a line: those done filled, the one under way a ring, those to come dim.
            int width = stages * PIP_SIZE + (stages - 1) * (PIP_STEP - PIP_SIZE);
            int first = -width / 2;
            MeshShape.Builder dim = MeshShape.builder();
            MeshShape.Builder lit = MeshShape.builder();
            for (int cell = 0; cell < stages; cell++) {
                int x = first + cell * PIP_STEP;
                if (cell > 0) {
                    PotBehavior.pixels(cell <= stage ? lit : dim, x - (PIP_STEP - PIP_SIZE) + 1, PIP_TOP + PIP_SIZE / 2,
                            PIP_STEP - PIP_SIZE - 2, 1);
                }
                dot(cell <= stage ? lit : dim, x, PIP_TOP, cell == stage);
            }
            view.node("thermo").ifPresent(node -> node.setShape(dim.build(), BADGE_TEXTURE, true));
            view.node("thermo_now").ifPresent(node -> node.setShape(lit.build(), BADGE_TEXTURE, true));
        }
        boolean blink = Mth.sin(time * 6.0F) > 0.0F;
        view.node("dial_track").ifPresent(node -> node.setVisible(shown));
        view.node("dial").ifPresent(node -> node.setVisible(shown));
        view.node("thermo").ifPresent(node -> node.setVisible(shown && stages > 1));
        view.node("thermo_now").ifPresent(node -> {
            node.setVisible(shown && stages > 1);
            node.setTint(stage >= stages ? PIP_DONE : PIP_NOW);
        });
        Item want = now.filter(KitchenEdVat.Stage::wantsItem).map(wanted -> wanted.ingredient().getItems())
                .filter(items -> items.length > 0).map(items -> items[0].getItem()).orElse(null);
        boolean wantsLid = now.map(KitchenEdVat.Stage::lid).orElse(false);
        // The column right of the icon while a stage is under way: its thing in the upper place, the lid in the lower one, each
        // ticked when it is as the stage wants it, framed and blinking when not; a stage that ages open strikes the lid through.
        boolean column = shown && aging && now.isPresent();
        boolean itemPlace = column && want != null;
        plate(view, column);
        view.node("want_item").ifPresent(node -> {
            if (want != shownWant && want != null) {
                node.setVisual(Visual.ofItem(want).withAnchor(ICON_MIDDLE));
                node.setTooltip(new ItemStack(want).getHoverName());
            }
            shownWant = want;
            node.setVisible(itemPlace);
        });
        view.node("want_lid").ifPresent(node -> {
            node.setVisible(column);
            node.setTint(wantsLid ? 0xFFFFFFFF : LID_STRUCK);
            Component lid = new ItemStack(KitchenEdVat.lid()).getHoverName();
            node.setTooltip(wantsLid ? lid : Component.translatable("handson_delight.vat.open", lid));
        });
        String marks = column + "/" + itemPlace + "/" + itemFits + "/" + wantsLid + "/" + lidFits;
        if (!marks.equals(drawnColumn)) {
            drawnColumn = marks;
            MeshShape.Builder cells = MeshShape.builder();
            MeshShape.Builder ok = MeshShape.builder();
            MeshShape.Builder missing = MeshShape.builder();
            MeshShape.Builder open = MeshShape.builder();
            if (itemPlace) {
                PotBehavior.pixels(cells, COLUMN_LEFT, ITEM_CELL_TOP, CELL, CELL);
                mark(itemFits ? ok : missing, ITEM_CELL_TOP, itemFits);
            }
            if (column) {
                PotBehavior.pixels(cells, COLUMN_LEFT, LID_CELL_TOP, CELL, CELL);
                if (!wantsLid) {
                    for (int step = 0; step < CELL; step++) {
                        PotBehavior.pixels(open, COLUMN_LEFT + CELL - 1 - step, LID_CELL_TOP + step, 1, 1);
                    }
                }
                mark(lidFits ? ok : missing, LID_CELL_TOP, lidFits);
            }
            view.node("cells").ifPresent(node -> node.setShape(cells.build(), BADGE_TEXTURE, true));
            view.node("want_ok").ifPresent(node -> node.setShape(ok.build(), BADGE_TEXTURE, true));
            view.node("want_missing").ifPresent(node -> node.setShape(missing.build(), BADGE_TEXTURE, true));
            view.node("want_open").ifPresent(node -> node.setShape(open.build(), BADGE_TEXTURE, true));
        }
        view.node("cells").ifPresent(node -> node.setVisible(column));
        view.node("want_ok").ifPresent(node -> node.setVisible(column));
        view.node("want_missing").ifPresent(node -> node.setVisible(column && blink));
        view.node("want_open").ifPresent(node -> node.setVisible(column));
        preview(view, shown, aging ? left : 0);
    }

    /** The plate behind the badge, wider while it has the column of what the stage wants. */
    private void plate(StationView view, boolean wide) {
        String plate = wide ? "wide" : "narrow";
        if (plate.equals(drawnPlate)) {
            return;
        }
        drawnPlate = plate;
        int right = wide ? PLATE_WIDE_RIGHT : PLATE_RIGHT;
        MeshShape.Builder shape = MeshShape.builder();
        PotBehavior.pixels(shape, -10, -17, right + 10, 37);
        PotBehavior.pixels(shape, -9, -18, right + 8, 1);
        PotBehavior.pixels(shape, -9, 20, right + 8, 1);
        view.node("badge").ifPresent(node -> node.setShape(shape.build(), BADGE_TEXTURE, true));
    }

    /** A stage dot at {@code left, top}: a rounded spot, or a ring for the stage under way. */
    private static void dot(MeshShape.Builder shape, int left, int top, boolean ring) {
        PotBehavior.pixels(shape, left + 1, top, PIP_SIZE - 2, 1);
        PotBehavior.pixels(shape, left + 1, top + PIP_SIZE - 1, PIP_SIZE - 2, 1);
        if (ring) {
            PotBehavior.pixels(shape, left, top + 1, 1, PIP_SIZE - 2);
            PotBehavior.pixels(shape, left + PIP_SIZE - 1, top + 1, 1, PIP_SIZE - 2);
        } else {
            PotBehavior.pixels(shape, left, top + 1, PIP_SIZE, PIP_SIZE - 2);
        }
    }

    /**
     * The mark of the column's place at {@code top}: a tick in its lower right corner when its want is met, else a frame round it
     * (drawn blinking).
     */
    private static void mark(MeshShape.Builder shape, int top, boolean met) {
        if (met) {
            int left = COLUMN_LEFT + CELL - TICK[0].length() + 1;
            int first = top + CELL - TICK.length + 1;
            for (int row = 0; row < TICK.length; row++) {
                for (int column = 0; column < TICK[row].length(); column++) {
                    if (TICK[row].charAt(column) == '#') {
                        PotBehavior.pixels(shape, left + column, first + row, 1, 1);
                    }
                }
            }
            return;
        }
        PotBehavior.pixels(shape, COLUMN_LEFT - 1, top - 1, CELL + 2, 1);
        PotBehavior.pixels(shape, COLUMN_LEFT - 1, top + CELL, CELL + 2, 1);
        PotBehavior.pixels(shape, COLUMN_LEFT - 1, top, 1, CELL);
        PotBehavior.pixels(shape, COLUMN_LEFT + CELL, top, 1, CELL);
    }

    /** Ticks as the badge tells them: whole days over a day ({@code 3d}), else minutes and seconds. */
    private static String duration(int ticks) {
        if (ticks >= DAY) {
            return Mth.ceil(ticks / (float) DAY) + "d";
        }
        int seconds = Math.max(1, Mth.ceil(ticks / 20.0F));
        return seconds >= 60 ? seconds / 60 + ":" + String.format("%02d", seconds % 60) : Integer.toString(seconds);
    }

    /** The icon of what ages or waits, with its name and the time left on hover; the container a cook lacks, blinking, for a moment. */
    private void preview(StationView view, boolean shown, int left) {
        boolean asking = needed != null && time < needUntil;
        view.node("preview").ifPresent(node -> {
            node.setVisible(shown && (!asking || Mth.sin(time * 9.0F) > -0.3F));
            if (!shown) {
                return;
            }
            Item icon = asking ? needed : dish.getItem();
            if (shownIcon != icon) {
                shownIcon = icon;
                node.setVisual(Visual.ofItem(icon).withAnchor(ICON_MIDDLE));
            }
            Component name = asking ? new ItemStack(needed).getHoverName() : dish.getHoverName();
            node.setTooltip(left <= 0 ? name : left >= DAY
                    ? Component.translatable("handson_delight.vat.aging_days", name, Mth.ceil(left / (float) DAY))
                    : Component.translatable("handson_delight.pot.cooking", name, Math.max(1, Mth.ceil(left / 20.0F))));
        });
    }
}
