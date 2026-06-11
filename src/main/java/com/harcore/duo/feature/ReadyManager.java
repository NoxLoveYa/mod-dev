package com.harcore.duo.feature;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ReadyManager {

	private static final Set<UUID> readyPlayers = new HashSet<>();
	private static final Set<UUID> resetVotes = new HashSet<>();
	private static final Map<UUID, double[]> freezePositions = new HashMap<>();
	private static boolean gameStarted = false;
	private static boolean gameFinished = false;
	private static long finalTime = 0;
	private static boolean countingDown = false;
	private static long countdownTick = 0;
	private static int lastBroadcastedSecond = -1;
	private static int lastTitleTick = 0;
	private static long startTime = 0;

	public static void register() {
		SidebarManager.setPlaceholder("time", "00:00");
		SidebarManager.setPlaceholder("best", "--:--");

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
				server.getPlayerList().broadcastSystemMessage(
						Component.literal("§a" + player.getScoreboardName() + " is ready! §7(" + readyPlayers.size() + "/" + SpeedrunConfig.getMinPlayers() + ")"),
						false
				);
				return 1;
			}));

			dispatcher.register(Commands.literal("tp-end")
					.executes(ctx -> {
						var player = ctx.getSource().getPlayerOrException();
						if (!ctx.getSource().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
							ctx.getSource().sendFailure(Component.literal("OP level 2 required"));
							return 0;
						}
						var endLevel = ctx.getSource().getServer().getLevel(Level.END);
						if (endLevel == null) {
							ctx.getSource().sendFailure(Component.literal("End dimension not found"));
							return 0;
						}
						player.teleportTo(endLevel, 100, 50, 0, java.util.Collections.emptySet(), 0, 0, false);
						player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
						ctx.getSource().sendSuccess(() -> Component.literal("Teleported to the End in creative"), true);
						return 1;
					}));

			dispatcher.register(Commands.literal("kill-dragon")
					.executes(ctx -> {
						if (!ctx.getSource().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
							ctx.getSource().sendFailure(Component.literal("OP level 2 required"));
							return 0;
						}
						var server = ctx.getSource().getServer();
						var endLevel = server.getLevel(Level.END);
						if (endLevel == null) {
							ctx.getSource().sendFailure(Component.literal("End dimension not found"));
							return 0;
						}
						server.getCommands().performPrefixedCommand(
								ctx.getSource(), "kill @e[type=minecraft:ender_dragon]");
						ctx.getSource().sendSuccess(() -> Component.literal("Dragon killed"), true);
						return 1;
					}));

			dispatcher.register(Commands.literal("reset")
					.executes(ctx -> {
						var server = ctx.getSource().getServer();
						var player = ctx.getSource().getPlayer();
						var isOp = ctx.getSource().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);

						if (isOp) {
							resetGame(server);
							return 1;
						}

						if (player == null) {
							ctx.getSource().sendFailure(Component.literal("Only players can vote for reset"));
							return 0;
						}

						resetVotes.add(player.getUUID());
						var total = server.getPlayerList().getPlayerCount();
						var votes = resetVotes.size();

						if (votes >= total) {
							resetGame(server);
							return 1;
						}

						server.getPlayerList().broadcastSystemMessage(
								Component.literal("§eReset vote: §f" + votes + " §7/ §f" + total + " §7(/reset to vote)"),
								false
						);
						return 1;
					}));
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			var id = handler.getPlayer().getUUID();
			readyPlayers.remove(id);
			resetVotes.remove(id);
			freezePositions.remove(id);
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof EnderDragon && gameStarted && !gameFinished) {
				var serverLevel = (ServerLevel) entity.level();
				var server = serverLevel.getServer();
				server.getPlayerList().broadcastSystemMessage(
						Component.literal("§a§lDragon killed! Enter the portal to finish."), false
				);
			}
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

				if (!countingDown && server.getTickCount() - lastTitleTick >= 20) {
					lastTitleTick = (int) server.getTickCount();
					int total = players.size();
					int ready = (int) players.stream().filter(p -> readyPlayers.contains(p.getUUID())).count();
					int min = SpeedrunConfig.getMinPlayers();
					var title = Component.literal("§eWaiting for players");
					var sub = Component.literal("§f" + ready + " §7/ §f" + min + "   §7type §f/ready");
					for (var player : players) {
						player.connection.send(new ClientboundSetTitleTextPacket(title));
						player.connection.send(new ClientboundSetSubtitleTextPacket(sub));
						player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 40, 10));
					}
				}

				if (!countingDown) {
					boolean allReady = !players.isEmpty();
					for (var player : players) {
						if (!readyPlayers.contains(player.getUUID())) {
							allReady = false;
							break;
						}
					}
					int readyCount = readyPlayers.size();
					int totalOnline = players.size();
					if (allReady && readyCount >= SpeedrunConfig.getMinPlayers()) {
						countingDown = true;
						countdownTick = server.getTickCount();
						lastBroadcastedSecond = -1;
						server.getPlayerList().broadcastSystemMessage(
								Component.literal("§eAll players ready! Starting in §c" + SpeedrunConfig.getCountdown() + "§e..."), false
						);
						for (var player : players) {
							player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
							player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
							player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 0, 0));
						}
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

			if (gameStarted && !gameFinished) {
				for (var player : players) {
					if (player.level().dimension() == Level.END) {
						var bb = player.getBoundingBox().inflate(0.5);
						var minX = (int) Math.floor(bb.minX);
						var maxX = (int) Math.floor(bb.maxX);
						var minY = (int) Math.floor(bb.minY);
						var maxY = (int) Math.floor(bb.maxY);
						var minZ = (int) Math.floor(bb.minZ);
						var maxZ = (int) Math.floor(bb.maxZ);
						var found = false;
						for (int x = minX; x <= maxX && !found; x++) {
							for (int y = minY; y <= maxY && !found; y++) {
								for (int z = minZ; z <= maxZ && !found; z++) {
									var block = player.level().getBlockState(new net.minecraft.core.BlockPos(x, y, z));
									if (block.getBlock() instanceof net.minecraft.world.level.block.EndPortalBlock) {
										found = true;
									}
								}
							}
						}
						if (found) {
							gameFinished = true;
							finalTime = (System.currentTimeMillis() - startTime) / 1000;
							long mins = finalTime / 60;
							long secs = finalTime % 60;
							var time = String.format("%02d:%02d", mins, secs);
							var allNames = players.stream().map(ServerPlayer::getScoreboardName).toList();
							var madeLeaderboard = BestTime.isBest(finalTime);
							BestTime.trySave(server, finalTime, allNames);
							var msg = Component.literal("§a§lRun complete! Time: §e" + time);
							if (madeLeaderboard) {
								msg = Component.literal("§a§lTOP 3! Time: §e" + time + "\n" + BestTime.podiumText());
							}
							server.getPlayerList().broadcastSystemMessage(msg, false);
							SidebarManager.setPlaceholder("time", "§a" + time);
							BestTime.updatePlaceholder(server);
							SidebarManager.applyLines(server);
							break;
						}
					}
				}

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

	public static void resetGame(MinecraftServer server) {
		gameStarted = false;
		gameFinished = false;
		countingDown = false;
		finalTime = 0;
		startTime = 0;
		readyPlayers.clear();
		resetVotes.clear();
		freezePositions.clear();
		lastTitleTick = 0;
		lastBroadcastedSecond = -1;

		SidebarManager.setPlaceholder("time", "00:00");
		SidebarManager.applyLines(server);
		WorldReset.restore(server.overworld());
	}
}

