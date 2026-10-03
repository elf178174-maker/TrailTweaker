package dev.katte.trailspawnercooldown.mixin;

import dev.katte.trailspawnercooldown.TrialSpawnerCooldownConfig;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TrialSpawner.class)
public class TrialSpawnerMixin {
	@Inject(method = "getTargetCooldownLength", at = @At("HEAD"), cancellable = true)
	private void trailspawnercooldown$useConfiguredCooldown(CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(TrialSpawnerCooldownConfig.cooldownTicks());
	}
}
