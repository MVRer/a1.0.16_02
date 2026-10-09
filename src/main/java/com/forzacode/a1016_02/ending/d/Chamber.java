package com.forzacode.a1016_02.ending.d;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Attention;
import com.forzacode.a1016_02.core.AttentionTrigger;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.lore.FragmentData;
import com.forzacode.a1016_02.lore.NameMatcher;
import com.forzacode.a1016_02.lore.UnbreakableSigns;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Steps 5 to 7, in the bedrock chamber: exactly six torches (a wrong count does nothing); the sentence finished under
 * F30's twin ("he is no longer with us", the words F08 carries, any case and spacing; his name or "sorry" there counts
 * as naming him, and the next block they stand on above bedrock is gone); his cross on bedrock between the torches,
 * built on their first block and topped with poplar planks from the untouched grove.
 */
public final class Chamber {
	public static final String NAMED_CAUSE = "ending:d/named";
	/** The words of the sentence come from this fragment's lines. */
	public static final String SENTENCE_FRAGMENT = "F08";
	private static final String SORRY = "sorry";

	private Chamber() {
	}

	/** The chamber's inside, from the bottom of the world to just under its ceiling. */
	static BoundingBox box(ServerLevel level, StairPlan plan) {
		return plan.chamberBox(level.getMinY());
	}

	static Iterable<BlockPos> cells(BoundingBox box) {
		return BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
	}

	// --- step 5 ---

	/** Torches of any kind (not redstone) in the chamber, standing or on a wall. */
	public static int torches(ServerLevel level, StairPlan plan) {
		int count = 0;
		for (BlockPos pos : cells(box(level, plan))) {
			if (level.getBlockState(pos).getBlock() instanceof TorchBlock) {
				count++;
			}
		}
		return count;
	}

	// --- step 6 ---

	/** Lower case letters only, accents dropped: "He is no / longer with us." reads "heisnolongerwithus". */
	public static String letters(String text) {
		String decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT);
		StringBuilder out = new StringBuilder(decomposed.length());
		for (int n = 0; n < decomposed.length(); n++) {
			char c = decomposed.charAt(n);
			if (c >= 'a' && c <= 'z') {
				out.append(c);
			}
		}
		return out.toString();
	}

	/** The sentence to finish, in {@link #letters} form, from F08's lines; empty if lore's data is not loaded. */
	public static Optional<String> sentence() {
		return FragmentData.get(SENTENCE_FRAGMENT).map(f -> letters(String.join(" ", f.linesFor(""))));
	}

	/** One side of a sign, its lines joined with spaces. */
	static String side(SignBlockEntity sign, SignTextSlot slot) {
		StringBuilder text = new StringBuilder();
		for (Component line : sign.getText(slot).getMessages(false)) {
			if (!text.isEmpty()) {
				text.append(' ');
			}
			text.append(line.getString());
		}
		return text.toString();
	}

	/** A sign in the chamber, what it says on each side. */
	public record Written(BlockPos pos, String front, String back) {
		boolean says(String sentence) {
			return letters(front).equals(sentence) || letters(back).equals(sentence);
		}

		/** His name, or "sorry": counts as naming him. */
		boolean names() {
			String all = front + " " + back;
			return NameMatcher.namesHim(all) || letters(all).contains(SORRY);
		}
	}

	/** Every sign in the chamber except F30's twin (and any other sign nobody may change). */
	public static List<Written> signs(ServerLevel level, StairPlan plan) {
		List<Written> found = new ArrayList<>();
		for (BlockPos pos : cells(box(level, plan))) {
			if (pos.equals(plan.twin()) || !(level.getBlockState(pos).getBlock() instanceof SignBlock) || UnbreakableSigns.isProtected(level, pos)) {
				continue;
			}
			if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
				found.add(new Written(pos.immutable(), side(sign, SignTextSlot.FRONT), side(sign, SignTextSlot.BACK)));
			}
		}
		return found;
	}

	/** True if the sign stands under (beside, at most {@code sentenceRadius} away from) F30's twin. */
	static boolean underTwin(BlockPos pos, StairPlan plan, EndingDConfig cfg) {
		BlockPos twin = plan.twin();
		return Math.abs(pos.getX() - twin.getX()) <= cfg.sentenceRadius && Math.abs(pos.getZ() - twin.getZ()) <= cfg.sentenceRadius
				&& Math.abs(pos.getY() - twin.getY()) <= 2;
	}

	/** Step 6: the finished sentence under the twin, if it is there. */
	public static Optional<Written> finished(List<Written> signs, StairPlan plan, EndingDConfig cfg, String sentence) {
		return signs.stream().filter(w -> underTwin(w.pos(), plan, cfg) && w.says(sentence)).findFirst();
	}

	/**
	 * Signs in the chamber that name him (or say sorry), each counted once: it arms the fall (the next block they stand
	 * on above bedrock goes) and counts as naming him.
	 */
	static void watchNaming(ServerPlayer player, EndingDState data, List<Written> signs) {
		for (Written sign : signs) {
			String key = "named@" + sign.pos().getX() + "," + sign.pos().getY() + "," + sign.pos().getZ() + "#" + letters(sign.front() + sign.back()).hashCode();
			if (!sign.names() || data.has(key)) {
				continue;
			}
			data.set(key, true);
			data.set(EndingDState.NAMED, true);
			if (!NameMatcher.namesHim(sign.front() + " " + sign.back())) {
				// "sorry" names him too; his name itself is already lore's telling.
				Attention.trigger(player.level().getServer(), AttentionTrigger.NAMED_HIM);
			}
			A1016_02.LOGGER.info("[a1016] ending d: named in the chamber at {}", sign.pos().toShortString());
		}
	}

	/**
	 * Naming's danger: the next block the player stands on above bedrock is gone, the moment it is out of view (in
	 * practice while they look up, D-027). One clue: the missing block under where they stood.
	 */
	public static boolean namedFall(ServerPlayer player, EndingDState data, View view) {
		if (!data.has(EndingDState.NAMED) || !player.onGround()) {
			return false;
		}
		ServerLevel level = player.level();
		BlockPos under = BlockPos.containing(player.getX(), player.getBoundingBox().minY - 0.01, player.getZ());
		BlockState state = level.getBlockState(under);
		if (state.isAir() || state.is(Blocks.BEDROCK) || UnbreakableSigns.isProtected(level, under) || level.getBlockEntity(under) != null) {
			return false;
		}
		if (view.outOfView(level, under) && Services.traces().remove(level, under, NAMED_CAUSE)) {
			data.set(EndingDState.NAMED, false);
			return true;
		}
		return false;
	}

	// --- step 7 ---

	/** A block that is part of a built cross (not air, a plant, a torch or a sign). */
	static boolean part(BlockState state) {
		return !state.isAir() && !state.canBeReplaced() && !(state.getBlock() instanceof BaseTorchBlock) && !(state.getBlock() instanceof SignBlock);
	}

	/**
	 * Step 7: his cross, if it stands. On bedrock in the chamber, its foot is the player's first block (placed from the
	 * item taken out of the cairn), a post goes up from it with arms to both sides, and its top is poplar planks made
	 * from logs cut in the untouched grove.
	 */
	public static Optional<BlockPos> cross(ServerLevel level, StairPlan plan, EndingDState data) {
		BoundingBox box = box(level, plan);
		for (EndingDState.Tracked tracked : data.tracked()) {
			GlobalPos at = tracked.pos();
			if (!tracked.mark().equals(Marks.FIRST_BLOCK) || !at.dimension().equals(level.dimension()) || !box.isInside(at.pos())) {
				continue;
			}
			BlockPos foot = at.pos();
			if (!part(level.getBlockState(foot)) || !level.getBlockState(foot.below()).is(Blocks.BEDROCK)) {
				continue;
			}
			int height = 0;
			while (height < 8 && part(level.getBlockState(foot.above(height + 1)))) {
				height++;
			}
			if (height < 2) {
				continue;
			}
			BlockPos top = foot.above(height);
			if (!level.getBlockState(top).is(Blocks.POPLAR_PLANKS)
					|| !data.trackedAt(GlobalPos.of(level.dimension(), top)).filter(Marks.GROVE_WOOD::equals).isPresent()) {
				continue;
			}
			for (int j = 1; j < height; j++) {
				BlockPos level2 = foot.above(j);
				boolean ew = part(level.getBlockState(level2.relative(Direction.EAST))) && part(level.getBlockState(level2.relative(Direction.WEST)));
				boolean ns = part(level.getBlockState(level2.relative(Direction.NORTH))) && part(level.getBlockState(level2.relative(Direction.SOUTH)));
				if (ew || ns) {
					return Optional.of(foot);
				}
			}
		}
		return Optional.empty();
	}
}
