package com.forzacode.a1016_02.dig;

import java.util.List;

import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.SiteType;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

/** Card tunnel game tests: the tunnel that grows, the tunnel into a mine, a plain tunnel. */
public class TunnelGameTests extends NetworkGameTests {
	@GameTest(maxTicks = 100)
	public void tunnelThatGrowsTenPerVisitAndStopsShortOfTheBase(GameTestHelper helper) {
		DigConfig config = new DigConfig();
		config.growingStartMin = 44;
		config.growingStartMax = 56;
		int growth = ModConfig.pacing().tunnelGrowthPerVisit;
		int clearance = config.tunnelPlayerClearance;
		DigGround g = DigGround.of(helper, 4, 64, 12, 24, Blocks.STONE);
		// The base: a room the player dug at the east end. A natural cave at the west end that the player explored.
		g.digBox(52, 4, 8, 60, 6, 14);
		BlockPos base = g.at(56, 4, 11);
		g.hollow(2, 4, 8, 5, 6, 14);
		PosSet explored = new PosSet();
		explored.add(g.at(3, 4, 11), Integer.MAX_VALUE);

		GrowingTunnel tunnel = GrowingTunnel.start(g.level, base, explored, config, clearance, growth, RandomSource.create(7L), Services.traces(), 0);
		helper.assertTrue(tunnel != null, "no tunnel started");
		helper.assertTrue(tunnel.length() == growth, "first length " + tunnel.length());
		helper.assertTrue(tunnel.dir == Direction.EAST, "it does not point at the base: " + tunnel.dir);
		double before = tunnel.distanceToBase(tunnel.end());
		int visits = 0;
		while (!tunnel.complete && visits < 12) {
			visits++;
			int length = tunnel.length();
			tunnel.visited = true;
			boolean grown = tunnel.grow(g.level, growth, clearance, config, Services.traces(), visits);
			helper.assertTrue(grown || tunnel.complete, "visit " + visits + " did not grow it");
			int added = tunnel.length() - length;
			helper.assertTrue(tunnel.complete ? added <= growth : added == growth, "visit " + visits + " added " + added);
			double now = tunnel.distanceToBase(tunnel.end());
			helper.assertTrue(now < before || added == 0, "it is not getting closer to the base");
			before = now;
		}
		helper.assertTrue(tunnel.complete, "never stopped");
		helper.assertTrue(tunnel.distanceToBase(tunnel.end()) > config.growingStopShortOfBase - 1, "it ran into the base");
		LongOpenHashSet cells = Tunnels.cells(tunnel.anchors);
		for (long packed : cells) {
			BlockPos cell = BlockPos.of(packed);
			helper.assertTrue(g.level.getBlockState(cell).isAir(), "cell not carved: " + cell);
			helper.assertTrue(minCheb(cell, g.dug) > clearance, "it came within " + clearance + " of the room at " + cell);
		}
		SiteRegistry.Site site = site(tunnel.siteId);
		helper.assertTrue(site.type() == SiteType.TUNNEL_END && site.size() == tunnel.length() && site.pos().equals(tunnel.end()), "site " + site);
		helper.assertFalse(tunnel.grow(g.level, growth, clearance, config, Services.traces(), 99), "a complete tunnel grew");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void tunnelThatGrowsKeepsItsSiteCurrentFromTheFirstVisit(GameTestHelper helper) {
		DigConfig config = new DigConfig();
		config.growingStartMin = 44;
		config.growingStartMax = 56;
		int growth = ModConfig.pacing().tunnelGrowthPerVisit;
		int clearance = config.tunnelPlayerClearance;
		DigGround g = DigGround.of(helper, 15, 64, 12, 24, Blocks.STONE);
		g.digBox(52, 4, 8, 60, 6, 14);
		BlockPos base = g.at(56, 4, 11);
		g.hollow(2, 4, 8, 5, 6, 14);
		PosSet explored = new PosSet();
		explored.add(g.at(3, 4, 11), Integer.MAX_VALUE);
		GrowingTunnel tunnel = GrowingTunnel.start(g.level, base, explored, config, clearance, growth, RandomSource.create(7L), Services.traces(), 0);
		helper.assertTrue(tunnel != null, "no tunnel started");
		helper.assertTrue(tunnel.siteId < 0 && tunnel.site().isEmpty(), "a site before the player ever came by");

		// The first visit records it at the end, sized by the length.
		tunnel.visit();
		int id = tunnel.siteId;
		assertSiteCurrent(helper, tunnel, id);
		// Each growth after a visit moves the same site to the new end and length.
		for (int visit = 1; visit <= 2; visit++) {
			tunnel.visited = false;
			tunnel.visit();
			int length = tunnel.length();
			helper.assertTrue(tunnel.grow(g.level, growth, clearance, config, Services.traces(), visit) && tunnel.length() > length,
					"visit " + visit + " did not grow it");
			assertSiteCurrent(helper, tunnel, id);
		}
		helper.assertTrue(Services.sites().all().stream().filter(site -> site.type() == SiteType.TUNNEL_END && site.id() != id)
				.noneMatch(site -> tunnel.anchors.contains(site.pos())), "more than one site for one tunnel");

		// Lore claims it (the longest tunnel): what it left at the end stays at the end, so the tunnel stops there.
		helper.assertTrue(Services.sites().claim(site(id), "f06"), "claim refused");
		BlockPos end = tunnel.end();
		int length = tunnel.length();
		helper.assertFalse(tunnel.grow(g.level, growth, clearance, config, Services.traces(), 3), "a claimed tunnel grew");
		helper.assertTrue(tunnel.complete && tunnel.end().equals(end) && tunnel.length() == length, "a claimed tunnel moved its end");
		SiteRegistry.Site claimed = site(id);
		helper.assertTrue(claimed.claimedBy().filter("f06"::equals).isPresent() && claimed.pos().equals(end) && claimed.size() == length,
				"the claimed site changed: " + claimed);
		helper.succeed();
	}

	private static void assertSiteCurrent(GameTestHelper helper, GrowingTunnel tunnel, int id) {
		helper.assertTrue(id >= 0 && tunnel.siteId == id, "site id " + tunnel.siteId + ", expected " + id);
		SiteRegistry.Site site = site(id);
		helper.assertTrue(site.type() == SiteType.TUNNEL_END && site.dimension().equals(tunnel.dimension) && site.pos().equals(tunnel.end())
				&& site.size() == tunnel.length(), "site " + site + " is not at the end " + tunnel.end() + " with length " + tunnel.length());
	}

	@GameTest(maxTicks = 100)
	public void tunnelIntoMineBreaksThroughAndEndsInStone(GameTestHelper helper) {
		DigConfig config = new DigConfig();
		DigGround g = DigGround.of(helper, 5, 48, 10, 48, Blocks.STONE);
		// A 1-wide, 2-high mine running north-south.
		g.digBox(24, 3, 6, 24, 4, 40);
		BlockPos away = g.at(24, 3, -60);
		CardTunnels.Carved carved = CardTunnels.intoMine(g.level, away, config, config.tunnelPlayerClearance, RandomSource.create(3L), Services.traces());
		helper.assertTrue(carved != null, "no tunnel into the mine");
		helper.assertTrue(carved.dir().getAxis() == Direction.Axis.X, "it does not lead away from the mine: " + carved.dir());
		helper.assertTrue(carved.anchors().size() >= Math.min(8, config.intoMineLengthMin), "too short: " + carved.anchors().size());
		LongOpenHashSet cells = Tunnels.cells(carved.anchors());
		boolean opens = false;
		for (long packed : cells) {
			BlockPos cell = BlockPos.of(packed);
			helper.assertTrue(g.level.getBlockState(cell).isAir(), "cell not carved: " + cell);
			for (Direction dir : Direction.values()) {
				BlockPos n = cell.relative(dir);
				if (cells.contains(n.asLong())) {
					continue;
				}
				if (g.dug.contains(n)) {
					opens = true;
				} else {
					helper.assertTrue(Tunnels.seals(g.level.getBlockState(n)), "it opens into " + n);
				}
			}
		}
		helper.assertTrue(opens, "it does not break into the mine");
		// It ends in stone: the next step is solid.
		for (BlockPos cell : Tunnels.cube(carved.end().relative(carved.dir()))) {
			if (!cells.contains(cell.asLong())) {
				helper.assertTrue(g.level.getBlockState(cell).is(Blocks.STONE), "it does not end in stone at " + cell);
			}
		}
		SiteRegistry.Site site = site(carved.siteId());
		helper.assertTrue(site.type() == SiteType.TUNNEL_END && site.size() == carved.anchors().size(), "site " + site);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void plainTunnelIsTwoByTwoThroughStone(GameTestHelper helper) {
		DigConfig config = new DigConfig();
		DigGround g = DigGround.of(helper, 6, 80, 20, 80, Blocks.STONE);
		BlockPos near = g.at(40, 20, 40);
		CardTunnels.Carved carved = CardTunnels.plain(g.level, near, config, config.tunnelPlayerClearance, RandomSource.create(11L), Services.traces());
		helper.assertTrue(carved != null, "no tunnel");
		helper.assertTrue(carved.anchors().size() >= config.demoTunnelLength / 2, "too short");
		List<BlockPos> anchors = carved.anchors();
		for (int i = 1; i < anchors.size(); i++) {
			helper.assertTrue(Tunnels.cheb(anchors.get(i), anchors.get(i - 1)) == 1 && anchors.get(i).getY() == anchors.get(i - 1).getY(),
					"not a connected level 2x2 run at " + anchors.get(i));
		}
		for (long packed : Tunnels.cells(anchors)) {
			helper.assertTrue(g.level.getBlockState(BlockPos.of(packed)).isAir(), "cell not carved");
		}
		helper.assertTrue(site(carved.siteId()).size() == anchors.size(), "TUNNEL_END size is not the length");
		helper.succeed();
	}
}
