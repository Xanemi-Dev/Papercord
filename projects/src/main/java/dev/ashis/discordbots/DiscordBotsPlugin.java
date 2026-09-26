package dev.ashis.discordbots;

import dev.ashis.discordbots.bot.BotManager;
import dev.ashis.discordbots.command.BotCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;

public final class DiscordBotsPlugin extends JavaPlugin {
    private BotManager botManager;

    public BotManager getBotManager() {
        return botManager;
    }

    @Override
    public void onEnable() {
        try {
            botManager = new BotManager(this);
        } catch (IOException exception) {
            getLogger().severe("Could not initialize bot profile storage: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PluginCommand command = getCommand("discordbots");
        if (command == null) {
            getLogger().severe("The discordbots command is missing from plugin.yml.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        BotCommand executor = new BotCommand(this, botManager);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        getLogger().info("DiscordBots enabled. Use /discordbots help to get started.");
    }

    @Override
    public void onDisable() {
        if (botManager != null) {
            botManager.shutdown();
        }
    }
}
