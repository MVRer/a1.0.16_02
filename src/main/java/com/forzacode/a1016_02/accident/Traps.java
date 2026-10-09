package com.forzacode.a1016_02.accident;

import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.accident.trap.BareWoolTrap;
import com.forzacode.a1016_02.accident.trap.CairnLureTrap;
import com.forzacode.a1016_02.accident.trap.DarkCornerTrap;
import com.forzacode.a1016_02.accident.trap.FallingDripstoneTrap;
import com.forzacode.a1016_02.accident.trap.FloodedTunnelTrap;
import com.forzacode.a1016_02.accident.trap.GravelCeilingTrap;
import com.forzacode.a1016_02.accident.trap.GroveLureTrap;
import com.forzacode.a1016_02.accident.trap.HouseFireTrap;
import com.forzacode.a1016_02.accident.trap.LavaFloorTrap;
import com.forzacode.a1016_02.accident.trap.LavaWallTrap;
import com.forzacode.a1016_02.accident.trap.MissingRungTrap;
import com.forzacode.a1016_02.accident.trap.MovedMobTrap;
import com.forzacode.a1016_02.accident.trap.NoBedTrap;
import com.forzacode.a1016_02.accident.trap.PowderSnowTrap;
import com.forzacode.a1016_02.accident.trap.ShortBridgeTrap;
import com.forzacode.a1016_02.accident.trap.SleepLureTrap;
import com.forzacode.a1016_02.accident.trap.WhiteEyesLureTrap;

/** Every trap: the Accidents table in order, then the four lure traps. */
public final class Traps {
	public static final TrapKind LAVA_FLOOR = new LavaFloorTrap();
	public static final TrapKind LAVA_WALL = new LavaWallTrap();
	public static final TrapKind HOUSE_FIRE = new HouseFireTrap();
	public static final TrapKind GRAVEL_CEILING = new GravelCeilingTrap();
	public static final TrapKind FALLING_DRIPSTONE = new FallingDripstoneTrap();
	public static final TrapKind MISSING_RUNG = new MissingRungTrap();
	public static final TrapKind SHORT_BRIDGE = new ShortBridgeTrap();
	public static final TrapKind FLOODED_TUNNEL = new FloodedTunnelTrap();
	public static final TrapKind DARK_CORNER = new DarkCornerTrap();
	public static final TrapKind MOVED_MOB = new MovedMobTrap();
	public static final TrapKind NO_BED = new NoBedTrap();
	public static final TrapKind POWDER_SNOW = new PowderSnowTrap();
	public static final TrapKind BARE_WOOL = new BareWoolTrap();
	public static final TrapKind LURE_CAIRN = new CairnLureTrap();
	public static final TrapKind LURE_GROVE = new GroveLureTrap();
	public static final TrapKind LURE_WHITE_EYES = new WhiteEyesLureTrap();
	public static final TrapKind LURE_SLEEP = new SleepLureTrap();

	public static final List<TrapKind> ALL = List.of(LAVA_FLOOR, LAVA_WALL, HOUSE_FIRE, GRAVEL_CEILING, FALLING_DRIPSTONE, MISSING_RUNG,
			SHORT_BRIDGE, FLOODED_TUNNEL, DARK_CORNER, MOVED_MOB, NO_BED, POWDER_SNOW, BARE_WOOL, LURE_CAIRN, LURE_GROVE, LURE_WHITE_EYES, LURE_SLEEP);

	private Traps() {
	}

	public static Optional<TrapKind> byId(String id) {
		return ALL.stream().filter(t -> t.id().equals(id)).findFirst();
	}

	/** The director card id of a trap. */
	public static String cardId(TrapKind trap) {
		return "accident_" + trap.id();
	}
}
