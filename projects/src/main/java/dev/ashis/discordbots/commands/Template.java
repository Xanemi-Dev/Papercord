package dev.ashis.discordbots.commands;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum Template {
    MODERATION("moderation", "Moderation", new String[][]{
            words("member", "ban kick timeout untimeout warn softban tempban unban unwarn warnings history notes add-note remove-note clear-notes case reason reset-nickname set-nickname lockdown unlockdown verify unverify restrict unrestrict"),
            words("messages", "purge purge-user purge-links purge-invites purge-mentions purge-bots purge-embeds purge-files purge-after slowmode slowmode-off pin unpin lock-channel unlock-channel clear-pins report reports delete-report quarantine unquarantine mute-channel unmute-channel filter-word unfilter-word"),
            words("server", "audit modlog set-modlog clear-modlog rules set-rules automod automod-on automod-off anti-spam anti-raid verify-mode set-verified-role set-muted-role set-log-channel staff staff-note staff-report appeal appeals warn-limit set-warn-limit kick-limit set-kick-limit banlist")
    }),
    FUN("fun", "Fun", new String[][]{
            words("games", "coinflip dice roll guess riddle trivia quiz wordle higher-lower rock-paper-scissors would-you-rather this-or-that hangman blackjack roulette slots number-guess emoji-quiz flag-quiz movie-quiz music-quiz anagram scramble tictactoe connect-four"),
            words("random", "random-number random-choice random-color random-animal random-food random-country random-name random-emoji random-team random-date random-time random-quote random-fact random-prompt random-compliment random-roast random-meme random-joke random-pun random-riddle random-song random-movie random-book random-activity random-question"),
            words("social", "hug pat highfive wave cheer clap boop cuddle poke bonk slap dance ship rate roast compliment fortune 8ball ascii-emoji poll vote choose nickname badge mood")
    }),
    UTILITY("utility", "Utility", new String[][]{
            words("text", "uppercase lowercase titlecase reverse-text count-characters count-words count-lines trim-text remove-spaces remove-duplicates sort-lines shuffle-lines repeat-text replace-text find-text split-text join-text wrap-text slugify-text quote-text codeblock escape-markdown unescape-markdown palindrome acronym"),
            words("data", "base64-encode base64-decode url-encode url-decode html-encode html-decode json-format json-minify yaml-format hex-encode hex-decode binary-encode binary-decode morse-encode morse-decode qr-help hash-sha256 hash-sha1 uuid timestamp unix-time date-format timezone convert-length convert-weight"),
            words("numbers", "calculate add subtract multiply divide percentage average median minimum maximum sum round floor ceil absolute factorial square-root power remainder gcd lcm prime-check even-odd roman-numeral ordinal")
    }),
    ESSENTIALS("essentials", "Essentials", new String[][]{
            words("server", "help about bot-info ping uptime invite support status latency server-info server-icon server-banner server-owner server-created server-region server-boosts server-emojis server-stickers server-roles server-channels server-members server-rules server-features server-permissions server-audit"),
            words("users", "user-info avatar banner user-id user-created user-joined user-roles user-permissions user-status user-activity user-nickname user-avatar user-banner user-mention user-timezone bot-list bot-count member-count role-info role-members online-members staff-list my-info my-avatar whois"),
            words("channels", "channel-info channel-id channel-topic channel-category channel-created channel-slowmode channel-nsfw channel-type role-list emoji-list sticker-list invite-info invite-create invite-revoke webhook-list boost-info emoji-info sticker-info permissions my-permissions say embed poll reminder note")
    });

    private final String key;
    private final String displayName;
    private final String[][] groups;

    Template(String key, String displayName, String[][] groups) {
        this.key = key;
        this.displayName = displayName;
        this.groups = groups;
        int count = Arrays.stream(groups).mapToInt(group -> group.length - 1).sum();
        if (count < 75) {
            throw new IllegalStateException(displayName + " template must contain at least 75 commands, found " + count);
        }
        for (String[] group : groups) {
            if (group.length - 1 > 25) {
                throw new IllegalStateException(group[0] + " has more than Discord's 25-subcommand group limit.");
            }
        }
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> groupNames() {
        return Arrays.stream(groups).map(group -> group[0]).toList();
    }

    public List<String> commandsInGroup(String groupName) {
        return Arrays.stream(groups)
                .filter(group -> group[0].equals(groupName))
                .findFirst()
                .map(group -> Arrays.asList(group).subList(1, group.length))
                .orElse(List.of());
    }

    public List<String> allCommands() {
        return Arrays.stream(groups)
                .flatMap(group -> Arrays.stream(group).skip(1))
                .toList();
    }

    public String groupFor(String commandName) {
        return Arrays.stream(groups)
                .filter(group -> Arrays.asList(group).subList(1, group.length).contains(commandName))
                .map(group -> group[0])
                .findFirst()
                .orElse(null);
    }

    public static Template fromKey(String key) {
        if (key == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(template -> template.key.equals(key.toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElse(null);
    }

    private static String[] words(String group, String commands) {
        String[] names = commands.split(" ");
        String[] result = new String[names.length + 1];
        result[0] = group;
        System.arraycopy(names, 0, result, 1, names.length);
        return result;
    }
}
