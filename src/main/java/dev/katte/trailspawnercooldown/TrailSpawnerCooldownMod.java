package dev.katte.trailspawnercooldown;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrailSpawnerCooldownMod implements ModInitializer {
	public static final String MOD_ID = "trailspawnercooldown";
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		TrialSpawnerCooldownConfig.load();
		LOGGER.info("Trail Spawner Cooldown ready: Trial Spawners will use a {} second ({} tick) cooldown.",
				TrialSpawnerCooldownConfig.cooldownSeconds(), TrialSpawnerCooldownConfig.cooldownTicks());
	}
}
