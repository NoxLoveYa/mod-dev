package com.harcore.duo.mixin;

import com.harcore.duo.feature.ReadyManager;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class FreezePlayerMixin {

	@Shadow
	ServerPlayer player;

	@Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
	private void onMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
		if (ReadyManager.isFrozen(player)) {
			ci.cancel();
		}
	}
}
