package com.pockyl.handson_delight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
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
import com.pockyl.handson_delight.station.KitchenMeltingPot;
import com.pockyl.handson_delight.station.MeltingPotStation;

import java.util.Optional;

/**
 * What the melting pot shows: its upper pot open, what has melted standing in it in its colour and going slowly round while it is
 * heated, the piece that melts now floating in it, going soft and shrinking into the pool it lets out, the rest of the stack
 * waiting round it. Over heat the water of the lower pot steams round the upper one. A bottle or bucket dips in and comes up full.
 */
public final class MeltingPotBehavior implements StationBehavior {
    private static final String[] BADGE_PARTS = {"badge", "dial_track", "dial", "flame", "preview"};
    private static final String[] POT_PARTS = {"body", "tray"};
    private static final ResourceLocation BADGE_TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "block/white_concrete");
    private static final Vector3f ICON_MIDDLE = new Vector3f(8.0F, 8.0F, 8.0F);
    private static final float ITEM_PIXELS = 8.0F;
    /** The upper pot's floor and how deep the liquid stands in it when full, scene pixels; its middle. */
    private static final float FLOOR = 9.0F;
    private static final float DEPTH = 3.6F;
    private static final float MIDDLE = 8.0F;
    /** A piece lies this big; the ones waiting go round the middle at this distance. */
    private static final float PIECE = 3.4F;
    private static final float WAITING_RADIUS = 2.4F;
    /** What has melted goes round this fast while heated, radians a second. */
    private static final float SWIRL = 0.45F;
    private static final float ARRIVE_TIME = 0.4F;
    private static final float ARRIVE_ARC = 3.0F;
    private static final float DIP_SIZE = 3.4F;
    private static final float STEAM_RATE = 3.0F;
    private static final int STEAM_COLOR = 0x5CFFFFFF;
    private static final float BUBBLE_RATE = 0.8F;
    private static final int BUBBLE_COLOR = 0x90FFFFFF;

    private static final int PLATE_COLOR = 0x8C1E140C;
    private static final int DIAL_COLOR = 0xFFFFCB6B;
    private static final int WAITING_COLOR = 0xFFA8A49C;
    private static final int TRACK_COLOR = 0x50FFF2DC;
    private static final int FLAME_LIT = 0xFFFF8A3C;
    private static final int FLAME_OUT = 0xFF8C8C8C;
    private static final int BAR_LEFT = -7;
    private static final int BAR_TOP = 10;
    private static final int BAR_LENGTH = 14;
    private static final int TEXT_TOP = 13;
    private static final String[] COLON = {".", "#", ".", "#", "."};
    private static final String[] TIMES = {"...", "#.#", ".#.", "#.#", "..."};
    private static final String[] FLAME = {".#.", "###", "###"};
    private static final float DONE_SWELL = 0.25F;
    private static final float DONE_TIME = 0.5F;
    /** The pool a melting piece lets out: its radius at the start and at the end of melting, scene pixels; its cells; its fade. */
    private static final float BLOT_FROM = 1.0F;
    private static final float BLOT_TO = 3.7F;
    private static final float BLOT_CELL = 0.5F;
    private static final float BLOT_FADE = 0.9F;
    private static final float BLOT_LIFT = 0.04F;
    private static final ResourceLocation BLOT_TEXTURE = HandsOnDelight.id("block/white");

    private final RecipeBook book = new RecipeBook(new MeltingPotBook());
    private final UsableCache usable = new UsableCache();
    private float time;
    private float level;
    private int shownColor;
    /** The fluid whose colour was last worked out, and that colour: reading a texture every frame would be wasteful. */
    private Fluid colorOf;
    private int colorOfFluid;
    private float turned;
    private Item drawnPiece;
    private int drawnCount = -1;
    private int lastCount;
    private int lastAmount;
    private boolean sawMelting;
    private float arriving = Float.NaN;
    private Vector3f arriveFrom = new Vector3f();
    private KitchenMotions.Dip dip;
    private float steam;
    private float bubbles;
    private float doneAt = -10.0F;
    private String drawnDial = "";
    private Item shownIcon;
    /** The pool under the melting piece: the radius drawn, its colour, what it melts into, and its size when the piece was done. */
    private float blotDrawn = -1.0F;
    private int blotColor;
    private Fluid blotOf;
    private float blotLeft;

    public MeltingPotBehavior(StationView view) {
    }

    private static KitchenMeltingPot pot(StationView view) {
        return KitchenMeltingPot.of(view.level().getBlockEntity(view.pos()));
    }

    private static Fluid fluid(StationView view) {
        String id = view.state().data().getString(MeltingPotStation.FLUID);
        ResourceLocation key = id.isEmpty() ? null : ResourceLocation.tryParse(id);
        return key == null ? Fluids.EMPTY : BuiltInRegistries.FLUID.get(key);
    }

    @Override
    public void onSceneReady(StationView view) {
        drawnDial = "";
        drawnPiece = null;
        drawnCount = -1;
        shownColor = 0;
        colorOf = null;
        shownIcon = null;
        blotDrawn = -1.0F;
        blotOf = null;
        view.node("dial").ifPresent(dial -> dial.setTint(DIAL_COLOR));
        view.node("dial_track").ifPresent(track -> track.setTint(TRACK_COLOR));
        view.node("badge").ifPresent(plate -> {
            plate.setTint(PLATE_COLOR);
            MeshShape.Builder shape = MeshShape.builder();
            PotBehavior.pixels(shape, -10, -16, 20, 35);
            PotBehavior.pixels(shape, -9, -17, 18, 1);
            PotBehavior.pixels(shape, -9, 19, 18, 1);
            plate.setShape(shape.build(), BADGE_TEXTURE, true);
        });
        view.node("flame").ifPresent(flame -> {
            MeshShape.Builder shape = MeshShape.builder();
            glyph(shape, FLAME, -1, -14);
            flame.setShape(shape.build(), BADGE_TEXTURE, true);
        });
        for (String part : BADGE_PARTS) {
            view.node(part).ifPresent(node -> node.setOnTop(true));
        }
        view.node("book_open").ifPresent(node -> node.setPainter(book));
        lastCount = view.state().getItem(MeltingPotStation.INPUT_SLOT).getCount();
        lastAmount = view.state().getTank(MeltingPotStation.MELT).amount();
    }

    @Override
    public void onOpen(StationView view) {
        usable.clear();
        view.setInventoryFilter(stack -> usable.test(stack, this::usable));
        book.opened(view);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && view.openedByUse() && player.inventoryMenu.getCarried().isEmpty() && usable(player.getMainHandItem())) {
            use(view, player.getInventory().selected);
        }
    }

    @Override
    public void onClose(StationView view) {
        book.shut(view);
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        StationState state = view.state();
        int count = state.getItem(MeltingPotStation.INPUT_SLOT).getCount();
        if (count > lastCount && view.isFocused()) {
            Optional<Vector3f> from = FromPlayer.point(view, 26.0F, 0, 1);
            if (from.isPresent()) {
                arriveFrom = from.get();
                arriving = 0.0F;
            }
        }
        if (state.getControl(MeltingPotStation.PROGRESS) > 0.0F) {
            sawMelting = true;
        }
        int amount = state.getTank(MeltingPotStation.MELT).amount();
        if (count < lastCount && sawMelting && amount > lastAmount) {
            melted(view);
        }
        lastCount = count;
        lastAmount = amount;
    }

    /** A piece has melted away: the liquid rises with a ripple and a soft plop, and the badge swells. */
    private void melted(StationView view) {
        sawMelting = false;
        doneAt = time;
        blotLeft = Math.max(blotLeft, Math.max(blotDrawn, 0.0F));
        view.node("melt").ifPresent(melt -> melt.ripple(MIDDLE, MIDDLE, 0.5F));
        view.playSound(SoundEvents.HONEY_BLOCK_SLIDE, "melt", 0.3F, 1.3F);
    }

    private boolean usable(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(Items.GLASS_BOTTLE) || stack.is(Items.BUCKET)) {
            return true;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && KitchenMeltingPot.melting(KitchenMeltingPot.recipes(minecraft.level), stack).isPresent();
    }

    @Override
    public boolean onInventoryShiftClick(StationView view, int slot, ItemStack stack) {
        if (!usable(stack)) {
            return false;
        }
        use(view, slot);
        return true;
    }

    /** The stack in inventory slot {@code slot} (-1: the cursor) goes in to melt, or, an empty bottle or bucket, draws what has melted. */
    private void use(StationView view, int slot) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack stack = slot < 0 ? player.inventoryMenu.getCarried() : player.getInventory().getItem(slot);
        if (stack.is(Items.GLASS_BOTTLE) || stack.is(Items.BUCKET)) {
            draw(view, slot, stack, false);
            return;
        }
        CompoundTag how = new CompoundTag();
        how.putInt(MeltingPotStation.FROM, slot);
        view.sendAction(MeltingPotStation.ACTION_PUT, how);
    }

    /** An empty bottle or bucket dips into what has melted and comes up full; one with nothing to fill it with gets a low note. */
    private void draw(StationView view, int slot, ItemStack empty, boolean all) {
        KitchenMeltingPot pot = pot(view);
        Fluid fluid = fluid(view);
        int amount = view.state().getTank(MeltingPotStation.MELT).amount() * KitchenMeltingPot.TANK / 100;
        boolean bucket = empty.is(Items.BUCKET);
        ItemStack full = pot == null ? ItemStack.EMPTY : bucket ? new ItemStack(fluid.getBucket()) : pot.handson$containerOf(fluid);
        if (fluid == Fluids.EMPTY || full.isEmpty() || full.is(Items.AIR) || amount < (bucket ? 1000 : 250)
                || !bucket && full.is(fluid.getBucket())) {
            view.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), "badge", 0.3F, 0.7F);
            return;
        }
        Optional<NodeView> node = view.node("dipper");
        if (dip == null && node.isPresent()) {
            Vector3f over = new Vector3f(MIDDLE - 1.5F, surface() + 3.0F, MIDDLE - 1.0F);
            Vector3f away = FromPlayer.point(view, 26.0F, 0, 1).orElse(new Vector3f(8.0F, 26.0F, -6.0F));
            KitchenMotions.showItem(node.get(), empty.getItem(), DIP_SIZE, 0xFFFFFFFF);
            node.get().moveTo(over.x, over.y, over.z, 0.0F);
            node.get().rotateTo(0.0F, 0.0F, 0.0F, 0.0F);
            dip = new KitchenMotions.Dip(node.get(), full.getItem(), over, new Vector3f(), away, this::surface, view.node("melt").orElse(null),
                    DIP_SIZE / ITEM_PIXELS, DIP_SIZE, 0xFF000000 | shownColor, () -> view.playSound(SoundEvents.BOTTLE_FILL, "melt", 0.45F, 0.9F));
        }
        CompoundTag how = new CompoundTag();
        how.putInt(MeltingPotStation.FROM, slot);
        how.putBoolean(MeltingPotStation.ALL, all);
        view.sendAction(MeltingPotStation.ACTION_DRAW, how);
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Click click && click.node().equals("book_open")) {
            book.click(view, click.button());
        } else if (event instanceof SceneEvent.Click click && click.button() == 0 && "mouth".equals(click.node())) {
            // An empty hand is offered a bottle from the inventory, else a bucket.
            int slot = slotOf(Items.GLASS_BOTTLE);
            if (slot < 0) {
                slot = slotOf(Items.BUCKET);
            }
            LocalPlayer player = Minecraft.getInstance().player;
            if (slot >= 0 && player != null) {
                draw(view, slot, player.getInventory().getItem(slot), false);
            }
        }
    }

    private static int slotOf(Item item) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return -1;
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (player.getInventory().getItem(slot).is(item)) {
                return slot;
            }
        }
        return -1;
    }

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
        if (("mouth".equals(node) || "input".equals(node)) && (stack.is(Items.GLASS_BOTTLE) || stack.is(Items.BUCKET))) {
            draw(view, -1, stack, shift);
            return true;
        }
        if (usable(stack)) {
            use(view, -1);
            return true;
        }
        return false;
    }

    private float surface() {
        return FLOOR + DEPTH * level;
    }

    @Override
    public void frame(StationView view, float dt) {
        time += dt;
        boolean inUse = view.isInUse();
        boolean whole = view.isBlockHidden();
        BlockState block = view.level().getBlockState(view.pos());
        boolean onTray = block.getProperties().stream().anyMatch(property -> property.getName().equals("support")
                && !"none".equals(nameOf(block.getValue(property))));
        view.node("body").ifPresent(node -> node.setVisible(whole));
        view.node("tray").ifPresent(node -> node.setVisible(whole && onTray));
        StationState state = view.state();
        boolean heated = state.data().getBoolean(MeltingPotStation.HEATED);
        float progress = state.getControl(MeltingPotStation.PROGRESS);
        liquid(view, dt, heated);
        pieces(view, dt, progress);
        blot(view, progress);
        effects(view, dt, heated);
        if (dip != null && !dip.frame(view, dt)) {
            dip = null;
        }
        view.node("mouth").ifPresent(node -> node.setEnabled(inUse));
        // With a bottle or bucket on the cursor the whole opening draws: what melts is not in the way.
        LocalPlayer player = Minecraft.getInstance().player;
        ItemStack carried = player == null ? ItemStack.EMPTY : player.inventoryMenu.getCarried();
        boolean drawing = carried.is(Items.GLASS_BOTTLE) || carried.is(Items.BUCKET);
        view.node("input").ifPresent(node -> node.setEnabled(inUse && Float.isNaN(arriving) && !drawing));
        book.frame(view, dt);
        float appear = RecipeBook.appear(view);
        badge(view, inUse && RecipeBook.isWorkedAt(view) && appear > RecipeBook.SPECK, heated, progress);
        float since = time - doneAt;
        float swell = since >= 0.0F && since < DONE_TIME ? 1.0F + DONE_SWELL * Mth.sin(Mth.PI * since / DONE_TIME) : 1.0F;
        for (String part : BADGE_PARTS) {
            view.node(part).ifPresent(node -> node.scaleTo(appear * swell, 0.0F));
        }
    }

    /** What has melted, as high as there is of it, in its colour, going slowly round while it is heated. */
    private void liquid(StationView view, float dt, boolean heated) {
        Optional<NodeView> melt = view.node("melt");
        if (melt.isEmpty()) {
            return;
        }
        float wanted = view.state().getTank(MeltingPotStation.MELT).amount() / 100.0F;
        level = Mth.lerp(Math.min(1.0F, dt * 3.0F), level, wanted);
        Fluid fluid = fluid(view);
        if (fluid != colorOf || colorOfFluid == 0) {
            colorOf = fluid;
            colorOfFluid = color(view, fluid);
        }
        int color = colorOfFluid;
        if (color != shownColor) {
            shownColor = color;
            melt.get().setLiquidColor(color);
        }
        float current = heated ? SWIRL : 0.0F;
        turned += dt * current;
        melt.get().setCurrent(current);
        melt.get().setMottle(0.02F);
        melt.get().setVisible(view.isBlockHidden() || view.isInUse());
    }

    private static int color(StationView view, Fluid fluid) {
        if (fluid == Fluids.EMPTY) {
            return 0xFF6A3F22;
        }
        int tint = ClientPlatform.INSTANCE.fluidColor(fluid);
        if (tint != 0) {
            return 0xFF000000 | tint;
        }
        // Untinted (Extra Delight's syrups): the colour is in the fluid's own texture. Its bottle would give the glass's blue.
        ResourceLocation texture = ClientPlatform.INSTANCE.fluidTexture(fluid);
        int drawn = texture == null ? 0 : FoodColors.ofTexture(texture);
        if (drawn != 0) {
            return 0xFF000000 | drawn;
        }
        KitchenMeltingPot pot = pot(view);
        ItemStack bottle = pot == null ? ItemStack.EMPTY : pot.handson$containerOf(fluid);
        return bottle.isEmpty() ? 0xFFF4F0E8 : KitchenLooks.liquidColor(bottle);
    }

    /**
     * The stack that melts: the piece melting now floats in the middle, goes soft (it takes the liquid's colour) and shrinks as it
     * melts; up to two more wait round it, going round with the liquid. Dropped in, a piece hops in from the cook.
     */
    private void pieces(StationView view, float dt, float progress) {
        ItemStack stack = view.state().getItem(MeltingPotStation.INPUT_SLOT);
        Item item = stack.isEmpty() ? null : stack.getItem();
        int count = Math.min(3, stack.getCount());
        if (item != drawnPiece || count != drawnCount) {
            drawnPiece = item;
            drawnCount = count;
            for (int i = 0; i < 3; i++) {
                boolean on = item != null && i < count;
                view.node("piece_" + i).ifPresent(node -> {
                    node.setVisible(on);
                    if (on) {
                        node.setVisual(Visual.ofItem(item).withAnchor(ICON_MIDDLE));
                    }
                });
            }
        }
        float top = surface() - FLOOR;
        for (int i = 0; i < 3; i++) {
            int index = i;
            boolean on = item != null && i < count && !(i == count - 1 && !Float.isNaN(arriving));
            view.node("piece_" + i).ifPresent(node -> {
                node.setVisible(on);
                if (!on) {
                    return;
                }
                if (index == 0) {
                    float soft = KitchenMotions.smooth(progress);
                    // It loses its size into the pool it lets out, lying on it (a piece sunk under the pool would be hidden by it).
                    node.moveTo(0.0F, Math.max(0.15F, top + 0.12F), 0.0F, 0.0F);
                    node.rotateTo(-90.0F, 0.0F, turned * Mth.RAD_TO_DEG * 0.5F, 0.0F);
                    node.scaleTo(PIECE / ITEM_PIXELS * (1.0F - 0.55F * soft), 0.0F);
                    node.setTint(KitchenLooks.mix(0xFFFFFFFF, 0xFF000000 | shownColor, 0.6F * soft));
                } else {
                    float angle = turned + index * Mth.PI;
                    node.moveTo(Mth.cos(angle) * WAITING_RADIUS, top + 0.15F + 0.1F * Mth.sin(time * 2.0F + index), Mth.sin(angle) * WAITING_RADIUS, 0.0F);
                    node.rotateTo(-90.0F, 0.0F, 40.0F * index, 0.0F);
                    node.scaleTo(PIECE / ITEM_PIXELS * 0.85F, 0.0F);
                    node.setTint(0xFFFFFFFF);
                }
            });
        }
        if (!Float.isNaN(arriving)) {
            arriving += dt;
            float k = Math.min(1.0F, arriving / ARRIVE_TIME);
            Vector3f to = new Vector3f(MIDDLE, surface() + 0.2F, MIDDLE);
            Vector3f now = new Vector3f(arriveFrom).lerp(to, KitchenMotions.smooth(k)).add(0.0F, ARRIVE_ARC * Mth.sin(Mth.PI * k), 0.0F);
            view.node("flying").ifPresent(node -> {
                if (item != null) {
                    node.setVisual(Visual.ofItem(item).withAnchor(ICON_MIDDLE));
                }
                node.scaleTo(PIECE / ITEM_PIXELS, 0.0F);
                node.moveTo(now.x, now.y, now.z, 0.0F);
                node.rotateTo(-90.0F, 0.0F, 0.0F, 0.0F);
                node.setVisible(k < 1.0F && item != null);
            });
            if (k >= 1.0F) {
                arriving = Float.NaN;
                view.node("melt").ifPresent(melt -> melt.ripple(MIDDLE, MIDDLE, 0.4F));
                view.playSound(level > 0.02F ? SoundEvents.HONEY_BLOCK_STEP : SoundEvents.COPPER_STEP, "input", 0.3F, 1.3F);
            }
        }
    }

    /**
     * What the piece melting now lets out pools under it: a blot in the colour of what it melts into spreads from under the piece as
     * it melts, on the floor of the empty pot or on what has melted before, a shade darker and glossy as fresh melt is. When the
     * piece is gone the blot runs into the liquid that has risen and fades.
     */
    private void blot(StationView view, float progress) {
        Optional<NodeView> blot = view.node("blot");
        Optional<NodeView> sheen = view.node("blot_sheen");
        if (blot.isEmpty() || sheen.isEmpty()) {
            return;
        }
        ItemStack input = view.state().getItem(MeltingPotStation.INPUT_SLOT);
        Optional<KitchenMeltingPot.Melting> melting = input.isEmpty() || view.level() == null ? Optional.empty()
                : KitchenMeltingPot.melting(KitchenMeltingPot.recipes(view.level()), input);
        float since = time - doneAt;
        float fade = since >= 0.0F && since < BLOT_FADE ? 1.0F - since / BLOT_FADE : 0.0F;
        if (fade <= 0.0F) {
            blotLeft = 0.0F;
        }
        float soft = progress > 0.0F && melting.isPresent() ? KitchenMotions.smooth(progress) : 0.0F;
        float radius;
        float alpha;
        if (blotLeft > 0.0F && fade > 0.0F) {
            // The last piece's pool runs into what has risen.
            radius = blotLeft + 0.4F * (1.0F - fade);
            alpha = fade;
        } else if (soft > 0.0F) {
            radius = BLOT_FROM + (BLOT_TO - BLOT_FROM) * Mth.sqrt(soft);
            alpha = Math.min(1.0F, soft * 6.0F);
        } else {
            radius = 0.0F;
            alpha = 0.0F;
        }
        if (melting.isPresent() && melting.get().fluid() != blotOf) {
            blotOf = melting.get().fluid();
            blotColor = color(view, blotOf);
        }
        boolean on = radius > 0.0F && alpha > 0.0F && blotOf != null && (view.isBlockHidden() || view.isInUse());
        blot.get().setVisible(on);
        sheen.get().setVisible(on);
        if (!on) {
            return;
        }
        float stepped = Math.round(radius / (BLOT_CELL * 0.5F)) * BLOT_CELL * 0.5F;
        if (stepped != blotDrawn) {
            blotDrawn = stepped;
            blot.get().setShape(blotShape(stepped, false), BLOT_TEXTURE);
            sheen.get().setShape(blotShape(stepped, true), BLOT_TEXTURE);
        }
        float y = surface() - FLOOR + BLOT_LIFT;
        int body = KitchenLooks.mix(0xFF000000 | blotColor, 0xFF000000, level > 0.02F ? 0.22F : 0.08F) & 0xFFFFFF;
        blot.get().moveTo(0.0F, y, 0.0F, 0.0F);
        blot.get().setTint(Math.round(235.0F * alpha) << 24 | body);
        sheen.get().moveTo(0.0F, y + 0.01F, 0.0F, 0.0F);
        int light = FoodColors.lighter(blotColor & 0xFFFFFF, 0.45F) & 0xFFFFFF;
        sheen.get().setTint(Math.round(150.0F * alpha) << 24 | light);
    }

    /**
     * The blot's cells in the node's pixels: a round pool of {@code radius} with a wavy edge (lobes where the melt ran further), or,
     * {@code sheen}, the narrow crescent of light along its upper left edge.
     */
    private static MeshShape blotShape(float radius, boolean sheen) {
        MeshShape.Builder shape = MeshShape.builder();
        int cells = Mth.ceil(radius * 1.3F / BLOT_CELL);
        for (int i = -cells; i < cells; i++) {
            for (int j = -cells; j < cells; j++) {
                float x = (i + 0.5F) * BLOT_CELL;
                float z = (j + 0.5F) * BLOT_CELL;
                float edge = blotEdge(radius, (float) Math.atan2(z, x));
                float out = Mth.sqrt(x * x + z * z);
                boolean in = out <= edge;
                if (sheen) {
                    // Light from the top left: a rim one cell wide inside the edge, on the side towards it.
                    in = in && out > edge - BLOT_CELL * 1.5F && x + z < -edge * 0.55F && radius > 1.6F;
                }
                if (in) {
                    float left = i * BLOT_CELL;
                    float right = left + BLOT_CELL;
                    float top = j * BLOT_CELL;
                    float bottom = top + BLOT_CELL;
                    shape.corner(left, 0.0F, top, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F)
                            .corner(left, 0.0F, bottom, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F)
                            .corner(right, 0.0F, bottom, 1.0F, 1.0F, 0.0F, 1.0F, 0.0F)
                            .corner(right, 0.0F, top, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F);
                }
            }
        }
        return shape.build();
    }

    /** How far the melt has run out in direction {@code angle}: a circle with a few lobes, the same every time. */
    private static float blotEdge(float radius, float angle) {
        return radius * (1.0F + 0.16F * Mth.sin(3.0F * angle + 0.7F) + 0.09F * Mth.sin(5.0F * angle + 2.1F)) / 1.05F;
    }

    /** Over heat: steam off the water of the lower pot round the upper one, and now and then a slow bubble in what has melted. */
    private void effects(StationView view, float dt, boolean heated) {
        if (!heated) {
            return;
        }
        RandomSource random = view.level().random;
        steam += dt * STEAM_RATE;
        if (steam >= 1.0F) {
            steam -= 1.0F;
            int side = random.nextInt(4);
            float along = 3.0F + random.nextFloat() * 10.0F;
            float x = side == 0 ? 2.5F : side == 1 ? 13.5F : along;
            float z = side == 2 ? 2.5F : side == 3 ? 13.5F : along;
            view.spawnParticles(DiegesisParticles.STEAM, new Vector3f(x, 8.3F, z), new Vector3f(0.0F, 3.0F, 0.0F), 0.4F, 1, STEAM_COLOR);
        }
        if (level > 0.02F && (view.isBlockHidden() || view.isInUse())) {
            bubbles += dt * BUBBLE_RATE;
            if (bubbles >= 1.0F) {
                bubbles -= 1.0F;
                view.spawnAtLiquidEdge(DiegesisParticles.BUBBLE_POP, "melt", 1, BUBBLE_COLOR, 0.5F);
            }
        }
    }

    /** The badge while something melts or waits: what it melts into, how far, the seconds left, and a flame lit while it is heated. */
    private void badge(StationView view, boolean cookHere, boolean heated, float progress) {
        ItemStack input = view.state().getItem(MeltingPotStation.INPUT_SLOT);
        Optional<KitchenMeltingPot.Melting> melting = view.level() == null ? Optional.empty()
                : KitchenMeltingPot.melting(KitchenMeltingPot.recipes(view.level()), input);
        KitchenMeltingPot pot = pot(view);
        ItemStack making = melting.isEmpty() || pot == null ? ItemStack.EMPTY : pot.handson$containerOf(melting.get().fluid());
        // Nothing melting, what has melted waits to be drawn: its bottle and how many bottles are left in the pot. A bottle takes
        // 250 mB of up to 6000, too little to see the level drop, so the count is what tells the cook it was drawn.
        int bottles = making.isEmpty() ? bottlesLeft(view, pot) : 0;
        boolean waiting = bottles > 0;
        ItemStack dish = waiting ? pot.handson$containerOf(fluid(view)) : making;
        boolean shown = cookHere && !dish.isEmpty();
        view.node("badge").ifPresent(plate -> plate.setVisible(shown));
        int left = view.state().data().getInt(MeltingPotStation.LEFT);
        int seconds = !waiting && progress > 0.0F && left > 0 ? Math.max(1, Mth.ceil(left / 20.0F)) : 0;
        int bar = waiting ? 0 : Math.round(progress * BAR_LENGTH);
        String text = waiting ? "x" + Math.min(bottles, 99) : seconds >= 60 ? seconds / 60 + ":" + String.format("%02d", seconds % 60)
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
            int width = -1;
            for (char c : text.toCharArray()) {
                width += (c == ':' ? 1 : 3) + 1;
            }
            int x = -width / 2 - width % 2;
            for (char c : text.toCharArray()) {
                String[] glyph = c == ':' ? COLON : c == 'x' ? TIMES : PotBehavior.DIGITS[c - '0'];
                glyph(lit, glyph, x, TEXT_TOP);
                x += glyph[0].length() + 1;
            }
            view.node("dial").ifPresent(node -> node.setShape(bar > 0 || !text.isEmpty() ? lit.build() : null, BADGE_TEXTURE, true));
        }
        view.node("dial").ifPresent(node -> node.setTint(heated ? DIAL_COLOR : WAITING_COLOR));
        view.node("dial_track").ifPresent(node -> node.setVisible(shown && !waiting));
        view.node("dial").ifPresent(node -> node.setVisible(shown));
        // The flame: lit while the pot is heated; grey and blinking while what is in it waits for heat.
        view.node("flame").ifPresent(node -> {
            node.setTint(heated ? FLAME_LIT : FLAME_OUT);
            node.setVisible(shown && !waiting && (heated || Mth.sin(time * 6.0F) > 0.0F));
        });
        view.node("preview").ifPresent(node -> {
            node.setVisible(shown);
            if (!shown) {
                return;
            }
            if (shownIcon != dish.getItem()) {
                shownIcon = dish.getItem();
                node.setVisual(Visual.ofItem(shownIcon).withAnchor(ICON_MIDDLE));
            }
            node.setTooltip(seconds > 0 ? Component.translatable("handson_delight.pot.cooking", dish.getHoverName(), seconds)
                    : waiting ? Component.translatable("handson_delight.melting_pot.left", dish.getHoverName(), bottles) : dish.getHoverName());
        });
    }

    /** How many bottles of what has melted are left in the pot: read from the pot itself, else from the scene's tank. */
    private static int bottlesLeft(StationView view, KitchenMeltingPot pot) {
        if (pot == null || fluid(view) == Fluids.EMPTY || pot.handson$containerOf(fluid(view)).isEmpty()) {
            return 0;
        }
        int amount = pot.handson$amount() > 0 ? pot.handson$amount()
                : view.state().getTank(MeltingPotStation.MELT).amount() * KitchenMeltingPot.TANK / 100;
        return amount / 250;
    }

    private static String nameOf(Object value) {
        return value instanceof net.minecraft.util.StringRepresentable named ? named.getSerializedName()
                : value.toString().toLowerCase(java.util.Locale.ROOT);
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
}
