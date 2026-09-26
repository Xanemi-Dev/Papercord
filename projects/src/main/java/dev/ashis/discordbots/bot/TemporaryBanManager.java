package dev.ashis.discordbots.bot;

import dev.ashis.discordbots.DiscordBotsPlugin;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.UserSnowflake;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TemporaryBanManager {
    private final DiscordBotsPlugin plugin;
    private final Map<String, BukkitTask> tasks = new ConcurrentHashMap<>();

    public TemporaryBanManager(DiscordBotsPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void schedule(String profileName, String guildId, String userId, long durationSeconds)
            throws IOException {
        long expiresAt = System.currentTimeMillis() + durationSeconds * 1000;
        updateEntry(profileName, guildId, userId, expiresAt);
        scheduleAt(profileName, guildId, userId, expiresAt);
    }

    public synchronized void resume(String profileName) {
        BotProfile profile = plugin.getBotManager().profile(profileName);
        if (profile == null) {
            return;
        }
        Path file = profile.directory().resolve("temp-bans.yml");
        if (!file.toFile().isFile()) {
            return;
        }
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file.toFile());
        for (Map<?, ?> entry : configuration.getMapList("bans")) {
            Object guild = entry.get("guild");
            Object user = entry.get("user");
            Object expiry = entry.get("expires-at");
            String guildId = identifier(guild);
            String userId = identifier(user);
            if (guildId != null && userId != null && expiry instanceof Number timestamp) {
                scheduleAt(profile.name(), guildId, userId, timestamp.longValue());
            } else {
                plugin.getLogger().warning("Ignoring malformed temporary-ban entry for profile '" + profileName + "'.");
            }
        }
    }

    private void scheduleAt(String profileName, String guildId, String userId, long expiresAt) {
        String key = profileName + "/" + guildId + "/" + userId;
        BukkitTask previous = tasks.remove(key);
        if (previous != null) {
            previous.cancel();
        }
        long remainingMillis = Math.max(0, expiresAt - System.currentTimeMillis());
        long delayTicks = Math.max(1, (remainingMillis + 49) / 50);
        tasks.put(key, plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> unban(profileName, guildId, userId, expiresAt), delayTicks));
    }

    private void unban(String profileName, String guildId, String userId, long expiresAt) {
        BotProfile profile = plugin.getBotManager().profile(profileName);
        if (profile == null || profile.jda() == null) {
            return;
        }
        Guild guild = profile.jda().getGuildById(guildId);
        if (guild == null) {
            plugin.getLogger().warning("Cannot lift temporary ban yet; bot '" + profileName +
                    "' is not connected to guild " + guildId + ".");
            scheduleRetry(profileName, guildId, userId, expiresAt);
            return;
        }
        guild.unban(UserSnowflake.fromId(userId)).queue(
                ignored -> {
                    try {
                        removeEntry(profileName, guildId, userId);
                        tasks.remove(profileName + "/" + guildId + "/" + userId);
                        plugin.getLogger().info("Temporary ban expired for Discord user " + userId +
                                " on bot '" + profileName + "'.");
                    } catch (IOException exception) {
                        plugin.getLogger().severe("Unbanned user " + userId +
                                " but could not update temporary-ban storage: " + exception.getMessage());
                    }
                },
                failure -> {
                    plugin.getLogger().warning("Could not lift temporary ban for profile '" + profileName +
                            "' (" + failure.getClass().getSimpleName() + "); retrying in 60 seconds.");
                    scheduleRetry(profileName, guildId, userId, expiresAt);
                }
        );
    }

    private void scheduleRetry(String profileName, String guildId, String userId, long expiresAt) {
        String key = profileName + "/" + guildId + "/" + userId;
        BukkitTask previous = tasks.remove(key);
        if (previous != null) {
            previous.cancel();
        }
        tasks.put(key, plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> unban(profileName, guildId, userId, expiresAt), 20L * 60));
    }

    private synchronized void updateEntry(String profileName, String guildId, String userId, long expiresAt)
            throws IOException {
        YamlConfiguration configuration = load(profileName);
        List<Map<?, ?>> entries = new ArrayList<>(configuration.getMapList("bans"));
        entries.removeIf(entry -> guildId.equals(entry.get("guild")) && userId.equals(entry.get("user")));
        Map<String, Object> entry = new HashMap<>();
        entry.put("guild", guildId);
        entry.put("user", userId);
        entry.put("expires-at", expiresAt);
        entries.add(entry);
        configuration.set("bans", entries);
        configuration.save(fileFor(profileName).toFile());
    }

    private synchronized void removeEntry(String profileName, String guildId, String userId) throws IOException {
        YamlConfiguration configuration = load(profileName);
        List<Map<?, ?>> entries = new ArrayList<>(configuration.getMapList("bans"));
        entries.removeIf(entry -> guildId.equals(entry.get("guild")) && userId.equals(entry.get("user")));
        configuration.set("bans", entries);
        configuration.save(fileFor(profileName).toFile());
    }

    private YamlConfiguration load(String profileName) throws IOException {
        Path file = fileFor(profileName);
        if (!file.toFile().exists()) {
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.createFile(file);
        }
        return YamlConfiguration.loadConfiguration(file.toFile());
    }

    private Path fileFor(String profileName) throws IOException {
        BotProfile profile = plugin.getBotManager().profile(profileName);
        if (profile == null) {
            throw new IOException("Bot profile no longer exists: " + profileName);
        }
        return profile.directory().resolve("temp-bans.yml");
    }

    private static String identifier(Object value) {
        if (value instanceof String text && text.matches("\\d+")) {
            return text;
        }
        if (value instanceof Number number) {
            return number.toString();
        }
        return null;
    }
}
