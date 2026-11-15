package com.pockyl.handson_delight.neoforge;

import com.pockyl.handson_delight.HandsOnDelight;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Reaches the parts of Youkai's Feasts' steamer that the station uses: its racks ({@code RackData}: four places, the steam that
 * passes), what lies on a place ({@code RackItemData}: the stack and how far it has steamed) and the tower's rules
 * ({@code RackInfo}: racks of a block, its lid, one rack more). Its classes build on the L2 libraries nested in its jar, which are
 * not on the compile classpath, so they are found by their own names here, once; none of these names is the game's, so they are
 * the same in every environment.
 */
public final class YoukaiSteamers {
    public static final String RACK_INFO = "dev.xkmc.youkaishomecoming.content.pot.steamer.RackInfo";

    private static final Map<String, Field> FIELDS = new HashMap<>();
    private static final Map<String, Method> METHODS = new HashMap<>();
    private static boolean warned;

    private YoukaiSteamers() {
    }

    /** The value of {@code target}'s field {@code name}, or null when it is not there. */
    public static Object get(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            Field field = field(target.getClass(), name);
            return field == null ? null : field.get(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(name, e);
            return null;
        }
    }

    /**
     * Calls {@code target}'s method {@code name} (a class for a static one) with {@code args}; null when it is not there or failed.
     * Methods are told apart by name and the number of arguments, as the steamer has them.
     */
    public static Object call(Object target, String name, Object... args) {
        if (target == null) {
            return null;
        }
        Class<?> type = target instanceof Class<?> cls ? cls : target.getClass();
        try {
            Method method = method(type, name, args.length);
            if (method == null) {
                return null;
            }
            return method.invoke(target instanceof Class<?> ? null : target, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(name, e);
            return null;
        }
    }

    /** One of the steamer's classes, from the class loader of its block entity. */
    public static Class<?> type(Object near, String name) {
        try {
            return Class.forName(name, true, near.getClass().getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            warn(name, e);
            return null;
        }
    }

    private static synchronized Field field(Class<?> type, String name) {
        String key = type.getName() + "#" + name;
        if (FIELDS.containsKey(key)) {
            return FIELDS.get(key);
        }
        Field found = null;
        for (Class<?> at = type; at != null && found == null; at = at.getSuperclass()) {
            for (Field field : at.getDeclaredFields()) {
                if (field.getName().equals(name)) {
                    field.setAccessible(true);
                    found = field;
                    break;
                }
            }
        }
        FIELDS.put(key, found);
        return found;
    }

    private static synchronized Method method(Class<?> type, String name, int arguments) {
        String key = type.getName() + "#" + name + "/" + arguments;
        if (METHODS.containsKey(key)) {
            return METHODS.get(key);
        }
        Method found = null;
        for (Class<?> at = type; at != null && found == null; at = at.getSuperclass()) {
            for (Method method : at.getDeclaredMethods()) {
                // The public one where it has two of a name and size (a place by the point clicked, or by its index).
                if (method.getName().equals(name) && method.getParameterCount() == arguments && !method.isBridge()
                        && (found == null || java.lang.reflect.Modifier.isPublic(method.getModifiers()))) {
                    found = method;
                }
            }
        }
        if (found != null) {
            found.setAccessible(true);
        }
        METHODS.put(key, found);
        return found;
    }

    private static void warn(String name, Throwable e) {
        if (!warned) {
            warned = true;
            HandsOnDelight.LOGGER.warn("Youkai's Feasts' steamer is not what the station knows ({})", name, e);
        }
    }
}
