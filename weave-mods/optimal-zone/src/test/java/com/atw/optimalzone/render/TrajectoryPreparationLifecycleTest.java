package com.atw.optimalzone.render;

import com.atw.optimalzone.OptimalZoneMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.AxisAlignedBB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;

import static com.atw.optimalzone.render.TrajectoryPreparationParityTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Calls frozen original and actual production compute methods with the mapped Minecraft geometry. */
class TrajectoryPreparationLifecycleTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    static TrajectoryPreparation state(ProjectileTrajectoryRenderer renderer) throws Exception {
        return (TrajectoryPreparation) field(renderer, "preparation");
    }

    static Object run(Scene scene, Object renderer, boolean original) throws Exception {
        scene.handler.sync(); scene.rewind(); reset();
        return (original ? BASE : CAND).invoke(renderer, scene.mc, scene.shooter, .5F);
    }

    static void parity(Scene scene, ProjectileTrajectoryRenderer renderer) throws Exception {
        String original = output(run(scene, baseline, true));
        String originalCounts = counts();
        ArrayList<String> originalCalls = new ArrayList<>(calls);
        assertEquals(original, output(run(scene, renderer, false)));
        assertEquals(originalCounts, counts(), "all live getter/network/trace counts");
        assertEquals(originalCalls, calls, "all live getter/network/trace order");
        assertNull(state(renderer).current, "no entity/world/box references survive a compute");
    }

    @Test void defaultsOffAndExactAllocationReuseKeepsAllLiveCalls() throws Exception {
        OptimalZoneMod mod = new OptimalZoneMod();
        ProjectileTrajectoryRenderer renderer = new ProjectileTrajectoryRenderer(mod);
        TrajectoryPreparation p = state(renderer);
        Scene scene = new Scene(32, 160);
        assertFalse(p.requested);
        assertTrue(mod.isEnabled() && mod.isProjectilesEnabled() && mod.isChamsEnabled()
                && mod.isMinimapEnabled() && mod.isInvisOverlayEnabled() && mod.isOptimalZoneEnabled());
        parity(scene, renderer);
        assertEquals(5120, p.baselinePreparations);
        assertEquals(0, p.reuses);
        renderer.setTrajectoryPreparation(true); renderer.clearTrajectoryPreparation();
        parity(scene, renderer);
        assertEquals(5120, boxes);
        assertEquals(5120, uuidLookups);
        assertEquals(32, p.misses);
        assertEquals(32, p.baselinePreparations);
        assertEquals(5088, p.reuses);
        assertEquals(1, p.activeComputations);
        assertTrue(p.status()[0].contains("available=true (source experiment) activeNow=false"));
        assertTrue(p.status()[0].contains("liveQualified=false"));
        assertTrue(p.status()[1].contains("not frames"));
        // A second renderer/session starts OFF regardless of the first renderer's request.
        assertFalse(state(new ProjectileTrajectoryRenderer(mod)).requested);
        assertTrue(mod.shouldRenderProjectiles());
    }

    @Test void sameBoxChangingOffsetsAndSameValuesNewIdentityBothMiss() throws Exception {
        for (boolean replace : new boolean[]{false, true}) {
            Scene scene = new Scene(1, 16);
            Player target = scene.targets.get(0);
            AxisAlignedBB original = target.box;
            scene.world.mutation = () -> {
                if (replace) target.box = new AxisAlignedBB(original.minX, original.minY, original.minZ,
                        original.maxX, original.maxY, original.maxZ);
                else target.posX = 30 + scene.world.calls * .25;
            };
            ProjectileTrajectoryRenderer renderer = new ProjectileTrajectoryRenderer(null);
            renderer.setTrajectoryPreparation(true);
            parity(scene, renderer);
            assertEquals(16, state(renderer).misses);
            assertEquals(15, state(renderer).invalidations);
            assertEquals(0, state(renderer).reuses);
        }
    }

    @Test void subclassOverridesNonfiniteAndSceneCapRetainOriginalPath() throws Exception {
        for (String kind : new String[]{"subclass", "nan", "infinity", "overflow", "cap", "unknown-list", "thread"}) {
            Scene scene = new Scene(kind.equals("cap") ? 257 : 1, 16);
            Player target = scene.targets.get(0);
            if (kind.equals("subclass")) target.box = new Box(target.box);
            if (kind.equals("nan")) target.box = new AxisAlignedBB(Double.NaN, 64, 0, 31, 66, 1);
            if (kind.equals("infinity")) target.lastTickPosX = Double.POSITIVE_INFINITY;
            if (kind.equals("overflow")) {
                target.box = new AxisAlignedBB(Double.MAX_VALUE, 64, 0, Double.MAX_VALUE, 66, 1);
                target.lastTickPosX = Double.MAX_VALUE / 2;
                target.posX = -Double.MAX_VALUE / 2;
            }
            if (kind.equals("unknown-list")) put(scene.world, net.minecraft.world.World.class,
                    "playerEntities", new LinkedList<>(scene.world.playerEntities));
            if (kind.equals("thread")) put(scene.mc, Minecraft.class, "mcThread", new Thread());
            ProjectileTrajectoryRenderer renderer = new ProjectileTrajectoryRenderer(null);
            renderer.setTrajectoryPreparation(true);
            parity(scene, renderer);
            assertEquals(0, state(renderer).reuses, kind);
            assertTrue(state(renderer).fallbackPreparations > 0, kind);
            assertFalse(state(renderer).fallback.startsWith("none"), kind);
        }
        Scene boundary = new Scene(256, 2);
        ProjectileTrajectoryRenderer renderer = new ProjectileTrajectoryRenderer(null);
        renderer.setTrajectoryPreparation(true);
        parity(boundary, renderer);
        assertEquals(256, state(renderer).misses);
        assertEquals(256, state(renderer).reuses);
    }

    @Test void laterWorldReplacementUsesNewLiveListAndDiscardsScope() throws Exception {
        Scene scene = new Scene(1, 16);
        World first = scene.world;
        World second = allocate(World.class);
        put(second, net.minecraft.world.World.class, "playerEntities", new ArrayList<>(first.playerEntities));
        second.stopAt = 2;
        first.mutation = () -> { if (first.calls == 2) scene.mc.theWorld = second; };
        scene.handler.sync();
        String expected = output(run(scene, baseline, true));
        second.calls = 0; scene.mc.theWorld = first;
        ProjectileTrajectoryRenderer renderer = new ProjectileTrajectoryRenderer(null);
        renderer.setTrajectoryPreparation(true);
        assertEquals(expected, output(run(scene, renderer, false)));
        assertEquals("world-change", state(renderer).fallback);
        assertEquals(0, state(renderer).reuses);
        assertNull(state(renderer).current);
    }

    @Test void activeScopePreservesLaterEligibilityNetworkAndTargetOrderChanges() throws Exception {
        for (String mutation : new String[]{"invis", "dead", "tab", "order"}) {
            Scene scene = new Scene(2, 16);
            Player first = scene.targets.get(0), second = scene.targets.get(1);
            scene.world.mutation = () -> {
                if (scene.world.calls == 1) {
                    first.invisible = false; first.isDead = false;
                    scene.handler.byUuid.add(first.uuid);
                    scene.world.playerEntities.set(0, first); scene.world.playerEntities.set(1, second);
                }
                if (scene.world.calls == 2) {
                    if (mutation.equals("invis")) first.invisible = true;
                    if (mutation.equals("dead")) first.isDead = true;
                    if (mutation.equals("tab")) scene.handler.byUuid.remove(first.uuid);
                    if (mutation.equals("order")) {
                        scene.world.playerEntities.set(0, second); scene.world.playerEntities.set(1, first);
                    }
                }
                try { scene.handler.sync(); } catch (Exception e) { throw new AssertionError(e); }
            };
            ProjectileTrajectoryRenderer renderer = new ProjectileTrajectoryRenderer(null);
            renderer.setTrajectoryPreparation(true);
            parity(scene, renderer);
            assertTrue(state(renderer).invalidations > 0, mutation);
            assertTrue(state(renderer).reuses > 0, "unaffected players still use exact derived boxes");
        }
    }

    public static class MutatingBox extends AxisAlignedBB {
        Runnable mutation;
        MutatingBox(AxisAlignedBB box, Runnable mutation) {
            super(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ); this.mutation = mutation;
        }
        @Override public AxisAlignedBB offset(double x, double y, double z) {
            return new MutatingBox(super.offset(x, y, z), mutation);
        }
        @Override public AxisAlignedBB expand(double x, double y, double z) {
            return new MutatingBox(super.expand(x, y, z), mutation);
        }
        @Override public net.minecraft.util.MovingObjectPosition calculateIntercept(net.minecraft.util.Vec3 from, net.minecraft.util.Vec3 to) {
            mutation.run(); return super.calculateIntercept(from, to);
        }
    }

    @Test void subclassInterceptSideEffectsStillPrecedeNextTargetsLiveReads() throws Exception {
        Scene scene = new Scene(2, 16);
        Player first = scene.targets.get(0), second = scene.targets.get(1);
        first.box = new MutatingBox(first.box, () -> second.invisible = (scene.world.calls & 1) == 0);
        ProjectileTrajectoryRenderer renderer = new ProjectileTrajectoryRenderer(null);
        renderer.setTrajectoryPreparation(true);
        parity(scene, renderer);
        assertTrue(state(renderer).fallbackPreparations >= 16);
        assertEquals(0, state(renderer).reuses, "alternating eligibility clears the second slot");
    }

    @Test void reentrancyAndToggleDuringGettersInvalidateOuterAndNestedReuse() throws Exception {
        Scene scene = new Scene(1, 16);
        final Object[] nestedRenderer = {baseline};
        scene.world.mutation = () -> {
            if (scene.world.calls == 2) {
                try { (nestedRenderer[0] == baseline ? BASE : CAND).invoke(nestedRenderer[0], scene.mc, scene.shooter, .5F); }
                catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            }
        };
        String expected = output(run(scene, baseline, true));
        ProjectileTrajectoryRenderer renderer = new ProjectileTrajectoryRenderer(null);
        renderer.setTrajectoryPreparation(true); nestedRenderer[0] = renderer;
        assertEquals(expected, output(run(scene, renderer, false)));
        assertEquals("reentrancy", state(renderer).fallback);
        assertEquals(0, state(renderer).reuses);
        assertNull(state(renderer).current);
        scene.world.mutation = null;
        run(scene, renderer, false);
        assertEquals(15, state(renderer).reuses, "next compute recovers without retained scope");
        for (String action : new String[]{"off", "clear", "disconnect"}) {
            renderer.setTrajectoryPreparation(true); renderer.clearTrajectoryPreparation();
            scene.world.mutation = () -> {
                if (scene.world.calls == 2) {
                    if (action.equals("off")) renderer.setTrajectoryPreparation(false);
                    else if (action.equals("clear")) renderer.clearTrajectoryPreparation();
                    else renderer.disconnectTrajectoryPreparation();
                }
            };
            run(scene, renderer, false);
            assertEquals(0, state(renderer).reuses, action);
            assertNull(state(renderer).current);
            assertEquals(action.equals("clear"), state(renderer).requested);
        }
    }

    @Test void exceptionsAlwaysReleaseScratchAndCountersSaturate() throws Exception {
        Scene scene = new Scene(1, 16);
        ProjectileTrajectoryRenderer renderer = new ProjectileTrajectoryRenderer(null);
        renderer.setTrajectoryPreparation(true);
        scene.world.mutation = () -> { if (scene.world.calls == 2) throw new IllegalStateException("fixture"); };
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> run(scene, renderer, false));
        assertNull(state(renderer).current);
        assertFalse((boolean)field(renderer, "computing"));
        scene.world.mutation = null;
        state(renderer).reuses = Long.MAX_VALUE;
        run(scene, renderer, false);
        assertEquals(Long.MAX_VALUE, state(renderer).reuses);
        renderer.clearTrajectoryPreparation();
        assertEquals(0, state(renderer).reuses);
        assertTrue(state(renderer).requested, "clear does not change the request");
    }

    @Test void synchronousGetterClearAndEarlyGetterReentrancyUseOriginalCalls() throws Exception {
        Scene scene = new Scene(1, 16);
        String expected = output(run(scene, baseline, true));
        ProjectileTrajectoryRenderer renderer = new ProjectileTrajectoryRenderer(null);
        renderer.setTrajectoryPreparation(true);
        scene.targets.get(0).boxHook = () -> {
            if (scene.targets.get(0).boxCalls == 2) renderer.clearTrajectoryPreparation();
        };
        assertEquals(expected, output(run(scene, renderer, false)));
        assertEquals(0, state(renderer).reuses);
        assertNull(state(renderer).current);
        scene.targets.get(0).boxHook = null;
        final boolean[] nested = {false};
        final Object[] arm = {baseline};
        scene.shooter.heldHook = () -> {
            if (!nested[0]) {
                nested[0] = true;
                try { (arm[0] == baseline ? BASE : CAND).invoke(arm[0], scene.mc, scene.shooter, .5F); }
                catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            }
        };
        expected = output(run(scene, baseline, true));
        nested[0] = false; arm[0] = renderer;
        renderer.clearTrajectoryPreparation();
        assertEquals(expected, output(run(scene, renderer, false)));
        assertEquals(0, state(renderer).reuses);
        assertEquals("reentrancy", state(renderer).fallback);
        assertNull(state(renderer).current);
    }

    @Test void renderCallbackCounterDoesNotPretendInactiveCallbacksAreFrames() throws Exception {
        OptimalZoneMod mod = new OptimalZoneMod();
        ProjectileTrajectoryRenderer renderer = new ProjectileTrajectoryRenderer(mod);
        Scene scene = new Scene(1, 16);
        scene.mc.gameSettings = allocate(GameSettings.class);
        scene.mc.gameSettings.hideGUI = true;
        renderer.render(null); // Returns before event access/GL, preserving the existing hideGUI gate.
        renderer.setTrajectoryPreparation(true);
        renderer.render(null);
        assertEquals(2, state(renderer).renderCalls);
        assertEquals(1, state(renderer).requestedRenderCalls);
        assertEquals(0, state(renderer).computeCalls);
        assertEquals(0, state(renderer).activeComputations);
        assertTrue(mod.shouldRenderProjectiles());
    }

    @Test void drawGradientMarkersAndGlCodeRemainByteForByteOriginal() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/atw/optimalzone/render/ProjectileTrajectoryRenderer.java")).replace("\r\n", "\n");
        String original = Files.readString(Path.of("src/test/java/com/atw/optimalzone/render/BaselineProjectileTrajectoryRenderer.java"))
                .replace("BaselineProjectileTrajectoryRenderer", "ProjectileTrajectoryRenderer").replace("\r\n", "\n");
        String seam = "    private void drawTrajectory(";
        assertEquals(original.substring(original.indexOf(seam)), source.substring(source.indexOf(seam)));
    }
}
