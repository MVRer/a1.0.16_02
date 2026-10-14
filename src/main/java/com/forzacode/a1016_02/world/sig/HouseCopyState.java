package com.forzacode.a1016_02.world.sig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.core.CoreCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import org.jspecify.annotations.Nullable;

/**
 * The copy of one player's first shelter ("Your house, elsewhere", D-005), as saved: whose house it is, the shell
 * captured from the real house (positions relative to {@link #realOrigin}), the inside it encloses, where the copy
 * stands and every block moved so far. Immutable; {@link HouseCopier} replaces it as the copy grows. A copy saved
 * before multiplayer has no {@code owner}: it was the first known player's ({@link HouseCopier#owner}).
 *
 * @param realOrigin the min corner of the captured shelter; a target's real spot is {@code realOrigin + rel}
 * @param targets    the shell to rebuild, floor first and the roof last
 * @param interior   the inside of the real house (relative), the air a player stands in
 * @param copyOrigin where {@link #realOrigin} maps at the copy, once the site is picked
 * @param siteId     the HOUSE_COPY site, or -1
 * @param moved      every block moved into the copy, oldest first (Ending D's undo uses the ledger, this is the index)
 * @param nextStep   {@code GameClock.dayTicks} of the next step
 * @param owner      whose house it is: the {@value HouseCopier#OWNER_KEY} owner when it was captured (D-061)
 */
public record HouseCopyState(ResourceKey<Level> dimension, BlockPos realOrigin, List<Target> targets, List<BlockPos> interior,
		Optional<BlockPos> copyOrigin, int siteId, List<Moved> moved, long nextStep, Phase phase, long startedDay, Optional<UUID> owner) {
	/** Where the copy is in its life. */
	public enum Phase {
		/** Captured; the copy site is being looked for. */
		SITE,
		/** Blocks move out of the real house a few at a time. */
		BUILDING,
		/** Ending B asked for it to be finished; it completes as soon as it is out of view. */
		FINISHING,
		/** Nothing is left to move. */
		FINISHED
	}

	/**
	 * One shell block of the first shelter.
	 *
	 * @param rel  position relative to the shelter's min corner
	 * @param roof part of the roof (the real one is never taken)
	 */
	public record Target(BlockPos rel, BlockState state, boolean roof) {
		static final Codec<Target> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("rel").forGetter(Target::rel),
				BlockState.CODEC.fieldOf("state").forGetter(Target::state),
				Codec.BOOL.optionalFieldOf("roof", false).forGetter(Target::roof)
		).apply(i, Target::new));
	}

	/**
	 * One block moved into the copy.
	 *
	 * @param local taken from the ground near the copy (Ending B's finish), not from the real house
	 */
	public record Moved(BlockPos from, BlockPos to, BlockState state, boolean local) {
		static final Codec<Moved> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("from").forGetter(Moved::from),
				BlockPos.CODEC.fieldOf("to").forGetter(Moved::to),
				BlockState.CODEC.fieldOf("state").forGetter(Moved::state),
				Codec.BOOL.optionalFieldOf("local", false).forGetter(Moved::local)
		).apply(i, Moved::new));
	}

	public static final Codec<HouseCopyState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(HouseCopyState::dimension),
			BlockPos.CODEC.fieldOf("realOrigin").forGetter(HouseCopyState::realOrigin),
			Target.CODEC.listOf().fieldOf("targets").forGetter(HouseCopyState::targets),
			BlockPos.CODEC.listOf().optionalFieldOf("interior", List.of()).forGetter(HouseCopyState::interior),
			BlockPos.CODEC.optionalFieldOf("copyOrigin").forGetter(HouseCopyState::copyOrigin),
			Codec.INT.optionalFieldOf("siteId", -1).forGetter(HouseCopyState::siteId),
			Moved.CODEC.listOf().optionalFieldOf("moved", List.of()).forGetter(HouseCopyState::moved),
			Codec.LONG.optionalFieldOf("nextStep", 0L).forGetter(HouseCopyState::nextStep),
			CoreCodecs.enumCodec(Phase.class).fieldOf("phase").forGetter(HouseCopyState::phase),
			Codec.LONG.optionalFieldOf("startedDay", 0L).forGetter(HouseCopyState::startedDay),
			UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(HouseCopyState::owner)
	).apply(i, HouseCopyState::new));

	public HouseCopyState {
		targets = List.copyOf(targets);
		interior = List.copyOf(interior);
		moved = List.copyOf(moved);
	}

	/** A shelter just captured, nobody's (tests): no site yet. */
	public static HouseCopyState captured(ResourceKey<Level> dimension, HouseShell.Capture capture, long today, long dayTicks) {
		return captured(dimension, capture, today, dayTicks, null);
	}

	/** {@code owner}'s shelter just captured: no site yet. */
	public static HouseCopyState captured(ResourceKey<Level> dimension, HouseShell.Capture capture, long today, long dayTicks, @Nullable UUID owner) {
		return new HouseCopyState(dimension, capture.origin(), capture.targets(), capture.interior(), Optional.empty(), -1, List.of(), dayTicks,
				Phase.SITE, today, Optional.ofNullable(owner));
	}

	public BlockPos realPos(Target target) {
		return realOrigin.offset(target.rel());
	}

	/** The target's spot at the copy; only once the site is picked. */
	public BlockPos copyPos(Target target) {
		return copyOrigin.orElseThrow().offset(target.rel());
	}

	/** The copy spot that mirrors a real position, if the site is picked. */
	public Optional<BlockPos> mirror(BlockPos real) {
		return copyOrigin.map(origin -> origin.offset(real.subtract(realOrigin)));
	}

	/** The captured shelter's box (targets and interior) in real coordinates. */
	public BoundingBox realBox() {
		return box(realOrigin);
	}

	/** The copy's box, once the site is picked. */
	public BoundingBox copyBox() {
		return box(copyOrigin.orElseThrow());
	}

	/**
	 * Where a player stands inside the house, relative to its origin: the lowest cell of the inside nearest the middle
	 * (the box's middle with no inside). The HOUSE_COPY site is recorded there at the copy; {@link #realInside} is the
	 * same cell in the real house.
	 */
	public BlockPos insideRel() {
		BlockPos middle = box(BlockPos.ZERO).getCenter();
		Comparator<BlockPos> lowestMiddle = Comparator.comparingInt(BlockPos::getY);
		lowestMiddle = lowestMiddle.thenComparingDouble(p -> p.distSqr(new BlockPos(middle.getX(), p.getY(), middle.getZ())));
		return interior.stream().min(lowestMiddle).orElse(middle);
	}

	/** {@link #insideRel} in the real house: where the house the copy copies is ("your house" for the ending). */
	public BlockPos realInside() {
		return realOrigin.offset(insideRel());
	}

	private BoundingBox box(BlockPos origin) {
		BoundingBox box = null;
		for (Target target : targets) {
			BoundingBox one = new BoundingBox(origin.offset(target.rel()));
			box = box == null ? one : BoundingBox.encapsulating(box, one);
		}
		for (BlockPos rel : interior) {
			BoundingBox one = new BoundingBox(origin.offset(rel));
			box = box == null ? one : BoundingBox.encapsulating(box, one);
		}
		return box == null ? new BoundingBox(origin) : box;
	}

	/** The site is picked: blocks start moving (or the finish goes on, if Ending B already asked for it). */
	public HouseCopyState withSite(BlockPos origin, int site, long next) {
		return new HouseCopyState(dimension, realOrigin, targets, interior, Optional.of(origin), site, moved, next,
				phase == Phase.FINISHING ? Phase.FINISHING : Phase.BUILDING, startedDay, owner);
	}

	public HouseCopyState withMoved(Moved one) {
		List<Moved> more = new ArrayList<>(moved);
		more.add(one);
		return new HouseCopyState(dimension, realOrigin, targets, interior, copyOrigin, siteId, more, nextStep, phase, startedDay, owner);
	}

	public HouseCopyState withNextStep(long next) {
		return new HouseCopyState(dimension, realOrigin, targets, interior, copyOrigin, siteId, moved, next, phase, startedDay, owner);
	}

	public HouseCopyState withPhase(Phase next) {
		return new HouseCopyState(dimension, realOrigin, targets, interior, copyOrigin, siteId, moved, nextStep, next, startedDay, owner);
	}

	/** How many moved blocks came out of the real house (not from the ground near the copy). */
	public int movedFromHouse() {
		return (int) moved.stream().filter(m -> !m.local()).count();
	}
}
