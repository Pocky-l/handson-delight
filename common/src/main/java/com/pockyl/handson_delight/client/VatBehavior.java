package com.pockyl.handson_delight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.MeshShape;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.KitchenTags;
import com.pockyl.handson_delight.RecipeParts;
import com.pockyl.handson_delight.station.KitchenVat;
import com.pockyl.handson_delight.station.VatStation;

import java.util.List;
import java.util.Optional;

/**
 * What the vat shows while somebody works at it: its lid swung up on its hinge, what was poured in standing in it in its colour, what
 * was dropped in floating round on it (lying on the floor while it is dry). Its warmth shows on the vat itself: over heat the liquid
 * rolls, seethes at the walls, steams and shimmers, and the boil is heard; over ice the vat frosts over, an ice crust closes in from
 * the walls, cold mist spills over the rim and the rime glitters. As it ages the liquid turns towards the drink's colour and a
 * fermenting drink gets its head; done, the drink stays in the vat until it is drawn: a bottle from the inventory dips in and comes up
 * full. While nobody works at it the vat is the block itself, its lid shut; the scene draws its frost, lets its steam out under the lid
 * and lets its boil and bubbling be heard.
 */
public final class VatBehavior implements StationBehavior {
    private static final int SLOTS = KitchenVat.INGREDIENTS;
    /** The badge over the vat: its plate, the bar, the thermometer and the icon. */
    private static final String[] BADGE_PARTS = {"badge", "dial_track", "dial", "thermo", "thermo_now", "preview"};
    /** The parts the block's own model has too: drawn by the scene only while the block is hidden. */
    private static final String[] VAT_PARTS = {"body", "lid"};
    /** The parts that lie as the block does in the world, not as the scene is turned to the cook. */
    private static final String[] WORLD_PARTS = {"body", "frost", "lid", "frost_ring"};
    private static final Identifier BADGE_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "block/white_concrete");
    /** The lid swings up this far about its hinge, degrees, in this many seconds, and settles with a little bounce. */
    private static final float LID_OPEN = 110.0F;
    private static final float LID_TIME = 0.45F;
    private static final float LID_BOUNCE = 4.0F;
    /** The inside: its floor, how deep it is to the rim, the rim, pixels (the scene's tank box). */
    private static final float FLOOR = 4.0F;
    private static final float DEPTH = 12.0F;
    private static final float RIM = 16.0F;
    /** Afloat, what is in it goes round on the current, radians a second for a still vat, a hot one; nothing turns in a frozen one. */
    private static final float CALM_CURRENT = 0.22F;
    private static final float HOT_CURRENT = 0.55F;
    /** A drink is drawn into a container this high, pixels, dipped in on the right of the middle. */
    private static final float DIP_SIZE = 3.6F;
    private static final Vector3f DIP_AT = new Vector3f(6.0F, 0.0F, 8.6F);
    /** What was aged without a container lies in the vat: as its block model, at most this wide, else as the item lying flat. */
    private static final float DISH_ITEM = 5.2F;
    private static final float DISH_BLOCK = 8.0F;
    private static final float DISH_BLOCK_SCALE = 0.6F;
    private static final float ITEM_PIXELS = 8.0F;
    private static final Vector3f ICON_MIDDLE = new Vector3f(8.0F, 8.0F, 8.0F);
    /** Heat: how the liquid rolls and seethes at the walls; bubbles, bubbles popping at the walls, steam and shimmer a second. */
    private static final float HOT_AGITATION = 0.18F;
    private static final float HOT_EDGE = 0.9F;
    private static final float HOT_BUBBLES = 6.0F;
    private static final float HOT_EDGE_POPS = 14.0F;
    private static final float HOT_STEAM = 6.0F;
    private static final float HOT_SHIMMER = 2.2F;
    private static final float BOIL_VOLUME = 0.5F;
    private static final float BOIL_SHUT_VOLUME = 0.18F;
    private static final float BOIL_EVERY = 3.5F;
    /** Shut over heat, steam is forced out under the lid at its edges, this many puffs a second. */
    private static final float SEEP_RATE = 3.0F;
    /**
     * Cold: the rime's colour and how thick it shows at its thickest, how fast it grows or thaws; how pale the cold liquid goes, and
     * how far under its surface the ring of rime on the walls starts.
     */
    private static final int RIME = 0xF2F8FF;
    private static final int RIME_ALPHA = 0xD0;
    private static final int CHILLED = 0xFFDCEAF4;
    private static final float CHILL = 0.35F;
    private static final float RING_UNDER = 0.6F;
    private static final float FROST_TIME = 3.0F;
    /** Cold mist spilling over the rim and down the vat's sides, glints of rime, a second. */
    private static final float MIST_RATE = 4.0F;
    private static final float GLINT_RATE = 1.4F;
    private static final int MIST_COLOR = 0x60EAF6FF;
    private static final int GLINT_COLOR = 0xFFD8F0FF;
    /** A fermenting drink grows a yeast head as it ages, at most this much of its surface (the keg's). */
    private static final float BREWING_HEAD = 0.45F;
    /** Just enough unevenness for the liquid to be drawn pixel by pixel, not as one quad. */
    private static final float SMOOTH_GRID = 0.02F;
    /** What ages sends up a bubble now and then. */
    private static final float AGING_BUBBLES = 1.4F;
    private static final int BUBBLE_COLOR = 0x90FFFFFF;
    private static final int STEAM_COLOR = 0x6CFFFFFF;
    private static final float EDGE_BUBBLE_SIZE = 0.5F;
    private static final Vector3f RISE = new Vector3f(0.0F, 3.5F, 0.0F);
    private static final Vector3f STEAM_RISE = new Vector3f(0.0F, 3.4F, 0.0F);
    /** Ice cream waiting in the vat is its icon's main colour this much towards white: the cream, not the cone. */
    private static final float MASS_PALE = 0.65F;
    private static final int CREAM = 0xFFFFF2D6;
    /** How much darker a liquid looks in the vat than on its icon. */
    private static final float IN_SHADE = 0.8F;
    /** How far the liquid has turned to the drink's colour by the time it is done. */
    private static final float TURNING = 0.85F;
    /** The badge, as the oven's: a plate, three cells of warmth over the icon (cold, mild, hot), the bar and the time under it. */
    private static final int PLATE_COLOR = 0x8C1E140C;
    private static final int DIAL_COLOR = 0xFFFFCB6B;
    private static final int WAITING_COLOR = 0xFFA8A49C;
    private static final int TRACK_COLOR = 0x50FFF2DC;
    private static final int[] WARMTH_COLORS = {0xFF6FB8FF, 0xFFFFF4D8, 0xFFFF6A3C};
    private static final int CELL_LEFT = -4;
    private static final int CELL_STEP = 3;
    private static final int CELL_TOP = -14;
    private static final int FITS_TOP = -10;
    private static final int TEXT_TOP = 13;
    private static final String[] COLON = {".", "#", ".", "#", "."};
    private static final String[] TIMES = {"...", "#.#", ".#.", "#.#", "..."};
    private static final String[] DAYS = {"..#", "..#", "###", "#.#", "###"};
    private static final float DONE_SWELL = 0.35F;
    private static final float DONE_TIME = 0.6F;
    private static final float READY_BREATH = 0.04F;
    /** No container in the inventory for a waiting drink: the badge shows the container instead of the drink, this long. */
    private static final float NEED_TIME = 1.6F;
    private static final int BAR_LEFT = -7;
    private static final int BAR_TOP = 10;
    private static final int BAR_LENGTH = 14;

    private final RecipeBook book = new RecipeBook(new VatBook());
    private final UsableCache usable = new UsableCache();
    private List<KitchenVat.Aging> recipes = List.of();
    private final VatContents contents = new VatContents(SLOTS, FLOOR, RIM, this::surface);
    private KitchenMotions.Dip dip;
    private float time;
    /** The turn {@link #turnToWorld} gave the vat's parts last; NaN: none yet. */
    private float shownTurn = Float.NaN;
    /** How far the lid is up, 0 shut to 1. */
    private float lid;
    private boolean wasOpening;
    private Boolean lidAsked;
    private float lidAskedAt;
    /** The liquid as it shows: how high (0 to 1 of the box), its colour; the current's angle. */
    private float level;
    private int baseColor = 0xFF3F76E4;
    private int shownColor;
    private float turned;
    /** How thick the rime is, 0 to 1. */
    private float frost;
    private float steam;
    private float bubbles;
    private float edgePops;
    private float shimmer;
    private float seep;
    private float mist;
    private float glints;
    private float boilClock;
    private String drawnDial = "";
    private String drawnThermo = "";
    private Item shownDish;
    private Item drawnServed;
    /** What the vat makes of what is in it, or what waits in it: the badge's icon. */
    private ItemStack dish = ItemStack.EMPTY;
    private Optional<KitchenVat.Aging> making = Optional.empty();
    private int servings;
    private boolean ready;
    private boolean aging;
    private boolean sawAging;
    private int doneBefore;
    private float doneAt = -10.0F;
    private float needUntil = -10.0F;
    private Item needed;
    private ItemStack lastServings = ItemStack.EMPTY;
    private float fizz;
    private float rising;

    public VatBehavior(StationView view) {
    }

    @Override
    public void onSceneReady(StationView view) {
        shownTurn = Float.NaN;
        drawnDial = "";
        drawnThermo = "";
        shownDish = null;
        drawnServed = null;
        shownColor = 0;
        contents.sceneReady();
        recipes = KitchenVat.recipes(view.level());
        view.node("dial").ifPresent(dial -> dial.setTint(DIAL_COLOR));
        view.node("dial_track").ifPresent(track -> track.setTint(TRACK_COLOR));
        view.node("thermo").ifPresent(track -> track.setTint(TRACK_COLOR));
        view.node("badge").ifPresent(plate -> {
            plate.setTint(PLATE_COLOR);
            MeshShape.Builder plateShape = MeshShape.builder();
            PotBehavior.pixels(plateShape, -10, -16, 20, 35);
            PotBehavior.pixels(plateShape, -9, -17, 18, 1);
            PotBehavior.pixels(plateShape, -9, 19, 18, 1);
            plate.setShape(plateShape.build(), BADGE_TEXTURE, true);
        });
        for (String part : BADGE_PARTS) {
            view.node(part).ifPresent(node -> node.setOnTop(true));
        }
        view.node("book_open").ifPresent(node -> node.setPainter(book));
        view.node("brew").ifPresent(brew -> {
            brew.setWakeLook(1.8F, 0.45F);
            brew.setFloatingWake(0.0F);
        });
        refresh(view);
    }

    /**
     * The turn about the vertical that undoes the scene's own turn towards the cook: Diegesis turns a scene facing {@code facing} into
     * the world by minus its yaw (north 0, east 90, south 180, west 270), so a node turned by plus that yaw lies as in the world.
     */
    static float lidTurn(Direction facing) {
        return Mth.wrapDegrees(facing.toYRot() + 180.0F);
    }

    /**
     * The vat's own parts lie as the block's do, whichever side the cook came from: its lid's handle, the stone and the rime stay where
     * they are in the world. Read every frame: the scene is made ready before anybody opens it, from whatever side it faced then.
     */
    private void turnToWorld(StationView view) {
        float turn = lidTurn(view.facing());
        if (turn == shownTurn) {
            return;
        }
        shownTurn = turn;
        for (String part : WORLD_PARTS) {
            view.node(part).ifPresent(node -> node.rotateTo(0.0F, turn, 0.0F, 0.0F));
        }
    }

    @Override
    public void onOpen(StationView view) {
        recipes = KitchenVat.recipes(view.level());
        usable.clear();
        view.setInventoryFilter(stack -> usable.test(stack, this::usable));
        book.opened(view);
        doneBefore = doneCount(view.state());
        // What the cook came with is what they meant to put in, or to draw the drink into: a right click with it does it, never slower.
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && view.openedByUse() && player.inventoryMenu.getCarried().isEmpty() && usable(player.getMainHandItem())) {
            use(view, player.getInventory().getSelectedSlot(), player.getMainHandItem(), false);
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

    /** The lid is still swinging shut after the last one left: the scene stands in for the block until it is shut. */
    @Override
    public boolean holdsBlock(StationView view) {
        return !view.isInUse() && lid > 0.0F;
    }

    /** What the inventory at the vat shows: what goes into any of its recipes, and the container of a drink that waits in it. */
    private boolean usable(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (isContainer(stack)) {
            return containerFor(drinkWaiting()).map(container -> container.test(stack)).orElse(false);
        }
        return recipes.stream().anyMatch(recipe -> recipe.ingredients().stream().anyMatch(ingredient -> ingredient.test(stack)));
    }

    private boolean isContainer(ItemStack stack) {
        return KitchenVat.isContainer(recipes, stack);
    }

    /** A shift click in the inventory puts the whole stack in; a stack of containers has the waiting drink drawn into as many as it holds. */
    @Override
    public boolean onInventoryShiftClick(StationView view, int slot, ItemStack stack) {
        if (!usable(stack)) {
            return false;
        }
        use(view, slot, stack, true);
        return true;
    }

    private void use(StationView view, int slot, ItemStack stack, boolean all) {
        if (isContainer(stack)) {
            draw(view, all, slot);
            return;
        }
        CompoundTag how = new CompoundTag();
        how.putInt(VatStation.FROM, slot);
        view.sendAction(VatStation.ACTION_PUT, how);
        if (!isLidOpen(view) && view.state().getControl(VatStation.PROGRESS) <= 0.0F) {
            askLid(view, true);
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
     * A container on the cursor clicked over the vat draws the waiting drink into it, wherever on the opening it lands: the place
     * where things are dropped in lies over the same liquid.
     */
    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if (("mouth".equals(node) || "served".equals(node)) && isContainer(stack) && !drinkWaiting(view).isEmpty()) {
            draw(view, shift, -1);
            return true;
        }
        if ("book_open".equals(node)) {
            book.click(view, button);
            return true;
        }
        return clicked(view, node);
    }

    private boolean clicked(StationView view, String node) {
        switch (node) {
            case "lid" -> askLid(view, !isLidOpen(view));
            case "served" -> draw(view, Minecraft.getInstance().hasShiftDown(), -1);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** The drink waiting in the vat, as the last state change told it. */
    private ItemStack drinkWaiting() {
        return lastServings;
    }

    private ItemStack drinkWaiting(StationView view) {
        return view.state().getItem(VatStation.SERVINGS_SLOT);
    }

    private Optional<net.minecraft.world.item.crafting.Ingredient> containerFor(ItemStack drink) {
        return KitchenVat.bottling(recipes, drink).map(KitchenVat.Aging::container);
    }

    /**
     * The cook takes what was aged. A drink waiting in the vat is drawn into the container they bring, on their cursor or from inventory
     * slot {@code from}: it is dipped in and comes up full. With nothing to draw it into, the badge shows the container it wants.
     * Anything else (pickles, cheese) is taken as it lies.
     */
    private void draw(StationView view, boolean all, int from) {
        ItemStack drink = drinkWaiting(view);
        ItemStack served = view.state().getItem(VatStation.SERVED_SLOT);
        if (drink.isEmpty() && served.isEmpty()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (!drink.isEmpty()) {
            Optional<net.minecraft.world.item.crafting.Ingredient> container = containerFor(drink);
            ItemStack brought = player == null ? ItemStack.EMPTY
                    : from < 0 ? player.inventoryMenu.getCarried() : player.getInventory().getItem(from);
            if (container.isEmpty() || brought.isEmpty() || !container.get().test(brought)) {
                needUntil = time + NEED_TIME;
                needed = container.map(ingredient -> RecipeParts.items(ingredient).length > 0 ? RecipeParts.items(ingredient)[0].getItem() : null).orElse(null);
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
        take.putBoolean(VatStation.ALL, all);
        take.putInt(VatStation.FROM, from);
        view.sendAction(VatStation.ACTION_TAKE, take);
    }

    /** An empty container hangs over the drink, dips in and comes up as the drink, and goes to the cook at the bottom of the screen. */
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
        float scale = DIP_SIZE / ITEM_PIXELS;
        dip = new KitchenMotions.Dip(node.get(), full, over, new Vector3f(), away, this::surface, view.node("brew").orElse(null), scale, DIP_SIZE,
                0xFF000000 | shownColor, () -> view.playSound(SoundEvents.BOTTLE_FILL, "brew", 0.45F, 1.0F));
    }

    /** Whether the lid is up: as the cook clicked it, until the vat says so too. */
    private boolean isLidOpen(StationView view) {
        boolean open = view.state().getControl(VatStation.LID) > 0.5F;
        if (lidAsked != null && (lidAsked == open || time - lidAskedAt > 1.0F)) {
            lidAsked = null;
        }
        return lidAsked != null ? lidAsked : open;
    }

    private void askLid(StationView view, boolean open) {
        lidAsked = open;
        lidAskedAt = time;
        CompoundTag data = new CompoundTag();
        data.putBoolean(VatStation.OPEN, open);
        view.sendAction(VatStation.ACTION_LID, data);
    }

    private void refresh(StationView view) {
        StationState state = view.state();
        ItemStack servingsNow = state.getItem(VatStation.SERVINGS_SLOT);
        if (!ItemStack.isSameItem(servingsNow, lastServings)) {
            usable.clear();
        }
        lastServings = servingsNow.copy();
        contents.update(view, state.getItems(VatStation.INGREDIENTS));
        baseColor = mixedColor(view);
        served(view);
        badgeState(view);
    }

    /**
     * The colour of the liquid: what was poured in, each liquid by how much of it there is; a drink waiting in the vat in its own
     * colour; water's blue when there is nothing yet.
     */
    private int mixedColor(StationView view) {
        ItemStack drink = view.state().getItem(VatStation.SERVINGS_SLOT);
        float r = 0;
        float g = 0;
        float b = 0;
        float weight = 0;
        if (!drink.isEmpty() && !KitchenVat.isDrink(recipes, drink)) {
            // Ice cream: a warm cream, neither darkened nor chilled blue like a liquid.
            return KitchenLooks.mix(0xFF000000 | FoodColors.main(drink), CREAM, MASS_PALE);
        }
        if (!drink.isEmpty() && VatDrinks.of(drink).isPresent()) {
            // A drink waiting in the vat is as clear and as coloured as it is described.
            return drinkColor(drink);
        }
        if (!drink.isEmpty()) {
            int color = drinkColor(drink);
            float w = drink.getCount();
            r += (color >> 16 & 0xFF) * w;
            g += (color >> 8 & 0xFF) * w;
            b += (color & 0xFF) * w;
            weight += w;
        }
        for (ItemStack stack : contents.shown()) {
            if (stack.isEmpty() || !VatContents.isPoured(stack)) {
                continue;
            }
            int color = KitchenLooks.liquidColor(stack);
            float w = stack.getCount() * (RecipeParts.remainder(stack.getItem()) == net.minecraft.world.item.Items.BUCKET ? 3.0F : 1.0F);
            r += (color >> 16 & 0xFF) * w;
            g += (color >> 8 & 0xFF) * w;
            b += (color & 0xFF) * w;
            weight += w;
        }
        if (weight <= 0) {
            return baseColor;
        }
        // In the stone vat's shade a liquid looks deeper than on its icon.
        return 0xFF000000 | Math.round(r / weight * IN_SHADE) << 16 | Math.round(g / weight * IN_SHADE) << 8 | Math.round(b / weight * IN_SHADE);
    }

    /**
     * The colour of a drink the vat makes: the main colour of its icon. Its icon is a mug or a glass, not the drink in a bottle against
     * the empty bottle, so the pixels that differ from its container would be its foam or its glass.
     */
    private static int drinkColor(ItemStack drink) {
        return VatDrinks.of(drink).map(KegLiquids.Drink::color).orElseGet(() -> vivid(FoodColors.main(drink)));
    }

    /** A colour as a liquid shows it in bulk: more saturated than the few pixels of an icon, as beer is amber and not beige. */
    static int vivid(int rgb) {
        float[] hsb = java.awt.Color.RGBtoHSB(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, null);
        return 0xFF000000 | java.awt.Color.HSBtoRGB(hsb[0], Math.min(1.0F, hsb[1] * 1.6F + 0.1F), Math.min(1.0F, hsb[2] * 0.95F));
    }

    /** What was aged without a container lies in the vat: as its block model when it is a block with one (cheese), else flat. */
    private void served(StationView view) {
        ItemStack stack = view.state().getItem(VatStation.SERVED_SLOT);
        Item item = stack.isEmpty() ? null : stack.getItem();
        if (item != drawnServed) {
            drawnServed = item;
            view.node("dish").ifPresent(node -> {
                node.setVisible(item != null);
                if (item == null) {
                    return;
                }
                float extent = blockExtent(item);
                if (extent > 0.0F) {
                    node.setVisual(Visual.ofBlock(((BlockItem) item).getBlock().defaultBlockState()));
                    node.rotateTo(0.0F, 0.0F, 0.0F, 0.0F);
                    node.scaleTo(Math.min(DISH_BLOCK_SCALE, DISH_BLOCK / extent), 0.0F);
                    node.moveTo(0.0F, 0.0F, VatContents.PILE_BACK, 0.0F);
                } else {
                    node.setVisual(Visual.ofItem(item).withAnchor(ICON_MIDDLE));
                    node.rotateTo(-90.0F, 0.0F, 0.0F, 0.0F);
                    node.scaleTo(DISH_ITEM / ITEM_PIXELS, 0.0F);
                    node.moveTo(0.0F, 0.15F, VatContents.PILE_BACK, 0.0F);
                }
            });
        }
        boolean any = item != null || !view.state().getItem(VatStation.SERVINGS_SLOT).isEmpty();
        view.node("served").ifPresent(node -> node.setVisible(any));
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

    private static int doneCount(StationState state) {
        return state.getItem(VatStation.SERVED_SLOT).getCount() + state.getItem(VatStation.SERVINGS_SLOT).getCount();
    }

    /** What the badge says: what ages or waits, how many, whether it was just done. */
    private void badgeState(StationView view) {
        StationState state = view.state();
        float progress = state.getControl(VatStation.PROGRESS);
        ItemStack waiting = state.getItem(VatStation.SERVINGS_SLOT);
        ItemStack served = state.getItem(VatStation.SERVED_SLOT);
        making = KitchenVat.matchingPlaces(recipes, state.getItems(VatStation.INGREDIENTS));
        aging = making.isPresent();
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
        if (progress > 0.0F) {
            sawAging = true;
        }
        int done = doneCount(state);
        if (done > doneBefore && sawAging) {
            sawAging = progress > 0.0F;
            done(view);
        }
        doneBefore = done;
    }

    /** Something is done: the liquid ripples and steams, and at the station the badge swells with a chime. */
    private void done(StationView view) {
        doneAt = time;
        view.node("brew").ifPresent(brew -> brew.ripple(8.0F, 8.0F, 0.6F));
        view.spawnOnLiquid(DiegesisParticles.STEAM, "brew", 8, STEAM_COLOR, STEAM_RISE);
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
        boolean open = inUse && isLidOpen(view);
        turnToWorld(view);
        swing(view, dt, open);
        boolean whole = view.isBlockHidden();
        for (String part : VAT_PARTS) {
            view.node(part).ifPresent(node -> node.setVisible(whole));
        }
        // What is inside shows only while the lid is up: in the world the shut lid covers it.
        boolean inside = whole && lid > 0.0F;
        KitchenVat.Temperature warmth = KitchenVat.temperature(view.level().getBlockState(view.pos()));
        float progress = view.state().getControl(VatStation.PROGRESS);
        boolean wet = view.state().getTank(VatStation.BREW).amount() > 0;
        liquid(view, dt, inside, warmth, progress, wet);
        float current = !wet || warmth == KitchenVat.Temperature.COLD ? 0.0F : warmth == KitchenVat.Temperature.HOT ? HOT_CURRENT : CALM_CURRENT;
        turned += dt * current;
        view.node("brew").ifPresent(brew -> brew.setCurrent(current));
        boolean reachable = open && lid >= 1.0F;
        contents.frame(view, dt, time, turned, level, shownColor, inside, wet, inside && wet && current > 0.0F, reachable);
        if (dip != null && !dip.frame(view, dt)) {
            dip = null;
        }
        view.node("dish").ifPresent(node -> node.setVisible(inside && drawnServed != null));
        view.node("served").ifPresent(node -> node.setEnabled(reachable));
        view.node("mouth").ifPresent(node -> node.setEnabled(reachable));
        heat(view, dt, warmth, inside, wet);
        cold(view, dt, warmth, inside);
        book.frame(view, dt);
        float appear = RecipeBook.appear(view);
        badge(view, inUse && RecipeBook.isWorkedAt(view) && appear > RecipeBook.SPECK, warmth, open);
        float swell = badgeSwell();
        for (String part : BADGE_PARTS) {
            view.node(part).ifPresent(node -> node.scaleTo(appear * swell, 0.0F));
        }
    }

    /**
     * The lid swings up about its hinge on the far side, comes to rest leant back on the rim with a little bounce; shutting, it drops
     * back with a knock.
     */
    private void swing(StationView view, float dt, boolean open) {
        float before = lid;
        lid = open ? Math.min(1.0F, lid + dt / LID_TIME) : Math.max(0.0F, lid - dt / LID_TIME);
        if (open && !wasOpening && before < 1.0F) {
            view.playSound(SoundEvents.BARREL_OPEN, "lid", 0.45F, 0.95F + view.level().getRandom().nextFloat() * 0.1F);
        }
        if (before > 0.0F && lid <= 0.0F) {
            view.playSound(SoundEvents.BARREL_CLOSE, "lid", 0.45F, 0.95F + view.level().getRandom().nextFloat() * 0.1F);
        }
        if (before < 1.0F && lid >= 1.0F) {
            // The lid comes to rest on the far rim with a knock of wood on stone.
            view.playSound(SoundEvents.WOOD_HIT, "lid", 0.35F, 0.9F);
        }
        wasOpening = open;
        float k = open ? KitchenMotions.out(lid) : lid * lid;
        // Open, it rocks back once on the rim: a little past its rest at the end of the swing, settling.
        float bounce = open && lid > 0.75F ? LID_BOUNCE * Mth.sin((lid - 0.75F) / 0.25F * Mth.PI) : 0.0F;
        float angle = LID_OPEN * k + bounce;
        view.node("hinge").ifPresent(node -> node.rotateTo(angle, 0.0F, 0.0F, 0.0F));
    }

    /**
     * The liquid: as high as what was poured or waits to be drawn, in its colour, turning to the drink's as it ages, and what ages sends
     * up a bubble now and then. Over ice it goes pale and lies still: cold water does not move.
     */
    private void liquid(StationView view, float dt, boolean inside, KitchenVat.Temperature warmth, float progress, boolean wet) {
        Optional<NodeView> brew = view.node("brew");
        if (brew.isEmpty()) {
            return;
        }
        NodeView node = brew.get();
        float wanted = view.state().getTank(VatStation.BREW).amount() / 100.0F;
        level = Mth.lerp(Math.min(1.0F, dt * 3.0F), level, wanted);
        int target = baseColor;
        // A drink turns to its colour as it ages; what will set (ice cream) keeps the colour it has until it sets.
        if (aging && progress > 0.0F && KitchenVat.isDrink(recipes, making.get().result())) {
            target = KitchenLooks.mix(baseColor, drinkColor(making.get().result()), TURNING * KitchenMotions.smooth(progress));
        }
        boolean cream = view.state().getTank(VatStation.BREW).amountOf(com.pockyl.handson_delight.station.VatLogic.CREAM_LIQUID) > 0;
        if (!cream) {
            target = KitchenLooks.mix(target, CHILLED, CHILL * KitchenMotions.smooth(frost));
        }
        if (target != shownColor) {
            shownColor = target;
            node.setLiquidColor(target);
        }
        node.setVisible(inside);
        // Drawn as a grid of pixels, as the pot's soup is: a plain surface catches the light along its diagonal, which shows on a
        // shallow drink. The patches themselves are too faint to see.
        // Each drink as it is, as in the keg: its head of foam, its fizz, what rises off it; a fermenting one grows a yeast head as it
        // ages. Never on what is frozen, nor on ice cream.
        ItemStack drink = view.state().getItem(VatStation.SERVINGS_SLOT);
        Optional<KegLiquids.Drink> waiting = VatDrinks.of(drink);
        Optional<KegLiquids.Drink> brewing = aging && progress > 0.0F ? VatDrinks.of(making.get().result()).filter(KegLiquids.Drink::brewed)
                : Optional.empty();
        boolean frozen = warmth == KitchenVat.Temperature.COLD;
        float foam = 0.0F;
        int foamColor = KegLiquids.HEAD;
        if (waiting.isPresent() && waiting.get().foam() > 0.0F) {
            foam = waiting.get().foam();
            foamColor = waiting.get().foamColor();
        }
        if (brewing.isPresent()) {
            float head = BREWING_HEAD * KitchenMotions.smooth(progress);
            if (head > foam) {
                foam = head;
                foamColor = KegLiquids.HEAD;
            }
        }
        node.setFoam(wet && !frozen ? foam : 0.0F, foamColor);
        node.setMottle(wet ? Math.max(SMOOTH_GRID, waiting.map(KegLiquids.Drink::mottle).orElse(0.0F)) : 0.0F);
        if (inside && wet && !frozen && waiting.isPresent()) {
            fizz += dt * waiting.get().fizz();
            if (fizz >= 1.0F) {
                view.spawnInLiquid(DiegesisParticles.BUBBLE, "brew", (int) fizz, BUBBLE_COLOR, RISE);
                fizz -= (int) fizz;
            }
            if (waiting.get().rising() != null) {
                rising += dt * waiting.get().risingRate();
                if (rising >= 1.0F) {
                    view.spawnOnLiquid(waiting.get().rising(), "brew", (int) rising, waiting.get().risingColor(), new Vector3f(0.0F, 2.0F, 0.0F));
                    rising -= (int) rising;
                }
            }
        }
        if (inside && wet && aging && warmth == KitchenVat.Temperature.NORMAL) {
            bubbles += dt * AGING_BUBBLES;
            if (bubbles >= 1.0F) {
                view.spawnInLiquid(DiegesisParticles.BUBBLE, "brew", (int) bubbles, BUBBLE_COLOR, RISE);
                bubbles -= (int) bubbles;
            }
        }
    }

    /**
     * Heat under the vat: the liquid rolls and seethes along the walls, bubbles rise and pop at the edge, steam and a shimmer of hot air
     * go up, and the boil is heard. Shut, steam is forced out under the lid at its edges and the boil is muffled.
     */
    private void heat(StationView view, float dt, KitchenVat.Temperature warmth, boolean inside, boolean wet) {
        boolean hot = warmth == KitchenVat.Temperature.HOT;
        view.node("brew").ifPresent(brew -> {
            brew.setAgitation(hot && wet ? HOT_AGITATION : 0.0F);
            brew.setEdgeBoil(hot && wet ? HOT_EDGE : 0.0F);
        });
        boolean shut = lid <= 0.0F;
        view.loopSound("boil", PotSounds.BODY, !hot || !wet ? 0.0F : shut ? BOIL_SHUT_VOLUME : BOIL_VOLUME, 1.0F);
        if (!hot) {
            return;
        }
        RandomSource random = view.level().getRandom();
        if (inside && wet) {
            bubbles += dt * HOT_BUBBLES;
            if (bubbles >= 1.0F) {
                view.spawnInLiquid(DiegesisParticles.BUBBLE, "brew", (int) bubbles, BUBBLE_COLOR, RISE);
                bubbles -= (int) bubbles;
            }
            edgePops += dt * HOT_EDGE_POPS;
            if (edgePops >= 1.0F) {
                view.spawnAtLiquidEdge(DiegesisParticles.BUBBLE_POP, "brew", (int) edgePops, BUBBLE_COLOR, EDGE_BUBBLE_SIZE);
                edgePops -= (int) edgePops;
            }
            steam += dt * HOT_STEAM;
            if (steam >= 1.0F) {
                view.spawnOnLiquid(DiegesisParticles.STEAM, "brew", (int) steam, STEAM_COLOR, STEAM_RISE);
                steam -= (int) steam;
            }
            boilClock -= dt;
            if (boilClock <= 0.0F) {
                boilClock = BOIL_EVERY;
                view.playSound(FarmersDelightContent.BOIL_SOUP, "brew", 0.7F, 0.9F + 0.1F * random.nextFloat());
            }
        }
        if (!shut || inside && wet) {
            shimmer += dt * HOT_SHIMMER;
            if (shimmer >= 1.0F) {
                view.spawnParticles(HandsOnDelightClient.HEAT, new Vector3f(8.0F, RIM + 0.5F, 8.0F), new Vector3f(0.0F, 3.0F, 0.0F), 4.0F,
                        (int) shimmer, 0x48FFFFFF);
                shimmer -= (int) shimmer;
            }
        }
        if (shut && wet) {
            seep += dt * SEEP_RATE;
            if (seep >= 1.0F) {
                seep -= 1.0F;
                // Out under the lid at one of its edges, pushed sideways and up.
                int side = random.nextInt(4);
                float along = 2.5F + random.nextFloat() * 11.0F;
                float x = side == 0 ? 2.0F : side == 1 ? 14.0F : along;
                float z = side == 2 ? 2.0F : side == 3 ? 14.0F : along;
                Vector3f out = new Vector3f(side == 0 ? -1.2F : side == 1 ? 1.2F : 0.0F, 3.0F, side == 2 ? -1.2F : side == 3 ? 1.2F : 0.0F);
                view.spawnParticles(DiegesisParticles.STEAM, new Vector3f(x, RIM + 0.1F, z), out, 0.6F, 1, STEAM_COLOR);
            }
        }
    }

    /**
     * Ice under the vat: rime grows up its outside from the foot, thick there and in single crystals higher up, and rings the liquid on
     * the inside walls; it thaws again when the ice is gone. Cold mist spills over the rim and slides down the sides; the rime glitters
     * now and then.
     */
    private void cold(StationView view, float dt, KitchenVat.Temperature warmth, boolean inside) {
        boolean cold = warmth == KitchenVat.Temperature.COLD;
        frost = cold ? Math.min(1.0F, frost + dt / FROST_TIME) : Math.max(0.0F, frost - dt / FROST_TIME);
        int tint = Math.round(RIME_ALPHA * KitchenMotions.smooth(frost)) << 24 | RIME;
        boolean frosted = frost > 0.01F;
        view.node("frost").ifPresent(node -> {
            node.setVisible(frosted);
            node.setTint(tint);
        });
        boolean wet = view.state().getTank(VatStation.BREW).amount() > 0;
        float ring = surface() - RING_UNDER;
        view.node("frost_ring").ifPresent(node -> {
            node.setVisible(frosted && inside && wet);
            node.setTint(tint);
            node.moveTo(0.0F, ring, 0.0F, 0.0F);
        });
        if (!cold) {
            return;
        }
        RandomSource random = view.level().getRandom();
        mist += dt * MIST_RATE;
        if (mist >= 1.0F) {
            mist -= 1.0F;
            // Over the outer edge of the rim, out and down the side.
            int side = random.nextInt(4);
            float along = random.nextFloat() * 16.0F;
            float x = side == 0 ? -0.2F : side == 1 ? 16.2F : along;
            float z = side == 2 ? -0.2F : side == 3 ? 16.2F : along;
            Vector3f out = new Vector3f(side == 0 ? -0.8F : side == 1 ? 0.8F : 0.0F, -2.6F, side == 2 ? -0.8F : side == 3 ? 0.8F : 0.0F);
            view.spawnParticles(DiegesisParticles.STEAM, new Vector3f(x, RIM + 0.3F, z), out, 0.5F, 1, MIST_COLOR);
        }
        glints += dt * GLINT_RATE * frost;
        if (glints >= 1.0F) {
            glints -= 1.0F;
            Vector3f at = inside && random.nextBoolean()
                    ? new Vector3f(2.2F + random.nextFloat() * 11.6F, surface() + 0.4F, random.nextBoolean() ? 2.2F : 13.8F)
                    : new Vector3f(random.nextBoolean() ? -0.1F : 16.1F, random.nextFloat() * 6.0F, random.nextFloat() * 16.0F);
            view.spawnParticles(DiegesisParticles.GLINT, at, new Vector3f(), 0.1F, 1, GLINT_COLOR);
        }
    }

    private float surface() {
        return FLOOR + DEPTH * level;
    }

    float badgeSwell() {
        float since = time - doneAt;
        if (since >= 0.0F && since < DONE_TIME) {
            return 1.0F + DONE_SWELL * Mth.sin(Mth.PI * since / DONE_TIME);
        }
        return ready ? 1.0F + READY_BREATH * (0.5F - 0.5F * Mth.cos(time * 2.4F)) : 1.0F;
    }

    /**
     * The badge while something ages or waits: what it makes, the vat's warmth against the recipe's, how far it has aged and the time
     * left, or how many are ready. Bar and time go grey while the lid is up and aging waits.
     */
    private void badge(StationView view, boolean cookHere, KitchenVat.Temperature warmth, boolean open) {
        boolean shown = cookHere && !dish.isEmpty();
        view.node("badge").ifPresent(plate -> plate.setVisible(shown));
        float progress = view.state().getControl(VatStation.PROGRESS);
        int left = view.state().data().getIntOr(VatStation.LEFT, 0);
        int seconds = aging && progress > 0.0F && left > 0 ? Math.max(1, Mth.ceil(left / 20.0F)) : 0;
        int bar = aging ? Math.round(progress * BAR_LENGTH) : ready ? BAR_LENGTH : 0;
        String text = seconds >= 60 ? seconds / 60 + ":" + String.format("%02d", seconds % 60) : seconds > 0 ? Integer.toString(seconds)
                : !aging && servings > 0 ? "x" + Math.min(servings, 99) : "";
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
        int now = warmth.ordinal();
        int wanted = making.map(recipe -> recipe.temperature().ordinal()).orElse(-1);
        boolean thermometer = !ready || wanted >= 0;
        String key = now + "/" + wanted;
        if (!key.equals(drawnThermo)) {
            drawnThermo = key;
            // Dim cells cold to hot, a mark under the one the recipe wants; the vat's own cell lit and taller.
            MeshShape.Builder cells = MeshShape.builder();
            for (int cell = 0; cell < WARMTH_COLORS.length; cell++) {
                PotBehavior.pixels(cells, CELL_LEFT + cell * CELL_STEP, CELL_TOP, 2, 3);
                if (cell == wanted) {
                    PotBehavior.pixels(cells, CELL_LEFT + cell * CELL_STEP, FITS_TOP, 2, 1);
                }
            }
            view.node("thermo").ifPresent(node -> node.setShape(cells.build(), BADGE_TEXTURE, true));
            MeshShape.Builder cell = MeshShape.builder();
            PotBehavior.pixels(cell, CELL_LEFT + now * CELL_STEP, CELL_TOP - 1, 2, 5);
            view.node("thermo_now").ifPresent(node -> {
                node.setShape(cell.build(), BADGE_TEXTURE, true);
                node.setTint(WARMTH_COLORS[now]);
            });
        }
        view.node("dial_track").ifPresent(node -> node.setVisible(shown));
        view.node("dial").ifPresent(node -> node.setVisible(shown));
        view.node("thermo").ifPresent(node -> node.setVisible(shown && thermometer));
        // The vat's own cell blinks while it is too warm or too cold for what is in it.
        boolean fits = wanted < 0 || wanted == now;
        view.node("thermo_now").ifPresent(node -> node.setVisible(shown && thermometer && (fits || Mth.sin(time * 6.0F) > 0.0F)));
        preview(view, shown, seconds);
    }

    /** The badge's line of text under its bar, in pixel glyphs: digits, a colon, {@code x} for servings, {@code d} for days. */
    static void text(MeshShape.Builder shape, String text) {
        int width = -1;
        for (char c : text.toCharArray()) {
            width += (c == ':' ? 1 : 3) + 1;
        }
        int x = -width / 2 - width % 2;
        for (char c : text.toCharArray()) {
            String[] glyph = c == ':' ? COLON : c == 'x' ? TIMES : c == 'd' ? DAYS : PotBehavior.DIGITS[c - '0'];
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

    /**
     * The icon of what ages or waits, with its name and the seconds left on hover. When the cook has no container for a waiting drink,
     * it shows the container it wants for a moment, blinking.
     */
    private void preview(StationView view, boolean shown, int seconds) {
        boolean asking = needed != null && time < needUntil;
        view.node("preview").ifPresent(node -> {
            node.setVisible(shown && (!asking || Mth.sin(time * 9.0F) > -0.3F));
            if (!shown) {
                return;
            }
            Item icon = asking ? needed : dish.getItem();
            if (shownDish != icon) {
                shownDish = icon;
                node.setVisual(Visual.ofItem(icon).withAnchor(ICON_MIDDLE));
            }
            node.setTooltip(asking ? new ItemStack(needed).getHoverName()
                    : seconds > 0 ? Component.translatable("handson_delight.pot.cooking", dish.getHoverName(), seconds) : dish.getHoverName());
        });
    }
}
