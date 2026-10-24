package com.forzacode.a1016_02.core;

import java.util.Objects;

/**
 * One instance of each service. Stubs are the default; the owning workstream installs its real implementation
 * in its {@code init()}. {@link TraceService}, {@link PlayerWatch}, {@link SiteRegistry} and {@link ProtectedAreas}
 * are real, in core.
 */
public final class Services {
	private static final TraceService TRACES = new TraceService(false);
	private static final PlayerWatch WATCH = new PlayerWatch();
	private static final SiteRegistry SITES = new SiteRegistry();
	private static final ProtectedAreas PROTECTED = new ProtectedAreas();

	private static Director director = new Director.Stub();
	private static MobTamper mobs = new MobTamper.Stub();
	private static FragmentService fragments = new FragmentService.Stub();
	private static AccidentPlanner accidents = new AccidentPlanner.Stub();
	private static DeathMarker deaths = new DeathMarker.Stub();
	private static Figure figure = new Figure.Stub();
	private static Remains remains = new Remains.Stub();

	private Services() {
	}

	public static Director director() {
		return director;
	}

	public static TraceService traces() {
		return TRACES;
	}

	public static PlayerWatch watch() {
		return WATCH;
	}

	public static SiteRegistry sites() {
		return SITES;
	}

	/** Areas scars and edits must leave alone. */
	public static ProtectedAreas protectedAreas() {
		return PROTECTED;
	}

	public static MobTamper mobs() {
		return mobs;
	}

	public static FragmentService fragments() {
		return fragments;
	}

	public static AccidentPlanner accidents() {
		return accidents;
	}

	public static DeathMarker deaths() {
		return deaths;
	}

	/** The figure, for the sighting another workstream sets up: the glimpse (D-073). */
	public static Figure figure() {
		return figure;
	}

	/** His places, the second wave's worldgen (docs/SELF_CREATED_2.md): what the world generated and who went in. */
	public static Remains remains() {
		return remains;
	}

	/** Director workstream. */
	public static void installDirector(Director impl) {
		director = Objects.requireNonNull(impl);
	}

	/** Atmosphere workstream (D-017). */
	public static void installMobTamper(MobTamper impl) {
		mobs = Objects.requireNonNull(impl);
	}

	/** Lore workstream. */
	public static void installFragments(FragmentService impl) {
		fragments = Objects.requireNonNull(impl);
	}

	/** Accident workstream. */
	public static void installAccidents(AccidentPlanner impl) {
		accidents = Objects.requireNonNull(impl);
	}

	/** Accident workstream. */
	public static void installDeathMarker(DeathMarker impl) {
		deaths = Objects.requireNonNull(impl);
	}

	/** Entity workstream (D-073). */
	public static void installFigure(Figure impl) {
		figure = Objects.requireNonNull(impl);
	}

	/** World workstream (docs/SELF_CREATED_2.md, SC2-0b). */
	public static void installRemains(Remains impl) {
		remains = Objects.requireNonNull(impl);
	}
}
