package dev.katte.trailspawnercooldown.mixin;

import dev.katte.trailspawnercooldown.TrialSpawnerCooldownConfig;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.level.block.entity.vault.VaultServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VaultServerData.class)
public class VaultServerDataMixin {
	@Inject(method = "hasRewardedPlayer", at = @At("HEAD"), cancellable = true)
	private void trailspawnercooldown$allowRepeatOpening(CallbackInfoReturnable<Boolean> cir) {
		if (TrialSpawnerCooldownConfig.vaultReusable()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "getRewardedPlayers", at = @At("HEAD"), cancellable = true)
	private void trailspawnercooldown$keepVaultsConnected(CallbackInfoReturnable<Set<UUID>> cir) {
		if (TrialSpawnerCooldownConfig.vaultReusable()) {
			cir.setReturnValue(Set.of());
		}
	}
}
