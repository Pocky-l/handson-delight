package com.pockyl.handson_delight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.NodePainter;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.handson_delight.station.CookingPotStation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The pot's recipes in a book hanging in the air beside it: the flying book of the enchanting table, opened out, with the recipes on its
 * pages, so the player reads and stirs at once. The page on the left lists the dishes, what can be cooked now first; the page on the
 * right shows the dish under the cursor or the one picked. Pages are measured in units of about a GUI pixel.
 */
final class RecipeBook implements NodePainter {
    @SuppressWarnings("removal")
    private static final ResourceLocation PAPER = new ResourceLocation("textures/gui/book.png");
    @SuppressWarnings("removal")
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "block/white_concrete");
    private static final Quaternionf LYING = new Matrix3f(0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F).getNormalizedRotation(new Quaternionf());
    /** How much bigger than on the enchanting table. */
    private static final float SIZE = 0.72F;
    private static final float PAGE_WIDTH = 5.0F * SIZE;
    private static final float PAGE_HEIGHT = 8.0F * SIZE;
    private static final int UNITS_WIDE = 60;
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
    private static final int GRID_X = 4;
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
    /** The paper of the page in the texture: its clean middle. */
    private static final float PAPER_U0 = 34.0F / 256.0F;
    private static final float PAPER_U1 = 152.0F / 256.0F;
    private static final float PAPER_V0 = 14.0F / 256.0F;
    private static final float PAPER_V1 = 168.0F / 256.0F;
    private static final float REFRESH_TIME = 1.0F;
    /** How small it is closed. */
    private static final float CLOSED_SIZE = 0.62F;
    /** Closed, it shows its cover: the model turned about the spine, the spine moved to the left of the middle. */
    private static final float CLOSED_TURN = Mth.HALF_PI;
    private static final float CLOSED_SHIFT = 3.0F / 16.0F;
    /** The ribbon that closes the book, at the top of the right page. */
    private static final int RIBBON_X = 50;
    private static final int RIBBON_WIDTH = 6;
    private static final int RIBBON_HEIGHT = 13;
    private static final int RIBBON = 0xFFB5372B;
    private static final int RIBBON_LIT = 0xFFD9503E;
    /** The shade in the fold of the pages. */
    private static final int FOLD = 0x285A3C18;
    private static final int FOLD_WIDTH = 5;

    private BookModel model;
    private List<Entry> entries = List.of();
    private boolean wanted;
    private float open;
    private float flip = 1.0F;
    private int flipWay = 1;
    private int page;
    private Entry picked;
    private Entry hovered;
    private float refresh;
    private float reach;
    private float time;
    /** Where the cursor is on the left page (units), or on the right one past its width; negative: not on the book. */
    private float cursorX = -1.0F;
    private float cursorY = -1.0F;
    private StationView station;

    /** A dish, its ingredients and which of them the player has. */
    private record Entry(CookingPotRecipe recipe, ItemStack dish, List<Ingredient> ingredients, boolean[] found, int missing) {
        boolean isReady() {
            return missing == 0;
        }
    }

    boolean isOpen() {
        return wanted;
    }

    void toggle(StationView view) {
        station = view;
        wanted = !wanted;
        if (wanted) {
            read(view);
            page = 0;
            picked = entries.isEmpty() ? null : entries.get(0);
            turnSound();
        } else {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PUT, 1.0F, 0.5F));
        }
    }

    /** The player left the station. */
    void shut() {
        wanted = false;
        open = 0.0F;
    }

    private static void turnSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.6F));
    }

    private void read(StationView view) {
        Level level = view.level();
        List<ItemStack> pool = pool(view);
        List<Entry> found = new ArrayList<>();
        for (CookingPotRecipe recipe : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.COOKING.get())) {
            found.add(entry(recipe, level, pool));
        }
        found.sort(Comparator.comparingInt(Entry::missing).thenComparing(entry -> entry.dish().getHoverName().getString()));
        if (picked != null) {
            CookingPotRecipe kept = picked.recipe();
            picked = found.stream().filter(entry -> entry.recipe() == kept).findFirst().orElse(null);
        }
        entries = found;
    }

    /** What the player has to cook with: the inventory, and what already lies in the pot. */
    private static List<ItemStack> pool(StationView view) {
        List<ItemStack> pool = new ArrayList<>();
        var player = Minecraft.getInstance().player;
        if (player != null) {
            for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                pool.add(player.getInventory().getItem(i));
            }
        }
        pool.addAll(List.of(view.state().getItems(CookingPotStation.INGREDIENTS)));
        pool.removeIf(ItemStack::isEmpty);
        return pool;
    }

    private static Entry entry(CookingPotRecipe recipe, Level level, List<ItemStack> pool) {
        int[] left = pool.stream().mapToInt(ItemStack::getCount).toArray();
        List<Ingredient> ingredients = recipe.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).toList();
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
        return new Entry(recipe, recipe.getResultItem(level.registryAccess()), ingredients, found, missing);
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
        node.setVisible(view.isFocused());
        time += dt;
        open = Mth.clamp(open + (wanted ? dt : -dt) / OPEN_TIME, 0.0F, 1.0F);
        flip = Math.min(1.0F, flip + dt / FLIP_TIME);
        // Grows and opens where it hangs, breathing a little.
        float eased = open * open * (3.0F - 2.0F * open);
        reach = Mth.lerp(Math.min(1.0F, dt * 12.0F), reach, node.isHovered() && !wanted ? 1.0F : 0.0F);
        node.scaleTo(Mth.lerp(eased, CLOSED_SIZE * (1.0F + 0.08F * reach), 1.0F), 0.0F);
        node.moveTo(0.0F, Mth.sin(time * 1.3F) * 0.14F, 0.0F, 0.0F);
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
        if (cursor.isEmpty() || flip < 1.0F) {
            return;
        }
        Vector3f point = cursor.get();
        float y = (PAGE_HEIGHT / 2.0F - point.z) / UNIT;
        // The left page runs from its edge at +x to the fold at 0; the right one on from there.
        float x = point.x > 0.0F ? (PAGE_WIDTH - point.x) / UNIT : UNITS_WIDE - point.x / UNIT;
        if (x >= 0.0F && x < UNITS_WIDE * 2 && y >= 0.0F && y < UNITS_HIGH) {
            cursorX = x;
            cursorY = y;
            hovered = entryAt(x, y);
        }
    }

    private Entry entryAt(float x, float y) {
        int column = Mth.floor((x - GRID_X) / PITCH);
        int row = Mth.floor((y - GRID_Y) / PITCH);
        int index = page * PER_PAGE + row * COLUMNS + column;
        return column >= 0 && column < COLUMNS && row >= 0 && row < ROWS && index < entries.size() ? entries.get(index) : null;
    }

    private boolean onRibbon() {
        return cursorX >= UNITS_WIDE + RIBBON_X && cursorX < UNITS_WIDE + RIBBON_X + RIBBON_WIDTH && cursorY >= 0.0F && cursorY < RIBBON_HEIGHT;
    }

    /** A click on the book: opens it; open it picks a dish, turns the page, or closes by its ribbon. */
    void click(StationView view) {
        if (!wanted || onRibbon()) {
            toggle(view);
        } else if (hovered != null) {
            picked = hovered;
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

    @Override
    public void paint(PoseStack poseStack, MultiBufferSource buffers, int light) {
        if (model == null) {
            model = new BookModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.BOOK));
        }
        float eased = open * open * (3.0F - 2.0F * open);
        float closed = 1.0F - eased;
        poseStack.pushPose();
        poseStack.mulPose(LYING);
        poseStack.scale(16.0F * SIZE, 16.0F * SIZE, 16.0F * SIZE);
        poseStack.mulPose(new Quaternionf().rotationY(CLOSED_TURN * closed));
        poseStack.translate(-CLOSED_SHIFT * closed, 0.0F, 0.0F);
        model.setupAnim(0.0F, 0.0F, 1.0F, OPEN * eased);
        VertexConsumer cover = EnchantTableRenderer.BOOK_LOCATION.buffer(buffers, RenderType::entitySolid);
        model.render(poseStack, cover, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
        if (open > 0.0F) {
            // The sheets go with the model: the same turn from closed, the same angle of the pages all the way open.
            poseStack.pushPose();
            poseStack.mulPose(new Quaternionf().rotationZ(CLOSED_TURN * closed));
            poseStack.translate(0.0F, -CLOSED_SHIFT * 16.0F * SIZE * closed, 0.0F);
            float angle = 1.25F * OPEN * eased;
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
            side(poseStack, buffers, true, tilt, height, page);
            side(poseStack, buffers, false, -tilt, height, page);
            return;
        }
        float over = flip * flip * (3.0F - 2.0F * flip);
        // Forward the old list stays on the left until the sheet covers it with the new one; back the old list itself lifts off.
        boolean forward = flipWay > 0;
        side(poseStack, buffers, true, tilt, height, forward ? page - 1 : page);
        side(poseStack, buffers, false, -tilt, height, page);
        float right = -tilt;
        float left = tilt - Mth.PI;
        float turn = forward ? Mth.lerp(over, right, left) : Mth.lerp(over, left, right);
        float lifted = height + SHEET_LIFT;
        if (turn > -Mth.HALF_PI) {
            side(poseStack, buffers, false, turn, lifted, page);
        } else {
            side(poseStack, buffers, true, turn + Mth.PI, lifted, forward ? page : page + 1);
        }
    }

    /** One side of a sheet, turned about the fold: units run right and down as on a screen. */
    private void side(PoseStack poseStack, MultiBufferSource buffers, boolean left, float turn, float height, int listPage) {
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
            drawList(canvas, listPage);
        } else {
            Entry shown = hovered != null ? hovered : picked;
            if (shown != null) {
                drawDish(canvas, shown);
            }
        }
        poseStack.popPose();
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

    /** The dish, its name, what goes in, how long it takes and what it is served in. */
    private void drawDish(Sheet canvas, Entry entry) {
        Font font = Minecraft.getInstance().font;
        float middle = UNITS_WIDE / 2.0F;
        canvas.ribbon(onRibbon());
        canvas.item(entry.dish(), middle - 13.0F, 2.0F, 26.0F);
        float line = 29.0F;
        for (FormattedCharSequence text : font.split(entry.dish().getHoverName(), UNITS_WIDE - 6)) {
            canvas.text(text, middle - font.width(text) / 2.0F, line);
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
        float bottom = UNITS_HIGH - 14.0F;
        if (top + (count + 2) / 3 * PITCH <= bottom) {
            ItemStack container = entry.recipe().getOutputContainer();
            if (!container.isEmpty()) {
                canvas.item(container, 5.0F, bottom, 12.0F);
            }
            canvas.item(new ItemStack(Items.CLOCK), 22.0F, bottom, 12.0F);
            canvas.text(Component.translatable("handson_delight.book.seconds", entry.recipe().getCookTime() / 20).getVisualOrderText(), 35.0F, bottom + 2.0F);
        }
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
            quad(consumer, 0.0F, 0.0F, UNITS_WIDE, UNITS_HIGH, 0.0F, 0xFFFFFFFF, mirrored ? PAPER_U1 : PAPER_U0, mirrored ? PAPER_U0 : PAPER_U1, PAPER_V0, PAPER_V1);
        }

        void rect(float x, float y, float width, float height, float lift, int argb) {
            VertexConsumer consumer = buffers.getBuffer(RenderType.text(TextureAtlas.LOCATION_BLOCKS));
            quad(consumer, x, y, x + width, y + height, lift, argb, white.getU0(), white.getU1(), white.getV0(), white.getV1());
        }

        void ribbon(boolean lit) {
            int color = lit ? RIBBON_LIT : RIBBON;
            rect(RIBBON_X, 0.0F, RIBBON_WIDTH, RIBBON_HEIGHT - 3.0F, COVER_LIFT, color);
            rect(RIBBON_X, RIBBON_HEIGHT - 3.0F, 2.0F, 3.0F, COVER_LIFT, color);
            rect(RIBBON_X + RIBBON_WIDTH - 2.0F, RIBBON_HEIGHT - 3.0F, 2.0F, 3.0F, COVER_LIFT, color);
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
            quad(buffers.getBuffer(RenderType.text(PAPER)), x, y, x + ARROW_WIDTH, y + ARROW_HEIGHT, WELL_LIFT, 0xFFFFFFFF, u, u + ARROW_WIDTH / 256.0F, v / 256.0F,
                    (v + ARROW_HEIGHT) / 256.0F);
        }

        private void quad(VertexConsumer consumer, float x0, float y0, float x1, float y1, float lift, int argb, float u0, float u1, float v0, float v1) {
            Matrix4f matrix = poseStack.last().pose();
            int a = argb >>> 24;
            int r = argb >> 16 & 0xFF;
            int g = argb >> 8 & 0xFF;
            int b = argb & 0xFF;
            consumer.vertex(matrix, x0, y0, lift).color(r, g, b, a).uv(u0, v0).uv2(LightTexture.FULL_BRIGHT).endVertex();
            consumer.vertex(matrix, x0, y1, lift).color(r, g, b, a).uv(u0, v1).uv2(LightTexture.FULL_BRIGHT).endVertex();
            consumer.vertex(matrix, x1, y1, lift).color(r, g, b, a).uv(u1, v1).uv2(LightTexture.FULL_BRIGHT).endVertex();
            consumer.vertex(matrix, x1, y0, lift).color(r, g, b, a).uv(u1, v0).uv2(LightTexture.FULL_BRIGHT).endVertex();
        }

        void item(ItemStack stack, float x, float y, float size) {
            item(stack, x, y, size, false);
        }

        /** {@code faded}: what the player does not have yet, the icon itself see-through, as nothing laid over it would stay in its place. */
        void item(ItemStack stack, float x, float y, float size, boolean faded) {
            poseStack.pushPose();
            poseStack.translate(x + size / 2.0F, y + size / 2.0F, ITEM_LIFT);
            boolean block = Minecraft.getInstance().getItemRenderer().getModel(stack, station.level(), null, 0).isGui3d();
            poseStack.scale(size, -size, block ? BLOCK_DEPTH : ITEM_DEPTH);
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.GUI, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, poseStack,
                    faded ? DiegesisClient.tinted(buffers, FADED) : buffers, station.level(), 0);
            poseStack.popPose();
        }

        void text(String text, float x, float y) {
            text(Component.literal(text).getVisualOrderText(), x, y);
        }

        void text(FormattedCharSequence text, float x, float y) {
            Matrix4f matrix = new Matrix4f(poseStack.last().pose()).translate(0.0F, 0.0F, ITEM_LIFT);
            Minecraft.getInstance().font.drawInBatch(text, x, y, INK, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        }
    }
}
