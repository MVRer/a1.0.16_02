package com.forzacode.a1016_02.accident;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.MarkedDeath;
import com.forzacode.a1016_02.core.TraceLedger;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Game tests of the death marker and its cross (D-050, D-051): the Latin shape cell by cell, built only by moving
 * ground from around the spot and never glass, the tallest Latin cross the material allows (never a stub), nothing
 * while the spot is in view, the {@code mark} preview, and {@code lastCrossPos}.
 */
public class CrossGameTests extends TrapGameTests {
	private static final AtomicInteger MARKED_EVENTS = new AtomicInteger();
	private static final String CAUSE = "accident:cross";

	static {
		HerobrineEvents.MARKED_DEATH.register((player, cause, pos) -> MARKED_EVENTS.incrementAndGet());
	}

	@GameTest
	public void latinCrossCells(GameTestHelper helper) {
		BlockPos b = new BlockPos(40, 64, -20);
		// 5 tall: the post y0 to y4, the arms on y3 (one block of post above them, three below).
		List<BlockPos> five = List.of(b, b.above(1), b.above(2), b.above(3), b.above(4), b.above(3).east(), b.above(3).west());
		// 6 tall: the post y0 to y5, the arms on y4 (one block above them, four below).
		List<BlockPos> six = List.of(b, b.above(1), b.above(2), b.above(3), b.above(4), b.above(5), b.above(4).east(), b.above(4).west());
		List<BlockPos> sixAlongZ = List.of(b, b.above(1), b.above(2), b.above(3), b.above(4), b.above(5), b.above(4).south(), b.above(4).north());
		helper.assertTrue(CrossBuilder.cells(b, 5, Direction.Axis.X).equals(five), "5 tall: " + CrossBuilder.cells(b, 5, Direction.Axis.X));
		helper.assertTrue(CrossBuilder.cells(b, 6, Direction.Axis.X).equals(six), "6 tall: " + CrossBuilder.cells(b, 6, Direction.Axis.X));
		helper.assertTrue(CrossBuilder.cells(b, 6, Direction.Axis.Z).equals(sixAlongZ), "6 tall along z: " + CrossBuilder.cells(b, 6, Direction.Axis.Z));
		// New crosses are 5 or 6 tall, and a saved config with the old 3 to 4 cannot make a plus sign.
		RandomSource random = RandomSource.create(1016);
		Set<Integer> heights = new HashSet<>();
		for (int i = 0; i < 64; i++) {
			heights.add(CrossBuilder.height(random, new AccidentConfig()));
		}
		helper.assertTrue(heights.equals(Set.of(5, 6)), "default heights " + heights);
		AccidentConfig stale = new AccidentConfig();
		stale.latinCrossMinHeight = 3;
		stale.latinCrossMaxHeight = 4;
		for (int i = 0; i < 16; i++) {
			int height = CrossBuilder.height(random, stale);
			helper.assertTrue(height == CrossBuilder.MIN_HEIGHT, "a " + height + " tall cross from a config of 3 to 4");
		}
		helper.succeed();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void crossOfFiveStandsOnExactlyItsCellsByMovesAlone(GameTestHelper helper) {
		standsExactly(helper, 5);
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void crossOfSixStandsOnExactlyItsCellsByMovesAlone(GameTestHelper helper) {
		standsExactly(helper, 6);
	}

	/**
	 * A cross of {@code height} over a dirt yard with glass set into the ground right by the spot: exactly its cells
	 * stand, all dirt, the ledger shows one move per cell from the ground nearby, and the glass is never taken.
	 */
	private static void standsExactly(GameTestHelper helper, int height) {
		Yard y = dirtYard(helper);
		List<BlockPos> glass = List.of(y.abs(12, 1, 10), y.abs(14, 1, 12), y.abs(10, 1, 13), y.abs(13, 1, 14));
		List<Block> kinds = List.of(Blocks.GLASS, Blocks.TINTED_GLASS, Blocks.GLASS, Blocks.TINTED_GLASS);
		for (int i = 0; i < glass.size(); i++) {
			y.level.setBlockAndUpdate(glass.get(i), kinds.get(i).defaultBlockState());
		}
		MinecraftServer server = y.level.getServer();
		BlockPos death = y.abs(12, 2, 12);
		AccidentData.PendingCross cross = pending(y, death, height);
		TraceLedger ledger = TraceLedger.get(server);
		int before = ledger.entries().size();

		helper.assertTrue(new DeathMarkerImpl(s -> y.data, Yard.NOBODY).build(server, cross), "the cross was not built out of view: " + y.data.history());
		helper.assertTrue(y.data.crosses().isEmpty(), "the cross is still waiting");
		List<BlockPos> cells = CrossBuilder.cells(death, height, Direction.Axis.X);
		standsOn(helper, y, cells);
		movesOnly(helper, y, ledger.entries().subList(before, ledger.entries().size()), cells);
		for (int i = 0; i < glass.size(); i++) {
			helper.assertTrue(y.level.getBlockState(glass.get(i)).is(kinds.get(i)), "his cross took glass at " + glass.get(i));
		}
		y.succeedWithoutDrops();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void shortOfMaterialItIsTheTallestLatinCrossThatFits(GameTestHelper helper) {
		Yard y = bareYard(helper, 7);
		MinecraftServer server = y.level.getServer();
		BlockPos death = y.abs(12, 2, 12);
		AccidentData.PendingCross cross = pending(y, death, 6);
		TraceLedger ledger = TraceLedger.get(server);
		int before = ledger.entries().size();

		// 7 blocks: not enough for 6 tall (8), enough for 5 tall (7).
		helper.assertTrue(new DeathMarkerImpl(s -> y.data, Yard.NOBODY).build(server, cross), "no cross from 7 blocks: " + y.data.history());
		List<BlockPos> cells = CrossBuilder.cells(death, 5, Direction.Axis.X);
		standsOn(helper, y, cells);
		movesOnly(helper, y, ledger.entries().subList(before, ledger.entries().size()), cells);
		helper.assertTrue(y.data.crosses().isEmpty(), "the cross is still waiting");
		y.succeedWithoutDrops();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void tooLittleForALatinCrossBuildsNoStubAndKeepsTrying(GameTestHelper helper) {
		Yard y = bareYard(helper, 6);
		MinecraftServer server = y.level.getServer();
		BlockPos death = y.abs(12, 2, 12);
		AccidentData.PendingCross cross = pending(y, death, 6);
		TraceLedger ledger = TraceLedger.get(server);
		int before = ledger.entries().size();
		DeathMarkerImpl marker = new DeathMarkerImpl(s -> y.data, Yard.NOBODY);

		// 6 blocks would make the old 4 tall plus sign; a Latin cross needs 7 at least.
		helper.assertFalse(marker.build(server, cross), "a cross went up from 6 blocks");
		standsOn(helper, y, List.of());
		helper.assertTrue(holes(y) == 0 && ledger.entries().size() == before, "something moved without a cross to build");
		helper.assertTrue(y.data.crosses().equals(List.of(new AccidentData.PendingCross(cross.pos(), cross.cause(), 6, 1))),
				"the cross is not waiting to be tried again: " + y.data.crosses());
		marker.tick(server);
		helper.assertTrue(y.data.crosses().size() == 1 && y.data.crosses().getFirst().attempts() == 2, "not tried again: " + y.data.crosses());
		standsOn(helper, y, List.of());
		helper.succeed();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void nothingMovesWhileTheSpotIsInView(GameTestHelper helper) {
		Yard y = dirtYard(helper);
		MinecraftServer server = y.level.getServer();
		BlockPos death = y.abs(12, 2, 12);
		AccidentData.PendingCross cross = pending(y, death, 6);
		TraceLedger ledger = TraceLedger.get(server);
		int before = ledger.entries().size();

		// Someone 8.5 blocks north looking south at the spot, then everyone everywhere.
		for (ViewGate seen : List.of(y.viewer(12.5, 2, 3.5, 0, 0), Yard.EVERYONE)) {
			helper.assertFalse(new DeathMarkerImpl(s -> y.data, seen).build(server, cross), "the cross went up in view");
			standsOn(helper, y, List.of());
			helper.assertTrue(holes(y) == 0 && ledger.entries().size() == before, "something moved in view");
			helper.assertTrue(y.data.crosses().equals(List.of(cross)), "being in view counted against the cross: " + y.data.crosses());
		}
		// The same viewer turned around: it stands.
		helper.assertTrue(new DeathMarkerImpl(s -> y.data, y.viewer(12.5, 2, 3.5, 180, 0)).build(server, cross),
				"not built once out of view: " + y.data.history());
		standsOn(helper, y, CrossBuilder.cells(death, 6, Direction.Axis.X));
		y.succeedWithoutDrops();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void markPreviewBuildsALatinCross(GameTestHelper helper) {
		Yard y = dirtYard(helper);
		MinecraftServer server = y.level.getServer();
		BlockPos spot = y.abs(12, 2, 12);
		int deaths = HerobrineState.get(server).markedDeaths().size();
		int events = MARKED_EVENTS.get();

		helper.assertTrue(new DeathMarkerImpl(s -> y.data, Yard.NOBODY).preview(y.player, "fell", spot), "the preview did not stand: " + y.data.history());
		int height = dirtAbove(y) - 2;
		helper.assertTrue(height == 5 || height == 6, "a preview cross of " + (height + 2) + " blocks");
		standsOn(helper, y, CrossBuilder.cells(spot, height, Direction.Axis.X));
		helper.assertTrue(HerobrineState.get(server).markedDeaths().size() == deaths && MARKED_EVENTS.get() == events && y.data.lastCross().isEmpty(),
				"the preview recorded a death");
		y.succeedWithoutDrops();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void markedDeathLeavesACrossBuiltFromTheGround(GameTestHelper helper) {
		Yard y = dirtYard(helper);
		ServerLevel level = y.level;
		BlockPos death = y.abs(12, 2, 12);
		DeathMarkerImpl watched = new DeathMarkerImpl(server -> y.data, Yard.EVERYONE);
		DeathMarkerImpl unseen = new DeathMarkerImpl(server -> y.data, Yard.NOBODY);
		int deaths = HerobrineState.get(level.getServer()).markedDeaths().size();
		int events = MARKED_EVENTS.get();

		watched.mark(y.player, "fell", death);
		List<MarkedDeath> marked = HerobrineState.get(level.getServer()).markedDeaths();
		helper.assertTrue(marked.size() == deaths + 1, "the death was not recorded");
		MarkedDeath last = marked.get(marked.size() - 1);
		helper.assertTrue(last.cause().equals("fell") && last.pos().pos().equals(death) && last.pos().dimension().equals(level.dimension()), "wrong record " + last);
		helper.assertTrue(MARKED_EVENTS.get() > events, "MARKED_DEATH did not fire");
		helper.assertTrue(y.data.crosses().size() == 1 && dirtAbove(y) == 0, "the cross went up in view");

		unseen.tick(level.getServer());
		helper.assertTrue(y.data.crosses().isEmpty(), "the cross was not built out of view");
		int raised = dirtAbove(y);
		int height = raised - 2;
		helper.assertTrue(height >= CrossBuilder.MIN_HEIGHT && height <= CrossBuilder.MAX_HEIGHT, "cross of " + raised + " blocks; " + y.data.history()
				+ " column " + List.of(level.getBlockState(death), level.getBlockState(death.above()), level.getBlockState(death.below())));
		helper.assertTrue(holes(y) == raised, "the cross is not made of blocks taken from the ground: " + holes(y) + " holes, " + raised + " blocks");
		Optional<BlockPos> foot = crossFoot(y, height);
		helper.assertTrue(foot.isPresent(), "the blocks do not stand as a Latin cross");
		// DeathMarker.lastCrossPos: the post's bottom, for the ending's sign.
		Optional<GlobalPos> base = unseen.lastCrossPos(level.getServer());
		helper.assertTrue(base.equals(foot.map(p -> GlobalPos.of(level.dimension(), p))) && base.get().pos().closerThan(death, 10),
				"lastCrossPos " + base + " is not the foot of the cross at " + foot);
		helper.assertTrue(unseen.crossFor(level.getServer(), GlobalPos.of(level.dimension(), death)).equals(base), "crossFor does not give this death's cross");
		helper.assertTrue(unseen.crossFor(level.getServer(), GlobalPos.of(level.dimension(), death.east())).isEmpty(), "crossFor gave the cross to another death");
		y.succeedWithoutDrops();
	}

	/** Stone under a dirt ground layer (y = 1), the whole yard. */
	private static Yard dirtYard(GameTestHelper helper) {
		Yard y = new Yard(helper);
		y.fill(0, 0, 0, 23, 0, 23, Blocks.STONE);
		y.fill(0, 1, 0, 23, 1, 23, Blocks.DIRT);
		return y;
	}

	/** Bedrock ground (nothing he can take) with {@code dirt} blocks of dirt set into it in a row 4 blocks from the spot. */
	private static Yard bareYard(GameTestHelper helper, int dirt) {
		Yard y = new Yard(helper);
		y.fill(0, 0, 0, 23, 0, 23, Blocks.STONE);
		y.fill(0, 1, 0, 23, 1, 23, Blocks.BEDROCK);
		for (int i = 0; i < dirt; i++) {
			y.set(9 + i, 1, 8, Blocks.DIRT);
		}
		return y;
	}

	/** A marked death's cross waiting at {@code pos}, {@code height} tall. */
	private static AccidentData.PendingCross pending(Yard y, BlockPos pos, int height) {
		AccidentData.PendingCross cross = new AccidentData.PendingCross(GlobalPos.of(y.level.dimension(), pos), "fell", height, 0);
		y.data.addCross(cross);
		return cross;
	}

	/** Above the ground, dirt stands on exactly {@code cells} and everything else is air. */
	private static void standsOn(GameTestHelper helper, Yard y, List<BlockPos> cells) {
		Set<BlockPos> wanted = Set.copyOf(cells);
		for (BlockPos pos : BlockPos.betweenClosed(y.abs(0, 2, 0), y.abs(23, 10, 23))) {
			boolean cell = wanted.contains(pos);
			helper.assertTrue(cell ? y.level.getBlockState(pos).is(Blocks.DIRT) : y.level.getBlockState(pos).isAir(),
					(cell ? "no dirt in the cross at " : "something stands outside the cross at ") + pos.immutable() + "; " + y.data.history());
		}
	}

	/** The new ledger entries are one move per cell, each from what is now a hole in the dirt ground, and nothing else. */
	private static void movesOnly(GameTestHelper helper, Yard y, List<TraceLedger.Entry> added, List<BlockPos> cells) {
		helper.assertTrue(added.size() == cells.size(), "not one ledger entry per cell: " + added);
		int ground = y.abs(0, 1, 0).getY();
		for (TraceLedger.Entry entry : added) {
			BlockPos from = entry.pos().pos();
			helper.assertTrue(entry.kind() == TraceLedger.Kind.MOVE && entry.cause().equals(CAUSE), "not a move for the cross: " + entry);
			helper.assertTrue(from.getY() == ground && y.level.getBlockState(from).isAir() && entry.state().map(s -> s.is(Blocks.DIRT)).orElse(false),
					"not taken from the ground: " + entry);
		}
		Set<BlockPos> landed = added.stream().map(entry -> entry.to().orElseThrow()).collect(Collectors.toSet());
		helper.assertTrue(landed.equals(Set.copyOf(cells)), "the moves do not land on the cells: " + landed);
		helper.assertTrue(holes(y) == cells.size(), holes(y) + " holes in the ground for " + cells.size() + " blocks");
	}

	/** Dirt standing above the ground (y >= 2). */
	private static int dirtAbove(Yard y) {
		int n = 0;
		for (BlockPos pos : BlockPos.betweenClosed(y.abs(0, 2, 0), y.abs(23, 10, 23))) {
			n += y.level.getBlockState(pos).is(Blocks.DIRT) ? 1 : 0;
		}
		return n;
	}

	/** Missing blocks in the ground layer. */
	private static int holes(Yard y) {
		int n = 0;
		for (BlockPos pos : BlockPos.betweenClosed(y.abs(0, 1, 0), y.abs(23, 1, 23))) {
			n += y.level.getBlockState(pos).isAir() ? 1 : 0;
		}
		return n;
	}

	/** The foot of a dirt Latin cross of {@code height} standing on the ground, if there is one. */
	private static Optional<BlockPos> crossFoot(Yard y, int height) {
		for (BlockPos pos : BlockPos.betweenClosed(y.abs(0, 2, 0), y.abs(23, 2, 23))) {
			if (!y.level.getBlockState(pos).is(Blocks.DIRT)) {
				continue;
			}
			BlockPos base = pos.immutable();
			for (Direction.Axis axis : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z}) {
				if (CrossBuilder.cells(base, height, axis).stream().allMatch(c -> y.level.getBlockState(c).is(Blocks.DIRT))) {
					return Optional.of(base);
				}
			}
		}
		return Optional.empty();
	}
}
