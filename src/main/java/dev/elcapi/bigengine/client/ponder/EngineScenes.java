package dev.elcapi.bigengine.client.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import dev.elcapi.bigengine.content.block.CrankThrowBlock;
import dev.elcapi.bigengine.content.block.OilFilmBlock;
import dev.elcapi.bigengine.registry.ModBlocks;
import dev.elcapi.bigengine.registry.ModItems;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Ponder scenes. Text is looked up from the lang files as {@code aeronauticsbigengine.ponder.<scene>.text_N},
 * numbered in the order the {@code text(...)} calls appear below (English defaults are given inline).
 * Schematic "engine_i2": 2 cylinder inline engine, crank along X at z=3 (x=0 shaft, 1 output, 2 bearing,
 * 3 throw, 4 bearing, 5 throw, 6 flywheel), ECU at (2,1,4), cylinders at x=3 and x=5.
 */
public final class EngineScenes {
    private EngineScenes() {}

    private static final int CYL_A = 3, CYL_B = 5, Z = 3;

    // ------------------------------------------------------------------ helpers

    private static Selection crankLine(SceneBuildingUtil util) {
        return util.select().fromTo(1, 1, Z, 6, 1, Z);
    }

    private static Selection cylinderUpper(SceneBuildingUtil util) {
        return util.select().fromTo(2, 4, 2, 6, 6, 4);
    }

    /** Spins the crank and pumps the pistons for {@code halfTurns} half turns (10 ticks each). */
    private static void runEngine(CreateSceneBuilder scene, SceneBuildingUtil util, int halfTurns) {
        Selection rotating = util.select().position(CYL_A, 1, Z).add(util.select().position(CYL_B, 1, Z))
                .add(util.select().position(6, 1, Z));
        ElementLink<WorldSectionElement> crank = scene.world().makeSectionIndependent(rotating);
        scene.world().configureCenterOfRotation(crank, util.vector().centerOf(CYL_A, 1, Z));
        ElementLink<WorldSectionElement> a = scene.world().makeSectionIndependent(util.select().fromTo(CYL_A, 2, Z, CYL_A, 3, Z));
        ElementLink<WorldSectionElement> b = scene.world().makeSectionIndependent(util.select().fromTo(CYL_B, 2, Z, CYL_B, 3, Z));

        scene.world().setKineticSpeed(util.select().fromTo(0, 1, Z, 1, 1, Z), 64);
        // cylinder A starts at top dead centre, B (crank pin at 180 deg) at the bottom
        scene.world().moveSection(a, new Vec3(0, 1, 0), 5);
        scene.idle(5);
        boolean aUp = true;
        for (int i = 0; i < halfTurns; i++) {
            scene.world().rotateSection(crank, 180, 0, 0, 10);
            scene.world().moveSection(a, new Vec3(0, aUp ? -1 : 1, 0), 10);
            scene.world().moveSection(b, new Vec3(0, aUp ? 1 : -1, 0), 10);
            if (i % 2 == 0) scene.effects().emitParticles(util.vector().blockSurface(new BlockPos(aUp ? CYL_B : CYL_A, 5, 4), Direction.SOUTH),
                    scene.effects().simpleParticleEmitter(ParticleTypes.SMOKE, new Vec3(0, 0.05, 0.05)), 3, 2);
            aUp = !aUp;
            scene.idle(10);
        }
        scene.world().setKineticSpeed(util.select().fromTo(0, 1, Z, 1, 1, Z), 0);
        if (!aUp) scene.world().moveSection(a, new Vec3(0, -1, 0), 5);
        else scene.world().moveSection(b, new Vec3(0, -1, 0), 5);
        scene.idle(5);
    }

    private static ItemStack stack(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
    }

    // ------------------------------------------------------------------ 1. overview

    public static void overview(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("engine_overview", "Building a Big Engine");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);

        scene.world().showSection(crankLine(util), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(80).attachKeyFrame().pointAt(util.vector().centerOf(4, 1, Z)).placeNearTarget()
                .text("The crank line is built along one axis: crank throws, crankshaft pieces, main bearings and a flywheel");
        scene.idle(90);

        scene.world().showSection(util.select().position(0, 1, Z), Direction.EAST);
        scene.overlay().showText(70).attachKeyFrame().colored(PonderPalette.OUTPUT).pointAt(util.vector().centerOf(1, 1, Z)).placeNearTarget()
                .text("A single Power Output goes at one end of the line. This is where the engine feeds the Create network");
        scene.idle(80);

        scene.world().showSection(util.select().fromTo(CYL_A, 2, Z, CYL_B, 3, Z), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(70).attachKeyFrame().pointAt(util.vector().centerOf(CYL_A, 2, Z)).placeNearTarget()
                .text("Every crank throw drives one cylinder: a connecting rod with a piston on top of it");
        scene.idle(80);

        scene.world().showSection(cylinderUpper(util), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(80).attachKeyFrame().pointAt(util.vector().centerOf(CYL_B, 5, Z)).placeNearTarget()
                .text("Above each piston: an empty bore lined with cylinder walls, closed by a cylinder head carrying its ports");
        scene.idle(90);

        scene.world().showSection(util.select().position(2, 1, 4), Direction.NORTH);
        scene.idle(10);
        scene.overlay().showText(70).attachKeyFrame().pointAt(util.vector().centerOf(2, 1, 4)).placeNearTarget()
                .text("Finally, place the Engine Control Unit touching any block of the crank line");
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(2, 1, 4), Pointing.DOWN, 40).rightClick();
        scene.overlay().showText(60).pointAt(util.vector().centerOf(2, 1, 4)).placeNearTarget()
                .text("Right-click it to open the tuning screen, and press Start");
        scene.idle(70);

        scene.overlay().showText(90).attachKeyFrame().independent(20)
                .text("The starter cranks the engine, the cylinders fire and the power flows out through the Power Output");
        runEngine(scene, util, 12);
        scene.idle(20);
    }

    // ------------------------------------------------------------------ 2. cylinder anatomy

    public static void cylinder(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("engine_cylinder", "Anatomy of a Cylinder");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.world().showSection(crankLine(util).add(util.select().position(0, 1, Z)), Direction.DOWN);
        scene.idle(20);

        scene.world().showSection(util.select().position(CYL_A, 2, Z), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(70).attachKeyFrame().pointAt(util.vector().centerOf(CYL_A, 2, Z)).placeNearTarget()
                .text("Place a Connecting Rod on a crank throw, pointing away from the crankshaft");
        scene.idle(80);

        scene.world().showSection(util.select().position(CYL_A, 3, Z), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(70).attachKeyFrame().pointAt(util.vector().centerOf(CYL_A, 3, Z)).placeNearTarget()
                .text("Then an Engine Piston on top of the rod, facing the same way");
        scene.idle(80);

        scene.overlay().showOutline(PonderPalette.GREEN, "bore", util.select().position(CYL_A, 4, Z), 80);
        scene.overlay().showText(80).attachKeyFrame().colored(PonderPalette.GREEN).pointAt(util.vector().centerOf(CYL_A, 4, Z)).placeNearTarget()
                .text("The block above the piston is the bore. It must stay empty: the piston moves into it");
        scene.idle(90);

        scene.world().showSection(util.select().fromTo(2, 4, 2, 4, 4, 4), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(70).attachKeyFrame().pointAt(util.vector().blockSurface(new BlockPos(CYL_A, 4, 2), Direction.UP)).placeNearTarget()
                .text("Surround the bore with Cylinder Walls on every side");
        scene.idle(80);

        scene.world().showSection(util.select().position(CYL_A, 5, Z), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(70).attachKeyFrame().pointAt(util.vector().centerOf(CYL_A, 5, Z)).placeNearTarget()
                .text("And close it with a Cylinder Head. Its free faces hold the injector, spark plug, intake and exhaust");
        scene.idle(80);

        ElementLink<WorldSectionElement> piston = scene.world().makeSectionIndependent(util.select().fromTo(CYL_A, 2, Z, CYL_A, 3, Z));
        scene.overlay().showText(80).attachKeyFrame().independent(20)
                .text("While running, the piston travels one block: from its own block up into the bore and back");
        for (int i = 0; i < 4; i++) {
            scene.world().moveSection(piston, new Vec3(0, 1, 0), 10);
            scene.idle(12);
            scene.world().moveSection(piston, new Vec3(0, -1, 0), 10);
            scene.idle(12);
        }
        scene.idle(10);
    }

    // ------------------------------------------------------------------ 3. big bores and mass

    public static void bigBore(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("engine_big_bore", "Big Pistons, More Torque");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.world().showSection(util.select().fromTo(0, 1, 2, 4, 2, 2), Direction.DOWN);
        scene.idle(15);
        scene.world().showSection(util.select().fromTo(1, 3, 1, 3, 3, 3), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(80).attachKeyFrame().pointAt(util.vector().topOf(2, 3, 1)).placeNearTarget()
                .text("Pistons placed side by side, up to 3x3, form one big piston driven by a single rod");
        scene.idle(90);

        scene.world().showSection(util.select().layersFrom(4), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(80).attachKeyFrame().pointAt(util.vector().centerOf(0, 4, 2)).placeNearTarget()
                .text("The bore and the head grow with it. Walls must enclose the whole bore");
        scene.idle(90);

        scene.overlay().showText(100).attachKeyFrame().colored(PonderPalette.MEDIUM).independent(20)
                .text("Torque depends on the moving mass: every kg of piston and rod (Sable mass) adds stress capacity. Heavier pistons, more SU");
        scene.idle(110);
        scene.overlay().showText(90).attachKeyFrame().pointAt(util.vector().topOf(1, 6, 3)).placeNearTarget()
                .text("Big bores also need more air: add extra air intakes, up to one per piston block, for full power");
        scene.idle(100);
        scene.overlay().showText(80).attachKeyFrame().pointAt(util.vector().centerOf(4, 1, 2)).placeNearTarget()
                .text("A flywheel adds inertia: the engine revs up slower but shrugs off sudden loads");
        scene.idle(90);
    }

    // ------------------------------------------------------------------ 4. ports

    public static void ports(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("engine_ports", "Cylinder Head Ports");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.world().showSection(util.select().layers(1, 4), Direction.DOWN);
        scene.world().showSection(util.select().position(CYL_A, 5, Z).add(util.select().position(CYL_B, 5, Z)), Direction.DOWN);
        scene.idle(20);

        scene.world().showSection(util.select().fromTo(CYL_A, 6, Z, CYL_B, 6, Z), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(80).attachKeyFrame().pointAt(util.vector().centerOf(CYL_A, 6, Z)).placeNearTarget()
                .text("Fuel Injector (required): holds 1000 mB of fuel. Fill it with Create pipes or a bucket");
        scene.idle(60);
        scene.overlay().showControls(util.vector().topOf(CYL_A, 6, Z), Pointing.DOWN, 40).rightClick().withItem(new ItemStack(Items.LAVA_BUCKET));
        scene.idle(50);

        scene.world().showSection(util.select().position(2, 5, Z).add(util.select().position(6, 5, Z)), Direction.EAST);
        scene.idle(10);
        scene.overlay().showText(70).attachKeyFrame().pointAt(util.vector().centerOf(2, 5, Z)).placeNearTarget()
                .text("Spark Plug: without it the cylinder never ignites");
        scene.idle(80);

        scene.world().showSection(util.select().position(CYL_A, 5, 2).add(util.select().position(CYL_B, 5, 2)), Direction.SOUTH);
        scene.idle(10);
        scene.overlay().showText(80).attachKeyFrame().pointAt(util.vector().centerOf(CYL_A, 5, 2)).placeNearTarget()
                .text("Air Intake (required): it needs open air in front of it to breathe");
        scene.idle(90);

        scene.world().showSection(util.select().position(CYL_A, 5, 4).add(util.select().position(CYL_B, 5, 4)), Direction.NORTH);
        scene.idle(10);
        scene.overlay().showText(90).attachKeyFrame().pointAt(util.vector().centerOf(CYL_B, 5, 4)).placeNearTarget()
                .text("Exhaust: optional, but without it the cylinder only gives about a third of its power");
        scene.idle(30);
        for (int i = 0; i < 4; i++) {
            scene.effects().emitParticles(util.vector().blockSurface(new BlockPos(CYL_B, 5, 4), Direction.SOUTH),
                    scene.effects().simpleParticleEmitter(ParticleTypes.LARGE_SMOKE, new Vec3(0, 0.05, 0.05)), 2, 3);
            scene.idle(15);
        }
        scene.overlay().showText(70).independent(20)
                .text("Thick black smoke means a rich mixture: too much fuel for the air");
        scene.idle(80);
        scene.world().showSection(util.select().fromTo(0, 1, Z, 2, 1, 4), Direction.DOWN);
    }

    // ------------------------------------------------------------------ 5. oil

    public static void oil(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("engine_oil", "Lubricating the Cylinders");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.world().showSection(util.select().layers(1, 4), Direction.DOWN);
        scene.idle(20);

        BlockPos bore = new BlockPos(CYL_A, 4, Z);
        scene.overlay().showText(80).attachKeyFrame().pointAt(util.vector().centerOf(bore)).placeNearTarget()
                .text("Use the Engine Oil Can on the inner faces of the cylinder walls, inside the bore");
        scene.idle(30);
        scene.overlay().showControls(util.vector().blockSurface(new BlockPos(CYL_A - 1, 4, Z), Direction.EAST), Pointing.LEFT, 40)
                .rightClick().withItem(new ItemStack(ModItems.ENGINE_OIL.get()));
        scene.idle(20);
        BlockState oil = ModBlocks.OIL_FILM.get().defaultBlockState().setValue(OilFilmBlock.THICKNESS, 1)
                .setValue(MultifaceBlock.getFaceProperty(Direction.WEST), true);
        scene.world().setBlock(bore, oil, false);
        scene.idle(20);
        for (Direction d : new Direction[]{Direction.EAST, Direction.NORTH, Direction.SOUTH}) {
            oil = oil.setValue(MultifaceBlock.getFaceProperty(d), true);
            scene.world().setBlock(bore, oil, false);
            scene.idle(10);
        }
        scene.idle(20);

        scene.overlay().showText(80).attachKeyFrame().pointAt(util.vector().centerOf(bore)).placeNearTarget()
                .text("The oil clings to the walls like a vine. Use the can again to stack up to 3 layers");
        scene.idle(30);
        scene.world().setBlock(bore, oil.setValue(OilFilmBlock.THICKNESS, 2), false);
        scene.idle(20);
        scene.world().setBlock(bore, oil.setValue(OilFilmBlock.THICKNESS, 3), false);
        scene.idle(40);

        scene.overlay().showText(90).attachKeyFrame().independent(20)
                .text("The piston slides on this film. Every firing slowly wears it away, so top it up from time to time");
        scene.idle(100);
        scene.overlay().showText(90).attachKeyFrame().colored(PonderPalette.RED).independent(20)
                .text("Without oil, friction rises up to four times: the engine loses power, overheats and finally seizes");
        scene.idle(100);
        scene.overlay().showText(70).independent(20)
                .text("Engineer's Goggles on the controller show how well the engine is lubricated");
        scene.idle(80);
    }

    // ------------------------------------------------------------------ 6. controller & Sable

    public static void controller(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("engine_controller", "The Engine Control Unit");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.world().showSection(util.select().layersFrom(1), Direction.DOWN);
        scene.idle(20);

        BlockPos ecu = new BlockPos(2, 1, 4);
        scene.overlay().showText(70).attachKeyFrame().pointAt(util.vector().centerOf(ecu)).placeNearTarget()
                .text("The Engine Control Unit runs the engine. Right-click it to open its screen");
        scene.overlay().showControls(util.vector().topOf(ecu), Pointing.DOWN, 40).rightClick();
        scene.idle(80);

        scene.overlay().showText(90).attachKeyFrame().pointAt(util.vector().centerOf(ecu)).placeNearTarget()
                .text("Throttle sets the target speed between Idle and Max RPM. A governor adds or removes fuel to hold it");
        scene.idle(100);
        scene.effects().indicateRedstone(ecu);
        scene.overlay().showText(70).colored(PonderPalette.RED).pointAt(util.vector().centerOf(ecu)).placeNearTarget()
                .text("An analog redstone signal can work the throttle too");
        scene.idle(80);
        scene.overlay().showText(100).attachKeyFrame().independent(20)
                .text("Fuel limits the charge per cycle. Air/Fuel ratio (about 13:1 for best power) and ignition Advance tune efficiency");
        scene.idle(110);
        scene.overlay().showText(90).colored(PonderPalette.RED).independent(20)
                .text("Too much advance makes the engine knock: it heats up fast. Back it off when the screen warns you");
        scene.idle(100);

        scene.overlay().showText(100).attachKeyFrame().colored(PonderPalette.BLUE).independent(20)
                .text("Assemble (Sable) turns the crankshaft and every piston into Sable sub-levels that really move, driven by servo joints");
        runEngine(scene, util, 8);
        scene.idle(10);
        scene.overlay().showText(90).independent(20)
                .text("On an Aeronautics ship, the moving parts push on the ship itself. Disassemble puts every block back in place");
        scene.idle(100);
        scene.overlay().showText(80).attachKeyFrame().independent(20)
                .text("Engineer's Goggles show RPM, stress capacity, temperature, oil and fuel");
        scene.idle(90);
    }

    // ------------------------------------------------------------------ 7. crank timing

    public static void crankTiming(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("crank_timing", "Crank Throws and Firing Order");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.world().showSection(crankLine(util).add(util.select().position(0, 1, Z)), Direction.DOWN);
        scene.idle(20);

        BlockPos throwB = new BlockPos(CYL_B, 1, Z);
        scene.overlay().showText(80).attachKeyFrame().pointAt(util.vector().centerOf(throwB)).placeNearTarget()
                .text("Each crank throw has a crank pin angle: 0, 90, 180 or 270 degrees");
        scene.idle(90);
        scene.overlay().showText(70).attachKeyFrame().pointAt(util.vector().centerOf(throwB)).placeNearTarget()
                .text("Right-click a throw with a Wrench to turn its pin by 90 degrees");
        for (int i = 0; i < 4; i++) {
            scene.overlay().showControls(util.vector().topOf(throwB), Pointing.DOWN, 15).rightClick().withItem(stack("create:wrench"));
            scene.idle(5);
            scene.world().cycleBlockProperty(throwB, CrankThrowBlock.PHASE);
            scene.idle(15);
        }
        scene.idle(10);

        scene.world().showSection(util.select().layersFrom(2), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(90).attachKeyFrame().independent(20)
                .text("The pin angle decides when each piston reaches the top. Opposite pins (0 and 180) make pistons move in opposite directions");
        runEngine(scene, util, 4);
        scene.overlay().showText(100).attachKeyFrame().independent(20)
                .text("In a four-stroke engine each cylinder fires once every two turns. In the screen choose Turn 1 or Turn 2 for each cylinder");
        scene.idle(110);
        scene.overlay().showText(90).colored(PonderPalette.GREEN).independent(20)
                .text("Auto timing spreads the firings evenly over the 720 degree cycle: a smooth engine with a high balance value");
        scene.idle(100);
        scene.overlay().showText(80).colored(PonderPalette.RED).independent(20)
                .text("Uneven firing makes the engine shake, and on a ship that shake is passed to the hull");
        scene.idle(90);
    }
}
