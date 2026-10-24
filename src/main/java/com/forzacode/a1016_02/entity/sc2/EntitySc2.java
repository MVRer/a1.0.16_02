package com.forzacode.a1016_02.entity.sc2;

import com.forzacode.a1016_02.entity.sc2.s27.S27;
import com.forzacode.a1016_02.entity.sc2.s31.S31;

/**
 * The self-created layer's second wave (docs/SELF_CREATED_2.md), entity's part: one line per feature, so tasks running
 * at the same time never touch the same file. Called once from entity's init.
 */
public final class EntitySc2 {
	private EntitySc2() {
	}

	public static void init() {
		S27.init();
		S31.init();
	}
}
