package com.harcore.duo;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.scores.PlayerTeam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HardcoreDuo implements ModInitializer {
	public static final String MOD_ID = "hardcore-duo";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			var player = handler.getPlayer();
			var scoreboard = server.getScoreboard();
			var team = scoreboard.getPlayerTeam(MOD_ID);
			if (team == null) {
				team = scoreboard.addPlayerTeam(MOD_ID);
				team.setColor(ChatFormatting.AQUA);
			}
			scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
			player.addEffect(new MobEffectInstance(MobEffects.GLOWING, -1, 0, false, false));
		});

		LOGGER.info("Hardcore Duo initialized!");
	}
}