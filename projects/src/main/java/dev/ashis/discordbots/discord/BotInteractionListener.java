package dev.ashis.discordbots.discord;

import dev.ashis.discordbots.DiscordBotsPlugin;
import dev.ashis.discordbots.bot.BotProfile;
import dev.ashis.discordbots.commands.CommandCatalog;
import dev.ashis.discordbots.commands.Template;
import dev.ashis.discordbots.commands.TemplateCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class BotInteractionListener extends ListenerAdapter {
    private static final String[] EIGHT_BALL = {
            "Absolutely.", "Signs point to yes.", "Ask again later.", "The outlook is unclear.",
            "Probably not.", "Without a doubt.", "It is decidedly so.", "Concentrate and ask again."
    };
    private static final String[] JOKES = {
            "Why did the developer go broke? Because they used up all their cache.",
            "I told my computer I needed a break. It said: 'No problem, I'll go to sleep.'",
            "Why do Java developers wear glasses? Because they don't C#.",
            "A SQL query walks into a bar, walks up to two tables and asks: 'Can I join you?'"
    };

    private final DiscordBotsPlugin plugin;
    private final String profileName;

    public BotInteractionListener(DiscordBotsPlugin plugin, String profileName) {
        this.plugin = plugin;
        this.profileName = profileName;
    }

    @Override
    public void onReady(ReadyEvent event) {
        BotProfile profile = plugin.getBotManager().profile(profileName);
        if (profile == null) {
            plugin.getLogger().severe("The profile for connected bot '" + profileName + "' disappeared.");
            return;
        }

        List<SlashCommandData> slashCommands = buildSlashCommands(profile);
        event.getJDA().updateCommands().addCommands(slashCommands).queue(
                commands -> {
                    plugin.getLogger().info("Discord bot '" + profileName + "' connected as " +
                            event.getJDA().getSelfUser().getName() + "; registered " + countCommands(profile) +
                            " selected slash command(s).");
                    plugin.getBotManager().resumeTemporaryBans(profileName);
                },
                failure -> plugin.getLogger().severe("Could not register slash commands for bot '" + profileName +
                        "' (" + failure.getClass().getSimpleName() + ").")
        );
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        Template template = Template.fromKey(event.getName());
        if (template == null || event.getSubcommandGroup() == null || event.getSubcommandName() == null) {
            event.reply("This command is not part of an enabled template.").setEphemeral(true).queue();
            return;
        }
        TemplateCommand command = CommandCatalog.find(template, event.getSubcommandName());
        BotProfile profile = plugin.getBotManager().profile(profileName);
        if (command == null || profile == null ||
                !profile.enabledCommands().contains(template.key() + "/" + event.getSubcommandGroup() + "/" + event.getSubcommandName())) {
            event.reply("This command is no longer enabled.").setEphemeral(true).queue();
            return;
        }

        try {
            String response = switch (template) {
                case FUN -> handleFun(command, event);
                case UTILITY -> handleUtility(command, event);
                case ESSENTIALS -> handleEssentials(command, event);
                case MODERATION -> handleModeration(command, event);
            };
            event.reply(limitReply(response)).setEphemeral(template == Template.MODERATION).queue(
                    ignored -> plugin.getLogger().info("Bot '" + profileName + "' ran /" + template.key() + " " +
                            command.group() + " " + command.name() + " by " + event.getUser().getName() + "."),
                    failure -> plugin.getLogger().warning("Reply failed for bot '" + profileName + "', command " +
                            command.name() + " (" + failure.getClass().getSimpleName() + ").")
            );
        } catch (IllegalArgumentException exception) {
            event.reply(exception.getMessage()).setEphemeral(true).queue();
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Command /" + template.key() + " " + command.name() + " failed (" +
                    exception.getClass().getSimpleName() + ").");
            event.reply("That command could not be completed. Check the Minecraft server log for details.")
                    .setEphemeral(true).queue();
        }
    }

    private List<SlashCommandData> buildSlashCommands(BotProfile profile) {
        List<SlashCommandData> commands = new ArrayList<>();
        for (Template template : Template.values()) {
            List<TemplateCommand> selected = CommandCatalog.all().stream()
                    .filter(command -> command.template() == template)
                    .filter(command -> profile.enabledCommands().contains(command.id()))
                    .toList();
            if (selected.isEmpty()) {
                continue;
            }

            SlashCommandData root = Commands.slash(template.key(), template.displayName() + " template commands");
            for (String groupName : template.groupNames()) {
                List<SubcommandData> subcommands = selected.stream()
                        .filter(command -> command.group().equals(groupName))
                        .map(this::buildSubcommand)
                        .toList();
                if (!subcommands.isEmpty()) {
                    root.addSubcommandGroups(new SubcommandGroupData(groupName, groupName + " commands")
                            .addSubcommands(subcommands));
                }
            }
            commands.add(root);
        }
        return commands;
    }

    private SubcommandData buildSubcommand(TemplateCommand command) {
        SubcommandData data = new SubcommandData(command.name(), command.description());
        if (command.template() == Template.MODERATION) {
            data.addOptions(
                    new OptionData(OptionType.USER, "target", "Target member (when required)", false),
                    new OptionData(OptionType.STRING, "reason", "Reason or additional details", false).setMaxLength(500),
                    new OptionData(OptionType.STRING, "user_id", "Discord user ID for an unban", false),
                    new OptionData(OptionType.STRING, "message_id", "Message ID for pin or unpin", false),
                    new OptionData(OptionType.INTEGER, "amount", "Seconds, or number of messages (up to 100)", false)
                            .setMinValue(1).setMaxValue(2_419_200)
            );
        } else if (command.template() == Template.ESSENTIALS) {
            data.addOptions(
                    new OptionData(OptionType.USER, "user", "User to look up (defaults to you)", false),
                    new OptionData(OptionType.STRING, "input", "Optional command details", false).setMaxLength(1500)
            );
        } else {
            data.addOptions(new OptionData(OptionType.STRING, "input", "Text or values for this command", false)
                    .setMaxLength(1500));
        }
        return data;
    }

    private String handleFun(TemplateCommand command, SlashCommandInteractionEvent event) {
        String name = command.name();
        String input = option(event, "input");
        if (name.equals("coinflip")) {
            return ThreadLocalRandom.current().nextBoolean() ? "🪙 Heads!" : "🪙 Tails!";
        }
        if (name.equals("dice") || name.equals("roll")) {
            int sides = parseBoundedInteger(input, 6, 2, 1000);
            return "🎲 You rolled **" + ThreadLocalRandom.current().nextInt(1, sides + 1) + "** (d" + sides + ").";
        }
        if (name.equals("8ball")) {
            return "🎱 " + EIGHT_BALL[ThreadLocalRandom.current().nextInt(EIGHT_BALL.length)];
        }
        if (name.contains("joke") || name.equals("pun")) {
            return "😄 " + JOKES[ThreadLocalRandom.current().nextInt(JOKES.length)];
        }
        if (name.equals("choose") || name.equals("random-choice") || name.equals("this-or-that")) {
            List<String> choices = splitChoices(input);
            return "🎯 " + choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
        }
        if (name.equals("random-number") || name.equals("random-range")) {
            int[] range = parseRange(input);
            return "🎲 **" + ThreadLocalRandom.current().nextInt(range[0], range[1] + 1) + "**";
        }
        if (name.equals("would-you-rather")) {
            return "🤔 Would you rather be able to pause time or rewind it once a day?";
        }
        if (name.equals("riddle") || name.equals("random-riddle")) {
            return "🧩 What has keys but can't open locks? A piano.";
        }
        if (name.equals("trivia") || name.equals("quiz")) {
            return "🧠 Trivia: What is the largest planet in our solar system? **Jupiter**.";
        }
        if (name.equals("reverse-text")) {
            return new StringBuilder(requireInput(input)).reverse().toString();
        }
        if (name.equals("poll") || name.equals("vote")) {
            return "📊 **Poll:** " + requireInput(input) + "\nReact below to vote.";
        }
        return "✨ **" + displayName(name) + "**: " +
                (input.isBlank() ? "Your random moment of fun has arrived!" : input);
    }

    private String handleUtility(TemplateCommand command, SlashCommandInteractionEvent event) {
        String name = command.name();
        String input = option(event, "input");
        String text = input;
        if (text.isBlank() && !List.of("uuid", "unix-time", "timestamp").contains(name)) {
            text = requireInput(input);
        }
        return switch (name) {
            case "uppercase" -> requireInput(text).toUpperCase(Locale.ROOT);
            case "lowercase" -> requireInput(text).toLowerCase(Locale.ROOT);
            case "titlecase" -> titleCase(requireInput(text));
            case "reverse-text" -> new StringBuilder(requireInput(text)).reverse().toString();
            case "count-characters" -> "Characters: **" + requireInput(text).length() + "**";
            case "count-words" -> "Words: **" + requireInput(text).trim().split("\\s+").length + "**";
            case "count-lines" -> "Lines: **" + requireInput(text).split("\\R", -1).length + "**";
            case "trim-text" -> requireInput(text).trim();
            case "remove-spaces" -> requireInput(text).replaceAll("\\s+", "");
            case "slugify-text" -> requireInput(text).toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9]+", "-")
                    .replaceAll("^-|-$", "");
            case "base64-encode" -> java.util.Base64.getEncoder().encodeToString(requireInput(text).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            case "base64-decode" -> new String(java.util.Base64.getDecoder().decode(requireInput(text)), java.nio.charset.StandardCharsets.UTF_8);
            case "url-encode" -> java.net.URLEncoder.encode(requireInput(text), java.nio.charset.StandardCharsets.UTF_8);
            case "url-decode" -> java.net.URLDecoder.decode(requireInput(text), java.nio.charset.StandardCharsets.UTF_8);
            case "hash-sha256" -> sha256(requireInput(text));
            case "uuid" -> java.util.UUID.randomUUID().toString();
            case "unix-time", "timestamp" -> Long.toString(java.time.Instant.now().getEpochSecond());
            case "binary-encode" -> requireInput(text).codePoints().mapToObj(Integer::toBinaryString).collect(java.util.stream.Collectors.joining(" "));
            case "binary-decode" -> binaryDecode(requireInput(text));
            case "hex-encode" -> java.util.HexFormat.of().formatHex(requireInput(text).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            case "hex-decode" -> new String(java.util.HexFormat.of().parseHex(requireInput(text)), java.nio.charset.StandardCharsets.UTF_8);
            case "sort-lines" -> requireInput(text).lines().sorted().collect(java.util.stream.Collectors.joining("\n"));
            case "remove-duplicates" -> requireInput(text).lines().distinct().collect(java.util.stream.Collectors.joining("\n"));
            case "shuffle-lines" -> {
                List<String> lines = new ArrayList<>(requireInput(text).lines().toList());
                java.util.Collections.shuffle(lines);
                yield String.join("\n", lines);
            }
            case "repeat-text" -> repeatText(requireInput(text));
            case "replace-text" -> replaceText(requireInput(text));
            case "find-text" -> findText(requireInput(text));
            case "split-text" -> splitText(requireInput(text));
            case "join-text" -> joinText(requireInput(text));
            case "wrap-text" -> wrapText(requireInput(text));
            case "quote-text" -> requireInput(text).lines().map(line -> "> " + line)
                    .collect(java.util.stream.Collectors.joining("\n"));
            case "codeblock" -> "```\n" + requireInput(text).replace("```", "``\u200b`") + "\n```";
            case "escape-markdown" -> escapeMarkdown(requireInput(text));
            case "unescape-markdown" -> requireInput(text).replaceAll("\\\\([\\\\`*_{}\\[\\]()#+\\-.!|>~])", "$1");
            case "palindrome" -> {
                String normalized = requireInput(text).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
                yield normalized.equals(new StringBuilder(normalized).reverse().toString()) ? "Palindrome" : "Not a palindrome";
            }
            case "acronym" -> java.util.Arrays.stream(requireInput(text).trim().split("\\s+"))
                    .filter(word -> !word.isBlank()).map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT))
                    .collect(java.util.stream.Collectors.joining());
            case "average", "sum", "minimum", "maximum" -> aggregateNumbers(name, requireInput(text));
            case "add", "subtract", "multiply", "divide", "percentage" -> calculate(name, requireInput(text));
            case "median" -> median(requireInput(text));
            case "round", "floor", "ceil", "absolute" -> roundNumber(name, requireInput(text));
            case "factorial" -> factorial(requireInput(text));
            case "power", "remainder" -> calculate(name.equals("power") ? "power" : "remainder", requireInput(text));
            case "gcd", "lcm" -> gcdOrLcm(name, requireInput(text));
            case "decimal-to-hex" -> Long.toHexString(Long.parseLong(requireInput(text).trim())).toUpperCase(Locale.ROOT);
            case "hex-to-decimal" -> Long.toString(Long.parseLong(requireInput(text).trim().replaceFirst("^#", ""), 16));
            case "radians" -> Double.toString(Math.toRadians(Double.parseDouble(requireInput(text).trim())));
            case "degrees" -> Double.toString(Math.toDegrees(Double.parseDouble(requireInput(text).trim())));
            case "binary" -> Long.toBinaryString(Long.parseLong(requireInput(text).trim()));
            case "decimal" -> new java.math.BigDecimal(requireInput(text).trim()).stripTrailingZeros().toPlainString();
            case "fraction" -> decimalFraction(requireInput(text));
            case "ordinal" -> ordinal(Integer.parseInt(requireInput(text).trim()));
            case "even-odd" -> Long.parseLong(requireInput(text).trim()) % 2 == 0 ? "Even" : "Odd";
            case "prime-check" -> isPrime(Long.parseLong(requireInput(text).trim())) ? "Prime" : "Not prime";
            case "square-root" -> Double.toString(Math.sqrt(Double.parseDouble(requireInput(text).trim())));
            case "roman-numeral" -> toRoman(Integer.parseInt(requireInput(text).trim()));
            case "rgb-to-hex" -> rgbToHex(requireInput(text));
            case "hex-to-rgb" -> hexToRgb(requireInput(text));
            case "color-info" -> colorInfo(requireInput(text));
            case "byte-size" -> byteSize(requireInput(text));
            case "convert-temperature" -> convertTemperature(requireInput(text));
            case "convert-length" -> convertLength(requireInput(text));
            case "convert-weight" -> convertWeight(requireInput(text));
            case "html-encode" -> encodeHtml(requireInput(text));
            case "html-decode" -> decodeHtml(requireInput(text));
            case "json-format", "json-minify" -> formatJson(requireInput(text), name.equals("json-format"));
            case "yaml-format" -> formatYaml(requireInput(text));
            case "morse-encode" -> morseEncode(requireInput(text));
            case "morse-decode" -> morseDecode(requireInput(text));
            case "hash-sha1" -> hash(requireInput(text), "SHA-1");
            case "date-format" -> formatDate(requireInput(text));
            case "timezone" -> timezoneInfo(requireInput(text));
            case "qr-help" -> "QR generation is not included in the offline utility set. Use `/utility data` commands for local encoding tools.";
            case "convert-currency" -> "Live exchange rates are not configured; no stale conversion rate was used.";
            default -> "🛠️ **" + displayName(name) + "** received: " + text;
        };
    }

    private String handleEssentials(TemplateCommand command, SlashCommandInteractionEvent event) {
        String name = command.name();
        String input = option(event, "input");
        User selectedUser = event.getOption("user") == null ? event.getUser() : event.getOption("user").getAsUser();
        Member selectedMember = event.getGuild() == null ? null : event.getGuild().getMember(selectedUser);
        if (name.equals("ping") || name.equals("latency")) {
            return "🏓 Gateway ping: **" + event.getJDA().getGatewayPing() + " ms**.";
        }
        if (name.equals("about") || name.equals("bot-info") || name.equals("status")) {
            return "🤖 **" + event.getJDA().getSelfUser().getName() + "** is running from a Paper server.";
        }
        if (name.equals("help")) {
            return "Use `/moderation`, `/fun`, `/utility`, or `/essentials` and choose a command group. " +
                    "Only commands enabled by the Minecraft server operator are registered.";
        }
        if (name.equals("user-info") || name.equals("whois") || name.equals("my-info")) {
            return "👤 **" + selectedUser.getName() + "** (`" + selectedUser.getId() + "`)" +
                    "\nAccount created: " + selectedUser.getTimeCreated().toLocalDate() +
                    (selectedMember == null ? "" : "\nJoined this server: " + selectedMember.getTimeJoined().toLocalDate());
        }
        if (name.equals("banner")) {
            return "User banner lookup is not available through this Discord API response.";
        }
        if (name.equals("avatar") || name.equals("user-avatar") || name.equals("my-avatar")) {
            return selectedUser.getEffectiveAvatarUrl();
        }
        if (name.equals("user-id")) {
            return selectedUser.getId();
        }
        if (name.equals("user-created")) {
            return selectedUser.getTimeCreated().toString();
        }
        if (name.equals("user-joined")) {
            return selectedMember == null ? "That user is not in this server." : selectedMember.getTimeJoined().toString();
        }
        if (name.equals("user-roles") || name.equals("role-members")) {
            return selectedMember == null ? "That user is not in this server." :
                    selectedMember.getRoles().stream().map(role -> role.getName())
                            .collect(java.util.stream.Collectors.joining(", "));
        }
        if (name.equals("user-permissions") || name.equals("my-permissions") || name.equals("permissions")) {
            return selectedMember == null ? "That user is not in this server." :
                    selectedMember.getPermissions().stream().map(Permission::getName)
                            .collect(java.util.stream.Collectors.joining(", "));
        }
        if (name.equals("user-mention")) {
            return selectedUser.getAsMention();
        }
        if (name.equals("user-nickname")) {
            return selectedMember == null ? "That user is not in this server." :
                    (selectedMember.getNickname() == null ? selectedUser.getName() : selectedMember.getNickname());
        }
        if (name.equals("user-status") || name.equals("user-activity")) {
            return "Presence details are not available unless the bot is configured with Discord's privileged presence intent.";
        }
        if (name.equals("server-info") || name.equals("server-members") || name.equals("member-count")) {
            if (event.getGuild() == null) {
                return "This command can only be used in a server.";
            }
            return "🏠 **" + event.getGuild().getName() + "** — " + event.getGuild().getMemberCount() + " members.";
        }
        if (name.equals("server-icon")) {
            return event.getGuild() == null || event.getGuild().getIconUrl() == null
                    ? "This server has no icon." : event.getGuild().getIconUrl();
        }
        if (name.equals("server-banner")) {
            return event.getGuild() == null || event.getGuild().getBannerUrl() == null
                    ? "This server has no banner." : event.getGuild().getBannerUrl();
        }
        if (name.equals("server-owner")) {
            return event.getGuild() == null || event.getGuild().getOwner() == null
                    ? "Server owner is unavailable." : event.getGuild().getOwner().getUser().getAsMention();
        }
        if (name.equals("server-created")) {
            return event.getGuild() == null ? "This command can only be used in a server." :
                    event.getGuild().getTimeCreated().toLocalDate().toString();
        }
        if (name.equals("server-boosts")) {
            return event.getGuild() == null ? "This command can only be used in a server." :
                    event.getGuild().getBoostCount() + " boost(s).";
        }
        if (name.equals("invite")) {
            String clientId = event.getJDA().getSelfUser().getId();
            return "Invite this bot with `bot` and `applications.commands` scopes: " +
                    "https://discord.com/oauth2/authorize?client_id=" + clientId +
                    "&scope=bot%20applications.commands";
        }
        if (name.equals("server-emojis") || name.equals("emoji-list")) {
            return event.getGuild() == null ? "This command can only be used in a server." :
                    event.getGuild().getEmojis().stream().map(emoji -> emoji.getName())
                            .limit(30).collect(java.util.stream.Collectors.joining(", "));
        }
        if (name.equals("server-stickers") || name.equals("sticker-list")) {
            return event.getGuild() == null ? "This command can only be used in a server." :
                    event.getGuild().getStickers().stream().map(sticker -> sticker.getName())
                            .limit(30).collect(java.util.stream.Collectors.joining(", "));
        }
        if (name.equals("server-features")) {
            return event.getGuild() == null ? "This command can only be used in a server." :
                    String.join(", ", event.getGuild().getFeatures());
        }
        if (name.equals("server-roles") || name.equals("role-list")) {
            if (event.getGuild() == null) {
                return "This command can only be used in a server.";
            }
            return event.getGuild().getRoles().stream().limit(30).map(role -> role.getName())
                    .collect(java.util.stream.Collectors.joining(", "));
        }
        if (name.equals("server-channels") || name.equals("channel-list")) {
            if (event.getGuild() == null) {
                return "This command can only be used in a server.";
            }
            return event.getGuild().getTextChannels().stream().limit(30).map(channel -> "#" + channel.getName())
                    .collect(java.util.stream.Collectors.joining(", "));
        }
        if (name.equals("channel-info") || name.equals("channel-id") || name.equals("channel-topic")) {
            if (event.getChannel() instanceof TextChannel textChannel && name.equals("channel-topic")) {
                return textChannel.getTopic() == null ? "This channel has no topic." : textChannel.getTopic();
            }
            return event.getChannel().getName() + " (`" + event.getChannel().getId() + "`)";
        }
        return "ℹ️ **" + displayName(name) + "**" +
                (input.isBlank() ? "" : "\n" + input) +
                (event.getGuild() == null ? "" : "\nServer: " + event.getGuild().getName());
    }

    private String handleModeration(TemplateCommand command, SlashCommandInteractionEvent event) {
        String name = command.name();
        if (event.getGuild() == null || event.getMember() == null) {
            throw new IllegalArgumentException("This command can only be used in a server.");
        }
        Permission invokerPermission = switch (name) {
            case "ban", "tempban", "softban", "unban", "banlist" -> Permission.BAN_MEMBERS;
            case "kick", "kick-limit", "set-kick-limit" -> Permission.KICK_MEMBERS;
            case "timeout", "untimeout", "mute-channel", "unmute-channel", "warn", "warnings", "unwarn",
                    "warn-limit", "set-warn-limit" -> Permission.MODERATE_MEMBERS;
            case "purge", "purge-user", "purge-links", "purge-invites", "purge-mentions", "purge-bots",
                    "purge-embeds", "purge-files", "purge-after", "pin", "unpin", "clear-pins" ->
                    Permission.MESSAGE_MANAGE;
            case "slowmode", "slowmode-off", "lock-channel", "unlock-channel", "lockdown", "unlockdown" ->
                    Permission.MANAGE_CHANNEL;
            default -> Permission.MANAGE_SERVER;
        };
        if (!event.getMember().hasPermission(invokerPermission) &&
                !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            throw new IllegalArgumentException("You need a moderation permission to use this command.");
        }
        Member target = event.getOption("target") == null ? null : event.getOption("target").getAsMember();
        String reason = option(event, "reason");
        int amount = event.getOption("amount") == null ? 10 : (int) event.getOption("amount").getAsLong();
        if (name.equals("unban")) {
            requireBotPermission(event, Permission.BAN_MEMBERS);
            String userId = option(event, "user_id").trim();
            if (userId.isBlank() && target != null) {
                userId = target.getUser().getId();
            }
            if (!userId.matches("\\d{17,20}")) {
                throw new IllegalArgumentException("Provide the banned user's Discord ID in `user_id`.");
            }
            event.getGuild().unban(UserSnowflake.fromId(userId))
                    .reason(reason.isBlank() ? "Moderation command" : reason)
                    .queue(ignored -> { },
                            failure -> plugin.getLogger().warning("Unban action failed for bot '" + profileName +
                                    "' (" + failure.getClass().getSimpleName() + ")."));
            return "✅ Unban action queued for Discord user `" + userId + "`.";
        }
        if (name.equals("ban") || name.equals("tempban") || name.equals("softban")) {
            requireBotPermission(event, Permission.BAN_MEMBERS);
            requireTarget(target);
            if (name.equals("tempban") && event.getOption("amount") == null) {
                throw new IllegalArgumentException("Set the temporary-ban duration in seconds using `amount`.");
            }
            int deletedMessageDays = name.equals("softban") ? 1 : 0;
            event.getGuild().ban(target.getUser(), deletedMessageDays, java.util.concurrent.TimeUnit.DAYS)
                    .reason(reason.isBlank() ? "Moderation command" : reason)
                    .queue(ignored -> {
                        if (name.equals("tempban") || name.equals("softban")) {
                            long seconds = name.equals("softban") ? 2 : amount;
                            try {
                                plugin.getBotManager().scheduleTemporaryUnban(profileName,
                                        event.getGuild().getId(), target.getUser().getId(), seconds);
                            } catch (java.io.IOException exception) {
                                plugin.getLogger().severe("Ban was applied but its expiry could not be saved for bot '" +
                                        profileName + "': " + exception.getMessage());
                            }
                        }
                    }, failure -> plugin.getLogger().warning("Ban action failed for bot '" + profileName +
                            "' (" + failure.getClass().getSimpleName() + ")."));
            return switch (name) {
                case "tempban" -> "⏳ Temporarily banned **" + target.getUser().getName() + "** for " + amount + " seconds.";
                case "softban" -> "🧹 Soft-banned **" + target.getUser().getName() + "** and scheduled an unban.";
                default -> "🔨 Banned **" + target.getUser().getName() + "**.";
            };
        }
        if (name.equals("kick")) {
            requireBotPermission(event, Permission.KICK_MEMBERS);
            requireTarget(target);
            event.getGuild().kick(target).reason(reason.isBlank() ? "Moderation command" : reason).queue();
            return "👢 Kicked **" + target.getUser().getName() + "**.";
        }
        if (name.equals("timeout")) {
            requireBotPermission(event, Permission.MODERATE_MEMBERS);
            requireTarget(target);
            target.timeoutFor(Duration.ofSeconds(amount)).reason(reason.isBlank() ? "Moderation command" : reason).queue();
            return "⏳ Timed out **" + target.getUser().getName() + "** for " + amount + " seconds.";
        }
        if (name.equals("untimeout")) {
            requireBotPermission(event, Permission.MODERATE_MEMBERS);
            requireTarget(target);
            target.removeTimeout().reason(reason.isBlank() ? "Moderation command" : reason).queue();
            return "✅ Removed timeout for **" + target.getUser().getName() + "**.";
        }
        if (name.equals("purge") || name.startsWith("purge-")) {
            requireBotPermission(event, Permission.MESSAGE_MANAGE);
            if (amount > 100) {
                throw new IllegalArgumentException("Purge can remove at most 100 messages at a time.");
            }
            if (!(event.getChannel() instanceof TextChannel channel)) {
                throw new IllegalArgumentException("Purge can only be used in a text channel.");
            }
            channel.getHistory().retrievePast(amount).queue(messages -> {
                messages.forEach(message -> message.delete().queue(
                        ignored -> { },
                        failure -> plugin.getLogger().warning("A purge deletion failed for bot '" + profileName +
                                "' (" + failure.getClass().getSimpleName() + ").")
                ));
            }, failure -> plugin.getLogger().warning("Could not retrieve messages for bot '" + profileName +
                    "' (" + failure.getClass().getSimpleName() + ")."));
            return "🧹 Queued a purge of up to " + amount + " recent message(s).";
        }
        if (name.equals("slowmode") || name.equals("slowmode-off")) {
            requireBotPermission(event, Permission.MANAGE_CHANNEL);
            if (!(event.getChannel() instanceof TextChannel channel)) {
                throw new IllegalArgumentException("Slowmode can only be changed in a text channel.");
            }
            int seconds = name.equals("slowmode-off") ? 0 : Math.min(amount, 21600);
            channel.getManager().setSlowmode(seconds).queue();
            return "🐢 Set channel slowmode to " + seconds + " second(s).";
        }
        if (name.equals("pin") || name.equals("unpin")) {
            requireBotPermission(event, Permission.MESSAGE_MANAGE);
            String messageId = option(event, "message_id");
            if (messageId.isBlank()) {
                throw new IllegalArgumentException("Provide a message ID in the `message_id` option.");
            }
            event.getChannel().retrieveMessageById(messageId).queue(message ->
                    (name.equals("pin") ? message.pin() : message.unpin()).queue(
                            ignored -> { },
                            failure -> plugin.getLogger().warning("Pin action failed for bot '" + profileName +
                                    "' (" + failure.getClass().getSimpleName() + ").")
                    ), failure -> plugin.getLogger().warning("Could not find the requested message for bot '" +
                            profileName + "' (" + failure.getClass().getSimpleName() + ")."));
            return name.equals("pin") ? "📌 Pin action queued." : "📌 Unpin action queued.";
        }
        return "🛡️ **" + displayName(name) + "** is enabled." +
                (target == null ? "" : "\nTarget: " + target.getUser().getAsMention()) +
                (reason.isBlank() ? "" : "\nDetails: " + reason);
    }

    private static void requireBotPermission(SlashCommandInteractionEvent event, Permission permission) {
        if (event.getGuild().getSelfMember().hasPermission(permission)) {
            return;
        }
        throw new IllegalArgumentException("The bot needs the " + permission.getName() + " permission.");
    }

    private static void requireTarget(Member target) {
        if (target == null) {
            throw new IllegalArgumentException("Choose a target member.");
        }
    }

    private static int parseBoundedInteger(String input, int fallback, int min, int max) {
        if (input.isBlank()) {
            return fallback;
        }
        int value = Integer.parseInt(input.trim());
        if (value < min || value > max) {
            throw new IllegalArgumentException("Enter a number from " + min + " to " + max + ".");
        }
        return value;
    }

    private static List<String> splitChoices(String input) {
        List<String> choices = java.util.Arrays.stream(requireInput(input).split(","))
                .map(String::trim).filter(choice -> !choice.isEmpty()).toList();
        if (choices.size() < 2) {
            throw new IllegalArgumentException("Provide at least two choices separated by commas.");
        }
        return choices;
    }

    private static int[] parseRange(String input) {
        String[] bounds = requireInput(input).split("[,\\s]+");
        if (bounds.length != 2) {
            throw new IllegalArgumentException("Enter a minimum and maximum, for example `1 100`.");
        }
        int low = Integer.parseInt(bounds[0]);
        int high = Integer.parseInt(bounds[1]);
        if (low > high || high - (long) low > 1_000_000) {
            throw new IllegalArgumentException("Enter a valid range no larger than one million.");
        }
        return new int[]{low, high};
    }

    private static String requireInput(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Provide the required input in the `input` option.");
        }
        return input;
    }

    private static String option(SlashCommandInteractionEvent event, String name) {
        return event.getOption(name) == null ? "" : event.getOption(name).getAsString();
    }

    private static String displayName(String name) {
        return name.replace('-', ' ');
    }

    private static String titleCase(String text) {
        StringBuilder result = new StringBuilder();
        for (String word : text.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!word.isEmpty()) {
                if (!result.isEmpty()) {
                    result.append(' ');
                }
                result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return result.toString();
    }

    private static String aggregateNumbers(String operation, String input) {
        double[] values = java.util.Arrays.stream(input.trim().split("[,\\s]+"))
                .mapToDouble(Double::parseDouble).toArray();
        if (values.length == 0) {
            throw new IllegalArgumentException("Enter one or more numbers separated by spaces or commas.");
        }
        return switch (operation) {
            case "sum" -> Double.toString(java.util.Arrays.stream(values).sum());
            case "minimum" -> Double.toString(java.util.Arrays.stream(values).min().orElseThrow());
            case "maximum" -> Double.toString(java.util.Arrays.stream(values).max().orElseThrow());
            default -> Double.toString(java.util.Arrays.stream(values).average().orElseThrow());
        };
    }

    private static String calculate(String operation, String input) {
        String[] numbers = input.trim().split("[,\\s]+");
        if (numbers.length != 2) {
            throw new IllegalArgumentException("Enter two numbers separated by a space or comma.");
        }
        double first = Double.parseDouble(numbers[0]);
        double second = Double.parseDouble(numbers[1]);
        return switch (operation) {
            case "add" -> Double.toString(first + second);
            case "subtract" -> Double.toString(first - second);
            case "multiply" -> Double.toString(first * second);
            case "divide" -> second == 0 ? "Cannot divide by zero." : Double.toString(first / second);
            case "power" -> Double.toString(Math.pow(first, second));
            case "remainder" -> second == 0 ? "Cannot divide by zero." : Double.toString(first % second);
            default -> Double.toString(first * second / 100);
        };
    }

    private static String median(String input) {
        double[] values = java.util.Arrays.stream(input.trim().split("[,\\s]+")).mapToDouble(Double::parseDouble).sorted().toArray();
        if (values.length == 0) {
            throw new IllegalArgumentException("Enter one or more numbers separated by spaces or commas.");
        }
        int middle = values.length / 2;
        return Double.toString(values.length % 2 == 0 ? (values[middle - 1] + values[middle]) / 2 : values[middle]);
    }

    private static String roundNumber(String operation, String input) {
        double number = Double.parseDouble(input.trim());
        return switch (operation) {
            case "floor" -> Long.toString((long) Math.floor(number));
            case "ceil" -> Long.toString((long) Math.ceil(number));
            case "absolute" -> Double.toString(Math.abs(number));
            default -> Long.toString(Math.round(number));
        };
    }

    private static String factorial(String input) {
        int number = Integer.parseInt(input.trim());
        if (number < 0 || number > 20) {
            throw new IllegalArgumentException("Enter a whole number from 0 to 20.");
        }
        long result = 1;
        for (int value = 2; value <= number; value++) {
            result *= value;
        }
        return Long.toString(result);
    }

    private static String gcdOrLcm(String operation, String input) {
        String[] parts = input.trim().split("[,\\s]+");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Enter two whole numbers separated by a space or comma.");
        }
        long first = Long.parseLong(parts[0]);
        long second = Long.parseLong(parts[1]);
        long gcd = java.math.BigInteger.valueOf(first).abs().gcd(java.math.BigInteger.valueOf(second).abs()).longValueExact();
        if (operation.equals("gcd")) {
            return Long.toString(gcd);
        }
        if (gcd == 0) {
            return "0";
        }
        return Long.toString(Math.abs(Math.multiplyExact(first / gcd, second)));
    }

    private static String repeatText(String input) {
        int separator = input.indexOf(' ');
        if (separator < 1) {
            throw new IllegalArgumentException("Use `<count> <text>`.");
        }
        int count = Integer.parseInt(input.substring(0, separator));
        if (count < 1 || count > 20) {
            throw new IllegalArgumentException("Repeat count must be from 1 to 20.");
        }
        return (input.substring(separator + 1) + "\n").repeat(count).stripTrailing();
    }

    private static String replaceText(String input) {
        String[] parts = input.split("\\|", 3);
        if (parts.length != 3 || parts[0].isEmpty()) {
            throw new IllegalArgumentException("Use `<search> | <replacement> | <text>`.");
        }
        return parts[2].replace(parts[0].trim(), parts[1].trim());
    }

    private static String findText(String input) {
        String[] parts = input.split("\\|", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Use `<search> | <text>`.");
        }
        int index = parts[1].indexOf(parts[0].trim());
        return index < 0 ? "Not found." : "Found at character **" + index + "**.";
    }

    private static String splitText(String input) {
        String[] parts = input.split("\\|", 2);
        if (parts.length != 2 || parts[0].isEmpty()) {
            throw new IllegalArgumentException("Use `<separator> | <text>`.");
        }
        return String.join("\n", parts[1].split(java.util.regex.Pattern.quote(parts[0].trim()), -1));
    }

    private static String joinText(String input) {
        String[] parts = input.split("\\|", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Use `<separator> | <item 1>, <item 2>, ...>`.");
        }
        return String.join(parts[0].trim(), java.util.Arrays.stream(parts[1].split(","))
                .map(String::trim).toList());
    }

    private static String wrapText(String input) {
        String[] parts = input.split("\\s+", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Use `<width> <text>`.");
        }
        int width = Integer.parseInt(parts[0]);
        if (width < 10 || width > 200) {
            throw new IllegalArgumentException("Width must be from 10 to 200.");
        }
        StringBuilder output = new StringBuilder();
        int lineLength = 0;
        for (String word : parts[1].split("\\s+")) {
            if (lineLength > 0 && lineLength + word.length() + 1 > width) {
                output.append('\n');
                lineLength = 0;
            } else if (lineLength > 0) {
                output.append(' ');
                lineLength++;
            }
            output.append(word);
            lineLength += word.length();
        }
        return output.toString();
    }

    private static String escapeMarkdown(String input) {
        return input.replace("\\", "\\\\").replace("*", "\\*").replace("_", "\\_")
                .replace("~", "\\~").replace("|", "\\|").replace("`", "\\`");
    }

    private static String convertLength(String input) {
        String[] parts = input.trim().split("\\s+");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Use `<value> <m|km|mi|ft|in>`.");
        }
        double value = Double.parseDouble(parts[0]);
        double meters = switch (parts[1].toLowerCase(Locale.ROOT)) {
            case "m" -> value;
            case "km" -> value * 1000;
            case "mi" -> value * 1609.344;
            case "ft" -> value * 0.3048;
            case "in" -> value * 0.0254;
            default -> throw new IllegalArgumentException("Supported units: m, km, mi, ft, in.");
        };
        return "%.4f m (%.4f km, %.4f mi)".formatted(meters, meters / 1000, meters / 1609.344);
    }

    private static String convertWeight(String input) {
        String[] parts = input.trim().split("\\s+");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Use `<value> <kg|g|lb|oz>`.");
        }
        double value = Double.parseDouble(parts[0]);
        double kilograms = switch (parts[1].toLowerCase(Locale.ROOT)) {
            case "kg" -> value;
            case "g" -> value / 1000;
            case "lb" -> value * 0.45359237;
            case "oz" -> value * 0.028349523125;
            default -> throw new IllegalArgumentException("Supported units: kg, g, lb, oz.");
        };
        return "%.4f kg (%.4f lb)".formatted(kilograms, kilograms / 0.45359237);
    }

    private static String binaryDecode(String input) {
        return java.util.Arrays.stream(input.trim().split("\\s+"))
                .map(bits -> {
                    if (!bits.matches("[01]+")) {
                        throw new IllegalArgumentException("Binary input can contain only 0s and 1s.");
                    }
                    return new String(Character.toChars(Integer.parseInt(bits, 2)));
                }).collect(java.util.stream.Collectors.joining());
    }

    private static String colorInfo(String input) {
        String hex = input.trim().replaceFirst("^#", "");
        String rgb = hexToRgb(hex);
        int color = Integer.parseInt(hex, 16);
        double luminance = (0.2126 * ((color >> 16) & 255) + 0.7152 * ((color >> 8) & 255) +
                0.0722 * (color & 255)) / 255;
        return "#" + hex.toUpperCase(Locale.ROOT) + " — " + rgb + ", " +
                (luminance > 0.5 ? "light" : "dark") + " color.";
    }

    private static String byteSize(String input) {
        long bytes = Long.parseLong(input.trim());
        if (bytes < 0) {
            throw new IllegalArgumentException("Byte size cannot be negative.");
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KiB", "MiB", "GiB", "TiB"};
        double size = bytes;
        int unit = -1;
        do {
            size /= 1024;
            unit++;
        } while (size >= 1024 && unit < units.length - 1);
        return "%.2f %s".formatted(size, units[unit]);
    }

    private static String decimalFraction(String input) {
        java.math.BigDecimal decimal = new java.math.BigDecimal(input.trim()).stripTrailingZeros();
        int scale = decimal.scale();
        if (Math.abs(scale) > 9) {
            throw new IllegalArgumentException("Use a decimal with at most nine fractional digits.");
        }
        java.math.BigInteger numerator = decimal.unscaledValue();
        java.math.BigInteger denominator = java.math.BigInteger.ONE;
        if (scale > 0) {
            denominator = java.math.BigInteger.TEN.pow(scale);
        } else if (scale < 0) {
            numerator = numerator.multiply(java.math.BigInteger.TEN.pow(-scale));
        }
        java.math.BigInteger gcd = numerator.abs().gcd(denominator);
        return numerator.divide(gcd) + "/" + denominator.divide(gcd);
    }

    private static String ordinal(int number) {
        int lastTwo = Math.abs(number % 100);
        String suffix = lastTwo >= 11 && lastTwo <= 13 ? "th" : switch (Math.abs(number % 10)) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return number + suffix;
    }

    private static String encodeHtml(String input) {
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String decodeHtml(String input) {
        return input.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&amp;", "&");
    }

    private static String formatJson(String input, boolean pretty) {
        try {
            com.google.gson.JsonElement json = com.google.gson.JsonParser.parseString(input);
            com.google.gson.Gson gson = pretty
                    ? new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                    : new com.google.gson.Gson();
            return gson.toJson(json);
        } catch (com.google.gson.JsonParseException exception) {
            throw new IllegalArgumentException("Provide valid JSON.");
        }
    }

    private static String formatYaml(String input) {
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        try {
            yaml.loadFromString(input);
            return yaml.saveToString();
        } catch (org.bukkit.configuration.InvalidConfigurationException exception) {
            throw new IllegalArgumentException("Provide valid YAML.");
        }
    }

    private static final Map<Character, String> MORSE = Map.ofEntries(
            Map.entry('a', ".-"), Map.entry('b', "-..."), Map.entry('c', "-.-."),
            Map.entry('d', "-.."), Map.entry('e', "."), Map.entry('f', "..-."),
            Map.entry('g', "--."), Map.entry('h', "...."), Map.entry('i', ".."),
            Map.entry('j', ".---"), Map.entry('k', "-.-"), Map.entry('l', ".-.."),
            Map.entry('m', "--"), Map.entry('n', "-."), Map.entry('o', "---"),
            Map.entry('p', ".--."), Map.entry('q', "--.-"), Map.entry('r', ".-."),
            Map.entry('s', "..."), Map.entry('t', "-"), Map.entry('u', "..-"),
            Map.entry('v', "...-"), Map.entry('w', ".--"), Map.entry('x', "-..-"),
            Map.entry('y', "-.--"), Map.entry('z', "--.."), Map.entry('0', "-----"),
            Map.entry('1', ".----"), Map.entry('2', "..---"), Map.entry('3', "...--"),
            Map.entry('4', "....-"), Map.entry('5', "....."), Map.entry('6', "-...."),
            Map.entry('7', "--..."), Map.entry('8', "---.."), Map.entry('9', "----.")
    );

    private static String morseEncode(String input) {
        return input.toLowerCase(Locale.ROOT).chars()
                .mapToObj(character -> character == ' ' ? "/" : MORSE.getOrDefault((char) character, "?"))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private static String morseDecode(String input) {
        Map<String, Character> reverse = new java.util.HashMap<>();
        MORSE.forEach((character, code) -> reverse.put(code, character));
        return java.util.Arrays.stream(input.trim().split("\\s+"))
                .map(code -> code.equals("/") ? " " : String.valueOf(reverse.getOrDefault(code, '?')))
                .collect(java.util.stream.Collectors.joining());
    }

    private static String hash(String input, String algorithm) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance(algorithm)
                    .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(algorithm + " is unavailable.", exception);
        }
    }

    private static String formatDate(String input) {
        try {
            java.time.Instant instant = java.time.Instant.parse(input.trim());
            return java.time.format.DateTimeFormatter.ISO_ZONED_DATE_TIME
                    .withZone(java.time.ZoneOffset.UTC).format(instant);
        } catch (java.time.DateTimeException exception) {
            throw new IllegalArgumentException("Enter an ISO-8601 instant, for example `2026-09-26T12:00:00Z`.");
        }
    }

    private static String timezoneInfo(String input) {
        java.time.ZoneId zone;
        try {
            zone = java.time.ZoneId.of(input.trim());
        } catch (java.time.DateTimeException exception) {
            throw new IllegalArgumentException("Enter a valid time-zone ID such as `Europe/London` or `UTC`.");
        }
        return java.time.ZonedDateTime.now(zone).toString();
    }

    private static boolean isPrime(long value) {
        if (value < 2) {
            return false;
        }
        for (long divisor = 2; divisor <= value / divisor; divisor++) {
            if (value % divisor == 0) {
                return false;
            }
        }
        return true;
    }

    private static String toRoman(int number) {
        if (number < 1 || number > 3999) {
            throw new IllegalArgumentException("Enter a number from 1 to 3999.");
        }
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] numerals = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < values.length; index++) {
            while (number >= values[index]) {
                number -= values[index];
                result.append(numerals[index]);
            }
        }
        return result.toString();
    }

    private static String rgbToHex(String input) {
        String[] parts = input.trim().split("[,\\s]+");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Enter red, green, and blue values from 0 to 255.");
        }
        int red = Integer.parseInt(parts[0]);
        int green = Integer.parseInt(parts[1]);
        int blue = Integer.parseInt(parts[2]);
        if (red < 0 || red > 255 || green < 0 || green > 255 || blue < 0 || blue > 255) {
            throw new IllegalArgumentException("RGB values must be from 0 to 255.");
        }
        return "#%02X%02X%02X".formatted(red, green, blue);
    }

    private static String hexToRgb(String input) {
        String hex = input.trim().replaceFirst("^#", "");
        if (!hex.matches("[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Enter a six-digit hex color, such as `#33AAFF`.");
        }
        int rgb = Integer.parseInt(hex, 16);
        return "rgb(%d, %d, %d)".formatted((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255);
    }

    private static String convertTemperature(String input) {
        String[] parts = input.trim().split("\\s+");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Enter a temperature and unit, for example `32 F`.");
        }
        double value = Double.parseDouble(parts[0]);
        return switch (parts[1].toLowerCase(Locale.ROOT)) {
            case "c", "celsius" -> "%.2f °F".formatted(value * 9 / 5 + 32);
            case "f", "fahrenheit" -> "%.2f °C".formatted((value - 32) * 5 / 9);
            default -> throw new IllegalArgumentException("Temperature unit must be C or F.");
        };
    }

    private static String sha256(String input) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static String limitReply(String response) {
        if (response.length() <= 1900) {
            return response;
        }
        return response.substring(0, 1890) + "\n…(truncated)";
    }

    private static int countCommands(BotProfile profile) {
        return profile.enabledCommands().size();
    }
}
