package dev.elcapi.bigengine.engine;

/** Slider-crank kinematics and combustion efficiency curves. */
public final class EngineMath {
    /** Crank radius in blocks (stroke = 1 block). */
    public static final double CRANK_RADIUS = 0.5;
    /**
     * Connecting rod length in blocks (pin to pin). The rod block sits on the crank throw and its big end reaches
     * down to the crank pin at bottom dead centre, so the rod model extends one block below its own block.
     */
    public static final double ROD_LENGTH = 2.0;
    public static final double STOICHIOMETRIC_AFR = 14.7;

    private EngineMath() {}

    /**
     * Angle (degrees, right-hand around +axis) from the crank pin "phase 0" direction to {@code dir}.
     * Phase 0 points down for horizontal crankshafts and north for vertical ones (matches the block models), so a
     * phase-0 throw under an upright cylinder starts at bottom dead centre, exactly as the blocks are placed.
     */
    public static int angleTo(net.minecraft.core.Direction.Axis axis, net.minecraft.core.Direction dir) {
        net.minecraft.core.Direction axisDir = net.minecraft.core.Direction.fromAxisAndDirection(axis, net.minecraft.core.Direction.AxisDirection.POSITIVE);
        int ax = axisDir.getStepX(), ay = axisDir.getStepY(), az = axisDir.getStepZ();
        int vx = 0, vy = axis == net.minecraft.core.Direction.Axis.Y ? 0 : -1, vz = axis == net.minecraft.core.Direction.Axis.Y ? -1 : 0;
        for (int k = 0; k < 4; k++) {
            if (vx == dir.getStepX() && vy == dir.getStepY() && vz == dir.getStepZ()) return k * 90;
            int nx = ay * vz - az * vy, ny = az * vx - ax * vz, nz = ax * vy - ay * vx; // v = axis x v
            vx = nx; vy = ny; vz = nz;
        }
        return 0;
    }

    /** Crank pin direction for a pin angle measured from TDC, as a vector: d cos(a) + (axis x d) sin(a). */
    public static org.joml.Vector3d pinDirection(net.minecraft.core.Direction.Axis axis, net.minecraft.core.Direction dir, double alphaDeg) {
        net.minecraft.core.Direction ax = net.minecraft.core.Direction.fromAxisAndDirection(axis, net.minecraft.core.Direction.AxisDirection.POSITIVE);
        org.joml.Vector3d a = new org.joml.Vector3d(ax.getStepX(), ax.getStepY(), ax.getStepZ());
        org.joml.Vector3d d = new org.joml.Vector3d(dir.getStepX(), dir.getStepY(), dir.getStepZ());
        org.joml.Vector3d side = a.cross(d, new org.joml.Vector3d());
        double r = Math.toRadians(alphaDeg);
        return d.mul(Math.cos(r)).add(side.mul(Math.sin(r)));
    }

    /** Crank angle (0-360) at which a throw with the given phase puts its piston at top dead centre. */
    public static double tdcAngle(int phaseDeg) {
        return ((360 - phaseDeg) % 360 + 360) % 360;
    }

    /**
     * Piston travel from bottom dead centre, in blocks: 0 at BDC, 1 at TDC.
     *
     * @param alphaDeg angle of the crank pin measured from TDC
     */
    public static double pistonTravel(double alphaDeg) {
        double a = Math.toRadians(alphaDeg);
        double r = CRANK_RADIUS, l = ROD_LENGTH;
        double s = Math.sin(a);
        return r * Math.cos(a) + Math.sqrt(l * l - r * r * s * s) - (l - r);
    }

    /** Ignition advance (deg BTDC) giving best torque at a given RPM ("MBT"). */
    public static double bestAdvance(double rpm) {
        return 8.0 + 22.0 * Math.min(1.0, rpm / 256.0);
    }

    /** Fraction of the ideal torque obtained with the given ignition advance. */
    public static double timingEfficiency(double advance, double rpm) {
        double e = (advance - bestAdvance(rpm)) / 30.0;
        return Math.max(0.05, 1.0 - e * e);
    }

    /** True if the spark is so early that the mixture detonates (knock). */
    public static boolean knocks(double advance, double rpm) {
        return advance > bestAdvance(rpm) + 14.0;
    }

    /** Lambda = AFR / 14.7. Best power slightly rich (~0.88); misfires outside 0.6..1.6. */
    public static double mixtureEfficiency(double lambda) {
        if (lambda < 0.6 || lambda > 1.6) return 0.0;
        double e = lambda - 0.88;
        return Math.max(0.0, 1.0 - 1.4 * e * e);
    }
}
