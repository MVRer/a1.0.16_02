package com.forzacode.a1016_02.world.sig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.TraceBatch;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.core.WorldProfile;
import com.forzacode.a1016_02.world.SignatureData;
import com.forzacode.a1016_02.world.WorldConfig;
import com.forzacode.a1016_02.world.WorldData;
import com.forzacode.a1016_02.world.gen.Builds;
import com.forzacode.a1016_02.world.gen.Hash;
import com.forzacode.a1016_02.world.gen.NoiseTerrain;
import com.forzacode.a1016_02.world.gen.ScarContext;
import com.forzacode.a1016_02.world.gen.ScarPlanner;
import com.forzacode.a1016_02.world.gen.Terrain;
import com.forzacode.a1016_02.world.live.LiveTerrain;
import com.forzacode.a1016_02.world.live.NewScarPlacer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import org.jspecify.annotations.Nullable;

/**
 * "Still burning" (SIGNATURE, D-004): an abandoned build about 2000 blocks from the subject, in chunks nobody has
 * visited or loaded, with a lit furnace smelting something ordinary. Furnaces only tick while their chunk ticks,
 * so it carries fuel and input for {@code stillBurningLitTicks} of burning: still lit when the player walks up.
 * When the profile rolled F21 the emptied house is built beside it, recorded as EMPTIED_HOUSE with the furnace in
 * reach of lore's F21 placement, and {@value #LORE_FLAG} is set. Everything is "left by others" (one
 * {@link TraceBatch}, the furnace with its block entity data). At most once per world, whatever triggers it: never
 * again once it stands, nor once lore left F21's furnace itself. Server thread only.
 */
public final class StillBurning {
	public static final String ID = "signature_still_burning";
	public static final String CAUSE = "world:still_burning";
	/** The camp's ABANDONED_BUILD site is claimed with this, so the emptied-house card never clears the furnace. */
	public static final String CLAIM = "world:still_burning";
	/** Shared flag: the world's one still-burning moment, where lore puts F21 (D-004). */
	public static final String LORE_FLAG = "lore:still_burning";
	public static final String F21 = "F21";
	/** The emptied house's center stands this far to the build's right. */
	static final int HOUSE_OFFSET = 9;
	/** Lore's F21 placement looks this far below and above an EMPTIED_HOUSE site for a lit furnace. */
	public static final int LORE_SCAN_BELOW = 2;
	public static final int LORE_SCAN_ABOVE = 3;
	/** One coal block: the burn the furnace's lit time stands for. */
	private static final int COAL_BLOCK_TICKS = 16000;
	private static final int COOK_TICKS = 200;
	/** A candidate whose chunks do not load within this many ticks is skipped. */
	private static final long LOAD_TIMEOUT_TICKS = 1200;
	/** Recorded sites this close to a camp candidate rule it out (blocks). */
	private static final int SITE_CLEARANCE = 24;

	/** The camp that was built. {@code house} is the emptied house's site (F21 only). */
	public record Camp(BlockPos furnace, BlockPos build, @Nullable BlockPos house) {
	}

	/** One camp ready to commit. */
	public record Plan(Builds.Build build, Builds.@Nullable Build house, BlockPos furnace, BlockState furnaceState, CompoundTag furnaceData,
			BoundingBox box) {
	}

	private record Candidate(int x, int z, Direction facing, long seed, List<ChunkPos> chunks) {
	}

	private static final class Search {
		final List<Candidate> candidates;
		final boolean f21;
		final @Nullable Consumer<String> report;
		int index;
		long candidateStarted;

		Search(List<Candidate> candidates, boolean f21, @Nullable Consumer<String> report, long now) {
			this.candidates = candidates;
			this.f21 = f21;
			this.report = report;
			this.candidateStarted = now;
		}
	}

	private static @Nullable Search search;
	private static long retryAt;

	private StillBurning() {
	}

	// --- once per world (D-004) ---

	/** Why the camp may not be built now, or null: it already stands, or lore already left F21's furnace. */
	public static @Nullable String refusal(SignatureData data, boolean loreFlag) {
		Optional<SignatureData.Burning> done = data.stillBurning();
		if (done.isPresent()) {
			return "still burning already happened at " + done.get().furnace().pos().toShortString();
		}
		if (loreFlag) {
			return "lore already left F21's still-burning furnace (D-004: once per world)";
		}
		return null;
	}

	/** True if the profile wants the camp at all (D-004 folds F21 in). */
	public static boolean wanted(WorldProfile profile) {
		return profile.hasStillBurning();
	}

	public static boolean withF21(WorldProfile profile) {
		return profile.fragments().contains(F21);
	}

	// --- the camp ---

	/**
	 * Plans the camp on a frame at ({@code x}, {@code floorY}, {@code z}), door towards {@code facing}: the
	 * abandoned build, its lit furnace against the right wall, and (with {@code f21}) the emptied house to the right.
	 */
	public static Plan plan(ServerLevel level, int x, int floorY, int z, Direction facing, Builds.Wood wood, long seed, boolean f21, WorldConfig config) {
		Frame f = new Frame(x, floorY, z, facing);
		Builds.Build build = Builds.abandonedBuild(x, floorY, z, facing, wood, seed);
		Builds.Build house = null;
		if (f21) {
			BlockPos center = f.at(HOUSE_OFFSET, 0, 0);
			house = Builds.emptiedHouse(center.getX(), floorY, center.getZ(), facing, wood, Hash.of(seed, 21));
		}
		BlockPos furnace = f.at(1, 1, 0);
		BlockState state = Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, facing.getCounterClockWise())
				.setValue(AbstractFurnaceBlock.LIT, true);
		BoundingBox box = footprint(f, f21);
		box = BoundingBox.encapsulating(box, build.blueprint().box());
		if (house != null) {
			box = BoundingBox.encapsulating(box, house.blueprint().box());
		}
		return new Plan(build, house, furnace, state, furnaceData(level, furnace, state, seed, config), box);
	}

	/** The ground the camp stands on: from 3 left of the build to 4 right of the house, 4 deep on each side. */
	static BoundingBox footprint(Frame f, boolean f21) {
		int right = f21 ? HOUSE_OFFSET + 4 : 3;
		return BoundingBox.fromCorners(f.at(-3, 0, -4), f.at(right, 0, 4));
	}

	/**
	 * The furnace as someone left it a moment ago: a full stack of something ordinary waiting, a few done, coal in
	 * the fuel slot, and a fresh burn of {@code stillBurningLitTicks} (at least as long as the input takes to smelt).
	 */
	public static CompoundTag furnaceData(ServerLevel level, BlockPos pos, BlockState state, long seed, WorldConfig config) {
		ItemStack[][] ordinary = {
			{new ItemStack(Items.COBBLESTONE), new ItemStack(Items.STONE)},
			{new ItemStack(Items.PORKCHOP), new ItemStack(Items.COOKED_PORKCHOP)},
			{new ItemStack(Items.POTATO), new ItemStack(Items.BAKED_POTATO)},
			{new ItemStack(Items.SAND), new ItemStack(Items.GLASS)},
			{new ItemStack(Items.BEEF), new ItemStack(Items.COOKED_BEEF)}
		};
		ItemStack[] pick = ordinary[Hash.below(seed ^ 31, ordinary.length)];
		int input = Mth.clamp(config.stillBurningInputCount, 1, pick[0].getMaxStackSize());
		FurnaceBlockEntity furnace = new FurnaceBlockEntity(pos, state);
		furnace.setItem(0, pick[0].copyWithCount(input));
		furnace.setItem(1, new ItemStack(Items.COAL, Hash.between(seed ^ 32, 3, 9)));
		furnace.setItem(2, pick[1].copyWithCount(Hash.between(seed ^ 33, 3, 11)));
		CompoundTag data = furnace.saveCustomOnly(level.registryAccess());
		int lit = Math.max(config.stillBurningLitTicks, input * COOK_TICKS);
		data.putInt("lit_time_remaining", lit);
		data.putInt("lit_total_time", Math.max(lit, COAL_BLOCK_TICKS));
		data.putInt("cooking_time_spent", Hash.between(seed ^ 34, 20, COOK_TICKS - 20));
		data.putInt("cooking_total_time", COOK_TICKS);
		return data;
	}

	/**
	 * Builds a planned camp as one out-of-view batch and records its sites: the ABANDONED_BUILD (claimed, see
	 * {@link #CLAIM}) and, with F21, the EMPTIED_HOUSE whose size reaches the furnace. Null if refused.
	 */
	public static @Nullable Camp build(ServerLevel level, Plan plan, TraceService traces, SiteSink sites) {
		TraceBatch batch = traces.batch(level, CAUSE);
		plan.build().blueprint().queue(level, batch);
		if (plan.house() != null) {
			plan.house().blueprint().queue(level, batch);
		}
		batch.leave(plan.furnace(), plan.furnaceState(), plan.furnaceData());
		if (!batch.commit()) {
			return null;
		}
		SiteRegistry.Site build = sites.record(SiteType.ABANDONED_BUILD, level.dimension(), plan.build().site(), plan.build().size());
		if (build != null) {
			sites.claim(build, CLAIM);
		}
		BlockPos house = null;
		if (plan.house() != null) {
			house = plan.house().site();
			sites.record(SiteType.EMPTIED_HOUSE, level.dimension(), house, houseSiteSize(house, plan.furnace()));
		}
		return new Camp(plan.furnace(), plan.build().site(), house);
	}

	/** The EMPTIED_HOUSE size that puts the furnace inside lore's F21 scan of the site (at least 3). */
	static int houseSiteSize(BlockPos house, BlockPos furnace) {
		return Math.max(3, Math.max(Math.abs(house.getX() - furnace.getX()), Math.abs(house.getZ() - furnace.getZ())));
	}

	// --- live ---

	/**
	 * Starts the camp: checks once-per-world and (unless {@code forced}) the profile, then looks for the spot in
	 * the background. {@code report} hears where it was built.
	 */
	public static Outcome start(MinecraftServer server, boolean forced, @Nullable Consumer<String> report) {
		SignatureData data = WorldData.get(server).signatures();
		HerobrineState state = HerobrineState.get(server);
		String no = refusal(data, state.hasFlag(LORE_FLAG));
		if (no != null) {
			return Outcome.failed(no);
		}
		if (!forced && !wanted(state.profile())) {
			return Outcome.failed("still burning is not this world's (signature " + state.profile().signature() + ", no F21)");
		}
		if (data.stillBurningPending() && search != null) {
			return Outcome.failed("the camp is already being placed");
		}
		data.setStillBurningPending(true);
		search = newSearch(server, report);
		retryAt = 0;
		if (search == null) {
			return Outcome.scheduled("the camp waits for the subject to be online");
		}
		return Outcome.scheduled(String.format(Locale.ROOT, "looking at %d unvisited spot(s) %s out%s", search.candidates.size(),
				search.f21 ? "within " + WorldConfig.get().stillBurningF21MaxFromBase + " of the base" : "about " + WorldConfig.get().stillBurningMinBlocks
						+ " to " + WorldConfig.get().stillBurningMaxBlocks + " blocks", search.f21 ? ", with F21's emptied house" : ""));
	}

	/** Called every second: loads the next candidate's chunks and builds the camp there. */
	public static void tick(MinecraftServer server) {
		SignatureData data = WorldData.get(server).signatures();
		if (!data.stillBurningPending()) {
			search = null;
			return;
		}
		if (refusal(data, HerobrineState.get(server).hasFlag(LORE_FLAG)) != null) {
			data.setStillBurningPending(false);
			search = null;
			return;
		}
		long now = server.getTickCount();
		if (search == null) {
			if (now < retryAt) {
				return;
			}
			search = newSearch(server, null);
			if (search == null) {
				retryAt = now + ModConfig.realTicks(WorldConfig.get().stillBurningRetryMinutes * 60);
				return;
			}
		}
		Search s = search;
		ServerLevel level = server.overworld();
		if (s.index >= s.candidates.size()) {
			finish(s, "no spot for the camp among " + s.candidates.size() + " candidate(s); trying again later");
			retryAt = now + ModConfig.realTicks(WorldConfig.get().stillBurningRetryMinutes * 60);
			return;
		}
		Candidate c = s.candidates.get(s.index);
		if (!ChunkLoads.request(level, c.chunks())) {
			if (now - s.candidateStarted > LOAD_TIMEOUT_TICKS) {
				next(s, now);
			}
			return;
		}
		Camp camp = tryCandidate(level, c, s.f21);
		if (camp == null) {
			next(s, now);
			return;
		}
		data.setStillBurning(new SignatureData.Burning(GlobalPos.of(level.dimension(), camp.furnace()), GlobalPos.of(level.dimension(), camp.build()),
				Optional.ofNullable(camp.house()).map(h -> GlobalPos.of(level.dimension(), h)), GameClock.day(server)));
		if (camp.house() != null) {
			HerobrineState.get(server).setFlag(LORE_FLAG, true);
		}
		String where = "still burning: furnace at " + camp.furnace().toShortString() + (camp.house() != null ? ", F21's emptied house at "
				+ camp.house().toShortString() : "");
		A1016_02.LOGGER.info("[a1016] world: {}", where);
		finish(s, where);
	}

	private static void next(Search s, long now) {
		s.index++;
		s.candidateStarted = now;
	}

	private static void finish(Search s, String message) {
		search = null;
		if (s.report != null) {
			s.report.accept(message);
		}
	}

	/** Called when a server stops. */
	public static void clear() {
		search = null;
		retryAt = 0;
	}

	/** Checks a loaded candidate against the real ground and builds there. */
	private static @Nullable Camp tryCandidate(ServerLevel level, Candidate c, boolean f21) {
		WorldConfig config = WorldConfig.get();
		LiveTerrain terrain = new LiveTerrain(level);
		Frame f = new Frame(c.x(), 0, c.z(), c.facing());
		int floor = terrain.ground(c.x(), c.z());
		if (floor <= level.getMinY() || terrain.wet(c.x(), c.z())) {
			return null;
		}
		for (BlockPos p : samples(f, f21)) {
			if (terrain.wet(p.getX(), p.getZ()) || Math.abs(terrain.ground(p.getX(), p.getZ()) - floor) > 3) {
				return null;
			}
		}
		for (ChunkPos chunk : c.chunks()) {
			if (Services.watch().lastVisitDay(level, chunk) >= 0) {
				return null; // somebody was here after all
			}
		}
		GlobalPos center = GlobalPos.of(level.dimension(), new BlockPos(c.x(), floor, c.z()));
		for (SiteType type : SiteType.values()) {
			if (!Services.sites().find(type, center, SITE_CLEARANCE + HOUSE_OFFSET).isEmpty()) {
				return null; // an old scar grew here when the chunks generated
			}
		}
		Holder<Biome> biome = terrain.surfaceBiome(c.x(), c.z());
		Plan plan = plan(level, c.x(), floor, c.z(), c.facing(), Builds.Wood.of(biome), c.seed(), f21, config);
		if (Services.protectedAreas().intersects(level.dimension(), plan.box())) {
			return null;
		}
		return build(level, plan, Services.traces(), SiteSink.LIVE);
	}

	/** The candidate camps for the subject now, best first, or null if the subject is not online. */
	private static @Nullable Search newSearch(MinecraftServer server, @Nullable Consumer<String> report) {
		Optional<ServerPlayer> subject = Services.watch().subject(server);
		if (subject.isEmpty()) {
			return null;
		}
		ServerLevel level = server.overworld();
		ServerPlayer player = subject.get();
		BlockPos from = player.level() == level ? player.blockPosition() : level.getRespawnData().pos();
		BlockPos base = Services.watch().base(player).filter(b -> b.dimension().equals(level.dimension())).map(GlobalPos::pos).orElse(from);
		boolean f21 = withF21(HerobrineState.get(server).profile());
		List<Candidate> candidates = candidates(level, from, base, f21, WorldConfig.get(), RandomSource.create(), Services.watch()::lastVisitDay);
		return new Search(candidates, f21, report, server.getTickCount());
	}

	/**
	 * Spots for the camp from the noise (nothing is loaded): dry, flat, not dense woods, in chunks never visited
	 * and not loaded now. Without F21 they are {@code stillBurningMin..MaxBlocks} from {@code from}; with F21 within
	 * {@code stillBurningF21MaxFromBase} of the base and at least {@code stillBurningF21MinBlocks} from {@code from},
	 * farthest first.
	 */
	static List<Candidate> candidates(ServerLevel level, BlockPos from, BlockPos base, boolean f21, WorldConfig config, RandomSource random,
			NewScarPlacer.VisitLookup visits) {
		Terrain terrain = noise(level);
		List<Candidate> found = new ArrayList<>();
		int wanted = Math.max(1, config.stillBurningCandidates);
		for (int attempt = 0; attempt < 240 && found.size() < wanted; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			BlockPos center;
			int d;
			if (f21) {
				d = Mth.nextInt(random, Math.max(200, config.stillBurningF21MaxFromBase / 2), Math.max(201, config.stillBurningF21MaxFromBase));
				center = base.offset(Mth.floor(Math.cos(angle) * d), 0, Mth.floor(Math.sin(angle) * d));
				if (horizontal(center, from) < config.stillBurningF21MinBlocks) {
					continue;
				}
			} else {
				d = Mth.nextInt(random, config.stillBurningMinBlocks, Math.max(config.stillBurningMinBlocks, config.stillBurningMaxBlocks));
				center = from.offset(Mth.floor(Math.cos(angle) * d), 0, Mth.floor(Math.sin(angle) * d));
			}
			Direction facing = Direction.from2DDataValue(random.nextInt(4));
			Frame f = new Frame(center.getX(), 0, center.getZ(), facing);
			if (!dryAndFlat(terrain, f, f21)) {
				continue;
			}
			BoundingBox box = footprint(f, f21);
			List<ChunkPos> chunks = ChunkLoads.around(box, 0);
			boolean fresh = chunks.stream().allMatch(chunk -> visits.lastVisitDay(level, chunk) < 0
					&& level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) == null);
			if (!fresh || Services.protectedAreas().intersects(level.dimension(),
					new BoundingBox(box.minX(), level.getMinY(), box.minZ(), box.maxX(), level.getMaxY(), box.maxZ()))) {
				continue;
			}
			found.add(new Candidate(center.getX(), center.getZ(), facing, random.nextLong(), chunks));
		}
		if (f21) {
			found.sort(Comparator.comparingDouble((Candidate c) -> horizontal(new BlockPos(c.x(), 0, c.z()), from)).reversed());
		}
		return found;
	}

	private static boolean dryAndFlat(Terrain terrain, Frame f, boolean f21) {
		Holder<Biome> biome = terrain.surfaceBiome(f.x(), f.z());
		if (Terrain.isWetland(biome) || Terrain.isDenseWoods(biome)) {
			return false;
		}
		int center = terrain.ground(f.x(), f.z());
		if (center <= terrain.seaLevel()) {
			return false;
		}
		for (BlockPos p : samples(f, f21)) {
			if (terrain.wet(p.getX(), p.getZ()) || Math.abs(terrain.ground(p.getX(), p.getZ()) - center) > 3) {
				return false;
			}
		}
		return true;
	}

	/** Corners of the camp's ground, plus the house center with F21. */
	private static List<BlockPos> samples(Frame f, boolean f21) {
		int right = f21 ? HOUSE_OFFSET + 3 : 2;
		List<BlockPos> points = new ArrayList<>(List.of(f.at(-2, 0, -2), f.at(-2, 0, 2), f.at(right, 0, -3), f.at(right, 0, 3), f.at(0, 0, 0)));
		if (f21) {
			points.add(f.at(HOUSE_OFFSET, 0, 0));
			points.add(f.at(HOUSE_OFFSET - 3, 0, -3));
			points.add(f.at(HOUSE_OFFSET - 3, 0, 3));
		}
		return points;
	}

	private static Terrain noise(ServerLevel level) {
		ScarContext context = ScarContext.current();
		ScarPlanner planner = context == null ? null : context.planner(level);
		return planner != null ? planner.terrain() : new NoiseTerrain(level);
	}

	private static double horizontal(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Debug: one line about the camp. */
	public static String status(MinecraftServer server) {
		SignatureData data = WorldData.get(server).signatures();
		Optional<SignatureData.Burning> done = data.stillBurning();
		if (done.isPresent()) {
			return "still burning: furnace at " + done.get().furnace().pos().toShortString() + done.get().emptiedHouse()
					.map(h -> ", F21's emptied house at " + h.pos().toShortString()).orElse("") + " (day " + done.get().day() + ")";
		}
		Search s = search;
		if (data.stillBurningPending()) {
			return s == null ? "still burning: pending (waiting to search)" : String.format(Locale.ROOT, "still burning: pending, candidate %d of %d",
					s.index + 1, s.candidates.size());
		}
		return "still burning: not yet";
	}
}
