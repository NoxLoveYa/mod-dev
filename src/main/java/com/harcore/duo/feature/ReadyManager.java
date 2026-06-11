package com.harcore.duo.feature;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ReadyManager {

	private static final Set<UUID> readyPlayers = new HashSet<>();
	private static final Map<UUID, double[]> freezePositions = new HashMap<>();
	private static boolean gameStarted = false;
	private static boolean countingDown = false;
	private static long countdownTick = 0;
	private static int lastBroadcastedSecond = -1;
	private static long startTime = 0;

	public static void register() {
		SidebarManager.setPlaceholder("time", "00:00");

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("ready").executes(ctx -> {
				var player = ctx.getSource().getPlayerOrException();
				if (gameStarted) {
					ctx.getSource().sendFailure(Component.literal("Game already started!"));
					return 0;
				}
				if (countingDown) {
					ctx.getSource().sendFailure(Component.literal("Countdown in progress!"));
					return 0;
				}
				if (readyPlayers.contains(player.getUUID())) {
					ctx.getSource().sendFailure(Component.literal("You are already ready!"));
					return 0;
				}
				readyPlayers.add(player.getUUID());
				var server = ctx.getSource().getServer();
				var total = server.getPlayerList().getPlayerCount();
				server.getPlayerList().broadcastSystemMessage(
						Component.literal("§a" + player.getScoreboardName() + " is ready! §7(" + readyPlayers.size() + "/" + total + ")"),
						false
				);
				return 1;
			}));
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			var id = handler.getPlayer().getUUID();
			readyPlayers.remove(id);
			freezePositions.remove(id);
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			var players = server.getPlayerList().getPlayers();
			if (players.isEmpty()) {
				return;
			}

			if (!gameStarted) {
				for (var player : players) {
					if (!freezePositions.containsKey(player.getUUID())) {
						freezePositions.put(player.getUUID(), new double[]{
								player.getX(), player.getY(), player.getZ(),
								player.getYRot(), player.getXRot()
						});
					}
					var pos = freezePositions.get(player.getUUID());
					player.connection.teleport(pos[0], pos[1], pos[2], (float) pos[3], (float) pos[4]);
				}

				if (!countingDown) {
					boolean allReady = true;
					for (var player : players) {
						if (!readyPlayers.contains(player.getUUID())) {
							allReady = false;
							break;
						}
					}
					if (allReady) {
						countingDown = true;
						countdownTick = server.getTickCount();
						lastBroadcastedSecond = -1;
						server.getPlayerList().broadcastSystemMessage(
								Component.literal("§eAll players ready! Starting in §c" + SpeedrunConfig.getCountdown() + "§e..."), false
						);
					}
				}

				if (countingDown) {
					int elapsed = (int) (server.getTickCount() - countdownTick) / 20;
					int remaining = SpeedrunConfig.getCountdown() - elapsed;

					if (remaining != lastBroadcastedSecond && remaining > 0) {
						lastBroadcastedSecond = remaining;
						server.getPlayerList().broadcastSystemMessage(
								Component.literal("§c" + remaining + "§e..."), false
						);
					}

					if (remaining <= 0) {
						gameStarted = true;
						countingDown = false;
						startTime = System.currentTimeMillis();
						server.getPlayerList().broadcastSystemMessage(
								Component.literal("§a§lGO!"), false
						);
					} else {
						SidebarManager.setPlaceholder("time", "§e" + remaining + "§e...");
						SidebarManager.applyLines(server);
					}
				}
			}

			if (gameStarted) {
				long elapsed = (System.currentTimeMillis() - startTime) / 1000;
				long mins = elapsed / 60;
				long secs = elapsed % 60;
				SidebarManager.setPlaceholder("time", String.format("%02d:%02d", mins, secs));
				SidebarManager.applyLines(server);
			}
		});
	}

	public static boolean isFrozen(ServerPlayer player) {
		return !gameStarted && !readyPlayers.contains(player.getUUID());
	}
}
