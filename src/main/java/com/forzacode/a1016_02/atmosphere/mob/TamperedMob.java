package com.forzacode.a1016_02.atmosphere.mob;

import org.jspecify.annotations.Nullable;

/** Added to every {@code Mob} by the atmosphere mob mixin: the in-memory {@link TamperState}, never saved. */
public interface TamperedMob {
	@Nullable TamperState a1016_02$tamper();

	void a1016_02$setTamper(@Nullable TamperState state);
}
