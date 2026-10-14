package com.forzacode.a1016_02.ending.d;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
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
import net.minecraft.server.MinecraftServer;
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
 * Steps 5 to 7, in the bedrock chamber: exactly six torches, or six and one more for each friend whose line was finished
 * ({@link Chain#torchesCount}; a wrong count does nothing); the sentence finished under F30's twin ("he is no longer
 * with us", the words F08 carries, or "hes no longer with us", either one after the twin's "i did, but"; any case,
 * spacing and punctuation, D-066 M1; his name or "sorry" there never counts and is naming him, and the next block
 * someone stands on above bedrock is gone); his cross on bedrock between the torches, built (by anyone) with the first
 * block taken out of the cairn anywhere in its post, the post standing on bedrock, topped with wood from the untouched
 * grove in any shape (planks, log, wood, stripped; M5).
 */
public final class Chamber {
	public static final String NAMED_CAUSE = "ending:d/named";
	/** The words of the sentence come from this fragment's lines. */
	public static final String SENTENCE_FRAGMENT = "F08";
	/** The twin's words, "i did, but", which the sentence may follow (M1). */
	public static final String TWIN_FRAGMENT = "F30";
	private static final String SORRY = "sorry";

	private Chamber() {
	}

	/** The chamber's inside, from the bottom of the world to just under its ceiling. */
	static BoundingBox box(ServerLevel level, StairPlan plan) {
		return plan.chamberBox();
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

	/**
	 * Every way step 6 takes the sentence (M1), in {@link #letters} form: F08's "he is no longer with us", the same
	 * with "hes", and either one after the twin's "i did, but" (the whole canon line). Empty if lore's data is not
	 * loaded. "heisnolongerwithus", "hesnolongerwithus", "ididbutheisnolongerwithus", "ididbuthesnolongerwithus".
	 */
	public static List<String> sentences() {
		Optional<String> said = sentence();
		if (said.isEmpty() || said.get().isEmpty()) {
			return List.of();
		}
		String full = said.get();
		String hes = full.startsWith("heis") ? "hes" + full.substring("heis".length()) : full;
		String twin = FragmentData.get(TWIN_FRAGMENT).map(f -> letters(String.join(" ", f.linesFor("")))).orElse("");
		List<String> all = new ArrayList<>();
		for (String one : List.of(full, hes, twin + full, twin + hes)) {
			if (!all.contains(one)) {
				all.add(one);
			}
		}
		return List.copyOf(all);
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
		/** One side says one of the sentences, letters only; a sign that names him or says sorry never does. */
		boolean says(Collection<String> sentences) {
			return !names() && (sentences.contains(letters(front)) || sentences.contains(letters(back)));
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

	/** Step 6: the finished sentence under the twin, if it is there ({@code sentences}: {@link #sentences}). */
	public static Optional<Written> finished(List<Written> signs, StairPlan plan, EndingDConfig cfg, Collection<String> sentences) {
		if (sentences.isEmpty()) {
			return Optional.empty();
		}
		return signs.stream().filter(w -> underTwin(w.pos(), plan, cfg) && w.says(sentences)).findFirst();
	}

	/**
	 * Signs in the chamber that name him (or say sorry), each counted once, whoever wrote it: it arms the fall (the next
	 * block anyone down there stands on above bedrock goes) and counts as naming him.
	 */
	static void watchNaming(MinecraftServer server, EndingDState data, List<Written> signs) {
		for (Written sign : signs) {
			String key = "named@" + sign.pos().getX() + "," + sign.pos().getY() + "," + sign.pos().getZ() + "#" + letters(sign.front() + sign.back()).hashCode();
			if (!sign.names() || data.has(key)) {
				continue;
			}
			data.set(key, true);
			data.set(EndingDState.NAMED, true);
			if (!NameMatcher.namesHim(sign.front() + " " + sign.back())) {
				// "sorry" names him too; his name itself is already lore's telling.
				Attention.trigger(server, AttentionTrigger.NAMED_HIM);
			}
			A1016_02.LOGGER.info("[a1016] ending d: named in the chamber at {}", sign.pos().toShortString());
		}
	}

	/**
	 * Naming's danger: the next block this player (whoever of the group stands first) stands on above bedrock is gone,
	 * the moment it is out of view of everyone (in practice while they look up, D-027). One clue: the missing block
	 * under where they stood.
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

	/** Wood from the grove's poplars in any shape (M5): planks, a log, wood, a stripped log or stripped wood. */
	public static boolean groveWoodShape(BlockState state) {
		return state.is(Blocks.POPLAR_PLANKS) || state.is(Blocks.POPLAR_LOG) || state.is(Blocks.POPLAR_WOOD) || state.is(Blocks.STRIPPED_POPLAR_LOG)
				|| state.is(Blocks.STRIPPED_POPLAR_WOOD);
	}

	/**
	 * Step 7: his cross, if it stands, whoever built it. In the chamber, a post of at least three blocks whose lowest
	 * block stands on bedrock; the first block (the {@code story:first_blocks} owner's, placed from the item anyone took
	 * out of the cairn) is anywhere in the post (M5); arms to both sides at some level between its foot and its top; and
	 * its top is wood from the untouched grove, tracked as grove wood, in any shape (M5). Returns the post's foot.
	 */
	public static Optional<BlockPos> cross(ServerLevel level, StairPlan plan, EndingDState data) {
		BoundingBox box = box(level, plan);
		for (EndingDState.Tracked tracked : data.tracked()) {
			GlobalPos at = tracked.pos();
			if (!tracked.mark().equals(Marks.FIRST_BLOCK) || !at.dimension().equals(level.dimension()) || !box.isInside(at.pos())) {
				continue;
			}
			BlockPos first = at.pos();
			if (!part(level.getBlockState(first))) {
				continue;
			}
			// The post through the first block: down to its lowest block (which must stand on bedrock), up to its top.
			BlockPos foot = first;
			while (foot.getY() > box.minY() && !level.getBlockState(foot.below()).is(Blocks.BEDROCK) && part(level.getBlockState(foot.below()))) {
				foot = foot.below();
			}
			if (!level.getBlockState(foot.below()).is(Blocks.BEDROCK)) {
				continue;
			}
			BlockPos top = first;
			while (top.getY() < box.maxY() && top.getY() - foot.getY() < 12 && part(level.getBlockState(top.above()))) {
				top = top.above();
			}
			int height = top.getY() - foot.getY();
			if (height < 2 || top.equals(first)) {
				continue;
			}
			if (!groveWoodShape(level.getBlockState(top))
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
