package com.forzacode.a1016_02.lore.sc2;

import com.forzacode.a1016_02.lore.sc2.s30.S30;

/**
 * The self-created layer's second wave (docs/SELF_CREATED_2.md), lore's part: one line per feature, so tasks running
 * at the same time never touch the same file. Called once from lore's init.
 */
public final class LoreSc2 {
	private LoreSc2() {
	}

	public static void init() {
		S30.init();
	}
}
