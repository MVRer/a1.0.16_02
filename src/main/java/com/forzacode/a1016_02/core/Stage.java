package com.forzacode.a1016_02.core;

/** The five presence stages. Stored in {@link HerobrineState}; change with {@link HerobrineState#setStage}. */
public enum Stage {
	ALONE(0),
	TRACES(1),
	PROXIMITY(2),
	TELLING(3),
	REMOVAL(4);

	private final int level;

	Stage(int level) {
		this.level = level;
	}

	/** 0 to 4, as used by {@code /a1016 stage <n>}. */
	public int level() {
		return level;
	}

	public boolean atLeast(Stage other) {
		return level >= other.level;
	}

	public static Stage byLevel(int level) {
		for (Stage stage : values()) {
			if (stage.level == level) {
				return stage;
			}
		}
		throw new IllegalArgumentException("No stage " + level);
	}
}
