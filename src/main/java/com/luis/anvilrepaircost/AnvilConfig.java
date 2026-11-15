package com.luis.anvilrepaircost;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Porcentaje de la penalización por trabajos previos que se cobra (100 = vanilla). Se guarda en config/anvilrepaircost.json. */
public final class AnvilConfig {
	public static final int DEFAULT_PERCENT = 50;
	private static final Gson GSON = new Gson();
	private static volatile int percent = DEFAULT_PERCENT;

	private AnvilConfig() {}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("anvilrepaircost.json");
	}

	public static int percent() {
		return percent;
	}

	public static void load() {
		try {
			JsonObject json = GSON.fromJson(Files.readString(file()), JsonObject.class);
			int value = json.get("percent").getAsInt();
			percent = value >= 0 && value <= 100 ? value : DEFAULT_PERCENT;
		} catch (IOException | RuntimeException e) {
			// Falta, está corrupto o fuera de rango: valor por defecto.
			percent = DEFAULT_PERCENT;
		}
	}

	public static void set(int value) throws IOException {
		if (value < 0 || value > 100) {
			throw new IllegalArgumentException("percent fuera de 0-100: " + value);
		}
		JsonObject json = new JsonObject();
		json.addProperty("percent", value);
		Files.writeString(file(), GSON.toJson(json));
		percent = value;
	}
}
