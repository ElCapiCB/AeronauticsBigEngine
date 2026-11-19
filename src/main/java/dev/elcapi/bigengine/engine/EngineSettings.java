package dev.elcapi.bigengine.engine;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/** Everything the player can tune from the Engine Control Unit. */
public final class EngineSettings {
    public static final int MAX_CYLINDERS = 32;

    // ---- global keys (sent over the network) ----
    public static final int THROTTLE = 0, IDLE_RPM = 1, MAX_RPM = 2, FUEL_LIMIT = 3, AFR = 4,
            IGNITION = 5, REDSTONE = 6, REVERSE = 7;
    // ---- per cylinder: key = CYL_BASE + cyl * CYL_STRIDE + field ----
    public static final int CYL_BASE = 100, CYL_STRIDE = 4, CYL_ENABLED = 0, CYL_SECOND_TURN = 1, CYL_TRIM = 2;

    /** Throttle position 0-100 %. Sets the governor target between idle and max RPM. */
    public int throttle = 0;
    public int idleRpm = 24;
    public int maxRpm = 160;
    /** Max fuel per injection, % of a full charge. */
    public int fuelLimit = 100;
    /** Air/fuel ratio x10 (147 = stoichiometric 14.7:1). */
    public int afr = 130;
    /** Global ignition advance, degrees before TDC. */
    public int ignitionAdvance = 15;
    public boolean redstoneThrottle = true;
    public boolean reverse = false;

    public final boolean[] cylEnabled = new boolean[MAX_CYLINDERS];
    /** 4-stroke engines fire every other turn: false = fire on the 1st turn (0-360), true = 2nd turn (360-720). */
    public final boolean[] cylSecondTurn = new boolean[MAX_CYLINDERS];
    public final int[] cylTrim = new int[MAX_CYLINDERS];

    public EngineSettings() {
        java.util.Arrays.fill(cylEnabled, true);
    }

    public int get(int key) {
        if (key >= CYL_BASE) {
            int cyl = (key - CYL_BASE) / CYL_STRIDE, f = (key - CYL_BASE) % CYL_STRIDE;
            if (cyl >= MAX_CYLINDERS) return 0;
            return switch (f) {
                case CYL_ENABLED -> cylEnabled[cyl] ? 1 : 0;
                case CYL_SECOND_TURN -> cylSecondTurn[cyl] ? 1 : 0;
                case CYL_TRIM -> cylTrim[cyl];
                default -> 0;
            };
        }
        return switch (key) {
            case THROTTLE -> throttle;
            case IDLE_RPM -> idleRpm;
            case MAX_RPM -> maxRpm;
            case FUEL_LIMIT -> fuelLimit;
            case AFR -> afr;
            case IGNITION -> ignitionAdvance;
            case REDSTONE -> redstoneThrottle ? 1 : 0;
            case REVERSE -> reverse ? 1 : 0;
            default -> 0;
        };
    }

    /** Sets a value, clamped to its valid range. */
    public void set(int key, int v, int maxEngineRpm) {
        if (key >= CYL_BASE) {
            int cyl = (key - CYL_BASE) / CYL_STRIDE, f = (key - CYL_BASE) % CYL_STRIDE;
            if (cyl < 0 || cyl >= MAX_CYLINDERS) return;
            switch (f) {
                case CYL_ENABLED -> cylEnabled[cyl] = v != 0;
                case CYL_SECOND_TURN -> cylSecondTurn[cyl] = v != 0;
                case CYL_TRIM -> cylTrim[cyl] = Mth.clamp(v, -20, 20);
                default -> {}
            }
            return;
        }
        switch (key) {
            case THROTTLE -> throttle = Mth.clamp(v, 0, 100);
            case IDLE_RPM -> idleRpm = Mth.clamp(v, 8, Math.max(8, maxRpm - 8));
            case MAX_RPM -> maxRpm = Mth.clamp(v, idleRpm + 8, maxEngineRpm);
            case FUEL_LIMIT -> fuelLimit = Mth.clamp(v, 5, 100);
            case AFR -> afr = Mth.clamp(v, 70, 220);
            case IGNITION -> ignitionAdvance = Mth.clamp(v, -10, 50);
            case REDSTONE -> redstoneThrottle = v != 0;
            case REVERSE -> reverse = v != 0;
            default -> {}
        }
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("Throttle", throttle);
        t.putInt("Idle", idleRpm);
        t.putInt("Max", maxRpm);
        t.putInt("FuelLimit", fuelLimit);
        t.putInt("Afr", afr);
        t.putInt("Ignition", ignitionAdvance);
        t.putBoolean("Redstone", redstoneThrottle);
        t.putBoolean("Reverse", reverse);
        byte[] en = new byte[MAX_CYLINDERS], st = new byte[MAX_CYLINDERS];
        for (int i = 0; i < MAX_CYLINDERS; i++) {
            en[i] = (byte) (cylEnabled[i] ? 1 : 0);
            st[i] = (byte) (cylSecondTurn[i] ? 1 : 0);
        }
        t.putByteArray("CylEnabled", en);
        t.putByteArray("CylSecond", st);
        t.putIntArray("CylTrim", cylTrim.clone());
        return t;
    }

    public void load(CompoundTag t) {
        throttle = t.getInt("Throttle");
        idleRpm = t.contains("Idle") ? t.getInt("Idle") : idleRpm;
        maxRpm = t.contains("Max") ? t.getInt("Max") : maxRpm;
        fuelLimit = t.contains("FuelLimit") ? t.getInt("FuelLimit") : fuelLimit;
        afr = t.contains("Afr") ? t.getInt("Afr") : afr;
        ignitionAdvance = t.contains("Ignition") ? t.getInt("Ignition") : ignitionAdvance;
        redstoneThrottle = !t.contains("Redstone") || t.getBoolean("Redstone");
        reverse = t.getBoolean("Reverse");
        byte[] en = t.getByteArray("CylEnabled"), st = t.getByteArray("CylSecond");
        int[] tr = t.getIntArray("CylTrim");
        for (int i = 0; i < MAX_CYLINDERS; i++) {
            cylEnabled[i] = i >= en.length || en[i] != 0;
            cylSecondTurn[i] = i < st.length && st[i] != 0;
            cylTrim[i] = i < tr.length ? tr[i] : 0;
        }
    }

    /**
     * Sensible default firing order for an inline engine: alternate cylinders between the two crank turns
     * so that firings are spread over the 720 degree cycle.
     */
    public void autoTime(EngineLayout layout) {
        int n = layout.cylinders.size();
        for (int i = 0; i < n && i < MAX_CYLINDERS; i++) cylSecondTurn[i] = false;
        // Greedy: for every cylinder pick the turn that maximises the minimum gap to already placed firings.
        java.util.List<Double> placed = new java.util.ArrayList<>();
        for (int i = 0; i < n && i < MAX_CYLINDERS; i++) {
            double tdc = EngineMath.tdcAngle(layout.cylinders.get(i).phaseDeg);
            double a0 = tdc, a1 = tdc + 360;
            double g0 = minGap(placed, a0), g1 = minGap(placed, a1);
            boolean second = g1 > g0;
            cylSecondTurn[i] = second;
            placed.add(second ? a1 : a0);
        }
    }

    private static double minGap(java.util.List<Double> placed, double a) {
        double best = 720;
        for (double p : placed) {
            double d = Math.abs(p - a) % 720;
            best = Math.min(best, Math.min(d, 720 - d));
        }
        return best;
    }
}
