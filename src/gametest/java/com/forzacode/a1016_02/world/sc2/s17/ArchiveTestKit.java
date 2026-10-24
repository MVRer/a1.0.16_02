package com.forzacode.a1016_02.world.sc2.s17;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Figure;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.lore.Telling;
import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.live.NewScarPlacer;
import com.forzacode.a1016_02.world.sc.TorchTrail;
import com.forzacode.a1016_02.world.sc2.kit.Answers;
import com.forzacode.a1016_02.world.sc2.kit.Live;
import com.forzacode.a1016_02.world.sc2.kit.Recorder;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Data;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Piece;
import com.forzacode.a1016_02.world.sc2.kit.Siting;
import com.forzacode.a1016_02.world.sc2.kit.Snapshot;
import com.forzacode.a1016_02.world.sc2.kit.Start;
import com.forzacode.a1016_02.world.sc2.kit.Terrains;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;

/**
 * SC2-2's game test bodies (docs/SELF_CREATED_2.md, feature 17), in the archive's own package so they reach its
 * package-private parts; {@code world.SelfCreated2ArchiveTests} (the seam class the game tests list) calls each. Every
 * test builds at its own far-off spot (x 84 000 to 89 000, z 85 000: no other test class uses them) with its own data,
 * players, view and figure: nothing reaches the live remains.
 */
public final class ArchiveTestKit {
	/** The test environment of the worldgen test. */
	public static final String ENV = "a1016_02:world/s17";
	static final int X = 84_000;
	static final int Z = 85_000;
	static final long SEED = 0x5170A1L;

	private ArchiveTestKit() {
	}

	// --- helpers ---

	/** A player only these tests know. */
	static final class Visitor extends ServerPlayer {
		Visitor(ServerLevel level, String name) {
			super(level.getServer(), level, new GameProfile(UUID.nameUUIDFromBytes(("s17:" + name).getBytes()), name), ClientInformation.createDefault());
			GameType.SURVIVAL.updatePlayerAbilities(getAbilities());
		}

		@Override
		public GameType gameMode() {
			return GameType.SURVIVAL;
		}

		@Override
		public boolean isClientAuthoritative() {
			return false;
		}
	}

	static Visitor at(ServerLevel level, String name, BlockPos feet) {
		Visitor v = new Visitor(level, name);
		v.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0.0F, 0.0F);
		return v;
	}

	static void load(ServerLevel level, BoundingBox box) {
		for (int cx = (box.minX() >> 4) - 1; cx <= (box.maxX() >> 4) + 1; cx++) {
			for (int cz = (box.minZ() >> 4) - 1; cz <= (box.maxZ() >> 4) + 1; cz++) {
				level.getChunkSource().addTicketWithRadius(NewScarPlacer.TICKET, new ChunkPos(cx, cz), 0);
				level.getChunk(cx, cz);
			}
		}
	}

	/** Stone in every cell of the box (the land the archive is dug into). */
	static void rock(ServerLevel level, BoundingBox box) {
		load(level, box);
		BlockState stone = Blocks.STONE.defaultBlockState();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				for (int y = box.minY(); y <= box.maxY(); y++) {
					level.setBlock(at.set(x, y, z), stone, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				}
			}
		}
	}

	/** The short archive facing NORTH with its hall's floor at {@code floorY}, built by {@link Live}, in a block of rock if {@code rockTop} is set. */
	static Live.Result build(ServerLevel level, int x, int z, int floorY, boolean longHall, Integer rockTop) {
		return build(level, x, z, floorY, longHall, rockTop, plan -> {
		});
	}

	/** {@link #build}, with {@code before} run on the plan measured over the land just before it is placed. */
	static Live.Result build(ServerLevel level, int x, int z, int floorY, boolean longHall, Integer rockTop, java.util.function.Consumer<ArchivePlan> before) {
		Start start = new Start(new BlockPos(x, floorY, z), Direction.NORTH, ArchivePlan.variant(longHall, 0.08), SEED);
		ArchivePlan probe = ArchivePlan.of(start, new int[0]);
		BoundingBox reach = probe.box().inflatedBy(4);
		load(level, reach);
		if (rockTop != null) {
			int length = probe.length;
			rock(level, new BoundingBox(x - 30, floorY - 12, z - (probe.tunnelF0 + 2 * (rockTop - floorY) + 20), x + 30, rockTop, z + length + 6));
		}
		before.accept(ArchivePlan.of(start, Live.terrain(level)));
		Live.Result built = Live.place(level, S17.KIND, start);
		load(level, built.box());
		return built;
	}

	static ArchivePlan plan(Live.Result built) {
		return ((ArchiveShape) built.shape()).plan();
	}

	/** One test's own world round an archive: its data, players, day and tick, view, figure and answers. */
	static final class Rig {
		final ServerLevel level;
		final Live.Result built;
		final ArchivePlan plan;
		final Remains.Place place;
		final Sc2Data data = new Sc2Data();
		final List<ServerPlayer> inside = new ArrayList<>();
		final List<ServerPlayer> people = new ArrayList<>();
		final List<HerobrineState.Subject> known = new ArrayList<>();
		final List<String> answered = new ArrayList<>();
		final List<Vec3> glimpses = new ArrayList<>();
		final Sc17Config config = new Sc17Config();
		boolean seen;
		long now = 10_000;
		long day = 7;
		final ArchiveEnv env;
		final ArchiveRules rules;
		final Answers answers;

		Rig(ServerLevel level, Live.Result built) {
			this.level = level;
			this.built = built;
			this.plan = plan(built);
			this.place = built.place();
			data.add(built.known());
			TraceService traces = Services.traces().forced();
			Answers.View view = (l, cells) -> !seen;
			Answers.Sink sink = (l, p, answer, player) -> answered.add(answer + ":" + player.getName().getString());
			this.answers = Answers.over(new Answers.Env(s -> data, traces, view, (s, p) -> List.copyOf(inside), uuid -> false, uuid -> 400_000L,
					s -> null, () -> day, () -> now, sink));
			Figure figure = new Figure() {
				@Override
				public boolean canGlimpse(ServerPlayer player, Vec3 feet) {
					return true;
				}

				@Override
				public FireResult glimpse(ServerPlayer player, Vec3 feet, float yaw) {
					glimpses.add(feet);
					return FireResult.FIRED;
				}
			};
			this.env = new ArchiveEnv(answers, traces, view, p -> List.copyOf(inside), () -> List.copyOf(people), () -> data, () -> day, () -> now,
					figure, () -> config, Services.watch(), () -> List.copyOf(known), uuid -> false, uuid -> 400_000L, sink);
			this.rules = ArchiveRules.over(env);
		}

		Visitor enter(String name, BlockPos feet) {
			Visitor v = at(level, name, feet);
			inside.add(v);
			people.add(v);
			known.add(new HerobrineState.Subject(v.getUUID(), name));
			data.visit(v.getUUID(), place);
			return v;
		}

		void look(boolean scan) {
			rules.look(level.getServer(), scan);
		}
	}

	static int glowstone(ServerLevel level, List<BlockPos> cells) {
		int n = 0;
		for (BlockPos pos : cells) {
			n += level.getBlockState(pos).is(Blocks.GLOWSTONE) ? 1 : 0;
		}
		return n;
	}

	// --- the plan, pure ---

	/**
	 * Every maze (100 seeds, both sizes) reaches every cell from every way in and the hall's door, has loops (more open
	 * doorways than a tree for most seeds), is the same for the same seed; the three ways in are three different outer
	 * cells; the bay is an inner aisle.
	 */
	public static void mazes(GameTestHelper helper) {
		int withLoops = 0;
		for (int n = 0; n < 100; n++) {
			for (boolean longHall : new boolean[] {false, true}) {
				Start start = new Start(new BlockPos(0, 0, 0), Direction.EAST, ArchivePlan.variant(longHall, 0.08), 1000L + n * 7919L);
				ArchivePlan a = ArchivePlan.of(start, new int[0]);
				ArchivePlan b = ArchivePlan.of(start, new int[0]);
				int open = 0;
				for (int j = 0; j < a.rows; j++) {
					for (int c = 0; c < a.cols; c++) {
						for (boolean h : new boolean[] {true, false}) {
							helper.assertTrue(a.edgeOpen(c, j, h) == b.edgeOpen(c, j, h), "the same seed gave another maze");
							open += a.edgeOpen(c, j, h) ? 1 : 0;
						}
					}
				}
				withLoops += open > a.cellCount() - 1 ? 1 : 0;
				for (int t = 0; t < 3; t++) {
					int[] steps = a.cellSteps(a.outerCell(t));
					for (int i = 0; i < steps.length; i++) {
						helper.assertTrue(steps[i] >= 0, "seed " + start.seed() + ": cell " + i + " unreachable from way " + t);
					}
				}
				helper.assertTrue(a.tunnels[0].column() != a.tunnels[1].column() && a.tunnels[1].column() != a.tunnels[2].column()
						&& a.tunnels[0].column() != a.tunnels[2].column(), "two ways from one cell");
				helper.assertTrue(a.bayAisle >= 1 && a.bayAisle <= a.stackRows - 3, "the bay is at aisle " + a.bayAisle);
				helper.assertTrue(a.length == (longHall ? 95 : 63) && a.cols * a.rows == (longHall ? 35 : 15), "the sizes: " + a.length);
			}
		}
		helper.assertTrue(withLoops >= 150, "only " + withLoops + " of 200 mazes have a loop");
		helper.succeed();
	}

	/** A terrain of one ground height and one biome everywhere (siting's tests). */
	record Flat(int ground, Holder<Biome> biome) implements Terrain {
		@Override
		public int seaLevel() {
			return 63;
		}

		@Override
		public int minY() {
			return -64;
		}

		@Override
		public int ground(int x, int z) {
			return ground;
		}

		@Override
		public int surface(int x, int z) {
			return ground;
		}

		@Override
		public Holder<Biome> biome(int x, int y, int z) {
			return biome;
		}

		@Override
		public BlockState block(int x, int y, int z) {
			return y <= ground ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
		}
	}

	static Siting.Query query(GameTestHelper helper, ChunkPos chunk, int ground, ResourceKey<Biome> biome) {
		Holder<Biome> holder = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(biome);
		Snapshot snapshot = new Snapshot(true, Stage.TRACES, BlockPos.ZERO, 42L, 7L, Map.of(S17.ID, new Sc17Config()));
		return new Siting.Query(chunk, new Flat(ground, holder), snapshot, 42L, RandomSource.create(chunk.pack()), -64, 319);
	}

	/**
	 * Siting: the hall's floor from 36 under the lowest corner, never over 40 nor under -24; refused where the land is
	 * too low, in the deep dark, and where a way in would not find the surface; the way in faces the origin; the start's
	 * box within the kit's reach; the hall's ceiling at least 16 under the ground.
	 */
	public static void siting(GameTestHelper helper) {
		ArchiveSiting siting = new ArchiveSiting();
		for (int[] c : new int[][] {{60, 0}, {-60, 0}, {0, 60}, {0, -60}, {45, -50}}) {
			ChunkPos chunk = new ChunkPos(c[0], c[1]);
			Optional<Start> start = siting.find(query(helper, chunk, 70, Biomes.PLAINS));
			helper.assertTrue(start.isPresent(), "no start at " + chunk);
			Direction facing = start.get().facing();
			Direction toward = Siting.facingToOrigin(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ());
			helper.assertTrue(facing == toward, "the way in faces " + facing + ", not the origin (" + toward + ")");
			int floor = start.get().origin().getY();
			helper.assertTrue(floor <= 70 - 36 && floor >= 70 - 36 - 6, "the floor at " + floor + " under ground 70");
			helper.assertTrue(floor + ArchivePlan.CEILING + 1 + 16 <= 70, "the ceiling is under 16 below the ground");
			ArchivePlan plan = ArchivePlan.of(start.get(), new Flat(70, null));
			helper.assertTrue(plan.fits, "a way in found no surface");
			helper.assertTrue(com.forzacode.a1016_02.world.sc2.kit.Sc2Structure.withinReach(plan.box(), chunk), "the box leaves the reach: " + plan.box());
		}
		ChunkPos chunk = new ChunkPos(60, 0);
		// One plan over the land: at most 81 ground samples (D-104), its three ways in measured where they come out.
		int[] samples = {0};
		Terrain counting = new Flat(70, null);
		Terrain counted = new Terrain() {
			@Override
			public int seaLevel() {
				return 63;
			}

			@Override
			public int minY() {
				return -64;
			}

			@Override
			public int ground(int x, int z) {
				samples[0]++;
				return counting.ground(x, z);
			}

			@Override
			public int surface(int x, int z) {
				return ground(x, z);
			}

			@Override
			public Holder<Biome> biome(int x, int y, int z) {
				return null;
			}

			@Override
			public BlockState block(int x, int y, int z) {
				return counting.block(x, y, z);
			}
		};
		ArchivePlan measured = ArchivePlan.of(new Start(new BlockPos(chunk.getMiddleBlockX(), 30, 0), Direction.WEST, 0, 9L), counted);
		helper.assertTrue(measured.fits && samples[0] <= 81, samples[0] + " ground samples for one plan");
		Optional<Start> high = siting.find(query(helper, chunk, 110, Biomes.PLAINS));
		helper.assertTrue(high.isPresent() && high.get().origin().getY() <= 40, "a high land's floor over 40: " + high);
		helper.assertTrue(siting.find(query(helper, chunk, 8, Biomes.PLAINS)).isEmpty(), "land at 8 would put the floor under -24");
		helper.assertTrue(siting.find(query(helper, chunk, 70, Biomes.DEEP_DARK)).isEmpty(), "in the deep dark");
		helper.assertTrue(siting.find(query(helper, chunk, 260, Biomes.PLAINS)).isEmpty(), "a way in 220 up found the surface");
		helper.succeed();
	}

	// --- the shape ---

	/**
	 * The shape in a block of rock: every floor of the hall, the gallery and the cells is lit (block light at least 1: no
	 * monster spawns, vanilla's rule since 1.18) once the light settled; nowhere a step off a floor drops more than 3
	 * (a ladder catches); the list's six on the dais wall; the hall's door; the open way reaches the surface, the closed
	 * ways keep a plug of 12 just under it; a water pocket and a cave beside a way in were sealed.
	 */
	public static void shape(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int x = X;
		int z = Z;
		int top = 38;
		// A water pocket and a cave pocket just beyond the open way's lining, in the land before it is built: sealed.
		List<BlockPos> pockets = new ArrayList<>();
		Live.Result built = build(level, x, z, 2, false, top, measured -> {
			ArchivePlan.Tunnel way = measured.tunnels[0];
			BlockPos water = measured.at(way.left() - 2, ArchivePlan.foot(20), measured.tunnelF0 + 20);
			BlockPos cave = measured.at(way.left() + 3, ArchivePlan.foot(24) + 1, measured.tunnelF0 + 24);
			level.setBlock(water, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
			level.setBlock(cave, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			pockets.add(water);
			pockets.add(cave);
		});
		ArchivePlan plan = plan(built);
		for (BlockPos pocket : pockets) {
			BlockState state = level.getBlockState(pocket);
			helper.assertTrue(!state.isAir() && state.getFluidState().isEmpty(), "the seal left " + state + " at " + pocket.toShortString());
		}
		helper.assertTrue(plan.fits, "a way in found no surface");
		helper.assertTrue(Live.settleLight(level, built.box()), "the light did not settle");
		List<String> problems = new ArrayList<>();
		int lit = 0;
		int under8 = 0;
		BoundingBox hall = plan.hallInside();
		BoundingBox cells = plan.cellsInside();
		for (BoundingBox inside : List.of(hall, cells)) {
			for (BlockPos pos : BlockPos.betweenClosed(inside.minX(), inside.minY() - 1, inside.minZ(), inside.maxX(), inside.maxY(), inside.maxZ())) {
				if (!standable(level, pos)) {
					continue;
				}
				int light = level.getBrightness(LightLayer.BLOCK, pos);
				if (light < 1 && problems.size() < 6) {
					problems.add("dark at " + pos.toShortString());
				}
				lit++;
				under8 += light < 8 ? 1 : 0;
			}
		}
		helper.assertTrue(problems.isEmpty(), "the floors: " + problems);
		helper.assertTrue(lit > 1500, "only " + lit + " floor cells");
		// Walked from the hall's door (steps up of 1, ladders climbed, every step off measured): never a drop over 3.
		BoundingBox walkBox = hall.encapsulate(cells).inflatedBy(2);
		List<String> falls = new ArrayList<>();
		Set<BlockPos> walked = walk(level, plan.at(0, 1, -1), walkBox, falls);
		helper.assertTrue(falls.isEmpty(), "falls: " + falls);
		helper.assertTrue(walked.size() > 1500, "only " + walked.size() + " cells walked");
		helper.assertTrue(walked.contains(plan.at(-8, ArchivePlan.DECK + 1, -20)) && walked.contains(plan.at(8, ArchivePlan.DECK + 1, -30))
				&& walked.contains(plan.at(0, ArchivePlan.DAIS_HEIGHT + 1, -plan.length + 3)) && walked.contains(plan.cellFeet(0, plan.rows - 1)),
				"the gallery, the dais or the far cells were not reached");
		A1016_02.LOGGER.debug("[a1016] s17 test: {} floor cells, {} under light 8", lit, under8);
		// The list's six, one in each shelf beside the middle one.
		Set<String> titles = Records.titles(level, plan);
		helper.assertTrue(titles.containsAll(ArchivePlan.LIST_NAMES) && titles.size() == 6, "the wall holds " + titles);
		// The hall's door.
		helper.assertTrue(level.getBlockState(plan.at(0, 1, 0)).isAir() && level.getBlockState(plan.at(0, 2, 0)).isAir()
				&& !level.getBlockState(plan.at(0, 3, 0)).isAir(), "no 1 x 2 door into the hall");
		// The open way: its mouth stands on the ground outside, open to the sky; the closed ways' plugs are whole.
		ArchivePlan.Tunnel open = plan.tunnels[0];
		BlockPos mouth = plan.entrance();
		helper.assertTrue(level.getBlockState(mouth).isAir() && level.canSeeSky(mouth.above()), "the way in does not open: " + mouth.toShortString());
		helper.assertTrue(OtherDoor.openWay(level, plan) == 0, "the open way is " + OtherDoor.openWay(level, plan));
		for (int t = 1; t < 3; t++) {
			helper.assertTrue(OtherDoor.whole(level, plan.plug(plan.tunnels[t])), "way " + t + "'s plug is not whole");
			BlockPos inner = plan.at(plan.tunnels[t].left(), ArchivePlan.foot(plan.tunnels[t].mouth() - 4), plan.tunnelF0 + plan.tunnels[t].mouth() - 4);
			helper.assertTrue(level.getBlockState(inner).isAir(), "way " + t + " is not dug up to its plug");
		}
		helper.assertTrue(open.exit() > 24, "the way in is " + open.exit() + " long");
		helper.succeed();
	}

	/**
	 * A cell one can stand in: it and the one over it pass, and what is under it is a floor (a collision no higher than a
	 * block: not a fence's or a wall's, and not a ladder).
	 */
	static boolean standable(ServerLevel level, BlockPos pos) {
		BlockPos below = pos.below();
		BlockState under = level.getBlockState(below);
		var shape = under.getCollisionShape(level, below);
		return passes(level, pos) && passes(level, pos.above()) && !shape.isEmpty() && shape.max(Direction.Axis.Y) <= 1.0
				&& !(under.getBlock() instanceof LadderBlock) && !(level.getBlockState(pos).getBlock() instanceof LadderBlock);
	}

	/**
	 * Every floor a player can walk to from {@code from} inside {@code box}: level steps, steps up of 1 with headroom,
	 * ladders climbed to their top and down to their foot, and steps off an edge (into the air) landing below; a landing
	 * more than 3 under the floor stepped off is a fall (listed). Returns the floors reached.
	 */
	static Set<BlockPos> walk(ServerLevel level, BlockPos from, BoundingBox box, List<String> falls) {
		Set<BlockPos> seen = new HashSet<>();
		java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
		seen.add(from);
		queue.add(from);
		while (!queue.isEmpty()) {
			BlockPos at = queue.poll();
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos next = at.relative(side);
				if (!box.isInside(next)) {
					continue;
				}
				List<BlockPos> reach = new ArrayList<>();
				if (level.getBlockState(next).getBlock() instanceof LadderBlock || level.getBlockState(next.below()).getBlock() instanceof LadderBlock) {
					// A ladder: every floor beside it, top to foot.
					BlockPos low = next;
					while (level.getBlockState(low.below()).getBlock() instanceof LadderBlock) {
						low = low.below();
					}
					for (BlockPos rung = low; level.getBlockState(rung).getBlock() instanceof LadderBlock || rung.equals(low); rung = rung.above()) {
						for (Direction off : Direction.Plane.HORIZONTAL) {
							for (BlockPos cell : List.of(rung.relative(off), rung.relative(off).above())) {
								if (standable(level, cell)) {
									reach.add(cell);
								}
							}
						}
						if (!(level.getBlockState(rung).getBlock() instanceof LadderBlock)) {
							break;
						}
					}
					if (standable(level, low)) {
						reach.add(low);
					}
				} else if (standable(level, next)) {
					reach.add(next);
				} else if (standable(level, next.above()) && passes(level, at.above(2))) {
					reach.add(next.above());
				} else {
					int drop = drop(level, next);
					if (drop > 0) {
						if (drop > 3) {
							if (falls.size() < 6) {
								falls.add(drop + " off " + at.toShortString() + " to the " + side);
							}
							continue;
						}
						if (standable(level, next.below(drop))) {
							reach.add(next.below(drop));
						}
					}
				}
				for (BlockPos cell : reach) {
					if (box.isInside(cell) && seen.add(cell.immutable())) {
						queue.add(cell.immutable());
					}
				}
			}
		}
		return seen;
	}

	static boolean passes(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getBlockState(pos).getFluidState().isEmpty();
	}

	/** How far one falls stepping into {@code pos}: 0 if it does not pass, or a ladder catches; else the cells of air under it. */
	static int drop(ServerLevel level, BlockPos pos) {
		if (!passes(level, pos) || !passes(level, pos.above())) {
			return 0;
		}
		int n = 0;
		BlockPos at = pos.below();
		while (n < 40) {
			BlockState state = level.getBlockState(at);
			if (state.getBlock() instanceof LadderBlock || state.is(Blocks.WATER)) {
				return 0;
			}
			if (!state.getCollisionShape(level, at).isEmpty()) {
				return n;
			}
			n++;
			at = at.below();
		}
		return n;
	}

	/** The long hall (95, 7 x 5 cells), built in the open: every floor lit, walked from its door with no drop over 3, the list. */
	public static void shapeLong(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Live.Result built = build(level, X + 300, Z + 700, 2, true, null);
		ArchivePlan plan = plan(built);
		helper.assertTrue(plan.longHall && plan.length == 95 && plan.cols == 7 && plan.rows == 5, "not the long hall");
		helper.assertTrue(Live.settleLight(level, built.box()), "the light did not settle");
		List<String> problems = new ArrayList<>();
		for (BoundingBox inside : List.of(plan.hallInside(), plan.cellsInside())) {
			for (BlockPos pos : BlockPos.betweenClosed(inside.minX(), inside.minY() - 1, inside.minZ(), inside.maxX(), inside.maxY(), inside.maxZ())) {
				if (standable(level, pos) && level.getBrightness(LightLayer.BLOCK, pos) < 1 && problems.size() < 6) {
					problems.add("dark at " + pos.toShortString());
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), "the long hall's floors: " + problems);
		Set<BlockPos> walked = walk(level, plan.at(0, 1, -1), plan.hallInside().encapsulate(plan.cellsInside()).inflatedBy(2), problems);
		helper.assertTrue(problems.isEmpty() && walked.size() > 3000, "the long hall's walk: " + walked.size() + " " + problems);
		helper.assertTrue(Records.titles(level, plan).containsAll(ArchivePlan.LIST_NAMES), "the long hall's wall");
		helper.succeed();
	}

	/** The interior is the hall's inside and the cells, never the shell, the bookshelf face or a way in. */
	public static void interior(GameTestHelper helper) {
		Start start = new Start(new BlockPos(X + 600, 10, Z), Direction.WEST, ArchivePlan.variant(false, 0.08), SEED);
		ArchivePlan plan = ArchivePlan.of(start, new int[] {40, 42, 40, 42, 40, 42});
		var inside = plan.interior();
		helper.assertTrue(inside.contains(plan.at(0, 1, -30)) && inside.contains(plan.at(-12, 17, -1)) && inside.contains(plan.lectern()),
				"the hall is not inside");
		helper.assertTrue(inside.contains(plan.cellFeet(0, 0)) && inside.contains(plan.cellFeet(plan.cols - 1, plan.rows - 1)), "a cell is not inside");
		helper.assertFalse(inside.contains(plan.at(13, 5, -30)) || inside.contains(plan.at(0, 18, -30)) || inside.contains(plan.at(0, 0, -30)),
				"the face, the ceiling or the floor is inside");
		ArchivePlan.Tunnel way = plan.tunnels[0];
		helper.assertFalse(inside.contains(plan.at(way.left(), ArchivePlan.foot(10), plan.tunnelF0 + 10)), "a way in is inside");
		helper.assertTrue(plan.entrance().equals(plan.at(way.left(), ArchivePlan.foot(42), plan.tunnelF0 + 42)), "the entrance: " + plan.entrance());
		helper.succeed();
	}

	// --- 17a ---

	/** Records: the list's six, then one per known player inside (three friends: three), never twice. */
	public static void records(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Live.Result built = build(level, X + 800, Z, 2, false, null);
		Rig rig = new Rig(level, built);
		rig.enter("ana_s17", rig.plan.at(0, 1, -10));
		rig.enter("ben_s17", rig.plan.at(3, 1, -20));
		rig.enter("cy_s17", rig.plan.cellFeet(1, 1));
		rig.look(false);
		rig.look(false);
		Set<String> titles = Records.titles(level, rig.plan);
		helper.assertTrue(titles.containsAll(List.of("ana_s17", "ben_s17", "cy_s17")) && titles.containsAll(ArchivePlan.LIST_NAMES), "the wall: " + titles);
		int books = 0;
		for (BlockPos pos : rig.plan.recordWall()) {
			if (level.getBlockEntity(pos) instanceof Container shelf) {
				for (int i = 0; i < shelf.getContainerSize(); i++) {
					books += shelf.getItem(i).isEmpty() ? 0 : 1;
				}
			}
		}
		helper.assertTrue(books == 9, books + " books on the wall, not 9");
		// A new visit (the rules forget their session): still nine.
		Rig again = new Rig(level, built);
		again.enter("ana_s17", rig.plan.at(0, 1, -10));
		again.look(false);
		int after = 0;
		for (BlockPos pos : rig.plan.recordWall()) {
			if (level.getBlockEntity(pos) instanceof Container shelf) {
				for (int i = 0; i < shelf.getContainerSize(); i++) {
					after += shelf.getItem(i).isEmpty() ? 0 : 1;
				}
			}
		}
		helper.assertTrue(after == 9, "a second visit left another: " + after);
		// Someone at the dais gets none (it comes before they reach it).
		Rig late = new Rig(level, built);
		late.enter("dee_s17", rig.plan.at(0, ArchivePlan.DAIS_HEIGHT + 1, -rig.plan.length + 4));
		late.look(false);
		helper.assertFalse(Records.titles(level, rig.plan).contains("dee_s17"), "left at the dais, in front of them");
		helper.succeed();
	}

	// --- 17b ---

	static List<BlockPos> lightsOf(ArchivePlan plan, Set<Integer> units) {
		List<BlockPos> out = new ArrayList<>();
		for (int u : units) {
			out.addAll(LightsOut.lights(plan, u));
		}
		return out;
	}

	/**
	 * The lights: nothing before 60 s deep inside; then a unit every 160 ticks from the door toward the player (rows 0,
	 * 1, 2...), never a light within 6 of them, until 8 rows are dark; the span's middle at block light 0 (a monster may
	 * spawn there); the glimpse once after the fourth row, 20 to 40 off on the nave's floor in the dark; not again the
	 * same day; given back by the ledger.
	 */
	public static void lightsOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Live.Result built = build(level, X + 1200, Z, 2, false, null);
		Rig rig = new Rig(level, built);
		ArchivePlan plan = rig.plan;
		BlockPos feet = plan.at(1, 1, -50);
		ServerPlayer ana = rig.enter("ana_s17b", feet);
		rig.look(false);
		rig.now += 1100;
		rig.look(false);
		ArchiveRules.Visit visit = rig.rules.visit(rig.place);
		helper.assertTrue(visit != null && visit.dark.isEmpty(), "dark before 60 s: " + (visit == null ? null : visit.dark));
		List<Integer> order = new ArrayList<>();
		for (int step = 0; step < 40 && !visit.darkOver; step++) {
			rig.now += 200;
			int before = visit.dark.size();
			rig.look(false);
			if (visit.dark.size() > before) {
				for (int u : visit.dark) {
					if (!order.contains(u)) {
						order.add(u);
					}
				}
			}
		}
		helper.assertTrue(order.equals(List.of(0, 1, 2, 3, 4, 5, 6, 7)), "the rows went " + order + " (" + visit.darkWhy + ")");
		for (BlockPos light : lightsOf(plan, visit.dark)) {
			helper.assertFalse(level.getBlockState(light).is(Blocks.GLOWSTONE), "a light stayed at " + light.toShortString());
			helper.assertTrue(light.distSqr(feet) > 36, "a light within 6 went");
		}
		helper.assertTrue(glowstone(level, plan.rowLights(12)) == plan.rowLights(12).size(), "the player's own row went");
		helper.assertTrue(visit.lightsGone <= ArchiveRules.LIGHTS_MAX, visit.lightsGone + " lights went");
		helper.assertTrue(Live.settleLight(level, built.box()), "the light did not settle");
		BlockPos middle = plan.at(0, 1, -16);
		helper.assertTrue(level.getBrightness(LightLayer.BLOCK, middle) == 0, "the span's middle is lit " + level.getBrightness(LightLayer.BLOCK, middle));
		helper.assertTrue(rig.glimpses.size() == 1, rig.glimpses.size() + " glimpses");
		Vec3 spot = rig.glimpses.getFirst();
		double away = Math.hypot(spot.x - ana.getX(), spot.z - ana.getZ());
		BlockPos spotPos = BlockPos.containing(spot);
		helper.assertTrue(away >= 20 && away <= 40 && plan.localR(spotPos.getX(), spotPos.getZ()) == 0
				&& visit.dark.contains(ArchivePlan.rowAt(plan.localF(spotPos.getX(), spotPos.getZ()))), "the glimpse at " + spot + ", " + away + " off");
		helper.assertTrue(rig.answered.contains("17b:ana_s17b"), "not answered: " + rig.answered);
		// Not again the same day (a new visit).
		Rig again = new Rig(level, built);
		again.data.spend(rig.place, ArchiveRules.DARK, List.of(), rig.day);
		again.enter("ana_s17b", feet);
		again.look(false);
		again.now += 1300;
		again.look(false);
		ArchiveRules.Visit second = again.rules.visit(again.place);
		helper.assertTrue(second != null && second.dark.isEmpty() && second.darkOver, "again the same day: " + (second == null ? null : second.darkWhy));
		// Given back: the ledger's entries of this place put every light back.
		int given = undo(level, ArchiveRules.DARK_CAUSE, built.box());
		helper.assertTrue(given == visit.lightsGone, given + " given back of " + visit.lightsGone);
		helper.assertTrue(glowstone(level, lightsOf(plan, visit.dark)) == visit.lightsGone, "not every light came back");
		helper.succeed();
	}

	/** Undoes every ledger entry with this cause inside this box, newest first, again while a pass undid any; how many. */
	static int undo(ServerLevel level, String cause, BoundingBox box) {
		int n = 0;
		for (int pass = 0; pass < 4; pass++) {
			List<TraceLedger.Entry> entries = new ArrayList<>(TraceService.ledger(level.getServer()).entries());
			java.util.Collections.reverse(entries);
			int before = n;
			for (TraceLedger.Entry entry : entries) {
				if (entry.cause().equals(cause) && box.isInside(entry.pos().pos())) {
					n += Services.traces().forced().undo(level, entry) == TraceService.UndoResult.DONE ? 1 : 0;
				}
			}
			if (n == before) {
				break;
			}
		}
		return n;
	}

	/** While a row is in view nothing goes; once nobody sees it, it goes (the kit's retries, tried again later). */
	public static void lightsWaitOutOfView(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Live.Result built = build(level, X + 1600, Z, 2, false, null);
		Rig rig = new Rig(level, built);
		rig.enter("ana_s17v", rig.plan.at(0, 1, -50));
		rig.look(false);
		rig.now += 1300;
		rig.seen = true;
		rig.look(false);
		ArchiveRules.Visit visit = rig.rules.visit(rig.place);
		helper.assertTrue(visit != null && visit.darkStarted && visit.dark.isEmpty(), "a row went in view");
		helper.assertTrue(rig.answers.waiting(rig.place, ArchiveRules.DARK), "it does not wait");
		helper.assertTrue(glowstone(level, rig.plan.rowLights(0)) == rig.plan.rowLights(0).size(), "a light of row 0 went in view");
		rig.seen = false;
		rig.answers.tryAll(level.getServer());
		helper.assertTrue(visit.dark.contains(0) && glowstone(level, rig.plan.rowLights(0)) == 0, "out of view it did not go");
		helper.succeed();
	}

	/**
	 * The review's fixes to the rules (R12-9, R12-10, R12-12, R12-22, R12-27): a row that waits (in view) is not taken once a
	 * friend stands under it, only after they move off; once the story holds the layer back nothing of the archive's own
	 * comes (no record); the debug's forced door keeps the switch and nobody inside; 17e's look never rolls a container's
	 * loot; t0rch's hole is never dug beside water.
	 */
	public static void rulesKeepTheirGatesOnEveryTry(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Live.Result built = build(level, X + 6400, Z, 2, false, null);
		Rig rig = new Rig(level, built);
		Visitor ana = rig.enter("ana_s17r", rig.plan.at(0, 1, -50));
		rig.look(false);
		rig.now += 1300;
		rig.seen = true;
		rig.look(false);
		ArchiveRules.Visit visit = rig.rules.visit(rig.place);
		helper.assertTrue(visit != null && visit.darkStarted && rig.answers.waiting(rig.place, ArchiveRules.DARK), "the first row does not wait in view");
		// A friend walks in and stands under the row that waits: out of view now, it still waits.
		Visitor ben = rig.enter("ben_s17r", rig.plan.at(0, 1, -2));
		rig.seen = false;
		rig.answers.tryAll(level.getServer());
		helper.assertTrue(glowstone(level, rig.plan.rowLights(0)) == rig.plan.rowLights(0).size() && !visit.dark.contains(0), "a row went over a friend");
		rig.inside.remove(ben);
		rig.people.remove(ben);
		rig.now += 100;
		rig.answers.tryAll(level.getServer());
		helper.assertTrue(visit.dark.contains(0), "the row did not go once nobody stood under it");
		// The story holds the layer back: no record for a known newcomer, nothing of the door.
		Answers held = Answers.over(new Answers.Env(s -> rig.data, Services.traces().forced(), (l, c) -> true, (s, p) -> List.copyOf(rig.inside), u -> false,
				u -> 400_000L, s -> "ended", () -> rig.day, () -> rig.now, (l, p, a, pl) -> {
				}));
		ArchiveEnv heldEnv = new ArchiveEnv(held, Services.traces().forced(), (l, c) -> true, p -> List.copyOf(rig.inside), () -> List.copyOf(rig.people),
				() -> rig.data, () -> rig.day, () -> rig.now, rig.env.figure(), () -> rig.config, Services.watch(), () -> List.copyOf(rig.known), u -> false,
				u -> 400_000L, (l, p, a, pl) -> {
				});
		Visitor cid = rig.enter("cid_s17r", rig.plan.at(0, 1, -10));
		ArchiveRules.over(heldEnv).look(level.getServer(), true);
		helper.assertFalse(Records.titles(level, rig.plan).contains("cid_s17r"), "a record once the story is over");
		helper.assertTrue(ArchiveRules.over(heldEnv).door(heldEnv, level, rig.place, rig.plan, rig.data, true) != null, "the forced door once the story is over");
		// Forced, with someone inside: refused.
		String why = rig.rules.door(rig.env, level, rig.place, rig.plan, rig.data, true);
		helper.assertTrue(why != null && why.contains("inside"), "the forced door with players inside: " + why);
		// 17e's look: a chest whose loot is not rolled stays unrolled.
		BlockPos chest = BlockPos.containing(ana.position()).offset(0, 0, 0);
		BlockPos outside = rig.plan.box().getCenter().atY(level.getMinY() + 2);
		level.setBlock(outside, Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS);
		if (level.getBlockEntity(outside) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity box) {
			box.setLootTable(net.minecraft.world.level.storage.loot.BuiltInLootTables.SIMPLE_DUNGEON, 7L);
		}
		Visitor dan = at(level, "dan_s17r", outside.above());
		rig.people.add(dan);
		rig.look(true);
		helper.assertTrue(level.getBlockEntity(outside) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity box && box.getLootTable() != null,
				"the look rolled a chest's loot");
		// t0rch's hole: never beside water.
		BlockPos dry = outside.above(3);
		level.setBlock(dry, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		helper.assertFalse(ArchiveTrail.wetBeside(level, dry.above(5)), "dry ground is wet");
		level.setBlock(dry.above(6), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
		helper.assertTrue(ArchiveTrail.wetBeside(level, dry.above(5)), "water beside is not seen");
		level.setBlock(dry.above(6), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(outside, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		undo(level, ArchiveRules.DARK_CAUSE, built.box());
		helper.assertTrue(chest != null && cid != null, "");
		helper.succeed();
	}

	/**
	 * Two players apart in the hall (rows 3 and 14): the dark falls behind and between them, never on a row within 6 of
	 * either; the player near the door keeps their light; a third player only in the cells has the cells on their way out
	 * darkened instead, never their own cell nor the one before it.
	 */
	public static void lightsBetweenFriends(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Live.Result built = build(level, X + 2000, Z, 2, false, null);
		Rig rig = new Rig(level, built);
		ArchivePlan plan = rig.plan;
		BlockPos near = plan.at(-2, 1, -14);
		BlockPos far = plan.at(2, 1, -58);
		rig.enter("ana_s17m", near);
		rig.enter("ben_s17m", far);
		rig.look(false);
		rig.now += 1300;
		ArchiveRules.Visit visit = null;
		for (int step = 0; step < 40; step++) {
			rig.look(false);
			visit = rig.rules.visit(rig.place);
			if (visit.darkOver) {
				break;
			}
			rig.now += 200;
		}
		helper.assertTrue(visit != null && visit.darkRows() == rig.config.darkRowsMin, "rows dark: " + (visit == null ? null : visit.dark));
		int nearRow = ArchivePlan.rowAt(-14);
		for (int k : visit.dark) {
			helper.assertTrue(k >= 0 && Math.abs(k - nearRow) > 1, "row " + k + " beside the near player went");
		}
		helper.assertTrue(visit.dark.stream().anyMatch(k -> k > nearRow + 1), "nothing fell between them: " + visit.dark);
		for (BlockPos light : lightsOf(plan, visit.dark)) {
			helper.assertTrue(light.distSqr(near) > 36 && light.distSqr(far) > 36, "a light within 6 of a player went");
		}
		// Only in the cells: the cells on the way out, never the player's nor the one before it.
		Rig cells = new Rig(level, built);
		int outer = plan.outerCell(0);
		int deep = -1;
		int[] steps = plan.cellSteps(outer);
		for (int i = 0; i < steps.length; i++) {
			if (deep < 0 || steps[i] > steps[deep]) {
				deep = i;
			}
		}
		cells.enter("cy_s17m", plan.cellFeet(deep % plan.cols, deep / plan.cols));
		Set<Integer> dark = new HashSet<>();
		List<BlockPos> feet = List.of(plan.cellFeet(deep % plan.cols, deep / plan.cols));
		for (int i = 0; i < 20; i++) {
			int unit = LightsOut.next(plan, feet, dark, outer, rig.config.darkRowsMin);
			if (unit == LightsOut.NONE) {
				break;
			}
			dark.add(unit);
		}
		List<Integer> path = LightsOut.path(plan, outer, deep);
		helper.assertTrue(!dark.isEmpty() || path.size() <= 2, "no cell went dark on a way of " + path.size());
		for (int u : dark) {
			int cell = LightsOut.cellOfUnit(u);
			helper.assertTrue(u < 0 && path.contains(cell) && path.indexOf(cell) < path.size() - 2, "cell " + cell + " is not on the way out or too near");
		}
		helper.succeed();
	}

	// --- 17c ---

	/**
	 * The other door: a newcomer at 20 from the open mouth while the one who was inside is 100 away: one batch of 12
	 * moves, the closed way's plug into the open way's first three cells (every block conserved), that way open now;
	 * refused while the finder is near or anyone stands at a mouth; at most 2 moves; given back by the ledger.
	 */
	public static void otherDoor(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Live.Result built = build(level, X + 2600, Z, 2, false, 38);
		Rig rig = new Rig(level, built);
		ArchivePlan plan = rig.plan;
		Vec3 mouth = OtherDoor.mouth(plan, 0);
		Visitor newcomer = at(level, "new_s17c", BlockPos.containing(mouth.add(0, 0, -20)));
		newcomer.snapTo(mouth.x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) mouth.x, (int) mouth.z - 20),
				mouth.z - 20, 0, 0);
		Visitor finder = at(level, "old_s17c", BlockPos.containing(mouth.add(0, 0, -100)));
		rig.data.visit(finder.getUUID(), rig.place);
		rig.people.add(newcomer);
		rig.people.add(finder);
		// The finder near: refused.
		finder.snapTo(mouth.x + 30, newcomer.getY(), mouth.z - 20, 0, 0);
		String why = rig.rules.door(rig.env, level, rig.place, plan, rig.data, false);
		helper.assertTrue(why != null && OtherDoor.openWay(level, plan) == 0, "moved with the finder near");
		finder.snapTo(mouth.x, newcomer.getY(), mouth.z - 100, 0, 0);
		int closed = OtherDoor.closedToOpen(level, plan, 0, 0);
		List<BlockState> plug = new ArrayList<>();
		for (BlockPos pos : plan.plug(plan.tunnels[closed])) {
			plug.add(level.getBlockState(pos));
		}
		why = rig.rules.door(rig.env, level, rig.place, plan, rig.data, false);
		helper.assertTrue(why == null, "not moved: " + why);
		helper.assertTrue(OtherDoor.openWay(level, plan) == closed, "way " + closed + " is not the open one now: " + OtherDoor.openWay(level, plan));
		List<BlockPos> shut = plan.plug(plan.tunnels[0]);
		for (int i = 0; i < shut.size(); i++) {
			helper.assertTrue(level.getBlockState(shut.get(i)) == plug.get(i), "block " + i + " was not moved in: " + level.getBlockState(shut.get(i)));
		}
		for (BlockPos pos : plan.plug(plan.tunnels[closed])) {
			helper.assertTrue(level.getBlockState(pos).isAir(), "the plug left " + level.getBlockState(pos));
		}
		helper.assertTrue(rig.answered.contains("17c:new_s17c") && rig.data.spent(rig.place, OtherDoor.ANSWER).ever() == 1, "not counted");
		// Someone at a mouth: refused.
		Visitor at = at(level, "near_s17c", BlockPos.containing(OtherDoor.mouth(plan, closed)));
		rig.people.add(at);
		rig.day++;
		helper.assertTrue(rig.rules.door(rig.env, level, rig.place, plan, rig.data, false) != null, "moved with someone at the mouth");
		rig.people.remove(at);
		// The caps: a second move (another day, another newcomer), then never a third.
		Visitor second = at(level, "new2_s17c", BlockPos.containing(newcomer.position()));
		rig.people.remove(newcomer);
		rig.people.add(second);
		Vec3 now = OtherDoor.mouth(plan, closed);
		second.snapTo(now.x, newcomer.getY(), now.z - 20, 0, 0);
		why = rig.rules.door(rig.env, level, rig.place, plan, rig.data, false);
		helper.assertTrue(why == null, "the second move: " + why);
		rig.day++;
		Vec3 third = OtherDoor.mouth(plan, OtherDoor.openWay(level, plan));
		second.snapTo(third.x, newcomer.getY(), third.z - 20, 0, 0);
		why = rig.rules.door(rig.env, level, rig.place, plan, rig.data, false);
		helper.assertTrue(why != null && why.startsWith("moved 2"), "a third move: " + why);
		// Given back: both moves undone, the first way open again.
		undo(level, OtherDoor.CAUSE, built.box());
		helper.assertTrue(OtherDoor.openWay(level, plan) == 0, "given back, the open way is " + OtherDoor.openWay(level, plan));
		helper.succeed();
	}

	// --- 17d ---

	/**
	 * A marker moves: a torch a player placed in a cell, out of view, every player 2+ cells off, goes to the same spot in
	 * a cell that looks the same (same detail) that nobody stood in; a friend's torch too; at most 2 a visit.
	 */
	public static void markers(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Live.Result built = build(level, X + 3200, Z, 2, false, null);
		Rig rig = new Rig(level, built);
		ArchivePlan plan = rig.plan;
		ServerPlayer ana = rig.enter("ana_s17d", plan.at(0, 1, -20));
		Visitor ben = at(level, "ben_s17d", plan.at(2, 1, -22));
		rig.people.add(ben);
		List<Integer> plain = new ArrayList<>();
		for (int i = 0; i < plan.cellCount(); i++) {
			if (plan.detail(i % plan.cols, i / plan.cols) == ArchivePlan.Detail.NONE && i / plan.cols >= 1) {
				plain.add(i);
			}
		}
		helper.assertTrue(plain.size() >= 4, "too few plain cells: " + plain);
		List<BlockPos> torches = new ArrayList<>();
		for (int k = 0; k < 3; k++) {
			int cell = plain.get(k);
			BlockPos pos = plan.at(plan.cellR(cell) + 2, 1, plan.cellF(cell) + 2);
			level.setBlock(pos, Blocks.TORCH.defaultBlockState(), Block.UPDATE_ALL);
			Services.watch().onPlaced(k == 1 ? ben : ana, level, pos, Blocks.TORCH.defaultBlockState());
			torches.add(pos);
		}
		rig.look(true);
		rig.day++;
		rig.look(true);
		rig.look(true);
		ArchiveRules.Visit visit = rig.rules.visit(rig.place);
		helper.assertTrue(visit != null && visit.markersMoved == 2, (visit == null ? 0 : visit.markersMoved) + " moved, not 2");
		int stayed = 0;
		for (BlockPos pos : torches) {
			stayed += level.getBlockState(pos).is(Blocks.TORCH) ? 1 : 0;
		}
		helper.assertTrue(stayed == 1, stayed + " stayed of 3");
		for (int k = 0; k < 3; k++) {
			BlockPos from = torches.get(k);
			if (level.getBlockState(from).is(Blocks.TORCH)) {
				continue;
			}
			int cell = plain.get(k);
			boolean found = false;
			for (int to = 0; to < plan.cellCount(); to++) {
				BlockPos target = plan.at(plan.cellR(to) + 2, 1, plan.cellF(to) + 2);
				if (to != cell && level.getBlockState(target).is(Blocks.TORCH) && !torches.contains(target)) {
					found = true;
					helper.assertTrue(plan.detail(to % plan.cols, to / plan.cols) == ArchivePlan.Detail.NONE, "moved to a cell that looks different");
					helper.assertFalse(visit.stoodIn.contains(to) && !visit.markedCells.contains(to), "moved to a cell someone stood in");
				}
			}
			helper.assertTrue(found, "torch " + k + " went nowhere");
		}
		helper.succeed();
	}

	// --- 17e ---

	/** A written book about him, as lore stamps one, by this writer. */
	static ItemStack bookOf(UUID writer) {
		ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
		stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("him"), "ana", 0,
				List.of(Filterable.passThrough(net.minecraft.network.chat.Component.literal("he was there"))), true));
		CompoundTag tag = new CompoundTag();
		tag.putString(Telling.BOOK_MARKER, "s17e-test");
		tag.store(Telling.BOOK_WRITERS, UUIDUtil.STRING_CODEC.listOf(), NbtOps.INSTANCE, List.of(writer));
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	/**
	 * A book of one's own: the writer's marked book in a chest 64+ from them, out of view, nobody near: moved to the dais
	 * wall's middle shelf before they reach the dais, once per place; someone else's book stays; given back by the ledger.
	 */
	public static void ownBook(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Live.Result built = build(level, X + 3800, Z, 2, false, null);
		Rig rig = new Rig(level, built);
		ArchivePlan plan = rig.plan;
		BlockPos chestPos = plan.at(0, 0, 160);
		load(level, new BoundingBox(chestPos));
		level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		ServerPlayer ana = rig.enter("ana_s17e", plan.at(0, 1, -10));
		Visitor ben = new Visitor(level, "ben_s17e");
		Container chest = (Container) level.getBlockEntity(chestPos);
		chest.setItem(3, bookOf(ben.getUUID()));
		chest.setItem(5, bookOf(ana.getUUID()));
		// Seen once by someone near it, who then walks away.
		Visitor scout = at(level, "scout_s17e", chestPos.above());
		rig.rules.sightings.scan(List.of(scout), (l, p) -> false);
		rig.look(true);
		BlockPos shelf = plan.bookShelf();
		Container wall = (Container) level.getBlockEntity(shelf);
		boolean onWall = false;
		for (int i = 0; i < wall.getContainerSize(); i++) {
			onWall |= !wall.getItem(i).isEmpty() && com.forzacode.a1016_02.lore.LoreApi.bookAboutHimWriters(wall.getItem(i)).contains(ana.getUUID());
		}
		helper.assertTrue(onWall && chest.getItem(5).isEmpty(), "the book did not move to the dais wall");
		helper.assertFalse(chest.getItem(3).isEmpty(), "someone else's book moved");
		helper.assertTrue(rig.data.spent(rig.place, ArchiveRules.BOOK).ever() == 1, "not counted");
		// Once per place: a second book of hers stays.
		chest.setItem(6, bookOf(ana.getUUID()));
		rig.rules.sightings.scan(List.of(scout), (l, p) -> false);
		rig.day++;
		rig.look(true);
		helper.assertFalse(chest.getItem(6).isEmpty(), "a second book moved to the same place");
		// Given back.
		helper.assertTrue(undo(level, ArchiveRules.BOOK_CAUSE, new BoundingBox(chestPos).encapsulate(built.box())) == 1, "not given back");
		int hers = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			hers += com.forzacode.a1016_02.lore.LoreApi.bookAboutHimWriters(chest.getItem(i)).contains(ana.getUUID()) ? 1 : 0;
		}
		helper.assertTrue(hers == 2, "her books in the chest after the give-back: " + hers);
		helper.succeed();
	}

	// --- 11+ ---

	/**
	 * A breach for the trail's test: an archive in rock and a natural corridor 40 long at its cells' feet height, 4 off
	 * the cells' west wall, ending beside a cell; the breach to use (always, near this archive only). Returns the
	 * corridor's start (the cave spot), its end, and the breach.
	 */
	public record TrailRig(BlockPos spot, BlockPos end, TorchTrail.Breach breach, BoundingBox box, java.util.function.Predicate<BlockPos> inCell) {
	}

	public static TrailRig trailRig(ServerLevel level, int x, int z) {
		Live.Result built = build(level, x, z, 2, false, 38);
		ArchivePlan plan = plan(built);
		int r = -4 * plan.cols - 5;
		int endF = plan.cellBack(0) + 3;
		BlockPos end = plan.at(r, 1, endF);
		BlockPos spot = plan.at(r, 1, endF + 40);
		for (int f = endF; f <= endF + 40; f++) {
			for (int u = 1; u <= 2; u++) {
				level.setBlock(plan.at(r, u, f), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		Sc17Config always = new Sc17Config();
		always.t0rchChance = 1.0;
		ArchiveTrail breach = new ArchiveTrail(() -> always, (l, near) -> Optional.of(plan));
		return new TrailRig(spot, end, breach, built.box(), pos -> plan.cellOf(pos) >= 0);
	}

	// --- worldgen ---

	/**
	 * Worldgen builds what {@link Live} builds: the short hall's start made by worldgen's own path (the piece saved and
	 * loaded back, placed chunk by chunk and finished) and by {@code Live.place} 640 blocks away: block for block and
	 * block entity for block entity the same; the recorder knew it once.
	 */
	public static void worldgenEqualsLive(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Snapshot before = Snapshot.current();
		int x0 = X + 4400;
		int z0 = Z;
		int shift = 640;
		int ground = Terrains.noise(level).ground(x0, z0);
		Start start = new Start(new BlockPos(x0, ground + 4, z0), Direction.NORTH, ArchivePlan.variant(false, 0.08), SEED);
		ArchivePlan probe = ArchivePlan.of(start, Terrains.noise(level));
		load(level, probe.box().inflatedBy(2));
		load(level, probe.box().inflatedBy(2).moved(shift, 0, 0));
		Holder<Structure> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
				.getOrThrow(ResourceKey.create(Registries.STRUCTURE, A1016_02.id("world/s17")));
		Sc2Data data = new Sc2Data();
		try {
			Recorder.drain(new Sc2Data());
			StructureStart made = Live.startOf(level, structure, start);
			helper.assertTrue(made.isValid() && made.getPieces().getFirst() instanceof Sc2Piece, "worldgen made no start");
			StructureStart loaded = Live.reload(level, made);
			helper.assertTrue(loaded != null && loaded.isValid(), "the saved start did not load");
			Live.placeStart(level, loaded);
			helper.assertTrue(Recorder.drain(data) == 1 && data.places().size() == 1 && data.places().getFirst().place().kind() == Remains.Kind.ARCHIVE,
					"the recorder knew " + data.places().size());
			Live.Result live = Live.place(level, S17.KIND, start.moved(shift, 0, 0));
			BoundingBox box = made.getBoundingBox();
			helper.assertTrue(live.box().equals(box.moved(shift, 0, 0)), "the boxes: " + box + " / " + live.box());
			List<String> differ = new ArrayList<>();
			int structure2 = 0;
			for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				BlockState a = level.getBlockState(pos);
				BlockState b = level.getBlockState(pos.offset(shift, 0, 0));
				structure2 += a.is(Blocks.BOOKSHELF) || a.is(Blocks.CHISELED_BOOKSHELF) ? 1 : 0;
				if (a != b && differ.size() < 8) {
					differ.add(pos.toShortString() + " " + a + " / " + b);
				}
				if (a.hasBlockEntity()) {
					BlockEntity ea = level.getBlockEntity(pos);
					BlockEntity eb = level.getBlockEntity(pos.offset(shift, 0, 0));
					if (ea != null && (eb == null || !ea.saveCustomOnly(level.registryAccess()).equals(eb.saveCustomOnly(level.registryAccess())))
							&& differ.size() < 8) {
						differ.add(pos.toShortString() + " block entity");
					}
				}
			}
			helper.assertTrue(differ.isEmpty(), "worldgen and Live differ: " + differ);
			helper.assertTrue(structure2 > 3000, "only " + structure2 + " bookshelves");
			ArchivePlan builtPlan = ArchivePlan.of(start, ArchiveShape.lengths(data.places().getFirst().extra()));
			helper.assertTrue(Records.titles(level, builtPlan).containsAll(ArchivePlan.LIST_NAMES), "worldgen's wall: " + Records.titles(level, builtPlan));
		} finally {
			Recorder.drain(new Sc2Data());
			Snapshot.install(before);
		}
		helper.succeed();
	}
}
