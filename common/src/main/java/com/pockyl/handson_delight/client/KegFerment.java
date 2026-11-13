package com.pockyl.handson_delight.client;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.StationView;

/**
 * A brew is alive under its lid, and the keg says so, at the station and to whoever passes by: now and then it lets off its gas, the
 * lid knocks up a little, foam fizzes out along its seam and a breath of gas rises, with a deep soft blub and a pop of foam; once in a
 * while foam beads at the tap. Most often at the height of
 * a brew, more rarely as it settles, and not at all while the keg is too cold or too warm for it or its lid is off: a quiet keg
 * is one that does not ferment. What cures without liquid (meat, fish) breathes a thin warm wisp from the seam instead. Taking the
 * lid off a working brew lets out a puff of gas and foam.
 */
final class KegFerment {
    /** Seconds between two breaths at full vigour: at least this long, and up to this much longer. */
    private static final float REST = 2.2F;
    private static final float REST_SPREAD = 4.5F;
    /** How high the lid knocks up as the gas lifts it, pixels, and for how long, seconds. */
    private static final float BUMP = 0.35F;
    private static final float BUMP_TIME = 0.28F;
    /** Of the breaths, how many also bead foam at the tap. */
    private static final float TAP_FOAM = 0.18F;
    /** Height of the seam where the lid sits in the rim, pixels. */
    private static final float SEAM = 16.05F;
    private static final int GAS = 0x80FFFFFF;
    private static final int CURING = 0x60D2C4B0;

    /** A point of the keg in its own model's pixels, in the scene. */
    interface ToScene {
        Vector3f at(float x, float y, float z);
    }

    private float rest = 1.5F;
    private float bumpAt = -10.0F;
    private float time;

    /**
     * @param vigour how hard the brew works now, 0 (it does not) to 1
     * @param dry    nothing liquid in the keg: it cures rather than ferments
     * @param shut   the scene's own keg shows with its lid on, which can knock
     * @param foam   the colour of the brew's foam
     */
    void frame(StationView view, float dt, ToScene keg, KegBehavior.Shape shape, float vigour, boolean dry, boolean shut, Vector3f tap,
            int foam) {
        time += dt;
        if (vigour <= 0.0F) {
            rest = Math.max(rest, 1.0F);
            return;
        }
        rest -= dt * vigour;
        if (rest > 0.0F) {
            return;
        }
        RandomSource random = view.level().random;
        rest = REST + random.nextFloat() * REST_SPREAD;
        if (dry) {
            for (int i = 0; i < 2; i++) {
                view.spawnParticles(DiegesisParticles.STEAM, seam(keg, shape, random), new Vector3f(0.0F, 1.6F, 0.0F), 0.3F, 1, CURING);
            }
            return;
        }
        int pops = 3 + random.nextInt(4);
        for (int i = 0; i < pops; i++) {
            view.spawnParticles(DiegesisParticles.BUBBLE_POP, seam(keg, shape, random), new Vector3f(0.0F, 0.4F, 0.0F), 0.15F, 1, foam);
        }
        view.spawnParticles(DiegesisParticles.STEAM, seam(keg, shape, random), new Vector3f(0.0F, 2.2F, 0.0F), 0.5F, 1, GAS);
        // A deep, soft blub from inside, a little pop of the foam at the seam, and the lid settling back with a light wooden tap.
        view.playSound(PotSounds.BLUB, "lid", 0.28F, 0.7F + random.nextFloat() * 0.15F);
        view.playSound(SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, "lid", 0.18F, 0.85F + random.nextFloat() * 0.25F);
        if (shut) {
            bumpAt = time;
            view.playSound(SoundEvents.WOODEN_TRAPDOOR_CLOSE, "lid", 0.06F, 1.7F + random.nextFloat() * 0.2F);
        }
        if (random.nextFloat() < TAP_FOAM) {
            view.spawnParticles(DiegesisParticles.DROPLET, tap, new Vector3f(0.0F, -5.0F, 0.0F), 0.0F, 1, foam);
        }
    }

    /** The lid comes off a working brew: a puff of gas and foam out of the opening. */
    void opened(StationView view, ToScene keg, KegBehavior.Shape shape, boolean dry, int foam) {
        RandomSource random = view.level().random;
        Vector3f middle = keg.at(8.0F, SEAM + 0.5F, (shape.rimZ0() + shape.rimZ1()) / 2.0F);
        view.spawnParticles(DiegesisParticles.STEAM, middle, new Vector3f(0.0F, 3.0F, 0.0F), 2.0F, 4, dry ? CURING : GAS);
        if (!dry) {
            for (int i = 0; i < 6; i++) {
                view.spawnParticles(DiegesisParticles.BUBBLE_POP, seam(keg, shape, random), new Vector3f(0.0F, 0.6F, 0.0F), 0.3F, 1, foam);
            }
            view.playSound(PotSounds.BURST, "lid", 0.3F, 0.9F + random.nextFloat() * 0.1F);
        }
        rest = Math.max(rest, REST);
    }

    /** How far the gas lifts the lid now, pixels. */
    float bump() {
        float k = (time - bumpAt) / BUMP_TIME;
        return k >= 0.0F && k < 1.0F ? BUMP * Mth.sin(Mth.PI * k) : 0.0F;
    }

    /** A random point of the seam round the lid. */
    private static Vector3f seam(ToScene keg, KegBehavior.Shape shape, RandomSource random) {
        float width = shape.rimX1() - shape.rimX0();
        float depth = shape.rimZ1() - shape.rimZ0();
        float along = random.nextFloat() * 2.0F * (width + depth);
        float x;
        float z;
        if (along < width) {
            x = shape.rimX0() + along;
            z = shape.rimZ0();
        } else if (along < width + depth) {
            x = shape.rimX1();
            z = shape.rimZ0() + along - width;
        } else if (along < 2.0F * width + depth) {
            x = shape.rimX1() - (along - width - depth);
            z = shape.rimZ1();
        } else {
            x = shape.rimX0();
            z = shape.rimZ1() - (along - 2.0F * width - depth);
        }
        return keg.at(x, SEAM, z);
    }
}
