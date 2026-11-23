package com.pockyl.handson_delight.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;

import com.pockyl.handson_delight.HandsOnDelight;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

/**
 * The game tests on Fabric. They run when the game starts with {@code -Dfabric-api.gametest} ({@code :fabric:runGameTest}), and only
 * then are they there: each method marked {@link GameTest} in the classes below is registered here as a test function, and the tests
 * themselves come from the built-in data pack {@code resourcepacks/gametests} the build writes (one file each, its time limit and
 * environment there).
 */
public final class FabricGameTests implements ModInitializer {
    /** Fabric API's switch for the game test server. */
    private static final String ENABLED = "fabric-api.gametest";

    /** The classes holding the tests. */
    private static final List<Class<?>> TESTS = List.of(FabricPotTests.class, FabricBoardTests.class, FabricSkilletTests.class,
            FabricStoveTests.class, FabricKegTests.class, FabricOvenTests.class, FabricVatTests.class, FabricRecipeEditTests.class);

    @Override
    public void onInitialize() {
        if (System.getProperty(ENABLED) == null) {
            return;
        }
        for (Class<?> type : TESTS) {
            Object tests = create(type);
            for (Method method : type.getMethods()) {
                if (method.isAnnotationPresent(GameTest.class)) {
                    Registry.register(BuiltInRegistries.TEST_FUNCTION, HandsOnDelight.id(name(method)),
                            helper -> run(tests, method, helper));
                }
            }
        }
        FabricLoader.getInstance().getModContainer(HandsOnDelight.MOD_ID).ifPresent(mod ->
                ResourceLoader.registerBuiltinPack(HandsOnDelight.id("gametests"), mod, PackActivationType.ALWAYS_ENABLED));
    }

    /** The test's name, its method's in snake case: also the name of its file in the data pack. */
    private static String name(Method method) {
        return method.getName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private static Object create(Class<?> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot create " + type.getName(), e);
        }
    }

    private static void run(Object tests, Method method, GameTestHelper helper) {
        try {
            method.invoke(tests, helper);
        } catch (InvocationTargetException e) {
            // The test's own failure (an assertion of the helper) goes to the framework as it is.
            if (e.getCause() instanceof RuntimeException failure) {
                throw failure;
            }
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
