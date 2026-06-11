package com.harcore.duo.feature;

import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BestTime {

	private static final String FILENAME = "speedrun-best-time";
	private static final int MAX_ENTRIES = 3;
	private static final List<Entry> entries = new ArrayList<>();
	private static String sidebarText = "--:--";

	public static void load(MinecraftServer server) {
		var path = server.getServerDirectory().resolve(FILENAME);
		if (!Files.exists(path)) return;
		try {
			entries.clear();
			for (var line : Files.readAllLines(path)) {
				line = line.trim();
				if (line.isEmpty()) continue;
				var parts = line.split(" by ");
				if (parts.length == 2) {
					var timeParts = parts[0].split(":");
					if (timeParts.length == 2) {
						long seconds = Long.parseLong(timeParts[0]) * 60 + Long.parseLong(timeParts[1]);
						var names = List.of(parts[1].split(", "));
						entries.add(new Entry(seconds, parts[0], names));
					}
				}
			}
			entries.sort(Comparator.comparingLong(e -> e.seconds));
			updateSidebar();
		} catch (IOException | NumberFormatException ignored) {}
	}

	public static String get() {
		return sidebarText;
	}

	public static void trySave(MinecraftServer server, long seconds, List<String> names) {
		long mins = seconds / 60;
		long secs = seconds % 60;
		var timeStr = String.format("%02d:%02d", mins, secs);
		entries.add(new Entry(seconds, timeStr, names));
		entries.sort(Comparator.comparingLong(e -> e.seconds));

		while (entries.size() > MAX_ENTRIES) {
			entries.remove(entries.size() - 1);
		}

		updateSidebar();

		var path = server.getServerDirectory().resolve(FILENAME);
		var sb = new StringBuilder();
		for (var e : entries) {
			sb.append(e.time).append(" by ").append(String.join(", ", e.names)).append('\n');
		}
		try {
			Files.writeString(path, sb.toString());
		} catch (IOException ignored) {}
	}

	public static boolean isBest(long seconds) {
		return entries.size() < MAX_ENTRIES || seconds < entries.get(entries.size() - 1).seconds;
	}

	public static String podiumText() {
		var sb = new StringBuilder();
		for (int i = 0; i < entries.size(); i++) {
			var e = entries.get(i);
			sb.append("§6#").append(i + 1).append(" §f").append(e.time).append(" §7by ").append(String.join(", ", e.names)).append('\n');
		}
		return sb.toString();
	}

	public static void updatePlaceholder(MinecraftServer server) {
		SidebarManager.setPlaceholder("best", sidebarText);
		SidebarManager.applyLines(server);
	}

	private static void updateSidebar() {
		if (entries.isEmpty()) {
			sidebarText = "--:--";
		} else {
			sidebarText = entries.get(0).time;
		}
	}

	private static class Entry {
		final long seconds;
		final String time;
		final List<String> names;

		Entry(long seconds, String time, List<String> names) {
			this.seconds = seconds;
			this.time = time;
			this.names = names;
		}
	}
}
