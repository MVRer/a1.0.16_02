package com.forzacode.a1016_02.atmosphere.sc2;

import com.forzacode.a1016_02.atmosphere.sc2.s21.S21;
import com.forzacode.a1016_02.atmosphere.sc2.s22.S22;
import com.forzacode.a1016_02.atmosphere.sc2.s23.S23;
import com.forzacode.a1016_02.atmosphere.sc2.s24.S24;
import com.forzacode.a1016_02.atmosphere.sc2.s29.S29;

/**
 * The self-created layer's second wave (docs/SELF_CREATED_2.md), atmosphere's part: one line per feature, so tasks running
 * at the same time never touch the same file. Called once from atmosphere's init.
 */
public final class AtmosphereSc2 {
	private AtmosphereSc2() {
	}

	public static void init() {
		S21.init();
		S22.init();
		S23.init();
		S24.init();
		S29.init();
	}
}
