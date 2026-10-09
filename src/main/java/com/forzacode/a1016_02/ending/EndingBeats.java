package com.forzacode.a1016_02.ending;

import java.util.Locale;

/** The beats of Endings A, B and C, stored as their ordinal in {@link EndingState#progress}. */
public final class EndingBeats {
	private EndingBeats() {
	}

	/** Ending A, "Stop.". */
	public enum A {
		/** He is seen once, far off, walking away into the fog. */
		SIGHTING,
		/** Five to seven in-game days of real quiet. */
		QUIET,
		/** One ordinary accident in a place they trusted. */
		ACCIDENT,
		/** On that marked death: the "Stop." sign goes in front of the cross. */
		SIGN,
		DONE
	}

	/** Ending B, "Removed". */
	public enum B {
		/** Accidents closer together and closer to home. */
		ESCALATE,
		/** The real house is emptied out of view; the shell stays. */
		EMPTY_HOUSE,
		/** The copy elsewhere gets finished. */
		FINISH_COPY,
		/** Mobs wait in the doorway. */
		DOORWAY,
		/** The final death, inside the copy. */
		FINAL,
		/** F10 gains its last line; F20 lies under it. */
		RECORD,
		DONE
	}

	/** Ending C, "For the record". */
	public enum C {
		/** The world is quiet and stays quiet, until he is named. */
		SILENT
	}

	static <E extends Enum<E>> E of(Class<E> type, int ordinal) {
		E[] all = type.getEnumConstants();
		return all[Math.clamp(ordinal, 0, all.length - 1)];
	}

	/** The beat's name for a path, for status lines. */
	public static String name(EndingPath path, int beat) {
		String name = switch (path) {
			case A -> of(A.class, beat).name();
			case B -> of(B.class, beat).name();
			case C -> of(C.class, beat).name();
			case D -> "step " + beat;
			case NONE -> "-";
		};
		return name.toLowerCase(Locale.ROOT);
	}
}
