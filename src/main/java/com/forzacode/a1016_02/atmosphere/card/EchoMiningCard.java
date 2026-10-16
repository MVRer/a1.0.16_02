package com.forzacode.a1016_02.atmosphere.card;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.Gates;
import com.forzacode.a1016_02.atmosphere.WorldScan;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.PlayerAction;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Your mining, again (D-076; AMBIENT, Alone, SOUND, fake): one of the player's own mining bursts from 5 to 30 minutes
 * of their play ago ({@code echoMiningMin/MaxAgeMinutes}) plays again with the same hits and breaks, the same gaps and
 * each block's own sounds, from inside solid rock 14 to 24 blocks from them, below or beside them
 * ({@link WorldScan#rockSpot}, far in the rock's finder: a rock block buried on all six sides, so nobody can see the
 * spot, and the view rule is checked on it too). Only sound; nothing changes. Anyone in range hears it (D-060). Never
 * within 30 s of the player's combat. A burst is echoed when every block in it is natural rock or earth dug with a
 * pickaxe or a shovel ({@link #mined}): mining, not chopping or a torch knocked off, and never ice, glass, metal,
 * lanterns or amethyst, whose sounds nothing inside the rock could make.
 *
 * <p>The hits (vanilla 26.3 sends a dig's hit sound every 4 ticks while a block is dug, the first as it starts, then the
 * break): a block's hits are the 4-tick beats of its dig time ({@link #hits}). The dig time is the block's hardness
 * model, vanilla's {@code 30 * hardness / toolSpeed} ticks with the right tool ({@link #digTicks}): for every break but
 * the first, the player's own gap before it less vanilla's 5-tick delay after a break and the click that starts the next
 * ({@link #LEAD}), never longer than the block takes with a wooden tool ({@code echoMiningSlowToolSpeed}), so a pause
 * between two blocks is quiet, not more hits; the first break, with no gap before it, takes the tool speed the others
 * imply ({@code echoMiningToolSpeed}, iron, if they imply none). Stone dug with an iron pickaxe is 8 ticks, 2 hits;
 * deepslate 15, 4; dirt with a shovel 3, 1. An instant break has no hit, as in vanilla (a creative break or a block the
 * tool breaks at once goes straight to the break, without the dig's first hit): a gap under {@link #LEAD} plus
 * {@link #MIN_DIG} (creative's 6 ticks, an instamined run's 1 to 3), and the first break too when every other one was
 * instant or the implied speed breaks it at once.
 *
 * <p>The fake: a burst of the same size (2 to 6 with none) in a rock none of the burst's blocks sounds like, at a steady
 * rhythm well off the burst's own ({@link #fakeBreaks}).
 */
public final class EchoMiningCard extends EchoCard {
	public static final String ID = "echo_mining";
	/** Vanilla digs with a hit sound every this many ticks (level event 2020), the first as the block starts. */
	static final int HIT_EVERY = 4;
	/** After a break vanilla waits 5 ticks before the next block starts (its destroy delay); the click that starts it is one more. */
	static final int LEAD = 6;
	/** Vanilla's dig ticks are {@code DIG_FACTOR * hardness / toolSpeed} with the right tool. */
	static final double DIG_FACTOR = 30.0;
	/**
	 * The shortest dig that is not an instant break: vanilla adds a block's progress (under 1) from the tick after the
	 * click, so a dig takes at least 2 ticks; anything shorter (a gap of at most {@code LEAD + 1}, one tick of jitter
	 * on creative's 6) was a break at once, with no hit.
	 */
	static final int MIN_DIG = 2;
	/**
	 * Natural rock and earth (vanilla's own tags, so a modded block counts only when its mod puts it in them): the base
	 * stones of the overworld (stone, granite, diorite, andesite, tuff, deepslate) and of the Nether, every ore, the
	 * overworld's ground (dirt, coarse and rooted dirt, grass, podzol, mycelium, mud) and sand.
	 */
	private static final List<TagKey<Block>> NATURAL_TAGS = List.of(BlockTags.BASE_STONE_OVERWORLD, BlockTags.STONE_ORE_REPLACEABLES,
			BlockTags.DEEPSLATE_ORE_REPLACEABLES, BlockTags.BASE_STONE_NETHER, BlockTags.ORES, BlockTags.SUBSTRATE_OVERWORLD, BlockTags.SAND);
	/** Natural rock and earth no tag of {@link #NATURAL_TAGS} holds: gravel, clay, calcite, dripstone, sandstone and the dungeons' cobblestone. */
	private static final Set<Block> NATURAL_BLOCKS = Set.of(Blocks.GRAVEL, Blocks.SUSPICIOUS_GRAVEL, Blocks.CLAY, Blocks.CALCITE, Blocks.DRIPSTONE_BLOCK,
			Blocks.SANDSTONE, Blocks.RED_SANDSTONE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE);
	/** The fake's shortest period: its gaps (within 2 of the period) are never shorter than {@code LEAD + MIN_DIG}, a dig. */
	static final int FAKE_MIN_PERIOD = LEAD + MIN_DIG + 2;
	/** The fake's rocks: the spot's own rock if it sounds like none of the burst's blocks, else the first of these that does not. */
	private static final List<Block> FAKE_ROCKS = List.of(Blocks.DEEPSLATE, Blocks.STONE, Blocks.TUFF, Blocks.GRAVEL, Blocks.CALCITE, Blocks.DRIPSTONE_BLOCK);

	/** Whether there is rock around the player for the sound to come from (cheap). A game test swaps in its own, as {@link #sounds}. */
	BiPredicate<ServerLevel, BlockPos> rockAround = EchoMiningCard::hasRockAround;

	public EchoMiningCard() {
		super(ID, Tier.AMBIENT, Stage.ALONE, Set.of(), Set.of(CardTag.SOUND), true);
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return fits(player, !bursts(player, world, false).isEmpty()) && rockAround.test(world, player.blockPosition());
	}

	@Override
	public FireResult fire(FireContext ctx) {
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		RandomSource random = ctx.random();
		List<PlayerAction.Mining> bursts = bursts(player, level, ctx.forced());
		if (bursts.isEmpty() && !ctx.fake()) {
			return FireResult.SKIPPED;
		}
		PlayerAction.Mining burst = bursts.isEmpty() ? null : bursts.get(random.nextInt(bursts.size()));
		AtmosphereConfig cfg = cfg();
		BlockPos spot = WorldScan.rockSpot(level, player.blockPosition(), cfg.echoMiningMinDistance, cfg.echoMiningMaxDistance, cfg.echoMiningMaxAbove,
				cfg.echoMiningMaxBelow, random);
		if (spot == null || !outOfView.test(level, new AABB(spot))) {
			return FireResult.NO_SPOT;
		}
		List<PlayerAction.Break> breaks = ctx.fake() || burst == null ? fakeBreaks(burst, level.getBlockState(spot), spot, random, cfg) : burst.breaks();
		episodes.accept(new Replay(watch.get(), player.getUUID(), level, Vec3.atCenterOf(spot), replay(breaks, cfg), sounds));
		return FireResult.FIRED;
	}

	/**
	 * The player's bursts this card can echo, newest first: in the player's level now, every block mined
	 * ({@link #mined}), the last break {@code echoMiningMinAgeMinutes} to {@code echoMiningMaxAgeMinutes} of play ago. A
	 * forced fire (debug) with none that old takes their newest burst instead.
	 */
	List<PlayerAction.Mining> bursts(ServerPlayer player, ServerLevel level, boolean forced) {
		List<PlayerAction.Mining> mined = new ArrayList<>();
		for (PlayerAction.Mining burst : watch.get().recentBursts(player)) {
			if (burst.dimension().equals(level.dimension()) && burst.breaks().stream().allMatch(b -> mined(b.state()))) {
				mined.add(burst);
			}
		}
		AtmosphereConfig cfg = cfg();
		long now = now(level);
		long min = minutes(cfg.echoMiningMinAgeMinutes);
		long max = minutes(cfg.echoMiningMaxAgeMinutes);
		List<PlayerAction.Mining> due = mined.stream().filter(burst -> Gates.within(now - burst.tick(), min, max)).toList();
		if (due.isEmpty() && forced && !mined.isEmpty()) {
			return List.of(mined.getFirst());
		}
		return due;
	}

	/**
	 * A block that is mined, what an echo replays: natural rock or earth ({@link #natural}) dug with a pickaxe or a
	 * shovel, never in no time at all (hardness above 0). Ice, packed and blue ice (their break is glass), a lantern, an
	 * iron block or amethyst are pickaxe blocks but not rock: from inside the rock they would be sounds nothing there
	 * makes.
	 */
	static boolean mined(BlockState state) {
		return (state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_SHOVEL)) && hardness(state) > 0.0 && natural(state);
	}

	/** Natural rock or earth: in one of {@link #NATURAL_TAGS} or one of {@link #NATURAL_BLOCKS}. */
	static boolean natural(BlockState state) {
		if (NATURAL_BLOCKS.contains(state.getBlock())) {
			return true;
		}
		for (TagKey<Block> tag : NATURAL_TAGS) {
			if (state.is(tag)) {
				return true;
			}
		}
		return false;
	}

	static double hardness(BlockState state) {
		return state.getBlock().defaultDestroyTime();
	}

	/**
	 * The replay of {@code breaks} (oldest first): each block's hits on vanilla's 4-tick beat over its dig time
	 * ({@link #digTicks}), then its break, at the recorded ticks; tick 0 is the first sound. Each block's own sounds
	 * ({@code SoundType}: the hit at half its pitch, the break at 0.8, as vanilla plays them) at the configured volumes.
	 */
	static List<Beat> replay(List<PlayerAction.Break> breaks, AtmosphereConfig cfg) {
		int[] dig = digTicks(breaks, cfg);
		long origin = breaks.getFirst().tick() - dig[0];
		float hitVolume = (float) cfg.echoMiningHitVolume;
		float breakVolume = (float) cfg.echoMiningBreakVolume;
		List<Beat> beats = new ArrayList<>();
		for (int i = 0; i < breaks.size(); i++) {
			PlayerAction.Break each = breaks.get(i);
			SoundType type = each.state().getSoundType();
			long start = each.tick() - dig[i] - origin;
			for (int k = 0; k < hits(dig[i]); k++) {
				beats.add(new Beat((int) (start + (long) k * HIT_EVERY), type.getHitSound(), hitVolume, type.getPitch() * 0.5F));
			}
			beats.add(new Beat((int) (each.tick() - origin), type.getBreakSound(), breakVolume, type.getPitch() * 0.8F));
		}
		return inOrder(beats);
	}

	/** Hit sounds in a dig of {@code digTicks}: one as it starts and every 4 ticks after, before the break. */
	static int hits(int digTicks) {
		return digTicks <= 0 ? 0 : (digTicks + HIT_EVERY - 1) / HIT_EVERY;
	}

	/**
	 * Each break's dig time in ticks (0 for a block that breaks at once: no hit, as vanilla plays none for an instant
	 * break). Every break but the first: its gap from the one before less {@link #LEAD}, at most the block's time with a
	 * slow tool ({@code echoMiningSlowToolSpeed}); under {@link #MIN_DIG} it was an instant break (creative, or
	 * instamined). The first: its hardness at the tool speed the others' digs imply (their average), or
	 * {@code echoMiningToolSpeed} if no other break says anything, within the same bounds; instant if every other break
	 * was instant (a creative or instamined run) or if that speed breaks it in under {@link #MIN_DIG}.
	 */
	static int[] digTicks(List<PlayerAction.Break> breaks, AtmosphereConfig cfg) {
		int[] dig = new int[breaks.size()];
		double speeds = 0.0;
		int implied = 0;
		int instant = 0;
		for (int i = 1; i < breaks.size(); i++) {
			double hardness = hardness(breaks.get(i).state());
			if (hardness <= 0.0) {
				continue;
			}
			long gap = breaks.get(i).tick() - breaks.get(i - 1).tick();
			long ticks = Math.min(gap - LEAD, slowest(hardness, cfg));
			if (ticks < MIN_DIG) {
				instant++;
				continue;
			}
			dig[i] = (int) ticks;
			speeds += DIG_FACTOR * hardness / dig[i];
			implied++;
		}
		double first = hardness(breaks.getFirst().state());
		if (first > 0.0 && (implied > 0 || instant == 0)) {
			double speed = implied > 0 ? speeds / implied : Math.max(0.1, cfg.echoMiningToolSpeed);
			long ticks = Math.min((long) Math.ceil(DIG_FACTOR * first / speed), slowest(first, cfg));
			dig[0] = ticks < MIN_DIG ? 0 : (int) ticks;
		}
		return dig;
	}

	/** The block's dig time with the slow tool: the longest a dig of it is replayed. */
	private static long slowest(double hardness, AtmosphereConfig cfg) {
		return Math.max(1L, (long) Math.ceil(DIG_FACTOR * hardness / Math.max(0.1, cfg.echoMiningSlowToolSpeed)));
	}

	/**
	 * The fake: as many breaks as {@code burst} (2 to 6 if there is none), of a rock that sounds like none of its blocks
	 * ({@link #fakeBlock}), at {@code spot}, at a steady rhythm: a period from {@code echoMiningFakeGapMin/MaxTicks} at
	 * least {@code max(6, 30%)} off the burst's mean gap, each gap within 2 ticks of it. Ticks from 0. The period is at
	 * least {@link #FAKE_MIN_PERIOD}, so every gap is a dig and the same rock never breaks at once one time and with hits
	 * the next.
	 */
	static List<PlayerAction.Break> fakeBreaks(PlayerAction.@Nullable Mining burst, BlockState spotState, BlockPos spot, RandomSource random,
			AtmosphereConfig cfg) {
		int count = burst == null
				? PlayerAction.Mining.MIN_BURST + random.nextInt(PlayerAction.Mining.MAX_BREAKS - PlayerAction.Mining.MIN_BURST + 1)
				: burst.breaks().size();
		Set<SoundType> heard = new HashSet<>();
		double mean = Double.NaN;
		if (burst != null) {
			burst.breaks().forEach(b -> heard.add(b.state().getSoundType()));
			mean = burst.gaps().stream().mapToLong(Long::longValue).average().orElse(Double.NaN);
		}
		BlockState block = fakeBlock(spotState, heard);
		int lo = Math.max(FAKE_MIN_PERIOD, cfg.echoMiningFakeGapMinTicks);
		int hi = (int) Math.min(PlayerAction.Mining.GAP_TICKS - 3, Math.max(lo, cfg.echoMiningFakeGapMaxTicks));
		lo = Math.min(lo, hi);
		int period = steadyPeriod(mean, lo, hi, random);
		List<PlayerAction.Break> breaks = new ArrayList<>(count);
		long tick = 0;
		for (int i = 0; i < count; i++) {
			if (i > 0) {
				tick += Math.max(1, Math.min(PlayerAction.Mining.GAP_TICKS - 1, period + random.nextInt(5) - 2));
			}
			breaks.add(new PlayerAction.Break(spot, block, tick));
		}
		return breaks;
	}

	/** A period in {@code [lo, hi]} at least {@code max(6, 0.3 * mean)} ticks off {@code mean} (any, if there is no mean); the far end if none is. */
	static int steadyPeriod(double mean, int lo, int hi, RandomSource random) {
		if (Double.isNaN(mean)) {
			return lo + random.nextInt(hi - lo + 1);
		}
		double apart = Math.max(6.0, 0.3 * mean);
		for (int tries = 0; tries < 16; tries++) {
			int period = lo + random.nextInt(hi - lo + 1);
			if (Math.abs(period - mean) >= apart) {
				return period;
			}
		}
		return Math.abs(lo - mean) >= Math.abs(hi - mean) ? lo : hi;
	}

	/** The spot's own rock if it sounds like none of {@code heard}, else the first of {@link #FAKE_ROCKS} that does not. */
	static BlockState fakeBlock(BlockState spotState, Set<SoundType> heard) {
		if (WorldScan.isRock(spotState) && !heard.contains(spotState.getSoundType())) {
			return spotState;
		}
		for (Block rock : FAKE_ROCKS) {
			BlockState state = rock.defaultBlockState();
			if (!heard.contains(state.getSoundType())) {
				return state;
			}
		}
		return Blocks.GRAVEL.defaultBlockState();
	}

	/**
	 * Rock the echo could come from, cheaply (loaded blocks only, {@link WorldScan#isRock}): the column under the player
	 * from {@code echoMiningMinDistance} down, every other block, or one of the four sides at the band's middle, a little
	 * below the feet. False in a level without overworld rock (the Nether, the End), so the card never holds its tier
	 * there for a spot it cannot find.
	 */
	static boolean hasRockAround(ServerLevel level, BlockPos feet) {
		AtmosphereConfig cfg = cfg();
		int deepest = Math.min(cfg.echoMiningMaxDistance, cfg.echoMiningMaxBelow);
		for (int down = Math.max(1, cfg.echoMiningMinDistance); down <= deepest; down += 2) {
			if (rock(level, feet.below(down))) {
				return true;
			}
		}
		int across = (cfg.echoMiningMinDistance + cfg.echoMiningMaxDistance) / 2;
		int below = Math.min(4, Math.max(0, cfg.echoMiningMaxBelow));
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (rock(level, feet.relative(side, across).below(below))) {
				return true;
			}
		}
		return false;
	}

	private static boolean rock(ServerLevel level, BlockPos pos) {
		return level.isLoaded(pos) && WorldScan.isRock(level.getBlockState(pos));
	}
}
