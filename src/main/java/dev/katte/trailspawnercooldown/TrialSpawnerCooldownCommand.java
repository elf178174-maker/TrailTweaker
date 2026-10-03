package dev.katte.trailspawnercooldown;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class TrialSpawnerCooldownCommand {
	private TrialSpawnerCooldownCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("trailspawner")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("reload").executes(TrialSpawnerCooldownCommand::reload))
				.then(Commands.literal("status").executes(TrialSpawnerCooldownCommand::status))
				.executes(TrialSpawnerCooldownCommand::status));
	}

	private static int reload(CommandContext<CommandSourceStack> context) {
		String result = TrialSpawnerCooldownConfig.load();
		context.getSource().sendSuccess(() -> Component.literal("[Trail Spawner Cooldown] " + result), true);
		return TrialSpawnerCooldownConfig.cooldownSeconds();
	}

	private static int status(CommandContext<CommandSourceStack> context) {
		int seconds = TrialSpawnerCooldownConfig.cooldownSeconds();
		int ticks = TrialSpawnerCooldownConfig.cooldownTicks();
		boolean vaultReusable = TrialSpawnerCooldownConfig.vaultReusable();
		context.getSource().sendSuccess(() -> Component.literal("[Trail Spawner Cooldown] Trial Spawner cooldown is "
				+ seconds + " seconds (" + ticks + " ticks). Trial Vaults can be opened more than once: "
				+ (vaultReusable ? "yes" : "no")
				+ ". Edit config/trailspawnercooldown.json and run /trailspawner reload to change it."), false);
		return seconds;
	}
}
