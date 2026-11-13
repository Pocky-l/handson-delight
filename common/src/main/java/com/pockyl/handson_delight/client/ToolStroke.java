package com.pockyl.handson_delight.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;

import com.pockyl.handson_delight.FarmersDelightContent;
import com.pockyl.handson_delight.station.Cutting;

import java.util.List;
import java.util.Optional;

/**
 * How a tool works what lies on the board, each the way it is used: the knife cuts on its marks (drawn by {@code BoardBehavior}
 * itself), an axe strips a log in one push along it or splits wood with a swing, a pickaxe strikes stone until it breaks, a shovel
 * digs into gravel and clay and levers a lump out, a hoe hooks a chest off a boat or a cart and pries it away, shears snip leather
 * along their line. Read from the recipe, so every viewer agrees.
 *
 * <p>Poses are in the frame of the scene's {@code tool_turn}: x along the mark (or across the cook's view over a block, growing to
 * the cook's left), y up, z away from the cook. The tool is held at its grip, its working point (an axe's edge, a pick's point, a shovel's tip) on the
 * target at contact. The vanilla icons all draw a tool from the handle at the bottom left to the head at the top right.
 */
enum ToolStroke {
    /** The knife: {@code BoardBehavior} moves it along its marks. */
    CUT(null, 0.0F, 0.0F),
    /** An axe through wood that is no log (a door, a basket): a swing from above that bites in and is pulled out. */
    CHOP(Icon.AXE, 0.19F, 0.42F),
    /** An axe along a log: its blade flat against the near side, drawn from the top to the foot, the bark curling off under it. */
    STRIP(Icon.AXE, 0.27F, 0.42F),
    /** A pickaxe on stone: raised high, brought down hard on its point. */
    SMASH(Icon.PICKAXE, 0.19F, 0.40F),
    /** A shovel into gravel or clay: pushed in low from the near side, then levered so the lump comes up. */
    DIG(Icon.SHOVEL, 0.17F, 0.38F),
    /** A hoe at the seam of a boat and its chest: hooked in, then levered back so the part comes away. */
    PRY(Icon.HOE, 0.20F, 0.40F),
    /** Shears along the line: brought to its start and snipped along it in three bites. */
    SNIP(Icon.SHEARS, 0.36F, 0.46F);

    /** How big a tool is drawn on the board: as big as the knife, small beside a block. */
    static final float SCALE = 0.45F;
    /** Snips along the line, each this long, after the shears came to its start. */
    static final int SNIPS = 3;
    static final float SNIP_START = 0.06F;
    static final float SNIP_TIME = 0.1F;

    final Icon icon;
    /** When the tool meets the work, the moment the cut is sent; when the stroke is over, seconds. */
    final float impact;
    final float length;

    ToolStroke(Icon icon, float impact, float length) {
        this.icon = icon;
        this.impact = impact;
        this.length = length;
    }

    /** The stroke of {@code recipe} on {@code worked}: by the tools it takes, the knife for anything else (an addon's own tool). */
    static ToolStroke of(CuttingBoardRecipe recipe) {
        Ingredient tool = Cutting.tool(recipe);
        ItemStack knife = new ItemStack(BuiltInRegistries.ITEM.get(FarmersDelightContent.id("iron_knife")));
        ItemStack[] tools = tool.getItems();
        if (tools.length == 0 || tool.test(knife)) {
            return CUT;
        }
        for (ItemStack stack : tools) {
            if (stack.getItem() instanceof AxeItem) {
                return strips(recipe) ? STRIP : CHOP;
            }
            if (stack.getItem() instanceof PickaxeItem) {
                return SMASH;
            }
            if (stack.getItem() instanceof ShovelItem) {
                return DIG;
            }
            if (stack.getItem() instanceof HoeItem) {
                return PRY;
            }
            if (stack.getItem() instanceof ShearsItem) {
                return SNIP;
            }
        }
        return CUT;
    }

    /** The stroke for {@code worked} with {@code tool}, or by its first recipe when the tool takes none (another viewer's board). */
    static ToolStroke of(Level level, ItemStack worked, ItemStack tool) {
        if (worked.isEmpty()) {
            return CUT;
        }
        Optional<CuttingBoardRecipe> recipe = tool.isEmpty() ? Optional.empty() : Cutting.findWith(level, worked, tool);
        if (recipe.isEmpty()) {
            List<CuttingBoardRecipe> found = Cutting.recipesFor(level, worked);
            recipe = found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
        }
        return recipe.map(ToolStroke::of).orElse(CUT);
    }

    /** An axe that leaves a stripped block (logs, wood, stems, bamboo) strips; on anything else it splits. */
    private static boolean strips(CuttingBoardRecipe recipe) {
        return recipe.getResults().stream().anyMatch(result -> BuiltInRegistries.ITEM.getKey(result.getItem()).getPath().startsWith("stripped_"));
    }

    /**
     * Where a tool is on its icon, pixels from the top left: where it is held, the point that does the work, and which way that
     * point faces (an axe's edge out of the head, a pick's point along the head, a shovel's blade along the handle).
     */
    enum Icon {
        AXE(3.0F, 13.5F, 7.0F, 3.5F, -0.8F, -0.6F),
        PICKAXE(3.0F, 13.5F, 6.0F, 2.6F, -0.86F, -0.5F),
        SHOVEL(3.0F, 13.5F, 13.6F, 2.6F, 0.7F, -0.72F),
        HOE(3.0F, 13.5F, 7.2F, 1.8F, -0.91F, -0.41F),
        SHEARS(3.5F, 11.5F, 11.5F, 2.5F, 0.66F, -0.75F);

        final Vector3f grip;
        final Vector3f point;
        final Vector3f facing;

        Icon(float gripU, float gripV, float pointU, float pointV, float facingU, float facingV) {
            grip = node(gripU, gripV);
            point = node(pointU, pointV);
            facing = new Vector3f(-facingU, -facingV, 0.0F).normalize();
        }

        /** An icon pixel in the item node's space: the item is drawn FIXED, centred and turned about y, so u runs to -x and v to -y. */
        private static Vector3f node(float u, float v) {
            return new Vector3f(8.0F - u, 8.0F - v, 0.0F);
        }

        /** How far the hands are from the working point, scene pixels. */
        float reach() {
            return grip.distance(point) * SCALE;
        }
    }

    /** Where the tool is: its working point in the turn's frame, and how it is turned. */
    record Pose(Vector3f point, Quaternionf rotation) {
        /** Where the item node goes so that its working point lies on {@link #point}. */
        Vector3f nodePosition(Icon icon) {
            Vector3f offset = rotation.transform(new Vector3f(icon.point)).mul(SCALE);
            return new Vector3f(point).sub(offset);
        }

        /** The rotation as the scene's nodes take it: degrees about x, y and z, applied y, x, z. */
        Vector3f degrees() {
            Vector3f angles = rotation.getEulerAnglesYXZ(new Vector3f());
            return angles.mul(Mth.RAD_TO_DEG);
        }
    }

    /** Which way an icon's face looks in the item node's space. */
    private static final Vector3f FACE = new Vector3f(0.0F, 0.0F, 1.0F);

    /**
     * The tool's pose {@code age} seconds into the stroke (NaN: hovering, ready) with its working point to meet the work at
     * {@code target}. Every tool but the shears is held from the cook's right, its handle rising towards the hands, its flat side
     * to the cook, so that its head and every swing read from the camera; shears lie along their line. {@code snipFrom} and
     * {@code snipTo}: where along the line shears start and end their snips.
     */
    Pose pose(Vector3f target, float age, float snipFrom, float snipTo) {
        return switch (this) {
            case CHOP -> chop(target, age);
            case SMASH -> smash(target, age);
            case STRIP -> strip(target, age);
            case DIG -> dig(target, age);
            case PRY -> pry(target, age);
            case SNIP -> snip(target, age, snipFrom, snipTo);
            case CUT -> new Pose(target, new Quaternionf());
        };
    }

    /** An axe raised over the work, swung back, brought down so its edge bites, held a moment, lifted. */
    private Pose chop(Vector3f target, float age) {
        Vector3f handle = dir(-0.8F, 0.7F, -0.55F);
        Vector3f edge = dir(0.0F, -1.0F, 0.0F);
        float raise = Float.isNaN(age) ? 0.3F : raise(age, 0.3F, 0.95F, 0.13F, 0.24F);
        return held(target, handle, plane(handle, edge), edge, raise, 0.0F, new Vector3f());
    }

    /** A pickaxe raised high, its point down, brought down hard and let spring back. */
    private Pose smash(Vector3f target, float age) {
        Vector3f handle = dir(-0.75F, 0.75F, -0.6F);
        Vector3f point = dir(0.3F, -1.0F, 0.2F);
        float raise = Float.isNaN(age) ? 0.3F : raise(age, 0.3F, 1.05F, 0.14F, 0.22F);
        return held(target, handle, plane(handle, point), point, raise, 0.0F, new Vector3f());
    }

    /**
     * An axe with its blade flat against the log's near side and its edge down, drawn from the top to the foot: the bark curls off
     * under the edge. {@code target} is the top of that side.
     */
    private Pose strip(Vector3f target, float age) {
        Vector3f handle = dir(-1.0F, 0.6F, 0.0F);
        Vector3f edge = dir(0.0F, -1.0F, 0.0F);
        float travel = target.y - 1.6F;
        float y = 1.0F;
        if (!Float.isNaN(age)) {
            if (age < 0.07F) {
                y = Mth.lerp(ease(age / 0.07F), 1.0F, 0.0F);
            } else if (age < impact) {
                y = -travel * ease((age - 0.07F) / (impact - 0.07F));
            } else {
                y = Mth.lerp(ease((age - impact) / (length - impact)), -travel, 1.0F);
            }
        }
        return held(target, handle, plane(handle, edge), edge, 0.0F, 0.0F, new Vector3f(0.0F, y, 0.0F));
    }

    /** A shovel backed off the lump's near top edge, pushed in, levered down so the blade lifts a shovelful, and drawn back. */
    private Pose dig(Vector3f target, float age) {
        Vector3f handle = dir(-0.8F, 0.7F, -0.5F);
        Vector3f blade = new Vector3f(handle).negate();
        Vector3f side = new Vector3f(handle).cross(0.0F, 1.0F, 0.0F).normalize();
        float in = -1.5F;
        float lever = 0.0F;
        if (!Float.isNaN(age)) {
            if (age < 0.1F) {
                in = Mth.lerp(ease(age / 0.1F), -1.5F, 2.0F);
            } else if (age < 0.22F) {
                in = 2.0F;
                lever = 0.5F * ease((age - 0.1F) / 0.12F);
            } else {
                float t = ease((age - 0.22F) / (length - 0.22F));
                in = Mth.lerp(t, 2.0F, -1.5F);
                lever = 0.5F * (1.0F - t);
            }
        }
        return held(new Vector3f(blade).mul(in).add(target), handle, side, blade, 0.0F, lever, new Vector3f());
    }

    /** A hoe brought down so its blade hooks the seam, levered back on it so the part comes up, and lifted off. */
    private Pose pry(Vector3f target, float age) {
        Vector3f handle = dir(-1.0F, 0.8F, -0.3F);
        Vector3f blade = dir(0.0F, -1.0F, 0.0F);
        float raise = 0.35F;
        float lever = 0.0F;
        Vector3f offset = new Vector3f();
        if (!Float.isNaN(age)) {
            if (age < 0.08F) {
                raise = Mth.lerp(ease(age / 0.08F), 0.35F, 0.0F);
            } else if (age < 0.24F) {
                float t = ease((age - 0.08F) / 0.16F);
                raise = 0.0F;
                lever = 0.45F * t;
                offset.set(0.0F, 0.5F * t, -0.4F * t);
            } else {
                float t = ease((age - 0.24F) / (length - 0.24F));
                raise = 0.35F * t;
                lever = 0.45F * (1.0F - t);
                offset.set(0.0F, 0.5F * (1.0F - t), -0.4F * (1.0F - t));
            }
        }
        return held(target, handle, plane(handle, blade), blade, raise, lever, offset);
    }

    /**
     * Shears lying along the line, points first: brought to the line's start, they snip their way to its end, each bite a quick
     * turn of the blades, then go back to where they were.
     */
    private Pose snip(Vector3f target, float age, float from, float to) {
        float along = 0.0F;
        float bite = 0.0F;
        if (!Float.isNaN(age)) {
            if (age < SNIP_START) {
                along = Mth.lerp(ease(age / SNIP_START), 0.0F, from);
            } else if (age < impact) {
                float snips = (age - SNIP_START) / SNIP_TIME;
                int done = Math.min(SNIPS - 1, (int) snips);
                float part = snips - done;
                along = Mth.lerp((done + ease(part)) / SNIPS, from, to);
                bite = Mth.sin(part * Mth.PI) * 0.3F;
            } else {
                along = Mth.lerp(ease((age - impact) / (length - impact)), to, 0.0F);
            }
        }
        Vector3f handle = dir(-1.0F, 0.15F, 0.0F);
        Vector3f flat = dir(0.0F, 1.0F, -0.35F);
        flat.sub(new Vector3f(handle).mul(handle.dot(flat))).normalize();
        Quaternionf rotation = hold(handle, flat, dir(1.0F, 0.0F, 0.0F));
        rotation = new Quaternionf().rotationAxis(bite, flat).mul(rotation);
        return new Pose(new Vector3f(target).add(along, 0.25F, 0.0F), rotation);
    }

    /**
     * A swing about the hands: from {@code ready} back to {@code back} (raised further), down to the work by {@link #impact}
     * (gathering speed), held in it until {@code hold}, then back to ready by the end. How far the head is raised, radians.
     */
    private float raise(float age, float ready, float back, float raised, float hold) {
        if (age < raised) {
            return Mth.lerp(ease(age / raised), ready, back);
        }
        if (age < impact) {
            float t = (age - raised) / (impact - raised);
            return Mth.lerp(t * t, back, 0.0F);
        }
        if (age < hold) {
            return 0.0F;
        }
        return Mth.lerp(ease((age - hold) / (length - hold)), 0.0F, ready);
    }

    /**
     * The tool held with its handle along {@code handle}, its flat side square to {@code plane} and its working point facing
     * {@code facing}, its point on {@code target}; then raised by {@code raise} about the hands (its head comes up) and levered by
     * {@code lever} about its working point (its handle goes down), both in its own plane, and moved by {@code offset}.
     */
    private Pose held(Vector3f target, Vector3f handle, Vector3f plane, Vector3f facing, float raise, float lever, Vector3f offset) {
        Quaternionf rotation = hold(handle, plane, facing);
        Vector3f grip = new Vector3f(handle).mul(icon.reach()).add(target);
        Vector3f arm = new Vector3f(target).sub(grip);
        Quaternionf swung = turn(plane, arm, raise, true);
        Vector3f reached = swung.transform(new Vector3f(arm));
        Vector3f point = new Vector3f(reached).add(grip);
        Quaternionf levered = turn(plane, reached.negate(), lever, false);
        rotation = levered.mul(swung, new Quaternionf()).mul(rotation);
        return new Pose(point.add(offset), rotation);
    }

    /** The icon turned so its handle runs along {@code handle}, its face square to {@code plane}, its working point to {@code facing}. */
    private Quaternionf hold(Vector3f handle, Vector3f plane, Vector3f facing) {
        Vector3f toGrip = new Vector3f(icon.grip).sub(icon.point);
        Quaternionf rotation = orient(toGrip, FACE, handle, plane);
        if (rotation.transform(new Vector3f(icon.facing)).dot(facing) < 0.0F) {
            rotation = orient(toGrip, FACE, handle, new Vector3f(plane).negate());
        }
        return rotation;
    }

    /** A turn of {@code angle} about {@code axis} that lifts the end of {@code arm} ({@code up}) or lowers it. */
    private static Quaternionf turn(Vector3f axis, Vector3f arm, float angle, boolean up) {
        float rising = new Vector3f(axis).cross(arm).y;
        float sign = (rising >= 0.0F) == up ? 1.0F : -1.0F;
        return new Quaternionf().rotationAxis(sign * angle, axis);
    }

    /** The plane a tool swings in: through its handle and the way its working point faces. */
    private static Vector3f plane(Vector3f handle, Vector3f facing) {
        return new Vector3f(handle).cross(facing).normalize();
    }

    private static Vector3f dir(float x, float y, float z) {
        return new Vector3f(x, y, z).normalize();
    }

    /** The rotation that turns {@code a} to {@code toA} and, as near as the two allow, {@code b} to {@code toB}. */
    private static Quaternionf orient(Vector3f a, Vector3f b, Vector3f toA, Vector3f toB) {
        Vector3f[] from = frame(a, b);
        Vector3f[] to = frame(toA, toB);
        Matrix3f fromMatrix = new Matrix3f(from[0], from[1], from[2]);
        Matrix3f toMatrix = new Matrix3f(to[0], to[1], to[2]);
        Matrix3f rotation = toMatrix.mul(fromMatrix.transpose());
        return new Quaternionf().setFromNormalized(rotation);
    }

    /** Three axes at right angles: {@code a}, {@code b} made square to it, and the third. */
    private static Vector3f[] frame(Vector3f a, Vector3f b) {
        Vector3f x = new Vector3f(a).normalize();
        Vector3f y = new Vector3f(b).sub(new Vector3f(x).mul(x.dot(b))).normalize();
        return new Vector3f[] {x, y, new Vector3f(x).cross(y)};
    }

    private static float ease(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }
}
