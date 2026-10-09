package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * The {@code /a1016} command (op level 2). Workstreams add their subtree in {@code init()}:
 * <pre>{@code
 * CommandHooks.register((root, ctx) -> root.then(Commands.literal("lore").then(...)));
 * }</pre>
 */
public final class CommandHooks {
	public static final String ROOT = "a1016";

	@FunctionalInterface
	public interface Hook {
		void register(LiteralArgumentBuilder<CommandSourceStack> root, CommandBuildContext context);
	}

	private static final List<Hook> HOOKS = new ArrayList<>();

	private CommandHooks() {
	}

	public static synchronized void register(Hook hook) {
		HOOKS.add(hook);
	}

	static void install() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
			LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(ROOT)
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
			synchronized (CommandHooks.class) {
				for (Hook hook : HOOKS) {
					hook.register(root, context);
				}
			}
			dispatcher.register(root);
		});
	}
}
