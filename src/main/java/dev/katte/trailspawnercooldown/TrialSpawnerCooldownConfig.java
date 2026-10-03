package dev.katte.trailspawnercooldown;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TrialSpawnerCooldownConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("trailspawnercooldown");

	public static final int DEFAULT_COOLDOWN_SECONDS = 1800;
	public static final boolean DEFAULT_VAULT_REUSABLE = true;
	public static final int TICKS_PER_SECOND = 20;
	private static final int MAX_COOLDOWN_SECONDS = 100_000_000;

	private static final String KEY_COOLDOWN = "cooldown_seconds";
	private static final String KEY_VAULT_REUSABLE = "vault_reusable";

	private static volatile int cooldownSeconds = DEFAULT_COOLDOWN_SECONDS;
	private static volatile int cooldownTicks = DEFAULT_COOLDOWN_SECONDS * TICKS_PER_SECOND;
	private static volatile boolean vaultReusable = DEFAULT_VAULT_REUSABLE;

	private TrialSpawnerCooldownConfig() {
	}

	public static int cooldownSeconds() {
		return cooldownSeconds;
	}

	public static int cooldownTicks() {
		return cooldownTicks;
	}

	public static boolean vaultReusable() {
		return vaultReusable;
	}

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("trailspawnercooldown.json");
	}

	/** Loads the config file, creating it with defaults if missing. Returns a human-readable summary. */
	public static String load() {
		return loadFrom(configPath());
	}

	static String loadFrom(Path path) {
		try {
			if (Files.notExists(path)) {
				Files.createDirectories(path.getParent());
				cooldownSeconds = DEFAULT_COOLDOWN_SECONDS;
				cooldownTicks = DEFAULT_COOLDOWN_SECONDS * TICKS_PER_SECOND;
				vaultReusable = DEFAULT_VAULT_REUSABLE;
				write(path);
				LOGGER.info("Created {} with default settings.", path);
				return "Created a new config file. " + summary();
			}

			String raw = Files.readString(path, StandardCharsets.UTF_8);
			JsonElement parsed = JsonParser.parseString(raw);
			if (!parsed.isJsonObject()) {
				LOGGER.warn("{} does not contain a JSON object; keeping the current settings.", path);
				return "Config file is not a JSON object. " + summary();
			}

			JsonObject json = parsed.getAsJsonObject();
			List<String> notes = new ArrayList<>();
			readCooldown(json, path, notes);
			readVaultReusable(json, path, notes);

			if (!json.has(KEY_COOLDOWN) || !json.has(KEY_VAULT_REUSABLE)) {
				try {
					write(path);
					notes.add("added missing settings to the config file");
				} catch (IOException e) {
					LOGGER.warn("Could not update {} with the missing settings.", path, e);
				}
			}

			LOGGER.info("Settings loaded: {} second ({} tick) Trial Spawner cooldown, vault_reusable={}.",
					cooldownSeconds, cooldownTicks, vaultReusable);
			return summary() + (notes.isEmpty() ? "" : " (" + String.join("; ", notes) + ")");
		} catch (IOException e) {
			LOGGER.error("Could not read {}; keeping the current settings.", path, e);
			return "Could not read the config file. " + summary();
		} catch (RuntimeException e) {
			LOGGER.error("Could not parse {}; keeping the current settings.", path, e);
			return "Could not parse the config file (invalid JSON?). " + summary();
		}
	}

	private static void readCooldown(JsonObject json, Path path, List<String> notes) {
		if (!json.has(KEY_COOLDOWN) || !json.get(KEY_COOLDOWN).isJsonPrimitive()) {
			LOGGER.warn("{} is missing \"{}\"; keeping {} seconds.", path, KEY_COOLDOWN, cooldownSeconds);
			notes.add("\"cooldown_seconds\" missing, kept " + cooldownSeconds + "s");
			return;
		}

		int value;
		try {
			value = json.get(KEY_COOLDOWN).getAsInt();
		} catch (NumberFormatException | UnsupportedOperationException e) {
			LOGGER.warn("\"{}\" in {} is not a whole number; keeping {} seconds.", KEY_COOLDOWN, path, cooldownSeconds);
			notes.add("\"cooldown_seconds\" must be a whole number, kept " + cooldownSeconds + "s");
			return;
		}

		if (value < 0) {
			LOGGER.warn("\"{}\" is {} but must not be negative; using {} seconds.", KEY_COOLDOWN, value, DEFAULT_COOLDOWN_SECONDS);
			notes.add("\"cooldown_seconds\" must not be negative, used the default");
			value = DEFAULT_COOLDOWN_SECONDS;
		} else if (value > MAX_COOLDOWN_SECONDS) {
			LOGGER.warn("\"{}\" is {} which exceeds the maximum of {}; clamping.", KEY_COOLDOWN, value, MAX_COOLDOWN_SECONDS);
			notes.add("\"cooldown_seconds\" clamped to " + MAX_COOLDOWN_SECONDS + "s");
			value = MAX_COOLDOWN_SECONDS;
		}

		cooldownSeconds = value;
		cooldownTicks = value * TICKS_PER_SECOND;
	}

	private static void readVaultReusable(JsonObject json, Path path, List<String> notes) {
		if (!json.has(KEY_VAULT_REUSABLE) || !json.get(KEY_VAULT_REUSABLE).isJsonPrimitive()) {
			LOGGER.info("{} is missing \"{}\"; using {}.", path, KEY_VAULT_REUSABLE, DEFAULT_VAULT_REUSABLE);
			vaultReusable = DEFAULT_VAULT_REUSABLE;
			return;
		}

		JsonElement element = json.get(KEY_VAULT_REUSABLE);
		if (!element.getAsJsonPrimitive().isBoolean()) {
			LOGGER.warn("\"{}\" in {} must be true or false; keeping {}.", KEY_VAULT_REUSABLE, path, vaultReusable);
			notes.add("\"vault_reusable\" must be true or false, kept " + vaultReusable);
			return;
		}
		vaultReusable = element.getAsBoolean();
	}

	private static String summary() {
		return "Trial Spawner cooldown " + cooldownSeconds + "s (" + cooldownTicks + " ticks), vaults reopenable: "
				+ (vaultReusable ? "yes" : "no") + ".";
	}

	private static void write(Path path) throws IOException {
		String contents = """
				{
				  "_comment": [
				    "Trail Spawner Cooldown - configuration.",
				    "",
				    "cooldown_seconds: how long a Trial Spawner waits before it can activate again.",
				    "  Measured in SECONDS; must be a whole number of 0 or more.",
				    "  Applies to both normal and ominous Trial Spawners.",
				    "",
				    "    1800 = 30 minutes (this is the vanilla value)",
				    "     300 = 5 minutes",
				    "      60 = 1 minute",
				    "      30 = 30 seconds",
				    "       0 = no cooldown",
				    "",
				    "vault_reusable: whether a player may open the same Trial Vault more than once.",
				    "  true  = a player can keep opening a vault for as long as they have keys",
				    "  false = vanilla behaviour, one reward per player per vault",
				    "  Applies to both normal and ominous vaults.",
				    "",
				    "Apply changes with /trailspawner reload, or by restarting the server."
				  ],
				  "cooldown_seconds": %d,
				  "vault_reusable": %b
				}
				""".formatted(cooldownSeconds, vaultReusable);
		Files.writeString(path, contents, StandardCharsets.UTF_8);
	}
}
