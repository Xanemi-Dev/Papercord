package dev.ashis.discordbots.bot;

import net.dv8tion.jda.api.JDA;

import java.nio.file.Path;
import java.util.Set;

public record BotProfile(String name, Path directory, Set<String> enabledCommands, JDA jda) {
    public BotProfile withJda(JDA replacement) {
        return new BotProfile(name, directory, enabledCommands, replacement);
    }
}
