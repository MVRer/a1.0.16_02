package com.forzacode.a1016_02.lore;

import java.util.ArrayList;
import java.util.List;

import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;

/**
 * Sign text changes "he" makes: blanking a sign, or showing a fragment's own lines on it ("Stop."). They only ever
 * go through {@code TraceService.editSign} (out of view, vetoed, ledgered with the old text). Until core's contract
 * batch (P1-9) lands, {@link #CORE_EDIT_SIGN} is false and nothing is edited: the cards that need it skip.
 */
final class SignEdits {
	/** Flip to true once {@code TraceService.editSign} is on main (and wire {@link #edit} to it). */
	static final boolean CORE_EDIT_SIGN = false;

	/** One sign text change, both sides at once: an empty list blanks that side; at most 4 lines. */
	@FunctionalInterface
	interface Editor {
		boolean edit(ServerLevel level, BlockPos pos, List<String> front, List<String> back, String cause);
	}

	private SignEdits() {
	}

	/** True when he can change sign text at all. */
	static boolean available() {
		return CORE_EDIT_SIGN;
	}

	/** The editor backed by core's trace service ({@code forced()} in tests and debug). */
	static Editor editor(TraceService traces) {
		return (level, pos, front, back, cause) -> edit(traces, level, pos, front, back, cause);
	}

	private static boolean edit(TraceService traces, ServerLevel level, BlockPos pos, List<String> front, List<String> back, String cause) {
		if (!CORE_EDIT_SIGN || !(level.getBlockEntity(pos) instanceof SignBlockEntity)) {
			return false;
		}
		// Wired to TraceService.editSign once core lands. Never set sign text here.
		return false;
	}

	/** Plain lines as sign components. */
	static List<Component> components(List<String> lines) {
		List<Component> out = new ArrayList<>(lines.size());
		lines.forEach(line -> out.add(Component.literal(line)));
		return out;
	}

	/** Both sides of a sign as one text: the non-empty lines, front first, joined with " / ". */
	static String text(SignBlockEntity sign) {
		List<String> lines = new ArrayList<>();
		for (SignTextSlot slot : SignTextSlot.values()) {
			for (Component line : sign.getText(slot).getMessages(false)) {
				String text = line.getString();
				if (!text.isBlank()) {
					lines.add(text.strip());
				}
			}
		}
		return String.join(" / ", lines);
	}

	/** True if every line on both sides is empty. */
	static boolean isBlank(SignBlockEntity sign) {
		return text(sign).isEmpty();
	}

	/** The front's four lines as plain strings. */
	static List<String> front(SignBlockEntity sign) {
		return sign.getText(SignTextSlot.FRONT).getMessages(false).stream().map(Component::getString).toList();
	}

	/** The back's four lines as plain strings. */
	static List<String> back(SignBlockEntity sign) {
		return sign.getText(SignTextSlot.BACK).getMessages(false).stream().map(Component::getString).toList();
	}
}
