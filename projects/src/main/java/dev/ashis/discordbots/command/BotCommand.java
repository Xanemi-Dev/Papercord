package dev.ashis.discordbots.command;

import dev.ashis.discordbots.DiscordBotsPlugin;
import dev.ashis.discordbots.bot.BotManager;
import dev.ashis.discordbots.bot.BotProfile;
import dev.ashis.discordbots.commands.CommandCatalog;
import dev.ashis.discordbots.commands.Template;
import dev.ashis.discordbots.commands.TemplateCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BotCommand implements CommandExecutor, TabCompleter {
    private final DiscordBotsPlugin plugin;
    private final BotManager manager;

    public BotCommand(DiscordBotsPlugin plugin, BotManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("discordbots.manage")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to manage Discord bots.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "create" -> create(sender, args);
                case "list" -> list(sender);
                case "templates" -> templates(sender);
                case "commands" -> commands(sender, args);
                case "select" -> select(sender, args, true);
                case "unselect" -> select(sender, args, false);
                case "start" -> start(sender, args);
                case "stop" -> stop(sender, args);
                case "status" -> status(sender, args);
                default -> sendHelp(sender);
            }
        } catch (IllegalArgumentException | IOException exception) {
            sender.sendMessage(ChatColor.RED + exception.getMessage());
            plugin.getLogger().warning("Command /discordbots " + String.join(" ", args) +
                    " failed: " + exception.getMessage());
        }
        return true;
    }

    private void create(CommandSender sender, String[] args) throws IOException {
        requireLength(args, 2, "/discordbots create <name>");
        BotProfile profile = manager.createProfile(args[1]);
        sender.sendMessage(ChatColor.GREEN + "Created bot profile '" + profile.name() + "'.");
        sender.sendMessage(ChatColor.YELLOW + "Put the bot token in " +
                profile.directory().resolve("token.txt") + ", then select commands and run /discordbots start " +
                profile.name() + ".");
    }

    private void list(CommandSender sender) {
        List<BotProfile> profiles = manager.profiles();
        if (profiles.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No bot profiles yet. Create one with /discordbots create <name>.");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "Bot profiles:");
        for (BotProfile profile : profiles) {
            sender.sendMessage(ChatColor.GRAY + "- " + profile.name() + " | " +
                    (profile.jda() == null ? ChatColor.RED + "stopped" : ChatColor.GREEN + "running") +
                    ChatColor.GRAY + " | " + profile.enabledCommands().size() + " commands selected");
        }
    }

    private void templates(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Command templates (use the key with select/unselect):");
        for (Template template : Template.values()) {
            sender.sendMessage(ChatColor.GRAY + "- " + template.key() + ChatColor.WHITE + " — " +
                    template.displayName() + ChatColor.GRAY + " (" + template.allCommands().size() + " commands)");
        }
    }

    private void commands(CommandSender sender, String[] args) {
        if (args.length < 3 || args.length > 4) {
            throw new IllegalArgumentException("Usage: /discordbots commands <bot> <template> [group]");
        }
        BotProfile profile = requireProfile(args[1]);
        Template template = requireTemplate(args[2]);
        String groupFilter = args.length == 4 ? args[3].toLowerCase(Locale.ROOT) : null;
        if (groupFilter != null && !template.groupNames().contains(groupFilter)) {
            throw new IllegalArgumentException("Unknown group. Available groups: " +
                    String.join(", ", template.groupNames()));
        }
        sender.sendMessage(ChatColor.GOLD + template.displayName() + " commands for " + profile.name() + ":");
        for (String group : template.groupNames()) {
            if (groupFilter != null && !groupFilter.equals(group)) {
                continue;
            }
            List<String> enabled = new ArrayList<>();
            List<String> disabled = new ArrayList<>();
            for (String name : template.commandsInGroup(group)) {
                String id = template.key() + "/" + group + "/" + name;
                (profile.enabledCommands().contains(id) ? enabled : disabled).add(name);
            }
            sender.sendMessage(ChatColor.AQUA + group + ChatColor.GRAY + " — " +
                    ChatColor.GREEN + "on: " + (enabled.isEmpty() ? "none" : String.join(", ", enabled)) +
                    ChatColor.GRAY + " | " + ChatColor.RED + "off: " +
                    (disabled.isEmpty() ? "none" : String.join(", ", disabled)));
        }
        sender.sendMessage(ChatColor.YELLOW + "Select: /discordbots select " + profile.name() +
                " " + template.key() + " <command|all>");
    }

    private void select(CommandSender sender, String[] args, boolean enabled) throws IOException {
        requireLength(args, 4, "/discordbots " + (enabled ? "select" : "unselect") +
                " <bot> <template> <command|all>");
        BotProfile profile = requireProfile(args[1]);
        Template template = requireTemplate(args[2]);
        manager.setCommandSelection(profile.name(), template, args[3], enabled);
        sender.sendMessage(ChatColor.GREEN + (enabled ? "Enabled " : "Disabled ") +
                (args[3].equalsIgnoreCase("all") ? "all " + template.displayName() + " commands" :
                        template.key() + " command '" + args[3] + "'") + " for " + profile.name() + ".");
        sender.sendMessage(ChatColor.YELLOW + "Restart the bot with /discordbots stop " + profile.name() +
                " and /discordbots start " + profile.name() + " to update Discord's command list.");
    }

    private void start(CommandSender sender, String[] args) throws IOException {
        requireLength(args, 2, "/discordbots start <bot>");
        BotProfile profile = manager.start(args[1]);
        sender.sendMessage(ChatColor.GREEN + "Starting bot '" + profile.name() +
                "'. Connection and command registration status will appear in the server log.");
    }

    private void stop(CommandSender sender, String[] args) {
        requireLength(args, 2, "/discordbots stop <bot>");
        manager.stop(args[1]);
        sender.sendMessage(ChatColor.GREEN + "Stopped bot '" + args[1] + "'.");
    }

    private void status(CommandSender sender, String[] args) {
        requireLength(args, 2, "/discordbots status <bot>");
        BotProfile profile = requireProfile(args[1]);
        sender.sendMessage(ChatColor.GOLD + profile.name() + ChatColor.GRAY + ": " +
                (profile.jda() == null ? ChatColor.RED + "stopped" : ChatColor.GREEN + "running") +
                ChatColor.GRAY + ", " + profile.enabledCommands().size() + " selected command(s).");
    }

    private BotProfile requireProfile(String name) {
        BotProfile profile = manager.profile(name);
        if (profile == null) {
            throw new IllegalArgumentException("Bot profile not found: " + name);
        }
        return profile;
    }

    private static Template requireTemplate(String name) {
        Template template = Template.fromKey(name);
        if (template == null) {
            throw new IllegalArgumentException("Unknown template. Choose moderation, fun, utility, or essentials.");
        }
        return template;
    }

    private static void requireLength(String[] args, int length, String usage) {
        if (args.length != length) {
            throw new IllegalArgumentException("Usage: " + usage);
        }
    }

    private static void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "DiscordBots commands:");
        sender.sendMessage(ChatColor.YELLOW + "/discordbots create <name>");
        sender.sendMessage(ChatColor.YELLOW + "/discordbots list | templates");
        sender.sendMessage(ChatColor.YELLOW + "/discordbots commands <bot> <template> [group]");
        sender.sendMessage(ChatColor.YELLOW + "/discordbots select|unselect <bot> <template> <command|all>");
        sender.sendMessage(ChatColor.YELLOW + "/discordbots start|stop|status <bot>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("discordbots.manage")) {
            return List.of();
        }
        if (args.length == 1) {
            return partial(args[0], List.of("create", "list", "templates", "commands", "select", "unselect",
                    "start", "stop", "status", "help"));
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && !action.equals("create")) {
            return partial(args[1], manager.profiles().stream().map(BotProfile::name).toList());
        }
        if (args.length == 3 && List.of("commands", "select", "unselect").contains(action)) {
            return partial(args[2], List.of(Template.values()).stream().map(Template::key).toList());
        }
        if (args.length == 4 && List.of("select", "unselect").contains(action)) {
            Template template = Template.fromKey(args[2]);
            if (template != null) {
                List<String> names = new ArrayList<>(template.allCommands());
                names.add("all");
                return partial(args[3], names);
            }
        }
        if (args.length == 4 && action.equals("commands")) {
            Template template = Template.fromKey(args[2]);
            if (template != null) {
                return partial(args[3], template.groupNames());
            }
        }
        return List.of();
    }

    private static List<String> partial(String prefix, List<String> choices) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return choices.stream().filter(choice -> choice.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }
}
