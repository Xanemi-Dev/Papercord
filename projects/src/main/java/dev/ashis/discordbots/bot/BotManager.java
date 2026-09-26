package dev.ashis.discordbots.bot;

import dev.ashis.discordbots.DiscordBotsPlugin;
import dev.ashis.discordbots.commands.CommandCatalog;
import dev.ashis.discordbots.commands.Template;
import dev.ashis.discordbots.commands.TemplateCommand;
import dev.ashis.discordbots.discord.BotInteractionListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class BotManager {
    private static final Pattern PROFILE_NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    private final DiscordBotsPlugin plugin;
    private final Path botsDirectory;
    private final Map<String, BotProfile> profiles = new ConcurrentHashMap<>();
    private final TemporaryBanManager temporaryBans;

    public BotManager(DiscordBotsPlugin plugin) throws IOException {
        this.plugin = plugin;
        this.botsDirectory = plugin.getDataFolder().toPath().resolve("bots");
        this.temporaryBans = new TemporaryBanManager(plugin);
        Files.createDirectories(botsDirectory);
        loadProfiles();
    }

    public synchronized BotProfile createProfile(String rawName) throws IOException {
        String name = normalizeName(rawName);
        validateName(name);
        Path directory = botsDirectory.resolve(name);
        if (Files.exists(directory)) {
            throw new IllegalArgumentException("A bot profile named '" + name + "' already exists.");
        }

        Files.createDirectories(directory);
        Path profileFile = directory.resolve("profile.yml");
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("name", name);
        configuration.set("enabled-commands", List.of());
        configuration.save(profileFile.toFile());
        Files.createFile(directory.resolve("token.txt"));

        BotProfile profile = new BotProfile(name, directory, Set.of(), null);
        profiles.put(name, profile);
        return profile;
    }

    public List<BotProfile> profiles() {
        return profiles.values().stream()
                .sorted((left, right) -> left.name().compareTo(right.name()))
                .toList();
    }

    public BotProfile profile(String rawName) {
        if (rawName == null) {
            return null;
        }
        return profiles.get(normalizeName(rawName));
    }

    public synchronized Set<String> setCommandSelection(
            String rawName,
            Template template,
            String commandName,
            boolean enabled
    ) throws IOException {
        BotProfile profile = requireProfile(rawName);
        Set<String> selected = new LinkedHashSet<>(profile.enabledCommands());
        List<TemplateCommand> changes;

        if (commandName.equalsIgnoreCase("all")) {
            changes = template.allCommands().stream()
                    .map(name -> CommandCatalog.find(template, name))
                    .toList();
        } else {
            TemplateCommand command = CommandCatalog.find(template, commandName.toLowerCase(Locale.ROOT));
            if (command == null) {
                throw new IllegalArgumentException("Unknown " + template.displayName() + " command: " + commandName);
            }
            changes = List.of(command);
        }

        for (TemplateCommand command : changes) {
            if (enabled) {
                selected.add(command.id());
            } else {
                selected.remove(command.id());
            }
        }

        saveSelection(profile, selected);
        BotProfile updated = new BotProfile(profile.name(), profile.directory(), Set.copyOf(selected), profile.jda());
        profiles.put(updated.name(), updated);
        return updated.enabledCommands();
    }

    public synchronized BotProfile start(String rawName) throws IOException {
        BotProfile profile = requireProfile(rawName);
        if (profile.jda() != null) {
            throw new IllegalArgumentException("Bot '" + profile.name() + "' is already running.");
        }

        Path tokenFile = profile.directory().resolve("token.txt");
        if (!Files.isRegularFile(tokenFile)) {
            throw new IOException("Token file is missing for bot '" + profile.name() + "'.");
        }
        String token = Files.readString(tokenFile, StandardCharsets.UTF_8).trim();
        if (token.isEmpty()) {
            throw new IllegalArgumentException("Add the Discord bot token to " + tokenFile + " before starting it.");
        }

        BotInteractionListener listener = new BotInteractionListener(plugin, profile.name());
        JDA jda;
        try {
            jda = JDABuilder.createDefault(token)
                    .addEventListeners(listener)
                    .build();
        } catch (RuntimeException exception) {
            throw new IOException("Could not start bot '" + profile.name() + "' (" +
                    exception.getClass().getSimpleName() + "). Check the token and server logs.", exception);
        }

        BotProfile running = profile.withJda(jda);
        profiles.put(running.name(), running);
        plugin.getLogger().info("Starting Discord bot profile '" + running.name() + "'.");
        return running;
    }

    public synchronized void stop(String rawName) {
        BotProfile profile = requireProfile(rawName);
        if (profile.jda() == null) {
            throw new IllegalArgumentException("Bot '" + profile.name() + "' is not running.");
        }
        profile.jda().shutdown();
        profiles.put(profile.name(), profile.withJda(null));
        plugin.getLogger().info("Stopped Discord bot profile '" + profile.name() + "'.");
    }

    public void scheduleTemporaryUnban(String profileName, String guildId, String userId, long durationSeconds)
            throws IOException {
        temporaryBans.schedule(profileName, guildId, userId, durationSeconds);
    }

    public void resumeTemporaryBans(String profileName) {
        temporaryBans.resume(profileName);
    }

    public void shutdown() {
        for (BotProfile profile : profiles.values()) {
            if (profile.jda() != null) {
                profile.jda().shutdown();
            }
        }
    }

    private void loadProfiles() throws IOException {
        try (Stream<Path> paths = Files.list(botsDirectory)) {
            for (Path directory : paths.filter(Files::isDirectory).toList()) {
                Path profileFile = directory.resolve("profile.yml");
                if (!Files.isRegularFile(profileFile)) {
                    plugin.getLogger().warning("Ignoring bot folder without profile.yml: " + directory.getFileName());
                    continue;
                }

                YamlConfiguration configuration = YamlConfiguration.loadConfiguration(profileFile.toFile());
                String name = normalizeName(configuration.getString("name", directory.getFileName().toString()));
                try {
                    validateName(name);
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("Ignoring invalid bot profile folder: " + directory.getFileName());
                    continue;
                }

                Set<String> selected = new LinkedHashSet<>();
                for (String id : configuration.getStringList("enabled-commands")) {
                    if (CommandCatalog.all().stream().anyMatch(command -> command.id().equals(id))) {
                        selected.add(id);
                    } else {
                        plugin.getLogger().warning("Ignoring unknown command selection in profile '" + name + "': " + id);
                    }
                }
                profiles.put(name, new BotProfile(name, directory, Set.copyOf(selected), null));
            }
        }
    }

    private void saveSelection(BotProfile profile, Set<String> selection) throws IOException {
        Path file = profile.directory().resolve("profile.yml");
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file.toFile());
        configuration.set("name", profile.name());
        configuration.set("enabled-commands", new ArrayList<>(selection));
        configuration.save(file.toFile());
    }

    private BotProfile requireProfile(String rawName) {
        BotProfile profile = profile(rawName);
        if (profile == null) {
            throw new IllegalArgumentException("Bot profile not found: " + rawName);
        }
        return profile;
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    private static void validateName(String name) {
        if (!PROFILE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Profile names must be 1-32 characters: lowercase letters, digits, _ or -.");
        }
    }
}
