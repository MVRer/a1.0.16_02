package com.forzacode.a1016_02.debug.gallery;

import com.forzacode.a1016_02.core.Gallery;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * The gallery's registration (docs/SELF_CREATED_2.md, SC2-0c), in a development environment only: the demo exhibit
 * {@code 00} and {@code /a1016 sc g}. In a release jar it does nothing. Each structure or behaviour task registers its own
 * exhibits from its own init ({@link Gallery#register}).
 */
public final class GalleryInit {
	private GalleryInit() {
	}

	public static void init() {
		if (!Gallery.active()) {
			return;
		}
		Gallery.register(Exhibit00.exhibit());
		GalleryCommands.register();
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> GalleryCommands.PLACED.clear());
	}
}
