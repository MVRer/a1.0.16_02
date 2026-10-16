package com.forzacode.a1016_02.entity;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The sighting variants (DESIGN.md "Sightings"), one event card each but the glimpse. Each one says where he stands,
 * how he stands, and how the sighting ends.
 */
public enum Variant {
	/** Something cow-sized and still: low on all fours. Walk closer (or stare) and he rises, stares, then runs. */
	COW("cow", Tier.AMBIENT, Stage.ALONE, Spot.OPEN, Pose.LOW, Facing.PLAYER, false, Ending.RISE_STARE_LEAVE, Gait.RUN, Fake.COW),
	/** His back to you, walking slowly deeper into the fog. He never speeds up. */
	WALKS_AWAY("walks_away", Tier.MINOR, Stage.TRACES, Spot.OPEN, Pose.STAND, Facing.AWAY, false, Ending.NONE, Gait.SLOW, Fake.ZOMBIE),
	/** A still silhouette on a hilltop against the dusk sky. Look away and back, and the ridge is empty. */
	RIDGE("ridge", Tier.MINOR, Stage.TRACES, Spot.RIDGE, Pose.STAND, Facing.PLAYER, false, Ending.STARE_LEAVE, Gait.WALK, Fake.ZOMBIE),
	/** Half of him visible behind a trunk, watching. Gone when you round the tree. */
	BETWEEN_TRUNKS("between_trunks", Tier.MINOR, Stage.TRACES, Spot.TRUNK, Pose.STAND, Facing.PLAYER, true, Ending.HIDE, Gait.WALK, Fake.ZOMBIE),
	/** At the edge of a lone light's glow at night, facing the light, not you. */
	IN_THE_LIGHT("in_the_light", Tier.MINOR, Stage.TRACES, Spot.LIGHT, Pose.STAND, Facing.ANCHOR, false, Ending.STARE_LEAVE, Gait.RUN, Fake.NONE),
	/** On the far shore of a lake or bay. He doesn't move. Cross the water and the shore is empty. */
	ACROSS_WATER("across_water", Tier.MINOR, Stage.TRACES, Spot.SHORE, Pose.STAND, Facing.PLAYER, false, Ending.STARE_LEAVE, Gait.WALK, Fake.ZOMBIE),
	/** Near a place you know, still at the fog edge, watching. */
	CLOSE("close", Tier.MAJOR, Stage.PROXIMITY, Spot.KNOWN, Pose.STAND, Facing.PLAYER, true, Ending.STARE_LEAVE, Gait.RUN, Fake.NONE),
	/** Ending A only: he walks away and doesn't run. */
	LAST_ONE("last_one", Tier.MAJOR, Stage.TELLING, Spot.OPEN, Pose.STAND, Facing.AWAY, false, Ending.NONE, Gait.SLOW, Fake.NONE),
	/** The End (D-034): perfectly still among the endermen at the edge of what you can see, white eyes among purple ones. */
	AMONG_ENDERMEN("among_endermen", Tier.MINOR, Stage.TRACES, Spot.ENDERMEN, Pose.STAND, Facing.PLAYER, false, Ending.STARE_LEAVE, Gait.WALK, Fake.NONE),
	/** The Nether (D-034): still among the zombified piglins at the edge of the Nether fog. */
	AMONG_PIGLINS("among_piglins", Tier.MINOR, Stage.TRACES, Spot.PIGLINS, Pose.STAND, Facing.PLAYER, false, Ending.STARE_LEAVE, Gait.WALK, Fake.NONE),
	/**
	 * The glimpse (D-073): at the top of the tunnel ladder (dig's third rung, D-072), past the last torch, where the caller
	 * puts him ({@link GlimpseFigure}). Not a director card of its own; never rushes, never goes under, no fake, silent,
	 * eyes FLAT. The one figure that vanishes in view: gone before anyone looks straight at him.
	 */
	GLIMPSE("glimpse", Tier.MINOR, Stage.TRACES, Spot.GIVEN, Pose.STAND, Facing.PLAYER, false, Ending.VANISH, Gait.WALK, Fake.NONE);

	/**
	 * Where the spot finder looks. ENDERMEN and PIGLINS: beside existing mobs of that kind, never spawning one. GIVEN:
	 * the caller gives the spot (the glimpse), the spot finder never looks.
	 */
	public enum Spot { OPEN, RIDGE, TRUNK, LIGHT, SHORE, KNOWN, ENDERMEN, PIGLINS, GIVEN }

	public enum Pose { STAND, LOW }

	/** Which way he faces while idle. */
	public enum Facing { PLAYER, AWAY, ANCHOR }

	/**
	 * What he does when stared at or approached. {@link #NONE}: he keeps doing what he was doing. {@link #VANISH}: he is
	 * simply gone, in view or not (the glimpse, D-073, the one exception to "never vanishes in view").
	 */
	public enum Ending { NONE, STARE_LEAVE, RISE_STARE_LEAVE, HIDE, VANISH }

	public enum Gait { SLOW, WALK, RUN }

	/** The false positive: an existing mob of this kind, held still at the fog edge. */
	public enum Fake { NONE, COW, ZOMBIE }

	private final String name;
	private final Tier tier;
	private final Stage earliestStage;
	private final Spot spot;
	private final Pose pose;
	private final Facing facing;
	private final boolean tracksPlayer;
	private final Ending ending;
	private final Gait gait;
	private final Fake fake;

	Variant(String name, Tier tier, Stage earliestStage, Spot spot, Pose pose, Facing facing, boolean tracksPlayer, Ending ending, Gait gait, Fake fake) {
		this.name = name;
		this.tier = tier;
		this.earliestStage = earliestStage;
		this.spot = spot;
		this.pose = pose;
		this.facing = facing;
		this.tracksPlayer = tracksPlayer;
		this.ending = ending;
		this.gait = gait;
		this.fake = fake;
	}

	/** Short name, as used by {@code /a1016 entity spawn <variant>}. */
	public String shortName() {
		return name;
	}

	/** The event card id, {@code sighting_<name>}. */
	public String cardId() {
		return "sighting_" + name;
	}

	public Tier tier() {
		return tier;
	}

	public Stage earliestStage() {
		return earliestStage;
	}

	public Spot spot() {
		return spot;
	}

	public Pose pose() {
		return pose;
	}

	public Facing facing() {
		return facing;
	}

	/** True if his head follows the nearest player while he stands. */
	public boolean tracksPlayer() {
		return tracksPlayer;
	}

	public Ending ending() {
		return ending;
	}

	public Gait gait() {
		return gait;
	}

	public Fake fake() {
		return fake;
	}

	/** The one dimension this variant runs in (D-034): the End among endermen, the Nether among piglins, else the overworld. */
	public ResourceKey<Level> dimension() {
		return switch (spot) {
			case ENDERMEN -> Level.END;
			case PIGLINS -> Level.NETHER;
			default -> Level.OVERWORLD;
		};
	}

	/**
	 * True if the sighting may end with him going under (D-030) instead of walking or running off: the overworld
	 * variants that end with a stare back. Never the last one, never one that walks off from the start or hides, never
	 * in the End (the void) or the Nether (netherrack, and no shrine imagery).
	 */
	public boolean mayGoUnder() {
		return dimension() == Level.OVERWORLD && (ending == Ending.STARE_LEAVE || ending == Ending.RISE_STARE_LEAVE);
	}

	/** True if a chaser he cannot outrun may make him rush past them (D-037). Never Ending A's last one, never the glimpse. */
	public boolean mayRush() {
		return this != LAST_ONE && ending != Ending.VANISH;
	}

	/** True if the director has a card for this variant ({@code sighting_<name>}): every one but the glimpse. */
	public boolean isCard() {
		return spot != Spot.GIVEN;
	}

	/**
	 * True if he vanishes in view (D-073): the client stops drawing him the moment he is looked toward and the server
	 * removes him by his own rules, seen or not. Only the glimpse.
	 */
	public boolean vanishesInView() {
		return ending == Ending.VANISH;
	}

	/** The eye style this variant always has, whatever the config says, or empty for the configured one. The glimpse: FLAT. */
	public Optional<EyeStyle> eyeStyle() {
		return this == GLIMPSE ? Optional.of(EyeStyle.FLAT) : Optional.empty();
	}

	/**
	 * True if the variant may come in daytime fog (D-071: a day spell, a morning carry-over): the cow, the one walking
	 * away, between the trunks, across the water and close. The ridge keeps its dusk sky and the light its night; the
	 * other dimensions have no daytime.
	 */
	public boolean inDaytimeFog() {
		return switch (this) {
			case COW, WALKS_AWAY, BETWEEN_TRUNKS, ACROSS_WATER, CLOSE -> true;
			default -> false;
		};
	}

	/** Every variant the director has a card for ({@link #isCard}), in order. */
	public static List<Variant> cards() {
		return Arrays.stream(values()).filter(Variant::isCard).toList();
	}

	/** True if he stands with his back to you and walks off, slowly, once you have seen him. */
	public boolean walksFromStart() {
		return facing == Facing.AWAY;
	}

	/** By short name or card id. */
	public static Optional<Variant> byName(String name) {
		String key = name.toLowerCase(Locale.ROOT);
		for (Variant variant : values()) {
			if (variant.name.equals(key) || variant.cardId().equals(key)) {
				return Optional.of(variant);
			}
		}
		return Optional.empty();
	}
}
