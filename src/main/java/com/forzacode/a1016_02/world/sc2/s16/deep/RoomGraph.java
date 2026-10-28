package com.forzacode.a1016_02.world.sc2.s16.deep;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.world.sc2.s16.deep.DepthsPlan.Doorway;
import com.forzacode.a1016_02.world.sc2.s16.deep.DepthsPlan.House;
import com.forzacode.a1016_02.world.sc2.s16.deep.DepthsPlan.Level;
import com.forzacode.a1016_02.world.sc2.s16.deep.DepthsPlan.Way;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * The depths' houses as rooms (docs/SELF_CREATED_2_R3.md, "33", "Answers"): which house a player stands in, the houses
 * they can see from there (straight on through every wall that is a pane or an open doorway or door: a pane is not a
 * wall), how many doorways a house is from another (through any doorway one can pass, a shut door included), and the
 * empty house between two players in a row. Built once a place a run from its plan; the doors' states are read live.
 */
public final class RoomGraph {
	private final Depths depths;
	private final DepthsPlan plan;
	/** Each house's doorways to another house (a stair's are not here). */
	private final List<List<Doorway>> walls = new ArrayList<>();

	public RoomGraph(Depths depths) {
		this.depths = depths;
		this.plan = depths.plan();
		for (int i = 0; i < plan.houses().size(); i++) {
			walls.add(new ArrayList<>());
		}
		for (Doorway d : plan.doorways()) {
			if (d.a() >= 0 && d.b() >= 0) {
				walls.get(d.a()).add(d);
				walls.get(d.b()).add(d);
			}
		}
	}

	public Depths depths() {
		return depths;
	}

	/** The house whose inside holds these feet (or whose doorway they stand in), or -1. */
	public int houseAt(Vec3 feet) {
		BlockPos at = BlockPos.containing(feet);
		House h = depths.houseOf(at.getX(), at.getY(), at.getZ());
		if (h != null) {
			return h.index();
		}
		int s = plan.s(at.getX(), at.getZ());
		int f = plan.f(at.getX(), at.getZ());
		for (Doorway d : plan.doorways()) {
			if (d.s() == s && d.f() == f && at.getY() >= d.feet() && at.getY() <= d.feet() + 1) {
				return d.a() >= 0 ? d.a() : d.b();
			}
		}
		return -1;
	}

	/** True if one sees through this doorway now: an empty or broken one, a pane beside it (the rooms), or its door standing open. */
	public boolean seeThrough(BlockGetter level, Doorway d) {
		Way way = d.way();
		if (way == Way.OPEN || way == Way.BROKEN || way == Way.STAIR_FOOT) {
			return true;
		}
		if (way == Way.STUFFED) {
			return false;
		}
		if (way == Way.DOOR && d.a() >= 0 && plan.house(d.a()).level() == Level.L2) {
			return true;
		}
		return open(level, d);
	}

	/** True if the doorway's door stands open now (or it holds no door any more). */
	public boolean open(BlockGetter level, Doorway d) {
		BlockState lower = level.getBlockState(depths.cells(d).get(0));
		return !(lower.getBlock() instanceof DoorBlock) || lower.getValue(DoorBlock.OPEN);
	}

	/** True if it still holds its door (wooden or iron), shut or open. */
	public boolean hasDoor(BlockGetter level, Doorway d) {
		return level.getBlockState(depths.cells(d).get(0)).getBlock() instanceof DoorBlock;
	}

	/** The houses seen from inside {@code house} (itself first): straight on, house after house, through see-through walls. */
	public Set<Integer> seenFrom(BlockGetter level, int house) {
		Set<Integer> out = new HashSet<>();
		if (house < 0) {
			return out;
		}
		out.add(house);
		for (int[] dir : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			int at = house;
			while (true) {
				Doorway through = toward(at, dir);
				if (through == null || !seeThrough(level, through)) {
					break;
				}
				at = through.a() == at ? through.b() : through.a();
				out.add(at);
			}
		}
		return out;
	}

	/** The doorway in {@code house}'s wall toward grid direction {@code dir} ({di, dj}), or null. */
	private @Nullable Doorway toward(int house, int[] dir) {
		House h = plan.house(house);
		for (Doorway d : walls.get(house)) {
			House o = plan.house(d.a() == house ? d.b() : d.a());
			if (o.i() - h.i() == dir[0] && o.j() - h.j() == dir[1]) {
				return d;
			}
		}
		return null;
	}

	/** Every house anyone sees now: from the house they stand in, or from a doorway they stand by (within 3). */
	public Set<Integer> seen(BlockGetter level, Collection<ServerPlayer> players) {
		Set<Integer> out = new HashSet<>();
		for (ServerPlayer player : players) {
			out.addAll(seenBy(level, player.position()));
		}
		return out;
	}

	/** The houses seen from these feet. */
	public Set<Integer> seenBy(BlockGetter level, Vec3 feet) {
		int house = houseAt(feet);
		if (house >= 0) {
			return seenFrom(level, house);
		}
		Set<Integer> out = new HashSet<>();
		BlockPos at = BlockPos.containing(feet);
		for (Doorway d : plan.doorways()) {
			BlockPos cell = depths.cells(d).get(0);
			if (Math.abs(cell.getX() - at.getX()) <= 3 && Math.abs(cell.getZ() - at.getZ()) <= 3 && Math.abs(cell.getY() - at.getY()) <= 3
					&& seeThrough(level, d)) {
				if (d.a() >= 0) {
					out.add(d.a());
				}
				if (d.b() >= 0) {
					out.add(d.b());
				}
			}
		}
		return out;
	}

	/** How many doorways each house is from {@code house} through doorways one can pass (a shut door too); -1 unreached. */
	public int[] distances(int house) {
		int[] dist = new int[plan.houses().size()];
		Arrays.fill(dist, -1);
		if (house < 0) {
			return dist;
		}
		dist[house] = 0;
		ArrayDeque<Integer> queue = new ArrayDeque<>(List.of(house));
		while (!queue.isEmpty()) {
			int at = queue.poll();
			for (Doorway d : walls.get(at)) {
				int next = d.a() == at ? d.b() : d.a();
				if (d.way().passable() && dist[next] < 0) {
					dist[next] = dist[at] + 1;
					queue.add(next);
				}
			}
		}
		return dist;
	}

	/** A doorway's distance from a player's house: the nearer of its two houses (a stair's door: its house's). */
	public static int distance(Doorway d, int[] dist) {
		int a = d.a() >= 0 ? dist[d.a()] : Integer.MAX_VALUE;
		int b = d.b() >= 0 ? dist[d.b()] : Integer.MAX_VALUE;
		int min = Math.min(a < 0 ? Integer.MAX_VALUE : a, b < 0 ? Integer.MAX_VALUE : b);
		return min == Integer.MAX_VALUE ? -1 : min;
	}

	/** The house sharing a wall with {@code house} toward grid direction {@code dir}, or -1 (none on that side). */
	public int beside(int house, int[] dir) {
		House h = plan.house(house);
		for (House o : plan.houses(h.level())) {
			if (o.i() - h.i() == dir[0] && o.j() - h.j() == dir[1]) {
				return o.index();
			}
		}
		return -1;
	}

	/** The empty house between two houses of a row with one house between them, or -1. */
	public int between(int a, int b) {
		House ha = plan.house(a);
		House hb = plan.house(b);
		if (ha.level() != hb.level() || ha.level() == Level.L1 || ha.level() == Level.L4) {
			return -1;
		}
		boolean row = ha.i() == hb.i() && Math.abs(ha.j() - hb.j()) == 2 || ha.j() == hb.j() && Math.abs(ha.i() - hb.i()) == 2;
		return row ? beside(a, new int[] {Integer.signum(hb.i() - ha.i()), Integer.signum(hb.j() - ha.j())}) : -1;
	}

	/** The doorways in a house's walls to other houses. */
	public List<Doorway> walls(int house) {
		return walls.get(house);
	}
}
