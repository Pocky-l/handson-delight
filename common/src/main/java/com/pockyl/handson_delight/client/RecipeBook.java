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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.NodePainter;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.scene.Transform;
import com.pockyl.handson_delight.BookMath;
import com.pockyl.handson_delight.Cookable;
import com.pockyl.handson_delight.HandsOnConfig;
import com.pockyl.handson_delight.HandsOnDelight;
import com.pockyl.handson_delight.station.PotLogic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The pot's recipes in a book hanging in the air beside it: a green cookbook ({@link RecipeBookModel}), opened out, with the recipes on
 * its pages, so the player reads and stirs at once. The page on the left lists the dishes, what can be cooked now first; the page on the
 * right shows the dish under the cursor, the one pointed at last, or the one picked. Pages are measured in units of about a GUI pixel.
 * The pages turn by the wheel, the page keys and the list's foot (held, it leafs on); over the right page a switch shows only what
 * can be made now and a magnifier searches; a "made lately" tab keeps the dishes made from the book.
 */
final class RecipeBook implements NodePainter {
    /** The page's paper, a unit to a texel, and the ribbon beside it. */
    private static final ResourceLocation PAPER = HandsOnDelight.id("textures/entity/recipe_book_paper.png");
    /**
     * The flat sprite that wells and bars are tinted from: this mod's own, in the off-white of white concrete that their colours were
     * chosen against, so that a resource pack that retextures concrete does not show through the book.
     */
    private static final ResourceLocation WHITE = HandsOnDelight.id("block/white");
    private static final Quaternionf LYING = new Matrix3f(0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F).getNormalizedRotation(new Quaternionf());
    /** How big against the model's own pixels. */
    private static final float SIZE = 0.72F;
    /** The paper covers the model's blocks of pages exactly; the covers frame it with a pixel all round. */
    private static final float PAGE_WIDTH = 6.0F * SIZE;
    private static final float PAGE_HEIGHT = 8.0F * SIZE;
    private static final int UNITS_WIDE = 72;
    private static final int UNITS_HIGH = 96;
    private static final float UNIT = PAGE_WIDTH / UNITS_WIDE;
    /** How far the model opens, in its own measure, and the angle that leaves between a page and flat. */
    private static final float OPEN = 1.17F;
    /** The paper over the model's own pages, in the model's pixels; a sheet being turned over the pages under it, in pixels. */
    private static final float PAGE_GAP = 0.09F;
    private static final float SHEET_LIFT = 0.13F;
    /** Seconds the covers take to swing open or shut. */
    private static final float OPEN_TIME = 0.31F;
    /** How far the camera has come before the book starts to open: it arrives shut and opens before the player's eyes. */
    private static final float OPEN_FROM = 0.98F;
    /** How far the camera has come when the book starts to grow, flying in (and is gone, flying back). */
    private static final float APPEAR_FROM = 0.25F;
    /** Smaller than this the book is a speck, not a book: it is not drawn. */
    static final float SPECK = 0.08F;
    private static final float FLIP_TIME = 0.4F;
    /** A turn soon after the last one flips quicker, so that quick clicks are never kept waiting; and how soon that is, seconds. */
    private static final float FLIP_QUICK = 0.16F;
    private static final float QUICK_GAP = 0.5F;
    /** The least time between two page sounds, milliseconds: leafing quickly does not rattle. */
    private static final long TURN_SOUND_GAP = 80L;
    /** How far a turning sheet has gone over when the list it uncovers takes the cursor. */
    private static final float FLIP_LIVE = 0.6F;
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
    /**
     * Each page is laid out about the middle of its open part, clear of the shade in the fold: the left page's fold is on its right,
     * the right page's on its left. The list's grid sits in the middle of the left page, the arrows under its outer columns, and
     * the foot of the right page on the same line as the arrows.
     */
    /** The grid, 53 units wide, about the middle of the left page's open part, (72 - 5) / 2. */
    private static final int GRID_X = 7;
    private static final int GRID_Y = 5;
    private static final int ARROW_Y = 81;
    private static final int ARROW_WIDTH = 12;
    private static final int ARROW_HEIGHT = 11;
    /** Where the arrow's head starts along it, and how many rows either side of the middle its shaft has. */
    private static final int ARROW_HEAD = 5;
    private static final int ARROW_SHAFT = 1;
    /** The foot of the list, under the wells: its left half turns back, its right half on. */
    private static final float FOOT_TOP = 78.0F;
    /** The widest the page number between the arrows may be before it is drawn smaller. */
    private static final float NUMBER_ROOM = 27.0F;
    /** The list's grid across, and its middle: the page number stands there, the two halves of the foot meet there. */
    private static final float LIST_WIDE = COLUMNS * PITCH - (PITCH - WELL_SIZE);
    private static final float FOOT_MIDDLE = GRID_X + LIST_WIDE / 2.0F;
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
    private static final float TABS_X = GRID_X;
    private static final float TAB_WIDE = 16.0F;
    private static final float TAB_PITCH = 18.0F;
    private static final float TAB_HIGH = 14.0F;
    private static final float TAB_DROP = 3.0F;
    private static final float TAB_ICON = 11.0F;
    private static final int TAB_EDGE = 0x905A3C18;
    /** The least room kept between the tabs and the page's edges once the row has to move over to fit. */
    private static final float TAB_MARGIN = 2.0F;
    /** The "only what can be made now" switch: a tab over the right page, just clear of the fold's shade. */
    private static final float FILTER_X = 6.0F;
    /** The search: a tab beside the switch; typing, the row of tabs over the list becomes one slip of paper with the text on it. */
    private static final float SEARCH_X = 24.0F;
    private static final float SLIP_HIGH = 11.0F;
    private static final float SLIP_TEXT_X = 11.0F;
    private static final int QUERY_MOST = 50;
    private static final float CARET_BLINK = 0.5F;
    /** How long the switch nods when it leaves nothing on the page, seconds, and how high. */
    private static final float NOD_TIME = 0.8F;
    private static final float NOD_HEIGHT = 2.0F;
    /** How many dishes made last the "made lately" tab keeps, and its kind. */
    private static final int RECENT_MOST = 24;
    static final String RECENT = "#recent";
    /** The ribbon that closes the book, at the top of the right page: a bookmark coming out from under the head of the pages. */
    private static final int RIBBON_X = 62;
    private static final int RIBBON_WIDTH = 6;
    private static final int RIBBON_HEIGHT = 13;
    /** The ribbon takes the cursor a little round it: it is the only way to shut the book. */
    private static final float RIBBON_REACH = 2.0F;
    /** The shade in the fold of the pages. */
    private static final int FOLD = 0x285A3C18;
    private static final int FOLD_WIDTH = 5;
    /** The right page's middle, clear of the fold's shade on its left. */
    private static final float RIGHT_MIDDLE = (UNITS_WIDE + FOLD_WIDTH) / 2.0F;
    /** Small things at the foot of a page: how big, how far apart; the dish at the head of its page. */
    private static final float FOOT_SIZE = 12.0F;
    private static final float FOOT_PITCH = 15.0F;
    private static final float DISH_SIZE = 20.0F;
    private static final float DISH_Y = 3.0F;
    /** The least light the book is drawn in, block and sky: a torch-lit kitchen. */
    private static final int LIT = 12;
    /** How long a book handed over waits in the air for the next station to take it, seconds (the server lets the player in). */
    private static final float HANDOFF_WAIT = 1.0F;
    /** How small and how big the player can make the book, against its own size. */
    private static final float SIZE_LEAST = 0.75F;
    private static final float SIZE_MOST = 1.6F;
    /** Half the open book over its covers, node units: the pages and a model pixel of cover round them. */
    private static final float HALF_WIDE = 7.0F * SIZE;
    private static final float HALF_HIGH = 5.0F * SIZE;
    /** The corner that sizes the book, the top right one: its sides along the width (the right page at -x) and the height. */
    private static final int SIZING_CORNER_X = -1;
    private static final int SIZING_CORNER_Y = 1;
    /** A corner is taken on the cover's corner and the outer bit of the paper there, clear of the ribbon, node units. */
    private static final float CORNER_IN = 0.25F;
    /** How far the book can be moved from its place: a part of its width and of its height. */
    private static final float MOVE_WIDE = 0.75F;
    private static final float MOVE_HIGH = 0.5F;
    /** Kept between the book and the edges of the screen, node units. */
    private static final float SCREEN_MARGIN = 0.3F;
    /** How far a corner under the cursor rises towards the player, degrees, and how much a held book comes forward. */
    private static final float CORNER_RISE = 5.0F;
    private static final float HELD_GROWTH = 0.03F;
    /** The bar beside the ingredients when they take more rows than the page has: where it stands, how wide, its shortest grip. */
    private static final float BAR_X = UNITS_WIDE - 5.5F;
    private static final float BAR_WIDE = 2.0F;
    private static final float BAR_LEAST = 6.0F;
    private static final int BAR_TRACK = 0x285A3C18;
    private static final int BAR_GRIP = 0xA08A5A2B;
    /** The lowest an ingredient may reach with nothing under it on the page, units. */
    private static final float PAGE_FOOT = UNITS_HIGH - 3.0F;
    /** The keys of the book's size and place in its file: the size, then the place along its width and height, node units. */
    private static final String[] PLACE_KEYS = {"book.size", "book.x", "book.y"};
    /** The size and place the player gave the book, the same at every station; read from the file at first use. */
    private static float[] kept;

    private RecipeBookModel model;
    /** The size and place drawn now, going after the kept ones (at once while the player holds the book). */
    private float shownSize;
    private float shownX;
    private float shownY;
    /** Where the cursor is on the book's own plane, node units (x across, z up the page); null: not over it. */
    private Vector3f pointer;
    /** The book in the player's hand, by a corner to size it or by the rest to move it; null when nobody holds it. */
    private Hold hold;
    /** How much the book is held (comes forward), and the corner raised towards the cursor, each -1 to 1 along width and height. */
    private float held;
    private float riseX;
    private float riseY;
    /** The first row of ingredients shown when they take more rows than the page has, and the dish it is for. */
    private int firstRow;
    private Object rowsFor;
    /** The first row the picked dish was scrolled to, and that dish: the cursor crossing the list shows others a moment. */
    private int pickedRow;
    private Object pickedRowFor;
    /** The bar beside the ingredients as drawn last (right page units), null when they all fit: its rows and where its grip is. */
    private Bar bar;
    /** Where on the grip the player holds it, units from its top; negative while it is not held. */
    private float gripHeld = -1.0F;
    /** The move given to the node this frame, scene pixels: its place, the bob, the way over from another station. */
    private final Vector3f moved = new Vector3f();
    private List<Entry> entries = List.of();
    private boolean wanted;
    private float open;
    private float flip = 1.0F;
    private int flipWay = 1;
    /** How long the running flip takes, and when the last turn was, seconds of {@code time}; the last page sound, milliseconds. */
    private float flipTime = FLIP_TIME;
    private float lastTurn = -10.0F;
    private long lastTurnSound;
    /** What the left page listed when the running flip started: the sheet turned over shows it, not the list that is there now. */
    private Leaf flipFrom;
    /** The foot held down: the way it turns (0: none), since when, and how many pages it has turned. */
    private int leafing;
    private float leafSince;
    private int leafDone;
    private int page;
    private Entry picked;
    /** The key of the dish the player picked by a click since the book opened; a click on it again makes it. */
    private Object clicked;
    private Entry hovered;
    /**
     * The last dish of the list under the cursor: the right page keeps it while the cursor goes over to read it, until the cursor
     * leaves the book or the list changes.
     */
    private Entry previewed;
    /** What is drawn on the right page under the cursor, and the stack it showed when the cursor came: it stays shown. */
    private Drawn hoveredDrawn;
    private ItemStack drawnShown = ItemStack.EMPTY;
    /** The dish {@code hoveredDrawn} is a part of, and the recipe that makes it (null: the book has none). */
    private Entry drawnOf;
    private Page drawnTarget;
    /** The cursor is on the dish at the head of the right page. */
    private boolean overDish;
    /** The pages that make each item, as last read, whatever the tab and the switch: a thing that goes in leads to its recipe. */
    private Map<Item, List<Page>> makes = Map.of();
    /** What the player searches for, and whether they are typing it now (a click on the search tab or on the slip). */
    private String query = "";
    private boolean typing;
    private float typingFrom;
    private boolean overSearch;
    private boolean overSlip;
    /** The switch over the right page is under the cursor; when it last emptied the list, to nod it. */
    private boolean overFilter;
    private float switchNodFrom = -10.0F;
    private boolean emptiedBySwitch;
    /** The switch's icon: the station's own block, "what can be made here". */
    private ItemStack filterIcon = ItemStack.EMPTY;
    /** The page turned to under each tab of each book, while the game runs: the book opens where the player left it. */
    private static final Map<String, Integer> PAGES = new HashMap<>();
    /** The tabs over the list (none for a book of one kind), the kind shown (null: all), the tab under the cursor. */
    private List<Tab> tabs = List.of();
    private String tab;
    /** The tab the player chose, kept in the file; {@code tab} is the one shown, "all" while the chosen one holds nothing. */
    private String chosenTab;
    private boolean tabRemembered;
    private Tab hoveredTab;
    /**
     * The list keeps its order from the book's opening on: a dish never moves away from where the player found it while they
     * cook it (a rolled sheet is no longer its ingredients, which would put the dish at the end).
     */
    private boolean keepOrder;
    /** What is drawn on the right page and where, units of that page, for naming it under the cursor; and the dish it is of. */
    private final List<Drawn> drawn = new ArrayList<>();
    private Entry drawnFor;
    private Component tip;
    /**
     * The page the player last picked, kept on the client while the game runs: for each station block, and for each kind of
     * station. Coming back, the book opens at it again while what lies in the station goes on to it.
     */
    private static final Map<String, Object> PICKED = new HashMap<>();
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

    /**
     * The book taken by the player: by a corner ({@code cornerX}, {@code cornerY} the corner's side, -1 or 1, along the width and the
     * height) the corner across from it stays and the book grows or shrinks with the hand; by anything else it goes with the hand.
     * {@code grabX}, {@code grabY}: the point taken, node units; the rest is the book as it was taken.
     */
    private record Hold(int cornerX, int cornerY, float grabX, float grabY, float size, float x, float y) {
        boolean bySize() {
            return cornerX != 0;
        }
    }

    /** The ingredients' bar: {@code rows} in all, {@code shown} at once from {@code first}, its track and grip in right page units. */
    private record Bar(int rows, int shown, int first, float top, float high, float gripTop, float gripHigh) {
        boolean onGrip(float x, float y) {
            return onTrack(x, y) && y >= gripTop - 1.0F && y < gripTop + gripHigh + 1.0F;
        }

        boolean onTrack(float x, float y) {
            return x >= BAR_X - 2.0F && x < BAR_X + BAR_WIDE + 2.0F && y >= top && y < top + high;
        }

        /** The first row for the grip's top at {@code y}. */
        int rowAt(float y) {
            float room = high - gripHigh;
            return room <= 0.0F ? 0 : Math.round((y - top) / room * (rows - shown));
        }
    }

    RecipeBook(Source source) {
        this.source = source;
        float[] place = kept();
        shownSize = place[0];
        shownX = place[1];
        shownY = place[2];
    }

    private static float[] kept() {
        if (kept == null) {
            kept = new float[] {
                    Mth.clamp(BookTabs.number(PLACE_KEYS[0], 1.0F), SIZE_LEAST, SIZE_MOST),
                    BookTabs.number(PLACE_KEYS[1], 0.0F),
                    BookTabs.number(PLACE_KEYS[2], 0.0F)};
        }
        return kept;
    }

    private static void keep(float size, float x, float y) {
        float[] place = kept();
        place[0] = size;
        place[1] = x;
        place[2] = y;
        boolean usual = Math.abs(size - 1.0F) < 0.005F && Math.abs(x) < 0.005F && Math.abs(y) < 0.005F;
        BookTabs.putNumbers(PLACE_KEYS, usual ? new Float[3] : new Float[] {size, x, y});
    }

    /** What a station's book lists: its recipes, and what lies in the station already, which counts as the player's. */
    interface Source {
        List<Page> pages(StationView view);

        List<ItemStack> inStation(StationView view);

        /**
         * What a stack the player has counts as for the pages: by default itself; Youkai's Feasts' flask of soy sauce at its board is the
         * bottles of it that it holds.
         */
        default ItemStack asIngredient(StationView view, ItemStack stack) {
            return stack;
        }

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

        /**
         * Whether what lies in the station now can go on to the page {@code key}, so that a page picked before stays open; by default
         * when the station makes nothing yet or makes that page.
         */
        default boolean fits(StationView view, Object key) {
            Object current = current(view);
            return current == null || current.equals(key);
        }
    }

    /**
     * Something drawn on the right page, its box in units of that page; {@code ingredient}: which of the dish's ingredients it is, -1
     * for the dish and the small print.
     */
    private record Drawn(float x, float y, float size, ItemStack stack, int ingredient) {
        boolean contains(float pointX, float pointY) {
            return pointX >= x && pointX < x + size && pointY >= y && pointY < y + size;
        }

        /** The same place on the page, whatever it shows there now (an ingredient goes through its choices). */
        boolean sameSpot(Drawn other) {
            return other.ingredient == ingredient && other.x == x && other.y == y;
        }
    }

    /**
     * A tab over the list: the kind of recipe it shows ({@code Page.kind}; null: all; {@link #RECENT}: the dishes made last), drawn as
     * {@code icon}, named in its tooltip.
     */
    record Tab(String kind, ItemStack icon, Component name) {
    }

    /** What the left page lists: the list and its page. */
    private record Leaf(List<Entry> list, int page) {
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
            keepOrder = false;
            read(view);
            restore(view);
        } else {
            leafing = 0;
            endSearch();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PUT, 1.0F, 0.5F));
        }
    }

    /**
     * Opens the book where the player left it: the dish picked last (or the one the station makes now), on the page they had turned to
     * under this tab, unless the station makes that dish now and it lies on another page.
     */
    private void restore(StationView view) {
        previewed = null;
        clicked = null;
        picked = shown(view);
        Object current = source.current(view);
        boolean making = current != null && picked != null && picked.page().key().equals(current);
        Integer keptPage = PAGES.get(memoryKey());
        int at = picked == null ? 0 : Math.max(0, entries.indexOf(picked)) / PER_PAGE;
        page = Mth.clamp(keptPage != null && !making ? keptPage : at, 0, pages() - 1);
    }

    /** What this book is kept under in the book's file and while the game runs: its own name, else its kind of station. */
    private String bookId() {
        String memory = source.memory();
        return memory.isEmpty() ? source.getClass().getSimpleName() : memory;
    }

    /** Under which name the page of the tab shown is kept while the game runs. */
    private String memoryKey() {
        return bookId() + "/" + (tab == null ? "" : tab);
    }

    /** Keeps the page of the list in view for its tab (not of a search). */
    private void rememberPage() {
        if (!searching()) {
            PAGES.put(memoryKey(), page);
        }
    }

    /** Whether the book lists only what can be made now: the switch over the right page, kept for each book in the book's file. */
    private boolean filtering() {
        return "true".equals(BookTabs.get(bookId() + ".ready"));
    }

    /**
     * The player left the station. Going over to a neighbour, the book goes with them: it stays as it is until the next station's
     * book takes its place and flies from here to there with the camera. Leaving, it closes and goes away as the camera flies back.
     */
    void shut(StationView view) {
        if (hold != null) {
            letGo();
        }
        leafing = 0;
        endSearch();
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
            keepOrder = false;
            read(view);
            restore(view);
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

    private void turnSound() {
        turnSound(false);
    }

    /** A page turning; quick turns softer, and never so close together that leafing rattles. */
    private void turnSound(boolean quick) {
        long now = Util.getMillis();
        if (quick && now - lastTurnSound < TURN_SOUND_GAP) {
            return;
        }
        lastTurnSound = now;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F, quick ? 0.35F : 0.6F));
    }

    /**
     * The page the player picked last at this station (else at this kind of station) while what lies in it goes on to it; else the
     * page of what the station makes now; else the first. A page under another tab shows all the tabs' pages.
     */
    private Entry shown(StationView view) {
        List<Object> wanted = new ArrayList<>();
        for (String memory : List.of(placeMemory(view), kindMemory())) {
            Object kept = PICKED.get(memory);
            if (kept != null && source.fits(view, kept)) {
                wanted.add(kept);
            }
        }
        Object current = source.current(view);
        if (current != null) {
            wanted.add(current);
        }
        for (Object key : wanted) {
            Optional<Entry> found = find(key);
            if (found.isEmpty() && tab != null) {
                // All the tabs' dishes for now: the tab chosen stays kept in the file.
                chosenTab = null;
                keepOrder = false;
                read(view);
                found = find(key);
            }
            if (found.isPresent()) {
                return found.get();
            }
        }
        return entries.isEmpty() ? null : entries.get(0);
    }

    private Optional<Entry> find(Object key) {
        return entries.stream().filter(entry -> entry.page().key().equals(key)).findFirst();
    }

    private String kindMemory() {
        return source.getClass().getName();
    }

    private String placeMemory(StationView view) {
        return kindMemory() + "@" + view.pos().asLong();
    }

    /** The page the player has picked, null for none. */
    Object pickedKey() {
        return picked == null ? null : picked.page().key();
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

    /**
     * Reads the list again: the tab chosen, the search, the switch. Read afresh ({@code keepOrder} off: the book opened, or the player
     * changed what it shows), it is put in order: what can be made now first, then the rest, each by name, so that a dish has a place
     * the player learns; read again while it is open, it keeps that order.
     */
    private void read(StationView view) {
        if (!tabRemembered) {
            tabRemembered = true;
            chosenTab = BookTabs.get(bookId());
        }
        List<ItemStack> pool = pool(view);
        List<String> recent = recent();
        boolean filtering = filtering();
        // A recipe whose ingredient no item is (an addon's tag nothing fills) cannot be made: it is not offered.
        List<Page> all = new ArrayList<>();
        Map<Object, Page> byKey = new HashMap<>();
        Map<Item, List<Page>> making = new HashMap<>();
        Set<String> kinds = new HashSet<>();
        boolean anyRecent = false;
        for (Page page : source.pages(view)) {
            if (!Cookable.all(page.ingredients())) {
                continue;
            }
            all.add(page);
            byKey.putIfAbsent(page.key(), page);
            making.computeIfAbsent(page.dish().getItem(), item -> new ArrayList<>()).add(page);
            if (page.kind() != null) {
                kinds.add(page.kind());
            }
            anyRecent |= recent.contains(page.key().toString());
        }
        tabs = offered(view, kinds, anyRecent);
        tab = tabs.stream().anyMatch(it -> Objects.equals(it.kind(), chosenTab)) ? chosenTab : null;
        boolean searched = !query.isBlank();
        boolean lately = !searched && RECENT.equals(tab);
        List<Entry> found = new ArrayList<>();
        int inTab = 0;
        for (Page page : all) {
            if (searched ? !BookMath.matches(query, page.dish().getHoverName().getString(), page.key().toString())
                    : lately ? !recent.contains(page.key().toString()) : tab != null && !tab.equals(page.kind())) {
                continue;
            }
            inTab++;
            Entry entry = entry(page, pool);
            if (!filtering || entry.isReady()) {
                found.add(entry);
            }
        }
        if (keepOrder && !entries.isEmpty()) {
            // Open, the list keeps its order: a dish never moves away from where the player points. A tab, the switch or the search
            // chosen under the cursor is put in order afresh (they read with keepOrder off).
            // Looked up by key: read again every second, a modpack's book of a thousand dishes is no list to search in a sort.
            Map<Object, Integer> before = new HashMap<>();
            for (int i = 0; i < entries.size(); i++) {
                before.putIfAbsent(entries.get(i).page().key(), i);
            }
            found.sort(Comparator.comparingInt(entry -> before.getOrDefault(entry.page().key(), Integer.MAX_VALUE)));
        } else if (lately) {
            // Newest first, and they stay where they are: the point of the tab is that the dish of every day has its place.
            found.sort(Comparator.comparingInt(entry -> recent.indexOf(entry.page().key().toString())));
        } else {
            Map<Entry, String> names = new IdentityHashMap<>();
            found.forEach(entry -> names.put(entry, entry.dish().getHoverName().getString()));
            found.sort(Comparator.comparing((Entry entry) -> !entry.isReady()).thenComparing(names::get));
        }
        if (picked != null) {
            Object kept = picked.page().key();
            // Kept while the book has its recipe, also when the list does not show it (a dish the switch or the search hides). A reload
            // (a datapack, a script) that took the picked recipe away leaves the first one on the right page, not a blank one.
            Page page = byKey.get(kept);
            picked = found.stream().filter(entry -> entry.page().key().equals(kept)).findFirst()
                    .orElse(page != null ? entry(page, pool) : found.isEmpty() ? null : found.get(0));
        }
        if (previewed != null) {
            Object kept = previewed.page().key();
            previewed = found.stream().filter(entry -> entry.page().key().equals(kept)).findFirst().orElse(null);
        }
        entries = found;
        makes = making;
        keepOrder = true;
        page = Math.min(page, pages() - 1);
        // The switch emptied the list: it nods, so the player sees why the page is blank.
        boolean emptied = filtering && found.isEmpty() && inTab > 0;
        if (emptied && !emptiedBySwitch) {
            switchNodFrom = time;
        }
        emptiedBySwitch = emptied;
    }

    /**
     * The tabs offered: the station's own, and "made lately" after "all" once something was made from this book. Tabs with nothing
     * in them are not offered, and a single kind is no tab of its own (it would be "all" again); fewer than two tabs: none.
     */
    private List<Tab> offered(StationView view, Set<String> kinds, boolean anyRecent) {
        List<Tab> offered = new ArrayList<>(source.tabs(view));
        if (offered.isEmpty()) {
            offered.add(new Tab(null, new ItemStack(Items.COMPASS), Component.translatable("handson_delight.book.tab.all")));
        }
        offered.removeIf(it -> it.kind() != null && !kinds.contains(it.kind()));
        if (offered.stream().filter(it -> it.kind() != null).count() == 1) {
            offered.removeIf(it -> it.kind() != null);
        }
        if (anyRecent) {
            offered.add(1, new Tab(RECENT, new ItemStack(Items.CLOCK), Component.translatable("handson_delight.book.tab.recent")));
        }
        return offered.size() >= 2 ? offered : List.of();
    }

    /** The keys of the dishes made from this book lately, newest first, kept in the book's file. */
    private List<String> recent() {
        String kept = BookTabs.get(bookId() + ".recent");
        return kept == null || kept.isEmpty() ? List.of() : List.of(kept.split(","));
    }

    /** A dish was made from the book: it goes to the head of the dishes made lately. */
    private void made(Page page) {
        List<String> recent = new ArrayList<>(recent());
        String key = page.key().toString();
        recent.remove(key);
        recent.add(0, key);
        while (recent.size() > RECENT_MOST) {
            recent.remove(recent.size() - 1);
        }
        BookTabs.put(bookId() + ".recent", String.join(",", recent));
    }

    /** What the player has to cook with: the inventory, the stack in the hand (a knife taken to cut), and what lies in the station. */
    private List<ItemStack> pool(StationView view) {
        List<ItemStack> pool = new ArrayList<>();
        var player = Minecraft.getInstance().player;
        if (player != null) {
            for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                pool.add(source.asIngredient(view, player.getInventory().getItem(i)));
            }
            pool.add(source.asIngredient(view, player.inventoryMenu.getCarried()));
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
        float flight = DiegesisClient.goingOver();
        if (leaving != null && !leaving.taken && isWorkedAt(view) && flight >= 1.0F) {
            // The player came back before the next station took the book (A, D, A pressed quickly): it is this station's again. Left to
            // go stale, it would shut here, at the station the player is at.
            if (handoff == leaving) {
                handoff = null;
            }
            wanted = leaving.wanted;
            leaving = null;
            arrivingFrom = null;
            arrivingHeld = false;
        }
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
        if (arrivingFrom != null && !arrivingHeld && leaving == null && flight < 1.0F && !isWorkedAt(view)) {
            // The player went on before the book got here (the station was not theirs yet, so it was not shut).
            passOn(node);
        }
        if (arrivingFrom != null && !arrivingHeld && (flight >= 1.0F || !isWorkedAt(view))) {
            arrivingFrom = null;
        }
        float appear = appear(view);
        boolean involved = view.isFocused() || leaving != null || arrivingFrom != null || isWorkedAt(view);
        node.setVisible(involved && appear > SPECK);
        if (!node.isVisible() && leaving == null) {
            open = 0.0F;
        }
        if (!involved) {
            // A station the player passed by (quick A and D) keeps no open book: it would open from shut every frame, its page turning
            // on and on.
            wanted = false;
        }
        time += dt;
        // It comes in shut, growing with the camera, and opens once the camera is there: the covers swing out before the eyes.
        boolean opening = wanted && appear >= OPEN_FROM;
        if (opening && open <= 0.0F && node.isVisible()) {
            turnSound();
        }
        open = Mth.clamp(open + (opening ? dt : -dt) / OPEN_TIME, 0.0F, 1.0F);
        flip = Math.min(1.0F, flip + dt / flipTime);
        // Grows and opens where it hangs, breathing a little.
        float eased = eased();
        reach = Mth.lerp(Math.min(1.0F, dt * 12.0F), reach, node.isHovered() && !wanted ? 1.0F : 0.0F);
        place(view, node, dt, appear >= 1.0F && arrivingFrom == null);
        node.scaleTo(Mth.lerp(eased, CLOSED_SIZE * (1.0F + 0.08F * reach), 1.0F) * appear * drawnSize(), 0.0F);
        float bob = Mth.sin(time * 1.3F) * 0.14F;
        Vector3f shift = onScene(node, shownX, shownY);
        Quaternionf turn = facing(view, node, shift);
        if (arrivingFrom != null) {
            // With the camera, on the same easing: the book keeps its place on the screen while the kitchen moves under it.
            float left = arrivingHeld ? 1.0F : 1.0F - flight;
            arrivingLeft = left;
            moved.set(arrivingFrom).mul(left).add(shift.x * (1.0F - left), shift.y * (1.0F - left) + bob, shift.z * (1.0F - left));
        } else {
            moved.set(shift.x, shift.y + bob, shift.z);
        }
        node.moveTo(moved.x, moved.y, moved.z, 0.0F);
        // It faces the player wherever it was moved, sways a little, and a corner under the cursor rises towards the player, as a
        // page corner is lifted to be taken.
        turn.mul(Transform.toQuaternion(new Vector3f(Mth.sin(time * 0.9F) * 1.2F - riseY * CORNER_RISE, Mth.cos(time * 0.7F) * 1.5F,
                riseX * CORNER_RISE), new Quaternionf()));
        Vector3f angles = turn.getEulerAnglesYXZ(new Vector3f());
        node.rotateTo(angles.x * Mth.RAD_TO_DEG, angles.y * Mth.RAD_TO_DEG, angles.z * Mth.RAD_TO_DEG, 0.0F);
        refresh -= dt;
        if (refresh <= 0.0F && wanted) {
            refresh = REFRESH_TIME;
            read(view);
        }
        Item block = view.level().getBlockState(view.pos()).getBlock().asItem();
        if (filterIcon.getItem() != block) {
            filterIcon = new ItemStack(block);
        }
        follow(view, node);
        leaf();
        name(node);
    }

    /** The foot held down goes on turning pages, quicker and quicker, until it is let go, the cursor leaves it or the list ends. */
    private void leaf() {
        if (leafing == 0) {
            return;
        }
        if (!leftHeld() || !wanted || open < 1.0F || footSide() != leafing) {
            leafing = 0;
            return;
        }
        while (BookMath.repeatDue(time - leafSince, leafDone)) {
            leafDone++;
            int before = page;
            turn(leafing, false);
            if (page == before) {
                leafing = 0;
                return;
            }
        }
    }

    /** Whether the book shows a search: being typed, or its text standing on the slip. */
    private boolean searching() {
        return typing || !query.isEmpty();
    }

    /** The search is over: its text goes, the tabs come back (the book was shut or the station left). */
    private void endSearch() {
        typing = false;
        query = "";
    }

    private void setQuery(StationView view, String text) {
        if (text.equals(query)) {
            return;
        }
        query = text;
        previewed = null;
        keepOrder = false;
        read(view);
        page = 0;
    }

    /**
     * A key while the player works at the station. Typing, the book takes every key (Enter or Escape ends the typing, Backspace takes
     * a letter back, with Ctrl all of them, Ctrl+V pastes), so that the station's own keys do not act on letters; otherwise only
     * Page Up and Page Down, Home and End, which turn the open book's pages.
     */
    boolean key(StationView view, int keyCode, int modifiers) {
        if (!wanted || open < 1.0F || !HandsOnConfig.recipeBook()) {
            typing = false;
            return false;
        }
        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (typing) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                typing = false;
                if (query.isBlank()) {
                    stopSearch(view);
                }
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
                setQuery(view, control ? "" : query.substring(0, query.length() - 1));
            } else if (keyCode == GLFW.GLFW_KEY_V && control) {
                type(view, Minecraft.getInstance().keyboardHandler.getClipboard());
            } else {
                pageKey(keyCode);
            }
            return true;
        }
        return pageKey(keyCode);
    }

    /** A character typed: it goes into the search while the player is typing it. */
    boolean typed(StationView view, char character) {
        if (!typing || !wanted) {
            return false;
        }
        type(view, String.valueOf(character));
        return true;
    }

    private void type(StationView view, String text) {
        StringBuilder added = new StringBuilder(query);
        for (char character : text.toCharArray()) {
            if (net.minecraft.util.StringUtil.isAllowedChatCharacter(character) && added.length() < QUERY_MOST) {
                added.append(character);
            }
        }
        setQuery(view, added.toString());
    }

    /** Page Up and Down turn a page, Home and End go to the first and the last. */
    private boolean pageKey(int keyCode) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_PAGE_DOWN -> turn(1, false);
            case GLFW.GLFW_KEY_PAGE_UP -> turn(-1, false);
            case GLFW.GLFW_KEY_HOME -> turn(-page, false);
            case GLFW.GLFW_KEY_END -> turn(pages() - 1 - page, false);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** The search tab clicked again: the text goes and the tabs come back, on the page the tab was left at. */
    private void stopSearch(StationView view) {
        endSearch();
        keepOrder = false;
        read(view);
        Integer keptPage = PAGES.get(memoryKey());
        page = Mth.clamp(keptPage == null ? 0 : keptPage, 0, pages() - 1);
    }

    /**
     * The book's size and place: going after the hand while the player holds it, else easing to what was kept. Once the camera is
     * in, the book is kept on the screen whatever the window: a smaller window moves it in (or makes it smaller) only while it is
     * that small, the kept place stays.
     */
    private void place(StationView view, NodeView node, float dt, boolean settled) {
        pointer = view.pointerOn("book_open", 0.0F).orElse(null);
        Bar ingredients = bar;
        if (gripHeld >= 0.0F) {
            if (!leftHeld() || ingredients == null || open < 1.0F) {
                gripHeld = -1.0F;
            } else if (pointer != null) {
                // The grip goes with the hand, also off the page, a whole row at a time.
                float y = (PAGE_HEIGHT / 2.0F - pointer.z) / UNIT;
                int row = Mth.clamp(ingredients.rowAt(y - gripHeld), 0, ingredients.rows() - ingredients.shown());
                if (row != firstRow) {
                    firstRow = row;
                    rowSound();
                }
            }
        }
        boolean taking = hold != null;
        if (taking && (!leftHeld() || !node.isVisible() || open < 1.0F)) {
            letGo();
        } else if (taking && pointer != null) {
            follow(hold, settled ? onScreen(view, node, home(node)) : null);
        }
        float[] place = kept();
        float size = place[0];
        float x = place[1];
        float y = place[2];
        float[] screen = settled && hold == null ? onScreen(view, node, home(node)) : null;
        if (screen != null) {
            float[] fitted = fit(screen, size, x, y, 0, 0);
            size = fitted[0];
            x = fitted[1];
            y = fitted[2];
        }
        float ease = hold != null ? 1.0F : Math.min(1.0F, dt * 12.0F);
        shownSize = Mth.lerp(ease, shownSize, size);
        shownX = Mth.lerp(ease, shownX, x);
        shownY = Mth.lerp(ease, shownY, y);
        float step = Math.min(1.0F, dt * 14.0F);
        held = Mth.lerp(step, held, hold != null ? 1.0F : 0.0F);
        int[] corner = hold != null ? (hold.bySize() ? new int[] {hold.cornerX(), hold.cornerY()} : null)
                : onNothing() && node.isHovered() ? corner(riseX != 0.0F || riseY != 0.0F ? CORNER_IN * 0.6F : 0.0F) : null;
        riseX = Mth.lerp(step, riseX, corner == null ? 0.0F : corner[0]);
        riseY = Mth.lerp(step, riseY, corner == null ? 0.0F : corner[1]);
        if (Math.abs(riseX) < 0.002F && Math.abs(riseY) < 0.002F) {
            riseX = 0.0F;
            riseY = 0.0F;
        }
    }

    /** The book in the hand goes after it: sized from the corner across, or moved, then kept as far as the limits and the screen allow. */
    private void follow(Hold taken, float[] screen) {
        float size;
        float x;
        float y;
        if (taken.bySize()) {
            // The corner across stays; the size follows the hand along the line from that corner to where the book was taken. The
            // hand is on the plane the book hangs in at its own place and size: the node's units grow with the book.
            float handX = shownX + pointer.x * drawnSize();
            float handY = shownY + pointer.z * drawnSize();
            float stayX = taken.x() - taken.cornerX() * HALF_WIDE * taken.size();
            float stayY = taken.y() - taken.cornerY() * HALF_HIGH * taken.size();
            float fromX = taken.x() + taken.grabX() * taken.size() - stayX;
            float fromY = taken.y() + taken.grabY() * taken.size() - stayY;
            float along = ((handX - stayX) * fromX + (handY - stayY) * fromY) / Math.max(1.0E-4F, fromX * fromX + fromY * fromY);
            size = Mth.clamp(taken.size() * along, SIZE_LEAST, SIZE_MOST);
            x = stayX + taken.cornerX() * HALF_WIDE * size;
            y = stayY + taken.cornerY() * HALF_HIGH * size;
        } else {
            size = taken.size();
            // The point taken stays under the hand.
            x = shownX + (pointer.x - taken.grabX()) * drawnSize();
            y = shownY + (pointer.z - taken.grabY()) * drawnSize();
        }
        float[] fitted = screen == null ? limited(size, x, y) : fit(screen, size, x, y, taken.bySize() ? taken.cornerX() : 0,
                taken.bySize() ? taken.cornerY() : 0);
        kept()[0] = fitted[0];
        kept()[1] = fitted[1];
        kept()[2] = fitted[2];
    }

    /**
     * Whether the left button is down now. The game's own {@code MouseHandler.isLeftPressed} is only kept while no screen is open,
     * and the station is one: it always says up there.
     */
    private static boolean leftHeld() {
        return GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    /** The size the book is drawn at, against its own: as given, and a little bigger while it is held. */
    private float drawnSize() {
        return shownSize * (1.0F + HELD_GROWTH * held);
    }

    /** The place kept within the reach of its own: a part of the book's width and height from where it hangs. */
    private static float[] limited(float size, float x, float y) {
        return new float[] {size, Mth.clamp(x, -MOVE_WIDE * 2.0F * HALF_WIDE, MOVE_WIDE * 2.0F * HALF_WIDE),
                Mth.clamp(y, -MOVE_HIGH * 2.0F * HALF_HIGH, MOVE_HIGH * 2.0F * HALF_HIGH)};
    }

    /**
     * The book at {@code size} and {@code x, y} put where all of it is on the screen ({@code screen}: the screen's corners on the book's
     * plane), within its reach. A book too big for the screen is made smaller: from the corner across from {@code cornerX, cornerY}
     * when one is held, else about its middle.
     */
    private static float[] fit(float[] screen, float size, float x, float y, int cornerX, int cornerY) {
        float stayX = x - cornerX * HALF_WIDE * size;
        float stayY = y - cornerY * HALF_HIGH * size;
        float[] place = limited(size, x, y);
        for (int tries = 0; tries < 40; tries++) {
            place = limited(size, x, y);
            if (pushIn(screen, place) || size <= SIZE_LEAST) {
                return place;
            }
            size = Math.max(SIZE_LEAST, size - 0.02F);
            x = cornerX != 0 ? stayX + cornerX * HALF_WIDE * size : x;
            y = cornerY != 0 ? stayY + cornerY * HALF_HIGH * size : y;
        }
        return place;
    }

    /** Moves {@code place} (size, x, y) in from every edge of the screen it reaches over; true when all of it is on the screen then. */
    private static boolean pushIn(float[] screen, float[] place) {
        for (int pass = 0; pass < 3; pass++) {
            for (int edge = 0; edge < 4; edge++) {
                float[] in = inward(screen, edge);
                float clear = in == null ? SCREEN_MARGIN : clearance(in, place);
                if (clear < SCREEN_MARGIN) {
                    place[1] += in[0] * (SCREEN_MARGIN - clear);
                    place[2] += in[1] * (SCREEN_MARGIN - clear);
                }
            }
        }
        // Pushed in from one side and out again over the other: it does not fit.
        for (int edge = 0; edge < 4; edge++) {
            float[] in = inward(screen, edge);
            if (in != null && clearance(in, place) < SCREEN_MARGIN * 0.5F) {
                return false;
            }
        }
        return true;
    }

    /** An edge of the screen on the book's plane: its normal turned to the inside, and a point of it; null when it has no length. */
    private static float[] inward(float[] screen, int edge) {
        float fromX = screen[edge * 2];
        float fromY = screen[edge * 2 + 1];
        float alongX = screen[(edge + 1) % 4 * 2] - fromX;
        float alongY = screen[(edge + 1) % 4 * 2 + 1] - fromY;
        float length = Mth.sqrt(alongX * alongX + alongY * alongY);
        if (length < 1.0E-4F) {
            return null;
        }
        float inX = -alongY / length;
        float inY = alongX / length;
        float middleX = (screen[0] + screen[2] + screen[4] + screen[6]) / 4.0F;
        float middleY = (screen[1] + screen[3] + screen[5] + screen[7]) / 4.0F;
        float sign = inX * (middleX - fromX) + inY * (middleY - fromY) < 0.0F ? -1.0F : 1.0F;
        return new float[] {inX * sign, inY * sign, fromX, fromY};
    }

    /** How far all of the book at {@code place} is inside the edge {@code in}; negative: it reaches over. */
    private static float clearance(float[] in, float[] place) {
        return in[0] * (place[1] - in[2]) + in[1] * (place[2] - in[3]) - Math.abs(in[0]) * HALF_WIDE * place[0]
                - Math.abs(in[1]) * HALF_HIGH * place[0];
    }

    /**
     * The corners of the screen on the plane the book hangs in at its own place (node units at its own size: x across, y up the page),
     * in the order top left, top right, bottom right, bottom left; null while the camera cannot tell.
     */
    private static float[] onScreen(StationView view, NodeView node, Vector3f origin) {
        Transform own = node.definition().transform();
        Quaternionf turn = Transform.toQuaternion(own.rotation(), new Quaternionf());
        float base = own.scale().x();
        Vector3f across = turn.transform(new Vector3f(base, 0.0F, 0.0F));
        Vector3f up = turn.transform(new Vector3f(0.0F, 0.0F, base));
        Vector3f normal = turn.transform(new Vector3f(0.0F, 1.0F, 0.0F));
        float[] corners = new float[8];
        for (int i = 0; i < 4; i++) {
            float screenX = i == 1 || i == 2 ? 1.0F : 0.0F;
            float screenY = i >= 2 ? 1.0F : 0.0F;
            // The line from the eye through that corner of the screen, where it goes through the book's plane.
            Vector3f[] line = line(view, screenX, screenY, origin.y);
            if (line == null) {
                return null;
            }
            float towards = line[1].dot(normal);
            if (Math.abs(towards) < 1.0E-4F) {
                return null;
            }
            Vector3f hit = new Vector3f(line[1]).mul(new Vector3f(origin).sub(line[0]).dot(normal) / towards).add(line[0]).sub(origin);
            corners[i * 2] = hit.dot(across) / across.lengthSquared();
            corners[i * 2 + 1] = hit.dot(up) / up.lengthSquared();
        }
        return corners;
    }

    /** Where the book hangs in the scene at its own place, pixels: where it is drawn less the move given to it. */
    private Vector3f home(NodeView node) {
        return node.scenePosition().sub(moved);
    }

    /**
     * The turn that keeps the book facing the player wherever it is moved: from the way it looks at the eye from its own place to the
     * way it would from where it is, in the node's own axes (its scene turn comes first); none while the camera cannot tell.
     */
    private Quaternionf facing(StationView view, NodeView node, Vector3f move) {
        Vector3f origin = home(node);
        Vector3f eye = eye(view, origin.y);
        if (eye == null) {
            return new Quaternionf();
        }
        Vector3f fromHome = new Vector3f(eye).sub(origin).normalize();
        Vector3f fromHere = new Vector3f(eye).sub(origin).sub(move).normalize();
        Quaternionf own = Transform.toQuaternion(node.definition().transform().rotation(), new Quaternionf());
        Quaternionf turn = new Quaternionf().rotationTo(fromHome, fromHere);
        return new Quaternionf(own).conjugate().mul(turn).mul(own);
    }

    /** Where the player's eye is in the scene, pixels: where the lines through two points of the screen meet; null when not known. */
    private static Vector3f eye(StationView view, float height) {
        Vector3f[] middle = line(view, 0.5F, 0.5F, height);
        Vector3f[] corner = line(view, 0.1F, 0.1F, height);
        if (middle == null || corner == null) {
            return null;
        }
        // The closest points of the two lines; through one eye they meet.
        Vector3f across = new Vector3f(middle[0]).sub(corner[0]);
        float aa = middle[1].dot(middle[1]);
        float ab = middle[1].dot(corner[1]);
        float bb = corner[1].dot(corner[1]);
        float ad = middle[1].dot(across);
        float bd = corner[1].dot(across);
        float under = aa * bb - ab * ab;
        if (Math.abs(under) < 1.0E-6F) {
            return null;
        }
        float along = (ab * bd - bb * ad) / under;
        return new Vector3f(middle[1]).mul(along).add(middle[0]);
    }

    /** The line from the eye through a point of the screen: a point of it and its way, scene pixels; null when not known. */
    private static Vector3f[] line(StationView view, float x, float y, float height) {
        Optional<Vector3f> near = view.screenOn(x, y, height);
        Optional<Vector3f> far = view.screenOn(x, y, height - 8.0F);
        if (near.isEmpty() || far.isEmpty()) {
            return null;
        }
        return new Vector3f[] {far.get(), new Vector3f(near.get()).sub(far.get())};
    }

    /** {@code x, y} on the book's plane (node units at its own size) as a move of the node in the scene, pixels. */
    private static Vector3f onScene(NodeView node, float x, float y) {
        Transform home = node.definition().transform();
        float base = home.scale().x();
        return Transform.toQuaternion(home.rotation(), new Quaternionf()).transform(new Vector3f(x * base, 0.0F, y * base));
    }

    /**
     * The corner of the open book under the cursor that sizes it, its sides along the width and the height (-1 or 1); null off it.
     * Only the top right one does, one place to find: the left page lies at +x, the top of the pages at +z. A corner already raised
     * is kept a little further out ({@code more}), so that rising does not take it from under the cursor.
     */
    private int[] corner(float more) {
        if (pointer == null) {
            return null;
        }
        float x = Math.abs(pointer.x);
        float y = Math.abs(pointer.z);
        float in = CORNER_IN + more;
        boolean onCorner = x > PAGE_WIDTH - in && y > PAGE_HEIGHT / 2.0F - in && x < HALF_WIDE + in && y < HALF_HIGH + in;
        return onCorner && pointer.x < 0.0F && pointer.z > 0.0F ? new int[] {SIZING_CORNER_X, SIZING_CORNER_Y} : null;
    }

    /**
     * The mouse wheel over the book: over the ingredients that take more rows than the page has it goes down and up them, anywhere
     * else it turns the list's pages, a page a notch, and stops at the ends. True when the wheel was over the book, so that nothing
     * behind it turns.
     */
    boolean scroll(double amount) {
        if (!wanted || open < 1.0F || amount == 0.0) {
            return wanted;
        }
        int way = amount > 0.0 ? -1 : 1;
        if (cursorX >= UNITS_WIDE && cursorY >= 0.0F && bar != null) {
            scrollRows(way);
        } else {
            turn(way, false);
        }
        return true;
    }

    private void scrollRows(int by) {
        Bar ingredients = bar;
        if (ingredients == null) {
            return;
        }
        int row = Mth.clamp(firstRow + by, 0, ingredients.rows() - ingredients.shown());
        if (row != firstRow) {
            firstRow = row;
            rowSound();
        }
    }

    private static void rowSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.6F, 0.15F));
    }

    /**
     * A press on the open book where nothing of the recipes is: the left button takes it (a corner to size it, the rest to move it), the
     * right one puts it back to its own size and place.
     */
    private void take(int button) {
        if (open < 1.0F || flip < 1.0F || pointer == null) {
            return;
        }
        float[] place = kept();
        if (button == 1) {
            if (place[0] != 1.0F || place[1] != 0.0F || place[2] != 0.0F) {
                keep(1.0F, 0.0F, 0.0F);
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PUT, 1.1F, 0.4F));
            }
            return;
        }
        if (button != 0) {
            return;
        }
        // Taken as it is drawn, which a small window may have moved in from the kept place.
        int[] corner = corner(CORNER_IN * 0.6F);
        hold = new Hold(corner == null ? 0 : corner[0], corner == null ? 0 : corner[1], pointer.x, pointer.z, shownSize, shownX, shownY);
        place[0] = shownSize;
        place[1] = shownX;
        place[2] = shownY;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.3F, 0.35F));
    }

    /** The player let the book go: it settles where it is, and the place is kept for the next time. */
    private void letGo() {
        hold = null;
        float[] place = kept();
        keep(place[0], place[1], place[2]);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PUT, 1.25F, 0.3F));
    }

    /**
     * Words go into the tooltip, never onto the page: a dish or what goes in is named under the cursor, as an item in a slot is, and so
     * are a tab, the switch and the search.
     */
    private void name(NodeView node) {
        ItemStack under = hovered != null ? hovered.dish() : hoveredDrawn != null ? drawnShown : ItemStack.EMPTY;
        Component text = hoveredTab != null ? hoveredTab.name()
                : overFilter ? Component.translatable(filtering() ? "handson_delight.book.only_ready" : "handson_delight.book.showing_all")
                : overSearch ? Component.translatable("gui.recipebook.search_hint")
                : under.isEmpty() ? Component.empty() : under.getHoverName();
        if (!Objects.equals(text, tip)) {
            tip = text;
            node.setTooltip(text);
        }
    }

    /** How far open the book is drawn: the covers lift slowly, swing over and settle flat, and close the same way back. */
    private float eased() {
        return open * open * (3.0F - 2.0F * open);
    }

    /** The angle between a lying page and flat at this opening: the pages rise a little towards their outer edges. */
    private float pageTilt() {
        return Mth.HALF_PI - 1.25F * OPEN * eased();
    }

    /** How high the fold of the pages stands over the node's plane, pixels. */
    private float foldHeight() {
        return (Mth.sin(1.25F * OPEN * eased()) + PAGE_GAP) * SIZE;
    }

    private void follow(StationView view, NodeView node) {
        cursorX = -1.0F;
        cursorY = -1.0F;
        hovered = null;
        hoveredTab = null;
        overFilter = false;
        overSearch = false;
        overSlip = false;
        Drawn wasDrawn = hoveredDrawn;
        Entry wasOf = drawnOf;
        hoveredDrawn = null;
        overDish = false;
        followCursor(view);
        if (hovered != null) {
            previewed = hovered;
        } else if (cursorX < 0.0F) {
            previewed = null;
        }
        // The right page as drawn last: the dish at its head and what goes in, under the cursor.
        Entry right = rightShown();
        if (right != null && right == drawnFor && cursorX >= UNITS_WIDE && cursorY >= 0.0F && !onRibbon()) {
            float x = cursorX - UNITS_WIDE;
            overDish = x >= RIGHT_MIDDLE - DISH_SIZE / 2.0F && x < RIGHT_MIDDLE + DISH_SIZE / 2.0F && cursorY >= DISH_Y
                    && cursorY < DISH_Y + DISH_SIZE;
            for (Drawn thing : drawn) {
                if (thing.contains(x, cursorY)) {
                    hoveredDrawn = thing;
                }
            }
        }
        if (hoveredDrawn == null) {
            drawnOf = null;
            drawnTarget = null;
        } else if (wasDrawn == null || !wasDrawn.sameSpot(hoveredDrawn) || wasOf != right) {
            // Under the cursor it stops going through its choices: the one it showed when the cursor came stays.
            drawnOf = right;
            drawnShown = hoveredDrawn.stack();
            drawnTarget = hoveredDrawn.ingredient() < 0 ? null : target(right, hoveredDrawn.ingredient(), drawnShown);
        }
    }

    /** The dish the right page shows: the one under the cursor on the list, else the one last pointed at, else the one picked. */
    private Entry rightShown() {
        return hovered != null ? hovered : previewed != null ? previewed : picked;
    }

    /**
     * The recipe that makes a thing going into {@code entry}: of the stack shown, else of any of its choices; one that can be made now
     * first. Null when the book has none (most raw food), or only the dish's own.
     */
    private Page target(Entry entry, int index, ItemStack shown) {
        List<Page> making = shown.isEmpty() ? null : makes.get(shown.getItem());
        if (making == null) {
            for (ItemStack choice : entry.ingredients().get(index).getItems()) {
                making = makes.get(choice.getItem());
                if (making != null) {
                    break;
                }
            }
        }
        if (making == null || station == null) {
            return null;
        }
        List<ItemStack> pool = pool(station);
        Page first = null;
        for (Page page : making) {
            if (page.key().equals(entry.page().key())) {
                continue;
            }
            if (entry(page, pool).isReady()) {
                return page;
            }
            first = first == null ? page : first;
        }
        return first;
    }

    private void followCursor(StationView view) {
        if (open < 1.0F) {
            return;
        }
        // The pages rise from the fold towards their outer edges: the cursor's ray meets a page above the fold's plane, the higher
        // the further out. A few steps find the height of the page where the ray comes down on it.
        float tilt = pageTilt();
        float fold = foldHeight();
        float height = fold;
        Vector3f point = null;
        for (int step = 0; step < 4; step++) {
            Optional<Vector3f> cursor = view.pointerOn("book_open", height);
            if (cursor.isEmpty()) {
                return;
            }
            point = cursor.get();
            height = fold + Math.abs(point.x) * Mth.sin(tilt) / Mth.cos(tilt);
        }
        float y = (PAGE_HEIGHT / 2.0F - point.z) / UNIT;
        // Along the page from the fold; the left page runs from its edge at +x to the fold at 0, the right one on from there.
        float along = Math.abs(point.x) / Mth.cos(tilt);
        float x = point.x > 0.0F ? (PAGE_WIDTH - along) / UNIT : UNITS_WIDE + along / UNIT;
        boolean overRow = x >= 0.0F && x < UNITS_WIDE && y >= -TAB_HIGH && y < 0.0F;
        boolean onSlip = searching() && overRow && x >= TAB_MARGIN && x < UNITS_WIDE - TAB_MARGIN && y >= -SLIP_HIGH;
        boolean overTabs = !searching() && !tabs.isEmpty() && overRow;
        boolean onSearch = x >= UNITS_WIDE + SEARCH_X && x < UNITS_WIDE + SEARCH_X + TAB_WIDE && y >= -TAB_HIGH && y < 0.0F;
        boolean onFilter = x >= UNITS_WIDE + FILTER_X && x < UNITS_WIDE + FILTER_X + TAB_WIDE && y >= -TAB_HIGH && y < 0.0F;
        // The ribbon stands a little over the head of the page, and takes the cursor a little round it.
        boolean nearRibbon = x >= UNITS_WIDE + RIBBON_X - RIBBON_REACH && x < UNITS_WIDE + RIBBON_X + RIBBON_WIDTH + RIBBON_REACH
                && y >= -RIBBON_REACH && y < 0.0F;
        if (x >= 0.0F && x < UNITS_WIDE * 2 && (y >= 0.0F || overTabs || onFilter || nearRibbon || onSlip || onSearch)
                && y < UNITS_HIGH) {
            cursorX = x;
            cursorY = y;
            // While a sheet goes over, the list it uncovers takes the cursor once it is mostly in view; the foot and tabs at once.
            hovered = flip >= FLIP_LIVE && y >= 0.0F ? entryAt(x, y) : null;
            hoveredTab = overTabs ? tabAt(x) : null;
            overFilter = onFilter;
            overSearch = onSearch;
            overSlip = onSlip;
        }
    }

    /**
     * The half of the list's foot under the cursor, where the page arrows are: -1 the left half (back), 1 the right half (on), 0 off
     * it or with one page only. The whole foot turns pages, not only the arrows drawn on it.
     */
    private int footSide() {
        if (pages() <= 1 || cursorY < FOOT_TOP || cursorY >= UNITS_HIGH || cursorX < 0.0F || cursorX >= UNITS_WIDE) {
            return 0;
        }
        return cursorX < FOOT_MIDDLE ? -1 : 1;
    }

    /**
     * Whether the open book is under the cursor where nothing of its own is: no dish on the list, tab, switch, search, ribbon, foot,
     * dish or thing that goes in, nor the ingredients' bar. There the book is taken, sized and put back.
     */
    private boolean onNothing() {
        Bar ingredients = bar;
        boolean onBar = ingredients != null && cursorX >= UNITS_WIDE && ingredients.onTrack(cursorX - UNITS_WIDE, cursorY);
        return wanted && open >= 1.0F && flip >= 1.0F && hovered == null && hoveredTab == null && !overFilter && !overSearch && !overSlip
                && !onRibbon() && footSide() == 0 && hoveredDrawn == null && !overDish && !onBar;
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

    /** The ribbon that shuts the book, with a little room round it: it is the only way to shut it, so it is easy to hit. */
    private boolean onRibbon() {
        return cursorX >= UNITS_WIDE + RIBBON_X - RIBBON_REACH && cursorX < UNITS_WIDE + RIBBON_X + RIBBON_WIDTH + RIBBON_REACH
                && cursorY >= -RIBBON_REACH && cursorY < RIBBON_HEIGHT + RIBBON_REACH + 1.0F;
    }

    /**
     * A click on the book: opens it; open it picks a dish, turns the page, or closes by its ribbon; anywhere else the book is taken in
     * the hand (by a corner to size it), and the right button puts it back to its own size and place.
     */
    void click(StationView view, int button) {
        int foot = footSide();
        if (typing && !overSlip && !overSearch) {
            // A click anywhere else ends the typing, and does what it does.
            typing = false;
            if (query.isBlank()) {
                stopSearch(view);
            }
        }
        Bar ingredients = bar;
        if (wanted && ingredients != null && cursorX >= UNITS_WIDE && ingredients.onTrack(cursorX - UNITS_WIDE, cursorY)) {
            if (button == 0 && ingredients.onGrip(cursorX - UNITS_WIDE, cursorY)) {
                gripHeld = Mth.clamp(cursorY - ingredients.gripTop(), 0.0F, ingredients.gripHigh());
            } else if (button == 0) {
                // Beside the grip: on by as many rows as are shown, towards the click.
                scrollRows(cursorY < ingredients.gripTop() ? -ingredients.shown() : ingredients.shown());
            }
        } else if (overSearch && wanted) {
            if (searching()) {
                stopSearch(view);
            } else {
                rememberPage();
                typing = true;
                typingFrom = time;
            }
            turnSound();
        } else if (overSlip && wanted) {
            typing = true;
            typingFrom = time;
        } else if (onNothing()) {
            take(button);
        } else if (!wanted || onRibbon()) {
            toggle(view);
            putAway = !wanted;
        } else if (overFilter) {
            // The switch starts its list at the first page: the other list's page number means nothing in this one.
            flipFrom = leafNow();
            BookTabs.put(bookId() + ".ready", filtering() ? null : "true");
            keepOrder = false;
            read(view);
            page = 0;
            rememberPage();
            startFlip(1, false);
        } else if (hoveredTab != null) {
            if (!Objects.equals(tab, hoveredTab.kind())) {
                rememberPage();
                chosenTab = hoveredTab.kind();
                BookTabs.put(bookId(), chosenTab);
                flip = 1.0F;
                previewed = null;
                keepOrder = false;
                read(view);
                // The tab opens on the page the player left it at this game, else on its first page.
                Integer keptPage = PAGES.get(memoryKey());
                page = Mth.clamp(keptPage == null ? 0 : keptPage, 0, pages() - 1);
                // The right page shows the first of the kind until another is picked.
                if (picked == null && !entries.isEmpty()) {
                    picked = entries.get(0);
                }
                turnSound();
            }
        } else if (hoveredDrawn != null && drawnTarget != null && !overDish) {
            goTo(view, drawnTarget);
        } else if (overDish) {
            Entry shown = rightShown();
            if (shown != null) {
                pick(view, shown);
            }
        } else if (hovered != null) {
            pick(view, hovered);
        } else if (foot != 0) {
            // A click goes round the ends (on from the last page to the first); held, it leafs on and stops at the end.
            int before = page;
            turn(foot, true);
            boolean wrapped = foot > 0 ? page < before : page > before;
            leafing = wrapped ? 0 : foot;
            leafSince = time;
            leafDone = 1;
        }
    }

    /**
     * A click on a dish, on the list or at the head of the right page: the first picks it, to read it; a click on the dish picked so
     * goes on to make it from the inventory, and it joins the dishes made lately.
     */
    private void pick(StationView view, Entry entry) {
        boolean again = entry.page().key().equals(clicked);
        picked = entry;
        previewed = null;
        clicked = picked.page().key();
        PICKED.put(placeMemory(view), picked.page().key());
        PICKED.put(kindMemory(), picked.page().key());
        if (again) {
            source.picked(view, picked.page());
            made(picked.page());
        } else {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.4F, 0.3F));
        }
    }

    /**
     * Shows the recipe of a thing that goes in: picked as by a first click, and the list turned to it. A tab, the switch or the search
     * that hides it gives way to all dishes for now (the tab chosen stays kept); else the right page alone shows it.
     */
    private void goTo(StationView view, Page target) {
        Optional<Entry> listed = find(target.key());
        if (listed.isEmpty() && (tab != null || searching())) {
            rememberPage();
            endSearch();
            chosenTab = null;
            keepOrder = false;
            read(view);
            listed = find(target.key());
        }
        if (listed.isPresent()) {
            picked = listed.get();
            int at = entries.indexOf(picked) / PER_PAGE;
            if (at != page) {
                flipFrom = leafNow();
                int way = at > page ? 1 : -1;
                page = at;
                startFlip(way, false);
                rememberPage();
            } else {
                turnSound();
            }
        } else {
            picked = entry(target, pool(view));
            turnSound();
        }
        previewed = null;
        clicked = target.key();
        PICKED.put(placeMemory(view), target.key());
        PICKED.put(kindMemory(), target.key());
    }

    /** What the left page lists now. */
    private Leaf leafNow() {
        return new Leaf(entries, page);
    }

    /** A sheet starts to go over, {@code way} 1 forward, -1 back; quick ones (soon after the last) are quicker and softer. */
    private void startFlip(int way, boolean quick) {
        flip = 0.0F;
        flipWay = way > 0 ? 1 : -1;
        flipTime = quick ? FLIP_QUICK : FLIP_TIME;
        previewed = null;
        turnSound(quick);
    }

    /**
     * Turns the list's page by {@code by}, within the list or round its ends ({@code wrap}). A turn is never refused: one asked for
     * while a sheet is still going over lands that sheet and starts the next, quicker.
     */
    private void turn(int by, boolean wrap) {
        int next = BookMath.turned(page, by, pages(), wrap);
        if (next == page) {
            return;
        }
        flipFrom = leafNow();
        page = next;
        startFlip(by, time - lastTurn < QUICK_GAP);
        lastTurn = time;
        rememberPage();
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
        float eased = eased();
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
     * its back, and the page it uncovers lies ready under it, so nothing appears or goes away. The page turned away is the one that was
     * there when the turn started ({@code flipFrom}), whatever the list became since.
     */
    private void spread(PoseStack poseStack, MultiBufferSource buffers, float tilt, float height) {
        Leaf now = leafNow();
        if (flip >= 1.0F) {
            side(poseStack, buffers, true, tilt, height, now, true);
            side(poseStack, buffers, false, -tilt, height, now, true);
            return;
        }
        Leaf from = flipFrom == null ? now : flipFrom;
        float over = flip * flip * (3.0F - 2.0F * flip);
        // Forward the old list stays on the left until the sheet covers it with the new one; back the old list itself lifts off.
        boolean forward = flipWay > 0;
        side(poseStack, buffers, true, tilt, height, forward ? from : now, true);
        side(poseStack, buffers, false, -tilt, height, now, true);
        float right = -tilt;
        float left = tilt - Mth.PI;
        float turn = forward ? Mth.lerp(over, right, left) : Mth.lerp(over, left, right);
        float lifted = height + SHEET_LIFT;
        if (turn > -Mth.HALF_PI) {
            side(poseStack, buffers, false, turn, lifted, now, false);
        } else {
            side(poseStack, buffers, true, turn + Mth.PI, lifted, forward ? now : from, false);
        }
    }

    /**
     * One side of a sheet, turned about the fold: units run right and down as on a screen. {@code resting}: a page lying still, the
     * tabs and the switch stand on it, and it answers the cursor; a sheet going over shows what it holds, unlit.
     */
    private void side(PoseStack poseStack, MultiBufferSource buffers, boolean left, float turn, float height, Leaf leaf, boolean resting) {
        poseStack.pushPose();
        poseStack.translate(0.0F, height, 0.0F);
        poseStack.mulPose(new Quaternionf().rotationZ(turn));
        Matrix4f sheet = new Matrix4f(-UNIT, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -UNIT, 0.0F, 0.0F, UNIT, 0.0F, 0.0F, left ? PAGE_WIDTH : 0.0F, 0.0F, PAGE_HEIGHT / 2.0F, 1.0F);
        poseStack.last().pose().mul(sheet);
        Sheet canvas = new Sheet(poseStack, buffers);
        canvas.paper(left);
        canvas.rect(left ? UNITS_WIDE - FOLD_WIDTH : 0.0F, 0.0F, FOLD_WIDTH, UNITS_HIGH, 0.1F, FOLD);
        canvas.rect(left ? UNITS_WIDE - 2.0F : 0.0F, 0.0F, 2.0F, UNITS_HIGH, 0.15F, FOLD);
        boolean live = resting && (flip >= 1.0F || !left || leaf.page() == page);
        if (left) {
            if (resting) {
                drawTabs(canvas);
            }
            drawList(canvas, leaf, live);
        } else {
            if (resting) {
                // On, it is washed with the colour of what can be made, so it reads as a switch, not as one more kind of dish.
                float since = time - switchNodFrom;
                float nod = since >= 0.0F && since < NOD_TIME ? Math.abs(Mth.sin(since / NOD_TIME * Mth.TWO_PI)) * NOD_HEIGHT : 0.0F;
                boolean on = filtering();
                canvas.tab(FILTER_X, on, overFilter, filterIcon, nod, on);
                canvas.tab(SEARCH_X, searching(), overSearch, ItemStack.EMPTY, 0.0F, false);
                canvas.magnifier(SEARCH_X + (TAB_WIDE - 7.0F) / 2.0F, -(searching() ? TAB_HIGH : TAB_HIGH - TAB_DROP) + 3.0F);
                // The ribbon closes the book: it is there also while no dish is shown.
                canvas.ribbon(onRibbon());
            }
            Entry shown = rightShown();
            if (shown != null) {
                drawDish(canvas, shown, resting);
            } else if (resting) {
                drawn.clear();
                drawnFor = null;
                bar = null;
            }
        }
        poseStack.popPose();
    }

    /** The slip the search is typed on, over the list where the tabs were: the text, its tail when long, and a blinking caret. */
    private void drawSlip(Sheet canvas) {
        Font font = Minecraft.getInstance().font;
        float wide = UNITS_WIDE - 2.0F * TAB_MARGIN;
        canvas.slip(TAB_MARGIN, wide, SLIP_HIGH, overSlip && !typing);
        canvas.magnifier(TAB_MARGIN + 2.0F, -SLIP_HIGH + 2.0F);
        float room = wide - SLIP_TEXT_X - 3.0F;
        String shown = query;
        while (!shown.isEmpty() && font.width(shown) > room) {
            shown = shown.substring(1);
        }
        float textY = -SLIP_HIGH + 2.0F;
        canvas.text(shown, TAB_MARGIN + SLIP_TEXT_X, textY);
        if (typing && (time - typingFrom) % (CARET_BLINK * 2.0F) < CARET_BLINK) {
            canvas.rect(TAB_MARGIN + SLIP_TEXT_X + font.width(shown), textY - 1.0F, 1.0F, 9.0F, COVER_LIFT, INK);
        }
    }

    private void drawTabs(Sheet canvas) {
        if (searching()) {
            drawSlip(canvas);
            return;
        }
        for (int i = 0; i < tabs.size(); i++) {
            Tab shown = tabs.get(i);
            canvas.tab(tabsX() + i * tabPitch(), Objects.equals(shown.kind(), tab), shown == hoveredTab, shown.icon(), 0.0F, false);
        }
    }

    /** The list's page of {@code leaf}; {@code live}: the resting page, which answers the cursor. */
    private void drawList(Sheet canvas, Leaf leaf, boolean live) {
        List<Entry> list = leaf.list();
        int first = leaf.page() * PER_PAGE;
        Entry pointed = hovered != null ? hovered : previewed;
        for (int i = first; i < Math.min(list.size(), first + PER_PAGE); i++) {
            Entry entry = list.get(i);
            int x = GRID_X + (i - first) % COLUMNS * PITCH;
            int y = GRID_Y + (i - first) / COLUMNS * PITCH;
            canvas.rect(x, y, WELL_SIZE, WELL_SIZE, WELL_LIFT, entry.isReady() ? WELL_READY : WELL);
            canvas.item(entry.dish(), x + 0.5F, y + 0.5F, 16.0F, !entry.isReady());
            if (entry == picked || entry == pointed) {
                canvas.frame(x, y, WELL_SIZE);
            }
        }
        int leafPages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        if (leafPages > 1) {
            // "7/52" between the arrows, smaller when it would not fit; the arrow of the half under the cursor is lit.
            Font font = Minecraft.getInstance().font;
            String number = (leaf.page() + 1) + "/" + leafPages;
            float wide = font.width(number) - 1.0F;
            float scale = Math.min(1.0F, NUMBER_ROOM / wide);
            float high = (font.lineHeight - 2.0F) * scale;
            canvas.text(number, FOOT_MIDDLE - wide * scale / 2.0F, ARROW_Y + (ARROW_HEIGHT - high) / 2.0F, scale);
            int under = live ? footSide() : 0;
            canvas.arrow(GRID_X, ARROW_Y, false, under < 0);
            canvas.arrow(GRID_X + LIST_WIDE - ARROW_WIDTH, ARROW_Y, true, under > 0);
        }
    }

    /**
     * The dish, its name, its stars, what goes in, how long it takes and what it is served in. {@code live}: the resting page, which
     * answers the cursor and is kept for it.
     */
    private void drawDish(Sheet canvas, Entry entry, boolean live) {
        Font font = Minecraft.getInstance().font;
        float middle = RIGHT_MIDDLE;
        List<Drawn> laid = new ArrayList<>();
        canvas.item(entry.dish(), middle - DISH_SIZE / 2.0F, DISH_Y, DISH_SIZE);
        laid.add(new Drawn(middle - DISH_SIZE / 2.0F, DISH_Y, DISH_SIZE, entry.dish(), -1));
        if (live && overDish) {
            // The dish is picked and made at a click, as on the list.
            canvas.frame(middle - DISH_SIZE / 2.0F - 0.5F, DISH_Y - 0.5F, DISH_SIZE + 1.0F);
        }
        float line = 25.0F;
        // Two lines at most: a long modded name must leave room for what goes in.
        if (nameFor != entry) {
            nameFor = entry;
            nameLines = font.split(entry.dish().getHoverName(), Math.round(2.0F * (UNITS_WIDE - middle) - 6.0F));
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
        float rowWide = COLUMNS * PITCH - (PITCH - WELL_SIZE);
        canvas.rect(middle - rowWide / 2.0F, line + 0.5F, rowWide, 0.6F, WELL_LIFT, RULE);
        int count = entry.ingredients().size();
        long second = Util.getMillis() / 1000L;
        float top = line + 3.0F;
        // The small print goes where there is room left, in the middle, on the line of the list's arrows.
        float footMiddle = ARROW_Y + ARROW_HEIGHT / 2.0F;
        float bottom = footMiddle - FOOT_SIZE / 2.0F;
        int rows = (count + 2) / 3;
        // As many rows as fit over the small print; with fewer than two there, the small print gives way to the ingredients. Rows
        // that still do not fit are reached by the bar beside them, a whole row at a time.
        int overFoot = Math.max(0, Mth.floor((bottom - top + PITCH - WELL_SIZE) / PITCH));
        boolean withFoot = rows <= overFoot || overFoot >= 2;
        int shown = withFoot ? Math.min(rows, overFoot) : Math.max(1, Math.min(rows, Mth.floor((PAGE_FOOT - top + PITCH - WELL_SIZE) / PITCH)));
        if (!Objects.equals(rowsFor, entry.page().key())) {
            if (picked != null && Objects.equals(rowsFor, picked.page().key())) {
                pickedRowFor = rowsFor;
                pickedRow = firstRow;
            }
            rowsFor = entry.page().key();
            firstRow = Objects.equals(pickedRowFor, rowsFor) ? pickedRow : 0;
        }
        firstRow = Mth.clamp(firstRow, 0, rows - shown);
        if (shown < rows) {
            float high = shown * PITCH - (PITCH - WELL_SIZE);
            float gripHigh = Math.max(BAR_LEAST, high * shown / rows);
            float gripTop = top + (high - gripHigh) * firstRow / (rows - shown);
            Bar shownBar = new Bar(rows, shown, firstRow, top, high, gripTop, gripHigh);
            if (live) {
                bar = shownBar;
            }
            canvas.rect(BAR_X, top, BAR_WIDE, high, WELL_LIFT, BAR_TRACK);
            boolean lit = live && (gripHeld >= 0.0F || cursorX >= UNITS_WIDE && shownBar.onGrip(cursorX - UNITS_WIDE, cursorY));
            canvas.rect(BAR_X, gripTop, BAR_WIDE, gripHigh, WELL_LIFT + 0.05F, lit ? WELL_PICKED : BAR_GRIP);
        } else if (live) {
            bar = null;
        }
        for (int i = firstRow * 3; i < Math.min(count, (firstRow + shown) * 3); i++) {
            int inRow = Math.min(3, count - i / 3 * 3);
            float x = middle - (inRow * PITCH - (PITCH - WELL_SIZE)) / 2.0F + i % 3 * PITCH;
            float y = top + (i / 3 - firstRow) * PITCH;
            ItemStack[] choices = entry.ingredients().get(i).getItems();
            if (choices.length > 0) {
                canvas.rect(x, y, WELL_SIZE, WELL_SIZE, WELL_LIFT, entry.found()[i] ? WELL_READY : WELL);
                // Under the cursor it keeps the choice it showed when the cursor came; what the book makes lights, a click shows it.
                boolean pointed = live && hoveredDrawn != null && hoveredDrawn.ingredient() == i && drawnOf == entry;
                ItemStack choice = pointed ? drawnShown : choices[(int) (second % choices.length)];
                canvas.item(choice, x + 0.5F, y + 0.5F, 16.0F, !entry.found()[i]);
                if (pointed && drawnTarget != null) {
                    canvas.frame(x, y, WELL_SIZE);
                }
                laid.add(new Drawn(x, y, WELL_SIZE, choice, i));
            }
        }
        if (withFoot) {
            List<ItemStack> foot = entry.page().foot();
            FormattedCharSequence note = entry.page().note() == null ? null : entry.page().note().getVisualOrderText();
            // The note stands close after the last thing, as a time after its clock.
            float noteWide = note == null ? 0.0F : font.width(note) + 1.0F;
            float wide = foot.isEmpty() ? noteWide : (foot.size() - 1) * FOOT_PITCH + FOOT_SIZE + noteWide;
            float x = middle - wide / 2.0F;
            for (ItemStack small : foot) {
                canvas.item(small, x, bottom, FOOT_SIZE);
                laid.add(new Drawn(x, bottom, FOOT_SIZE, small, -1));
                x += FOOT_PITCH;
            }
            if (note != null) {
                float noteX = foot.isEmpty() ? x : x - FOOT_PITCH + FOOT_SIZE + 1.0F;
                canvas.text(note, noteX, footMiddle - font.lineHeight / 2.0F + 1.0F);
            }
        }
        if (live) {
            drawn.clear();
            drawn.addAll(laid);
            drawnFor = entry;
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
        void tab(float x, boolean chosen, boolean lit, ItemStack icon, float nod, boolean washed) {
            float high = chosen ? TAB_HIGH : TAB_HIGH - TAB_DROP;
            int paper = chosen ? 0xFFFFFFFF : lit ? 0xFFEDE4D0 : 0xFFD6C9AC;
            // Down into the page by a unit: the chosen one runs on into it, with no edge between.
            float bottom = chosen ? 1.0F : 0.0F;
            float u0 = TAB_PAPER_U / PAPER_SIZE;
            float v0 = TAB_PAPER_V / PAPER_SIZE;
            quad(buffers.getBuffer(RenderType.text(PAPER)), x, -high, x + TAB_WIDE, bottom, chosen ? 0.05F : 0.0F, paper, u0,
                    u0 + TAB_WIDE / PAPER_SIZE, v0, v0 + (bottom + high) / PAPER_SIZE);
            if (washed) {
                rect(x + 1.0F, -high + 1.0F, TAB_WIDE - 2.0F, high - 1.0F + bottom, 0.08F, WELL_READY);
            }
            rect(x + 1.0F, -high, TAB_WIDE - 2.0F, 1.0F, 0.1F, TAB_EDGE);
            rect(x, -high + 1.0F, 1.0F, high - 1.0F, 0.1F, TAB_EDGE);
            rect(x + TAB_WIDE - 1.0F, -high + 1.0F, 1.0F, high - 1.0F, 0.1F, TAB_EDGE);
            if (!icon.isEmpty()) {
                item(icon, x + (TAB_WIDE - TAB_ICON) / 2.0F, -high + 2.0F - nod, TAB_ICON, !chosen && !lit && !washed);
            }
        }

        /** The search's slip: the page's own paper standing over its top edge and running on into it, as the chosen tab does. */
        void slip(float x, float wide, float high, boolean lit) {
            float u0 = TAB_PAPER_U / PAPER_SIZE;
            float v0 = TAB_PAPER_V / PAPER_SIZE;
            quad(buffers.getBuffer(RenderType.text(PAPER)), x, -high, x + wide, 1.0F, 0.05F, lit ? 0xFFF6F0E2 : 0xFFFFFFFF, u0,
                    u0 + wide / PAPER_SIZE, v0, v0 + (high + 1.0F) / PAPER_SIZE);
            rect(x + 1.0F, -high, wide - 2.0F, 1.0F, 0.1F, TAB_EDGE);
            rect(x, -high + 1.0F, 1.0F, high - 1.0F, 0.1F, TAB_EDGE);
            rect(x + wide - 1.0F, -high + 1.0F, 1.0F, high - 1.0F, 0.1F, TAB_EDGE);
        }

        /** A magnifying glass in ink, 7 units square: a ring and its handle going down to the right. */
        void magnifier(float x, float y) {
            rect(x + 1.0F, y, 3.0F, 1.0F, COVER_LIFT, INK);
            rect(x + 1.0F, y + 4.0F, 3.0F, 1.0F, COVER_LIFT, INK);
            rect(x, y + 1.0F, 1.0F, 3.0F, COVER_LIFT, INK);
            rect(x + 4.0F, y + 1.0F, 1.0F, 3.0F, COVER_LIFT, INK);
            rect(x + 4.0F, y + 4.0F, 1.0F, 1.0F, COVER_LIFT, INK);
            rect(x + 5.0F, y + 5.0F, 2.0F, 1.0F, COVER_LIFT, INK);
            rect(x + 6.0F, y + 6.0F, 1.0F, 1.0F, COVER_LIFT, INK);
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

        /**
         * A page arrow in the ink of the page, warmer under the cursor. Drawn here, not taken from vanilla's book screen: a resource
         * pack restyles that one for its own pages (a dark one makes it light), and on this paper it could not be seen.
         */
        void arrow(float x, float y, boolean next, boolean over) {
            int colour = over ? WELL_PICKED : INK;
            int middle = ARROW_HEIGHT / 2;
            for (int row = 0; row < ARROW_HEIGHT; row++) {
                int off = Math.abs(row - middle);
                // The head: from its base to the tip, one unit shorter each row away from the middle; the shaft behind it.
                float from = off <= ARROW_SHAFT ? 1 : ARROW_HEAD;
                float to = ARROW_WIDTH - 1 - off;
                if (to > from) {
                    float left = next ? x + from : x + ARROW_WIDTH - to;
                    rect(left, y + row, to - from, 1.0F, WELL_LIFT, colour);
                }
            }
        }

        private void quad(VertexConsumer consumer, float x0, float y0, float x1, float y1, float lift, int argb, float u0, float u1, float v0, float v1) {
            Matrix4f matrix = poseStack.last().pose();
            int a = argb >>> 24;
            int r = argb >> 16 & 0xFF;
            int g = argb >> 8 & 0xFF;
            int b = argb & 0xFF;
            consumer.addVertex(matrix, x0, y0, lift).setColor(r, g, b, a).setUv(u0, v0).setLight(shade);
            consumer.addVertex(matrix, x0, y1, lift).setColor(r, g, b, a).setUv(u0, v1).setLight(shade);
            consumer.addVertex(matrix, x1, y1, lift).setColor(r, g, b, a).setUv(u1, v1).setLight(shade);
            consumer.addVertex(matrix, x1, y0, lift).setColor(r, g, b, a).setUv(u1, v0).setLight(shade);
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
            Matrix3f facing = new Matrix3f(poseStack.last().normal());
            poseStack.scale(size, -size, model.isGui3d() ? BLOCK_DEPTH : ITEM_DEPTH);
            // As in the GUI, the flip and the depth leave the normals alone: IconLight lights the faces as the GUI does.
            poseStack.last().normal().set(facing);
            // The icon looks as in a GUI, but is drawn as no GUI item: mods that decorate every GUI item (Shadow Drop's shadows and
            // its shift) take its world pose for a screen's and move or cover the icon.
            BakedModel shown = ClientPlatform.INSTANCE.guiModel(model, poseStack);
            MultiBufferSource lit = IconLight.of(buffers, facing, model.usesBlockLight());
            renderer.render(stack, ItemDisplayContext.NONE, false, poseStack, faded ? DiegesisClient.tinted(lit, FADED) : lit, shade,
                    OverlayTexture.NO_OVERLAY, shown);
            poseStack.popPose();
        }

        void text(String text, float x, float y) {
            text(Component.literal(text).getVisualOrderText(), x, y);
        }

        /** Text drawn smaller ({@code scale} under 1) from its top left corner: scaled as a whole, never wrapped. */
        void text(String text, float x, float y, float scale) {
            Matrix4f matrix = new Matrix4f(poseStack.last().pose()).translate(x, y, ITEM_LIFT).scale(scale, scale, 1.0F);
            Minecraft.getInstance().font.drawInBatch(Component.literal(text).getVisualOrderText(), 0.0F, 0.0F, INK, false, matrix, buffers,
                    Font.DisplayMode.NORMAL, 0, shade);
        }

        void text(FormattedCharSequence text, float x, float y) {
            Matrix4f matrix = new Matrix4f(poseStack.last().pose()).translate(0.0F, 0.0F, ITEM_LIFT);
            Minecraft.getInstance().font.drawInBatch(text, x, y, INK, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, shade);
        }
    }
}
