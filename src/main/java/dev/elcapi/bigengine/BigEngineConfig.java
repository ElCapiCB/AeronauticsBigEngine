package dev.elcapi.bigengine;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config (balance + physics tuning). File: serverconfig/aeronauticsbigengine-server.toml */
public final class BigEngineConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue TORQUE_PER_KPG;
    public static final ModConfigSpec.DoubleValue FRICTION_PER_KPG;
    public static final ModConfigSpec.DoubleValue VISCOUS_FRICTION;
    public static final ModConfigSpec.DoubleValue RPM_PER_TORQUE;
    public static final ModConfigSpec.IntValue MAX_ENGINE_RPM;
    public static final ModConfigSpec.IntValue MIN_FIRING_RPM;
    public static final ModConfigSpec.IntValue STARTER_RPM;
    public static final ModConfigSpec.IntValue STARTER_TICKS;
    public static final ModConfigSpec.DoubleValue FUEL_PER_FIRING;
    public static final ModConfigSpec.DoubleValue OIL_WEAR_CHANCE;
    public static final ModConfigSpec.DoubleValue OVERHEAT_TEMP;

    public static final ModConfigSpec.DoubleValue CRANK_STIFFNESS;
    public static final ModConfigSpec.DoubleValue CRANK_DAMPING;
    public static final ModConfigSpec.DoubleValue PISTON_STIFFNESS;
    public static final ModConfigSpec.DoubleValue PISTON_DAMPING;
    public static final ModConfigSpec.BooleanValue INVERT_CRANK_MOTOR;
    public static final ModConfigSpec.BooleanValue INVERT_PISTON_MOTOR;
    public static final ModConfigSpec.DoubleValue REACTION_TORQUE_SCALE;
    public static final ModConfigSpec.DoubleValue VIBRATION_SCALE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Engine balance").push("engine");
        TORQUE_PER_KPG = b.comment("Stress capacity (SU per RPM) produced per kpg of moving reciprocating mass (piston + rod, Sable mass) at full charge.",
                "Heavier pistons/rods => more torque => more stress units.").defineInRange("torquePerKpg", 4.0, 0.0, 1000.0);
        FRICTION_PER_KPG = b.comment("Static friction torque per kpg of moving mass.").defineInRange("frictionPerKpg", 0.02, 0.0, 100.0);
        VISCOUS_FRICTION = b.comment("Friction torque per kpg per RPM.").defineInRange("viscousFriction", 0.0012, 0.0, 10.0);
        RPM_PER_TORQUE = b.comment("How fast net torque changes RPM (divided by rotational inertia).").defineInRange("rpmPerTorque", 20.0, 0.1, 1000.0);
        MAX_ENGINE_RPM = b.comment("Hard RPM cap (Create speeds are limited to 256).").defineInRange("maxEngineRpm", 256, 16, 256);
        MIN_FIRING_RPM = b.comment("Below this RPM cylinders can not fire (engine stalls).").defineInRange("minFiringRpm", 10, 1, 128);
        STARTER_RPM = b.comment("RPM the starter motor cranks the engine to.").defineInRange("starterRpm", 40, 5, 128);
        STARTER_TICKS = b.comment("How long the starter runs, in ticks.").defineInRange("starterTicks", 80, 1, 1200);
        FUEL_PER_FIRING = b.comment("mB of fuel burned per firing of a full 1x1 bore at full charge and stoichiometric mixture.").defineInRange("fuelPerFiring", 0.25, 0.0, 100.0);
        OIL_WEAR_CHANCE = b.comment("Chance per firing that the oil film in that bore loses one layer.").defineInRange("oilWearChance", 0.003, 0.0, 1.0);
        OVERHEAT_TEMP = b.comment("Engine seizes above this temperature (C).").defineInRange("overheatTemp", 250.0, 50.0, 10000.0);
        b.pop();

        b.comment("Sable sub-level physics (assembled engines)").push("physics");
        CRANK_STIFFNESS = b.comment("Crank servo stiffness, multiplied by the crank sub-level inertia.").defineInRange("crankStiffness", 8000.0, 0.0, 1.0e7);
        CRANK_DAMPING = b.comment("Crank servo damping, multiplied by the crank sub-level inertia.").defineInRange("crankDamping", 180.0, 0.0, 1.0e6);
        PISTON_STIFFNESS = b.comment("Optional servo that helps the pistons follow the crank (they are already carried by the rod joints).",
                "0 = off. Multiplied by the piston sub-level mass.").defineInRange("pistonAssistStiffness", 0.0, 0.0, 1.0e7);
        PISTON_DAMPING = b.comment("Damping of the optional piston assist servo, multiplied by the piston sub-level mass.").defineInRange("pistonAssistDamping", 60.0, 0.0, 1.0e6);
        INVERT_CRANK_MOTOR = b.comment("Flip the crank servo direction if the crank turns the wrong way relative to the pistons.").define("invertCrankMotor", false);
        INVERT_PISTON_MOTOR = b.comment("Flip the optional piston assist servo direction.").define("invertPistonMotor", false);
        REACTION_TORQUE_SCALE = b.comment("Reaction torque applied to the ship (sub-level) carrying a non-assembled engine.").defineInRange("reactionTorqueScale", 0.5, 0.0, 1000.0);
        VIBRATION_SCALE = b.comment("Shake applied to the ship for unevenly timed engines (non-assembled).").defineInRange("vibrationScale", 0.2, 0.0, 1000.0);
        b.pop();
        SPEC = b.build();
    }

    private BigEngineConfig() {}
}
