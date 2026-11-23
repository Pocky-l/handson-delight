package com.pockyl.handson_delight.fabric;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A game test of {@link FabricGameTests}, in place of Fabric API's own annotation, which cannot be read outside development on
 * Minecraft 1.21.11 (its default rotation names an enum constant by its development name). The build writes a test file of the
 * data pack {@code resourcepacks/gametests} from each one (fabric/build.gradle), named as the method in snake case.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GameTest {
    /** The ticks the test may take. */
    int maxTicks() default 20;

    /** The test environment it runs in. */
    String environment() default "minecraft:default";
}
