package com.pockyl.handson_delight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.NodePainter;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_delight.HandsOnConfig;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.station.PotLogic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The pot's recipes in a book hanging in the air beside it: a green cookbook ({@link RecipeBookModel}), opened out, with the recipes on
 * its pages, so the player reads and stirs at once. The page on the left lists the dishes, what can be cooked now first; the page on the
 * right shows the dish under the cursor or the one picked. Pages are measured in units of about a GUI pixel.
 */
final class RecipeBook implements NodePainter {
    /** The page's paper, a unit to a texel, and the ribbon beside it. */
    private static final ResourceLocation PAPER = HandsOnDelight.id("textures/entity/recipe_book_paper.png");
    @SuppressWarnings("removal")
    private static final ResourceLocation ARROWS = new ResourceLocation("textures/gui/book.png");
    @SuppressWarnings("removal")
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "block/white_concrete");
    private static final Quaternionf LYING = new Matrix3f(0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F).getNormalizedRotation(new Quaternionf());
    /** How big against the model's own pixels. */
    private static final float SIZE = 0.72F;
    /** The paper covers the model's blocks of pages exactly; the covers frame it with a pixel all round. */
    private static final float PAGE_WIDTH = 6.0F * SIZE;
    private static final float PAGE_HEIGHT = 8.0F * SIZE;
    private static final int UNITS_WIDE = 72;
    private static final int UNITS_HIGH = 96;
    private static final float UNIT = PAGE_WIDTH / UNITS_WIDE;
    /** The pages' surface over the node's plane, in pixels. */
    static final float SURFACE = 1.09F * SIZE;
    /** How far the model opens, in its own measure, and the angle that leaves between a page and flat. */
    private static final float OPEN = 1.17F;
    /** The paper over the model's own pages, in the model's pixels; a sheet being turned over the pages under it, in pixels. */
    private static final float PAGE_GAP = 0.09F;
    private static final float SHEET_LIFT = 0.13F;
    private static final float OPEN_TIME = 0.4F;
    /** How far the camera has come when the book starts to grow, flying in (and is gone, flying back). */
    private static final float APPEAR_FROM = 0.25F;
    /** Smaller than this the book is a speck, not a book: it is not drawn. */
    static final float SPECK = 0.08F;
    private static final float FLIP_TIME = 0.4F;
    /** Over the paper, in units: wells, then what lies on them, then what covers that. */
    private static final float WELL_LIFT = 0.3F;
    private static final float ITEM_LIFT = 1.2F;
    /** How thick items are on the page: flat, with their faces well apart, or the two would flicker through each other. */
    private static final float ITEM_DEPTH = 8.0F;
    private static final float BLOCK_DEPTH = 0.5F;
    private static final float COVER_LIFT = 1.9F;
    private static final int COLUMNS = 3;
    private static final int ROWS = 4;
    private static final int PITCH = 18;
    private static final int WELL_SIZE = 17;
    private static final int PER_PAGE = COLUMNS * ROWS;
    private static final int GRID_X = 10;
    private static final int GRID_Y = 4;
    private static final int ARROW_Y = 79;
    private static final int ARROW_WIDTH = 23;
    private static final int ARROW_HEIGHT = 13;
    private static final int INK = 0xFF4A3418;
    private static final int WELL = 0x1E5A3C18;
    private static final int WELL_READY = 0x70E0A83C;
    private static final int WELL_PICKED = 0xFF8A5A2B;
    private static final int FADED = 0x55FFFFFF;
    private static final int RULE = 0x405A3C18;
    /** Stars on a page: open, the part of the next one already earned, still shut. */
    private static final int STAR_OPEN = 0xFFD08A1E;
    private static final int STAR_EARNING = 0x90D08A1E;
    private static final int STAR_SHUT = 0x305A3C18;
    /** The paper texture: its size, the page in its corner (the fold on the left), the ribbon and the lit ribbon beside it. */
    private static final float PAPER_SIZE = 128.0F;
    private static final float PAPER_U1 = UNITS_WIDE / PAPER_SIZE;
    private static final float PAPER_V1 = UNITS_HIGH / PAPER_SIZE;
    private static final float RIBBON_U = 80.0F;
    private static final float RIBBON_LIT_U = 88.0F;
    /** Where the tabs take their paper from: clear of the page's darker edge. */
    private static final float TAB_PAPER_U = 8.0F;
    private static final float TAB_PAPER_V = 8.0F;
    private static final float REFRESH_TIME = 1.0F;
    /** How small it is closed. */
    private static final float CLOSED_SIZE = 0.62F;
    /** Closed, it shows its cover: the model turned about the spine, the spine moved to the left of the middle. */
    private static final float CLOSED_TURN = Mth.HALF_PI;
    private static final float CLOSED_SHIFT = 3.0F / 16.0F;
    /** The tabs over the list: where the row starts, each one's width and spacing, height (the others lower), icon, edge. */
    private static final float TABS_X = 10.0F;
    private static final float TAB_WIDE = 16.0F;
    private static final float TAB_PITCH = 18.0F;
    private static final float TAB_HIGH = 14.0F;
    private static final float TAB_DROP = 3.0F;
    private static final float TAB_ICON = 11.0F;
    private static final int TAB_EDGE = 0x905A3C18;
    /** The least room kept between the tabs and the page's edges once the row has to move over to fit. */
    private static final float TAB_MARGIN = 2.0F;
    /** The ribbon that closes the book, at the top of the right page: a bookmark coming out from under the head of the pages. */
    private static final int RIBBON_X = 62;
    private static final int RIBBON_WIDTH = 6;
    private static final int RIBBON_HEIGHT = 13;
    /** The shade in the fold of the pages. */
    private static final int FOLD = 0x285A3C18;
    private static final int FOLD_WIDTH = 5;
    /** Small things at the foot of a page stand this far apart. */
    private static final float FOOT_PITCH = 17.0F;
    /** The least light the book is drawn in, block and sky: a torch-lit kitchen. */
    private static final int LIT = 12;
    /** How long a book handed over waits in the air for the next station to take it, seconds (the server lets the player in). */
    private static final float HANDOFF_WAIT = 1.0F;

    private RecipeBookModel model;
    private List<Entry> entries = List.of();
    private boolean wanted;
    private float open;
    private float flip = 1.0F;
    private int flipWay = 1;
    private int page;
    private Entry picked;
    private Entry hovered;
    /** The tabs over the list (none for a book of one kind), the kind shown (null: all), the tab under the cursor. */
    private List<Tab> tabs = List.of();
    private String tab;
    private boolean tabRemembered;
    private Tab hoveredTab;
    private float refresh;
    private float reach;
    private float time;
    /** The light the book is drawn in: where it hangs, never darker than a lit kitchen, so it stays readable. */
    private int shade = LightTexture.FULL_BRIGHT;
    /** The last time the player's statistics were asked for, milliseconds. */
    private long statsAsked;
    /** The dish's name broken into lines, for the entry it was broken for. */
    private Entry nameFor;
    private List<FormattedCharSequence> nameLines = List.of();
    /** Where the cursor is on the left page (units), or on the right one past its width; negative: not on the book. */
    private float cursorX = -1.0F;
    private float cursorY = -1.0F;
    private StationView station;
    /** The player closed the book by its ribbon: it stays shut at the next station until they open it again. */
    private static boolean putAway;
    /** The book in the air between two stations, while the camera flies over, so it does not vanish and open again. */
    private static Handoff handoff;
    /** The one this book handed over and still draws at its own place until the next station takes it. */
    private Handoff leaving;
    /** Where the book came from, this station's pixels from its own place, while it flies over with the camera; null at home. */
    private Vector3f arrivingFrom;
    /** How much of {@code arrivingFrom} is still to go, as drawn last; held: the player went on before it got here, it waits in the air. */
    private float arrivingLeft = 1.0F;
    private boolean arrivingHeld;
    private final Source source;

    /** A book going over to the next station: where it hung in the world and how it was, open or put away. */
    private static final class Handoff {
        private final Vec3 where;
        private final float open;
        private final boolean wanted;
        private final float time;
        private final long since = Util.getMillis();
        private boolean taken;

        private Handoff(Vec3 where, float open, boolean wanted, float time) {
            this.where = where;
            this.open = open;
            this.wanted = wanted;
            this.time = time;
        }

        private boolean isStale() {
            return Util.getMillis() - since > HANDOFF_WAIT * 1000.0F;
        }
    }

    RecipeBook(Source source) {
        this.source = source;
    }

    /** What a station's book lists: its recipes, and what lies in the station already, which counts as the player's. */
    interface Source {
        List<Page> pages(StationView view);

        List<ItemStack> inStation(StationView view);

        /** The player picked a page by a click. */
        default void picked(StationView view, Page page) {
        }

        /** The key of the page the station is making now (what lies in it), to open the book there; null for none. */
        default Object current(StationView view) {
            return null;
        }

        /**
         * The kinds of recipe the book can show alone, as tabs over the list, the first one showing them all (kind null). Fewer than
         * two kinds: no tabs.
         */
        default List<Tab> tabs(StationView view) {
            return List.of();
        }

        /** Under which name the chosen tab is remembered on the client: one per station type. */
        default String memory() {
            return "";
        }
    }

    /** A tab over the list: the kind of recipe it shows ({@code Page.kind}; null: all), drawn as {@code icon}. */
    record Tab(String kind, ItemStack icon) {
    }

    /**
     * One recipe: the dish on the list, what goes in, and small things at the foot of its page, with a note after them. {@code key}
     * keeps the page picked while the list is read again; {@code stars}: the pot's stars of the dish are shown.
     */
    record Page(Object key, ItemStack dish, List<Ingredient> ingredients, List<ItemStack> foot, Component note, boolean stars,
            String kind) {
        Page(Object key, ItemStack dish, List<Ingredient> ingredients, List<ItemStack> foot, Component note, boolean stars) {
            this(key, dish, ingredients, foot, note, stars, null);
        }
    }

    /** A page, which of its ingredients the player has, and how many of the dish they have made, which opens its stars. */
    private record Entry(Page page, boolean[] found, int missing, int made) {
        ItemStack dish() {
            return page.dish();
        }

        List<Ingredient> ingredients() {
            return page.ingredients();
        }

        boolean isReady() {
            return missing == 0;
        }
    }

    boolean isOpen() {
        return wanted;
    }

    /** The player came to the pot: the book is open already, unless they put it away. */
    void opened(StationView view) {
        if (!wanted && !putAway && HandsOnConfig.recipeBook()) {
            toggle(view);
        }
    }

    void toggle(StationView view) {
        station = view;
        wanted = !wanted;
        if (wanted) {
            // How much of each dish the player has made is in their statistics, which the server sends when asked.
            var connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                connection.send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS));
            }
            read(view);
            picked = shown(view);
            page = picked == null ? 0 : entries.indexOf(picked) / PER_PAGE;
            turnSound();
        } else {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PUT, 1.0F, 0.5F));
        }
    }

    /**
     * The player left the station. Going over to a neighbour, the book goes with them: it stays as it is until the next station's
     * book takes its place and flies from here to there with the camera. Leaving, it closes and goes away as the camera flies back.
     */
    void shut(StationView view) {
        Optional<NodeView> node = view.node("book_open");
        if (DiegesisClient.goingOver() < 1.0F && node.isPresent() && node.get().isVisible()) {
            passOn(node.get());
            return;
        }
        arrivingFrom = null;
        arrivingHeld = false;
        wanted = false;
    }

    /**
     * How much of the book is there, 0 to 1: it grows where it hangs as the camera flies in, and goes as the camera flies back, so it
     * neither pops up before the station is reached nor vanishes in the first frame of leaving. Going over, it is all there.
     */
    static float appear(StationView view) {
        boolean worked = DiegesisClient.focusedStation().map(focused -> focused.pos().equals(view.pos())).orElse(false);
        if (!worked || DiegesisClient.goingOver() < 1.0F) {
            return 1.0F;
        }
        // Over the last three quarters of the way in, and gone in the first part of the way back: far off it would be a speck.
        float t = Mth.clamp((DiegesisClient.arrival() - APPEAR_FROM) / (1.0F - APPEAR_FROM), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** Hands the book over to the next station from where it is now, also half way here (two quick presses of D). */
    private void passOn(NodeView node) {
        if (arrivingFrom != null && !arrivingHeld) {
            arrivingFrom.mul(arrivingLeft);
            arrivingHeld = true;
        }
        leaving = new Handoff(node.worldPosition(), open, wanted, time);
        handoff = leaving;
    }

    /** The station the player goes over to is this one: its book takes the one in the air, open as it was, and flies it here. */
    private void arrive(StationView view, NodeView node, Handoff passing) {
        handoff = null;
        passing.taken = true;
        station = view;
        arrivingHeld = false;
        wanted = passing.wanted;
        open = passing.open;
        time = passing.time;
        if (wanted) {
            var connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                connection.send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS));
            }
            read(view);
            picked = shown(view);
            page = picked == null ? 0 : entries.indexOf(picked) / PER_PAGE;
            turnSound();
        }
        // The way back to where it hung, in this station's pixels: the scene may face another way than the one left.
        Vector3f home = new Vector3f(node.definition().transform().pos());
        Vec3 origin = view.toWorld(home.x, home.y, home.z);
        Vec3 alongX = view.toWorld(home.x + 1.0F, home.y, home.z).subtract(origin);
        Vec3 alongY = view.toWorld(home.x, home.y + 1.0F, home.z).subtract(origin);
        Vec3 alongZ = view.toWorld(home.x, home.y, home.z + 1.0F).subtract(origin);
        Vec3 away = passing.where.subtract(origin);
        arrivingFrom = new Vector3f((float) (away.dot(alongX) / alongX.lengthSqr()), (float) (away.dot(alongY) / alongY.lengthSqr()),
                (float) (away.dot(alongZ) / alongZ.lengthSqr()));
    }

    static boolean isWorkedAt(StationView view) {
        return DiegesisClient.focusedStation().map(focused -> focused.pos().equals(view.pos())).orElse(false);
    }

    private static void turnSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.6F));
    }

    /** The page of what the station makes now, else the first. */
    private Entry shown(StationView view) {
        Object current = source.current(view);
        return entries.stream().filter(entry -> entry.page().key().equals(current)).findFirst()
                .orElse(entries.isEmpty() ? null : entries.get(0));
    }

    /** What the player has made changed (a serving, a dish): the stars on the page follow once the server tells the new numbers. */
    void statsChanged() {
        long now = Util.getMillis();
        var connection = Minecraft.getInstance().getConnection();
        if (wanted && connection != null && now - statsAsked > 1000L) {
            statsAsked = now;
            connection.send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS));
        }
    }

    private void read(StationView view) {
        List<Tab> offered = source.tabs(view);
        tabs = offered.size() > 2 ? offered : List.of();
        if (!tabRemembered) {
            tabRemembered = true;
            tab = BookTabs.get(source.memory());
        }
        if (tabs.stream().noneMatch(it -> Objects.equals(it.kind(), tab))) {
            tab = null;
        }
        List<ItemStack> pool = pool(view);
        List<Entry> found = new ArrayList<>();
        for (Page page : source.pages(view)) {
            if (tab == null || tab.equals(page.kind())) {
                found.add(entry(page, pool));
            }
        }
        if (cursorX >= 0.0F && !entries.isEmpty()) {
            // Under the cursor the list keeps its order: a dish never moves away from where the player points.
            List<Object> before = entries.stream().map(entry -> entry.page().key()).toList();
            found.sort(Comparator.comparingInt(entry -> {
                int at = before.indexOf(entry.page().key());
                return at < 0 ? Integer.MAX_VALUE : at;
            }));
        } else {
            found.sort(Comparator.comparingInt(Entry::missing).thenComparing(entry -> entry.dish().getHoverName().getString()));
        }
        if (picked != null) {
            Object kept = picked.page().key();
            picked = found.stream().filter(entry -> entry.page().key().equals(kept)).findFirst().orElse(null);
        }
        entries = found;
        page = Math.min(page, pages() - 1);
    }

    /** What the player has to cook with: the inventory, the stack in the hand (a knife taken to cut), and what lies in the station. */
    private List<ItemStack> pool(StationView view) {
        List<ItemStack> pool = new ArrayList<>();
        var player = Minecraft.getInstance().player;
        if (player != null) {
            for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                pool.add(player.getInventory().getItem(i));
            }
            pool.add(player.inventoryMenu.getCarried());
        }
        pool.addAll(source.inStation(view));
        pool.removeIf(ItemStack::isEmpty);
        return pool;
    }

    private static Entry entry(Page page, List<ItemStack> pool) {
        int[] left = pool.stream().mapToInt(ItemStack::getCount).toArray();
        List<Ingredient> ingredients = page.ingredients();
        boolean[] found = new boolean[ingredients.size()];
        int missing = 0;
        for (int i = 0; i < found.length; i++) {
            for (int stack = 0; stack < left.length && !found[i]; stack++) {
                if (left[stack] > 0 && ingredients.get(i).test(pool.get(stack))) {
                    left[stack]--;
                    found[i] = true;
                }
            }
            missing += found[i] ? 0 : 1;
        }
        var player = Minecraft.getInstance().player;
        int made = player == null ? 0 : player.getStats().getValue(Stats.ITEM_CRAFTED.get(page.dish().getItem()));
        return new Entry(page, found, missing, made);
    }

    private int pages() {
        return Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
    }

    /** Every frame: opens or closes, hangs in the air, follows the cursor. */
    void frame(StationView view, float dt) {
        station = view;
        NodeView node = view.node("book_open").orElse(null);
        if (node == null) {
            return;
        }
        if (!HandsOnConfig.recipeBook()) {
            node.setVisible(false);
            return;
        }
        Handoff passing = handoff;
        if (leaving != null) {
            if (leaving.taken || leaving.isStale()) {
                if (handoff == leaving) {
                    handoff = null;
                }
                leaving = null;
                arrivingFrom = null;
                arrivingHeld = false;
                wanted = false;
                open = 0.0F;
            }
        } else if (passing != null && !passing.taken && !passing.isStale() && isWorkedAt(view)) {
            arrive(view, node, passing);
        }
        float flight = DiegesisClient.goingOver();
        if (arrivingFrom != null && !arrivingHeld && leaving == null && flight < 1.0F && !isWorkedAt(view)) {
            // The player went on before the book got here (the station was not theirs yet, so it was not shut).
            passOn(node);
        }
        if (arrivingFrom != null && !arrivingHeld && (flight >= 1.0F || !isWorkedAt(view))) {
            arrivingFrom = null;
        }
        float appear = appear(view);
        node.setVisible((view.isFocused() || leaving != null || arrivingFrom != null || isWorkedAt(view)) && appear > SPECK);
        if (!node.isVisible() && leaving == null) {
            open = 0.0F;
        }
        time += dt;
        open = Mth.clamp(open + (wanted ? dt : -dt) / OPEN_TIME, 0.0F, 1.0F);
        if (wanted) {
            // Coming in, it is open by the time the camera lands.
            open = Math.max(open, appear);
        }
        flip = Math.min(1.0F, flip + dt / FLIP_TIME);
        // Grows and opens where it hangs, breathing a little.
        float eased = open * open * (3.0F - 2.0F * open);
        reach = Mth.lerp(Math.min(1.0F, dt * 12.0F), reach, node.isHovered() && !wanted ? 1.0F : 0.0F);
        node.scaleTo(Mth.lerp(eased, CLOSED_SIZE * (1.0F + 0.08F * reach), 1.0F) * appear, 0.0F);
        float bob = Mth.sin(time * 1.3F) * 0.14F;
        if (arrivingFrom != null) {
            // With the camera, on the same easing: the book keeps its place on the screen while the kitchen moves under it.
            float left = arrivingHeld ? 1.0F : 1.0F - flight;
            arrivingLeft = left;
            node.moveTo(arrivingFrom.x * left, arrivingFrom.y * left + bob, arrivingFrom.z * left, 0.0F);
        } else {
            node.moveTo(0.0F, bob, 0.0F, 0.0F);
        }
        node.rotateTo(Mth.sin(time * 0.9F) * 1.2F, Mth.cos(time * 0.7F) * 1.5F, 0.0F, 0.0F);
        refresh -= dt;
        if (refresh <= 0.0F && wanted) {
            refresh = REFRESH_TIME;
            read(view);
        }
        follow(view, node);
    }

    private void follow(StationView view, NodeView node) {
        Optional<Vector3f> cursor = view.pointerOn("book_open", SURFACE);
        cursorX = -1.0F;
        cursorY = -1.0F;
        hovered = null;
        hoveredTab = null;
        if (cursor.isEmpty() || flip < 1.0F) {
            return;
        }
        Vector3f point = cursor.get();
        float y = (PAGE_HEIGHT / 2.0F - point.z) / UNIT;
        // The left page runs from its edge at +x to the fold at 0; the right one on from there.
        float x = point.x > 0.0F ? (PAGE_WIDTH - point.x) / UNIT : UNITS_WIDE - point.x / UNIT;
        boolean overTabs = !tabs.isEmpty() && x >= 0.0F && x < UNITS_WIDE && y >= -TAB_HIGH && y < 0.0F;
        if (x >= 0.0F && x < UNITS_WIDE * 2 && (y >= 0.0F || overTabs) && y < UNITS_HIGH) {
            cursorX = x;
            cursorY = y;
            hovered = entryAt(x, y);
            hoveredTab = overTabs ? tabAt(x) : null;
        }
    }

    private Entry entryAt(float x, float y) {
        int column = Mth.floor((x - GRID_X) / PITCH);
        int row = Mth.floor((y - GRID_Y) / PITCH);
        int index = page * PER_PAGE + row * COLUMNS + column;
        return column >= 0 && column < COLUMNS && row >= 0 && row < ROWS && index < entries.size() ? entries.get(index) : null;
    }

    private Tab tabAt(float x) {
        float start = tabsX();
        float pitch = tabPitch();
        // A tab drawn later lies over the one before it: each shows from its own left edge to the next one's.
        int index = Math.min(Mth.floor((x - start) / pitch), tabs.size() - 1);
        float into = x - start - index * pitch;
        return index >= 0 && into < TAB_WIDE ? tabs.get(index) : null;
    }

    /** Where the row of tabs starts: its usual place while they fit over the left page, else nearer its edge. */
    private float tabsX() {
        return TABS_X + (tabs.size() - 1) * TAB_PITCH + TAB_WIDE <= UNITS_WIDE - TAB_MARGIN ? TABS_X : TAB_MARGIN;
    }

    /** How far apart the tabs stand: as usual, or closer, overlapping, so that however many there are all stay over the left page. */
    private float tabPitch() {
        if (tabs.size() < 2) {
            return TAB_PITCH;
        }
        return Math.min(TAB_PITCH, (UNITS_WIDE - TAB_MARGIN - tabsX() - TAB_WIDE) / (tabs.size() - 1));
    }

    private boolean onRibbon() {
        return cursorX >= UNITS_WIDE + RIBBON_X && cursorX < UNITS_WIDE + RIBBON_X + RIBBON_WIDTH && cursorY >= 0.0F && cursorY < RIBBON_HEIGHT;
    }

    /** A click on the book: opens it; open it picks a dish, turns the page, or closes by its ribbon. */
    void click(StationView view) {
        if (!wanted || onRibbon()) {
            toggle(view);
            putAway = !wanted;
        } else if (hoveredTab != null) {
            if (!Objects.equals(tab, hoveredTab.kind())) {
                tab = hoveredTab.kind();
                BookTabs.put(source.memory(), tab);
                page = 0;
                flip = 1.0F;
                read(view);
                // The right page shows the first of the kind until another is picked.
                if (picked == null && !entries.isEmpty()) {
                    picked = entries.get(0);
                }
                turnSound();
            }
        } else if (hovered != null) {
            picked = hovered;
            source.picked(view, picked.page());
        } else if (cursorY >= ARROW_Y && cursorY < ARROW_Y + ARROW_HEIGHT) {
            if (cursorX >= GRID_X && cursorX < GRID_X + ARROW_WIDTH) {
                turn(-1);
            } else if (cursorX >= GRID_X + COLUMNS * PITCH - ARROW_WIDTH && cursorX < GRID_X + COLUMNS * PITCH) {
                turn(1);
            }
        }
    }

    private void turn(int by) {
        int next = Mth.clamp(page + by, 0, pages() - 1);
        if (next != page && flip >= 1.0F) {
            page = next;
            flip = 0.0F;
            flipWay = by;
            turnSound();
        }
    }

    /** It hangs before the camera and sways: drawn by the scene, its edges showed steps. */
    @Override
    public boolean smooth() {
        return true;
    }

    @Override
    public void paint(PoseStack poseStack, MultiBufferSource buffers, int light) {
        shade = LightTexture.pack(Math.max(LightTexture.block(light), LIT), Math.max(LightTexture.sky(light), LIT));
        if (model == null) {
            model = RecipeBookModel.bake();
        }
        float eased = open * open * (3.0F - 2.0F * open);
        float closed = 1.0F - eased;
        // How far each cover has turned from shut, as the enchanting table's book turns them.
        float angle = 1.25F * OPEN * eased;
        poseStack.pushPose();
        poseStack.mulPose(LYING);
        poseStack.scale(16.0F * SIZE, 16.0F * SIZE, 16.0F * SIZE);
        poseStack.mulPose(new Quaternionf().rotationY(CLOSED_TURN * closed));
        poseStack.translate(-CLOSED_SHIFT * closed, 0.0F, 0.0F);
        model.setupAnim(angle);
        // Cut out: the brass corners are triangles on square caps.
        model.render(poseStack, buffers.getBuffer(RenderType.entityCutout(RecipeBookModel.TEXTURE)), shade, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        if (open > 0.0F) {
            // The sheets go with the model: the same turn from closed, the same angle of the pages all the way open.
            poseStack.pushPose();
            poseStack.mulPose(new Quaternionf().rotationZ(CLOSED_TURN * closed));
            poseStack.translate(0.0F, -CLOSED_SHIFT * 16.0F * SIZE * closed, 0.0F);
            spread(poseStack, buffers, Mth.HALF_PI - angle, (Mth.sin(angle) + PAGE_GAP) * SIZE);
            poseStack.popPose();
        }
    }

    /**
     * The open pages. Turning, three are on show: a sheet goes over from one side to the other with one page on its face and the next on
     * its back, and the page it uncovers lies ready under it, so nothing appears or goes away.
     */
    private void spread(PoseStack poseStack, MultiBufferSource buffers, float tilt, float height) {
        if (flip >= 1.0F) {
            side(poseStack, buffers, true, tilt, height, page, true);
            side(poseStack, buffers, false, -tilt, height, page, false);
            return;
        }
        float over = flip * flip * (3.0F - 2.0F * flip);
        // Forward the old list stays on the left until the sheet covers it with the new one; back the old list itself lifts off.
        boolean forward = flipWay > 0;
        side(poseStack, buffers, true, tilt, height, forward ? page - 1 : page, true);
        side(poseStack, buffers, false, -tilt, height, page, false);
        float right = -tilt;
        float left = tilt - Mth.PI;
        float turn = forward ? Mth.lerp(over, right, left) : Mth.lerp(over, left, right);
        float lifted = height + SHEET_LIFT;
        if (turn > -Mth.HALF_PI) {
            side(poseStack, buffers, false, turn, lifted, page, false);
        } else {
            side(poseStack, buffers, true, turn + Mth.PI, lifted, forward ? page : page + 1, false);
        }
    }

    /** One side of a sheet, turned about the fold: units run right and down as on a screen. {@code resting}: the tabs stand on it. */
    private void side(PoseStack poseStack, MultiBufferSource buffers, boolean left, float turn, float height, int listPage,
            boolean resting) {
        poseStack.pushPose();
        poseStack.translate(0.0F, height, 0.0F);
        poseStack.mulPose(new Quaternionf().rotationZ(turn));
        Matrix4f sheet = new Matrix4f(-UNIT, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -UNIT, 0.0F, 0.0F, UNIT, 0.0F, 0.0F, left ? PAGE_WIDTH : 0.0F, 0.0F, PAGE_HEIGHT / 2.0F, 1.0F);
        poseStack.last().pose().mul(sheet);
        Sheet canvas = new Sheet(poseStack, buffers);
        canvas.paper(left);
        canvas.rect(left ? UNITS_WIDE - FOLD_WIDTH : 0.0F, 0.0F, FOLD_WIDTH, UNITS_HIGH, 0.1F, FOLD);
        canvas.rect(left ? UNITS_WIDE - 2.0F : 0.0F, 0.0F, 2.0F, UNITS_HIGH, 0.15F, FOLD);
        if (left) {
            if (resting) {
                drawTabs(canvas);
            }
            drawList(canvas, listPage);
        } else {
            Entry shown = hovered != null ? hovered : picked;
            if (shown != null) {
                drawDish(canvas, shown);
            }
        }
        poseStack.popPose();
    }

    private void drawTabs(Sheet canvas) {
        for (int i = 0; i < tabs.size(); i++) {
            Tab shown = tabs.get(i);
            canvas.tab(tabsX() + i * tabPitch(), Objects.equals(shown.kind(), tab), shown == hoveredTab, shown.icon());
        }
    }

    private void drawList(Sheet canvas, int listPage) {
        int first = listPage * PER_PAGE;
        for (int i = first; i < Math.min(entries.size(), first + PER_PAGE); i++) {
            Entry entry = entries.get(i);
            int x = GRID_X + (i - first) % COLUMNS * PITCH;
            int y = GRID_Y + (i - first) / COLUMNS * PITCH;
            canvas.rect(x, y, WELL_SIZE, WELL_SIZE, WELL_LIFT, entry.isReady() ? WELL_READY : WELL);
            canvas.item(entry.dish(), x + 0.5F, y + 0.5F, 16.0F, !entry.isReady());
            if (entry == picked || entry == hovered) {
                canvas.frame(x, y, WELL_SIZE);
            }
        }
        if (pages() > 1) {
            Font font = Minecraft.getInstance().font;
            String number = String.valueOf(listPage + 1);
            canvas.text(number, GRID_X + (COLUMNS * PITCH - font.width(number)) / 2.0F, ARROW_Y + 3.0F);
            if (listPage > 0) {
                canvas.arrow(GRID_X, ARROW_Y, 205);
            }
            if (listPage < pages() - 1) {
                canvas.arrow(GRID_X + COLUMNS * PITCH - ARROW_WIDTH, ARROW_Y, 192);
            }
        }
    }

    /** The dish, its name, its stars, what goes in, how long it takes and what it is served in. */
    private void drawDish(Sheet canvas, Entry entry) {
        Font font = Minecraft.getInstance().font;
        float middle = UNITS_WIDE / 2.0F;
        canvas.ribbon(onRibbon());
        canvas.item(entry.dish(), middle - 11.0F, 2.0F, 22.0F);
        float line = 25.0F;
        // Two lines at most: a long modded name must leave room for what goes in.
        if (nameFor != entry) {
            nameFor = entry;
            nameLines = font.split(entry.dish().getHoverName(), UNITS_WIDE - 6);
        }
        List<FormattedCharSequence> name = nameLines;
        for (FormattedCharSequence text : name.subList(0, Math.min(2, name.size()))) {
            canvas.text(text, middle - font.width(text) / 2.0F, line);
            line += font.lineHeight;
        }
        if (entry.page().stars()) {
            drawStars(canvas, entry, middle, line);
            line += font.lineHeight;
        }
        canvas.rect(10, line, UNITS_WIDE - 20, 0.6F, WELL_LIFT, RULE);
        int count = entry.ingredients().size();
        long second = Util.getMillis() / 1000L;
        float top = line + 3.0F;
        for (int i = 0; i < count; i++) {
            int inRow = Math.min(3, count - i / 3 * 3);
            float x = middle - inRow * PITCH / 2.0F + i % 3 * PITCH;
            float y = top + i / 3 * PITCH;
            ItemStack[] choices = entry.ingredients().get(i).getItems();
            if (choices.length > 0) {
                canvas.rect(x, y, WELL_SIZE, WELL_SIZE, WELL_LIFT, entry.found()[i] ? WELL_READY : WELL);
                canvas.item(choices[(int) (second % choices.length)], x + 0.5F, y + 0.5F, 16.0F, !entry.found()[i]);
            }
        }
        // The small print goes where there is room left.
        float bottom = UNITS_HIGH - 13.0F;
        if (top + (count + 2) / 3 * PITCH <= bottom) {
            float x = 11.0F;
            for (ItemStack small : entry.page().foot()) {
                canvas.item(small, x, bottom, 12.0F);
                x += FOOT_PITCH;
            }
            if (entry.page().note() != null) {
                canvas.text(entry.page().note().getVisualOrderText(), x - 4.0F, bottom + 2.0F);
            }
        }
    }

    /**
     * The stars the player can cook the dish with now and the most they add to a batch; the next star fills as they make more
     * of it.
     */
    private void drawStars(Sheet canvas, Entry entry, float middle, float line) {
        Font font = Minecraft.getInstance().font;
        int open = PotLogic.openStars(entry.made());
        FormattedCharSequence bonus = Component.translatable("handson_delight.book.bonus", PotLogic.bonusPercent(open)).getVisualOrderText();
        int size = PotBehavior.STAR_SIZE;
        float starsWide = 3 * size + 2;
        float left = middle - (starsWide + 3 + font.width(bonus)) / 2.0F;
        for (int star = 0; star < 3; star++) {
            float x = left + star * (size + 1);
            int earning = star == open ? Math.round(PotLogic.nextStar(entry.made()) * size) : 0;
            for (int row = 0; row < size; row++) {
                int color = star < open ? STAR_OPEN : row >= size - earning ? STAR_EARNING : STAR_SHUT;
                for (int column = 0; column < size; column++) {
                    if (PotBehavior.STAR[row].charAt(column) == '#') {
                        canvas.rect(x + column, line + 1 + row, 1, 1, WELL_LIFT, color);
                    }
                }
            }
        }
        canvas.text(bonus, left + starsWide + 3, line);
    }

    /** Drawing on a page. */
    private final class Sheet {
        private final PoseStack poseStack;
        private final MultiBufferSource buffers;
        private final TextureAtlasSprite white;

        Sheet(PoseStack poseStack, MultiBufferSource buffers) {
            this.poseStack = poseStack;
            this.buffers = buffers;
            white = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(WHITE);
        }

        void paper(boolean mirrored) {
            VertexConsumer consumer = buffers.getBuffer(RenderType.text(PAPER));
            quad(consumer, 0.0F, 0.0F, UNITS_WIDE, UNITS_HIGH, 0.0F, 0xFFFFFFFF, mirrored ? PAPER_U1 : 0.0F, mirrored ? 0.0F : PAPER_U1, 0.0F, PAPER_V1);
        }

        void rect(float x, float y, float width, float height, float lift, int argb) {
            VertexConsumer consumer = buffers.getBuffer(RenderType.text(TextureAtlas.LOCATION_BLOCKS));
            quad(consumer, x, y, x + width, y + height, lift, argb, white.getU0(), white.getU1(), white.getV0(), white.getV1());
        }

        /**
         * A paper tab standing over the page's top edge, as vanilla's recipe book has them: the chosen one taller and in the page's own
         * paper, the others a shade darker behind it, each with its kind's icon.
         */
        void tab(float x, boolean chosen, boolean lit, ItemStack icon) {
            float high = chosen ? TAB_HIGH : TAB_HIGH - TAB_DROP;
            int paper = chosen ? 0xFFFFFFFF : lit ? 0xFFEDE4D0 : 0xFFD6C9AC;
            // Down into the page by a unit: the chosen one runs on into it, with no edge between.
            float bottom = chosen ? 1.0F : 0.0F;
            float u0 = TAB_PAPER_U / PAPER_SIZE;
            float v0 = TAB_PAPER_V / PAPER_SIZE;
            quad(buffers.getBuffer(RenderType.text(PAPER)), x, -high, x + TAB_WIDE, bottom, chosen ? 0.05F : 0.0F, paper, u0,
                    u0 + TAB_WIDE / PAPER_SIZE, v0, v0 + (bottom + high) / PAPER_SIZE);
            rect(x + 1.0F, -high, TAB_WIDE - 2.0F, 1.0F, 0.1F, TAB_EDGE);
            rect(x, -high + 1.0F, 1.0F, high - 1.0F, 0.1F, TAB_EDGE);
            rect(x + TAB_WIDE - 1.0F, -high + 1.0F, 1.0F, high - 1.0F, 0.1F, TAB_EDGE);
            item(icon, x + (TAB_WIDE - TAB_ICON) / 2.0F, -high + 2.0F, TAB_ICON, !chosen && !lit);
        }

        /** The ribbon from the paper texture, its forked end frayed; lighter under the cursor. */
        void ribbon(boolean lit) {
            float u = (lit ? RIBBON_LIT_U : RIBBON_U) / PAPER_SIZE;
            quad(buffers.getBuffer(RenderType.text(PAPER)), RIBBON_X, 0.0F, RIBBON_X + RIBBON_WIDTH, RIBBON_HEIGHT, COVER_LIFT, 0xFFFFFFFF, u,
                    u + RIBBON_WIDTH / PAPER_SIZE, 0.0F, RIBBON_HEIGHT / PAPER_SIZE);
        }

        void frame(float x, float y, float size) {
            rect(x, y, size, 1.0F, COVER_LIFT, WELL_PICKED);
            rect(x, y + size - 1.0F, size, 1.0F, COVER_LIFT, WELL_PICKED);
            rect(x, y, 1.0F, size, COVER_LIFT, WELL_PICKED);
            rect(x + size - 1.0F, y, 1.0F, size, COVER_LIFT, WELL_PICKED);
        }

        void arrow(float x, float y, int v) {
            boolean over = cursorX >= x && cursorX < x + ARROW_WIDTH && cursorY >= y && cursorY < y + ARROW_HEIGHT;
            float u = (over ? ARROW_WIDTH : 0.0F) / 256.0F;
            quad(buffers.getBuffer(RenderType.text(ARROWS)), x, y, x + ARROW_WIDTH, y + ARROW_HEIGHT, WELL_LIFT, 0xFFFFFFFF, u, u + ARROW_WIDTH / 256.0F, v / 256.0F,
                    (v + ARROW_HEIGHT) / 256.0F);
        }

        private void quad(VertexConsumer consumer, float x0, float y0, float x1, float y1, float lift, int argb, float u0, float u1, float v0, float v1) {
            Matrix4f matrix = poseStack.last().pose();
            int a = argb >>> 24;
            int r = argb >> 16 & 0xFF;
            int g = argb >> 8 & 0xFF;
            int b = argb & 0xFF;
            consumer.vertex(matrix, x0, y0, lift).color(r, g, b, a).uv(u0, v0).uv2(shade).endVertex();
            consumer.vertex(matrix, x0, y1, lift).color(r, g, b, a).uv(u0, v1).uv2(shade).endVertex();
            consumer.vertex(matrix, x1, y1, lift).color(r, g, b, a).uv(u1, v1).uv2(shade).endVertex();
            consumer.vertex(matrix, x1, y0, lift).color(r, g, b, a).uv(u1, v0).uv2(shade).endVertex();
        }

        void item(ItemStack stack, float x, float y, float size) {
            item(stack, x, y, size, false);
        }

        /** {@code faded}: what the player does not have yet, the icon itself see-through, as nothing laid over it would stay in its place. */
        void item(ItemStack stack, float x, float y, float size, boolean faded) {
            poseStack.pushPose();
            poseStack.translate(x + size / 2.0F, y + size / 2.0F, ITEM_LIFT);
            ItemRenderer renderer = Minecraft.getInstance().getItemRenderer();
            BakedModel model = renderer.getModel(stack, station.level(), null, 0);
            poseStack.scale(size, -size, model.isGui3d() ? BLOCK_DEPTH : ITEM_DEPTH);
            // The icon looks as in a GUI, but is drawn as no GUI item: mods that decorate every GUI item (Shadow Drop's shadows and
            // its shift) take its world pose for a screen's and move or cover the icon.
            BakedModel shown = ClientPlatform.INSTANCE.guiModel(model, poseStack);
            renderer.render(stack, ItemDisplayContext.NONE, false, poseStack, faded ? DiegesisClient.tinted(buffers, FADED) : buffers, shade,
                    OverlayTexture.NO_OVERLAY, shown);
            poseStack.popPose();
        }

        void text(String text, float x, float y) {
            text(Component.literal(text).getVisualOrderText(), x, y);
        }

        void text(FormattedCharSequence text, float x, float y) {
            Matrix4f matrix = new Matrix4f(poseStack.last().pose()).translate(0.0F, 0.0F, ITEM_LIFT);
            Minecraft.getInstance().font.drawInBatch(text, x, y, INK, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, shade);
        }
    }
}
