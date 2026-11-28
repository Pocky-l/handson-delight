package com.pockyl.handson_delight.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.pockyl.diegesis.api.client.SceneBuffers;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Items on a page are icons, lit as in the inventory whichever way the camera and the book turn. The shade the GUI's two lights
 * give each face is worked out here and multiplied into the colour; the normal then points into the level's light, where the
 * shader leaves the colour as it is. Lit by the level instead, an icon darkened as the camera or the book turned.
 */
final class IconLight implements VertexConsumer {
    private static final Vector3f LIGHT_0 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
    private static final Vector3f LIGHT_1 = new Vector3f(-0.2F, 1.0F, 0.7F).normalize();
    /**
     * The GUI's lights in the icon's own frame: for block items ({@code Lighting.setupFor3DItems}) and flat ones. Since 1.20.5 the
     * GUI flips the normals and turns the lights back; the light each face gets is the same as on 1.20.1, worked out the same way.
     */
    private static final Vector3f[] BLOCK_LIGHTS = lights(new Matrix4f().rotationYXZ(1.0821041F, 3.2375858F, 0.0F)
            .rotateYXZ(-Mth.PI / 8.0F, 2.3561945F, 0.0F));
    private static final Vector3f[] FLAT_LIGHTS = lights(new Matrix4f().scaling(1.0F, -1.0F, 1.0F).rotateY(-Mth.PI / 8.0F).rotateX(2.3561945F));
    /** The entity shader's light: each light's share, and what lights every face. */
    private static final float POWER = 0.6F;
    private static final float AMBIENT = 0.4F;

    private final VertexConsumer delegate;
    private final Matrix3f toIcon;
    private final Vector3f[] lights;
    private final Vector3f face = new Vector3f();

    private IconLight(VertexConsumer delegate, Matrix3f toIcon, Vector3f[] lights) {
        this.delegate = delegate;
        this.toIcon = toIcon;
        this.lights = lights;
    }

    /**
     * Buffers for one icon. {@code facing}: the normal matrix the icon's model is drawn under, before the model's own GUI turn
     * (the icon's flip and depth must not be in it, as in the GUI); {@code blockLight}: the model's {@code usesBlockLight}.
     */
    static SceneBuffers of(SceneBuffers buffers, Matrix3f facing, boolean blockLight) {
        Matrix3f toIcon = new Matrix3f(facing).invert();
        Vector3f[] lights = blockLight ? BLOCK_LIGHTS : FLAT_LIGHTS;
        return type -> new IconLight(buffers.getBuffer(type), toIcon, lights);
    }

    private static Vector3f[] lights(Matrix4f turn) {
        return new Vector3f[] {turn.transformDirection(new Vector3f(LIGHT_0)), turn.transformDirection(new Vector3f(LIGHT_1))};
    }

    /** What the GUI's lights make of a face with this normal, 0.4 to 1. */
    private float shade(float x, float y, float z) {
        toIcon.transform(face.set(x, y, z));
        float length = face.length();
        if (length < 1.0e-6F) {
            return 1.0F;
        }
        face.div(length);
        return Math.min(1.0F, POWER * (Math.max(0.0F, face.dot(lights[0])) + Math.max(0.0F, face.dot(lights[1]))) + AMBIENT);
    }

    @Override
    public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX, float normalY,
            float normalZ) {
        float shade = shade(normalX, normalY, normalZ);
        int red = Math.round((color >> 16 & 0xFF) * shade);
        int green = Math.round((color >> 8 & 0xFF) * shade);
        int blue = Math.round((color & 0xFF) * shade);
        // The level's lights stand in the world, as the pose's normals do since 1.20.5; the first one also lights the Nether fully.
        delegate.addVertex(x, y, z, color & 0xFF000000 | red << 16 | green << 8 | blue, u, v, overlay, light, LIGHT_0.x(), LIGHT_0.y(),
                LIGHT_0.z());
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        // Built piece by piece, the colour comes before the normal: such a vertex keeps its colour and is fully lit.
        delegate.setColor(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer setColor(int argb) {
        return setColor(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24);
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        delegate.setLineWidth(width);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        delegate.setNormal(LIGHT_0.x(), LIGHT_0.y(), LIGHT_0.z());
        return this;
    }
}
