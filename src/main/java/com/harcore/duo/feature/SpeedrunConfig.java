package com.harcore.duo.feature;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;

public class SpeedrunConfig {

	private static final String CONFIG_FILENAME = "speedrun_settings.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static Settings settings = new Settings();

	public static void load(MinecraftServer server) {
		var path = server.getServerDirectory().resolve(CONFIG_FILENAME);
		if (!Files.exists(path)) {
			return;
		}
		try (var reader = Files.newBufferedReader(path)) {
			settings = GSON.fromJson(reader, Settings.class);
		} catch (IOException ignored) {
		}
	}

	public static int getCountdown() {
		return settings.countdown;
	}

	private static class Settings {
		int countdown = 5;
	}
}
