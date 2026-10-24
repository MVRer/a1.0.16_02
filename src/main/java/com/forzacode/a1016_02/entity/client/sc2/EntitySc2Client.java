package com.forzacode.a1016_02.entity.client.sc2;

import com.forzacode.a1016_02.entity.client.sc2.s27.S27Client;

/**
 * The self-created layer's second wave (docs/SELF_CREATED_2.md), entity's client part: one line per feature, so tasks
 * running at the same time never touch the same file. Called once from entity's client init.
 */
public final class EntitySc2Client {
	private EntitySc2Client() {
	}

	public static void init() {
		S27Client.init();
	}
}
