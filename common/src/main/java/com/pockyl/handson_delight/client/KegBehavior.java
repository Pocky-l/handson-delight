package com.pockyl.handson_delight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
import com.pockyl.handson_delight.station.KegLogic;
import com.pockyl.handson_delight.station.KegStation;
import com.pockyl.handson_delight.station.KitchenKeg;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * What the keg shows while somebody works at it: the lid swung open, the liquid at its level and colour with what ferments floating
 * on it, the tap in front. While nobody does, the keg is the block itself, shut: the station hides the block only while it is used.
 */
public final class KegBehavior implements StationBehavior {
    /**
     * The inside of a keg, in pixels: where the liquid starts and how high it stands when full, under the lid; where the tap's hole is
     * in the scene, before the keg is turned to its facing.
     */
    public record Shape(float floor, float top, Vector3f tap) {
        /** The lying keg: a hatch between its hoops, the tap low in the middle of its front. */
        public static final Shape LYING = new Shape(3.0F, 14.5F, new Vector3f(8.0F, 5.5F, 0.0F));
        /** The standing keg: the lid in its head, the tap near the bottom of its front. */
        public static final Shape STANDING = new Shape(2.0F, 14.5F, new Vector3f(8.0F, 2.5F, 1.0F));
    }

    private static final String[] ITEM_NODES = {"item_0", "item_1", "item_2", "item_3"};
    /** The badge over the keg: its plate, the bar and the thermometer. */
    private static final String[] BADGE_PARTS = {"badge", "dial_track", "dial", "thermo", "thermo_now"};
    private static final String[] SHOWN_IN_USE = {"keg", "lid", "drink", "tap", "mouth", "served", "badge", "dial_track", "dial", "thermo",
            "thermo_now"};
    @SuppressWarnings("removal")
    private static final ResourceLocation BADGE_TEXTURE = new ResourceLocation("minecraft", "block/white_concrete");
    /** How far the lid swings open, degrees, and how fast. */
    private static final float LID_OPEN = -105.0F;
    private static final float LID_RATE = 5.0F;
    /** Where things float, pixels from the middle; how they bob. */
    private static final float FLOAT_RADIUS = 1.8F;
    private static final Vector3f RISE = new Vector3f(0.0F, 3.0F, 0.0F);
    private static final Vector3f FALL = new Vector3f(0.0F, -6.0F, 0.0F);
    private static final int WATER_ALPHA = 0xC0;
    private static final int DRINK_ALPHA = 0xF0;
    private static final int PLATE_COLOR = 0x8C1E140C;
    private static final int DIAL_COLOR = 0xFFFFCB6B;
    private static final int TRACK_COLOR = 0x50FFF2DC;
    /** The thermometer's colours, cold to hot, and the dim cells. */
    private static final int[] WARMTH_COLORS = {0xFF6FB8FF, 0xFFB5E3FF, 0xFFFFF4D8, 0xFFFFB45A, 0xFFFF6A3C};
    private static final int BAR_LEFT = -7;
    private static final int BAR_TOP = 10;
    private static final int BAR_LENGTH = 14;
    private static final int CELL_TOP = 6;
    /** A container from the inventory offered under the tap to an empty hand: how see-through, how long it stays away after a drink. */
    private static final int OFFERED = 0x8CFFFFFF;
    private static final float OFFER_AWAY = 0.35F;

    private final Shape shape;
    private final RecipeBook book = new RecipeBook(new KegBook());
    private final UsableCache usable = new UsableCache();
    private final ItemStack[] shown = new ItemStack[ITEM_NODES.length];
    private float time;
    private float lid;
    private float level;
    private float bubbles;
    private float shownTurn = Float.NaN;
    private int drawnBar = -1;
    private int drawnWarmth = -1;
    private int liquidColor;
    private ItemStack offered = ItemStack.EMPTY;
    private Item offeredItem;
    private float tappedAt = -10.0F;

    public KegBehavior(StationView view, Shape shape) {
        this.shape = shape;
        Arrays.fill(shown, ItemStack.EMPTY);
    }

    public static KegBehavior lying(StationView view) {
        return new KegBehavior(view, Shape.LYING);
    }

    public static KegBehavior standing(StationView view) {
        return new KegBehavior(view, Shape.STANDING);
    }

    private KitchenKeg keg(StationView view) {
        return KitchenKeg.of(view.level().getBlockEntity(view.pos()));
    }

    @Override
    public void onSceneReady(StationView view) {
        drawnBar = -1;
        drawnWarmth = -1;
        shownTurn = Float.NaN;
        view.node("dial").ifPresent(dial -> dial.setTint(DIAL_COLOR));
        view.node("dial_track").ifPresent(track -> track.setTint(TRACK_COLOR));
        view.node("thermo").ifPresent(track -> track.setTint(TRACK_COLOR));
        view.node("badge").ifPresent(plate -> {
            plate.setTint(PLATE_COLOR);
            MeshShape.Builder plateShape = MeshShape.builder();
            PotBehavior.pixels(plateShape, -10, -10, 20, 23);
            plate.setShape(plateShape.build(), BADGE_TEXTURE, true);
        });
        view.node("thermo").ifPresent(track -> {
            MeshShape.Builder cells = MeshShape.builder();
            for (int cell = 0; cell < WARMTH_COLORS.length; cell++) {
                PotBehavior.pixels(cells, cell * 3 - 7, CELL_TOP, 2, 2);
            }
            track.setShape(cells.build(), BADGE_TEXTURE, true);
        });
        view.node("book_open").ifPresent(node -> node.setPainter(book));
        refresh(view);
    }

    @Override
    public void onOpen(StationView view) {
        usable.clear();
        view.setInventoryFilter(stack -> usable.test(stack, it -> usable(view, it)));
        book.opened(view);
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

    /** What the inventory at the keg shows: what ferments in it, and what pours into it or is filled from it. */
    private boolean usable(StationView view, ItemStack stack) {
        KitchenKeg keg = keg(view);
        if (keg == null) {
            return false;
        }
        if (keg.handson$isVessel(stack)) {
            return true;
        }
        return keg.handson$recipes().stream().anyMatch(recipe -> recipe.ingredients().stream().anyMatch(ingredient -> ingredient.test(stack)));
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Click click && click.node().equals("book_open")) {
            book.click(view);
        } else if (event instanceof SceneEvent.Click click && click.node().equals("tap") && click.button() == 0 && !offered.isEmpty()) {
            CompoundTag how = new CompoundTag();
            how.putString(KegStation.AT, KegStation.TAP);
            how.putBoolean(KegStation.FROM_INVENTORY, true);
            how.putBoolean(KegStation.ALL, Screen.hasShiftDown());
            view.sendAction(KegStation.ACTION_USE, how);
            tappedAt = time;
        }
    }

    /** A container clicked on the tap fills; a bucket or a drink clicked on the opening pours in; the book takes its clicks. */
    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if ("book_open".equals(node)) {
            book.click(view);
            return true;
        }
        KitchenKeg keg = keg(view);
        if (keg == null) {
            return false;
        }
        boolean tap = "tap".equals(node) && keg.handson$canTap(stack);
        boolean pour = ("mouth".equals(node) || "drink".equals(node)) && keg.handson$canPour(stack);
        if (!tap && !pour) {
            return false;
        }
        CompoundTag how = new CompoundTag();
        how.putString(KegStation.AT, tap ? KegStation.TAP : KegStation.MOUTH);
        how.putBoolean(KegStation.ALL, shift);
        view.sendAction(KegStation.ACTION_USE, how);
        return true;
    }

    @Override
    public void onEffect(StationView view, ResourceLocation effect, String node, CompoundTag data) {
        if (KegStation.EFFECT_POURED.equals(effect)) {
            view.node("drink").ifPresent(drink -> drink.ripple(8.0F, 8.0F, 0.6F));
            view.spawnOnLiquid(DiegesisParticles.SPLASH, "drink", 6, 0xFF000000 | liquidColor, RISE);
            view.playSound(SoundEvents.BUCKET_EMPTY, "drink", 0.7F, 1.0F);
        } else if (KegStation.EFFECT_TAPPED.equals(effect)) {
            Vector3f from = tapPoint(view);
            view.spawnParticles(DiegesisParticles.DROPLET, from, FALL, 0.2F, 8, 0xFF000000 | liquidColor);
            view.playSound(SoundEvents.BOTTLE_FILL, "tap", 0.8F, 0.9F);
            view.node("drink").ifPresent(drink -> drink.ripple(8.0F, 8.0F, 0.2F));
        }
    }

    /** The tap's hole in the scene, turned with the keg. */
    private Vector3f tapPoint(StationView view) {
        return view.node("keg").map(keg -> keg.sceneMatrix().transformPosition(new Vector3f(shape.tap()).sub(8.0F, 0.0F, 8.0F)))
                .orElse(new Vector3f(shape.tap()));
    }

    private void refresh(StationView view) {
        StationState state = view.state();
        ItemStack[] items = state.getItems(KegStation.INGREDIENTS);
        for (int i = 0; i < ITEM_NODES.length && i < items.length; i++) {
            if (!items[i].isEmpty() && shown[i].isEmpty() && view.isFocused()) {
                view.spawnParticles(DiegesisParticles.SPLASH, ITEM_NODES[i], 4, 0);
                view.playSound(PotSounds.PLOP, ITEM_NODES[i], 0.4F, 0.95F + 0.04F * i);
            }
            shown[i] = items[i].copy();
        }
        colour(view);
        tooltip(view);
    }

    /** The liquid's colour: water as the biome's, anything else as the drink poured from it; fermenting moves it to the result's. */
    private void colour(StationView view) {
        KitchenKeg keg = keg(view);
        if (keg == null) {
            return;
        }
        Fluid fluid = keg.handson$fluid();
        int base = colourOf(view, keg, fluid);
        float progress = view.state().getControl(KegStation.PROGRESS);
        Optional<KitchenKeg.Fermenting> making = making(view, keg);
        int colour = base;
        if (making.isPresent() && progress > 0.0F) {
            KitchenKeg.Fermenting recipe = making.get();
            int result = recipe.resultFluid() != Fluids.EMPTY ? colourOf(view, keg, recipe.resultFluid())
                    : 0xFF000000 | FoodColors.main(recipe.resultItem());
            colour = blend(base, result, progress);
        }
        liquidColor = colour & 0xFFFFFF;
        int shownColour = colour;
        view.node("drink").ifPresent(drink -> drink.setLiquidColor(shownColour));
    }

    private static int colourOf(StationView view, KitchenKeg keg, Fluid fluid) {
        if (fluid == Fluids.EMPTY) {
            return WATER_ALPHA << 24;
        }
        if (fluid.isSame(Fluids.WATER)) {
            return WATER_ALPHA << 24 | BiomeColors.getAverageWaterColor(view.level(), view.pos()) & 0xFFFFFF;
        }
        ItemStack drink = keg.handson$drinkOf(fluid);
        return DRINK_ALPHA << 24 | (drink.isEmpty() ? 0xB08850 : KitchenLooks.liquidColor(drink) & 0xFFFFFF);
    }

    /** The recipe what lies in the keg makes: its ingredients there, its base the liquid in it. */
    private static Optional<KitchenKeg.Fermenting> making(StationView view, KitchenKeg keg) {
        List<ItemStack> items = Arrays.stream(view.state().getItems(KegStation.INGREDIENTS)).filter(stack -> !stack.isEmpty()).toList();
        if (items.isEmpty()) {
            return Optional.empty();
        }
        Fluid fluid = keg.handson$fluid();
        return keg.handson$recipes().stream().filter(recipe -> recipe.base().isSame(fluid) || recipe.base() == Fluids.EMPTY && fluid == Fluids.EMPTY)
                .filter(recipe -> matches(recipe, items)).findFirst();
    }

    private static boolean matches(KitchenKeg.Fermenting recipe, List<ItemStack> items) {
        List<net.minecraft.world.item.crafting.Ingredient> wanted = recipe.ingredients().stream().filter(ingredient -> !ingredient.isEmpty()).toList();
        if (wanted.size() != items.size()) {
            return false;
        }
        boolean[] used = new boolean[items.size()];
        for (var ingredient : wanted) {
            boolean found = false;
            for (int i = 0; i < items.size() && !found; i++) {
                if (!used[i] && ingredient.test(items.get(i))) {
                    used[i] = true;
                    found = true;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private void tooltip(StationView view) {
        KitchenKeg keg = keg(view);
        view.node("drink").ifPresent(node -> {
            if (keg == null || keg.handson$fluid() == Fluids.EMPTY) {
                node.setTooltip(Component.empty());
                return;
            }
            ItemStack drink = keg.handson$drinkOf(keg.handson$fluid());
            Component name = drink.isEmpty() ? Component.literal(BuiltInRegistries.FLUID.getKey(keg.handson$fluid()).getPath())
                    : drink.getHoverName();
            int left = view.state().data().getInt(KegStation.LEFT);
            node.setTooltip(left > 0 && view.state().getControl(KegStation.PROGRESS) > 0.0F
                    ? Component.translatable("handson_delight.keg.fermenting", name, Math.max(1, Math.round(left / 1200.0F)))
                    : Component.translatable("handson_delight.keg.holds", name, keg.handson$amount()));
        });
    }

    private static int blend(int from, int to, float amount) {
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            result |= Math.round(Mth.lerp(amount, from >>> shift & 0xFF, to >>> shift & 0xFF)) << shift;
        }
        return result;
    }

    @Override
    public void frame(StationView view, float dt) {
        time += dt;
        boolean inUse = view.isInUse();
        for (String id : SHOWN_IN_USE) {
            view.node(id).ifPresent(node -> node.setVisible(inUse));
        }
        turnToBlock(view);
        lid = Mth.lerp(Math.min(1.0F, dt * LID_RATE), lid, inUse ? 1.0F : 0.0F);
        float angle = LID_OPEN * lid;
        view.node("lid").ifPresent(node -> node.rotateTo(angle, 0.0F, 0.0F, 0.0F));
        level = Mth.lerp(Math.min(1.0F, dt * 3.0F), level, view.state().getTank(KegStation.DRINK).amount() / 100.0F);
        float surface = shape.floor() + (shape.top() - shape.floor()) * level;
        floaters(view, inUse && level > 0.02F, surface);
        view.node("served").ifPresent(node -> node.moveTo(0.0F, surface + 0.25F, 0.0F, 0.0F));
        book.frame(view, dt);
        boolean fermenting = view.state().getControl(KegStation.PROGRESS) > 0.0F;
        view.node("drink").ifPresent(drink -> {
            drink.setAgitation(fermenting ? 0.04F : 0.01F);
            drink.setEdgeBoil(fermenting ? 0.35F : 0.0F);
        });
        bubbles += dt * (inUse && fermenting ? 1.5F : 0.0F);
        if (bubbles >= 1.0F) {
            view.spawnInLiquid(DiegesisParticles.BUBBLE, "drink", (int) bubbles, 0xE0FFFFFF, RISE);
            bubbles -= (int) bubbles;
        }
        offerContainer(view, inUse);
        // The badge comes with the camera as the book does, growing as it flies in and going as it flies back.
        float appear = RecipeBook.appear(view);
        badge(view, inUse && RecipeBook.isWorkedAt(view) && appear > RecipeBook.SPECK);
        for (String part : BADGE_PARTS) {
            view.node(part).ifPresent(node -> node.scaleTo(appear, 0.0F));
        }
    }

    /** What lies in the keg floats on the liquid, a little apart, bobbing; with no liquid it lies on the floor. */
    private void floaters(StationView view, boolean afloat, float surface) {
        int present = 0;
        for (ItemStack stack : shown) {
            present += stack.isEmpty() ? 0 : 1;
        }
        int rank = 0;
        for (int i = 0; i < ITEM_NODES.length; i++) {
            int index = i;
            boolean there = !shown[i].isEmpty() && view.isInUse();
            float place = rank * Mth.TWO_PI / Math.max(1, present) + time * (afloat ? 0.15F : 0.0F);
            if (!shown[i].isEmpty()) {
                rank++;
            }
            float bob = afloat ? Mth.sin(time * 0.9F + index * 1.3F) * 0.07F : 0.0F;
            float y = (afloat ? surface : shape.floor()) + 0.25F + bob;
            view.node(ITEM_NODES[i]).ifPresent(node -> {
                node.setEnabled(there);
                node.moveTo(Mth.cos(place) * FLOAT_RADIUS, y, Mth.sin(place) * FLOAT_RADIUS, 0.0F);
                node.rotateTo(Mth.sin(time * 0.5F + index) * 3.0F, -place * Mth.RAD_TO_DEG, 0.0F, 0.0F);
            });
        }
    }

    /** The keg's parts stand as the block faces, whichever way the scene turned to the player. */
    private void turnToBlock(StationView view) {
        BlockState state = view.level().getBlockState(view.pos());
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return;
        }
        Direction block = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        float turn = view.facing().toYRot() - block.toYRot();
        if (turn != shownTurn) {
            shownTurn = turn;
            view.node("keg").ifPresent(node -> node.rotateTo(0.0F, turn, 0.0F, 0.0F));
        }
    }

    /** With an empty hand at the tap, a container from the inventory hangs see-through under it: a click fills it. */
    private void offerContainer(StationView view, boolean inUse) {
        offered = ItemStack.EMPTY;
        NodeView node = view.node("offer").orElse(null);
        LocalPlayer player = Minecraft.getInstance().player;
        KitchenKeg keg = keg(view);
        boolean hovered = view.node("tap").map(NodeView::isHovered).orElse(false);
        if (node == null || player == null || keg == null || !inUse || !view.isFocused() || !hovered
                || !player.inventoryMenu.getCarried().isEmpty() || time - tappedAt < OFFER_AWAY) {
            if (node != null) {
                node.setVisible(false);
            }
            return;
        }
        int slot = KegLogic.containerSlot(keg, player.getInventory());
        node.setVisible(slot >= 0);
        if (slot < 0) {
            return;
        }
        offered = player.getInventory().getItem(slot);
        if (offeredItem != offered.getItem()) {
            offeredItem = offered.getItem();
            node.setVisual(Visual.ofItem(offeredItem).withAnchor(new Vector3f(8.0F, 8.0F, 8.0F)));
            node.setTint(OFFERED);
        }
        Vector3f under = tapPoint(view);
        node.moveTo(under.x - 8.0F, under.y - 3.0F, under.z - 8.0F, 0.0F);
    }

    /** A bar for how far it has fermented and five cells for how warm the keg is; the one the recipe wants lit too when it differs. */
    private void badge(StationView view, boolean shown) {
        view.node("badge").ifPresent(plate -> plate.setVisible(shown));
        float progress = view.state().getControl(KegStation.PROGRESS);
        int bar = Math.round(progress * BAR_LENGTH);
        if (bar != drawnBar) {
            drawnBar = bar;
            MeshShape.Builder track = MeshShape.builder();
            PotBehavior.pixels(track, BAR_LEFT, BAR_TOP, BAR_LENGTH, 1);
            view.node("dial_track").ifPresent(node -> node.setShape(track.build(), BADGE_TEXTURE, true));
            MeshShape.Builder lit = MeshShape.builder();
            if (bar > 0) {
                PotBehavior.pixels(lit, BAR_LEFT, BAR_TOP, bar, 1);
            }
            view.node("dial").ifPresent(node -> node.setShape(bar > 0 ? lit.build() : null, BADGE_TEXTURE, true));
        }
        int now = Mth.clamp(Math.round(view.state().getControl(KegStation.TEMPERATURE)), 1, 5);
        int wanted = Math.round(view.state().getControl(KegStation.WANTED));
        int key = now * 10 + wanted;
        if (key != drawnWarmth) {
            drawnWarmth = key;
            MeshShape.Builder cell = MeshShape.builder();
            PotBehavior.pixels(cell, (now - 1) * 3 - 7, CELL_TOP - 1, 2, 4);
            view.node("thermo_now").ifPresent(node -> {
                node.setShape(cell.build(), BADGE_TEXTURE, true);
                node.setTint(WARMTH_COLORS[now - 1]);
            });
        }
        view.node("dial_track").ifPresent(node -> node.setVisible(shown));
        view.node("dial").ifPresent(node -> node.setVisible(shown));
        view.node("thermo").ifPresent(node -> node.setVisible(shown));
        // The keg's own cell blinks while it is too cold or too warm for what is in it.
        boolean fits = wanted == 0 || fitsWarmth(now, wanted);
        view.node("thermo_now").ifPresent(node -> node.setVisible(shown && (fits || Mth.sin(time * 6.0F) > 0.0F)));
    }

    /** Brewin' and Chewin's own rule of which temperatures a recipe takes. */
    static boolean fitsWarmth(int keg, int wanted) {
        return switch (wanted) {
            case 1 -> keg <= 1;
            case 2 -> keg <= 2;
            case 3 -> keg > 1 && keg < 5;
            case 4 -> keg >= 4;
            case 5 -> keg >= 5;
            default -> true;
        };
    }
}
