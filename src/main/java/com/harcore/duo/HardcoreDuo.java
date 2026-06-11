package com.harcore.duo;

import net.fabricmc.api.ModInitializer;

import com.harcore.duo.feature.GlowManager;
import com.harcore.duo.feature.SidebarManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HardcoreDuo implements ModInitializer {
	public static final String MOD_ID = "hardcore-duo";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		GlowManager.register();
		SidebarManager.register();
		LOGGER.info("Hardcore Speedrun initialized!");
	}
}