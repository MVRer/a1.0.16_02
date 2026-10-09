package com.forzacode.a1016_02.core;

import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.Curves;
import com.forzacode.a1016_02.world.live.NewScarPlacer;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Tests for the core contract batch (fog limits, salts, sites, protected areas, clock); {@link TraceContractTests} extends this. */
public class CoreContractTests {
	@GameTest
	public void fogLimitsMatchAtmosphereClientCurve(GameTestHelper helper) {
		AtmosphereConfig cfg = new AtmosphereConfig();
		helper.assertTrue(cfg.fogShape().equals(FogLimits.Shape.DEFAULT), "FogLimits' default shape is not atmosphere's default");
		FogLimits.Shape shape = cfg.fogShape();
		for (int client : new int[] {0, 2, 6, 12, 32}) {
			for (int server : new int[] {2, 10, 32}) {
				int chunks = client > 0 ? Math.min(client, server) : server;
				double base = Math.max(chunks, 2) * 16.0;
				for (float dusk : new float[] {0.0F, 0.15F, 0.45F, 0.7F, 1.0F}) {
					for (long time = 0; time < 24000; time += 250) {
						FogLimits.Result result = FogLimits.of(client, server, dusk, time, shape);
						// What atmosphere's client fog does in the overworld, where the render limit is the base.
						double clientEnd = Curves.fogEnd(base, cfg.duskMinFogBlocks, dusk * Curves.duskWeight(time, cfg.duskNightWeight));
						helper.assertTrue(result.renderLimit() == base, "render limit " + result.renderLimit() + " for " + client + "/" + server);
						helper.assertTrue(Math.abs(result.fogEnd() - clientEnd) < 1.0E-9,
								"fog end " + result.fogEnd() + " != client " + clientEnd + " at " + client + "/" + server + " dusk " + dusk + " t " + time);
						helper.assertTrue(result.fogEnd() <= result.renderLimit() + 1.0E-9, "fog pushed out past the render limit");
					}
				}
			}
		}
		FogLimits.Result noon = FogLimits.of(12, 12, 1.0F, 6000, shape);
		FogLimits.Result dusk = FogLimits.of(12, 12, 1.0F, 13000, shape);
		helper.assertTrue(noon.fogEnd() == 192.0 && Math.abs(dusk.fogEnd() - cfg.duskMinFogBlocks) < 1.0E-9, "no dusk fog by day, full at dusk");
		helper.succeed();
	}

	@GameTest
	public void worldgenSaltStaysThroughReroll(GameTestHelper helper) {
		HerobrineState state = new HerobrineState();
		state.reroll(42L, 5L);
		long salt = state.worldgenSalt();
		helper.assertTrue(salt == HerobrineState.deriveWorldgenSalt(5L) && salt != 5L, "worldgen salt not derived from the profile salt");
		state.reroll(42L, 6L);
		helper.assertTrue(state.worldgenSalt() == salt, "a reroll changed the worldgen salt");

		RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		Tag saved = HerobrineState.CODEC.encodeStart(ops, state).getOrThrow();
		HerobrineState loaded = HerobrineState.CODEC.parse(ops, saved).getOrThrow();
		helper.assertTrue(loaded.worldgenSalt() == salt && loaded.salt() == 6L, "salts did not round-trip");

		// A world saved before the worldgen salt existed derives it from its profile salt once, then keeps it.
		HerobrineState old = new HerobrineState();
		old.reroll(42L, 9L);
		Tag oldSave = HerobrineState.CODEC.encodeStart(ops, old).getOrThrow();
		((CompoundTag) oldSave).remove("worldgenSalt");
		helper.assertTrue(HerobrineState.CODEC.parse(ops, oldSave).getOrThrow().worldgenSalt() == HerobrineState.deriveWorldgenSalt(9L),
				"an old save did not derive its worldgen salt");
		helper.succeed();
	}

	@GameTest
	public void siteUpdateKeepsIdTypeAndClaim(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos far = new BlockPos(-29_000_000, 70, -29_000_123);
		SiteRegistry sites = Services.sites();
		SiteRegistry.Site site = sites.record(SiteType.TUNNEL_END, level.dimension(), far, 2);
		helper.assertTrue(sites.claim(site, "F99"), "claim failed");
		Optional<SiteRegistry.Site> moved = sites.update(site, far.east(9), 5);
		helper.assertTrue(moved.isPresent() && moved.get().id() == site.id() && moved.get().pos().equals(far.east(9)) && moved.get().size() == 5
				&& moved.get().type() == SiteType.TUNNEL_END && moved.get().claimedBy().equals(Optional.of("F99")), "update lost something");
		List<SiteRegistry.Site> found = sites.find(SiteType.TUNNEL_END, GlobalPos.of(level.dimension(), far.east(9)), 0);
		helper.assertTrue(found.stream().anyMatch(s -> s.id() == site.id()), "the moved site is not where it was moved");
		helper.assertTrue(sites.find(SiteType.TUNNEL_END, GlobalPos.of(level.dimension(), far), 0).stream().noneMatch(s -> s.id() == site.id()),
				"the site is still where it was");
		helper.assertTrue(sites.update(new SiteRegistry.Site(-1, SiteType.CUT, Level.OVERWORLD, far, 1, Optional.empty()), far, 1).isEmpty(),
				"an unknown site was updated");
		helper.succeed();
	}

	@GameTest
	public void protectedAreasAnswerAndRoundTrip(GameTestHelper helper) {
		ProtectedAreas areas = new ProtectedAreas();
		BoundingBox grove = new BoundingBox(0, 60, 0, 10, 90, 10);
		areas.protect("test:grove", Level.OVERWORLD, grove);
		helper.assertTrue(areas.isProtected(Level.OVERWORLD, new BlockPos(5, 70, 5)), "inside not protected");
		helper.assertFalse(areas.isProtected(Level.OVERWORLD, new BlockPos(11, 70, 5)), "outside protected");
		helper.assertFalse(areas.isProtected(Level.NETHER, new BlockPos(5, 70, 5)), "protected in another dimension");
		helper.assertTrue(!areas.intersects(Level.OVERWORLD, new BoundingBox(10, 0, 10, 20, 0, 20))
				&& areas.intersects(Level.OVERWORLD, new BoundingBox(10, 0, 10, 20, 100, 20)), "intersects");
		areas.protect("test:grove", Level.OVERWORLD, new BoundingBox(100, 60, 100, 110, 90, 110));
		helper.assertTrue(areas.all().size() == 1 && !areas.isProtected(Level.OVERWORLD, new BlockPos(5, 70, 5)), "protecting the same id again did not move it");

		RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		ProtectedAreas.Data data = new ProtectedAreas.Data();
		data.areas.put("test:grove", new ProtectedAreas.Area("test:grove", Level.OVERWORLD, grove));
		ProtectedAreas.Data loaded = ProtectedAreas.Data.CODEC.parse(ops, ProtectedAreas.Data.CODEC.encodeStart(ops, data).getOrThrow()).getOrThrow();
		helper.assertTrue(loaded.areas.equals(data.areas), "protected areas did not round-trip");
		helper.assertTrue(areas.unprotect("test:grove") && areas.all().isEmpty() && !areas.unprotect("test:grove"), "unprotect");
		helper.succeed();
	}

	/** Sky access: the new-scar column scan starts at the top of the world. */
	@GameTest(skyAccess = true)
	public void newScarSkipsProtectedColumns(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.DIRT);
			}
		}
		BlockPos kept = new BlockPos(3, 3, 4);
		BlockPos bare = new BlockPos(5, 3, 4);
		helper.setBlock(kept, Blocks.OAK_LEAVES);
		helper.setBlock(bare, Blocks.OAK_LEAVES);
		BlockPos keptAbs = helper.absolutePos(kept);
		String id = "test:grove_" + keptAbs.toShortString();
		Services.protectedAreas().protect(id, level.dimension(), new BoundingBox(keptAbs.getX(), level.getMinY(), keptAbs.getZ(),
				keptAbs.getX(), level.getMaxY(), keptAbs.getZ()));
		try {
			List<NewScarPlacer.Edit> edits = NewScarPlacer.collectBare(level, helper.absolutePos(new BlockPos(4, 1, 4)), 2, chunk -> true);
			helper.assertTrue(edits.stream().anyMatch(e -> e.pos().equals(helper.absolutePos(bare))), "the unprotected leaves were not planned");
			helper.assertTrue(edits.stream().noneMatch(e -> e.pos().equals(keptAbs)), "a protected column was planned");
		} finally {
			Services.protectedAreas().unprotect(id);
		}
		helper.succeed();
	}

	@GameTest
	public void dayTicksAndStubTimewarp(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		long time = Math.floorMod(server.overworld().getOverworldClockTime(), GameClock.TICKS_PER_DAY);
		helper.assertTrue(GameClock.dayTicks(server) == GameClock.day(server) * GameClock.TICKS_PER_DAY + time, "dayTicks");
		helper.assertFalse(new Director.Stub().timewarp(server, 0).isEmpty(), "the stub's timewarp has no summary");
		helper.assertFalse(new MobTamper.Stub().isTampered(null), "the stub tampers");
		helper.succeed();
	}
}
