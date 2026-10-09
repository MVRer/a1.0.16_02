package com.forzacode.a1016_02.dig;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.TraceService;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

/**
 * "The tunnel that grows": a 2x2 tunnel that starts in a cave wall or hillside 40 to 72 blocks out and points at the
 * base. Each visit it is about {@code Pacing.tunnelGrowthPerVisit} blocks longer (grown while the player is away). It
 * stops just short of the base and never breaks in: it ends as soon as the next step would come within the card
 * clearance of anything the player dug or placed, or within {@link DigConfig#growingStopShortOfBase} of the base.
 */
public final class GrowingTunnel {
	public static final String CAUSE = "dig:tunnel_that_grows";
	private static final Set<String> PLAYER_REASONS = Set.of("near a player dig", "near a player block", "player space", "player block");

	final ResourceKey<Level> dimension;
	final BlockPos base;
	/** The first anchor (in the wall). */
	final BlockPos first;
	final List<BlockPos> anchors = new ArrayList<>();
	Direction dir;
	boolean complete;
	boolean visited;
	long lastGrowDay;
	int siteId = -1;

	GrowingTunnel(ResourceKey<Level> dimension, BlockPos base, BlockPos first, Direction dir) {
		this.dimension = dimension;
		this.base = base.immutable();
		this.first = first.immutable();
		this.dir = dir;
	}

	public int length() {
		return anchors.size();
	}

	public boolean complete() {
		return complete;
	}

	public List<BlockPos> anchors() {
		return List.copyOf(anchors);
	}

	public BlockPos base() {
		return base;
	}

	/** The far end (last anchor), or the first anchor before anything was carved. */
	public BlockPos end() {
		return anchors.isEmpty() ? first : anchors.getLast();
	}

	/** Horizontal distance from the center of an anchor's cube to the base. */
	double distanceToBase(BlockPos anchor) {
		double dx = anchor.getX() + 1.0 - (base.getX() + 0.5);
		double dz = anchor.getZ() + 1.0 - (base.getZ() + 0.5);
		return Math.sqrt(dx * dx + dz * dz);
	}

	/**
	 * Finds a wall facing the base (a cave the player explored first, then any cave or hillside) and carves the
	 * first {@code steps} of the tunnel. Null if no spot works now.
	 */
	static @Nullable GrowingTunnel start(ServerLevel level, BlockPos base, @Nullable PosSet explored, DigConfig config, int clearance, int steps,
			RandomSource random, TraceService traces, long day) {
		List<BlockPos> mouths = new ArrayList<>();
		if (explored != null) {
			List<BlockPos> ring = new ArrayList<>();
			explored.forEachNear(base, config.growingStartMax, packed -> {
				BlockPos pos = BlockPos.of(packed);
				double dist = horizontalDist(pos, base);
				if (dist >= config.growingStartMin && dist <= config.growingStartMax && Math.abs(pos.getY() - base.getY()) <= 16) {
					ring.add(pos);
				}
			});
			NetworkGrower.shuffle(ring, random);
			mouths.addAll(ring.subList(0, Math.min(32, ring.size())));
		}
		for (int i = 0; i < 48; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double r = Mth.lerp(random.nextDouble(), config.growingStartMin, config.growingStartMax);
			int x = base.getX() + (int) Math.round(Math.cos(angle) * r);
			int z = base.getZ() + (int) Math.round(Math.sin(angle) * r);
			for (int dy = 0; dy <= 16; dy++) {
				int y = base.getY() + (dy % 2 == 0 ? dy / 2 : -(dy + 1) / 2);
				BlockPos pos = new BlockPos(x, y, z);
				if (level.isLoaded(pos) && level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
						&& Tunnels.seals(level.getBlockState(pos.below()))) {
					mouths.add(pos);
					break;
				}
			}
		}
		Tunnels.Rules rules = Tunnels.Rules.card(clearance);
		for (BlockPos spot : mouths) {
			Direction dir = toward(spot, base);
			int along = Math.abs(dir.getStepX() != 0 ? base.getX() - spot.getX() : base.getZ() - spot.getZ());
			int perp = Math.abs(dir.getStepX() != 0 ? base.getZ() - spot.getZ() : base.getX() - spot.getX());
			if (perp * 2 > along) {
				continue;
			}
			// Walk toward the base while there is air at feet and head height: the last air block is the mouth.
			BlockPos mouth = spot;
			for (int i = 0; i < 8 && level.isLoaded(mouth.relative(dir)) && level.getBlockState(mouth.relative(dir)).isAir()
					&& level.getBlockState(mouth.relative(dir).above()).isAir(); i++) {
				mouth = mouth.relative(dir);
			}
			BlockPos front = Tunnels.anchorInFront(mouth, dir);
			Direction side = dir.getClockWise();
			for (BlockPos anchor : List.of(front, front.relative(side, -1))) {
				// It has to start in a wall, not in the open.
				long solid = Tunnels.cube(anchor).stream().filter(cell -> Tunnels.carvable(level.getBlockState(cell))).count();
				if (solid < 6 || Tunnels.check(level, anchor, new LongOpenHashSet(), rules) != null) {
					continue;
				}
				GrowingTunnel tunnel = new GrowingTunnel(level.dimension(), base, anchor, dir);
				if (tunnel.grow(level, steps, clearance, config, traces, day) && !tunnel.anchors.isEmpty()) {
					return tunnel;
				}
			}
		}
		return null;
	}

	/**
	 * Carves up to {@code steps} more anchors toward the base as one out-of-view batch. Marks the tunnel complete
	 * (and records its TUNNEL_END site) when it reaches the stop distance or the player's spaces. False if nothing
	 * was carved (in view, or blocked for now).
	 */
	boolean grow(ServerLevel level, int steps, int clearance, DigConfig config, TraceService traces, long day) {
		if (complete) {
			return false;
		}
		Tunnels.Rules rules = Tunnels.Rules.card(clearance);
		LongOpenHashSet before = Tunnels.cells(anchors);
		LongOpenHashSet existing = new LongOpenHashSet(before);
		List<BlockPos> planned = new ArrayList<>();
		Direction heading = dir;
		boolean reachedEnd = false;
		boolean blocked = false;
		BlockPos last = anchors.isEmpty() ? null : anchors.getLast();
		for (int i = 0; i < steps; i++) {
			BlockPos next;
			if (last == null) {
				next = first;
			} else {
				heading = steer(last, heading);
				next = last.relative(heading);
			}
			if (last != null && distanceToBase(next) <= config.growingStopShortOfBase) {
				reachedEnd = true;
				break;
			}
			String why = Tunnels.check(level, next, existing, rules);
			if (why != null && last != null) {
				for (BlockPos jog : List.of(next.above(), next.below())) {
					if (Tunnels.check(level, jog, existing, rules) == null) {
						next = jog;
						why = null;
						break;
					}
				}
			}
			if (why != null) {
				// Never into the player's spaces: that is where it ends. Other obstacles end it once nothing is left to carve.
				reachedEnd = PLAYER_REASONS.contains(why);
				blocked = !Tunnels.UNLOADED.equals(why);
				break;
			}
			planned.add(next);
			existing.addAll(Tunnels.cells(List.of(next)));
			last = next;
		}
		if (planned.isEmpty()) {
			if ((reachedEnd || blocked) && !anchors.isEmpty()) {
				finish();
			}
			return false;
		}
		if (!Tunnels.carve(level, traces, planned, before, CAUSE)) {
			return false;
		}
		anchors.addAll(planned);
		dir = heading;
		lastGrowDay = day;
		if (reachedEnd) {
			finish();
		}
		return true;
	}

	private void finish() {
		complete = true;
		if (siteId < 0) {
			siteId = Services.sites().record(SiteType.TUNNEL_END, dimension, end(), length()).id();
		}
	}

	/** Keeps going along the current axis while it still closes in on the base, then turns toward it (rare turns). */
	private Direction steer(BlockPos last, Direction current) {
		double dx = base.getX() + 0.5 - (last.getX() + 1.0);
		double dz = base.getZ() + 0.5 - (last.getZ() + 1.0);
		double along = current.getStepX() != 0 ? dx * current.getStepX() : dz * current.getStepZ();
		if (along > 1.5) {
			return current;
		}
		if (current.getStepX() != 0) {
			return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
		}
		return dx >= 0 ? Direction.EAST : Direction.WEST;
	}

	private static Direction toward(BlockPos from, BlockPos to) {
		int dx = to.getX() - from.getX();
		int dz = to.getZ() - from.getZ();
		return Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? Direction.EAST : Direction.WEST) : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
	}

	static double horizontalDist(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	// --- NBT ---

	CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putString("dimension", dimension.identifier().toString());
		tag.putLong("base", base.asLong());
		tag.putLong("first", first.asLong());
		tag.putLongArray("anchors", anchors.stream().mapToLong(BlockPos::asLong).toArray());
		tag.putInt("dir", dir.get3DDataValue());
		tag.putBoolean("complete", complete);
		tag.putBoolean("visited", visited);
		tag.putLong("lastGrowDay", lastGrowDay);
		tag.putInt("site", siteId);
		return tag;
	}

	static GrowingTunnel load(CompoundTag tag) {
		ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, Identifier.parse(tag.getStringOr("dimension", "minecraft:overworld")));
		GrowingTunnel tunnel = new GrowingTunnel(dimension, BlockPos.of(tag.getLongOr("base", 0L)), BlockPos.of(tag.getLongOr("first", 0L)),
				Direction.from3DDataValue(tag.getIntOr("dir", 2)));
		for (long packed : tag.getLongArray("anchors").orElse(new long[0])) {
			tunnel.anchors.add(BlockPos.of(packed));
		}
		tunnel.complete = tag.getBooleanOr("complete", false);
		tunnel.visited = tag.getBooleanOr("visited", false);
		tunnel.lastGrowDay = tag.getLongOr("lastGrowDay", 0L);
		tunnel.siteId = tag.getIntOr("site", -1);
		return tunnel;
	}
}
