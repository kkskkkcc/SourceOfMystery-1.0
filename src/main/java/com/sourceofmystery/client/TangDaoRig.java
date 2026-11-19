package com.sourceofmystery.client;

import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Vector3f;

/**
 * Solves the first-person arms and knife from one row of clip channels (tools/tang_dao/rig.py).
 *
 * <p>Space: the vanilla hand pass, camera at the origin looking down -Z. The arms are the
 * vanilla arm model at native size. The knuckles face the edge, so the forearm lies in the
 * blade/edge plane: {@code forearm = sin(dev) * blade + cos(dev) * edge}. The arm's flat side
 * faces the blade's flat and its fist centre sits on the authored hand point, so the knife
 * always moves with the arm. Channels are authored right-handed; {@link #mirror} flips them.
 */
final class TangDaoRig {
    static final int HAND = 0, BLADE = 3, EDGE = 6, DEV = 9, OFF_HAND = 10, OFF_FROM = 13, OFF_ROLL = 16,
            REACH = 17, ALONG = 18, TRAVEL = 19, DROP = 20;
    /** Fist centre below the arm pivot, in blocks (arm model pixels / 16). */
    static final float FIST = 8 / 16f;

    /** An arm's pivot and rotation (columns: local x, y, z in view space). */
    static final class Arm {
        final Vector3f pivot = new Vector3f();
        final Matrix3f rotation = new Matrix3f();
    }

    /** A knife or scabbard origin and rotation (columns: edge +X, blade +Y, flat +Z). */
    static final class Prop {
        final Vector3f origin = new Vector3f();
        final Matrix3f rotation = new Matrix3f();

        Vector3f along(float distance, Vector3f out) {
            return rotation.getColumn(1, out).mul(distance).add(origin);
        }
    }

    private TangDaoRig() {}

    static void mirror(float[] v) {
        for (int c : new int[]{HAND, BLADE, EDGE, OFF_HAND, OFF_FROM}) v[c] = -v[c];
    }

    private static Vector3f vec(float[] v, int c) {
        return new Vector3f(v[c], v[c + 1], v[c + 2]);
    }

    /** Edge, blade and flat directions of the knife. */
    private static Matrix3f bladeFrame(float[] v, Matrix3f out) {
        Vector3f blade = vec(v, BLADE).normalize();
        Vector3f edge = vec(v, EDGE);
        edge.fma(-edge.dot(blade), blade).normalize();
        Vector3f flat = new Vector3f(edge).cross(blade);
        return out.setColumn(0, edge).setColumn(1, blade).setColumn(2, flat);
    }

    static void knife(float[] v, TangDaoClips.Rig rig, Prop out) {
        bladeFrame(v, out.rotation);
        Vector3f blade = out.rotation.getColumn(1, new Vector3f());
        out.origin.set(v[HAND], v[HAND + 1], v[HAND + 2]).fma(rig.fistFront() - rig.guard() * rig.bladeScale(), blade);
    }

    static void mainArm(float[] v, int side, Arm out) {
        Matrix3f frame = bladeFrame(v, new Matrix3f());
        Vector3f edge = frame.getColumn(0, new Vector3f());
        Vector3f blade = frame.getColumn(1, new Vector3f());
        Vector3f x = frame.getColumn(2, new Vector3f()).negate();
        float dev = v[DEV] * Mth.DEG_TO_RAD;
        Vector3f forearm = blade.mul(Mth.sin(dev)).add(edge.mul(Mth.cos(dev)));
        out.rotation.setColumn(0, x).setColumn(1, forearm).setColumn(2, new Vector3f(x).cross(forearm));
        place(v[HAND], v[HAND + 1], v[HAND + 2], side, out);
    }

    /** Free arm: keyed fist, pulled onto {@code target} by the reach weight, pointing away from its origin. */
    static void offArm(float[] v, int side, Vector3f target, Arm out) {
        Vector3f hand = vec(v, OFF_HAND);
        if (target != null && v[REACH] > 0) hand.lerp(target, v[REACH]);
        Vector3f axis = new Vector3f(hand).sub(v[OFF_FROM], v[OFF_FROM + 1], v[OFF_FROM + 2]).normalize();
        Vector3f x = new Vector3f(axis).cross(0, 0, 1);
        if (x.lengthSquared() < 1.0e-8f) x.set(1, 0, 0);
        x.normalize();
        float roll = v[OFF_ROLL] * Mth.DEG_TO_RAD;
        x.mul(Mth.cos(roll)).add(new Vector3f(axis).cross(x).mul(Mth.sin(roll)));
        out.rotation.setColumn(0, x).setColumn(1, axis).setColumn(2, new Vector3f(x).cross(axis));
        place(hand.x, hand.y, hand.z, side, out);
    }

    private static void place(float hx, float hy, float hz, int side, Arm out) {
        Vector3f fist = out.rotation.transform(new Vector3f(-side / 16f, FIST, 0));
        out.pivot.set(hx, hy, hz).sub(fist);
    }

    /** Scabbard slid along the anchor knife by {@code travel} and lowered by {@code drop}. */
    static void scabbard(float[] v, int side, Prop anchor, TangDaoClips.Rig rig, Prop out) {
        out.rotation.set(anchor.rotation);
        float slide = rig.bladeScale() * (rig.mouth() + v[TRAVEL] * (rig.tip() + rig.mouth()));
        anchor.along(slide, out.origin).add(side * rig.dropX() * v[DROP], rig.dropY() * v[DROP], rig.dropZ() * v[DROP]);
    }

    static boolean scabbardShown(float[] v) {
        return v[TRAVEL] < .999f || v[DROP] < .999f;
    }
}
