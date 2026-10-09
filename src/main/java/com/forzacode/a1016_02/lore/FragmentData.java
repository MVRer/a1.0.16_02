package com.forzacode.a1016_02.lore;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.forzacode.a1016_02.A1016_02;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * The loaded fragments, from {@code data/a1016_02/lore/fragments/*.json}. Reloaded with {@code /reload}; always
 * read through {@link #get} so new text is used from then on (fragments already in the world keep theirs).
 */
public final class FragmentData extends SimpleJsonResourceReloadListener<Fragment> {
	public static final String FOLDER = "lore/fragments";
	private static volatile Map<String, Fragment> fragments = Map.of();

	private FragmentData() {
		super(Fragment.CODEC, FileToIdConverter.json(FOLDER));
	}

	static void register() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(A1016_02.id(FOLDER), new FragmentData());
	}

	@Override
	protected void apply(Map<Identifier, Fragment> loaded, ResourceManager manager, ProfilerFiller profiler) {
		Map<String, Fragment> byId = new TreeMap<>();
		loaded.forEach((file, fragment) -> {
			if (!file.getNamespace().equals(A1016_02.MOD_ID)) {
				return;
			}
			if (!file.getPath().equals(fragment.id())) {
				A1016_02.LOGGER.warn("[a1016] lore: fragment file {} holds id {}", file, fragment.id());
			}
			for (String problem : BookLayout.problems(fragment)) {
				A1016_02.LOGGER.warn("[a1016] lore: {} {}", fragment.id(), problem);
			}
			byId.put(fragment.id(), fragment);
		});
		fragments = Collections.unmodifiableMap(byId);
		A1016_02.LOGGER.info("[a1016] lore: loaded {} fragments", byId.size());
	}

	/** The fragment with this id, if its data file loaded. */
	public static Optional<Fragment> get(String id) {
		return Optional.ofNullable(fragments.get(id));
	}

	/** Every loaded fragment, ordered by id. */
	public static Collection<Fragment> all() {
		return fragments.values();
	}

	public static List<String> ids() {
		return List.copyOf(fragments.keySet());
	}
}
