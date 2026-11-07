package com.pockyl.handson_delight.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/** The colour food gives a broth: the average of its icon's visible pixels, so modded food needs no table. */
final class FoodColors {
    private static final Map<Item, Integer> CACHE = new HashMap<>();
    private static final int FALLBACK = 0xB0A070;
    private static final int HUES = 12;
    private static final Map<Item, Integer> MAIN = new HashMap<>();
    private static final Map<Item, Integer> BROTH = new HashMap<>();
    /** Where the bowl starts on a dish's icon, as a part of its height. */
    private static final float BOWL_TOP = 0.6F;
    private static final float GREY = 0.14F;
    private static final float WARM_HUE = 0.1F;
    private static final float MIN_SATURATION = 0.38F;
    private static final float MIN_BRIGHTNESS = 0.72F;

    private FoodColors() {
    }

    /** RGB of a stack's icon. */
    static int of(ItemStack stack) {
        return CACHE.computeIfAbsent(stack.getItem(), item -> sample(stack));
    }

    private static int sample(ItemStack stack) {
        SpriteContents contents = Minecraft.getInstance().getItemRenderer().getModel(stack, null, null, 0).getParticleIcon().contents();
        NativeImage image = contents.getOriginalImage();
        long red = 0;
        long green = 0;
        long blue = 0;
        long weight = 0;
        for (int y = 0; y < contents.height(); y++) {
            for (int x = 0; x < contents.width(); x++) {
                int abgr = image.getPixelRGBA(x, y);
                if ((abgr >>> 24) < 128) {
                    continue;
                }
                int r = abgr & 0xFF;
                int g = abgr >> 8 & 0xFF;
                int b = abgr >> 16 & 0xFF;
                // Colourful pixels say more about food than its grey outline does.
                int vivid = 1 + Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
                red += (long) r * vivid;
                green += (long) g * vivid;
                blue += (long) b * vivid;
                weight += vivid;
            }
        }
        return weight == 0 ? FALLBACK : (int) (red / weight) << 16 | (int) (green / weight) << 8 | (int) (blue / weight);
    }

    /**
     * The colour a stack is mostly made of: the biggest family of hues on its icon, brightened a little, so that the green top of a
     * carrot does not turn the carrot brown as an average would.
     */
    static int main(ItemStack stack) {
        return MAIN.computeIfAbsent(stack.getItem(), item -> sampleMain(stack, 1.0F));
    }

    /** The colour of a soup: of what is in its bowl, the upper part of the icon; the wood of the bowl would turn every soup brown. */
    static int broth(ItemStack meal) {
        return BROTH.computeIfAbsent(meal.getItem(), item -> sampleMain(meal, BOWL_TOP));
    }

    private static int sampleMain(ItemStack stack, float rows) {
        SpriteContents contents = Minecraft.getInstance().getItemRenderer().getModel(stack, null, null, 0).getParticleIcon().contents();
        NativeImage image = contents.getOriginalImage();
        long[][] hues = new long[HUES][4];
        for (int y = 0; y < contents.height() * rows; y++) {
            for (int x = 0; x < contents.width(); x++) {
                int abgr = image.getPixelRGBA(x, y);
                if ((abgr >>> 24) < 128) {
                    continue;
                }
                int r = abgr & 0xFF;
                int g = abgr >> 8 & 0xFF;
                int b = abgr >> 16 & 0xFF;
                float[] hsb = Color.RGBtoHSB(r, g, b, null);
                // The outline of an icon is its darkest part, and says nothing of what it shows.
                if (hsb[2] < 0.3F) {
                    continue;
                }
                // Colour counts for more than grey: a shadow on meat is no colour of its own.
                int weight = 1 + Math.round(hsb[1] * 4.0F);
                long[] family = hues[Math.min(HUES - 1, (int) (hsb[0] * HUES))];
                family[0] += (long) r * weight;
                family[1] += (long) g * weight;
                family[2] += (long) b * weight;
                family[3] += weight;
            }
        }
        long[] best = hues[0];
        for (long[] family : hues) {
            best = family[3] > best[3] ? family : best;
        }
        if (best[3] == 0) {
            return FALLBACK;
        }
        float[] hsb = Color.RGBtoHSB((int) (best[0] / best[3]), (int) (best[1] / best[3]), (int) (best[2] / best[3]), null);
        // Food is warm and full of colour: what comes out grey is given the tone of something cooked.
        float hue = hsb[1] < GREY ? WARM_HUE : hsb[0];
        float saturation = Math.max(MIN_SATURATION, Math.min(1.0F, hsb[1] * 1.3F));
        float brightness = Math.max(MIN_BRIGHTNESS, Math.min(1.0F, hsb[2] * 1.2F));
        return Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
    }

    static boolean isGreen(int rgb) {
        float hue = Color.RGBtoHSB(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, null)[0];
        return hue > 0.2F && hue < 0.47F;
    }

    /** {@code rgb} moved towards white by {@code amount}, 0 to 1. */
    static int lighter(int rgb, float amount) {
        int result = 0;
        for (int shift = 0; shift < 24; shift += 8) {
            int value = rgb >> shift & 0xFF;
            result |= Math.round(value + (255 - value) * amount) << shift;
        }
        return result;
    }

    /** The average of several RGB colours, or the fallback for none. */
    static int mix(Iterable<ItemStack> stacks) {
        int red = 0;
        int green = 0;
        int blue = 0;
        int count = 0;
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                int color = of(stack);
                red += color >> 16 & 0xFF;
                green += color >> 8 & 0xFF;
                blue += color & 0xFF;
                count++;
            }
        }
        return count == 0 ? FALLBACK : red / count << 16 | green / count << 8 | blue / count;
    }
}
