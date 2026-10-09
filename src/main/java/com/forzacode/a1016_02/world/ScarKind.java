package com.forzacode.a1016_02.world;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.SiteType;

/**
 * Every scar the world workstream can make (DESIGN.md "The world is wrong"). Worldgen kinds are weighted by the
 * world profile's habits; {@link #RUINED_HUT} is the one-per-world hut and is never rolled.
 */
public enum ScarKind {
	DEAD_MOUNTAIN("dead_mountain", SiteType.DEAD_MOUNTAIN, true, true, 0.6, Habit.STRIPPER),
	BARE_FOREST("bare_forest", SiteType.BARE_GROVE, true, true, 2.0, Habit.STRIPPER),
	CUT("cut", SiteType.CUT, false, true, 0.6, Habit.CARVER),
	TUNNEL("tunnel", SiteType.CUT, false, true, 0.6, Habit.CARVER),
	STAIR("stair", SiteType.STAIR_BOTTOM, false, true, 0.5, Habit.CARVER),
	ABANDONED_BUILD("abandoned_build", SiteType.ABANDONED_BUILD, false, true, 1.0, Habit.VISITOR),
	PANIC_TOWER("panic_tower", SiteType.PANIC_TOWER, false, true, 0.3, Habit.VISITOR, Habit.WATCHER),
	EMPTIED_HOUSE("emptied_house", SiteType.EMPTIED_HOUSE, false, true, 0.5, Habit.VISITOR, Habit.COLLECTOR),
	CROSS("cross", SiteType.CROSS, false, true, 0.6, Habit.MOURNER),
	LONE_LIGHT("lone_light", SiteType.LONE_LIGHT, false, true, 0.5, Habit.MOURNER, Habit.WATCHER),
	OCEAN_PYRAMID("ocean_pyramid", SiteType.OCEAN_PYRAMID, false, true, 3.0, Habit.COLLECTOR),
	RUINED_HUT("ruined_hut", SiteType.RUINED_HUT, false, false, 0.0);

	private final String id;
	private final SiteType site;
	private final boolean area;
	private final boolean worldgen;
	private final double defaultWeight;
	private final Set<Habit> habits;

	ScarKind(String id, SiteType site, boolean area, boolean worldgen, double defaultWeight, Habit... habits) {
		this.id = id;
		this.site = site;
		this.area = area;
		this.worldgen = worldgen;
		this.defaultWeight = defaultWeight;
		this.habits = habits.length == 0 ? EnumSet.noneOf(Habit.class) : EnumSet.copyOf(Arrays.asList(habits));
	}

	/** Snake-case name used in config and commands. */
	public String id() {
		return id;
	}

	public SiteType site() {
		return site;
	}

	/** Large scars that span many chunks (dead mountain, bare forest). */
	public boolean area() {
		return area;
	}

	/** Rolled per grid cell during worldgen. */
	public boolean worldgen() {
		return worldgen;
	}

	public double defaultWeight() {
		return defaultWeight;
	}

	public Set<Habit> habits() {
		return habits;
	}

	public boolean matches(Set<Habit> worldHabits) {
		for (Habit habit : habits) {
			if (worldHabits.contains(habit)) {
				return true;
			}
		}
		return false;
	}

	public static Optional<ScarKind> byId(String id) {
		for (ScarKind kind : values()) {
			if (kind.id.equals(id)) {
				return Optional.of(kind);
			}
		}
		return Optional.empty();
	}
}
