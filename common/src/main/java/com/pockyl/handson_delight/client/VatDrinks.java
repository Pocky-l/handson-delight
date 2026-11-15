package com.pockyl.handson_delight.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.pockyl.diegesis.api.client.DiegesisParticles;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * How Cultural Delights' drinks look in the vat, as the keg's drinks look in the keg ({@link KegLiquids}): a beer golden under a head
 * of foam with bubbles rising, a wine deep red, vodka and gin clear, a cola dark and fizzing, glow wine shining. Colours follow its
 * own items; the alpha is how clear the drink is. A drink nobody describes here takes the main colour of its icon.
 */
final class VatDrinks {
    private static final Map<ResourceLocation, KegLiquids.Drink> DRINKS = new HashMap<>();

    static {
        String cd = "culturaldelights";
        drink(cd, "beer", KegLiquids.Drink.of(0xD0D49A3C).foam(0.55F, KegLiquids.HEAD).fizz(1.6F).asBrew());
        drink(cd, "apple_cider", KegLiquids.Drink.of(0xC8DC9030).foam(0.18F, KegLiquids.HEAD).fizz(1.0F).asBrew());
        drink(cd, "ginger_beer", KegLiquids.Drink.of(0xC8E2B44E).foam(0.32F, KegLiquids.HEAD).fizz(1.8F).asBrew());
        drink(cd, "mead", KegLiquids.Drink.of(0xD8F2A21E).fizz(0.3F).thick(0.25F).asBrew());
        drink(cd, "wine", KegLiquids.Drink.of(0xE86E1426).thick(0.05F).asBrew());
        drink(cd, "glow_wine", KegLiquids.Drink.of(0xD8E8B030).glowing().giving(DiegesisParticles.GLINT, 0xFFFFE6A0, 2.0F).asBrew());
        drink(cd, "vinegar", KegLiquids.Drink.of(0xB0A04A24));
        drink(cd, "acid", KegLiquids.Drink.of(0xB89CE22C).glowing().giving(DiegesisParticles.SMOKE, 0x809CE22C, 0.8F));
        drink(cd, "gin", KegLiquids.Drink.of(0x48E6F0EC));
        drink(cd, "vodka", KegLiquids.Drink.of(0x40DDEEE9));
        drink(cd, "rum", KegLiquids.Drink.of(0xD8B0602A).thick(0.1F));
        drink(cd, "tequila", KegLiquids.Drink.of(0xA8E8C870));
        drink(cd, "whiskey", KegLiquids.Drink.of(0xE09A5420).thick(0.05F));
        drink(cd, "brandy", KegLiquids.Drink.of(0xE0883A1E).thick(0.05F));
        drink(cd, "lemon_liqueur", KegLiquids.Drink.of(0xD8F0E050).cloudy(0.2F));
        drink(cd, "cola", KegLiquids.Drink.of(0xF23A1E12).foam(0.12F, 0xF0D8B890).fizz(2.4F));
        drink(cd, "mojito", KegLiquids.Drink.of(0x90CDEBA8).fizz(0.6F));
        drink(cd, "margarita", KegLiquids.Drink.of(0xC0E2EEA0).cloudy(0.3F));
        drink(cd, "bloody_mary", KegLiquids.Drink.of(0xF8861C14).thick(0.35F).cloudy(0.3F));
    }

    private VatDrinks() {
    }

    private static void drink(String namespace, String path, KegLiquids.Drink drink) {
        DRINKS.put(ResourceLocation.tryBuild(namespace, path), drink);
    }

    /** The look of the drink {@code stack} is, if anybody described it. */
    static Optional<KegLiquids.Drink> of(ItemStack stack) {
        return stack.isEmpty() ? Optional.empty() : Optional.ofNullable(DRINKS.get(BuiltInRegistries.ITEM.getKey(stack.getItem())));
    }
}
