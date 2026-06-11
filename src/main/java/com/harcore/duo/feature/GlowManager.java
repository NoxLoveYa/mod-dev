package com.harcore.duo.feature;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.scores.PlayerTeam;

public class GlowManager {

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			var player = handler.getPlayer();
			var team = getOrCreateTeam(server);
			server.getScoreboard().addPlayerToTeam(player.getScoreboardName(), team);
			player.addEffect(new MobEffectInstance(MobEffects.GLOWING, -1, 0, false, false));
		});
	}

	private static PlayerTeam getOrCreateTeam(MinecraftServer server) {
		var scoreboard = server.getScoreboard();
		var team = scoreboard.getPlayerTeam("hardcore-duo");
		if (team == null) {
			team = scoreboard.addPlayerTeam("hardcore-duo");
			team.setColor(ChatFormatting.AQUA);
		}
		return team;
	}
}
