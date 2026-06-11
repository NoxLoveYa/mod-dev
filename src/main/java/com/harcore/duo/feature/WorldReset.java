package com.harcore.duo.feature;

import net.minecraft.server.level.ServerLevel;

public class WorldReset {

	public static void restore(ServerLevel overworld) {
		var server = overworld.getServer();

		for (var player : server.getPlayerList().getPlayers()) {
			player.connection.disconnect(net.minecraft.network.chat.Component.literal("§cWorld reset — rejoin after restart!"));
		}

		try { Thread.sleep(500); } catch (InterruptedException ignored) {}

		server.halt(false);
	}
}
