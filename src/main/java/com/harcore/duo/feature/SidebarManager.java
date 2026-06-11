package com.harcore.duo.feature;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SidebarManager {

	private static final String OBJECTIVE_NAME = "hardcore-duo.sidebar";
	private static final String CONFIG_FILENAME = "speedrun_scoreboard_objectif.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final List<ScoreHolder> currentHolders = new ArrayList<>();
	private static final Map<String, String> placeholders = new HashMap<>();
	private static List<String> rawLines = List.of();
	private static String titleText = "";

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			SpeedrunConfig.load(server);
			reload(server);
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) {
				setPlaceholder("players", String.valueOf(server.getPlayerList().getPlayerCount()));
				applyLines(server);
			}
		});
	}

	public static void setPlaceholder(String key, String value) {
		placeholders.put(key, value);
	}

	public static void reload(MinecraftServer server) {
		var config = loadConfig(server);
		if (config == null) {
			return;
		}
		rawLines = config.lines;
		titleText = config.title;
		setPlaceholder("players", String.valueOf(server.getPlayerList().getPlayerCount()));
		setupObjective(server);
	}

	private static Config loadConfig(MinecraftServer server) {
		var path = server.getServerDirectory().resolve(CONFIG_FILENAME);
		if (!Files.exists(path)) {
			return null;
		}
		try (var reader = Files.newBufferedReader(path)) {
			return GSON.fromJson(reader, Config.class);
		} catch (IOException e) {
			return null;
		}
	}

	private static void setupObjective(MinecraftServer server) {
		var scoreboard = server.getScoreboard();
		var old = scoreboard.getObjective(OBJECTIVE_NAME);
		if (old != null) {
			scoreboard.removeObjective(old);
		}
		var objective = scoreboard.addObjective(
				OBJECTIVE_NAME,
				ObjectiveCriteria.DUMMY,
				GradientText.of(titleText),
				ObjectiveCriteria.DUMMY.getDefaultRenderType(),
				false,
				null
		);
		objective.setNumberFormat(BlankFormat.INSTANCE);
		scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, objective);

		currentHolders.clear();
		var resolved = resolvePlaceholders(rawLines);
		int score = resolved.size();
		for (var line : resolved) {
			var holder = ScoreHolder.forNameOnly(line);
			scoreboard.getOrCreatePlayerScore(holder, objective).set(score--);
			currentHolders.add(holder);
		}
	}

	public static void applyLines(MinecraftServer server) {
		var scoreboard = server.getScoreboard();
		var old = scoreboard.getObjective(OBJECTIVE_NAME);
		if (old == null) {
			return;
		}
		scoreboard.removeObjective(old);

		var objective = scoreboard.addObjective(
				OBJECTIVE_NAME,
				ObjectiveCriteria.DUMMY,
				GradientText.of(titleText),
				ObjectiveCriteria.DUMMY.getDefaultRenderType(),
				false,
				null
		);
		objective.setNumberFormat(BlankFormat.INSTANCE);
		scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, objective);

		currentHolders.clear();
		var resolved = resolvePlaceholders(rawLines);
		int score = resolved.size();
		for (var line : resolved) {
			var holder = ScoreHolder.forNameOnly(line);
			scoreboard.getOrCreatePlayerScore(holder, objective).set(score--);
			currentHolders.add(holder);
		}
	}

	private static List<String> resolvePlaceholders(List<String> lines) {
		return lines.stream()
				.map(line -> {
					var result = line;
					for (var entry : placeholders.entrySet()) {
						result = result.replace("{" + entry.getKey() + "}", entry.getValue());
					}
					return result;
				})
				.toList();
	}

	private static class Config {
		String title = "";
		List<String> lines = List.of();
	}
}
